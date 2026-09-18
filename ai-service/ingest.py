"""
Pipeline de Ingesta y Vectorización de Manuales Técnicos en PostgreSQL (pgvector).
Lysandri Executive - AI Service
"""

import os
import sys
import logging
from pathlib import Path
from typing import List, Dict, Any

from pypdf import PdfReader
from sentence_transformers import SentenceTransformer
import psycopg2
from pgvector.psycopg2 import register_vector

from config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("ingest-pipeline")

def get_db_connection():
    """Establece conexión a PostgreSQL y registra el soporte para tipos vectoriales de pgvector."""
    try:
        conn = psycopg2.connect(**settings.db_connection_params)
        register_vector(conn)
        return conn
    except Exception as e:
        logger.error(f"Error conectando a la base de datos PostgreSQL: {e}")
        raise

def chunk_text(text: str, chunk_size: int = 500, overlap: int = 50) -> List[str]:
    """
    Divide un texto en fragmentos (chunks) de longitud aproximada con solapamiento (overlap).
    Preserva límites de palabras para evitar truncar términos técnicos a la mitad.
    """
    cleaned_text = " ".join(text.split())
    if not cleaned_text:
        return []

    if len(cleaned_text) <= chunk_size:
        return [cleaned_text]

    chunks: List[str] = []
    start = 0
    text_length = len(cleaned_text)

    while start < text_length:
        end = start + chunk_size
        
        if end >= text_length:
            chunk = cleaned_text[start:].strip()
            if chunk:
                chunks.append(chunk)
            break

        # Buscar el último espacio en blanco cercano para no cortar palabras
        boundary = cleaned_text.rfind(" ", start, end)
        if boundary != -1 and boundary > start + (chunk_size // 2):
            end = boundary

        chunk = cleaned_text[start:end].strip()
        if chunk:
            chunks.append(chunk)

        start = end - overlap
        if start < 0 or start >= text_length:
            break

    return chunks

def extract_chunks_from_pdf(pdf_path: Path) -> List[Dict[str, Any]]:
    """Extrae texto página por página de un archivo PDF y genera fragmentos anotados."""
    logger.info(f"Leyendo documento PDF: {pdf_path.name}")
    reader = PdfReader(str(pdf_path))
    document_chunks: List[Dict[str, Any]] = []

    for page_index, page in enumerate(reader.pages, start=1):
        try:
            page_text = page.extract_text() or ""
            chunks = chunk_text(page_text, chunk_size=500, overlap=50)

            for chunk_content in chunks:
                document_chunks.append({
                    "documento_origen": pdf_path.name,
                    "numero_pagina": page_index,
                    "contenido": chunk_content,
                })
        except Exception as e:
            logger.warning(f"Error extrayendo texto en página {page_index} de {pdf_path.name}: {e}")

    logger.info(f"-> Extraídos {len(document_chunks)} fragmentos de {pdf_path.name}")
    return document_chunks

def ingest_documents(docs_dir_path: str = None):
    """
    Pipeline principal:
    1. Escanea manuales técnicos en docs_dir.
    2. Extrae texto y realiza chunking.
    3. Calcula embeddings vectoriales con all-MiniLM-L6-v2 (384 dimensiones).
    4. Inserta los registros en la tabla documento_chunks en PostgreSQL.
    """
    docs_path = Path(docs_dir_path or settings.DOCS_DIR)
    docs_path.mkdir(parents=True, exist_ok=True)

    pdf_files = list(docs_path.glob("*.pdf"))
    if not pdf_files:
        logger.warning(f"No se encontraron archivos PDF en '{docs_path.resolve()}'.")
        logger.info("Por favor, deposita manuales técnicos (.pdf) en esa carpeta para su ingesta.")
        return

    logger.info(f"Se encontraron {len(pdf_files)} archivo(s) PDF para procesar.")

    # Cargar modelo de embeddings (all-MiniLM-L6-v2, 384 dimensiones)
    logger.info(f"Cargando modelo de embeddings: {settings.EMBEDDING_MODEL}")
    model = SentenceTransformer(settings.EMBEDDING_MODEL)

    conn = get_db_connection()
    total_chunks_ingestados = 0

    try:
        with conn.cursor() as cursor:
            for pdf_path in pdf_files:
                chunks_data = extract_chunks_from_pdf(pdf_path)
                if not chunks_data:
                    continue

                # Extraer contenidos para cálculo por lotes (batch embedding)
                text_contents = [c["contenido"] for c in chunks_data]
                logger.info(f"Generando embeddings vectoriales para {len(text_contents)} fragmentos...")
                embeddings = model.encode(text_contents, show_progress_bar=False, normalize_embeddings=True)

                # Limpiar fragmentos anteriores del mismo documento para evitar duplicados
                cursor.execute(
                    "DELETE FROM documento_chunks WHERE documento_origen = %s",
                    (pdf_path.name,)
                )

                # Insertar los nuevos fragmentos vectorizados
                logger.info(f"Insertando fragmentos en la tabla 'documento_chunks'...")
                insert_query = """
                    INSERT INTO documento_chunks (documento_origen, numero_pagina, contenido, embedding)
                    VALUES (%s, %s, %s, %s)
                """

                rows_to_insert = [
                    (
                        chunk["documento_origen"],
                        chunk["numero_pagina"],
                        chunk["contenido"],
                        embedding.tolist()
                    )
                    for chunk, embedding in zip(chunks_data, embeddings)
                ]

                cursor.executemany(insert_query, rows_to_insert)
                conn.commit()

                total_chunks_ingestados += len(rows_to_insert)
                logger.info(f"✓ Éxito: {len(rows_to_insert)} fragmentos indexados de '{pdf_path.name}'")

        logger.info(f"============================================================")
        logger.info(f"Ingesta finalizada: Total {total_chunks_ingestados} fragmentos indexados.")
        logger.info(f"============================================================")
    except Exception as e:
        conn.rollback()
        logger.error(f"Fallo durante la ingesta de documentos: {e}")
        raise
    finally:
        conn.close()

if __name__ == "__main__":
    target_dir = sys.argv[1] if len(sys.argv) > 1 else None
    ingest_documents(target_dir)

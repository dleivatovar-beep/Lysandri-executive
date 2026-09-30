"""
Microservicio de IA y Pipeline RAG con pgvector y LLM Gratuito (Groq / Gemini).
Lysandri Executive - AI Service
"""

import logging
from typing import List, Optional
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import psycopg2
from pgvector.psycopg2 import register_vector
from sentence_transformers import SentenceTransformer

from config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("ai-service")

# Modelos en memoria y conexiones
embedding_model: Optional[SentenceTransformer] = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Carga de recursos al iniciar y limpieza al detener el servicio."""
    global embedding_model
    logger.info(f"Cargando modelo de embeddings en memoria: {settings.EMBEDDING_MODEL}")
    try:
        embedding_model = SentenceTransformer(settings.EMBEDDING_MODEL)
        logger.info("✓ Modelo de embeddings cargado correctamente.")
    except Exception as e:
        logger.error(f"Fallo cargando modelo de embeddings: {e}")
        embedding_model = None

    yield
    logger.info("Cerrando recursos de ai-service.")

app = FastAPI(
    title="Lysandri Executive - AI RAG Service",
    description="Microservicio de recuperación aumentada por generación (RAG) con pgvector y LLMs",
    version="1.0.0",
    lifespan=lifespan
)

# Configuración de CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============================================================================
# ESQUEMAS PYDANTIC
# ============================================================================

class QueryRequest(BaseModel):
    pregunta: str = Field(..., min_length=2, description="Consulta del usuario a responder con contexto RAG")
    top_k: Optional[int] = Field(default=settings.TOP_K, ge=1, le=10, description="Número de fragmentos a recuperar")

class QueryResponse(BaseModel):
    respuesta: str = Field(..., description="Respuesta generada o mensaje de contexto no encontrado")
    fuentes: List[str] = Field(default_factory=list, description="Lista de manuales y páginas que fundamentan la respuesta")

# ============================================================================
# FUNCIONES AUXILIARES DE BASE DE DATOS Y LLM
# ============================================================================

def get_db_connection():
    """Retorna una conexión a PostgreSQL con soporte pgvector."""
    conn = psycopg2.connect(**settings.db_connection_params)
    register_vector(conn)
    return conn

def search_relevant_chunks(query_vector: list, top_k: int, min_similarity: float):
    """
    Ejecuta consulta vectorial usando la distancia coseno de pgvector (<=>).
    Similitud coseno = 1 - distancia_coseno.
    """
    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            # Operador <=> calcula la distancia coseno (0 = idénticos, 1 = ortogonales, 2 = opuestos)
            sql = """
                SELECT documento_origen, numero_pagina, contenido,
                       1 - (embedding <=> %s::vector) AS similitud
                FROM documento_chunks
                WHERE 1 - (embedding <=> %s::vector) >= %s
                ORDER BY embedding <=> %s::vector ASC
                LIMIT %s;
            """
            cursor.execute(sql, (query_vector, query_vector, min_similarity, query_vector, top_k))
            rows = cursor.fetchall()

            chunks = []
            for row in rows:
                chunks.append({
                    "documento_origen": row[0],
                    "numero_pagina": row[1],
                    "contenido": row[2],
                    "similitud": float(row[3])
                })
            return chunks
    finally:
        conn.close()

def generate_llm_response(pregunta: str, chunks: List[dict]) -> str:
    """
    Sintetiza la respuesta utilizando el LLM configurado (Groq con Llama 3.1 o Google Gemini).
    Si no hay API key configurada en entorno local, genera un resumen fundamentado directo.
    """
    contexto = "\n\n".join([
        f"--- Documento: {c['documento_origen']} (Página {c['numero_pagina']}) ---\n{c['contenido']}"
        for c in chunks
    ])

    system_prompt = (
        "Eres el Asistente IA Técnico y Directivo de Lysandri Executive. "
        "Tu misión es responder con precisión, autoridad técnica y tono profesional a la consulta del usuario, "
        "basándote ESTRICTAMENTE en los fragmentos de manuales y playbooks proporcionados a continuación.\n\n"
        "Reglas:\n"
        "1. No inventes información que no esté sustentada en el contexto.\n"
        "2. Cita explícitamente los manuales o normas referenciadas.\n"
        "3. Sé conciso, claro y directo en español.\n\n"
        f"Contexto Recuperado:\n{contexto}"
    )

    # 1. Opción Groq (Llama-3.1-8b-instant)
    if (settings.LLM_PROVIDER == "groq" or not settings.GEMINI_API_KEY) and settings.GROQ_API_KEY:
        try:
            from groq import Groq
            client = Groq(api_key=settings.GROQ_API_KEY)
            chat_completion = client.chat.completions.create(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": pregunta}
                ],
                model=settings.GROQ_MODEL,
                temperature=0.2,
                max_tokens=800,
            )
            return chat_completion.choices[0].message.content.strip()
        except Exception as e:
            logger.warning(f"Error llamando a Groq API: {e}. Intentando fallback...")

    # 2. Opción Google Gemini (gemini-1.5-flash)
    if settings.GEMINI_API_KEY:
        try:
            import google.generativeai as genai
            genai.configure(api_key=settings.GEMINI_API_KEY)
            model = genai.GenerativeModel(settings.GEMINI_MODEL)
            prompt_completo = f"{system_prompt}\n\nPregunta del Usuario: {pregunta}"
            response = model.generate_content(prompt_completo)
            if response.text:
                return response.text.strip()
        except Exception as e:
            logger.warning(f"Error llamando a Gemini API: {e}. Intentando fallback...")

    # 3. Fallback en caso de no disponer de API keys activas en desarrollo
    logger.info("Generando síntesis documental directa con los fragmentos recuperados (Modo Offline/Local).")
    fragmentos_texto = "\n\n• ".join([c["contenido"] for c in chunks[:2]])
    return (
        f"De acuerdo con los manuales técnicos oficiales de Lysandri Executive:\n\n"
        f"• {fragmentos_texto}\n\n"
        f"(Respuesta sintetizada automáticamente a partir del contexto documental de pgvector)."
    )

# ============================================================================
# ENDPOINTS
# ============================================================================

@app.get("/")
def read_root():
    return {
        "service": "Lysandri Executive AI RAG Service",
        "status": "online",
        "embedding_model": settings.EMBEDDING_MODEL,
        "llm_provider": settings.LLM_PROVIDER
    }

@app.get("/health")
def health_check():
    db_status = "unknown"
    try:
        conn = get_db_connection()
        with conn.cursor() as cursor:
            cursor.execute("SELECT 1;")
        conn.close()
        db_status = "connected"
    except Exception as e:
        db_status = f"disconnected: {str(e)}"

    return {
        "status": "healthy" if db_status == "connected" else "degraded",
        "database": db_status,
        "embeddings_ready": embedding_model is not None
    }

@app.post("/query", response_model=QueryResponse)
def query_rag(request: QueryRequest):
    """
    Endpoint principal de consulta RAG:
    1. Genera el embedding de la pregunta recibida.
    2. Realiza búsqueda por similitud coseno en pgvector (> SIMILARITY_THRESHOLD).
    3. Si no supera el umbral, indica que la información no está en los manuales oficiales.
    4. Si supera el umbral, genera la respuesta con el LLM gratuito e incluye las fuentes.
    """
    global embedding_model
    if embedding_model is None:
        try:
            embedding_model = SentenceTransformer(settings.EMBEDDING_MODEL)
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"No se pudo inicializar el modelo de embeddings: {e}"
            )

    pregunta_limpia = request.pregunta.strip()
    logger.info(f"Procesando consulta RAG: '{pregunta_limpia}' (top_k={request.top_k})")

    # 1. Generar embedding de la pregunta
    query_vector = embedding_model.encode(pregunta_limpia, normalize_embeddings=True).tolist()

    # 2. Búsqueda vectorial en PostgreSQL
    try:
        chunks = search_relevant_chunks(
            query_vector=query_vector,
            top_k=request.top_k,
            min_similarity=settings.SIMILARITY_THRESHOLD
        )
    except Exception as e:
        logger.error(f"Error consultando pgvector en base de datos: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error consultando el almacén vectorial: {str(e)}"
        )

    # 3. Validación de umbral de similitud
    if not chunks:
        logger.info(f"Ningún fragmento superó el umbral de similitud de {settings.SIMILARITY_THRESHOLD}.")
        return QueryResponse(
            respuesta="La información solicitada no se encuentra en los manuales oficiales de la plataforma Lysandri Executive.",
            fuentes=[]
        )

    # 4. Formatear lista de fuentes con formato 'Documento.pdf (pág. X)'
    fuentes_vistas = set()
    fuentes_lista = []
    for c in chunks:
        fuente_fmt = f"{c['documento_origen']} (pág. {c['numero_pagina']})"
        if fuente_fmt not in fuentes_vistas:
            fuentes_vistas.add(fuente_fmt)
            fuentes_lista.append(fuente_fmt)

    # 5. Generar respuesta con LLM
    respuesta_llm = generate_llm_response(pregunta_limpia, chunks)

    return QueryResponse(
        respuesta=respuesta_llm,
        fuentes=fuentes_lista
    )

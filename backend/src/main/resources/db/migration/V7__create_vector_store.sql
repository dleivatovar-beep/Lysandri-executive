-- ============================================================================
-- Migración V7: Almacén Vectorial para RAG (Retrieval-Augmented Generation)
-- Modelo de embedding esperado: all-MiniLM-L6-v2 (dimensión 384)
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documento_chunks (
    id BIGSERIAL PRIMARY KEY,
    documento_origen VARCHAR(255) NOT NULL,
    numero_pagina INT,
    contenido TEXT NOT NULL,
    embedding vector(384),
    creado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Índice HNSW optimizado para búsquedas por similitud de coseno
CREATE INDEX IF NOT EXISTS idx_documento_chunks_embedding 
    ON documento_chunks USING hnsw (embedding vector_cosine_ops);

-- Asignación de privilegios para el rol de aplicación si existe en el entorno
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE documento_chunks TO lysandri_app;
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO lysandri_app;
    END IF;
END
$$;

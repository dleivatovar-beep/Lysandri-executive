-- V10: Asegurar tabla de fragmentos vectoriales para el microservicio de IA (Python RAG)
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documento_chunks (
    id BIGSERIAL PRIMARY KEY,
    documento_origen VARCHAR(255) NOT NULL,
    numero_pagina INT,
    contenido TEXT NOT NULL,
    embedding vector(384),
    creado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documento_chunks_embedding 
ON documento_chunks USING hnsw (embedding vector_cosine_ops);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE documento_chunks TO lysandri_app;
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO lysandri_app;
    END IF;
END $$;

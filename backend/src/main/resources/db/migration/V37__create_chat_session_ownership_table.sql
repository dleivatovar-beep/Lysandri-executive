-- V37: Tabla de propiedad explícita y control seguro de sesiones del Asistente Virtual RAG
CREATE TABLE IF NOT EXISTS chat_session (
    id_sesion VARCHAR(64) PRIMARY KEY,
    id_user BIGINT REFERENCES usuario(id_user) ON DELETE SET NULL,
    session_token_hash VARCHAR(128) NOT NULL,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ultima_actividad TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user ON chat_session(id_user);


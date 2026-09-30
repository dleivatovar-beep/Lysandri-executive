-- ============================================================================
-- Migración V8: Purga de LMS propietario y migración a Tienda de Conocimiento
-- Integración con Moodle LMS externo + Stripe + RAG pgvector (1536 dim)
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Eliminar tablas obsoletas del LMS propietario
DROP TABLE IF EXISTS PROGRESO_LECCION CASCADE;
DROP TABLE IF EXISTS ACTIVIDAD_BLOQUE CASCADE;
DROP TABLE IF EXISTS LECCION CASCADE;
DROP TABLE IF EXISTS MODULO CASCADE;
DROP TABLE IF EXISTS BLOQUE CASCADE;
DROP TABLE IF EXISTS INSCRIPCIONES CASCADE;
DROP TABLE IF EXISTS SUSCRIPCIONES CASCADE;
DROP TABLE IF EXISTS INSTRUCTOR CASCADE;
DROP TABLE IF EXISTS CHAT_MENSAJES CASCADE;
DROP TABLE IF EXISTS documento_chunks CASCADE;

-- 2. Migrar o Crear tabla USUARIO
CREATE TABLE IF NOT EXISTS USUARIO (
    id_user BIGSERIAL PRIMARY KEY,
    moodle_user_id BIGINT UNIQUE,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    passw VARCHAR(255) NOT NULL,
    telefono VARCHAR(25),
    rol VARCHAR(20) NOT NULL DEFAULT 'CLIENTE' CHECK (rol IN ('CLIENTE', 'ADMIN')),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_usuario_email ON USUARIO(email);
CREATE INDEX IF NOT EXISTS idx_usuario_moodle_id ON USUARIO(moodle_user_id);

-- Si existía USUARIOS previamente con datos, migrar
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'usuarios') THEN
        INSERT INTO USUARIO (id_user, nombres, apellidos, email, passw, telefono, rol, activo, fecha_creacion)
        SELECT id_user, nombres, apellidos, email, passw, telefono, 
               CASE WHEN rol = 'ADMIN' THEN 'ADMIN' ELSE 'CLIENTE' END, 
               TRUE, CURRENT_TIMESTAMP
        FROM USUARIOS
        ON CONFLICT (email) DO NOTHING;
        
        DROP TABLE USUARIOS CASCADE;
    END IF;
END $$;

-- 3. Asegurar estructura de PROGRAMA para Tienda con Moodle
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'programa') THEN
        CREATE TABLE PROGRAMA (
            id_programa BIGSERIAL PRIMARY KEY,
            moodle_course_id BIGINT NOT NULL UNIQUE,
            titulo VARCHAR(200) NOT NULL,
            slug VARCHAR(220) NOT NULL UNIQUE,
            subtitulo VARCHAR(300),
            descripcion_corta TEXT,
            descripcion_detallada TEXT,
            precio DECIMAL(10, 2) NOT NULL CHECK (precio >= 0),
            moneda VARCHAR(3) NOT NULL DEFAULT 'USD',
            imagen_portada_url VARCHAR(500),
            syllabus_url VARCHAR(500),
            instructor_nombre VARCHAR(150),
            instructor_bio TEXT,
            nivel VARCHAR(50) DEFAULT 'EJECUTIVO',
            duracion_horas INT DEFAULT 0,
            activo BOOLEAN NOT NULL DEFAULT TRUE,
            fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
    ELSE
        -- Adaptar columnas si ya existía
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS moodle_course_id BIGINT;
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS slug VARCHAR(220);
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS subtitulo VARCHAR(300);
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS descripcion_corta TEXT;
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS descripcion_detallada TEXT;
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS precio DECIMAL(10, 2) DEFAULT 0.00;
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS moneda VARCHAR(3) DEFAULT 'USD';
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS imagen_portada_url VARCHAR(500);
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS syllabus_url VARCHAR(500);
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS instructor_nombre VARCHAR(150);
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS instructor_bio TEXT;
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS duracion_horas INT DEFAULT 0;
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS activo BOOLEAN DEFAULT TRUE;
        ALTER TABLE PROGRAMA ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

        -- Poblar moodle_course_id y slug para filas existentes si estuviesen vacíos
        UPDATE PROGRAMA SET moodle_course_id = id_programa WHERE moodle_course_id IS NULL;
        UPDATE PROGRAMA SET slug = 'programa-' || id_programa WHERE slug IS NULL;
        UPDATE PROGRAMA SET precio = 499.00 WHERE precio = 0.00;

        ALTER TABLE PROGRAMA ALTER COLUMN moodle_course_id SET NOT NULL;
        ALTER TABLE PROGRAMA ALTER COLUMN slug SET NOT NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_programa_moodle_course ON PROGRAMA(moodle_course_id);
CREATE INDEX IF NOT EXISTS idx_programa_slug ON PROGRAMA(slug);

-- 4. Recrear o adaptar ORDENES y DETALLE_ORDENES
DROP TABLE IF EXISTS DETALLE_ORDENES CASCADE;
DROP TABLE IF EXISTS ORDENES CASCADE;

CREATE TABLE ORDENES (
    id_orden BIGSERIAL PRIMARY KEY,
    id_user BIGINT NOT NULL REFERENCES USUARIO(id_user) ON DELETE RESTRICT,
    codigo_orden VARCHAR(36) NOT NULL UNIQUE,
    fecha_orden TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado_orden VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado_orden IN ('PENDIENTE', 'PAGADO', 'FALLIDO', 'REEMBOLSADO')),
    total DECIMAL(10, 2) NOT NULL CHECK (total >= 0),
    moneda VARCHAR(3) NOT NULL DEFAULT 'USD',
    metodo_pago VARCHAR(50) NOT NULL DEFAULT 'STRIPE',
    stripe_session_id VARCHAR(255) UNIQUE,
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    moodle_matricula_sincronizada BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_pago TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ordenes_user ON ORDENES(id_user);
CREATE INDEX IF NOT EXISTS idx_ordenes_stripe_session ON ORDENES(stripe_session_id);

CREATE TABLE DETALLE_ORDENES (
    id_detalle BIGSERIAL PRIMARY KEY,
    id_orden BIGINT NOT NULL REFERENCES ORDENES(id_orden) ON DELETE CASCADE,
    id_programa BIGINT NOT NULL REFERENCES PROGRAMA(id_programa) ON DELETE RESTRICT,
    precio_unitario DECIMAL(10, 2) NOT NULL CHECK (precio_unitario >= 0),
    cantidad INT NOT NULL DEFAULT 1 CHECK (cantidad > 0),
    subtotal DECIMAL(10, 2) NOT NULL CHECK (subtotal >= 0),
    moodle_matriculado BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_orden_programa UNIQUE (id_orden, id_programa)
);

CREATE INDEX IF NOT EXISTS idx_detalle_orden ON DETALLE_ORDENES(id_orden);

-- 5. Crear tablas para RAG (DOCUMENTO, DOCUMENTO_CHUNK con 1536 dim, HISTORIAL_CONSULTA)
CREATE TABLE IF NOT EXISTS DOCUMENTO (
    id_documento BIGSERIAL PRIMARY KEY,
    id_programa BIGINT REFERENCES PROGRAMA(id_programa) ON DELETE CASCADE,
    titulo VARCHAR(250) NOT NULL,
    tipo_documento VARCHAR(50) NOT NULL CHECK (tipo_documento IN ('SYLLABUS', 'BROCHURE', 'POLITICA_ACADEMICA', 'GUIA')),
    url_archivo VARCHAR(500),
    checksum_sha256 VARCHAR(64),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_subida TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS DOCUMENTO_CHUNK (
    id_chunk BIGSERIAL PRIMARY KEY,
    id_documento BIGINT NOT NULL REFERENCES DOCUMENTO(id_documento) ON DELETE CASCADE,
    id_programa BIGINT REFERENCES PROGRAMA(id_programa) ON DELETE CASCADE,
    numero_pagina INT,
    contenido TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    embedding vector(1536) NOT NULL,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documento_chunk_hnsw_cosine 
ON DOCUMENTO_CHUNK USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_chunk_programa ON DOCUMENTO_CHUNK(id_programa);

CREATE TABLE IF NOT EXISTS HISTORIAL_CONSULTA (
    id_historial BIGSERIAL PRIMARY KEY,
    id_user BIGINT REFERENCES USUARIO(id_user) ON DELETE SET NULL,
    id_programa BIGINT REFERENCES PROGRAMA(id_programa) ON DELETE SET NULL,
    sesion_id VARCHAR(64) NOT NULL,
    pregunta TEXT NOT NULL,
    respuesta_ia TEXT NOT NULL,
    chunks_referenciados JSONB,
    tokens_prompt INT DEFAULT 0,
    tokens_completion INT DEFAULT 0,
    tiempo_respuesta_ms INT DEFAULT 0,
    calificacion_usuario SMALLINT CHECK (calificacion_usuario BETWEEN 1 AND 5),
    fecha_consulta TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_historial_sesion ON HISTORIAL_CONSULTA(sesion_id);

-- Vista de compatibilidad para consultas directas del Chatbot
CREATE OR REPLACE VIEW CHAT_MENSAJES AS
SELECT 
    id_historial AS id_mensaje,
    id_user,
    pregunta,
    respuesta_ia,
    fecha_consulta AS fecha
FROM HISTORIAL_CONSULTA;

-- Asignar permisos si el rol lysandri_app existe
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO lysandri_app;
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO lysandri_app;
    END IF;
END $$;

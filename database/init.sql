CREATE EXTENSION IF NOT EXISTS "vector";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";


DO $$
DECLARE
    app_pwd text := coalesce(nullif(current_setting('lysandri.app_password', true), ''), 'lysandri_app_secret_2026');
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        EXECUTE format('CREATE ROLE lysandri_app WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', app_pwd);
    ELSE
        EXECUTE format('ALTER ROLE lysandri_app WITH PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', app_pwd);
    END IF;
END
$$;

GRANT CONNECT ON DATABASE lysandri_db TO lysandri_app;
GRANT USAGE ON SCHEMA public TO lysandri_app;


-- Tabla de Usuarios
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

-- Tabla de Programas Ejecutivos (Cursos sincronizados con Moodle)
CREATE TABLE IF NOT EXISTS PROGRAMA (
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

CREATE INDEX IF NOT EXISTS idx_programa_moodle_course ON PROGRAMA(moodle_course_id);
CREATE INDEX IF NOT EXISTS idx_programa_slug ON PROGRAMA(slug);

-- Tabla de Órdenes de Compra
CREATE TABLE IF NOT EXISTS ORDENES (
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
    fecha_pago TIMESTAMPTZ,
    tipo_comprobante_solicitado VARCHAR(10),
    numero_documento_cliente VARCHAR(15),
    nombre_facturacion VARCHAR(200)
);

CREATE INDEX IF NOT EXISTS idx_ordenes_user ON ORDENES(id_user);
CREATE INDEX IF NOT EXISTS idx_ordenes_stripe_session ON ORDENES(stripe_session_id);

-- Tipos ENUM para Facturación Electrónica (SUNAT)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_comprobante') THEN
        CREATE TYPE tipo_comprobante AS ENUM ('BOLETA', 'FACTURA');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_comprobante') THEN
        CREATE TYPE estado_comprobante AS ENUM ('EMITIDO', 'ANULADO');
    END IF;
END $$;

-- Tabla de Comprobantes de Pago
CREATE TABLE IF NOT EXISTS comprobante_pago (
    id_comprobante SERIAL PRIMARY KEY,
    id_orden BIGINT NOT NULL UNIQUE REFERENCES ORDENES(id_orden) ON DELETE RESTRICT,
    tipo tipo_comprobante NOT NULL,
    serie VARCHAR(5) NOT NULL,
    correlativo INT NOT NULL,
    tipo_documento_identidad VARCHAR(10) NOT NULL CHECK (tipo_documento_identidad IN ('DNI', 'RUC')),
    numero_documento_identidad VARCHAR(15) NOT NULL,
    razon_social_o_nombre VARCHAR(200) NOT NULL,
    monto_subtotal NUMERIC(10, 2) NOT NULL CHECK (monto_subtotal >= 0),
    monto_igv NUMERIC(10, 2) NOT NULL CHECK (monto_igv >= 0),
    monto_total NUMERIC(10, 2) NOT NULL CHECK (monto_total >= 0),
    moneda VARCHAR(3) DEFAULT 'PEN',
    fecha_emision TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado estado_comprobante DEFAULT 'EMITIDO',
    pdf_url VARCHAR(255),
    codigo_hash VARCHAR(64),
    monto_detraccion NUMERIC(10, 2) DEFAULT 0.00,
    porcentaje_detraccion NUMERIC(5, 2) DEFAULT 0.00,
    medio_pago VARCHAR(50) DEFAULT 'STRIPE_CHECKOUT',
    motivo_anulacion VARCHAR(255),
    fecha_anulacion TIMESTAMP,
    CONSTRAINT uq_comprobante_serie_correlativo UNIQUE (serie, correlativo)
);

CREATE INDEX IF NOT EXISTS idx_comprobante_fecha ON comprobante_pago(fecha_emision);
CREATE INDEX IF NOT EXISTS idx_comprobante_doc_cliente ON comprobante_pago(numero_documento_identidad);

-- Detalle de Órdenes
CREATE TABLE IF NOT EXISTS DETALLE_ORDENES (
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

-- Solicitudes de Información B2B / Corporativo
CREATE TABLE IF NOT EXISTS SOLICITUD_INFORMACION (
    id_solicitud BIGSERIAL PRIMARY KEY,
    id_programa BIGINT REFERENCES PROGRAMA(id_programa) ON DELETE SET NULL,
    nombre_completo VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    telefono VARCHAR(25),
    empresa VARCHAR(150),
    cargo VARCHAR(100),
    mensaje TEXT,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'CONTACTADO', 'DESCARTADO')),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_solicitud_email ON SOLICITUD_INFORMACION(email);
CREATE INDEX IF NOT EXISTS idx_solicitud_estado ON SOLICITUD_INFORMACION(estado);


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

-- Tabla para microservicio AI RAG (SentenceTransformer all-MiniLM-L6-v2 de 384 dimensiones)
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

-- Permisos
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO lysandri_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO lysandri_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO lysandri_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO lysandri_app;

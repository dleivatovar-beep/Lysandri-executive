CREATE EXTENSION IF NOT EXISTS "vector";

-- Usuario
CREATE TABLE IF NOT EXISTS usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nombre_completo VARCHAR(255) NOT NULL,
    rol VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_usuario_email ON usuario(email);

-- Sesión y Mensajes
CREATE TABLE IF NOT EXISTS sesion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    titulo VARCHAR(255) NOT NULL DEFAULT 'Nueva conversación',
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sesion_usuario_id ON sesion(usuario_id);

CREATE OR REPLACE FUNCTION actualizar_marca_tiempo()
RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sesion_actualizado_en ON sesion;
CREATE TRIGGER trg_sesion_actualizado_en
    BEFORE UPDATE ON sesion
    FOR EACH ROW
    EXECUTE FUNCTION actualizar_marca_tiempo();

CREATE TABLE IF NOT EXISTS mensaje (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id UUID NOT NULL REFERENCES sesion(id) ON DELETE CASCADE,
    tipo_remitente VARCHAR(20) NOT NULL CHECK (tipo_remitente IN ('USER', 'ASSISTANT', 'SYSTEM')),
    contenido TEXT NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mensaje_sesion_id_creado ON mensaje(sesion_id, creado_en ASC);

-- Documento y Embeddings RAG
CREATE TABLE IF NOT EXISTS documento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    titulo VARCHAR(255) NOT NULL,
    tipo_fuente VARCHAR(20) NOT NULL CHECK (tipo_fuente IN ('PDF', 'TXT', 'MANUAL')),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    documento_id UUID NOT NULL REFERENCES documento(id) ON DELETE CASCADE,
    fragmento_texto TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    numero_chunk INT NOT NULL DEFAULT 0,
    cantidad_tokens INT NOT NULL DEFAULT 0,
    tsv_chunk tsvector GENERATED ALWAYS AS (to_tsvector('spanish', fragmento_texto)) STORED,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_embeddings_documento_id ON embeddings(documento_id);
CREATE INDEX IF NOT EXISTS idx_embeddings_vector_hnsw ON embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_embeddings_tsv ON embeddings USING gin(tsv_chunk);

-- Dominio, Playbook y Artefacto
CREATE TABLE IF NOT EXISTS dominio (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(255) NOT NULL UNIQUE,
    descripcion TEXT,
    rol_objetivo VARCHAR(100),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS playbook (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dominio_id UUID NOT NULL REFERENCES dominio(id) ON DELETE CASCADE,
    titulo VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    resumen_ejecutivo TEXT,
    stack_arquitectura TEXT,
    tipo_playbook VARCHAR(50) NOT NULL,
    precio NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    esta_publicado BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_playbook_dominio_id ON playbook(dominio_id);

DROP TRIGGER IF EXISTS trg_playbook_actualizado_en ON playbook;
CREATE TRIGGER trg_playbook_actualizado_en
    BEFORE UPDATE ON playbook
    FOR EACH ROW
    EXECUTE FUNCTION actualizar_marca_tiempo();

CREATE TABLE IF NOT EXISTS artefacto (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    playbook_id UUID NOT NULL REFERENCES playbook(id) ON DELETE CASCADE,
    titulo VARCHAR(255) NOT NULL,
    tipo_artefacto VARCHAR(50) NOT NULL,
    url_recurso VARCHAR(500) NOT NULL,
    orden INT NOT NULL DEFAULT 0,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_artefacto_playbook_id ON artefacto(playbook_id);

-- Empresa, Órdenes, Detalle y Suscripciones B2B
CREATE TABLE IF NOT EXISTS empresa (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(255) NOT NULL,
    identificacion_fiscal VARCHAR(100) NOT NULL UNIQUE,
    industria VARCHAR(100),
    email_contacto VARCHAR(255) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ordenes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id UUID REFERENCES empresa(id) ON DELETE SET NULL,
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    monto_total NUMERIC(10, 2) NOT NULL,
    moneda VARCHAR(10) NOT NULL DEFAULT 'USD',
    estado VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'PAGADO', 'CANCELADO', 'REEMBOLSADO')),
    ref_pasarela_pago VARCHAR(255),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ordenes_empresa_id ON ordenes(empresa_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_usuario_id ON ordenes(usuario_id);

CREATE TABLE IF NOT EXISTS detalle (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    orden_id UUID NOT NULL REFERENCES ordenes(id) ON DELETE CASCADE,
    playbook_id UUID NOT NULL REFERENCES playbook(id),
    precio_unitario NUMERIC(10, 2) NOT NULL,
    cantidad_licencias INT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_detalle_orden_id ON detalle(orden_id);

CREATE TABLE IF NOT EXISTS suscripciones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empresa_id UUID NOT NULL REFERENCES empresa(id) ON DELETE CASCADE,
    nivel_plan VARCHAR(50) NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'EXPIRADO', 'CANCELADO', 'SUSPENDIDO')),
    fecha_inicio TIMESTAMPTZ NOT NULL,
    fecha_fin TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_suscripciones_empresa_id ON suscripciones(empresa_id);

-- Asesorías, Progreso, Reseñas y Auditoría
CREATE TABLE IF NOT EXISTS asesorias (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    playbook_id UUID REFERENCES playbook(id) ON DELETE SET NULL,
    fecha_programada TIMESTAMPTZ NOT NULL,
    url_reunion VARCHAR(500),
    estado VARCHAR(50) NOT NULL DEFAULT 'PROGRAMADA' CHECK (estado IN ('PROGRAMADA', 'COMPLETADA', 'CANCELADA', 'REPROGRAMADA')),
    notas_ejecutivas TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_asesorias_usuario_id ON asesorias(usuario_id);

CREATE TABLE IF NOT EXISTS progreso (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    playbook_id UUID NOT NULL REFERENCES playbook(id) ON DELETE CASCADE,
    artefacto_id UUID NOT NULL REFERENCES artefacto(id) ON DELETE CASCADE,
    esta_completado BOOLEAN NOT NULL DEFAULT FALSE,
    ultimo_acceso_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuario_artefacto UNIQUE (usuario_id, artefacto_id)
);

CREATE INDEX IF NOT EXISTS idx_progreso_usuario_id ON progreso(usuario_id);

CREATE TABLE IF NOT EXISTS reseñas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    playbook_id UUID NOT NULL REFERENCES playbook(id) ON DELETE CASCADE,
    calificacion INT NOT NULL CHECK (calificacion >= 1 AND calificacion <= 5),
    comentario TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuario_playbook UNIQUE (usuario_id, playbook_id)
);

CREATE INDEX IF NOT EXISTS idx_reseñas_playbook_id ON reseñas(playbook_id);

CREATE TABLE IF NOT EXISTS auditoria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    accion VARCHAR(100) NOT NULL,
    recurso_afectado VARCHAR(255) NOT NULL,
    direccion_ip VARCHAR(50),
    agente_usuario TEXT,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auditoria_usuario_id ON auditoria(usuario_id);

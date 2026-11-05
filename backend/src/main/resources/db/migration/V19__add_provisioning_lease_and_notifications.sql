-- Migración V19: Control de arrendamiento para aprovisionamiento exclusivo e idempotencia de notificaciones
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS estado_aprovisionamiento VARCHAR(30) DEFAULT 'PENDIENTE';
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS aprovisionamiento_lease_token VARCHAR(100);
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS aprovisionamiento_lease_expires_at TIMESTAMPTZ;
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS aprovisionamiento_intentos INTEGER DEFAULT 0;
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS aprovisionamiento_ultimo_error TEXT;

CREATE TABLE IF NOT EXISTS notificaciones_log (
    id_notificacion BIGSERIAL PRIMARY KEY,
    id_orden BIGINT REFERENCES ORDENES(id_orden) ON DELETE SET NULL,
    codigo_orden VARCHAR(36),
    destinatario VARCHAR(255) NOT NULL,
    tipo_notificacion VARCHAR(50) NOT NULL,
    clave_idempotencia VARCHAR(255) NOT NULL UNIQUE,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    intentos INTEGER NOT NULL DEFAULT 0,
    ultimo_error TEXT,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_envio TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_notif_log_clave ON notificaciones_log(clave_idempotencia);
CREATE INDEX IF NOT EXISTS idx_ordenes_estado_aprov ON ORDENES(estado_aprovisionamiento);

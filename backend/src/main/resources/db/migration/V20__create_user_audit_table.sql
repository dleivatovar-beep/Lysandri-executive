CREATE TABLE IF NOT EXISTS usuario_cambio_audit (
    id_auditoria BIGSERIAL PRIMARY KEY,
    id_usuario BIGINT NOT NULL,
    email VARCHAR(150) NOT NULL,
    accion VARCHAR(50) NOT NULL,
    rol_anterior VARCHAR(30),
    rol_nuevo VARCHAR(30),
    motivo VARCHAR(255),
    ejecutado_por VARCHAR(150) NOT NULL,
    fecha_hora TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_usuario_cambio_audit_id_usuario
    ON usuario_cambio_audit (id_usuario);

CREATE INDEX IF NOT EXISTS idx_usuario_cambio_audit_fecha_hora
    ON usuario_cambio_audit (fecha_hora DESC);

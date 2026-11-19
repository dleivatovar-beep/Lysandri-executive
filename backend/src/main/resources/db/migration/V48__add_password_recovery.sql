ALTER TABLE usuario ADD COLUMN version_credenciales INTEGER NOT NULL DEFAULT 0;

CREATE TABLE recuperacion_cuenta (
    id_recuperacion BIGSERIAL PRIMARY KEY,
    id_user BIGINT NOT NULL REFERENCES usuario(id_user),
    token_hash CHAR(64) NOT NULL UNIQUE,
    enlace_cifrado TEXT,
    fecha_solicitud TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_expiracion TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '30 minutes'),
    fecha_uso TIMESTAMPTZ,
    fecha_revocacion TIMESTAMPTZ,
    estado_correo VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado_correo IN ('PENDIENTE', 'ENVIANDO', 'ENVIADO', 'REVISAR'))
);
CREATE INDEX idx_recuperacion_usuario ON recuperacion_cuenta(id_user, fecha_solicitud DESC);
CREATE INDEX idx_recuperacion_correo ON recuperacion_cuenta(estado_correo, fecha_solicitud);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON recuperacion_cuenta TO lysandri_app;
        GRANT USAGE, SELECT ON SEQUENCE recuperacion_cuenta_id_recuperacion_seq TO lysandri_app;
    END IF;
END $$;

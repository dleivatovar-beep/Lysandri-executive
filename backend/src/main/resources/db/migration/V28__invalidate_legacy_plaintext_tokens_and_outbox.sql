-- Migración V28: Invalidación estricta de tokens legados en claro y creación
-- del mecanismo Outbox persistente y desacoplado para notificaciones de activación.

DO $$
BEGIN
    -- 1. Transición explícita para tokens antiguos almacenados en claro:
    -- Invalidar (revocar) cualquier token que no sea un hash hexadecimal SHA-256 de 64 caracteres.
    UPDATE usuario_activacion_token
    SET revocado = TRUE,
        fecha_revocacion = CURRENT_TIMESTAMP
    WHERE revocado = FALSE
      AND usado = FALSE
      AND (length(token) != 64 OR token !~ '^[0-9a-f]{64}$');

    -- 2. Crear tabla Outbox para desacoplar la entrega de notificaciones de activación
    -- de la sincronización con Moodle y facturación electrónica.
    CREATE TABLE IF NOT EXISTS outbox_notificacion_activacion (
        id_tarea BIGSERIAL PRIMARY KEY,
        id_orden BIGINT,
        codigo_orden VARCHAR(36),
        id_user BIGINT NOT NULL,
        id_token BIGINT NOT NULL,
        enlace_cifrado TEXT,
        destinatario VARCHAR(255) NOT NULL,
        nombre_destinatario VARCHAR(255),
        clave_idempotencia VARCHAR(255) NOT NULL UNIQUE,
        estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
        intentos INT NOT NULL DEFAULT 0,
        max_intentos INT NOT NULL DEFAULT 5,
        proximo_intento TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        bloqueado_hasta TIMESTAMPTZ,
        bloqueado_por VARCHAR(100),
        ultimo_error TEXT,
        fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        fecha_envio TIMESTAMPTZ,
        fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT fk_outbox_activacion_user FOREIGN KEY (id_user) REFERENCES usuario(id_user) ON DELETE CASCADE,
        CONSTRAINT fk_outbox_activacion_token FOREIGN KEY (id_token) REFERENCES usuario_activacion_token(id_token) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_outbox_activacion_estado_proximo
        ON outbox_notificacion_activacion(estado, proximo_intento);
    CREATE INDEX IF NOT EXISTS idx_outbox_activacion_clave
        ON outbox_notificacion_activacion(clave_idempotencia);
    CREATE INDEX IF NOT EXISTS idx_outbox_activacion_user
        ON outbox_notificacion_activacion(id_user);

    -- 3. Asignar privilegios de mínimo privilegio para lysandri_app
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE outbox_notificacion_activacion TO lysandri_app;
        GRANT USAGE, SELECT ON SEQUENCE outbox_notificacion_activacion_id_tarea_seq TO lysandri_app;
    END IF;
END $$;

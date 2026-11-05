-- Migración V27: Separación estricta entre activación pendiente y deshabilitación administrativa,
-- y soporte para revocación explícita de tokens de activación de un solo uso.

DO $$
BEGIN
    -- 1. Agregar columna bloqueado_admin en USUARIO
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='usuario' AND column_name='bloqueado_admin') THEN
        ALTER TABLE usuario ADD COLUMN bloqueado_admin BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    -- 2. Agregar columna pendiente_activacion en USUARIO
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='usuario' AND column_name='pendiente_activacion') THEN
        ALTER TABLE usuario ADD COLUMN pendiente_activacion BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    -- 3. Clasificación segura de cuentas existentes:
    -- Preservar la base original: No reinterpretar cuentas inactivas antiguas como pendientes de activación.
    -- Las cuentas inactivas históricas (activo = FALSE) que NO posean contraseña provisional invalidada por V26
    -- se clasifican como BLOQUEADAS ADMINISTRATIVAMENTE.
    UPDATE usuario
    SET bloqueado_admin = TRUE, pendiente_activacion = FALSE
    WHERE activo = FALSE
      AND (passw IS NULL OR NOT passw LIKE '$2a$10$INVALIDATED_PROVISIONAL_PASSWORD_HASH%');

    -- Las cuentas inactivas que provienen de contraseñas provisionales invalidadas por V26
    -- se clasifican legítimamente como PENDIENTES DE ACTIVACIÓN.
    UPDATE usuario
    SET pendiente_activacion = TRUE, bloqueado_admin = FALSE
    WHERE activo = FALSE
      AND passw LIKE '$2a$10$INVALIDATED_PROVISIONAL_PASSWORD_HASH%';

    -- 4. Soporte para revocación y auditoría de tokens en USUARIO_ACTIVACION_TOKEN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='usuario_activacion_token' AND column_name='revocado') THEN
        ALTER TABLE usuario_activacion_token ADD COLUMN revocado BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='usuario_activacion_token' AND column_name='fecha_revocacion') THEN
        ALTER TABLE usuario_activacion_token ADD COLUMN fecha_revocacion TIMESTAMPTZ;
    END IF;

    -- 5. Privilegios para lysandri_app
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE usuario TO lysandri_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE usuario_activacion_token TO lysandri_app;
    END IF;
END $$;

-- Migración V26: Gobernanza de precios comerciales, preservación de programas gratuitos
-- y tokens de activación de un solo uso para cuentas de invitados.

DO $$
BEGIN
    -- 1. Agregar columnas para gobernanza de precios en PROGRAMA
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='precio_aprobado') THEN
        ALTER TABLE programa ADD COLUMN precio_aprobado BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='es_gratuito') THEN
        ALTER TABLE programa ADD COLUMN es_gratuito BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    -- 2. Preservar programas gratuitos legítimos (precio = 0.00)
    UPDATE programa SET es_gratuito = TRUE, precio_aprobado = TRUE WHERE precio = 0.00;

    -- 3. Marcar precios aprobados para los programas sembrados oficiales (seeds 1, 2, 3, 4) si coinciden con catálogo oficial
    UPDATE programa SET precio_aprobado = TRUE WHERE id_programa IN (1, 2, 3, 4) AND precio IN (799.00, 899.00, 950.00, 750.00);

    -- 4. Los programas con precio provisional 499.00 introducido por V23 permanecen con precio_aprobado = FALSE
    -- para impedir su venta comercial hasta contar con la aprobación del catálogo oficial de precios.
    UPDATE programa SET precio_aprobado = FALSE WHERE precio = 499.00 AND id_programa NOT IN (1, 2, 3, 4);

    -- 5. Crear tabla para tokens de activación de cuentas de invitados (un solo uso)
    CREATE TABLE IF NOT EXISTS usuario_activacion_token (
        id_token BIGSERIAL PRIMARY KEY,
        id_user BIGINT NOT NULL,
        token VARCHAR(128) NOT NULL UNIQUE,
        fecha_expiracion TIMESTAMPTZ NOT NULL,
        usado BOOLEAN NOT NULL DEFAULT FALSE,
        fecha_uso TIMESTAMPTZ,
        fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
        CONSTRAINT fk_activacion_usuario FOREIGN KEY (id_user) REFERENCES usuario(id_user) ON DELETE CASCADE
    );

    CREATE INDEX IF NOT EXISTS idx_activacion_token ON usuario_activacion_token(token);
    CREATE INDEX IF NOT EXISTS idx_activacion_usuario ON usuario_activacion_token(id_user);

    -- 6. Invalidar hash de contraseña compartida de pruebas anterior en cuentas existentes
    UPDATE usuario
    SET passw = '$2a$10$INVALIDATED_PROVISIONAL_PASSWORD_HASH_SECURITY_RESET_' || substring(md5(random()::text) from 1 for 16)
    WHERE passw = '$2a$10$7EqJtq98hPqEX7fNZaFWoO.83.R3yR/Xh7b.d8Zt.m6qDk6H/H9G6';

    -- 7. Configuración de permisos de mínimo privilegio para lysandri_app
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE usuario_activacion_token TO lysandri_app;
        GRANT USAGE, SELECT ON SEQUENCE usuario_activacion_token_id_token_seq TO lysandri_app;
    END IF;
END $$;

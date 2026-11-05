DO $$
BEGIN
    IF to_regclass('public.usuario') IS NOT NULL THEN
        ALTER TABLE usuario ALTER COLUMN rol TYPE VARCHAR(30);
        ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_rol_check;
        ALTER TABLE usuario DROP CONSTRAINT IF EXISTS chk_usuario_rol;
        ALTER TABLE usuario ADD CONSTRAINT usuario_rol_check CHECK (rol IN (
            'CLIENTE', 'ESTUDIANTE', 'INSTRUCTOR', 'DOCENTE', 'TRABAJADOR', 'ADMIN',
            'CONTABILIDAD', 'FINANZAS', 'VENTAS', 'COORDINADOR_ACADEMICO', 'AUDITOR'
        ));
    END IF;
END $$;

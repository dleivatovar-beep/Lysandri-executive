-- Migración V11: Permitir roles ampliados para la plataforma ejecutiva en tabla USUARIO
DO $$
BEGIN
    -- Eliminar restricciones CHECK previas sobre la columna rol
    ALTER TABLE USUARIO DROP CONSTRAINT IF EXISTS usuario_rol_check;
    ALTER TABLE USUARIO DROP CONSTRAINT IF EXISTS chk_usuario_rol;

    -- Agregar restricción que permita CLIENTE, ESTUDIANTE, INSTRUCTOR, DOCENTE y ADMIN
    ALTER TABLE USUARIO ADD CONSTRAINT usuario_rol_check 
        CHECK (rol IN ('CLIENTE', 'ESTUDIANTE', 'INSTRUCTOR', 'DOCENTE', 'ADMIN'));
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END $$;

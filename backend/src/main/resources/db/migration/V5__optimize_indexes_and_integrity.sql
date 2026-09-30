-- Eliminar indices redundantes
DROP INDEX IF EXISTS idx_usuarios_email;
DROP INDEX IF EXISTS idx_leccion_id_programa;

-- Restricciones de unicidad compuestas
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_inscripcion_usuario_programa'
    ) THEN
        ALTER TABLE INSCRIPCIONES ADD CONSTRAINT uq_inscripcion_usuario_programa UNIQUE (id_user, id_programa);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_progreso_usuario_leccion'
    ) THEN
        ALTER TABLE PROGRESO_LECCION ADD CONSTRAINT uq_progreso_usuario_leccion UNIQUE (id_user, id_leccion);
    END IF;
END
$$;

-- Permisos para usuario de aplicacion
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT USAGE ON SCHEMA public TO lysandri_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO lysandri_app;
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO lysandri_app;
    END IF;
END
$$;

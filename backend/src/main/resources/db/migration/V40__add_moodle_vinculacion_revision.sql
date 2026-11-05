-- V40: Revision de vinculacion Moodle para control de concurrencia y solucion del problema ABA

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='moodle_vinculacion_revision') THEN
        ALTER TABLE programa ADD COLUMN moodle_vinculacion_revision BIGINT NOT NULL DEFAULT 1;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE programa TO lysandri_app;
    END IF;
END $$;


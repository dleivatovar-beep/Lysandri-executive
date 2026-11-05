-- V38: Campos de verificación de curso Moodle LMS y congelación de curso en detalle de órdenes

DO $$
BEGIN
    -- 1. Agregar columnas de verificación Moodle en tabla PROGRAMA
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='moodle_course_nombre') THEN
        ALTER TABLE programa ADD COLUMN moodle_course_nombre VARCHAR(255);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='moodle_verificado') THEN
        ALTER TABLE programa ADD COLUMN moodle_verificado BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='moodle_verificado_en') THEN
        ALTER TABLE programa ADD COLUMN moodle_verificado_en TIMESTAMPTZ;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='moodle_estado_verificacion') THEN
        ALTER TABLE programa ADD COLUMN moodle_estado_verificacion VARCHAR(50) NOT NULL DEFAULT 'PENDIENTE_VERIFICACION';
    END IF;

    -- Actualizar estado inicial: los que no tienen curso son SIN_VINCULACION
    UPDATE programa
    SET moodle_estado_verificacion = 'SIN_VINCULACION', moodle_verificado = FALSE
    WHERE moodle_course_id IS NULL;

    -- Los programas con moodle_course_id existente permanecen con moodle_verificado = FALSE y PENDIENTE_VERIFICACION
    UPDATE programa
    SET moodle_estado_verificacion = 'PENDIENTE_VERIFICACION', moodle_verificado = FALSE
    WHERE moodle_course_id IS NOT NULL AND moodle_verificado IS NOT TRUE;

    -- 2. Congelar moodle_course_id en DETALLE_ORDENES para evitar que un cambio posterior en el programa
    -- afecte el aprovisionamiento de órdenes ya emitidas
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='detalle_ordenes' AND column_name='moodle_course_id') THEN
        ALTER TABLE detalle_ordenes ADD COLUMN moodle_course_id BIGINT;
    END IF;

    -- Poblar históricamente moodle_course_id en detalle_ordenes desde programa para órdenes existentes
    UPDATE detalle_ordenes
    SET moodle_course_id = p.moodle_course_id
    FROM programa p
    WHERE detalle_ordenes.id_programa = p.id_programa AND detalle_ordenes.moodle_course_id IS NULL;

    -- 3. Permisos de mínimo privilegio para rol lysandri_app si existe
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE programa TO lysandri_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE detalle_ordenes TO lysandri_app;
    END IF;
END $$;

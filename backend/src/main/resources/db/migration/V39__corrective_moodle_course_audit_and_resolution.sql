-- V39: Migración correctiva para auditoría de vinculación Moodle y resolución administrativa de órdenes

DO $$
BEGIN
    -- 1. Columna de último intento de verificación en PROGRAMA
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='moodle_ultimo_intento_en') THEN
        ALTER TABLE programa ADD COLUMN moodle_ultimo_intento_en TIMESTAMPTZ;
    END IF;

    -- 2. Columnas de origen y auditoría de resolución en DETALLE_ORDENES
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='detalle_ordenes' AND column_name='origen_moodle_course_id') THEN
        ALTER TABLE detalle_ordenes ADD COLUMN origen_moodle_course_id VARCHAR(50);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='detalle_ordenes' AND column_name='moodle_resolucion_autorizada_por') THEN
        ALTER TABLE detalle_ordenes ADD COLUMN moodle_resolucion_autorizada_por VARCHAR(100);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='detalle_ordenes' AND column_name='moodle_resolucion_fecha') THEN
        ALTER TABLE detalle_ordenes ADD COLUMN moodle_resolucion_fecha TIMESTAMPTZ;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='detalle_ordenes' AND column_name='moodle_resolucion_motivo') THEN
        ALTER TABLE detalle_ordenes ADD COLUMN moodle_resolucion_motivo TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='detalle_ordenes' AND column_name='moodle_resolucion_evidencia') THEN
        ALTER TABLE detalle_ordenes ADD COLUMN moodle_resolucion_evidencia TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='detalle_ordenes' AND column_name='moodle_resolucion_curso_anterior') THEN
        ALTER TABLE detalle_ordenes ADD COLUMN moodle_resolucion_curso_anterior BIGINT;
    END IF;

    -- 3. TRATAMIENTO CORRECTIVO DE DESTINOS COPIADOS POR V38 PREVIA:
    -- A) Si la matrícula ya estaba completada históricamente (moodle_matriculado = TRUE),
    -- se preserva el registro pero se documenta explícitamente que fue reconstruido por V38.
    UPDATE detalle_ordenes
    SET origen_moodle_course_id = 'HISTORICO_RECONSTRUIDO_V38'
    WHERE moodle_matriculado = TRUE AND (origen_moodle_course_id IS NULL OR origen_moodle_course_id = '');

    -- B) Si la matrícula NO está completada (moodle_matriculado IS NOT TRUE / FALSE / NULL),
    -- no se puede certificar como original el curso que V38 copió desde el programa actual.
    -- Se guarda el valor asignado por V38 en moodle_resolucion_curso_anterior para trazabilidad,
    -- se limpia moodle_course_id a NULL para evitar matrículas no deseadas,
    -- y se marca expresamente como PENDIENTE_RESOLUCION_ADMINISTRATIVA.
    UPDATE detalle_ordenes
    SET moodle_resolucion_curso_anterior = moodle_course_id,
        moodle_course_id = NULL,
        origen_moodle_course_id = 'PENDIENTE_RESOLUCION_ADMINISTRATIVA'
    WHERE (moodle_matriculado IS NOT TRUE OR moodle_matriculado = FALSE)
      AND moodle_course_id IS NOT NULL
      AND (origen_moodle_course_id IS NULL OR origen_moodle_course_id = '');

    -- C) Cualquier detalle restante sin moodle_course_id se marca como PENDIENTE_RESOLUCION_ADMINISTRATIVA
    UPDATE detalle_ordenes
    SET origen_moodle_course_id = 'PENDIENTE_RESOLUCION_ADMINISTRATIVA'
    WHERE moodle_course_id IS NULL
      AND (origen_moodle_course_id IS NULL OR origen_moodle_course_id = '');

    -- 4. Permisos de mínimo privilegio para rol lysandri_app si existe
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE programa TO lysandri_app;
        GRANT SELECT, INSERT, UPDATE ON TABLE detalle_ordenes TO lysandri_app;
    END IF;
END $$;


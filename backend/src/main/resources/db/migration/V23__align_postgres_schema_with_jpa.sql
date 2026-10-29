-- Forward-only schema alignment for databases that already recorded V1-V22.
-- Historical migrations are immutable; this version repairs old widths/columns safely.
DO $$
DECLARE
    constraint_row RECORD;
BEGIN
    -- Remove FK constraints around key-width normalization; recreate below after all sides match.
    FOR constraint_row IN
        SELECT conrelid::regclass AS table_name, conname
        FROM pg_constraint
                WHERE contype = 'f'
                    AND conrelid IN (
                        to_regclass('public.ordenes'), to_regclass('public.detalle_ordenes'),
                        to_regclass('public.solicitud_informacion'), to_regclass('public.lote_matricula_corporativa'),
                        to_regclass('public.documento'), to_regclass('public.documento_chunk'),
                        to_regclass('public.historial_consulta'), to_regclass('public.comprobante_pago'),
                        to_regclass('public.stripe_processed_event')
                    )
                    AND confrelid IN (
                            to_regclass('public.usuario'),
                            to_regclass('public.programa'),
                            to_regclass('public.ordenes'),
                            to_regclass('public.solicitud_informacion'),
                            to_regclass('public.documento')
                    )
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', constraint_row.table_name, constraint_row.conname);
    END LOOP;
END $$;

ALTER TABLE usuario ALTER COLUMN rol TYPE VARCHAR(30);
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_rol_check;
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS chk_usuario_rol;
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_check CHECK (rol IN (
    'CLIENTE', 'ESTUDIANTE', 'INSTRUCTOR', 'DOCENTE', 'TRABAJADOR', 'ADMIN',
    'CONTABILIDAD', 'FINANZAS', 'VENTAS', 'COORDINADOR_ACADEMICO', 'AUDITOR'
));

ALTER TABLE solicitud_informacion ADD COLUMN IF NOT EXISTS empresa VARCHAR(150);
ALTER TABLE solicitud_informacion ADD COLUMN IF NOT EXISTS cargo VARCHAR(100);
ALTER TABLE solicitud_informacion ALTER COLUMN id_solicitud TYPE BIGINT;
ALTER TABLE solicitud_informacion ALTER COLUMN id_programa TYPE BIGINT;

ALTER TABLE ordenes ALTER COLUMN id_orden TYPE BIGINT;
ALTER TABLE ordenes ALTER COLUMN id_user TYPE BIGINT;
ALTER TABLE detalle_ordenes ALTER COLUMN id_detalle TYPE BIGINT;
ALTER TABLE detalle_ordenes ALTER COLUMN id_orden TYPE BIGINT;
ALTER TABLE detalle_ordenes ALTER COLUMN id_programa TYPE BIGINT;
ALTER TABLE programa ALTER COLUMN id_programa TYPE BIGINT;

ALTER TABLE comprobante_pago ALTER COLUMN id_comprobante TYPE BIGINT;
ALTER TABLE comprobante_pago ALTER COLUMN estado DROP DEFAULT;
ALTER TABLE comprobante_pago ALTER COLUMN tipo TYPE VARCHAR(10) USING tipo::text;
ALTER TABLE comprobante_pago ALTER COLUMN estado TYPE VARCHAR(10) USING estado::text;
ALTER TABLE comprobante_pago ALTER COLUMN estado SET DEFAULT 'EMITIDO';
ALTER TABLE comprobante_pago ADD CONSTRAINT chk_comprobante_tipo CHECK (tipo IN ('BOLETA', 'FACTURA'));
ALTER TABLE comprobante_pago ADD CONSTRAINT chk_comprobante_estado CHECK (estado IN ('EMITIDO', 'ANULADO'));

ALTER TABLE lote_matricula_corporativa ALTER COLUMN id_solicitud TYPE BIGINT;
ALTER TABLE lote_matricula_corporativa ALTER COLUMN id_programa TYPE BIGINT;
ALTER TABLE historial_consulta ALTER COLUMN calificacion_usuario TYPE INTEGER;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='titulo_programa')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='titulo') THEN
        ALTER TABLE programa RENAME COLUMN titulo_programa TO titulo;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='level')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='programa' AND column_name='nivel') THEN
        ALTER TABLE programa RENAME COLUMN level TO nivel;
    END IF;
    -- Retain historical instructor references; modern programs may have no legacy instructor.
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
               AND table_name='programa' AND column_name='id_instructor_dni') THEN
        ALTER TABLE programa ALTER COLUMN id_instructor_dni DROP NOT NULL;
    END IF;
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS titulo VARCHAR(200);
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS precio DECIMAL(10, 2) DEFAULT 0.00;
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS moneda VARCHAR(3) DEFAULT 'USD';
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS moodle_course_id BIGINT;
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS slug VARCHAR(220);
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS subtitulo VARCHAR(300);
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS descripcion_corta TEXT;
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS descripcion_detallada TEXT;
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS imagen_portada_url VARCHAR(500);
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS syllabus_url VARCHAR(500);
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS instructor_nombre VARCHAR(150);
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS instructor_bio TEXT;
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS duracion_horas INT DEFAULT 0;
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS activo BOOLEAN DEFAULT TRUE;
    ALTER TABLE programa ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;
    UPDATE programa SET titulo=COALESCE(titulo, 'Programa '||id_programa);
    UPDATE programa SET precio=499.00 WHERE precio IS NULL OR precio=0;
    UPDATE programa SET moneda='USD' WHERE moneda IS NULL;
    UPDATE programa SET moodle_course_id=id_programa WHERE moodle_course_id IS NULL;
    UPDATE programa SET slug='programa-'||id_programa WHERE slug IS NULL;
    ALTER TABLE programa ALTER COLUMN titulo SET NOT NULL;
    ALTER TABLE programa ALTER COLUMN precio SET NOT NULL;
    ALTER TABLE programa ALTER COLUMN moneda SET NOT NULL;
    ALTER TABLE programa ALTER COLUMN moodle_course_id SET NOT NULL;
    ALTER TABLE programa ALTER COLUMN slug SET NOT NULL;
    CREATE UNIQUE INDEX IF NOT EXISTS uq_programa_moodle_course_id ON programa(moodle_course_id);
    CREATE UNIQUE INDEX IF NOT EXISTS uq_programa_slug ON programa(slug);
END $$;

DO $$
BEGIN
    IF to_regclass('public.lysandri_v7_programa_backup') IS NOT NULL THEN
        INSERT INTO programa (
            id_programa, moodle_course_id, titulo, slug, subtitulo,
            descripcion_corta, descripcion_detallada, precio, moneda,
            instructor_nombre, instructor_bio, nivel, duracion_horas,
            activo, fecha_creacion
        )
        SELECT
            id_programa, id_programa, titulo_programa,
            'programa-' || id_programa,
            NULL,
            NULL,
            NULL,
            499.00,
            'USD',
            NULL,
            NULL,
            level,
            0,
            TRUE,
            CURRENT_TIMESTAMP
        FROM lysandri_v7_programa_backup
        ON CONFLICT (id_programa) DO NOTHING;

        UPDATE programa target
        SET descripcion_corta = legacy.metodologia,
            descripcion_detallada = legacy.requisitos,
            nivel = legacy.level,
            duracion_horas = COALESCE(NULLIF(regexp_replace(legacy.duracion_programa, '\D', '', 'g'), '')::INTEGER, 0)
        FROM lysandri_v7_programa_backup legacy
        WHERE target.id_programa = legacy.id_programa;

        PERFORM setval(pg_get_serial_sequence('programa', 'id_programa'),
                       GREATEST(COALESCE((SELECT MAX(id_programa) FROM programa), 1), 1),
                       EXISTS (SELECT 1 FROM programa));
    END IF;
END $$;

ALTER TABLE documento ALTER COLUMN id_documento TYPE BIGINT;
ALTER TABLE documento ALTER COLUMN id_programa TYPE BIGINT;
ALTER TABLE documento_chunk ALTER COLUMN id_chunk TYPE BIGINT;
ALTER TABLE documento_chunk ALTER COLUMN id_documento TYPE BIGINT;
ALTER TABLE documento_chunk ALTER COLUMN id_programa TYPE BIGINT;
DROP VIEW IF EXISTS chat_mensajes;
ALTER TABLE historial_consulta ALTER COLUMN id_historial TYPE BIGINT;
CREATE OR REPLACE VIEW chat_mensajes AS
SELECT id_historial AS id_mensaje, id_user, pregunta, respuesta_ia, fecha_consulta AS fecha
FROM historial_consulta;

ALTER TABLE ordenes ADD CONSTRAINT fk_ordenes_usuario
    FOREIGN KEY (id_user) REFERENCES usuario(id_user) ON DELETE RESTRICT;
ALTER TABLE detalle_ordenes ADD CONSTRAINT fk_detalle_ordenes_orden
    FOREIGN KEY (id_orden) REFERENCES ordenes(id_orden) ON DELETE CASCADE;
ALTER TABLE detalle_ordenes ADD CONSTRAINT fk_detalle_ordenes_programa
    FOREIGN KEY (id_programa) REFERENCES programa(id_programa) ON DELETE RESTRICT;
ALTER TABLE solicitud_informacion ADD CONSTRAINT fk_solicitud_programa
    FOREIGN KEY (id_programa) REFERENCES programa(id_programa) ON DELETE SET NULL;
ALTER TABLE lote_matricula_corporativa ADD CONSTRAINT fk_lote_solicitud
    FOREIGN KEY (id_solicitud) REFERENCES solicitud_informacion(id_solicitud) ON DELETE SET NULL;
ALTER TABLE lote_matricula_corporativa ADD CONSTRAINT fk_lote_programa
    FOREIGN KEY (id_programa) REFERENCES programa(id_programa) ON DELETE RESTRICT;
ALTER TABLE documento ADD CONSTRAINT fk_documento_programa
    FOREIGN KEY (id_programa) REFERENCES programa(id_programa) ON DELETE CASCADE;
ALTER TABLE documento_chunk ADD CONSTRAINT fk_documento_chunk_documento
    FOREIGN KEY (id_documento) REFERENCES documento(id_documento) ON DELETE CASCADE;
ALTER TABLE documento_chunk ADD CONSTRAINT fk_documento_chunk_programa
    FOREIGN KEY (id_programa) REFERENCES programa(id_programa) ON DELETE CASCADE;
ALTER TABLE historial_consulta ADD CONSTRAINT fk_historial_consulta_usuario
    FOREIGN KEY (id_user) REFERENCES usuario(id_user) ON DELETE SET NULL;
ALTER TABLE historial_consulta ADD CONSTRAINT fk_historial_consulta_programa
    FOREIGN KEY (id_programa) REFERENCES programa(id_programa) ON DELETE SET NULL;
ALTER TABLE comprobante_pago ADD CONSTRAINT fk_comprobante_pago_orden
    FOREIGN KEY (id_orden) REFERENCES ordenes(id_orden) ON DELETE RESTRICT;
ALTER TABLE stripe_processed_event ADD CONSTRAINT fk_stripe_event_orden
    FOREIGN KEY (id_orden) REFERENCES ordenes(id_orden) ON DELETE SET NULL;

-- Re-seed identities after widening legacy serial identifiers, preserving all existing ids.

CREATE UNIQUE INDEX IF NOT EXISTS uq_ordenes_codigo_orden ON ordenes(codigo_orden);

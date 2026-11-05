-- Migración V25: Aplicación de la política estricta de mínimo privilegio para el usuario lysandri_app

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        -- 1. Asegurar atributos de rol seguros (sin privilegios administrativos)
        ALTER ROLE lysandri_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;

        -- 2. Revocar todos los privilegios previos en el esquema public, tablas y secuencias
        REVOKE ALL ON SCHEMA public FROM lysandri_app;
        REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM lysandri_app;
        REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM lysandri_app;

        -- 3. Limpiar privilegios por defecto acumulados previamente
        ALTER DEFAULT PRIVILEGES FOR ROLE lysandri_admin IN SCHEMA public REVOKE ALL ON TABLES FROM lysandri_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE lysandri_admin IN SCHEMA public REVOKE ALL ON SEQUENCES FROM lysandri_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE lysandri_admin IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM lysandri_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE lysandri_admin IN SCHEMA public REVOKE ALL ON TYPES FROM lysandri_app;

        -- 4. Esquema: USAGE únicamente (sin CREATE: lysandri_app no puede ejecutar DDL)
        GRANT USAGE ON SCHEMA public TO lysandri_app;
        REVOKE CREATE ON SCHEMA public FROM PUBLIC;
        REVOKE CREATE ON SCHEMA public FROM lysandri_app;

        -- 5. Tablas Operacionales de la Aplicación: SELECT, INSERT, UPDATE, DELETE (sin TRUNCATE)
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
            usuario,
            programa,
            ordenes,
            detalle_ordenes,
            solicitud_informacion,
            comprobante_pago,
            comprobante_correlativo,
            lote_matricula_corporativa,
            stripe_processed_event,
            notificaciones_log,
            documento,
            documento_chunk,
            documento_chunks,
            historial_consulta
        TO lysandri_app;

        -- 6. Tablas Históricas, Académicas y Respaldo: SOLO LECTURA (SELECT)
        GRANT SELECT ON TABLE
            usuarios,
            instructor,
            chat_mensajes_legacy,
            bloque,
            modulo,
            leccion,
            actividad_bloque,
            inscripciones,
            suscripciones,
            progreso_leccion
        TO lysandri_app;

        IF to_regclass('public.lysandri_v7_programa_backup') IS NOT NULL THEN
            GRANT SELECT ON TABLE lysandri_v7_programa_backup TO lysandri_app;
        END IF;

        -- 7. Historial de Migraciones Flyway: SOLO LECTURA (SELECT)
        GRANT SELECT ON TABLE flyway_schema_history TO lysandri_app;

        -- 8. Vistas: SOLO LECTURA (SELECT)
        GRANT SELECT ON TABLE chat_mensajes TO lysandri_app;

        -- 9. Tablas de Auditoría: APÉNDICE INMUTABLE (SELECT e INSERT únicamente)
        -- Queda terminantemente denegado UPDATE, DELETE y TRUNCATE para lysandri_app
        GRANT SELECT, INSERT ON TABLE auditoria_contable TO lysandri_app;
        GRANT SELECT, INSERT ON TABLE usuario_cambio_audit TO lysandri_app;

        -- 10. Prohibición expresa de TRUNCATE en todas las tablas
        REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM lysandri_app;

        -- 11. Secuencias: USAGE y SELECT únicamente (sin UPDATE para impedir setval arbitrarios)
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO lysandri_app;

        -- 12. Configurar privilegios por defecto seguros para futuras tablas creadas por lysandri_admin
        ALTER DEFAULT PRIVILEGES FOR ROLE lysandri_admin IN SCHEMA public GRANT SELECT ON TABLES TO lysandri_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE lysandri_admin IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO lysandri_app;
    END IF;
END $$;

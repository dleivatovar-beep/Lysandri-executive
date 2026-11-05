package com.lysandri.adapters;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyV7UpgradeIntegrationTest {

    @Test
    void migratesRepresentativeV7DataForwardWithoutLosingUsersProgramsOrdersOrDetails() {
        try (var database = PostgresTestDatabase.newContainer()) {
        database.start();

        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(database.getJdbcUrl());
        dataSource.setUser(database.getUsername());
        dataSource.setPassword(database.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7"))
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .outOfOrder(false)
                .load()
                .migrate();

        jdbc.update("""
                INSERT INTO usuarios (id_user, nombres, apellidos, email, passw, telefono, rol)
                VALUES (701, 'Legacy', 'Student', 'legacy.student@integration.invalid', 'hash', '999000111', 'ESTUDIANTE')
                """);
        jdbc.update("""
                INSERT INTO instructor (id_instructor_dni, id_user, especialidad, direccion_instructor)
                VALUES ('LEGACY-DNI-701', 701, 'Administración', 'Legacy address')
                """);
        jdbc.update("""
                INSERT INTO programa (id_instructor_dni, titulo_programa, duracion_programa, tipo_programa, level,
                                     fecha_inicio_global, fecha_final_global, requisitos, metodologia)
                VALUES ('LEGACY-DNI-701', 'Programa V7 conservado', '40 horas', 'DIPLOMADO', 'EJECUTIVO',
                        CURRENT_DATE, CURRENT_DATE + 90, 'Requisito legacy', 'Metodología legacy')
                """);
        jdbc.update("""
                INSERT INTO ordenes (id_user, fecha_orden, estado_order, total, metodo_pago, direccion_envio)
                VALUES (701, CURRENT_TIMESTAMP, 'PAGADO', 499.00, 'TEST', 'Legacy address')
                """);
        jdbc.update("""
                INSERT INTO detalle_ordenes (id_ordenes, id_programa, precio_unitario, cantidad, subtotal)
                VALUES (1, 1, 499.00, 1, 499.00)
                """);

        // Populate every legacy academic domain, not only commerce tables.
        jdbc.update("INSERT INTO bloque (id_programa,titulo,orden) VALUES (1,'Bloque conservado',1)");
        jdbc.update("INSERT INTO modulo (id_bloque,titulo,orden) VALUES (1,'Modulo conservado',1)");
        jdbc.update("INSERT INTO leccion (id_programa,id_modulo,titulo_leccion,orden) VALUES (1,1,'Leccion conservada',1)");
        jdbc.update("INSERT INTO actividad_bloque (id_bloque,titulo,tipo_actividad,orden) VALUES (1,'Reto conservado','RETO',1)");
        jdbc.update("INSERT INTO inscripciones (id_user,id_programa,estatus,porcentaje_progreso) VALUES (701,1,'ACTIVO',33.33)");
        jdbc.update("INSERT INTO suscripciones (id_user,tipo_plan,fecha_inicio,estado) VALUES (701,'LEGACY',CURRENT_DATE,'ACTIVO')");
        jdbc.update("INSERT INTO progreso_leccion (id_user,id_leccion,completado) VALUES (701,1,true)");
        jdbc.update("INSERT INTO chat_mensajes (id_user,pregunta,respuesta_ia) VALUES (701,'Pregunta conservada','Respuesta conservada')");
        jdbc.update("INSERT INTO documento_chunks (documento_origen,numero_pagina,contenido) VALUES ('legacy.pdf',1,'Texto conservado')");
        jdbc.update("INSERT INTO usuarios (id_user,nombres,apellidos,email,passw,rol) VALUES (702,'Existing','Identity','danny@lysandri.com','existing-hash','ESTUDIANTE')");
        var tables = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename", String.class);
        var columnsByTable = new java.util.LinkedHashMap<String, java.util.List<String>>();
        var rowsBefore = new java.util.LinkedHashMap<String, java.util.List<String>>();
        for (String table : tables) {
            var columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=? ORDER BY ordinal_position", String.class, table);
            columnsByTable.put(table, columns);
            rowsBefore.put(table, jdbc.queryForList("SELECT to_jsonb(t)::text FROM public." + table + " t ORDER BY to_jsonb(t)::text", String.class));
        }

        Flyway upgraded = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .outOfOrder(false)
                .validateOnMigrate(true)
                .load();
        upgraded.migrate();
        upgraded.validate();
        // Compare every original field, including legacy passwords, roles, dates and IDs;
        // values remain in memory and are not printed in reports on mismatch.
        for (String table : tables) {
            String target = table.equals("chat_mensajes") ? "chat_mensajes_legacy" : table;
            var pairs = new java.util.ArrayList<String>();
            for (String column : columnsByTable.get(table)) {
                String current = column;
                if (table.equals("programa") && column.equals("titulo_programa")) current = "titulo";
                if (table.equals("programa") && column.equals("level")) current = "nivel";
                if (table.equals("ordenes") && column.equals("id_ordenes")) current = "id_orden";
                if (table.equals("ordenes") && column.equals("estado_order")) current = "estado_orden";
                if (table.equals("detalle_ordenes") && column.equals("id_detalle_ordenes")) current = "id_detalle";
                if (table.equals("detalle_ordenes") && column.equals("id_ordenes")) current = "id_orden";
                pairs.add("'" + column + "',t." + current);
            }
            String projection = "jsonb_build_object(" + String.join(",", pairs) + ")::text";
            var actual = jdbc.queryForList("SELECT " + projection + " FROM public." + target + " t ORDER BY " + projection, String.class);
            org.junit.jupiter.api.Assertions.assertTrue(rowsBefore.get(table).equals(actual), "Original fields changed in " + table);
        }
        assertEquals("ESTUDIANTE", jdbc.queryForObject("SELECT rol FROM usuario WHERE id_user=701", String.class));
        assertEquals("Existing", jdbc.queryForObject("SELECT nombres FROM usuario WHERE id_user=702", String.class));
        assertEquals("ESTUDIANTE", jdbc.queryForObject("SELECT rol FROM usuario WHERE id_user=702", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conrelid='bloque'::regclass AND contype='f'", Integer.class));
        org.junit.jupiter.api.Assertions.assertTrue(jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('ordenes','id_orden'))", Long.class) > 1);
        org.junit.jupiter.api.Assertions.assertTrue(jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('detalle_ordenes','id_detalle'))", Long.class) > 1);


        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE id_user=701 AND email='legacy.student@integration.invalid'",
                Integer.class));
        Long programId = jdbc.queryForObject(
                "SELECT id_programa FROM programa WHERE titulo='Programa V7 conservado'", Long.class);
        assertNotNull(programId);
        assertEquals("Metodología legacy", jdbc.queryForObject(
                "SELECT descripcion_corta FROM programa WHERE id_programa=?", String.class, programId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM ordenes WHERE id_orden=1 AND codigo_orden='LEGACY-1' AND total=499.00",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM detalle_ordenes WHERE id_orden=1 AND id_programa=? AND subtotal=499.00",
                Integer.class, programId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version='24' AND success", Integer.class));
        }
    }
}

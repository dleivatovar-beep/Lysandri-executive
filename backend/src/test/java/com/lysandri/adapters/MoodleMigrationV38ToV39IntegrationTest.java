package com.lysandri.adapters;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración de Flyway en contenedor desechable PostgreSQL para verificar:
 * 1. La migración limpia desde V1 hasta la versión final (V40) con validación de checksums (código 0).
 * 2. La ruta representativa de actualización V38 -> V39 -> V40 con preservación de datos históricos.
 *
 * NOTA DE AUDITORÍA HISTÓRICA:
 * El script V38 verificado en este suite proviene del archivo 'V38__moodle_course_verification_and_order_snapshot.sql'
 * presente en el repositorio. Tras inspección exhaustiva de 'git log --all', ramas remotas, volcados ('database/backup_verified.dump'
 * que solo alcanza V7) y paquetes de evidencias previas, no se halló una versión externa o preexistente de V38.
 * Por consiguiente, esta prueba garantiza la compatibilidad del esquema y los datos representativos del código actual,
 * quedando documentado que la contrastación con bases que hayan aplicado una versión externa distinta de V38 requiere
 * el suministro del artefacto SQL y checksum de dicha base.
 */
class MoodleMigrationV38ToV39IntegrationTest {

    @Test
    @DisplayName("Demostración 1: Migración desde cero hasta la versión final en PostgreSQL desechable")
    void testMigracionDesdeCeroHastaVersionFinal() {
        try (var database = PostgresTestDatabase.newContainer()) {
            database.start();

            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setURL(database.getJdbcUrl());
            dataSource.setUser(database.getUsername());
            dataSource.setPassword(database.getPassword());

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("0"))
                    .outOfOrder(false)
                    .validateOnMigrate(true)
                    .load();

            var result = flyway.migrate();
            assertTrue(result.migrationsExecuted > 0, "Deben ejecutarse migraciones desde cero");

            // flyway validate con código 0 (sin excepciones ni discrepancias)
            assertDoesNotThrow(flyway::validate, "flyway validate debe retornar 0 errores");

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Integer v40Count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version='40' AND success = TRUE", Integer.class);
            assertEquals(1, v40Count, "La versión 40 debe estar aplicada exitosamente");
        }
    }

    @Test
    @DisplayName("Demostración 2: Actualización V38 -> V39/V40 con datos históricos representativos")
    void testActualizacionV38HaciaV39ConDatosHistoricos() {
        try (var database = PostgresTestDatabase.newContainer()) {
            database.start();

            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setURL(database.getJdbcUrl());
            dataSource.setUser(database.getUsername());
            dataSource.setPassword(database.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            // 1. Migrar hasta la versión V38 del workspace
            Flyway flywayV38 = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("38"))
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("0"))
                    .outOfOrder(false)
                    .validateOnMigrate(true)
                    .load();

            flywayV38.migrate();
            assertDoesNotThrow(flywayV38::validate);

            // 2. Sembrar datos históricos representativos en V38
            jdbc.update("""
                    INSERT INTO usuario (id_user, email, passw, nombres, apellidos, rol, activo)
                    VALUES (9001, 'estudiante.historico@test.pe', 'hash', 'Estudiante', 'Historico', 'ESTUDIANTE', TRUE)
                    """);

            jdbc.update("""
                    INSERT INTO programa (id_programa, titulo, slug, precio, moneda, activo, precio_aprobado, moodle_course_id, moodle_verificado, moodle_estado_verificacion)
                    VALUES (901, 'Programa Base V38', 'prog-base-v38', 250.00, 'PEN', TRUE, TRUE, 101, FALSE, 'PENDIENTE_VERIFICACION')
                    """);

            // Caso A: Orden Pagada y Matriculada (moodle_matriculado = TRUE, moodle_course_id = 101)
            jdbc.update("""
                    INSERT INTO ordenes (id_orden, id_user, codigo_orden, estado_orden, estado_aprovisionamiento, total, moneda, metodo_pago, moodle_matricula_sincronizada)
                    VALUES (90001, 9001, 'ORD-V38-MATRICULADA', 'PAGADO', 'COMPLETADO', 250.00, 'PEN', 'STRIPE', TRUE)
                    """);
            jdbc.update("""
                    INSERT INTO detalle_ordenes (id_detalle, id_orden, id_programa, precio_unitario, cantidad, subtotal, moodle_matriculado, moodle_course_id)
                    VALUES (900001, 90001, 901, 250.00, 1, 250.00, TRUE, 101)
                    """);

            // Caso B: Orden Pendiente con curso copiado por V38 (moodle_matriculado = FALSE, moodle_course_id = 101)
            jdbc.update("""
                    INSERT INTO ordenes (id_orden, id_user, codigo_orden, estado_orden, estado_aprovisionamiento, total, moneda, metodo_pago, moodle_matricula_sincronizada)
                    VALUES (90002, 9001, 'ORD-V38-COPIADA-PENDIENTE', 'PAGADO', 'PENDIENTE', 250.00, 'PEN', 'STRIPE', FALSE)
                    """);
            jdbc.update("""
                    INSERT INTO detalle_ordenes (id_detalle, id_orden, id_programa, precio_unitario, cantidad, subtotal, moodle_matriculado, moodle_course_id)
                    VALUES (900002, 90002, 901, 250.00, 1, 250.00, FALSE, 101)
                    """);

            // Caso C: Orden con curso nulo (moodle_matriculado = FALSE, moodle_course_id = NULL)
            jdbc.update("""
                    INSERT INTO ordenes (id_orden, id_user, codigo_orden, estado_orden, estado_aprovisionamiento, total, moneda, metodo_pago, moodle_matricula_sincronizada)
                    VALUES (90003, 9001, 'ORD-V38-SIN-CURSO', 'PAGADO', 'PENDIENTE', 250.00, 'PEN', 'STRIPE', FALSE)
                    """);
            jdbc.update("""
                    INSERT INTO detalle_ordenes (id_detalle, id_orden, id_programa, precio_unitario, cantidad, subtotal, moodle_matriculado, moodle_course_id)
                    VALUES (900003, 90003, 901, 250.00, 1, 250.00, FALSE, NULL)
                    """);

            // 3. Aplicar actualización hacia V39 y V40
            Flyway flywayUpgrade = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("0"))
                    .outOfOrder(false)
                    .validateOnMigrate(true)
                    .load();

            flywayUpgrade.migrate();

            // 4. Salida limpia de flyway validate con código 0
            assertDoesNotThrow(flywayUpgrade::validate, "flyway validate debe retornar sin excepciones (código 0)");

            // 5. Verificación de reglas de preservación y seguridad:
            // A) Conservación de matrícula completada
            Map<String, Object> filaMatriculada = jdbc.queryForMap(
                    "SELECT moodle_course_id, origen_moodle_course_id, moodle_matriculado FROM detalle_ordenes WHERE id_detalle = 900001");
            assertEquals(101L, ((Number) filaMatriculada.get("moodle_course_id")).longValue(),
                    "La matrícula completada debe conservar su curso Moodle 101");
            assertEquals("HISTORICO_RECONSTRUIDO_V38", filaMatriculada.get("origen_moodle_course_id"),
                    "El origen debe documentar la reconstrucción histórica de V38");
            assertEquals(Boolean.TRUE, filaMatriculada.get("moodle_matriculado"));

            // B) No aprovisionamiento automático de órdenes ambiguas (curso limpiado a NULL)
            Map<String, Object> filaCopiada = jdbc.queryForMap(
                    "SELECT moodle_course_id, moodle_resolucion_curso_anterior, origen_moodle_course_id, moodle_matriculado FROM detalle_ordenes WHERE id_detalle = 900002");
            assertNull(filaCopiada.get("moodle_course_id"),
                    "Para órdenes no matriculadas, el curso copiado por V38 debe limpiarse para evitar aprovisionamiento automático no deseado");
            assertEquals(101L, ((Number) filaCopiada.get("moodle_resolucion_curso_anterior")).longValue(),
                    "El curso copiado previo se preserva en moodle_resolucion_curso_anterior para trazabilidad y auditoría");
            assertEquals("PENDIENTE_RESOLUCION_ADMINISTRATIVA", filaCopiada.get("origen_moodle_course_id"),
                    "Debe requerir expresamente resolución administrativa");

            // C) Órdenes con curso nulo marcadas para resolución administrativa
            Map<String, Object> filaSinCurso = jdbc.queryForMap(
                    "SELECT moodle_course_id, origen_moodle_course_id, moodle_matriculado FROM detalle_ordenes WHERE id_detalle = 900003");
            assertNull(filaSinCurso.get("moodle_course_id"));
            assertEquals("PENDIENTE_RESOLUCION_ADMINISTRATIVA", filaSinCurso.get("origen_moodle_course_id"));

            // D) Verificación de V40: Columna moodle_vinculacion_revision agregada en programa
            Long rev = jdbc.queryForObject(
                    "SELECT moodle_vinculacion_revision FROM programa WHERE id_programa = 901", Long.class);
            assertEquals(1L, rev, "V40 debe haber inicializado moodle_vinculacion_revision en 1");
        }
    }
}

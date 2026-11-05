package com.lysandri.adapters;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de restauracion aislada, comparacion completa de datos (100% preservacion)
 * y migracion desde V7 hasta la ultima version (V25).
 *
 * Utiliza un contenedor y volumen Docker unicos y temporales por ejecucion,
 * verifica la integridad SHA-256 del respaldo antes de restaurar,
 * y no modifica ningun contenedor ni clon persistente preexistente.
 */
@Tag("real-clone")
@Tag("restoration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IsolatedRestorationFullComparisonIntegrationTest {

    private IsolatedDockerRestorationEnvironment restorationEnv;
    private PGSimpleDataSource adminDataSource;
    private PGSimpleDataSource appDataSource;
    private JdbcTemplate jdbcAdmin;

    // Snapshot en memoria de los datos previos a la migracion (capturados en V7)
    private final Map<String, List<String>> columnsBeforeMigration = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> rowsBeforeMigration = new LinkedHashMap<>();

    @BeforeAll
    void setupIsolatedContainerAndRestore() throws Exception {
        restorationEnv = new IsolatedDockerRestorationEnvironment();
        restorationEnv.startAndRestore();

        adminDataSource = new PGSimpleDataSource();
        adminDataSource.setURL(restorationEnv.getJdbcUrl());
        adminDataSource.setUser(restorationEnv.getAdminUser());
        adminDataSource.setPassword(restorationEnv.getAdminPassword());

        appDataSource = new PGSimpleDataSource();
        appDataSource.setURL(restorationEnv.getJdbcUrl());
        appDataSource.setUser(restorationEnv.getAppUser());
        appDataSource.setPassword(restorationEnv.getAppPassword());

        jdbcAdmin = new JdbcTemplate(adminDataSource);
    }

    @AfterAll
    void tearDownIsolatedContainer() {
        if (restorationEnv != null) {
            restorationEnv.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Paso 1: Revalida baseline e integridad y captura snapshot completo de datos antes de migrar (V7)")
    void paso1_revalidaBaselineYCapturaSnapshotPreMigracion() throws SQLException {
        try (Connection conn = adminDataSource.getConnection()) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("SELECT current_database()");
                assertTrue(rs.next());
                assertEquals("lysandri_verify_db", rs.getString(1));
            }
        }

        // 1. Exactamente 8 migraciones historicas aplicadas (V0 baseline + V1-V7)
        Integer initialMigrations = jdbcAdmin.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertEquals(8, initialMigrations, "El respaldo restaurado debe iniciar con exactamente 8 migraciones (V0-V7)");

        // 2. Verificacion de checksums historicos del respaldo oficial
        Map<String, Integer> expectedHistoricalChecksums = Map.of(
                "1", -1748506468,
                "2", 830737127,
                "3", -803715202,
                "4", -429426097,
                "5", -1712512655,
                "6", -21803896,
                "7", 46689470
        );

        for (Map.Entry<String, Integer> entry : expectedHistoricalChecksums.entrySet()) {
            Integer actualChecksum = jdbcAdmin.queryForObject(
                    "SELECT checksum FROM flyway_schema_history WHERE version = ?",
                    Integer.class, entry.getKey());
            assertEquals(entry.getValue(), actualChecksum, "Checksum historico de V" + entry.getKey() + " difiere");
        }

        // 3. Captura exhaustiva de todas las tablas y columnas originales de V7
        List<String> tables = jdbcAdmin.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename",
                String.class);
        assertEquals(15, tables.size(), "Debe haber exactamente 15 tablas de negocio en V7");

        for (String table : tables) {
            List<String> cols = jdbcAdmin.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=? ORDER BY ordinal_position",
                    String.class, table);
            columnsBeforeMigration.put(table, cols);

            List<Map<String, Object>> rows = jdbcAdmin.queryForList("SELECT * FROM public." + table);
            rowsBeforeMigration.put(table, rows);
        }

        // Validar conteos esperados del snapshot original en memoria
        assertEquals(3, rowsBeforeMigration.get("usuarios").size(), "usuarios debe tener 3 registros originales");
        assertEquals(1, rowsBeforeMigration.get("instructor").size(), "instructor debe tener 1 registro original");
        assertEquals(11, rowsBeforeMigration.get("programa").size(), "programa debe tener 11 registros originales");

        // Tablas originalmente vacías
        List<String> emptyTables = List.of(
                "actividad_bloque", "bloque", "chat_mensajes", "detalle_ordenes",
                "documento_chunks", "inscripciones", "leccion", "modulo",
                "ordenes", "progreso_leccion", "solicitud_informacion", "suscripciones"
        );
        for (String emptyTable : emptyTables) {
            assertTrue(rowsBeforeMigration.containsKey(emptyTable), "Debe haberse capturado la tabla " + emptyTable);
            assertEquals(0, rowsBeforeMigration.get(emptyTable).size(), "La tabla " + emptyTable + " debe estar originalmente vacia");
        }
    }

    @Test
    @Order(2)
    @DisplayName("Paso 2: Ejecuta migracion limpia V7.1 a V27 con Flyway en contenedor aislado")
    void paso2_ejecutaMigracionV7aV27() {
        Flyway flyway = Flyway.configure()
                .dataSource(adminDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .outOfOrder(false)
                .validateOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();
        assertTrue(result.migrationsExecuted > 0, "Se debieron ejecutar migraciones pendientes");
        assertEquals(21, result.migrationsExecuted, "Deben haberse ejecutado exactamente 21 migraciones (V7.1 a V27)");

        flyway.validate();

        Integer totalMigrations = jdbcAdmin.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertEquals(29, totalMigrations, "Se esperan 29 registros de migracion (V0, V1-V7, V7.1, V8-V27)");

        String latestVersion = jdbcAdmin.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1",
                String.class);
        assertEquals("27", latestVersion, "La version maxima instalada debe ser 27");
    }

    private final Map<String, Set<String>> coveredColumns = new LinkedHashMap<>();

    private void markColumnCovered(String table, String column) {
        coveredColumns.computeIfAbsent(table.toLowerCase(Locale.ROOT), k -> new HashSet<>())
                .add(column.toLowerCase(Locale.ROOT));
    }

    private void assertSafeFieldEquals(String table, String column, Object recordKey, Object expected, Object actual) {
        markColumnCovered(table, column);
        if (!Objects.equals(expected, actual)) {
            fail(String.format("Discrepancia en tabla '%s', columna '%s', registro ref: '%s'. Los valores no coinciden.",
                    table, column, recordKey));
        }
    }

    private void assertSafeCondition(String table, String column, Object recordKey, boolean condition, String reason) {
        markColumnCovered(table, column);
        if (!condition) {
            fail(String.format("Condicion no satisfecha en tabla '%s', columna '%s', registro ref: '%s'. Motivo: %s",
                    table, column, recordKey, reason));
        }
    }

    private void assertThrowsForeignKeyViolation(String sql, String description) {
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = adminDataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                } finally {
                    try { conn.rollback(); } catch (Exception ignored) {}
                }
            }
        }, "Se esperaba violacion de Foreign Key para: " + description);
        assertEquals("23503", ex.getSQLState(),
                "Se esperaba SQLSTATE 23503 (foreign_key_violation) para " + description + " pero se obtuvo " + ex.getSQLState());
    }

    @Test
    @Order(3)
    @DisplayName("Paso 3: Comprobacion automatica antes/despues: 100% de conservacion exhaustiva de datos y esquema")
    void paso3_comprobacionAutomaticaConservacionCompleta() {
        coveredColumns.clear();

        // =====================================================================
        // A. Tabla historica 'usuarios' (todas las 7 columnas originales comparadas exhaustivamente)
        // =====================================================================
        List<Map<String, Object>> usuariosBefore = rowsBeforeMigration.get("usuarios");
        List<Map<String, Object>> usuariosAfter = jdbcAdmin.queryForList("SELECT * FROM usuarios ORDER BY id_user");
        assertEquals(usuariosBefore.size(), usuariosAfter.size(), "usuarios historico debe conservar exactamente 3 filas");

        for (Map<String, Object> after : usuariosAfter) {
            int id = ((Number) after.get("id_user")).intValue();
            Map<String, Object> before = usuariosBefore.stream()
                    .filter(b -> ((Number) b.get("id_user")).intValue() == id)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Usuario id=" + id + " no encontrado en snapshot original"));

            assertSafeFieldEquals("usuarios", "id_user", id,
                    ((Number) before.get("id_user")).longValue(), ((Number) after.get("id_user")).longValue());
            assertSafeFieldEquals("usuarios", "nombres", id, before.get("nombres"), after.get("nombres"));
            assertSafeFieldEquals("usuarios", "apellidos", id, before.get("apellidos"), after.get("apellidos"));
            assertSafeFieldEquals("usuarios", "email", id, before.get("email"), after.get("email"));
            assertSafeFieldEquals("usuarios", "passw", id, before.get("passw"), after.get("passw"));
            assertSafeFieldEquals("usuarios", "telefono", id, before.get("telefono"), after.get("telefono"));
            assertSafeFieldEquals("usuarios", "rol", id, before.get("rol"), after.get("rol"));
        }

        // =====================================================================
        // B. Tabla operativa moderna 'usuario' (reconciliacion y separacion de fundadores V12)
        // =====================================================================
        List<Map<String, Object>> usuarioModern = jdbcAdmin.queryForList("SELECT * FROM usuario ORDER BY id_user");
        assertTrue(usuarioModern.size() >= 3, "usuario debe contener al menos los 3 usuarios originales");

        Set<Integer> originalUserIds = Set.of(1, 3, 4);
        List<Map<String, Object>> originalReconciled = new ArrayList<>();
        List<Map<String, Object>> seededAdminsV12 = new ArrayList<>();

        for (Map<String, Object> u : usuarioModern) {
            Object id = u.get("id_user");
            if (id instanceof Number n && originalUserIds.contains(n.intValue())) {
                originalReconciled.add(u);
            } else {
                seededAdminsV12.add(u);
            }
        }

        assertEquals(3, originalReconciled.size(), "Deben reconciliarse exactamente los 3 usuarios originales en tabla moderna");
        for (Map<String, Object> modern : originalReconciled) {
            int id = ((Number) modern.get("id_user")).intValue();
            Map<String, Object> original = usuariosBefore.stream()
                    .filter(b -> ((Number) b.get("id_user")).intValue() == id)
                    .findFirst()
                    .orElseThrow();

            assertSafeFieldEquals("usuario", "id_user", id,
                    ((Number) original.get("id_user")).longValue(), ((Number) modern.get("id_user")).longValue());
            assertSafeFieldEquals("usuario", "nombres", id, original.get("nombres"), modern.get("nombres"));
            assertSafeFieldEquals("usuario", "apellidos", id, original.get("apellidos"), modern.get("apellidos"));
            assertSafeFieldEquals("usuario", "email", id, original.get("email"), modern.get("email"));
            assertSafeFieldEquals("usuario", "passw", id, original.get("passw"), modern.get("passw"));
            assertSafeFieldEquals("usuario", "telefono", id, original.get("telefono"), modern.get("telefono"));
            assertSafeFieldEquals("usuario", "rol", id, original.get("rol"), modern.get("rol"));
            assertSafeCondition("usuario", "activo", id, Boolean.TRUE.equals(modern.get("activo")), "Usuario debe estar activo");
            assertSafeCondition("usuario", "fecha_creacion", id, modern.get("fecha_creacion") != null, "Fecha de creacion requerida");
            assertSafeCondition("usuario", "bloqueado_admin", id, Boolean.FALSE.equals(modern.get("bloqueado_admin")), "Usuario activo no debe estar bloqueado administrativamente");
            assertSafeCondition("usuario", "pendiente_activacion", id, Boolean.FALSE.equals(modern.get("pendiente_activacion")), "Usuario historico migrado no debe estar pendiente de activacion");
        }

        // Verificar que los usuarios añadidos legitimamente por migraciones son validos
        assertFalse(seededAdminsV12.isEmpty(), "Deben existir administradores fundadores sembrados por V12");
        for (Map<String, Object> admin : seededAdminsV12) {
            int adminId = ((Number) admin.get("id_user")).intValue();
            assertSafeCondition("usuario", "email", adminId, admin.get("email") != null, "Admin fundacional debe tener email");
            assertSafeCondition("usuario", "passw", adminId, admin.get("passw") != null, "Admin fundacional debe tener hash de password");
            assertSafeCondition("usuario", "rol", adminId,
                    Set.of("ADMIN", "DIRECTOR_ACADEMICO", "SUPERADMIN").contains(admin.get("rol")),
                    "Rol de admin fundacional no reconocido");
        }

        // =====================================================================
        // C. Tabla 'instructor' (todas las 4 columnas comparadas exhaustivamente)
        // =====================================================================
        List<Map<String, Object>> instructorBefore = rowsBeforeMigration.get("instructor");
        List<Map<String, Object>> instructorAfter = jdbcAdmin.queryForList("SELECT * FROM instructor");
        assertEquals(instructorBefore.size(), instructorAfter.size(), "instructor debe tener exactamente 1 fila");

        String dniKey = String.valueOf(instructorAfter.get(0).get("id_instructor_dni"));
        assertSafeFieldEquals("instructor", "id_instructor_dni", dniKey,
                instructorBefore.get(0).get("id_instructor_dni"), instructorAfter.get(0).get("id_instructor_dni"));
        assertSafeFieldEquals("instructor", "id_user", dniKey,
                ((Number) instructorBefore.get(0).get("id_user")).longValue(), ((Number) instructorAfter.get(0).get("id_user")).longValue());
        assertSafeFieldEquals("instructor", "especialidad", dniKey,
                instructorBefore.get(0).get("especialidad"), instructorAfter.get(0).get("especialidad"));
        assertSafeFieldEquals("instructor", "direccion_instructor", dniKey,
                instructorBefore.get(0).get("direccion_instructor"), instructorAfter.get(0).get("direccion_instructor"));

        // =====================================================================
        // D. Tabla 'programa' (todas las 10 columnas originales mapeadas y comparadas)
        // =====================================================================
        List<Map<String, Object>> programasBefore = rowsBeforeMigration.get("programa");
        List<Map<String, Object>> programasAfter = jdbcAdmin.queryForList("SELECT * FROM programa ORDER BY id_programa");
        assertEquals(11, programasAfter.size(), "programa debe contener todos los 11 cursos");

        for (Map<String, Object> after : programasAfter) {
            int id = ((Number) after.get("id_programa")).intValue();
            Map<String, Object> before = programasBefore.stream()
                    .filter(b -> ((Number) b.get("id_programa")).intValue() == id)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Programa id=" + id + " no encontrado en snapshot original"));

            assertSafeFieldEquals("programa", "id_programa", id,
                    ((Number) before.get("id_programa")).longValue(), ((Number) after.get("id_programa")).longValue());
            assertSafeFieldEquals("programa", "id_instructor_dni", id,
                    before.get("id_instructor_dni"), after.get("id_instructor_dni"));

            // Mapeo explicito de transformaciones de nombres de columnas
            assertSafeFieldEquals("programa", "titulo_programa", id, before.get("titulo_programa"), after.get("titulo"));
            assertSafeFieldEquals("programa", "level", id, before.get("level"), after.get("nivel"));

            assertSafeFieldEquals("programa", "duracion_programa", id,
                    String.valueOf(before.get("duracion_programa")), String.valueOf(after.get("duracion_programa")));
            assertSafeFieldEquals("programa", "tipo_programa", id,
                    String.valueOf(before.get("tipo_programa")), String.valueOf(after.get("tipo_programa")));
            assertSafeFieldEquals("programa", "fecha_inicio_global", id,
                    String.valueOf(before.get("fecha_inicio_global")), String.valueOf(after.get("fecha_inicio_global")));
            assertSafeFieldEquals("programa", "fecha_final_global", id,
                    String.valueOf(before.get("fecha_final_global")), String.valueOf(after.get("fecha_final_global")));
            assertSafeFieldEquals("programa", "requisitos", id,
                    String.valueOf(before.get("requisitos")), String.valueOf(after.get("requisitos")));
            assertSafeFieldEquals("programa", "metodologia", id,
                    String.valueOf(before.get("metodologia")), String.valueOf(after.get("metodologia")));

            // Validacion de columnas modernas enriquecidas por migraciones
            assertNotNull(after.get("moodle_course_id"), "moodle_course_id debe estar asignado");
            assertNotNull(after.get("slug"), "slug debe estar inicializado");
            assertNotNull(after.get("precio"), "precio debe estar inicializado");
            assertNotNull(after.get("moneda"), "moneda debe estar inicializado");
        }

        // =====================================================================
        // E. Tablas originalmente vacías: verificacion de existencia, columnas y transformaciones
        // =====================================================================
        // 1. chat_mensajes -> renombrada a chat_mensajes_legacy, vista chat_mensajes
        assertEquals("chat_mensajes_legacy", jdbcAdmin.queryForObject(
                "SELECT to_regclass('public.chat_mensajes_legacy')::text", String.class));
        String chatKind = jdbcAdmin.queryForObject(
                "SELECT c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname='chat_mensajes'",
                String.class);
        assertEquals("v", chatKind, "chat_mensajes debe ser una vista (relkind 'v')");

        List<String> chatLegacyCols = jdbcAdmin.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='chat_mensajes_legacy'",
                String.class);
        for (String col : columnsBeforeMigration.get("chat_mensajes")) {
            assertTrue(chatLegacyCols.contains(col.toLowerCase(Locale.ROOT)),
                    "Columna original de chat_mensajes debe persistir en chat_mensajes_legacy: " + col);
            markColumnCovered("chat_mensajes", col);
        }

        // 2. ordenes -> columnas renombradas id_ordenes -> id_orden, estado_order -> estado_orden
        List<String> ordenesCols = jdbcAdmin.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='ordenes'",
                String.class);
        Map<String, String> ordenesRenames = Map.of(
                "id_ordenes", "id_orden",
                "estado_order", "estado_orden"
        );
        for (String col : columnsBeforeMigration.get("ordenes")) {
            String targetCol = ordenesRenames.getOrDefault(col, col);
            assertTrue(ordenesCols.contains(targetCol.toLowerCase(Locale.ROOT)),
                    "Columna original de ordenes debe existir como '" + targetCol + "' en esquema migrado");
            markColumnCovered("ordenes", col);
        }

        // 3. detalle_ordenes -> columnas renombradas id_detalle_ordenes -> id_detalle, id_ordenes -> id_orden
        List<String> detalleCols = jdbcAdmin.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='detalle_ordenes'",
                String.class);
        Map<String, String> detalleRenames = Map.of(
                "id_detalle_ordenes", "id_detalle",
                "id_ordenes", "id_orden"
        );
        for (String col : columnsBeforeMigration.get("detalle_ordenes")) {
            String targetCol = detalleRenames.getOrDefault(col, col);
            assertTrue(detalleCols.contains(targetCol.toLowerCase(Locale.ROOT)),
                    "Columna original de detalle_ordenes debe existir como '" + targetCol + "' en esquema migrado");
            markColumnCovered("detalle_ordenes", col);
        }

        // 4. Otras tablas originalmente vacías (conservan sus nombres y columnas)
        List<String> otherEmptyTables = List.of(
                "actividad_bloque", "bloque", "documento_chunks", "inscripciones",
                "leccion", "modulo", "progreso_leccion", "solicitud_informacion", "suscripciones"
        );
        for (String emptyTable : otherEmptyTables) {
            assertNotNull(jdbcAdmin.queryForObject("SELECT to_regclass('public." + emptyTable + "')::text", String.class),
                    "La tabla " + emptyTable + " debe existir tras la migracion");

            List<String> postCols = jdbcAdmin.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?",
                    String.class, emptyTable);
            for (String col : columnsBeforeMigration.get(emptyTable)) {
                assertTrue(postCols.contains(col.toLowerCase(Locale.ROOT)),
                        "Columna " + col + " debe existir en tabla post-migracion " + emptyTable);
                markColumnCovered(emptyTable, col);
            }
        }

        // 5. Verificación exhaustiva de contenido y comparación de conteos antes y después
        // Mapeo de nombres originales en V7 hacia los nombres efectivos post-migración V25
        Map<String, String> emptyTableTargetMapping = Map.ofEntries(
                Map.entry("chat_mensajes", "chat_mensajes_legacy"),
                Map.entry("ordenes", "ordenes"),
                Map.entry("detalle_ordenes", "detalle_ordenes"),
                Map.entry("actividad_bloque", "actividad_bloque"),
                Map.entry("bloque", "bloque"),
                Map.entry("documento_chunks", "documento_chunks"),
                Map.entry("inscripciones", "inscripciones"),
                Map.entry("leccion", "leccion"),
                Map.entry("modulo", "modulo"),
                Map.entry("progreso_leccion", "progreso_leccion"),
                Map.entry("solicitud_informacion", "solicitud_informacion"),
                Map.entry("suscripciones", "suscripciones")
        );

        // Declaración explícita de registros esperados introducidos por migraciones V7.1-V25.
        // Ninguna de estas 12 tablas recibe inserciones por scripts de migración (esperado 0 registros nuevos).
        Map<String, Integer> expectedMigrationInserts = Map.ofEntries(
                Map.entry("chat_mensajes_legacy", 0),
                Map.entry("ordenes", 0),
                Map.entry("detalle_ordenes", 0),
                Map.entry("actividad_bloque", 0),
                Map.entry("bloque", 0),
                Map.entry("documento_chunks", 0),
                Map.entry("inscripciones", 0),
                Map.entry("leccion", 0),
                Map.entry("modulo", 0),
                Map.entry("progreso_leccion", 0),
                Map.entry("solicitud_informacion", 0),
                Map.entry("suscripciones", 0)
        );

        for (Map.Entry<String, String> mapping : emptyTableTargetMapping.entrySet()) {
            String originalTable = mapping.getKey();
            String targetTable = mapping.getValue();

            int countBefore = rowsBeforeMigration.get(originalTable).size();
            assertEquals(0, countBefore,
                    String.format("La tabla original '%s' debía tener 0 registros en el snapshot V7", originalTable));

            int expectedInserts = expectedMigrationInserts.getOrDefault(targetTable, 0);
            int expectedCountAfter = countBefore + expectedInserts;

            Integer actualCountAfter = jdbcAdmin.queryForObject(
                    "SELECT count(*) FROM public." + targetTable, Integer.class);
            assertNotNull(actualCountAfter, "El conteo post-migración no debe ser nulo para " + targetTable);

            assertEquals(expectedCountAfter, actualCountAfter.intValue(),
                    String.format("Discrepancia en conteo post-migración para tabla '%s' (mapeada de '%s'): esperado %d, obtenido %d",
                            targetTable, originalTable, expectedCountAfter, actualCountAfter));

            if (expectedInserts == 0) {
                // Al no existir inserciones previstas, exigir explícitamente que la tabla siga vacía
                assertEquals(0, actualCountAfter.intValue(),
                        String.format("La tabla '%s' no tiene inserciones legítimas previstas y debe permanecer vacía (0 filas)", targetTable));
            }
        }

        // Verificación complementaria sobre la vista chat_mensajes (respaldada por historial_consulta, también vacía)
        Integer chatViewCount = jdbcAdmin.queryForObject("SELECT count(*) FROM public.chat_mensajes", Integer.class);
        assertEquals(0, chatViewCount, "La vista chat_mensajes debe reportar 0 filas ya que historial_consulta está vacía");

        // =====================================================================
        // F. COMPROBACION EXHAUSTIVA DE COBERTURA: CERO COLUMNAS SIN CUBRIR
        // =====================================================================
        for (Map.Entry<String, List<String>> entry : columnsBeforeMigration.entrySet()) {
            String table = entry.getKey();
            List<String> originalCols = entry.getValue();
            Set<String> covered = coveredColumns.getOrDefault(table.toLowerCase(Locale.ROOT), Collections.emptySet());
            for (String col : originalCols) {
                assertTrue(covered.contains(col.toLowerCase(Locale.ROOT)),
                        String.format("VIOLACION DE CONSERVACION: Columna sin cubrir en verificacion exhaustiva: tabla '%s', columna '%s'",
                                table, col));
            }
        }

        // =====================================================================
        // G. Integridad activa de Foreign Keys (verificacion de catalogo y de ejecucion)
        // =====================================================================
        Integer invalidConstraints = jdbcAdmin.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated",
                Integer.class);
        assertEquals(0, invalidConstraints, "Todas las restricciones deben estar validadas sin violaciones");

        // Pruebas de ejecucion activa para demostrar que PostgreSQL hace cumplir las restricciones
        assertThrowsForeignKeyViolation(
                "INSERT INTO programa (titulo, precio, moneda, moodle_course_id, slug, id_instructor_dni) " +
                        "VALUES ('Prog FK Test', 100, 'USD', 888881, 'slug-fk-test-1', 'DNI_NON_EXISTENT')",
                "programa.id_instructor_dni -> instructor.id_instructor_dni");

        assertThrowsForeignKeyViolation(
                "INSERT INTO instructor (id_instructor_dni, id_user, especialidad) " +
                        "VALUES ('DNI_FK_TEST', 999999, 'Test')",
                "instructor.id_user -> usuario.id_user");

        assertThrowsForeignKeyViolation(
                "INSERT INTO ordenes (codigo_orden, id_user, total, estado_orden) " +
                        "VALUES ('ORD-FK-FAIL', 999999, 100.0, 'PENDIENTE')",
                "ordenes.id_user -> usuario.id_user");

        assertThrowsForeignKeyViolation(
                "INSERT INTO detalle_ordenes (id_orden, id_programa, precio_unitario, cantidad, subtotal) " +
                        "VALUES (999999, 1, 100.0, 1, 100.0)",
                "detalle_ordenes.id_orden -> ordenes.id_orden");

        // =====================================================================
        // H. Secuencias correctamente posicionadas e insercion activa sin colisiones
        // =====================================================================
        Long userSeq = jdbcAdmin.queryForObject("SELECT last_value FROM usuario_id_user_seq", Long.class);
        Long maxUserId = jdbcAdmin.queryForObject("SELECT max(id_user) FROM usuario", Long.class);
        assertTrue(userSeq >= maxUserId, "Secuencia de usuario (" + userSeq + ") debe ser >= max ID (" + maxUserId + ")");

        Long progSeq = jdbcAdmin.queryForObject("SELECT last_value FROM programa_id_programa_seq", Long.class);
        Long maxProgId = jdbcAdmin.queryForObject("SELECT max(id_programa) FROM programa", Long.class);
        assertTrue(progSeq >= maxProgId, "Secuencia de programa (" + progSeq + ") debe ser >= max ID (" + maxProgId + ")");

        // Insercion activa sin colision en usuario (omitiendo id_user explicitamente)
        Long nextUserId = jdbcAdmin.queryForObject(
                "INSERT INTO usuario (nombres, apellidos, email, passw, rol, activo) " +
                        "VALUES ('TestSeq', 'Insert', 'seq_test_" + System.currentTimeMillis() + "@lysandri.invalid', 'dummy_hash', 'CLIENTE', true) " +
                        "RETURNING id_user", Long.class);
        assertNotNull(nextUserId);
        assertTrue(nextUserId > maxUserId, "El nuevo ID autogenerado debe ser estrictamente superior al maximo preexistente");
        jdbcAdmin.update("DELETE FROM usuario WHERE id_user = ?", nextUserId);

        // Insercion activa sin colision en programa (omitiendo id_programa explicitamente)
        Long nextProgId = jdbcAdmin.queryForObject(
                "INSERT INTO programa (titulo, precio, moneda, moodle_course_id, slug, activo) " +
                        "VALUES ('TestSeq Prog', 250.00, 'USD', 999995, 'test-seq-prog-" + System.currentTimeMillis() + "', true) " +
                        "RETURNING id_programa", Long.class);
        assertNotNull(nextProgId);
        assertTrue(nextProgId > maxProgId, "El nuevo ID de programa debe ser superior al maximo preexistente");
        jdbcAdmin.update("DELETE FROM programa WHERE id_programa = ?", nextProgId);
    }

    @Test
    @Order(4)
    @DisplayName("Paso 4: Valida permisos minimos (SQLSTATE 42501) en contenedor aislado")
    void paso4_validaPermisosMinimosEnContenedorAislado() throws SQLException {
        try (Connection conn = appDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Operacion permitida de lectura
                var rs = stmt.executeQuery("SELECT count(*) FROM programa");
                assertTrue(rs.next());
            } finally {
                conn.rollback();
            }
        }

        // Operaciones prohibidas: denegadas con SQLSTATE 42501
        assertDeniedOperation(appDataSource, "INSERT INTO usuarios (nombres, apellidos, email, passw, rol) VALUES ('x','y','x@y.invalid','p','ESTUDIANTE')", "INSERT historico usuarios");
        assertDeniedOperation(appDataSource, "UPDATE usuarios SET nombres='hacked' WHERE id_user=1", "UPDATE historico usuarios");
        assertDeniedOperation(appDataSource, "DELETE FROM usuarios WHERE id_user=1", "DELETE historico usuarios");
        assertDeniedOperation(appDataSource, "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, installed_by, execution_time, success) VALUES (999, '999', 'fake', 'SQL', 'fake.sql', 'fake', 1, true)", "INSERT flyway_schema_history");
        assertDeniedOperation(appDataSource, "TRUNCATE TABLE usuario", "TRUNCATE usuario");
        assertDeniedOperation(appDataSource, "SELECT setval('usuario_id_user_seq', 1)", "setval en secuencias");
    }

    private void assertDeniedOperation(PGSimpleDataSource ds, String sql, String label) {
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = ds.getConnection()) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                } finally {
                    try { conn.rollback(); } catch (Exception ignored) {}
                }
            }
        }, "Operacion debio ser denegada: " + label);

        assertEquals("42501", ex.getSQLState(),
                "Se esperaba SQLSTATE 42501 (insufficient_privilege) para " + label + " pero se obtuvo " + ex.getSQLState());
    }
}

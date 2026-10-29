package com.lysandri.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.application.service.OrderFinancialTransactionService;
import com.lysandri.application.service.ResultadoReservaAprovisionamiento;
import com.lysandri.domain.model.*;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import com.lysandri.domain.ports.out.BillingPort;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("clone")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RealCloneSpringBootIntegrationTest {

    private static final String CLONE_HOST = "127.0.0.1";
    private static final int CLONE_PORT = 5434;
    private static final String CLONE_DB = "lysandri_clone_db";
    private static final String CLONE_CONTAINER_ID = "d27359376c51";
    private static final String CLONE_APP_USER = "lysandri_app";

    @DynamicPropertySource
    static void configureCloneDatasource(DynamicPropertyRegistry registry) {
        String jdbcUrl = "jdbc:postgresql://" + CLONE_HOST + ":" + CLONE_PORT + "/" + CLONE_DB;
        DatabaseTargetSafetyGuard.validateTarget(jdbcUrl, CLONE_CONTAINER_ID);
        DatabaseTargetSafetyGuard.validateUrls(jdbcUrl, jdbcUrl);

        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> CLONE_APP_USER);
        registry.add("spring.datasource.password", () -> CloneCredentialProvider.getRequiredSecret("CLONE_APP_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", () -> "lysandri_admin");
        registry.add("spring.flyway.password", () -> CloneCredentialProvider.getRequiredSecret("CLONE_ADMIN_PASSWORD"));
        registry.add("spring.flyway.validate-on-migrate", () -> true);
        registry.add("spring.flyway.out-of-order", () -> false);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserManagementUseCase userManagement;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private BillingPort billingPort;

    @Autowired
    private OrderRepositoryPort orderRepository;

    @Autowired
    private OrderFinancialTransactionService orderFinancialTransactionService;

    @Test
    @Order(1)
    void paso9_hibernateValidaEsquemaYFlywayValidaMigracionesEnClon() {
        // If Hibernate ddl-auto=validate failed, Spring context would never start.
        assertNotNull(jdbcTemplate);
        flyway.validate();

        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertEquals(27, migrationCount, "Clone must have exactly 27 migrations including V25");

        // Verify connected database is strictly lysandri_clone_db
        String currentDatabase = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        assertEquals("lysandri_clone_db", currentDatabase);

        // Verify connected user is lysandri_app
        String currentUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);
        assertEquals("lysandri_app", currentUser);
    }

    @Test
    @Order(2)
    void paso10_pruebaGestionUsuariosYAuditoriaEnClon() throws Exception {
        String email = "clone-worker-" + UUID.randomUUID() + "@lysandri.invalid";
        String password = "Clone-Safe-Password-2026!";
        String requestJson = objectMapper.writeValueAsString(Map.of(
                "nombres", "Trabajador",
                "apellidos", "Clon",
                "email", email,
                "passw", password,
                "rol", "TRABAJADOR"
        ));

        // 1. Admin creates worker
        mockMvc.perform(post("/api/v1/usuarios")
                        .with(user("admin@lysandri.com").roles("ADMIN"))
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isCreated());

        Long userId = jdbcTemplate.queryForObject(
                "SELECT id_user FROM usuario WHERE email = ?", Long.class, email);
        assertNotNull(userId);

        // 2. Worker can authenticate
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponse).path("token").asText();
        assertFalse(token.isBlank());

        // 3. Admin updates role to CONTABILIDAD with audited reason
        mockMvc.perform(patch("/api/v1/usuarios/{id}/rol", userId)
                        .with(user("admin@lysandri.com").roles("ADMIN"))
                        .param("rol", "CONTABILIDAD")
                        .param("motivo", "Asignación contable en clon"))
                .andExpect(status().isOk());

        Usuario userAfterRole = userManagement.obtenerPorId(userId);
        assertEquals("CONTABILIDAD", userAfterRole.getRol().name());

        // Verify audit log persisted
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM usuario_cambio_audit WHERE id_usuario = ? AND accion = 'ROL_ACTUALIZADO'",
                Integer.class, userId);
        assertEquals(1, auditCount);

        // 4. Non-admin cannot change roles
        mockMvc.perform(patch("/api/v1/usuarios/{id}/rol", userId)
                        .with(user("estudiante@lysandri.com").roles("ESTUDIANTE"))
                        .param("rol", "ADMIN"))
                .andExpect(status().isForbidden());

        // 5. Deactivate user with audited reason
        mockMvc.perform(patch("/api/v1/usuarios/{id}/desactivar", userId)
                        .with(user("admin@lysandri.com").roles("ADMIN"))
                        .param("motivo", "Desactivación de prueba en clon"))
                .andExpect(status().isOk());

        Boolean activo = jdbcTemplate.queryForObject(
                "SELECT activo FROM usuario WHERE id_user = ?", Boolean.class, userId);
        assertEquals(Boolean.FALSE, activo);

        // 6. Deactivated user cannot authenticate
        assertThrows(DisabledException.class, () -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)));

        // 7. Issued token is rejected
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void paso10_pruebaContabilidadFacturacionYCorrelativosEnClon() throws Exception {
        // 1. Accounting access control
        mockMvc.perform(get("/api/v1/internal/accounting/ventas")
                        .with(user("contable@lysandri.invalid").roles("CONTABILIDAD"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/internal/accounting/ventas")
                        .with(user("estudiante@lysandri.com").roles("ESTUDIANTE"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin"))
                .andExpect(status().isForbidden());

        // 2. Create order in clone database
        Long adminUserId = jdbcTemplate.queryForObject(
                "SELECT id_user FROM usuario WHERE email = 'admin@lysandri.com'", Long.class);

        String orderCode = "CLONE-ORD-" + UUID.randomUUID().toString().substring(0, 8);
        Orden orden = Orden.builder()
                .idUser(adminUserId)
                .codigoOrden(orderCode)
                .fechaOrden(java.time.OffsetDateTime.now())
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("354.00"))
                .moneda("PEN")
                .metodoPago("TEST_CLONE")
                .nombreFacturacion("Cliente Clon Real")
                .numeroDocumentoCliente("20123456789")
                .tipoComprobanteSolicitado("FACTURA")
                .build();

        Orden ordenGuardada = orderRepository.guardar(orden);
        assertNotNull(ordenGuardada.getIdOrden());

        // 3. Emit receipt with atomic counter
        var receipt = billingPort.emitirComprobante(ordenGuardada);
        assertNotNull(receipt.getIdComprobante());
        assertTrue(receipt.getCorrelativo() > 0);

        // 4. Verify atomic receipt counter table
        Long lastCorrelativo = jdbcTemplate.queryForObject(
                "SELECT ultimo_correlativo FROM comprobante_correlativo WHERE serie = ?",
                Long.class, receipt.getSerie());
        assertNotNull(lastCorrelativo);
        assertTrue(lastCorrelativo >= receipt.getCorrelativo());

        // 5. Void receipt with reason and verify accounting audit log
        mockMvc.perform(post("/api/v1/internal/accounting/comprobantes/{id}/anular", receipt.getIdComprobante())
                        .with(user("contable@lysandri.invalid").roles("CONTABILIDAD"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin")
                        .param("motivo", "Error de digitación verificado en clon"))
                .andExpect(status().isOk());

        String estadoComprobante = jdbcTemplate.queryForObject(
                "SELECT estado FROM comprobante_pago WHERE id_comprobante = ?",
                String.class, receipt.getIdComprobante());
        assertEquals("ANULADO", estadoComprobante);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auditoria_contable WHERE entidad = 'COMPROBANTE_PAGO' AND entidad_id = ? AND accion = 'COMPROBANTE_ANULADO'",
                Integer.class, receipt.getIdComprobante());
        assertEquals(1, auditCount);

        // 6. Export SIRE
        mockMvc.perform(get("/api/v1/internal/accounting/ventas/export-sire")
                        .with(user("contable@lysandri.invalid").roles("CONTABILIDAD"))
                        .header("X-Audit-PIN", "test-accounting-audit-pin"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void paso10_pruebaComprasAprovisionamientoIdempotenteYMatriculaEnClon() {
        Long adminUserId = jdbcTemplate.queryForObject(
                "SELECT id_user FROM usuario WHERE email = 'admin@lysandri.com'", Long.class);

        String orderCode = "CLONE-LEASE-" + UUID.randomUUID().toString().substring(0, 8);
        Orden orden = Orden.builder()
                .idUser(adminUserId)
                .codigoOrden(orderCode)
                .fechaOrden(java.time.OffsetDateTime.now())
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("499.00"))
                .moneda("USD")
                .metodoPago("TEST_GATEWAY")
                .estadoAprovisionamiento(EstadoAprovisionamiento.PENDIENTE)
                .build();

        orderRepository.guardar(orden);

        // 1. Acquire exclusive provisioning lease
        ResultadoReservaAprovisionamiento reserva1 =
                orderFinancialTransactionService.reservarAprovisionamiento(orderCode);
        assertEquals(ResultadoReservaAprovisionamiento.EstadoReserva.RESERVADO, reserva1.getEstado());
        assertNotNull(reserva1.getOwnerToken());

        // 2. Concurrency collision: second attempt while lease is active returns EN_PROCESO
        ResultadoReservaAprovisionamiento reserva2 =
                orderFinancialTransactionService.reservarAprovisionamiento(orderCode);
        assertEquals(ResultadoReservaAprovisionamiento.EstadoReserva.EN_PROCESO, reserva2.getEstado());

        // 3. Complete provisioning with valid token
        orderFinancialTransactionService.completarAprovisionamiento(
                orderCode, reserva1.getOwnerToken(), true, List.of(1L));

        // 4. Third attempt returns YA_COMPLETADO (idempotency)
        ResultadoReservaAprovisionamiento reserva3 =
                orderFinancialTransactionService.reservarAprovisionamiento(orderCode);
        assertEquals(ResultadoReservaAprovisionamiento.EstadoReserva.YA_COMPLETADO, reserva3.getEstado());
    }

    @Test
    @Order(5)
    void paso11_pruebaConcurrenciaRealExclusionMutuaYFacturacionEnClon() throws Exception {
        Long adminUserId = jdbcTemplate.queryForObject(
                "SELECT id_user FROM usuario WHERE email = 'admin@lysandri.com'", Long.class);

        // 1. Multi-Threaded Lease Reservation (10 simultaneous threads on the same order)
        String concOrderCode = "CONC-ORD-" + UUID.randomUUID().toString().substring(0, 8);
        Orden concOrder = Orden.builder()
                .idUser(adminUserId)
                .codigoOrden(concOrderCode)
                .fechaOrden(java.time.OffsetDateTime.now())
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("199.00"))
                .moneda("USD")
                .metodoPago("TEST_GATEWAY")
                .estadoAprovisionamiento(EstadoAprovisionamiento.PENDIENTE)
                .build();
        orderRepository.guardar(concOrder);

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        List<ResultadoReservaAprovisionamiento> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all 10 threads
                    ResultadoReservaAprovisionamiento res =
                            orderFinancialTransactionService.reservarAprovisionamiento(concOrderCode);
                    results.add(res);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release threads concurrently
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent lease tasks timed out");
        executor.shutdown();

        assertEquals(0, errorCount.get(), "No thread should have failed with unhandled exception or deadlock");
        assertEquals(numThreads, results.size());

        long reservadoCount = results.stream()
                .filter(r -> r.getEstado() == ResultadoReservaAprovisionamiento.EstadoReserva.RESERVADO)
                .count();
        long enProcesoCount = results.stream()
                .filter(r -> r.getEstado() == ResultadoReservaAprovisionamiento.EstadoReserva.EN_PROCESO)
                .count();

        assertEquals(1, reservadoCount, "Exactly 1 concurrent thread must acquire the lease (mutual exclusion)");
        assertEquals(numThreads - 1, enProcesoCount, "All 9 other concurrent threads must receive EN_PROCESO");

        // Complete the provisioning with the winning lease
        ResultadoReservaAprovisionamiento winner = results.stream()
                .filter(r -> r.getEstado() == ResultadoReservaAprovisionamiento.EstadoReserva.RESERVADO)
                .findFirst().orElseThrow();
        orderFinancialTransactionService.completarAprovisionamiento(
                concOrderCode, winner.getOwnerToken(), true, List.of(1L));

        // Subsequent call returns YA_COMPLETADO
        ResultadoReservaAprovisionamiento afterComplete =
                orderFinancialTransactionService.reservarAprovisionamiento(concOrderCode);
        assertEquals(ResultadoReservaAprovisionamiento.EstadoReserva.YA_COMPLETADO, afterComplete.getEstado());

        // 2. Concurrent receipt emission with atomic sequential numbering (comprobante_correlativo)
        int numReceipts = 5;
        ExecutorService receiptExecutor = Executors.newFixedThreadPool(numReceipts);
        CountDownLatch receiptStart = new CountDownLatch(1);
        CountDownLatch receiptDone = new CountDownLatch(numReceipts);
        List<ComprobantePago> emittedReceipts = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numReceipts; i++) {
            final int idx = i;
            receiptExecutor.submit(() -> {
                try {
                    String oCode = "REC-ORD-" + idx + "-" + UUID.randomUUID().toString().substring(0, 6);
                    Orden ro = Orden.builder()
                            .idUser(adminUserId)
                            .codigoOrden(oCode)
                            .fechaOrden(java.time.OffsetDateTime.now())
                            .estadoOrden(EstadoOrden.PAGADO)
                            .total(new BigDecimal("100.00"))
                            .moneda("PEN")
                            .metodoPago("TEST_GATEWAY")
                            .nombreFacturacion("Cliente Concurrente " + idx)
                            .numeroDocumentoCliente("10456789012")
                            .tipoComprobanteSolicitado("BOLETA")
                            .build();
                    Orden savedRo = orderRepository.guardar(ro);
                    receiptStart.await();
                    ComprobantePago cp = billingPort.emitirComprobante(savedRo);
                    emittedReceipts.add(cp);
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    receiptDone.countDown();
                }
            });
        }

        receiptStart.countDown(); // Fire concurrent billing
        assertTrue(receiptDone.await(10, TimeUnit.SECONDS), "Concurrent billing timed out");
        receiptExecutor.shutdown();

        assertEquals(numReceipts, emittedReceipts.size());
        Set<Integer> uniqueCorrelatives = emittedReceipts.stream()
                .map(ComprobantePago::getCorrelativo)
                .collect(Collectors.toSet());
        assertEquals(numReceipts, uniqueCorrelatives.size(),
                "Every concurrent receipt must receive a strictly unique atomic correlative");
    }

    @Test
    @Order(6)
    void paso12_pruebaRecuperacionDeFallosEnAprovisionamiento() {
        Long adminUserId = jdbcTemplate.queryForObject(
                "SELECT id_user FROM usuario WHERE email = 'admin@lysandri.com'", Long.class);

        String failOrderCode = "FAIL-ORD-" + UUID.randomUUID().toString().substring(0, 8);
        Orden orden = Orden.builder()
                .idUser(adminUserId)
                .codigoOrden(failOrderCode)
                .fechaOrden(java.time.OffsetDateTime.now())
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("250.00"))
                .moneda("USD")
                .metodoPago("TEST_GATEWAY")
                .estadoAprovisionamiento(EstadoAprovisionamiento.PENDIENTE)
                .build();
        orderRepository.guardar(orden);

        // 1. Acquire lease
        ResultadoReservaAprovisionamiento reserva =
                orderFinancialTransactionService.reservarAprovisionamiento(failOrderCode);
        assertEquals(ResultadoReservaAprovisionamiento.EstadoReserva.RESERVADO, reserva.getEstado());

        // 2. Simulate external dependency failure (e.g. Moodle network timeout)
        orderFinancialTransactionService.registrarFalloAprovisionamiento(
                failOrderCode, reserva.getOwnerToken(), "Moodle simulated timeout (HTTP 504 Gateway Timeout)");

        // Verify state is FALLIDO and error logged directly in PostgreSQL
        String estadoFallido = jdbcTemplate.queryForObject(
                "SELECT estado_aprovisionamiento FROM ordenes WHERE codigo_orden = ?",
                String.class, failOrderCode);
        assertEquals("FALLIDO", estadoFallido);

        String leaseAfterFailure = jdbcTemplate.queryForObject(
                "SELECT aprovisionamiento_lease_token FROM ordenes WHERE codigo_orden = ?",
                String.class, failOrderCode);
        assertNull(leaseAfterFailure, "Lease must be released on failure to allow retry");

        String errorMsg = jdbcTemplate.queryForObject(
                "SELECT aprovisionamiento_ultimo_error FROM ordenes WHERE codigo_orden = ?",
                String.class, failOrderCode);
        assertEquals("Moodle simulated timeout (HTTP 504 Gateway Timeout)", errorMsg);

        // 3. Retry succeeds: acquire new lease and complete provisioning
        ResultadoReservaAprovisionamiento retryReserva =
                orderFinancialTransactionService.reservarAprovisionamiento(failOrderCode);
        assertEquals(ResultadoReservaAprovisionamiento.EstadoReserva.RESERVADO, retryReserva.getEstado());
        assertNotEquals(reserva.getOwnerToken(), retryReserva.getOwnerToken(), "New lease must have distinct owner token");

        orderFinancialTransactionService.completarAprovisionamiento(
                failOrderCode, retryReserva.getOwnerToken(), true, List.of(1L));

        String estadoCompletado = jdbcTemplate.queryForObject(
                "SELECT estado_aprovisionamiento FROM ordenes WHERE codigo_orden = ?",
                String.class, failOrderCode);
        assertEquals("COMPLETADO", estadoCompletado);

        Boolean moodleSinc = jdbcTemplate.queryForObject(
                "SELECT moodle_matricula_sincronizada FROM ordenes WHERE codigo_orden = ?",
                Boolean.class, failOrderCode);
        assertEquals(Boolean.TRUE, moodleSinc);
    }

    @Test
    @Order(7)
    void paso13_pruebaLecturaDeDatosHistoricosConservadosDesdeServicios() {
        // Query catalog through real database: all 11 original programs
        List<Map<String, Object>> programas = jdbcTemplate.queryForList(
                "SELECT id_programa, titulo, precio, moodle_course_id, slug, id_instructor_dni FROM programa ORDER BY id_programa");
        assertEquals(11, programas.size());

        // Verify each program preserves instructor DNI10203040
        for (var p : programas) {
            assertEquals("DNI10203040", p.get("id_instructor_dni"));
            assertNotNull(p.get("moodle_course_id"));
            assertNotNull(p.get("slug"));
        }

        // Verify legacy historical table usuarios is accessible via app user
        Integer usuariosCount = jdbcTemplate.queryForObject("SELECT count(*) FROM usuarios", Integer.class);
        assertEquals(3, usuariosCount);

        // Verify legacy instructor is accessible
        Integer instructorCount = jdbcTemplate.queryForObject("SELECT count(*) FROM instructor", Integer.class);
        assertEquals(1, instructorCount);

        // Verify chat_mensajes view is accessible
        assertDoesNotThrow(() -> jdbcTemplate.queryForList("SELECT * FROM chat_mensajes LIMIT 5"));

        // Verify academic tables are accessible
        assertDoesNotThrow(() -> jdbcTemplate.queryForList("SELECT * FROM bloque LIMIT 1"));
        assertDoesNotThrow(() -> jdbcTemplate.queryForList("SELECT * FROM modulo LIMIT 1"));
        assertDoesNotThrow(() -> jdbcTemplate.queryForList("SELECT * FROM leccion LIMIT 1"));
        assertDoesNotThrow(() -> jdbcTemplate.queryForList("SELECT * FROM actividad_bloque LIMIT 1"));
        assertDoesNotThrow(() -> jdbcTemplate.queryForList("SELECT * FROM inscripciones LIMIT 1"));
        assertDoesNotThrow(() -> jdbcTemplate.queryForList("SELECT * FROM suscripciones LIMIT 1"));
        assertDoesNotThrow(() -> jdbcTemplate.queryForList("SELECT * FROM progreso_leccion LIMIT 1"));
    }
}

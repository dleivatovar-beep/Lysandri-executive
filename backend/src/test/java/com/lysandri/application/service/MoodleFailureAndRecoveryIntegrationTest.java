package com.lysandri.application.service;

import com.lysandri.adapters.PostgresTestDatabase;
import com.lysandri.adapters.out.database.repository.SpringDataComprobantePagoRepository;
import com.lysandri.adapters.out.database.repository.SpringDataOrderRepository;
import com.lysandri.domain.model.EstadoAprovisionamiento;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de integración de extremo a extremo que valida:
 * 1. Entrada por el caso de uso real de compra/aprovisionamiento sobre PostgreSQL desechable.
 * 2. Provocación de fallo HTTP 504 desde servidor Moodle local mediante adaptador WebClient real.
 * 3. Persistencia de orden PAGADA, aprovisionamiento FALLIDO, liberación del lease y registro del error 504.
 * 4. Competencia concurrente real: 6 hilos compitiendo mientras la orden está en estado FALLIDO.
 * 5. Demostración de que solo una ejecución adquiere la reserva activa y llama a Moodle.
 * 6. Verificación de no duplicación de órdenes, ítems, comprobantes ni notificaciones.
 * 7. Comprobación de estado final COMPLETADO e idempotencia posterior sobre orden ya completada.
 */
@SpringBootTest
@ActiveProfiles("test")
class MoodleFailureAndRecoveryIntegrationTest extends PostgresTestDatabase {

    private static HttpServer mockMoodleServer;
    private static int mockMoodlePort;
    private static final AtomicBoolean simulate504Failure = new AtomicBoolean(true);
    private static final AtomicInteger totalMoodleCalls = new AtomicInteger(0);
    private static final AtomicInteger moodleEnrollmentCallCount = new AtomicInteger(0);
    private static final AtomicInteger controlledDelayMs = new AtomicInteger(0);

    @BeforeAll
    static void startMockMoodleHttpServer() throws IOException {
        mockMoodleServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockMoodlePort = mockMoodleServer.getAddress().getPort();

        mockMoodleServer.createContext("/webservice/rest/server.php", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                totalMoodleCalls.incrementAndGet();
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // Si se activa demora controlada (para coordinar competencia concurrente)
                int delay = controlledDelayMs.get();
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ignored) {}
                }

                if (simulate504Failure.get()) {
                    byte[] response = "<html><body>504 Gateway Time-out: Moodle cluster upstream unreachable</body></html>"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(504, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                    return;
                }

                // Modo recuperado (SUCCESS)
                String jsonResponse;
                if (body.contains("wsfunction=core_user_get_users_by_field")) {
                    jsonResponse = "[{\"id\": 5001}]";
                } else if (body.contains("wsfunction=core_user_create_users")) {
                    jsonResponse = "[{\"id\": 5001}]";
                } else if (body.contains("wsfunction=enrol_manual_enrol_users")) {
                    moodleEnrollmentCallCount.incrementAndGet();
                    jsonResponse = "null"; // Retorno estándar de Moodle para enrol_manual_enrol_users
                } else if (body.contains("wsfunction=core_enrol_get_users_courses")) {
                    jsonResponse = "[{\"id\": 1}]";
                } else {
                    jsonResponse = "[]";
                }

                byte[] response = jsonResponse.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        });

        mockMoodleServer.start();
    }

    @AfterAll
    static void stopMockMoodleHttpServer() {
        if (mockMoodleServer != null) {
            mockMoodleServer.stop(0);
        }
    }

    @DynamicPropertySource
    static void moodleProperties(DynamicPropertyRegistry registry) {
        registry.add("moodle.url", () -> "http://127.0.0.1:" + mockMoodlePort);
        registry.add("moodle.token", () -> "mock-test-token");
    }

    @Autowired
    private BuyCourseUseCase buyCourseService;

    @Autowired
    private SpringDataOrderRepository orderRepository;

    @Autowired
    private SpringDataComprobantePagoRepository comprobanteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.lysandri.security.JwtService jwtService;

    @Test
    @DisplayName("Flujo completo: fallo 504 de Moodle, persistencia de fallo, competencia concurrente real y recuperacion sin duplicados")
    void flujoRealCompra_fallaMoodle504_competenciaConcurrenteYRecuperacionSegura() throws Exception {
        // 1. Configurar Moodle simulado en modo HTTP 504
        simulate504Failure.set(true);
        totalMoodleCalls.set(0);
        moodleEnrollmentCallCount.set(0);
        controlledDelayMs.set(0);

        // 2. Preparar instructor y programa en la base de datos desechable
        jdbcTemplate.update("""
                INSERT INTO usuarios (id_user, nombres, apellidos, email, passw, rol)
                VALUES (99, 'Profesor', 'Test', 'profesor.test@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR')
                ON CONFLICT (id_user) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO usuario (id_user, nombres, apellidos, email, passw, rol, activo)
                VALUES (99, 'Profesor', 'Test', 'profesor.test@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR', true)
                ON CONFLICT (id_user) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO instructor (id_instructor_dni, id_user, especialidad, direccion_instructor)
                VALUES ('DNI88888888', 99, 'Finanzas', 'Av. Ejecutiva 123')
                ON CONFLICT (id_user) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO programa (id_programa, id_instructor_dni, titulo, precio, moneda, activo, moodle_course_id, slug, duracion_programa, tipo_programa, nivel, precio_aprobado)
                VALUES (101, 'DNI88888888', 'FinOps Empresarial', 499.00, 'USD', true, 101, 'finops-empresarial', '40 horas', 'DIPLOMADO', 'EJECUTIVO', true)
                ON CONFLICT (id_programa) DO UPDATE SET precio_aprobado = true
                """);

        // 3. Preparar usuario comprador en base de datos
        String email = "ejecutivo.banco." + UUID.randomUUID().toString().substring(0, 8) + "@empresa.pe";

        // 4. Entrar por el caso de uso real de compra (iniciarCompra)
        String guestToken = jwtService.generateGuestCheckoutToken(UUID.randomUUID().toString(), email);
        BuyCourseUseCase.CreateOrderCommand crearCommand = new BuyCourseUseCase.CreateOrderCommand(
                null,
                List.of(101L), // Programa ID 101
                email,
                "Carlos Mendoza",
                "BOLETA",
                "70809010",
                "Carlos Mendoza",
                "TRANSFERENCIA",
                "OP-TRANSFER-504-TEST",
                null,
                guestToken
        );

        BuyCourseUseCase.CheckoutSessionResponse sessionResponse = buyCourseService.iniciarCompra(crearCommand);
        assertNotNull(sessionResponse);
        assertNotNull(sessionResponse.codigoOrden());
        String codigoOrden = sessionResponse.codigoOrden();

        Orden ordenInicial = buyCourseService.obtenerOrdenPorCodigo(codigoOrden);
        assertNotNull(ordenInicial);
        assertNotNull(ordenInicial.getIdOrden());

        // 5. Confirmar pago mediante caso de uso real (confirmarPagoManual)
        // Esto confirma el pago financiero y ejecuta aprovisionamiento (que fallará con HTTP 504)
        BuyCourseUseCase.ConfirmarPagoManualCommand pagoCommand = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "OP-TRANSFER-504-TEST",
                "BCP",
                "193-98218491-0-28",
                ordenInicial.getTotal(),
                ordenInicial.getMoneda(),
                java.time.OffsetDateTime.now().minusHours(1),
                "TRANSFERENCIA",
                "Pago verificado en cuenta BCP",
                "Comprobante verificado",
                "ADMIN"
        );

        Orden ordenFallida = buyCourseService.confirmarPagoManual(ordenInicial.getIdOrden(), pagoCommand);

        // 6. Comprobar que el flujo capturó el error, orden PAGADA, aprovisionamiento FALLIDO y lease liberado
        assertNotNull(ordenFallida);
        assertEquals(EstadoOrden.PAGADO, ordenFallida.getEstadoOrden(), "La orden debe quedar PAGADA (el pago financiero es irrevocable)");
        assertEquals(EstadoAprovisionamiento.FALLIDO, ordenFallida.getEstadoAprovisionamiento(),
                "El aprovisionamiento debe quedar como FALLIDO tras el timeout de Moodle");
        assertFalse(ordenFallida.isMoodleMatriculaSincronizada(), "La sincronización Moodle no debió completarse");

        String estadoAprovisionamientoDb = jdbcTemplate.queryForObject(
                "SELECT estado_aprovisionamiento FROM ordenes WHERE codigo_orden = ?",
                String.class, codigoOrden);
        assertEquals("FALLIDO", estadoAprovisionamientoDb);

        String leaseTokenDb = jdbcTemplate.queryForObject(
                "SELECT aprovisionamiento_lease_token FROM ordenes WHERE codigo_orden = ?",
                String.class, codigoOrden);
        assertNull(leaseTokenDb, "El lease debe estar liberado inmediatamente para permitir reintentos");

        String ultimoErrorDb = jdbcTemplate.queryForObject(
                "SELECT aprovisionamiento_ultimo_error FROM ordenes WHERE codigo_orden = ?",
                String.class, codigoOrden);
        assertNotNull(ultimoErrorDb);
        assertTrue(ultimoErrorDb.contains("504"),
                "El error registrado debe contener la referencia al fallo 504 de Moodle");

        Integer comprobantesFase1 = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM comprobante_pago WHERE id_orden = ?",
                Integer.class, ordenInicial.getIdOrden());
        assertEquals(1, comprobantesFase1, "Debe existir exactamente 1 comprobante emitido");

        // 7. RESTABLECER MOODLE A ESTADO SALUDABLE CON DEMORA CONTROLADA (150ms)
        // Esto asegura que durante la llamada a Moodle, el lease se mantiene ocupado
        // demostrando competencia concurrente real entre los hilos competidores.
        simulate504Failure.set(false);
        controlledDelayMs.set(150);
        moodleEnrollmentCallCount.set(0);

        // 8. LANZAR REINTENTOS SIMULTÁNEOS MIENTRAS LA ORDEN TODAVÍA NECESITA RECUPERARSE (estado FALLIDO)
        int numThreads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        try {
            CyclicBarrier barrier = new CyclicBarrier(numThreads);
            List<Callable<Orden>> tasks = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                tasks.add(() -> {
                    barrier.await(5, TimeUnit.SECONDS); // Salida rigurosamente simultánea
                    return buyCourseService.reintentarMatricula(ordenInicial.getIdOrden());
                });
            }

            List<Future<Orden>> futures = executor.invokeAll(tasks);
            List<Orden> resultados = new ArrayList<>();
            List<Throwable> excepciones = new ArrayList<>();

            for (Future<Orden> future : futures) {
                try {
                    resultados.add(future.get(10, TimeUnit.SECONDS));
                } catch (ExecutionException ee) {
                    excepciones.add(ee.getCause());
                }
            }

            // Recoger y verificar resultados y excepciones (no silenciarlos)
            assertTrue(excepciones.isEmpty(), "Ningún hilo debió fallar de manera descontrolada: " + excepciones);
            assertEquals(numThreads, resultados.size(), "Todos los 6 hilos debieron retornar resultado");

            // Comprobar que solo UNA ejecución llamó a la matrícula en Moodle
            assertEquals(1, moodleEnrollmentCallCount.get(),
                    "Solo UNA ejecución debió adquirir la reserva activa y ejecutar la llamada a Moodle");

            // 9. COMPROBAR RESULTADO FINAL COMPLETADO EN BASE DE DATOS
            String estadoFinalDb = jdbcTemplate.queryForObject(
                    "SELECT estado_aprovisionamiento FROM ordenes WHERE codigo_orden = ?",
                    String.class, codigoOrden);
            assertEquals("COMPLETADO", estadoFinalDb, "La orden debió alcanzar el estado COMPLETADO tras la recuperación");

            Boolean sincronizadoFinalDb = jdbcTemplate.queryForObject(
                    "SELECT moodle_matricula_sincronizada FROM ordenes WHERE codigo_orden = ?",
                    Boolean.class, codigoOrden);
            assertTrue(sincronizadoFinalDb, "La sincronización Moodle debe ser true en la base de datos");

            String leaseFinalDb = jdbcTemplate.queryForObject(
                    "SELECT aprovisionamiento_lease_token FROM ordenes WHERE codigo_orden = ?",
                    String.class, codigoOrden);
            assertNull(leaseFinalDb, "El lease debe ser liberado al concluir el aprovisionamiento");

            // 10. VERIFICACIONES DE NO DUPLICACIÓN
            Integer totalOrdenes = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM ordenes WHERE codigo_orden = ?",
                    Integer.class, codigoOrden);
            assertEquals(1, totalOrdenes, "No deben crearse órdenes duplicadas");

            Integer totalItems = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM detalle_ordenes WHERE id_orden = ?",
                    Integer.class, ordenInicial.getIdOrden());
            assertEquals(1, totalItems, "No deben crearse detalles de orden duplicados");

            Integer totalComprobantes = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM comprobante_pago WHERE id_orden = ?",
                    Integer.class, ordenInicial.getIdOrden());
            assertEquals(1, totalComprobantes, "El comprobante tributario no debe duplicarse");

            Integer totalNotificaciones = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM notificaciones_log WHERE id_orden = ?",
                    Integer.class, ordenInicial.getIdOrden());
            assertTrue(totalNotificaciones <= 1, "Las notificaciones log no deben duplicarse");

            // 11. COMPROBACIÓN SEPARADA DE IDEMPOTENCIA CUANDO LA ORDEN YA ESTÁ COMPLETADA
            controlledDelayMs.set(0);
            int enrollmentCallsBeforeIdempotenceCheck = moodleEnrollmentCallCount.get();

            Orden ordenIdempotente = buyCourseService.reintentarMatricula(ordenInicial.getIdOrden());
            assertNotNull(ordenIdempotente);
            assertEquals(EstadoAprovisionamiento.COMPLETADO, ordenIdempotente.getEstadoAprovisionamiento());
            assertTrue(ordenIdempotente.isMoodleMatriculaSincronizada());

            // Verificar que no se realizaron llamadas adicionales a Moodle ni facturación
            assertEquals(enrollmentCallsBeforeIdempotenceCheck, moodleEnrollmentCallCount.get(),
                    "Reintentar sobre una orden ya completada no debe llamar nuevamente al endpoint de matrícula");
            assertEquals(1, (int) jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM comprobante_pago WHERE id_orden = ?", Integer.class, ordenInicial.getIdOrden()));
            assertEquals(1, (int) jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM ordenes WHERE codigo_orden = ?", Integer.class, codigoOrden));

        } finally {
            executor.shutdownNow();
        }
    }
}

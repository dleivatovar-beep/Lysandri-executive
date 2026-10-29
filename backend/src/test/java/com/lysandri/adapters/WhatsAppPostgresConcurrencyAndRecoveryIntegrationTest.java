package com.lysandri.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.adapters.out.database.adapter.WhatsAppRepositoryAdapter;
import com.lysandri.adapters.out.database.repository.SpringDataWhatsAppRepository;
import com.lysandri.adapters.out.whatsapp.WhatsAppCloudApiAdapter;
import com.lysandri.application.service.WhatsAppService;
import com.lysandri.domain.model.whatsapp.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class WhatsAppPostgresConcurrencyAndRecoveryIntegrationTest extends PostgresTestDatabase {

    @Autowired
    private SpringDataWhatsAppRepository repository;

    @Autowired
    private WhatsAppRepositoryAdapter repositoryAdapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private HttpServer mockHttpServer;
    private int mockPort;
    private final AtomicInteger httpCallsCount = new AtomicInteger(0);
    private final List<String> receivedPayloads = new CopyOnWriteArrayList<>();

    private WhatsAppCloudApiAdapter cloudApiAdapter;
    private WhatsAppService whatsAppService;

    @BeforeEach
    void setUp() throws Exception {
        httpCallsCount.set(0);
        receivedPayloads.clear();

        // Servidor HTTP local simulado para WhatsApp Cloud API
        mockHttpServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockHttpServer.createContext("/", exchange -> {
            httpCallsCount.incrementAndGet();
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            receivedPayloads.add(new String(requestBytes, StandardCharsets.UTF_8));

            byte[] response = ("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.mock."
                    + UUID.randomUUID() + "\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        mockHttpServer.start();
        mockPort = mockHttpServer.getAddress().getPort();

        cloudApiAdapter = new WhatsAppCloudApiAdapter(
                true,
                "http://127.0.0.1:" + mockPort,
                "109876543210",
                "test_access_token",
                "test_verify_token",
                "test_app_secret",
                5000,
                5000,
                objectMapper
        );

        whatsAppService = new WhatsAppService(cloudApiAdapter, repositoryAdapter, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (mockHttpServer != null) {
            mockHttpServer.stop(0);
        }
    }

    @Test
    @DisplayName("Concurrencia real en PostgreSQL: Dos solicitudes simultáneas con misma idSolicitud ejecutan 1 sola llamada de red")
    void testDosSolicitudesSimultaneas_MismaIdSolicitud_SoloUnaLlamadaHttp() throws Exception {
        String idSolicitud = "req-real-concurrent-" + UUID.randomUUID();
        String destinatario = "51999888777";
        String contenido = "Mensaje concurrente para pruebas";

        // Reconfigurar handler para introducir un retraso controlado en la primera petición y forzar solapamiento real
        CountDownLatch requestArrivedLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);

        mockHttpServer.removeContext("/");
        mockHttpServer.createContext("/", exchange -> {
            httpCallsCount.incrementAndGet();
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            receivedPayloads.add(new String(reqBytes, StandardCharsets.UTF_8));
            requestArrivedLatch.countDown();
            try {
                // Mantiene el hilo 1 esperando para asegurar que el hilo 2 intente la misma solicitud simultáneamente
                releaseLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}

            byte[] response = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.concurrent.ok\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<MensajeWhatsApp> f1 = executor.submit(() ->
                whatsAppService.enviarMensajeOperador(destinatario, contenido, "operadorA", idSolicitud)
        );

        // Espera a que el hilo 1 haya entrado efectivamente a contactar al mock server
        assertTrue(requestArrivedLatch.await(5, TimeUnit.SECONDS), "El primer hilo debe contactar al servidor HTTP");

        // Hilo 2 intenta procesar exactamente la misma solicitud mientras el hilo 1 aún no ha guardado la respuesta
        Future<MensajeWhatsApp> f2 = executor.submit(() ->
                whatsAppService.enviarMensajeOperador(destinatario, contenido, "operadorB", idSolicitud)
        );

        // Permitir que el servidor HTTP responda al hilo 1
        releaseLatch.countDown();

        MensajeWhatsApp res1 = f1.get(5, TimeUnit.SECONDS);
        MensajeWhatsApp res2 = f2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactamente 1 llamada física llegó al servidor mock del proveedor
        assertEquals(1, httpCallsCount.get(), "El proveedor simulado debió recibir exactamente 1 llamada HTTP de red");

        // Verificación de integridad en PostgreSQL
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM whatsapp_mensaje WHERE id_solicitud = ?", Integer.class, idSolicitud);
        assertEquals(1, count, "Debe existir exactamente 1 registro persistido en BD para la solicitud");

        String estadoBd = jdbcTemplate.queryForObject(
                "SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud);
        assertEquals("ENVIADO", estadoBd);

        // Comprobación de que el payload enviado contiene biz_opaque_callback_data con el idSolicitud
        assertTrue(receivedPayloads.get(0).contains("biz_opaque_callback_data"));
        assertTrue(receivedPayloads.get(0).contains(idSolicitud));
    }

    @Test
    @DisplayName("Integridad: Reutilización de idSolicitud con distinto contenido o destinatario es rechazada con error controlado")
    void testReutilizacionIdSolicitudConDatosDiferentes_Rechazada() {
        String idSolicitud = "req-reuse-" + UUID.randomUUID();

        // Primer envío legítimo
        whatsAppService.enviarMensajeOperador("51999888777", "Mensaje original", "op", idSolicitud);
        assertEquals(1, httpCallsCount.get());

        // Intento de reusar la misma idSolicitud con distinto contenido
        IllegalArgumentException exContenido = assertThrows(IllegalArgumentException.class, () ->
                whatsAppService.enviarMensajeOperador("51999888777", "Mensaje alterado", "op", idSolicitud)
        );
        assertTrue(exContenido.getMessage().contains("destinatario o contenido diferente"));

        // Intento de reusar la misma idSolicitud con distinto destinatario
        IllegalArgumentException exDest = assertThrows(IllegalArgumentException.class, () ->
                whatsAppService.enviarMensajeOperador("51911122233", "Mensaje original", "op", idSolicitud)
        );
        assertTrue(exDest.getMessage().contains("destinatario o contenido diferente"));

        // No se generaron llamadas adicionales a la red
        assertEquals(1, httpCallsCount.get());
    }

    @Test
    @DisplayName("Persistencia y fallo local: Aceptación por proveedor y fallo DB posterior no provoca reenvío")
    void testAceptacionProveedorYFalloGuardado_NoReenviaDuplicado() {
        String idSolicitud = "req-db-fail-" + UUID.randomUUID();

        // 1. Simular registro manual que quedó en EN_PROCESO_ENVIO (fase 1 completada, intento 1 registrado)
        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje de prueba', 'TEXTO', " +
                        "'EN_PROCESO_ENVIO', NOW(), 'op', 'tok-previo', 1)",
                idSolicitud
        );

        // 2. Se intenta reenviar la solicitud
        MensajeWhatsApp res = whatsAppService.enviarMensajeOperador("51999888777", "Mensaje de prueba", "op", idSolicitud);

        // La operación no debió volver a llamar a la red porque ya existía un intento en curso / no confirmado
        assertEquals(0, httpCallsCount.get(), "No debe reenviar automáticamente cuando existe un intento previo no confirmado");
        assertEquals(EstadoMensajeWhatsApp.EN_PROCESO_ENVIO, res.getEstado());
    }

    @Test
    @DisplayName("Recuperación tras reinicio: Solicitudes en EN_PROCESO_ENVIO pasan a RESULTADO_INCIERTO sin auto-reenvío")
    void testRecuperacionTrasReinicio_MarcaResultadoInciertoSinReenvio() {
        String idSolicitud = "req-restart-" + UUID.randomUUID();

        // Simular que el proceso anterior se cayó dejando la fila en EN_PROCESO_ENVIO
        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje post reinicio', 'TEXTO', " +
                        "'EN_PROCESO_ENVIO', NOW(), 'op', 'tok-crash', 1)",
                idSolicitud
        );

        // Simular arranque de la aplicación (evento de inicio)
        whatsAppService.alIniciarAplicacion();

        // Verificar en PostgreSQL que el estado se actualizó a RESULTADO_INCIERTO
        String estado = jdbcTemplate.queryForObject(
                "SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud);
        assertEquals("RESULTADO_INCIERTO", estado);

        // Si el cliente reintenta enviar esa solicitud, se rechaza un nuevo envío a la red
        MensajeWhatsApp resReintento = whatsAppService.enviarMensajeOperador(
                "51999888777", "Mensaje post reinicio", "op", idSolicitud);
        assertEquals(EstadoMensajeWhatsApp.RESULTADO_INCIERTO, resReintento.getEstado());
        assertEquals(0, httpCallsCount.get(), "No debe llamar al proveedor simulado para una solicitud en RESULTADO_INCIERTO");
    }

    @Test
    @DisplayName("Webhooks y correlación con biz_opaque_callback_data: Concilia estado tras resultado incierto y reinicio")
    void testWebhook_ConciliaEstadoInciertoConBizOpaqueCallbackData() {
        String idSolicitud = "req-webhook-reconcile-" + UUID.randomUUID();
        String wamid = "wamid.webhook." + UUID.randomUUID();

        // Mensaje persistido en RESULTADO_INCIERTO sin idProveedor
        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje incierto', 'TEXTO', " +
                        "'RESULTADO_INCIERTO', NOW(), 'op', NULL, 1)",
                idSolicitud
        );

        // Llega webhook de Meta reportando estado DELIVERED con biz_opaque_callback_data
        String webhookPayload = String.format("""
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "statuses": [{
                          "id": "%s",
                          "status": "delivered",
                          "timestamp": "1730160000",
                          "biz_opaque_callback_data": "%s"
                        }]
                      }
                    }]
                  }]
                }
                """, wamid, idSolicitud);

        // Procesar webhook (validación HMAC simulada válida)
        whatsAppService.procesarWebhook(webhookPayload, "sha256=" + calcularFirmaHmac(webhookPayload, "test_app_secret"));

        // Verificar en BD que el estado se concilió a ENTREGADO y se enlazó el wamid
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT estado, id_proveedor FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
        assertEquals("ENTREGADO", fila.get("estado"));
        assertEquals(wamid, fila.get("id_proveedor"));
    }

    @Test
    @DisplayName("Regla de precedencia: Webhook LEIDO no retrocede a ENTREGADO ni a ENVIADO por eventos fuera de orden")
    void testReglaPrecedencia_NoRetrocedeEstadoLeido() {
        String idSolicitud = "req-order-" + UUID.randomUUID();
        String wamid = "wamid.order." + UUID.randomUUID();

        // Registrar mensaje directamente en estado LEIDO
        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, id_proveedor, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador) " +
                        "VALUES (?, ?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje orden', 'TEXTO', 'LEIDO', NOW(), 'op')",
                idSolicitud, wamid
        );

        // Llega webhook tardío o desordenado con status "sent" (ENVIADO)
        repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.ENVIADO, null);
        String estado1 = jdbcTemplate.queryForObject("SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud);
        assertEquals("LEIDO", estado1, "No debe retroceder de LEIDO a ENVIADO");

        // Llega webhook con status "delivered" (ENTREGADO)
        repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.ENTREGADO, null);
        String estado2 = jdbcTemplate.queryForObject("SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud);
        assertEquals("LEIDO", estado2, "No debe retroceder de LEIDO a ENTREGADO");
    }

    // =========================================================================
    // 1. Concurrencia de Webhooks con Bloqueo Pesimista en PostgreSQL
    // =========================================================================

    @Test
    @DisplayName("Concurrencia real: Webhooks simultáneos ENTREGADO y LEIDO con barrera nunca retroceden de LEIDO")
    void testWebhooksSimultaneos_EntregadoYLeido_ConcurrenciaConBarrera_EstadoFinalLeido() throws Exception {
        String idSolicitud = "req-concurrent-wh-" + UUID.randomUUID();
        String wamid = "wamid.concurrent.wh." + UUID.randomUUID();

        // Registrar mensaje en estado ENVIADO
        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, id_proveedor, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador) " +
                        "VALUES (?, ?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje prueba concurrencia', 'TEXTO', 'ENVIADO', NOW(), 'op')",
                idSolicitud, wamid
        );

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.ENTREGADO, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.LEIDO, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        String estadoFinal = jdbcTemplate.queryForObject(
                "SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud);
        assertEquals("LEIDO", estadoFinal, "Bajo concurrencia con bloqueo pesimista, el estado final debe ser LEIDO y no retroceder a ENTREGADO");
    }

    @Test
    @DisplayName("Concurrencia real: Múltiples notificaciones de webhook duplicadas simultáneas no causan fallos ni inconsistencias")
    void testWebhooksDuplicados_ConcurrenciaConBarrera_NoGeneraErroresNiInconsistencias() throws Exception {
        String idSolicitud = "req-dup-wh-" + UUID.randomUUID();
        String wamid = "wamid.dup.wh." + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, id_proveedor, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador) " +
                        "VALUES (?, ?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje duplicado', 'TEXTO', 'ENVIADO', NOW(), 'op')",
                idSolicitud, wamid
        );

        int nThreads = 4;
        CyclicBarrier barrier = new CyclicBarrier(nThreads);
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < nThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.ENTREGADO, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        String estadoFinal = jdbcTemplate.queryForObject(
                "SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud);
        assertEquals("ENTREGADO", estadoFinal);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM whatsapp_mensaje WHERE id_solicitud = ?", Integer.class, idSolicitud);
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Webhooks fuera de orden: LEIDO primero, luego ENTREGADO, luego ENVIADO conserva LEIDO")
    void testWebhooksFueraDeOrden_Completo_ConservaEstadoLeido() {
        String idSolicitud = "req-out-of-order-" + UUID.randomUUID();
        String wamid = "wamid.out.of.order." + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, id_proveedor, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador) " +
                        "VALUES (?, ?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje fuera orden', 'TEXTO', 'ENVIADO', NOW(), 'op')",
                idSolicitud, wamid
        );

        // 1. Llega primero evento LEIDO
        repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.LEIDO, null);
        assertEquals("LEIDO", jdbcTemplate.queryForObject("SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud));

        // 2. Llega después evento ENTREGADO
        repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.ENTREGADO, null);
        assertEquals("LEIDO", jdbcTemplate.queryForObject("SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud));

        // 3. Llega después evento ENVIADO
        repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.ENVIADO, null);
        assertEquals("LEIDO", jdbcTemplate.queryForObject("SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud));
    }

    @Test
    @DisplayName("Concurrencia real: Carrera entre finalización de envío por trabajador y confirmación por webhook")
    void testWebhookYRespuestaEnvioSimultaneos_ConcurrenciaConBarrera() throws Exception {
        String idSolicitud = "req-race-worker-wh-" + UUID.randomUUID();
        String tokenIntento = "tok-race-worker-1";
        String wamid = "wamid.race.worker." + UUID.randomUUID();

        // Mensaje registrado en EN_PROCESO_ENVIO con token asignado
        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje carrera', 'TEXTO', " +
                        "'EN_PROCESO_ENVIO', NOW(), 'op', ?, 1)",
                idSolicitud, tokenIntento
        );

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Hilo 1: Trabajador guarda resultado ACEPTADO
        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                repositoryAdapter.actualizarResultadoEnvio(
                        idSolicitud, tokenIntento, ResultadoLlamadaWhatsApp.aceptado(wamid));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Hilo 2: Webhook confirma directamente LEIDO
        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                repositoryAdapter.actualizarEstadoConReconciliacion(wamid, idSolicitud, EstadoMensajeWhatsApp.LEIDO, null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT estado, id_proveedor FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
        assertEquals("LEIDO", fila.get("estado"), "Bajo concurrencia entre trabajador y webhook, prevalece el estado avanzado LEIDO");
        assertEquals(wamid, fila.get("id_proveedor"));
    }

    @Test
    @DisplayName("Correlación de proveedor: Rechaza reemplazar idProveedor existente por uno incompatible")
    void testCorrelacionProveedor_RechazaReemplazarPorIdIncompatible() {
        String idSolicitud = "req-correlacion-" + UUID.randomUUID();
        String wamidOriginal = "wamid.original.100";
        String wamidIncompatible = "wamid.incompatible.999";

        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, id_proveedor, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador) " +
                        "VALUES (?, ?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje correlacion', 'TEXTO', 'ENVIADO', NOW(), 'op')",
                idSolicitud, wamidOriginal
        );

        // Intento de conciliar con idProveedor incompatible
        repositoryAdapter.actualizarEstadoConReconciliacion(wamidIncompatible, idSolicitud, EstadoMensajeWhatsApp.LEIDO, null);

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT estado, id_proveedor FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
        // Debe conservar el wamid original y no actualizarse con el incompatible
        assertEquals(wamidOriginal, fila.get("id_proveedor"), "No debe reemplazar un idProveedor legítimo existente por uno incompatible");
    }

    // =========================================================================
    // 2. Propiedad Válida del Intento al Guardar Resultado
    // =========================================================================

    @Test
    @DisplayName("Propiedad de intento: Token correcto actualiza a ENVIADO y libera bloqueo temporal")
    void testPropiedadIntento_TokenCorrecto_ActualizaAEnviado() {
        String idSolicitud = "req-prop-ok-" + UUID.randomUUID();
        String tokenIntento = "tok-legitimo-123";
        String wamid = "wamid.prop.ok." + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje prop ok', 'TEXTO', " +
                        "'EN_PROCESO_ENVIO', NOW(), 'op', ?, 1)",
                idSolicitud, tokenIntento
        );

        MensajeWhatsApp res = repositoryAdapter.actualizarResultadoEnvio(
                idSolicitud, tokenIntento, ResultadoLlamadaWhatsApp.aceptado(wamid));

        assertEquals(EstadoMensajeWhatsApp.ENVIADO, res.getEstado());
        assertEquals(wamid, res.getIdProveedor());

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT estado, id_proveedor, token_intento, bloqueado_hasta FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
        assertEquals("ENVIADO", fila.get("estado"));
        assertEquals(wamid, fila.get("id_proveedor"));
        assertNull(fila.get("bloqueado_hasta"), "El lease temporal debe liberarse al finalizar");
    }

    @Test
    @DisplayName("Propiedad de intento: Token diferente es rechazado y no modifica el registro")
    void testPropiedadIntento_TokenDiferente_Rechazado_NoModificaRegistro() {
        String idSolicitud = "req-prop-diff-" + UUID.randomUUID();
        String tokenReal = "tok-real-888";
        String tokenAjeno = "tok-ajeno-999";

        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje prop diff', 'TEXTO', " +
                        "'EN_PROCESO_ENVIO', NOW(), 'op', ?, 1)",
                idSolicitud, tokenReal
        );

        MensajeWhatsApp res = repositoryAdapter.actualizarResultadoEnvio(
                idSolicitud, tokenAjeno, ResultadoLlamadaWhatsApp.aceptado("wamid.fake"));

        // Se retorna el estado existente sin mutar
        assertEquals(EstadoMensajeWhatsApp.EN_PROCESO_ENVIO, res.getEstado());

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT estado, id_proveedor, token_intento FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
        assertEquals("EN_PROCESO_ENVIO", fila.get("estado"), "El estado en BD no debe mutar");
        assertEquals(tokenReal, fila.get("token_intento"), "El token legítimo no debe sobrescribirse");
        assertNull(fila.get("id_proveedor"));
    }

    @Test
    @DisplayName("Propiedad de intento: Token recibido nulo o vacío es rechazado y no modifica el registro")
    void testPropiedadIntento_TokenRecibidoNuloOVacio_Rechazado_NoModificaRegistro() {
        String idSolicitud = "req-prop-null-" + UUID.randomUUID();
        String tokenReal = "tok-real-777";

        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje prop null', 'TEXTO', " +
                        "'EN_PROCESO_ENVIO', NOW(), 'op', ?, 1)",
                idSolicitud, tokenReal
        );

        // 1. Token nulo
        MensajeWhatsApp res1 = repositoryAdapter.actualizarResultadoEnvio(
                idSolicitud, null, ResultadoLlamadaWhatsApp.aceptado("wamid.fake1"));
        assertEquals(EstadoMensajeWhatsApp.EN_PROCESO_ENVIO, res1.getEstado());

        // 2. Token vacío / espacios
        MensajeWhatsApp res2 = repositoryAdapter.actualizarResultadoEnvio(
                idSolicitud, "   ", ResultadoLlamadaWhatsApp.aceptado("wamid.fake2"));
        assertEquals(EstadoMensajeWhatsApp.EN_PROCESO_ENVIO, res2.getEstado());

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT estado, id_proveedor, token_intento FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
        assertEquals("EN_PROCESO_ENVIO", fila.get("estado"));
        assertEquals(tokenReal, fila.get("token_intento"));
        assertNull(fila.get("id_proveedor"));
    }

    @Test
    @DisplayName("Propiedad de intento: Token almacenado nulo es rechazado y no modifica el registro")
    void testPropiedadIntento_TokenAlmacenadoNulo_Rechazado_NoModificaRegistro() {
        String idSolicitud = "req-prop-stored-null-" + UUID.randomUUID();

        // Mensaje sin token_intento activo
        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje stored null', 'TEXTO', " +
                        "'PENDIENTE_ENVIO', NOW(), 'op', NULL, 0)",
                idSolicitud
        );

        MensajeWhatsApp res = repositoryAdapter.actualizarResultadoEnvio(
                idSolicitud, "tok-inventado", ResultadoLlamadaWhatsApp.aceptado("wamid.fake"));

        assertEquals(EstadoMensajeWhatsApp.PENDIENTE_ENVIO, res.getEstado());

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT estado, token_intento FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
        assertEquals("PENDIENTE_ENVIO", fila.get("estado"));
        assertNull(fila.get("token_intento"));
    }

    @Test
    @DisplayName("Propiedad de intento: Respuesta tardía del proveedor tras confirmación por webhook no degrada estado")
    void testPropiedadIntento_RespuestaTardiaTrasWebhook_NoDegradaEstado() {
        String idSolicitud = "req-prop-late-" + UUID.randomUUID();
        String tokenAntiguo = "tok-antiguo-111";
        String wamidWebhook = "wamid.webhook.confirmed." + UUID.randomUUID();

        // 1. Mensaje fue registrado inicialmente en EN_PROCESO_ENVIO
        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje tardio', 'TEXTO', " +
                        "'EN_PROCESO_ENVIO', NOW(), 'op', ?, 1)",
                idSolicitud, tokenAntiguo
        );

        // 2. Un webhook llega primero y confirma ENTREGADO (liberando token_intento)
        repositoryAdapter.actualizarEstadoConReconciliacion(wamidWebhook, idSolicitud, EstadoMensajeWhatsApp.ENTREGADO, null);
        assertEquals("ENTREGADO", jdbcTemplate.queryForObject("SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud));

        // 3. El hilo del proveedor finalmente responde tarde con el token antiguo y resultado ACEPTADO
        MensajeWhatsApp resTardia = repositoryAdapter.actualizarResultadoEnvio(
                idSolicitud, tokenAntiguo, ResultadoLlamadaWhatsApp.aceptado("wamid.otro.ignorar"));

        // Comprobar que no retrocedió a ENVIADO ni modificó el wamid confirmado
        assertEquals(EstadoMensajeWhatsApp.ENTREGADO, resTardia.getEstado());
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT estado, id_proveedor FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
        assertEquals("ENTREGADO", fila.get("estado"), "No debe retroceder de ENTREGADO a ENVIADO por respuesta tardía");
        assertEquals(wamidWebhook, fila.get("id_proveedor"));
    }

    @Test
    @DisplayName("Propiedad de intento: Repetición del resultado con el mismo token es idempotente sin mutaciones redundantes")
    void testPropiedadIntento_RepeticionMismoIntento_IdempotenteSinMutacion() {
        String idSolicitud = "req-prop-idem-" + UUID.randomUUID();
        String token = "tok-idem-555";
        String wamid = "wamid.idem." + UUID.randomUUID();

        jdbcTemplate.update(
                "INSERT INTO whatsapp_mensaje (id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
                        "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count) " +
                        "VALUES (?, 'LYSANDRI_OFFICIAL', '51999888777', 'SALIENTE', 'Mensaje idem', 'TEXTO', " +
                        "'EN_PROCESO_ENVIO', NOW(), 'op', ?, 1)",
                idSolicitud, token
        );

        // Primera llamada
        MensajeWhatsApp res1 = repositoryAdapter.actualizarResultadoEnvio(
                idSolicitud, token, ResultadoLlamadaWhatsApp.aceptado(wamid));
        assertEquals(EstadoMensajeWhatsApp.ENVIADO, res1.getEstado());

        // Segunda llamada repetida con mismo token
        MensajeWhatsApp res2 = repositoryAdapter.actualizarResultadoEnvio(
                idSolicitud, token, ResultadoLlamadaWhatsApp.aceptado(wamid));
        assertEquals(EstadoMensajeWhatsApp.ENVIADO, res2.getEstado());
        assertEquals(wamid, res2.getIdProveedor());

        String estadoBd = jdbcTemplate.queryForObject("SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = ?", String.class, idSolicitud);
        assertEquals("ENVIADO", estadoBd);
    }

    // =========================================================================
    // 3A. Escenario 3A: Aceptación por Proveedor seguida de Fallo en PostgreSQL
    // =========================================================================

    @Test
    @DisplayName("Escenario 3A: Aceptación por proveedor seguida de fallo en PostgreSQL, sin reenvío y conciliado por webhook")
    void testEscenario3A_AceptacionProveedorSeguidaDeFalloGuardadoPostgres_Y_Recuperacion() {
        String idSolicitud = "req-escenario3a-" + UUID.randomUUID();
        String destinatario = "51999888777";
        String contenido = "Prueba de fallo de persistencia post-aceptacion";

        // 1. Instalar trigger en PostgreSQL que forzará fallo controlado al intentar actualizar a ENVIADO para esta solicitud
        String sqlTrigger = String.format("""
            CREATE OR REPLACE FUNCTION trigger_fallo_guardado_resultado()
            RETURNS TRIGGER AS $func$
            BEGIN
                IF NEW.id_solicitud = '%s' AND NEW.estado = 'ENVIADO' THEN
                    RAISE EXCEPTION 'Fallo simulado de disco/conexion al persistir resultado en PostgreSQL';
                END IF;
                RETURN NEW;
            END;
            $func$ LANGUAGE plpgsql;

            DROP TRIGGER IF EXISTS trg_fallo_resultado ON whatsapp_mensaje;
            CREATE TRIGGER trg_fallo_resultado
            BEFORE UPDATE ON whatsapp_mensaje
            FOR EACH ROW EXECUTE FUNCTION trigger_fallo_guardado_resultado();
        """, idSolicitud);
        jdbcTemplate.execute(sqlTrigger);

        try {
            // 2. Iniciar solicitud mediante el flujo real
            MensajeWhatsApp resInicial = whatsAppService.enviarMensajeOperador(destinatario, contenido, "op-pruebas", idSolicitud);

            // El servidor HTTP simulado debió recibir y aceptar el mensaje
            assertEquals(1, httpCallsCount.get(), "El proveedor simulado debió recibir exactamente 1 llamada HTTP");

            // 3. Comprobar que la intención previa permanece registrada en PostgreSQL en EN_PROCESO_ENVIO
            Map<String, Object> filaBd = jdbcTemplate.queryForMap(
                    "SELECT estado, intento_count, token_intento FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
            assertEquals("EN_PROCESO_ENVIO", filaBd.get("estado"), "La intención previa debe permanecer en EN_PROCESO_ENVIO tras el fallo de escritura del resultado");
            assertEquals(1, ((Number) filaBd.get("intento_count")).intValue());
            assertNotNull(filaBd.get("token_intento"));

            // 4. Restaurar disponibilidad de escritura eliminando el trigger de fallo
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fallo_resultado ON whatsapp_mensaje;");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS trigger_fallo_guardado_resultado();");

            // 5. Repetir la misma solicitud mediante el flujo real
            MensajeWhatsApp resRepetida = whatsAppService.enviarMensajeOperador(destinatario, contenido, "op-pruebas", idSolicitud);

            // Verificar que el proveedor NO recibe otro envío (el contador de llamadas sigue siendo 1)
            assertEquals(1, httpCallsCount.get(), "El proveedor simulado NO debe recibir una segunda llamada para solicitud en proceso");
            assertEquals(EstadoMensajeWhatsApp.EN_PROCESO_ENVIO, resRepetida.getEstado());

            // 6. Conciliación posterior mediante webhook de Meta
            String wamidConciliado = "wamid.escenario3a." + UUID.randomUUID();
            String webhookPayload = String.format("""
                    {
                      "object": "whatsapp_business_account",
                      "entry": [{
                        "changes": [{
                          "value": {
                            "statuses": [{
                              "id": "%s",
                              "status": "delivered",
                              "timestamp": "1730165000",
                              "biz_opaque_callback_data": "%s"
                            }]
                          }
                        }]
                      }]
                    }
                    """, wamidConciliado, idSolicitud);

            whatsAppService.procesarWebhook(webhookPayload, "sha256=" + calcularFirmaHmac(webhookPayload, "test_app_secret"));

            // 7. Verificar estado final en PostgreSQL y total de llamadas
            Map<String, Object> filaFinal = jdbcTemplate.queryForMap(
                    "SELECT estado, id_proveedor FROM whatsapp_mensaje WHERE id_solicitud = ?", idSolicitud);
            assertEquals("ENTREGADO", filaFinal.get("estado"), "El estado final debe conciliarse a ENTREGADO mediante el webhook");
            assertEquals(wamidConciliado, filaFinal.get("id_proveedor"));
            assertEquals(1, httpCallsCount.get(), "El total de llamadas a la red del proveedor debe ser estrictamente 1");

        } finally {
            // Asegurar limpieza del trigger si hubo excepción
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fallo_resultado ON whatsapp_mensaje;");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS trigger_fallo_guardado_resultado();");
        }
    }

    private String calcularFirmaHmac(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}


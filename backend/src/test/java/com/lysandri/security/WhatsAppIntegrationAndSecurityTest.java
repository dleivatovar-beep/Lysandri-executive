package com.lysandri.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.adapters.in.web.WhatsAppOperadorController;
import com.lysandri.adapters.in.web.WhatsAppWebhookController;
import com.lysandri.adapters.out.whatsapp.WhatsAppCloudApiAdapter;
import com.lysandri.domain.model.whatsapp.*;
import com.lysandri.domain.ports.in.WhatsAppUseCase;
import com.lysandri.domain.ports.out.WhatsAppCloudClientPort;
import com.lysandri.domain.ports.out.WhatsAppRepositoryPort;
import com.lysandri.exception.GlobalExceptionHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class WhatsAppIntegrationAndSecurityTest {

    private MockMvc mockMvcWebhook;
    private MockMvc mockMvcOperador;

    @Mock
    private WhatsAppUseCase whatsAppUseCase;

    @Mock
    private WhatsAppRepositoryPort repositoryPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String APP_SECRET = "secret_whatsapp_mock_key_12345";
    private static final String VERIFY_TOKEN = "token_verificacion_lysandri_wh";

    @BeforeEach
    void setUp() {
        WhatsAppWebhookController webhookController = new WhatsAppWebhookController(whatsAppUseCase);
        WhatsAppOperadorController operadorController = new WhatsAppOperadorController(whatsAppUseCase);

        mockMvcWebhook = MockMvcBuilders.standaloneSetup(webhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvcOperador = MockMvcBuilders.standaloneSetup(operadorController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String calcularFirma(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return "sha256=" + sb.toString();
    }

    // =========================================================================
    // 1. Webhook Handshake (GET /api/v1/whatsapp/webhook)
    // =========================================================================

    @Test
    @DisplayName("Handshake de Webhook válido: responde HTTP 200 con el challenge de Meta")
    void testWebhookHandshake_Valido() throws Exception {
        when(whatsAppUseCase.verificarWebhook("subscribe", VERIFY_TOKEN, "challenge_12345"))
                .thenReturn("challenge_12345");

        mockMvcWebhook.perform(get("/api/v1/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "challenge_12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge_12345"));
    }

    @Test
    @DisplayName("Handshake de Webhook con token inválido: retorna 403 Forbidden")
    void testWebhookHandshake_TokenInvalido_Retorna403() throws Exception {
        when(whatsAppUseCase.verificarWebhook("subscribe", "wrong_token", "challenge_12345"))
                .thenThrow(new AccessDeniedException("Token de verificación inválido"));

        mockMvcWebhook.perform(get("/api/v1/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong_token")
                        .param("hub.challenge", "challenge_12345"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. Validación de Firma Criptográfica HMAC-SHA256 (POST Webhook)
    // =========================================================================

    @Test
    @DisplayName("Webhook con firma HMAC válida: aceptado exitosamente (HTTP 200)")
    void testWebhook_FirmaValida_Aceptado() throws Exception {
        String payload = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";
        String firma = calcularFirma(payload, APP_SECRET);

        doNothing().when(whatsAppUseCase).procesarWebhook(eq(payload), eq(firma));

        mockMvcWebhook.perform(post("/api/v1/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", firma)
                        .content(payload))
                .andExpect(status().isOk());

        verify(whatsAppUseCase).procesarWebhook(eq(payload), eq(firma));
    }

    @Test
    @DisplayName("Webhook con firma alterada: rechazado con 403 Forbidden")
    void testWebhook_FirmaAlterada_Rechazado() throws Exception {
        String payload = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";
        String firmaFalsa = "sha256=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

        doThrow(new AccessDeniedException("Firma de webhook de WhatsApp inválida"))
                .when(whatsAppUseCase).procesarWebhook(eq(payload), eq(firmaFalsa));

        mockMvcWebhook.perform(post("/api/v1/whatsapp/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", firmaFalsa)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. Deduplicación Idempotente y Precedencia de Estados en Servicio
    // =========================================================================

    @Test
    @DisplayName("Deduplicación idempotente: evento con idProveedor ya existente no se guarda de nuevo")
    void testDeduplicacionIdempotente_NoGuardaDuplicado() {
        WhatsAppCloudClientPort clientMock = mock(WhatsAppCloudClientPort.class);
        when(clientMock.validarFirmaWebhook(anyString(), anyString())).thenReturn(true);

        com.lysandri.application.service.WhatsAppService service =
                new com.lysandri.application.service.WhatsAppService(clientMock, repositoryPort, objectMapper);

        when(repositoryPort.existePorIdProveedor("wamid.duplicate123")).thenReturn(true);

        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "id": "wamid.duplicate123",
                          "from": "51999888777",
                          "text": { "body": "Hola, solicito información" },
                          "type": "text",
                          "timestamp": "1730000000"
                        }]
                      }
                    }]
                  }]
                }
                """;

        assertDoesNotThrow(() -> service.procesarWebhook(payload, "sha256=any"));
        verify(repositoryPort, never()).guardar(any());
    }

    @Test
    @DisplayName("Precedencia de estados: ENTREGADO retrasado no sobreescribe LEIDO y FALLIDO no retrocede")
    void testPrecedenciaEstados_NoSobrescribeLeido() {
        assertFalse(EstadoMensajeWhatsApp.LEIDO.puedeTransicionarA(EstadoMensajeWhatsApp.ENTREGADO));
        assertFalse(EstadoMensajeWhatsApp.LEIDO.puedeTransicionarA(EstadoMensajeWhatsApp.ENVIADO));
        assertTrue(EstadoMensajeWhatsApp.ENVIADO.puedeTransicionarA(EstadoMensajeWhatsApp.ENTREGADO));
        assertTrue(EstadoMensajeWhatsApp.ENTREGADO.puedeTransicionarA(EstadoMensajeWhatsApp.LEIDO));
        assertTrue(EstadoMensajeWhatsApp.ENVIADO.puedeTransicionarA(EstadoMensajeWhatsApp.FALLIDO));
        assertFalse(EstadoMensajeWhatsApp.LEIDO.puedeTransicionarA(EstadoMensajeWhatsApp.FALLIDO));
        assertFalse(EstadoMensajeWhatsApp.FALLIDO.puedeTransicionarA(EstadoMensajeWhatsApp.ENVIADO));
        assertTrue(EstadoMensajeWhatsApp.RESULTADO_INCIERTO.puedeTransicionarA(EstadoMensajeWhatsApp.ENTREGADO));
    }

    // =========================================================================
    // 4. Adaptador de Infraestructura con Servidor Mock (Timeout y Concurrencia)
    // =========================================================================

    @Test
    @DisplayName("Adaptador WhatsApp: Deshabilitado por configuración arroja IllegalStateException")
    void testAdaptador_Deshabilitado_ArrojaExcepcion() {
        WhatsAppCloudApiAdapter adapter = new WhatsAppCloudApiAdapter(
                false, "http://localhost:8080", "123", "token", "vtoken", "secret", 1000, 1000, objectMapper
        );

        assertFalse(adapter.isHabilitado());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adapter.enviarMensajeTexto("51999888777", "Hola"));
        assertTrue(ex.getMessage().contains("deshabilitada"));
    }

    @Test
    @DisplayName("Adaptador WhatsApp: Configuración habilitada pero incompleta arroja error explícito en arranque")
    void testAdaptador_ConfiguracionIncompleta_ArrojaError() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new WhatsAppCloudApiAdapter(
                        true, "http://localhost:8080", "", "token", "", "secret", 1000, 1000, objectMapper
                ));
        assertTrue(ex.getMessage().contains("Variables requeridas ausentes"));
    }

    @Test
    @DisplayName("Adaptador WhatsApp con servidor simulado: Envío exitoso procesa wamid devuelto por Meta")
    void testAdaptador_EnvioExitosoConServidorSimulado() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] response = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.HBgLMTEyMjMzNDQ1NQ\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        String baseUrl = "http://localhost:" + port;

        WhatsAppCloudApiAdapter adapter = new WhatsAppCloudApiAdapter(
                true, baseUrl, "phone123", "access_tok", "verify_tok", "secret", 3000, 3000, objectMapper
        );

        ResultadoLlamadaWhatsApp res = adapter.enviarMensajeTexto("51999888777", "Mensaje oficial de asesor Lysandri");
        assertEquals(ResultadoLlamadaWhatsApp.TipoResultadoLlamada.ACEPTADO, res.tipo());
        assertEquals("wamid.HBgLMTEyMjMzNDQ1NQ", res.wamid());

        server.stop(0);
    }

    @Test
    @DisplayName("Servidor simulado: Timeout o respuesta perdida produce RESULTADO_INCIERTO con exactamente 1 llamada real")
    void testServidorSimulado_Timeout_ProduceResultadoInciertoSinReintentoCiego() throws Exception {
        AtomicInteger serverCalls = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            serverCalls.incrementAndGet();
            // Retardo intencional superior al read-timeout (150ms) para forzar timeout de lectura
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {}
            byte[] response = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.timeout\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        String baseUrl = "http://localhost:" + port;

        // Read timeout muy bajo (150ms) para garantizar detección de timeout
        WhatsAppCloudApiAdapter adapter = new WhatsAppCloudApiAdapter(
                true, baseUrl, "phone123", "access_tok", "verify_tok", "secret", 500, 150, objectMapper
        );

        ResultadoLlamadaWhatsApp resultado = adapter.enviarMensajeTexto("51999888777", "Mensaje con timeout");
        server.stop(0);

        assertEquals(ResultadoLlamadaWhatsApp.TipoResultadoLlamada.RESULTADO_INCIERTO, resultado.tipo());
        assertTrue(resultado.errorDetalle().contains("timeout") || resultado.errorDetalle().contains("Resultado no confirmado"));
        assertEquals(1, serverCalls.get(), "No debe reintentar a ciegas ante timeout para evitar envíos duplicados al usuario");
    }

    @Test
    @DisplayName("Servidor simulado: Error transitorio 500 del proveedor retorna RESULTADO_INCIERTO con 1 sola llamada")
    void testServidorSimulado_Error500_ProduceResultadoIncierto() throws Exception {
        AtomicInteger serverCalls = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            serverCalls.incrementAndGet();
            byte[] response = "{\"error\": {\"message\": \"Server Error\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        String baseUrl = "http://localhost:" + port;

        WhatsAppCloudApiAdapter adapter = new WhatsAppCloudApiAdapter(
                true, baseUrl, "phone123", "access_tok", "verify_tok", "secret", 2000, 2000, objectMapper
        );

        ResultadoLlamadaWhatsApp resultado = adapter.enviarMensajeTexto("51999888777", "Mensaje con 500");
        server.stop(0);

        assertEquals(ResultadoLlamadaWhatsApp.TipoResultadoLlamada.RESULTADO_INCIERTO, resultado.tipo());
        assertEquals(1, serverCalls.get());
    }

    @Test
    @DisplayName("Servidor simulado: Rechazo confirmado 400 por error de cliente retorna FALLIDO_CONFIRMADO")
    void testServidorSimulado_Error400_ProduceFalloConfirmado() throws Exception {
        AtomicInteger serverCalls = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            serverCalls.incrementAndGet();
            byte[] response = "{\"error\": {\"message\": \"Invalid recipient\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        int port = server.getAddress().getPort();
        String baseUrl = "http://localhost:" + port;

        WhatsAppCloudApiAdapter adapter = new WhatsAppCloudApiAdapter(
                true, baseUrl, "phone123", "access_tok", "verify_tok", "secret", 2000, 2000, objectMapper
        );

        ResultadoLlamadaWhatsApp resultado = adapter.enviarMensajeTexto("51999888777", "Mensaje con 400");
        server.stop(0);

        assertEquals(ResultadoLlamadaWhatsApp.TipoResultadoLlamada.FALLIDO_CONFIRMADO, resultado.tipo());
        assertEquals(1, serverCalls.get());
    }

    // =========================================================================
    // 5. Flujo de Servicio Simulado (Pruebas de Componente con Mocks)
    // =========================================================================

    @Test
    @DisplayName("Flujo de Servicio: Intención previa y reserva atómica antes de contactar proveedor")
    void testServicio_IntencionPreviaRegistrada() {
        WhatsAppCloudClientPort clientMock = mock(WhatsAppCloudClientPort.class);
        when(clientMock.enviarMensajeTexto(anyString(), anyString(), anyString()))
                .thenReturn(ResultadoLlamadaWhatsApp.aceptado("wamid.success777"));

        MensajeWhatsApp intencion = MensajeWhatsApp.builder()
                .idSolicitud("req-12345")
                .telefonoDestinatario("51999888777")
                .estado(EstadoMensajeWhatsApp.EN_PROCESO_ENVIO)
                .tokenIntento("tok-intento-123")
                .build();

        when(repositoryPort.reservarEnvio(eq("req-12345"), eq("51999888777"), eq("Hola"), eq("operador1")))
                .thenReturn(new ReservaEnvioWhatsApp(intencion, true, "tok-intento-123"));

        MensajeWhatsApp enviado = MensajeWhatsApp.builder()
                .idSolicitud("req-12345")
                .idProveedor("wamid.success777")
                .estado(EstadoMensajeWhatsApp.ENVIADO)
                .build();

        when(repositoryPort.actualizarResultadoEnvio(eq("req-12345"), eq("tok-intento-123"), any(ResultadoLlamadaWhatsApp.class)))
                .thenReturn(enviado);

        com.lysandri.application.service.WhatsAppService service =
                new com.lysandri.application.service.WhatsAppService(clientMock, repositoryPort, objectMapper);

        MensajeWhatsApp resultado = service.enviarMensajeOperador("51999888777", "Hola", "operador1", "req-12345");

        assertEquals(EstadoMensajeWhatsApp.ENVIADO, resultado.getEstado());
        assertEquals("wamid.success777", resultado.getIdProveedor());
        verify(repositoryPort).reservarEnvio("req-12345", "51999888777", "Hola", "operador1");
        verify(clientMock).enviarMensajeTexto("51999888777", "Hola", "req-12345");
        verify(repositoryPort).actualizarResultadoEnvio(eq("req-12345"), eq("tok-intento-123"), any(ResultadoLlamadaWhatsApp.class));
    }

    @Test
    @DisplayName("Flujo Simulado: Verificación secuencial con mocks de deduplicación de llamadas")
    void testServicioSimulado_DeduplicacionSecuencialConMocks() throws Exception {
        WhatsAppCloudClientPort clientMock = mock(WhatsAppCloudClientPort.class);
        when(clientMock.enviarMensajeTexto(anyString(), anyString(), anyString()))
                .thenReturn(ResultadoLlamadaWhatsApp.aceptado("wamid.concurrent123"));

        com.lysandri.application.service.WhatsAppService service =
                new com.lysandri.application.service.WhatsAppService(clientMock, repositoryPort, objectMapper);

        String idSolicitud = "req-concurrent-888";

        MensajeWhatsApp primerIntento = MensajeWhatsApp.builder()
                .idSolicitud(idSolicitud)
                .estado(EstadoMensajeWhatsApp.EN_PROCESO_ENVIO)
                .tokenIntento("tok-1")
                .build();

        MensajeWhatsApp yaProcesado = MensajeWhatsApp.builder()
                .idSolicitud(idSolicitud)
                .idProveedor("wamid.concurrent123")
                .estado(EstadoMensajeWhatsApp.ENVIADO)
                .build();

        // Primera llamada obtiene autorización para despacho
        when(repositoryPort.reservarEnvio(eq(idSolicitud), anyString(), anyString(), anyString()))
                .thenReturn(new ReservaEnvioWhatsApp(primerIntento, true, "tok-1"))
                .thenReturn(new ReservaEnvioWhatsApp(yaProcesado, false, null)); // Segunda llamada no autorizada

        when(repositoryPort.actualizarResultadoEnvio(eq(idSolicitud), eq("tok-1"), any()))
                .thenReturn(yaProcesado);

        MensajeWhatsApp res1 = service.enviarMensajeOperador("51999888777", "Texto 1", "op1", idSolicitud);
        MensajeWhatsApp res2 = service.enviarMensajeOperador("51999888777", "Texto 1", "op1", idSolicitud);

        assertEquals(EstadoMensajeWhatsApp.ENVIADO, res1.getEstado());
        assertEquals(EstadoMensajeWhatsApp.ENVIADO, res2.getEstado());
        // Exactamente 1 llamada al proveedor de red
        verify(clientMock, times(1)).enviarMensajeTexto(anyString(), anyString(), eq(idSolicitud));
    }

    @Test
    @DisplayName("Flujo Simulado: Webhook concilia estado de mensaje registrado previamente")
    void testServicioSimulado_WebhookConciliaEstadoIncierto() {
        WhatsAppCloudClientPort clientMock = mock(WhatsAppCloudClientPort.class);
        when(clientMock.validarFirmaWebhook(anyString(), anyString())).thenReturn(true);

        com.lysandri.application.service.WhatsAppService service =
                new com.lysandri.application.service.WhatsAppService(clientMock, repositoryPort, objectMapper);

        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "statuses": [{
                          "id": "wamid.reconcile999",
                          "status": "delivered",
                          "timestamp": "1730000000",
                          "biz_opaque_callback_data": "req-uncertain-101"
                        }]
                      }
                    }]
                  }]
                }
                """;

        service.procesarWebhook(payload, "sha256=valid");

        // Verifica que se invocó la reconciliación con wamid y biz_opaque_callback_data
        verify(repositoryPort).actualizarEstadoConReconciliacion(
                eq("wamid.reconcile999"),
                eq("req-uncertain-101"),
                eq(EstadoMensajeWhatsApp.ENTREGADO),
                isNull()
        );
    }

    // =========================================================================
    // 6. Enmascaramiento de Teléfono en Dominio (Protección de Datos)
    // =========================================================================

    @Test
    @DisplayName("Protección de datos: enmascaramiento correcto de números en logs")
    void testEnmascaramientoTelefono() {
        assertEquals("+519****8777", MensajeWhatsApp.enmascararTelefono("+51 999 888 777"));
        assertEquals("5199****6789", MensajeWhatsApp.enmascararTelefono("51991236789"));
        assertEquals("***", MensajeWhatsApp.enmascararTelefono("123"));
        assertEquals("[desconocido]", MensajeWhatsApp.enmascararTelefono(null));
    }
}

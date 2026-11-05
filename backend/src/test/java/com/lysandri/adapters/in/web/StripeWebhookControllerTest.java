package com.lysandri.adapters.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.application.service.OrderFinancialTransactionService;
import com.lysandri.application.service.StripeIdempotencyService;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private BuyCourseUseCase buyCourseUseCase;

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private StripeIdempotencyService stripeIdempotencyService;

    @Mock
    private OrderFinancialTransactionService orderFinancialTransactionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StripeWebhookController stripeWebhookController;

    private static final String TEST_SECRET = "whsec_test_secret_for_junit_tests_12345";
    private static final String TEST_OWNER_TOKEN = "test-token-uuid-1234";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stripeWebhookController, "webhookSecret", TEST_SECRET);
        lenient().when(orderRepository.guardar(any(Orden.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(orderRepository.buscarPorStripePaymentIntentId(anyString())).thenReturn(Optional.empty());
        lenient().when(stripeIdempotencyService.marcarCompletado(any(), any(), any(), any(), any(), any())).thenReturn(true);
        lenient().when(orderFinancialTransactionService.registrarPagoFallidoOExpirado(any(), any(), any(), any()))
                .thenAnswer(invocation -> Orden.builder()
                        .codigoOrden(invocation.getArgument(0))
                        .estadoOrden(invocation.getArgument(2))
                        .build());
    }

    private String generarStripeSignatureHeader(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return "t=" + timestamp + ",v1=" + hex.toString();
    }

    @Test
    @DisplayName("Webhook sin cabecera Stripe-Signature debe rechazar con HTTP 400 Bad Request")
    void handleStripeWebhook_sinFirma_retornaBadRequest() {
        String payload = "{\"type\":\"checkout.session.completed\"}";

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Firma Stripe-Signature ausente"));
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Webhook con firma criptográfica inválida debe rechazar con HTTP 400 Bad Request")
    void handleStripeWebhook_firmaInvalida_retornaBadRequest() {
        String payload = "{\"type\":\"checkout.session.completed\"}";
        String invalidHeader = "t=123456789,v1=bad_signature_hash";

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, invalidHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Firma de Stripe inválida"));
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Firma válida y evento checkout.session.completed nuevo procesa orden y marca completado")
    void handleStripeWebhook_firmaValidaPagoCompletado_procesaOrden() throws Exception {
        String sessionId = "cs_live_test_session_999";
        String payload = "{"
                + "\"id\":\"evt_test_1\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-999\",\"amount_total\":15000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_test_1"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-999")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(1L)
                .codigoOrden("ORD-999")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("150.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(buyCourseUseCase).procesarPagoExitoso("ORD-999");
        verify(stripeIdempotencyService).marcarCompletado(eq("evt_test_1"), eq(TEST_OWNER_TOKEN), eq(1L), eq("ORD-999"), eq(sessionId), isNull());
    }

    @Test
    @DisplayName("Evento duplicado ya registrado como COMPLETADO retorna HTTP 200 sin reprocesar")
    void handleStripeWebhook_eventoDuplicado_noProcesaDobleVez() throws Exception {
        String sessionId = "cs_live_already_paid_123";
        String payload = "{"
                + "\"id\":\"evt_test_2\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_test_2"), eq("checkout.session.completed"), eq(sessionId), isNull(), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.yaCompletado());

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Evento duplicado ya completado"));
        verifyNoInteractions(buyCourseUseCase);
        verify(orderRepository, never()).buscarPorCualquierIdentificador(anyString());
    }

    @Test
    @DisplayName("Evento en concurrencia (EN_PROCESO) retorna HTTP 429 Retry-After para reintento controlado")
    void handleStripeWebhook_eventoEnProceso_retornaTooManyRequests429() throws Exception {
        String sessionId = "cs_live_in_progress_777";
        String payload = "{"
                + "\"id\":\"evt_test_concurrent\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_test_concurrent"), eq("checkout.session.completed"), eq(sessionId), isNull(), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.enProceso(OffsetDateTime.now().plusMinutes(2)));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertTrue(response.getHeaders().containsKey("Retry-After"));
        assertEquals("5", response.getHeaders().getFirst("Retry-After"));
        assertTrue(response.getBody().contains("Evento en procesamiento activo"));
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Importe ausente en evento de confirmación rechaza con HTTP 400 y no confirma orden")
    void handleStripeWebhook_importeAusente_rechazaConfirmacionCon400() throws Exception {
        String sessionId = "cs_sin_importe";
        // Payload sin amount_total
        String payload = "{"
                + "\"id\":\"evt_no_amount\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-NO-AMT\",\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_no_amount"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-NO-AMT")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(10L)
                .codigoOrden("ORD-NO-AMT")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Importe ausente o inválido"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarFallido(eq("evt_no_amount"), eq(TEST_OWNER_TOKEN), contains("Importe ausente"));
    }

    @Test
    @DisplayName("Importe cero en evento de confirmación rechaza con HTTP 400 y no confirma orden")
    void handleStripeWebhook_importeCero_rechazaConfirmacionCon400() throws Exception {
        String sessionId = "cs_importe_cero";
        String payload = "{"
                + "\"id\":\"evt_zero_amount\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-ZERO\",\"amount_total\":0,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_zero_amount"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-ZERO")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(11L)
                .codigoOrden("ORD-ZERO")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Importe ausente o inválido"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarFallido(eq("evt_zero_amount"), eq(TEST_OWNER_TOKEN), contains("Importe ausente o cero"));
    }

    @Test
    @DisplayName("Moneda ausente en evento de confirmación rechaza con HTTP 400 y no confirma orden")
    void handleStripeWebhook_monedaAusente_rechazaConfirmacionCon400() throws Exception {
        String sessionId = "cs_sin_moneda";
        String payload = "{"
                + "\"id\":\"evt_no_curr\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-NO-CURR\",\"amount_total\":10000}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_no_curr"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-NO-CURR")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(13L)
                .codigoOrden("ORD-NO-CURR")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Moneda ausente"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarFallido(eq("evt_no_curr"), eq(TEST_OWNER_TOKEN), contains("Moneda obligatoria ausente"));
    }

    @Test
    @DisplayName("Discrepancia de monto inferior/superior rechaza confirmación con HTTP 400 y no matricula")
    void handleStripeWebhook_discrepanciaMonto_rechazaConfirmacionYMarcaFallido() throws Exception {
        String sessionId = "cs_monto_mismatch";
        String payload = "{"
                + "\"id\":\"evt_monto_1\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-MONTO\",\"amount_total\":8000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_monto_1"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-MONTO")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(12L)
                .codigoOrden("ORD-MONTO")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Discrepancia financiera de importe"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarFallido(eq("evt_monto_1"), eq(TEST_OWNER_TOKEN), contains("Discrepancia de monto"));
    }

    @Test
    @DisplayName("Discrepancia de moneda rechaza confirmación con HTTP 400 y no matricula")
    void handleStripeWebhook_discrepanciaMoneda_rechazaConfirmacionYMarcaFallido() throws Exception {
        String sessionId = "cs_moneda_mismatch";
        String payload = "{"
                + "\"id\":\"evt_moneda_1\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-MONEDA\",\"amount_total\":10000,\"currency\":\"eur\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_moneda_1"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-MONEDA")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(14L)
                .codigoOrden("ORD-MONEDA")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Discrepancia financiera de moneda"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarFallido(eq("evt_moneda_1"), eq(TEST_OWNER_TOKEN), contains("Discrepancia de moneda"));
    }

    @Test
    @DisplayName("Discrepancia de relación de orden rechaza confirmación con HTTP 400")
    void handleStripeWebhook_discrepanciaCodigoOrden_rechazaConfirmacion() throws Exception {
        String sessionId = "cs_orden_mismatch";
        String payload = "{"
                + "\"id\":\"evt_orden_1\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-HACK\",\"amount_total\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_orden_1"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-HACK")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(18L)
                .codigoOrden("ORD-REAL")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Discrepancia de orden vinculada"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarFallido(eq("evt_orden_1"), eq(TEST_OWNER_TOKEN), contains("Discrepancia: El pago refiere orden ORD-HACK pero se localizó ORD-REAL"));
    }

    @Test
    @DisplayName("PaymentIntent ya vinculado a otra orden diferente rechaza con HTTP 409 Conflict")
    void handleStripeWebhook_paymentIntentYaVinculadoAOtraOrden_rechazaConConflict409() throws Exception {
        String intentId = "pi_already_used_777";
        String payload = "{"
                + "\"id\":\"evt_conflict_pi\","
                + "\"object\":\"event\","
                + "\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"id\":\"" + intentId + "\",\"amount\":10000,\"currency\":\"usd\",\"metadata\":{\"codigoOrden\":\"ORD-ACTUAL\"}}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_conflict_pi"), eq("payment_intent.succeeded"), isNull(), eq(intentId), eq("ORD-ACTUAL")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden ordenActual = Orden.builder()
                .idOrden(50L)
                .codigoOrden("ORD-ACTUAL")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        Orden ordenPrevia = Orden.builder()
                .idOrden(99L)
                .codigoOrden("ORD-PREVIA")
                .stripePaymentIntentId(intentId)
                .build();

        when(orderRepository.buscarPorCodigo("ORD-ACTUAL")).thenReturn(Optional.of(ordenActual));
        when(orderRepository.buscarPorStripePaymentIntentId(intentId)).thenReturn(Optional.of(ordenPrevia));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("Conflicto de identificadores"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarFallido(eq("evt_conflict_pi"), eq(TEST_OWNER_TOKEN), contains("PaymentIntent ya vinculado a otra orden distinta"));
    }

    @Test
    @DisplayName("Sesión incompatible con la ya existente en la orden rechaza con HTTP 409 Conflict")
    void handleStripeWebhook_sesionIncompatibleConOrden_rechazaConConflict409() throws Exception {
        String sessionId = "cs_nueva_diferente";
        String payload = "{"
                + "\"id\":\"evt_conflict_sess\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-SESS\",\"amount_total\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_conflict_sess"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-SESS")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden ordenExistente = Orden.builder()
                .idOrden(60L)
                .codigoOrden("ORD-SESS")
                .stripeSessionId("cs_original_previa") // Ya tiene una sesión asignada diferente
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.empty());
        when(orderRepository.buscarPorCodigo("ORD-SESS")).thenReturn(Optional.of(ordenExistente));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("Conflicto de sesión de pago"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarFallido(eq("evt_conflict_sess"), eq(TEST_OWNER_TOKEN), contains("Sesión de checkout incompatible"));
    }

    @Test
    @DisplayName("Fallo al marcar completado por lease expirado o token perdido retorna HTTP 409 Conflict")
    void handleStripeWebhook_marcarCompletadoFallaPorLeaseExpirado_retornaConflict409() throws Exception {
        String sessionId = "cs_lease_expired";
        String payload = "{"
                + "\"id\":\"evt_lease_exp\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"client_reference_id\":\"ORD-EXP\",\"amount_total\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_lease_exp"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-EXP")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(70L)
                .codigoOrden("ORD-EXP")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));
        // Simular que marcarCompletado devuelve false porque el lease expiró durante la ejecución
        when(stripeIdempotencyService.marcarCompletado(eq("evt_lease_exp"), eq(TEST_OWNER_TOKEN), eq(70L), eq("ORD-EXP"), eq(sessionId), isNull()))
                .thenReturn(false);

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("el arrendamiento del evento expiró"));
    }

    @Test
    @DisplayName("Sesión checkout completada pero con payment_status 'unpaid' no debe procesar matrícula")
    void handleStripeWebhook_checkoutSessionUnpaid_noMarcaComoPagado() throws Exception {
        String sessionId = "cs_async_unpaid_321";
        String payload = "{"
                + "\"id\":\"evt_async_1\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"unpaid\",\"client_reference_id\":\"ORD-ASYNC\",\"amount_total\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_async_1"), eq("checkout.session.completed"), eq(sessionId), isNull(), eq("ORD-ASYNC")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(5L)
                .codigoOrden("ORD-ASYNC")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("esperando pago asíncrono"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarCompletado(eq("evt_async_1"), eq(TEST_OWNER_TOKEN), eq(5L), eq("ORD-ASYNC"), eq(sessionId), isNull());
    }

    @Test
    @DisplayName("Evento payment_intent.succeeded relaciona payment_intent_id y procesa la orden")
    void handleStripeWebhook_paymentIntentSucceeded_resuelveOrdenPorPaymentIntentId() throws Exception {
        String intentId = "pi_succ_8888";
        String payload = "{"
                + "\"id\":\"evt_pi_succ_1\","
                + "\"object\":\"event\","
                + "\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"id\":\"" + intentId + "\",\"status\":\"succeeded\",\"amount\":20000,\"currency\":\"usd\",\"metadata\":{\"codigoOrden\":\"ORD-PI-888\"}}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_pi_succ_1"), eq("payment_intent.succeeded"), isNull(), eq(intentId), eq("ORD-PI-888")))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(88L)
                .codigoOrden("ORD-PI-888")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("200.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(intentId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(buyCourseUseCase).procesarPagoExitoso("ORD-PI-888");
        verify(stripeIdempotencyService).marcarCompletado(eq("evt_pi_succ_1"), eq(TEST_OWNER_TOKEN), eq(88L), eq("ORD-PI-888"), isNull(), eq(intentId));
    }

    @Test
    @DisplayName("Fase 2: checkout.session.completed sin campo payment_status debe rechazar con HTTP 400 Bad Request")
    void handleStripeWebhook_checkoutSession_paymentStatusAusente_retornaBadRequest() throws Exception {
        String sessionId = "cs_no_ps_123";
        String payload = "{"
                + "\"id\":\"evt_no_ps\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"amount_total\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_no_ps"), eq("checkout.session.completed"), eq(sessionId), isNull(), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(1L)
                .codigoOrden("ORD-NO-PS")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Estado de pago ausente"));
        verify(stripeIdempotencyService).marcarFallido(eq("evt_no_ps"), eq(TEST_OWNER_TOKEN), anyString());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Fase 2: checkout.session.completed con payment_status nulo debe rechazar con HTTP 400 Bad Request")
    void handleStripeWebhook_checkoutSession_paymentStatusNulo_retornaBadRequest() throws Exception {
        String sessionId = "cs_null_ps_123";
        String payload = "{"
                + "\"id\":\"evt_null_ps\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":null,\"amount_total\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_null_ps"), eq("checkout.session.completed"), eq(sessionId), isNull(), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(2L)
                .codigoOrden("ORD-NULL-PS")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Estado de pago ausente"));
        verify(stripeIdempotencyService).marcarFallido(eq("evt_null_ps"), eq(TEST_OWNER_TOKEN), anyString());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Fase 2: checkout.session.completed con payment_status no_payment_required en orden con costo debe rechazar con HTTP 400")
    void handleStripeWebhook_checkoutSession_noPaymentRequired_retornaBadRequest() throws Exception {
        String sessionId = "cs_nopayreq_123";
        String payload = "{"
                + "\"id\":\"evt_nopayreq\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"no_payment_required\",\"amount_total\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_nopayreq"), eq("checkout.session.completed"), eq(sessionId), isNull(), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(3L)
                .codigoOrden("ORD-NOPAYREQ")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Estado no_payment_required no permitido"));
        verify(stripeIdempotencyService).marcarFallido(eq("evt_nopayreq"), eq(TEST_OWNER_TOKEN), anyString());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Fase 2: payment_intent.succeeded sin campo status debe rechazar con HTTP 400 Bad Request")
    void handleStripeWebhook_paymentIntent_statusAusente_retornaBadRequest() throws Exception {
        String intentId = "pi_nostatus_123";
        String payload = "{"
                + "\"id\":\"evt_pi_nostatus\","
                + "\"object\":\"event\","
                + "\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"id\":\"" + intentId + "\",\"amount\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_pi_nostatus"), eq("payment_intent.succeeded"), isNull(), eq(intentId), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(4L)
                .codigoOrden("ORD-PI-NOSTATUS")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(intentId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Estado de Payment Intent ausente"));
        verify(stripeIdempotencyService).marcarFallido(eq("evt_pi_nostatus"), eq(TEST_OWNER_TOKEN), anyString());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Fase 2: payment_intent.succeeded con status distinto de succeeded debe rechazar con HTTP 400 Bad Request")
    void handleStripeWebhook_paymentIntent_statusInvalido_retornaBadRequest() throws Exception {
        String intentId = "pi_not_succ_123";
        String payload = "{"
                + "\"id\":\"evt_pi_not_succ\","
                + "\"object\":\"event\","
                + "\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"id\":\"" + intentId + "\",\"status\":\"requires_payment_method\",\"amount\":10000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_pi_not_succ"), eq("payment_intent.succeeded"), isNull(), eq(intentId), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden orden = Orden.builder()
                .idOrden(5L)
                .codigoOrden("ORD-PI-NOT-SUCC")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("100.00"))
                .moneda("USD")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(intentId)).thenReturn(Optional.of(orden));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("Estado de Payment Intent no es succeeded"));
        verify(stripeIdempotencyService).marcarFallido(eq("evt_pi_not_succ"), eq(TEST_OWNER_TOKEN), anyString());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Evento payment_intent.payment_failed actualiza orden pendiente a FALLIDO")
    void handleStripeWebhook_pagoFallido_actualizaOrdenAFallidoYSinMatricula() throws Exception {
        String intentId = "pi_failed_payment_456";
        String payload = "{"
                + "\"id\":\"evt_test_3\","
                + "\"object\":\"event\","
                + "\"type\":\"payment_intent.payment_failed\","
                + "\"data\":{\"object\":{\"id\":\"" + intentId + "\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_test_3"), eq("payment_intent.payment_failed"), isNull(), eq(intentId), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden ordenPendiente = Orden.builder()
                .idOrden(20L)
                .codigoOrden("ORD-20")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(intentId)).thenReturn(Optional.of(ordenPendiente));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EstadoOrden.FALLIDO, ordenPendiente.getEstadoOrden());
        verify(orderFinancialTransactionService).registrarPagoFallidoOExpirado(eq("ORD-20"), eq(intentId), eq(EstadoOrden.FALLIDO), anyString());
        verify(stripeIdempotencyService).marcarCompletado(eq("evt_test_3"), eq(TEST_OWNER_TOKEN), eq(20L), eq("ORD-20"), isNull(), eq(intentId));
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Evento tardío de fallo o expiración no revierte orden que ya se encuentra PAGADA")
    void handleStripeWebhook_eventoTardioFallo_noRevierteOrdenYaPagada() throws Exception {
        String sessionId = "cs_expired_late_99";
        String payload = "{"
                + "\"id\":\"evt_test_late\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.expired\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_test_late"), eq("checkout.session.expired"), eq(sessionId), isNull(), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden ordenPagada = Orden.builder()
                .idOrden(30L)
                .codigoOrden("ORD-30")
                .estadoOrden(EstadoOrden.PAGADO)
                .stripeSessionId(sessionId)
                .moodleMatriculaSincronizada(true)
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(ordenPagada));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Evento tardío ignorado"));
        assertEquals(EstadoOrden.PAGADO, ordenPagada.getEstadoOrden());
        verify(orderRepository, never()).guardar(any(Orden.class));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarCompletado(eq("evt_test_late"), eq(TEST_OWNER_TOKEN), eq(30L), eq("ORD-30"), eq(sessionId), isNull());
    }

    @Test
    @DisplayName("Orden ya pagada y matriculada previamente confirma éxito idempotentemente sin re-matricular")
    void handleStripeWebhook_ordenYaPagadaYMatriculada_retornaOkIdempotente() throws Exception {
        String sessionId = "cs_already_done_555";
        String payload = "{"
                + "\"id\":\"evt_done_555\","
                + "\"object\":\"event\","
                + "\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\"" + sessionId + "\",\"payment_status\":\"paid\",\"amount_total\":5000,\"currency\":\"usd\"}}"
                + "}";

        String sigHeader = generarStripeSignatureHeader(payload, TEST_SECRET);

        when(stripeIdempotencyService.iniciarProcesamiento(eq("evt_done_555"), eq("checkout.session.completed"), eq(sessionId), isNull(), isNull()))
                .thenReturn(StripeIdempotencyService.ResultadoAdquisicion.adquirido(TEST_OWNER_TOKEN, OffsetDateTime.now().plusMinutes(3)));

        Orden ordenPagada = Orden.builder()
                .idOrden(55L)
                .codigoOrden("ORD-55")
                .estadoOrden(EstadoOrden.PAGADO)
                .stripeSessionId(sessionId)
                .total(new BigDecimal("50.00"))
                .moneda("USD")
                .moodleMatriculaSincronizada(true)
                .build();

        when(orderRepository.buscarPorCualquierIdentificador(sessionId)).thenReturn(Optional.of(ordenPagada));

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(payload, sigHeader);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Orden ya pagada y matriculada"));
        verifyNoInteractions(buyCourseUseCase);
        verify(stripeIdempotencyService).marcarCompletado(eq("evt_done_555"), eq(TEST_OWNER_TOKEN), eq(55L), eq("ORD-55"), eq(sessionId), isNull());
    }
}


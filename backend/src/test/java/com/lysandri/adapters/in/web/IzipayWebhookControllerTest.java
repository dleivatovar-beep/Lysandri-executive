package com.lysandri.adapters.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias: IzipayWebhookController con verificación estricta de seguridad")
class IzipayWebhookControllerTest {

    @Mock
    private BuyCourseUseCase buyCourseUseCase;

    @Mock
    private OrderRepositoryPort orderRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private IzipayWebhookController izipayWebhookController;

    private static final String TEST_KEY = "test_secret_key_12345";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(izipayWebhookController, "sha256Key", TEST_KEY);
    }

    private String calcularHmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }

    private Orden crearOrdenValida(String codigo, String metodo, BigDecimal total, String moneda) {
        return Orden.builder()
                .idOrden(100L)
                .codigoOrden(codigo)
                .metodoPago(metodo)
                .total(total)
                .moneda(moneda)
                .estadoOrden(EstadoOrden.PENDIENTE)
                .build();
    }

    @Test
    @DisplayName("Firma ausente o nula: rechaza con 401 Unauthorized")
    void recibirIpn_firmaAusente_retornaUnauthorized() {
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-100\"}}";

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Firma inválida o manipulada: rechaza con 401 Unauthorized")
    void recibirIpn_firmaInvalida_retornaUnauthorized() {
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-100\"}}";
        String invalidHash = "hash_invalido_hacker";

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, invalidHash);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Configuración incompleta: clave SHA256 no configurada retorna 500")
    void recibirIpn_claveNoConfigurada_retornaInternalServerError() {
        ReflectionTestUtils.setField(izipayWebhookController, "sha256Key", "");
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-100\"}}";

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, "dummy_hash");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Orden no encontrada: retorna 404 Not Found")
    void recibirIpn_ordenNoEncontrada_retornaNotFound() throws Exception {
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-999\",\"orderTotalAmount\":79900,\"orderCurrency\":\"PEN\"}}";
        String validHash = calcularHmac(answer, TEST_KEY);

        when(orderRepository.buscarPorCualquierIdentificador("ORD-999")).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, validHash);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Orden con método de pago manual (YAPE/TRANSFERENCIA): rechaza con 400 Bad Request")
    void recibirIpn_ordenPagoManual_retornaBadRequest() throws Exception {
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-YAPE-100\",\"orderTotalAmount\":79900,\"orderCurrency\":\"PEN\"}}";
        String validHash = calcularHmac(answer, TEST_KEY);

        Orden ordenManual = crearOrdenValida("ORD-YAPE-100", "YAPE", new BigDecimal("799.00"), "PEN");
        when(orderRepository.buscarPorCualquierIdentificador("ORD-YAPE-100")).thenReturn(Optional.of(ordenManual));

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, validHash);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("manual"));
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Orden configurada para Stripe recibida en webhook Izipay: rechaza con 400 Bad Request")
    void recibirIpn_ordenStripeRecibidaEnIzipay_retornaBadRequest() throws Exception {
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-STRIPE-100\",\"orderTotalAmount\":79900,\"orderCurrency\":\"USD\"}}";
        String validHash = calcularHmac(answer, TEST_KEY);

        Orden ordenStripe = crearOrdenValida("ORD-STRIPE-100", "STRIPE", new BigDecimal("799.00"), "USD");
        when(orderRepository.buscarPorCualquierIdentificador("ORD-STRIPE-100")).thenReturn(Optional.of(ordenStripe));

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, validHash);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("Stripe"));
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Discrepancia en importe (monto alterado en webhook): rechaza con 400 Bad Request")
    void recibirIpn_importeDiscrepante_retornaBadRequest() throws Exception {
        // En webhook viene 5000 centavos (S/. 50.00) pero la orden es por S/. 799.00 (79900 centavos)
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-100\",\"orderTotalAmount\":5000,\"orderCurrency\":\"PEN\"}}";
        String validHash = calcularHmac(answer, TEST_KEY);

        Orden orden = crearOrdenValida("ORD-100", "IZIPAY", new BigDecimal("799.00"), "PEN");
        when(orderRepository.buscarPorCualquierIdentificador("ORD-100")).thenReturn(Optional.of(orden));

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, validHash);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("importe"));
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Discrepancia en moneda: rechaza con 400 Bad Request")
    void recibirIpn_monedaDiscrepante_retornaBadRequest() throws Exception {
        // En webhook viene USD pero la orden está en PEN
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-100\",\"orderTotalAmount\":79900,\"orderCurrency\":\"USD\"}}";
        String validHash = calcularHmac(answer, TEST_KEY);

        Orden orden = crearOrdenValida("ORD-100", "IZIPAY", new BigDecimal("799.00"), "PEN");
        when(orderRepository.buscarPorCualquierIdentificador("ORD-100")).thenReturn(Optional.of(orden));

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, validHash);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("moneda"));
        verifyNoInteractions(buyCourseUseCase);
    }

    @Test
    @DisplayName("Firma válida, orden coherente y estado PAID: procesa pago exitoso")
    void recibirIpn_firmaValidaYEstadoPaid_procesaPagoExitoso() throws Exception {
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-100\",\"orderTotalAmount\":79900,\"orderCurrency\":\"PEN\"}}";
        String validHash = calcularHmac(answer, TEST_KEY);

        Orden orden = crearOrdenValida("ORD-100", "IZIPAY", new BigDecimal("799.00"), "PEN");
        when(orderRepository.buscarPorCualquierIdentificador("ORD-100")).thenReturn(Optional.of(orden));

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, validHash);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(buyCourseUseCase).procesarPagoExitoso("ORD-100");
    }

    @Test
    @DisplayName("Idempotencia: si la orden ya está PAGADA, responde OK sin reprocesar")
    void recibirIpn_ordenYaPagada_respondeOkIdempotente() throws Exception {
        String answer = "{\"orderStatus\":\"PAID\",\"orderDetails\":{\"orderId\":\"ORD-100\",\"orderTotalAmount\":79900,\"orderCurrency\":\"PEN\"}}";
        String validHash = calcularHmac(answer, TEST_KEY);

        Orden ordenYaPagada = crearOrdenValida("ORD-100", "IZIPAY", new BigDecimal("799.00"), "PEN");
        ordenYaPagada.setEstadoOrden(EstadoOrden.PAGADO);
        ordenYaPagada.setMoodleMatriculaSincronizada(true);
        when(orderRepository.buscarPorCualquierIdentificador("ORD-100")).thenReturn(Optional.of(ordenYaPagada));

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, validHash);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().get("message").toString().contains("ya procesada"));
        verify(buyCourseUseCase, never()).procesarPagoExitoso(anyString());
    }

    @Test
    @DisplayName("Firma válida y estado REFUSED: actualiza orden a FALLIDO")
    void recibirIpn_firmaValidaYEstadoRefused_actualizaOrdenAFallido() throws Exception {
        String answer = "{\"orderStatus\":\"REFUSED\",\"orderDetails\":{\"orderId\":\"ORD-200\",\"orderTotalAmount\":79900,\"orderCurrency\":\"PEN\"}}";
        String validHash = calcularHmac(answer, TEST_KEY);

        Orden ordenPendiente = crearOrdenValida("ORD-200", "IZIPAY", new BigDecimal("799.00"), "PEN");
        when(orderRepository.buscarPorCualquierIdentificador("ORD-200")).thenReturn(Optional.of(ordenPendiente));

        ResponseEntity<Map<String, Object>> response = izipayWebhookController.handleIzipayWebhook(null, answer, validHash);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(EstadoOrden.FALLIDO, ordenPendiente.getEstadoOrden());
        verify(orderRepository).guardar(ordenPendiente);
    }
}

package com.lysandri.adapters.out.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.out.PaymentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Adaptador de salida para la pasarela de pagos izipay Online (Lyra Network).
 * Implementa PaymentPort bajo Arquitectura Hexagonal.
 * Soporta modo Sandbox (pruebas locales/dev) y modo Producción con API REST oficial.
 */
@Slf4j
@Primary
@Component
public class IzipayPaymentAdapter implements PaymentPort {

    private final String shopId;
    private final String apiKey;
    private final String publicKey;
    private final String sha256Key;
    private final String endpoint;
    private final String successUrl;
    private final String cancelUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public IzipayPaymentAdapter(
            @Value("${izipay.shop-id:test_shop_lysandri}") String shopId,
            @Value("${izipay.api-key:test_password_lysandri_key}") String apiKey,
            @Value("${izipay.public-key:test_public_key_lysandri}") String publicKey,
            @Value("${izipay.sha256-key:test_hmac_sha256_key}") String sha256Key,
            @Value("${izipay.endpoint:https://api.micuentaweb.pe}") String endpoint,
            @Value("${izipay.success-url:http://localhost:3000/checkout/success?session_id={CHECKOUT_SESSION_ID}}") String successUrl,
            @Value("${izipay.cancel-url:http://localhost:3000/checkout/cancel}") String cancelUrl) {
        this.shopId = shopId;
        this.apiKey = apiKey;
        this.publicKey = publicKey;
        this.sha256Key = sha256Key;
        this.endpoint = endpoint;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public PaymentSessionResult crearSesionPago(Orden orden, String customerEmail, String customerName) {
        log.info("Creando sesión de pago en izipay Online para orden {} - Cliente: {} ({})",
                orden.getCodigoOrden(), customerName, customerEmail);

        boolean isMockOrDev = shopId == null || shopId.startsWith("test_shop") || apiKey == null || apiKey.startsWith("test_password");

        // Modo Entorno Local / Sandbox Simulado
        if (isMockOrDev) {
            String sessionId = "izisess_" + UUID.randomUUID().toString().replace("-", "");
            String redirectUrl = successUrl.replace("{CHECKOUT_SESSION_ID}", sessionId);
            String mockTxId = "izitx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String mockFormToken = "DEMO_IZIPAY_FORM_TOKEN_" + UUID.randomUUID();

            log.info("Sesión izipay Online generada en entorno Sandbox local: {} (Tx: {})", sessionId, mockTxId);
            return new PaymentSessionResult(sessionId, redirectUrl, mockTxId, mockFormToken, publicKey);
        }

        // Modo Producción / Sandbox Oficial izipay REST API
        try {
            String createPaymentUrl = endpoint + "/api-payment/V4/Charge/CreatePayment";

            long amountInCents = orden.getTotal().multiply(BigDecimal.valueOf(100)).longValue();
            String currency = orden.getMoneda() != null ? orden.getMoneda().toUpperCase() : "USD";

            String[] nameParts = (customerName != null ? customerName.trim() : "Cliente Ejecutivo").split(" ", 2);
            String firstName = nameParts[0];
            String lastName = nameParts.length > 1 ? nameParts[1] : "Lysandri";

            Map<String, Object> billingDetails = new HashMap<>();
            billingDetails.put("firstName", firstName);
            billingDetails.put("lastName", lastName);

            Map<String, Object> customer = new HashMap<>();
            customer.put("email", customerEmail);
            customer.put("billingDetails", billingDetails);

            String redirectUrl = successUrl.replace("{CHECKOUT_SESSION_ID}", orden.getCodigoOrden());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("amount", amountInCents);
            requestBody.put("currency", currency);
            requestBody.put("orderId", orden.getCodigoOrden());
            requestBody.put("customer", customer);
            requestBody.put("returnUrl", redirectUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String auth = shopId + ":" + apiKey;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + new String(encodedAuth));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(createPaymentUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String status = root.path("status").asText();

                if ("SUCCESS".equalsIgnoreCase(status)) {
                    String formToken = root.path("answer").path("formToken").asText();
                    String orderId = orden.getCodigoOrden();
                    log.info("formToken obtenido exitosamente de izipay Online para orden {}", orderId);
                    return new PaymentSessionResult(orderId, redirectUrl, orderId, formToken, publicKey);
                } else {
                    String errorCode = root.path("answer").path("errorCode").asText();
                    String errorMessage = root.path("answer").path("errorMessage").asText();
                    log.error("Respuesta fallida de izipay Online: {} - {}", errorCode, errorMessage);
                    throw new RuntimeException("Error en izipay Online: " + errorMessage);
                }
            } else {
                throw new RuntimeException("Código HTTP inesperado de izipay: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Fallo al conectar con la API REST de izipay Online: {}", e.getMessage(), e);
            throw new RuntimeException("Error al procesar la sesión de pago con izipay Online: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verificarPagoCompletado(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;

        boolean isMockOrDev = shopId == null || shopId.startsWith("test_shop") || apiKey == null || apiKey.startsWith("test_password");
        if (isMockOrDev) {
            return true; // En modo pruebas local se aprueba automáticamente
        }

        try {
            String checkUrl = endpoint + "/api-payment/V4/Charge/Get";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String auth = shopId + ":" + apiKey;
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + new String(encodedAuth));

            Map<String, Object> requestBody = Map.of("orderId", sessionId);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(checkUrl, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String orderStatus = root.path("answer").path("orderStatus").asText();
                return "PAID".equalsIgnoreCase(orderStatus);
            }
            return false;
        } catch (Exception e) {
            log.error("Error consultando estado de transacción en izipay Online para {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getSha256Key() {
        return sha256Key;
    }
}

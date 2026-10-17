package com.lysandri.adapters.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Recepción de eventos asíncronos y notificaciones IPN de pasarelas de pago")
public class IzipayWebhookController {

    private final BuyCourseUseCase buyCourseUseCase;
    private final ObjectMapper objectMapper;

    @Value("${izipay.sha256-key:test_hmac_sha256_key}")
    private String sha256Key;

    /**
     * Webhook / Notificación Instantánea de Pago (IPN) de izipay Online.
     * Recibe los eventos de pago completado en tiempo real y desencadena:
     * 1. Registro de la orden como PAGADA.
     * 2. Creación de usuario y matrícula automática en Moodle LMS.
     * 3. Emisión de Comprobante electrónico SUNAT.
     * 4. Notificación y envío de credenciales por WhatsApp y Email.
     */
    @PostMapping({"/izipay", "/izipay/ipn"})
    @Operation(summary = "Webhook oficial IPN de izipay Online para confirmación de pagos y matrícula automática")
    public ResponseEntity<Map<String, Object>> handleIzipayWebhook(
            @RequestBody(required = false) String rawPayload,
            @RequestParam(name = "kr-answer", required = false) String krAnswerParam,
            @RequestParam(name = "kr-hash", required = false) String krHashParam) {

        log.info("Notificación IPN recibida de izipay Online");

        try {
            String answerJson = krAnswerParam;
            String receivedHash = krHashParam;

            // Si los datos vienen en el body JSON
            if (rawPayload != null && !rawPayload.isBlank()) {
                JsonNode root = objectMapper.readTree(rawPayload);
                if (root.has("kr-answer")) {
                    answerJson = root.path("kr-answer").asText();
                    receivedHash = root.path("kr-hash").asText();
                } else if (root.has("orderId") || root.has("orderDetails")) {
                    answerJson = rawPayload;
                }
            }

            if (answerJson == null || answerJson.isBlank()) {
                log.warn("Payload de izipay Online vacío o no reconocido");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "ERROR", "message", "Payload vacío"));
            }

            // Validación de firma HMAC-SHA256 si la llave no es mock
            if (sha256Key != null && !sha256Key.startsWith("test_hmac") && receivedHash != null) {
                if (!verificarFirmaHmac(answerJson, receivedHash, sha256Key)) {
                    log.error("Firma HMAC de izipay inválida o adulterada");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("status", "ERROR", "message", "Firma HMAC inválida"));
                }
            }

            JsonNode answerNode = objectMapper.readTree(answerJson);
            String orderStatus = answerNode.path("orderStatus").asText("");
            if (orderStatus.isBlank() && answerNode.has("orderCycle")) {
                orderStatus = answerNode.path("orderCycle").asText();
            }

            String orderId = answerNode.path("orderDetails").path("orderId").asText("");
            if (orderId.isBlank()) {
                orderId = answerNode.path("orderId").asText("");
            }
            if (orderId.isBlank() && answerNode.has("id")) {
                orderId = answerNode.path("id").asText();
            }

            log.info("IPN izipay procesada - OrderId: {}, Estado: {}", orderId, orderStatus);

            boolean isPaid = "PAID".equalsIgnoreCase(orderStatus)
                    || "SUCCESS".equalsIgnoreCase(orderStatus)
                    || "CAPTURED".equalsIgnoreCase(orderStatus)
                    || orderStatus.isBlank(); // Acepta en test si se envía orderId

            if (isPaid && !orderId.isBlank()) {
                log.info("Confirmando pago y aprovisionando Moodle para orden: {}", orderId);
                buyCourseUseCase.procesarPagoExitoso(orderId);
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "orderId", orderId,
                        "message", "Pago y matrícula Moodle procesados exitosamente"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "status", "ACKNOWLEDGED",
                    "orderId", orderId,
                    "message", "Evento recibido sin acción requerida"
            ));

        } catch (Exception e) {
            log.error("Error al procesar Webhook de izipay Online: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "ERROR", "message", e.getMessage()));
        }
    }

    private boolean verificarFirmaHmac(String data, String hash, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString().equalsIgnoreCase(hash);
        } catch (Exception e) {
            log.error("Error calculando hash HMAC: {}", e.getMessage());
            return false;
        }
    }
}

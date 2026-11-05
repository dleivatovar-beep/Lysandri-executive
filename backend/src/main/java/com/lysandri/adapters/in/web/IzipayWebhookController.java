package com.lysandri.adapters.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Recepción de eventos asíncronos y notificaciones IPN de pasarelas de pago")
public class IzipayWebhookController {

    private final BuyCourseUseCase buyCourseUseCase;
    private final OrderRepositoryPort orderRepository;
    private final ObjectMapper objectMapper;

    @Value("${izipay.sha256-key:${IZIPAY_SHA256_KEY:}}")
    private String sha256Key;

    /**
     * Webhook / Notificación Instantánea de Pago (IPN) de izipay Online.
     * Valida de manera estricta la firma criptográfica HMAC-SHA256, la correspondencia
     * de orden, pasarela, importe, moneda y estado de transacción antes de procesar el pago.
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

            // 1. Verificación obligatoria de clave y firma criptográfica HMAC-SHA256
            if (sha256Key == null || sha256Key.isBlank()) {
                log.error("Clave SHA256 de izipay no configurada en el entorno.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("status", "ERROR", "message", "Clave HMAC no configurada"));
            }

            if (receivedHash == null || receivedHash.isBlank() || !verificarFirmaHmac(answerJson, receivedHash, sha256Key)) {
                log.error("Firma HMAC de izipay Online inválida o ausente");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "ERROR", "message", "Firma HMAC inválida o ausente"));
            }

            JsonNode answerNode = objectMapper.readTree(answerJson);
            String orderStatus = answerNode.path("orderStatus").asText("");
            if (orderStatus.isBlank() && answerNode.has("orderCycle")) {
                orderStatus = answerNode.path("orderCycle").asText();
            }

            JsonNode orderDetails = answerNode.path("orderDetails");
            String orderId = orderDetails.path("orderId").asText("");
            if (orderId.isBlank()) {
                orderId = answerNode.path("orderId").asText("");
            }
            if (orderId.isBlank() && answerNode.has("id")) {
                orderId = answerNode.path("id").asText();
            }

            if (orderId.isBlank()) {
                log.error("Identificador de orden ausente en payload de izipay");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "ERROR", "message", "Identificador de orden ausente"));
            }

            // 2. Localizar la orden en la base de datos
            Optional<Orden> ordenOpt = orderRepository.buscarPorCualquierIdentificador(orderId);
            if (ordenOpt.isEmpty()) {
                log.warn("Orden {} no encontrada en base de datos para IPN izipay", orderId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "ERROR", "message", "Orden no encontrada"));
            }

            Orden orden = ordenOpt.get();

            // 3. Validar pasarela: no permitir confirmación online para órdenes de pago manual
            String metodoPago = orden.getMetodoPago() != null ? orden.getMetodoPago().trim().toUpperCase() : "";
            boolean isManualPayment = "TRANSFERENCIA".equalsIgnoreCase(metodoPago)
                    || "TRANSFERENCIA_BANCARIA".equalsIgnoreCase(metodoPago)
                    || "YAPE".equalsIgnoreCase(metodoPago)
                    || "PLIN".equalsIgnoreCase(metodoPago)
                    || "MANUAL".equalsIgnoreCase(metodoPago);

            if (isManualPayment) {
                log.error("INTENTO DE CONFIRMACIÓN ILEGÍTIMO: Se intentó confirmar mediante webhook online una orden manual: {} ({})",
                        orden.getCodigoOrden(), metodoPago);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "ERROR", "message", "No se permite confirmar pagos manuales mediante webhook online"));
            }

            // 4. Validar correspondencia de pasarela: no admitir órdenes asociadas a Stripe
            if ("STRIPE".equalsIgnoreCase(metodoPago)) {
                log.error("DISCREPANCIA DE PASARELA: Orden {} configurada para Stripe pero recibida en webhook Izipay", orden.getCodigoOrden());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "ERROR", "message", "Discrepancia de pasarela: la orden pertenece a Stripe"));
            }

            // 5. Validar correspondencia financiera (importe y moneda)
            Long amountInCents = null;
            if (orderDetails.has("orderTotalAmount") && orderDetails.path("orderTotalAmount").isNumber()) {
                amountInCents = orderDetails.path("orderTotalAmount").asLong();
            } else if (answerNode.has("amount") && answerNode.path("amount").isNumber()) {
                amountInCents = answerNode.path("amount").asLong();
            }

            if (amountInCents == null || amountInCents <= 0) {
                log.error("Importe ausente o inválido en notificación izipay para orden {}", orden.getCodigoOrden());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "ERROR", "message", "Importe ausente o inválido"));
            }

            long expectedCents = (orden.getTotal() != null)
                    ? orden.getTotal().multiply(BigDecimal.valueOf(100)).longValue()
                    : -1L;

            if (expectedCents <= 0 || amountInCents != expectedCents) {
                log.error("DISCREPANCIA FINANCIERA DE MONTO en IPN izipay para orden {}: Izipay={} centavos vs Orden={} centavos",
                        orden.getCodigoOrden(), amountInCents, expectedCents);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "ERROR", "message", "Discrepancia financiera de importe"));
            }

            String currency = orderDetails.path("orderCurrency").asText("");
            if (currency.isBlank() && answerNode.has("currency")) {
                currency = answerNode.path("currency").asText("");
            }

            if (currency.isBlank() || (orden.getMoneda() != null && !currency.equalsIgnoreCase(orden.getMoneda()))) {
                log.error("DISCREPANCIA FINANCIERA DE MONEDA en IPN izipay para orden {}: Izipay={} vs Orden={}",
                        orden.getCodigoOrden(), currency, orden.getMoneda());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "ERROR", "message", "Discrepancia financiera de moneda"));
            }

            log.info("IPN izipay validada financieramente - OrderId: {}, Estado: {}", orderId, orderStatus);

            boolean isPaid = "PAID".equalsIgnoreCase(orderStatus)
                    || "SUCCESS".equalsIgnoreCase(orderStatus)
                    || "CAPTURED".equalsIgnoreCase(orderStatus);

            boolean isFailed = "REFUSED".equalsIgnoreCase(orderStatus)
                    || "ERROR".equalsIgnoreCase(orderStatus)
                    || "CANCELLED".equalsIgnoreCase(orderStatus)
                    || "FAILED".equalsIgnoreCase(orderStatus);

            if (isPaid) {
                // Idempotencia: si ya está pagada y matriculada
                if (orden.getEstadoOrden() == EstadoOrden.PAGADO && orden.isMoodleMatriculaSincronizada()) {
                    log.info("Orden {} ya pagada y matriculada previamente. Retornando éxito idempotente.", orden.getCodigoOrden());
                    return ResponseEntity.ok(Map.of(
                            "status", "SUCCESS",
                            "orderId", orderId,
                            "message", "Orden ya procesada y matriculada previamente"
                    ));
                }

                log.info("Confirmando pago y aprovisionando Moodle para orden: {}", orderId);
                buyCourseUseCase.procesarPagoExitoso(orden.getCodigoOrden());
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "orderId", orderId,
                        "message", "Pago y matrícula Moodle procesados exitosamente"
                ));
            } else if (isFailed) {
                if (orden.getEstadoOrden() == EstadoOrden.PENDIENTE) {
                    orden.setEstadoOrden(EstadoOrden.FALLIDO);
                    orderRepository.guardar(orden);
                    log.warn("Orden {} actualizada a FALLIDO por IPN izipay (status: {})", orden.getCodigoOrden(), orderStatus);
                }
                return ResponseEntity.ok(Map.of(
                        "status", "FAILED",
                        "orderId", orderId,
                        "message", "Orden actualizada a estado fallido/cancelado"
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

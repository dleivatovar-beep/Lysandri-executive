package com.lysandri.adapters.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Recepción de eventos asíncronos de pasarelas de pago")
public class StripeWebhookController {

    private final BuyCourseUseCase buyCourseUseCase;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook-secret:whsec_mock_lysandri_secret}")
    private String webhookSecret;

    @PostMapping("/stripe")
    @Operation(summary = "Webhook de Stripe para confirmación de pagos y enrolamiento")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        log.info("Evento Webhook recibido de Stripe");

        try {
            Event event = null;
            if (webhookSecret != null && !webhookSecret.startsWith("whsec_mock") && sigHeader != null) {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            }

            String eventType = event != null ? event.getType() : null;
            String sessionId = null;

            if (event != null && "checkout.session.completed".equals(eventType)) {
                JsonNode eventJson = objectMapper.readTree(payload);
                sessionId = eventJson.path("data").path("object").path("id").asText();
            } else if (event == null) {
                // Modo dev/test: parse directo del JSON
                JsonNode root = objectMapper.readTree(payload);
                eventType = root.path("type").asText();
                if ("checkout.session.completed".equals(eventType)) {
                    sessionId = root.path("data").path("object").path("id").asText();
                }
            }

            if (sessionId != null && !sessionId.isBlank()) {
                log.info("Procesando evento checkout.session.completed para sesión: {}", sessionId);
                buyCourseUseCase.confirmarPagoYMatricular(sessionId);
                return ResponseEntity.ok("Enrolamiento y pago procesados");
            }

            return ResponseEntity.ok("Evento recibido pero no requirió acción");
        } catch (Exception e) {
            log.error("Error procesando Webhook de Stripe: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error en webhook: " + e.getMessage());
        }
    }
}

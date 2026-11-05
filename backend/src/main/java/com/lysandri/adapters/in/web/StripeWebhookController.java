package com.lysandri.adapters.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.application.service.StripeIdempotencyService;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import com.stripe.exception.SignatureVerificationException;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Recepción de eventos asíncronos de pasarelas de pago")
public class StripeWebhookController {

    private final BuyCourseUseCase buyCourseUseCase;
    private final OrderRepositoryPort orderRepository;
    private final StripeIdempotencyService stripeIdempotencyService;
    private final com.lysandri.application.service.OrderFinancialTransactionService orderFinancialTransactionService;
    private final ObjectMapper objectMapper;

    @Value("${stripe.webhook-secret:${STRIPE_WEBHOOK_SECRET:}}")
    private String webhookSecret;

    private static final Set<String> EVENTOS_ADMITIDOS = Set.of(
            "checkout.session.completed",
            "checkout.session.async_payment_succeeded",
            "checkout.session.async_payment_failed",
            "checkout.session.expired",
            "payment_intent.succeeded",
            "payment_intent.payment_failed"
    );

    @PostMapping("/stripe")
    @Operation(summary = "Webhook de Stripe para confirmación de pagos y enrolamiento")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        log.info("Evento Webhook recibido de Stripe");

        if (sigHeader == null || sigHeader.isBlank()) {
            log.warn("Firma Stripe-Signature ausente en petición a webhook");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Firma Stripe-Signature ausente");
        }

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Clave secreta de Webhook de Stripe no configurada en las variables de entorno");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook secret no configurado");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Firma criptográfica Stripe-Signature inválida: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Firma de Stripe inválida");
        } catch (Exception e) {
            log.error("Error al procesar payload de Stripe: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Payload inválido");
        }

        String eventId = event.getId();
        String eventType = event.getType();

        if (!EVENTOS_ADMITIDOS.contains(eventType)) {
            log.info("Evento Stripe {} recibido pero no admitido para procesamiento: {}", eventId, eventType);
            return ResponseEntity.ok("Evento recibido pero no procesado: " + eventType);
        }

        String sessionId = null;
        String paymentIntentId = null;
        String paymentStatus = null;
        String paymentIntentStatus = null;
        String currency = null;
        Long amountReceived = null;
        String codigoOrden = null;

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode dataObject = root.path("data").path("object");

            if (eventType.startsWith("checkout.session.")) {
                sessionId = dataObject.path("id").asText(null);
                paymentIntentId = dataObject.path("payment_intent").asText(null);

                JsonNode psNode = dataObject.path("payment_status");
                if (!psNode.isMissingNode() && !psNode.isNull() && !psNode.asText().isBlank()) {
                    paymentStatus = psNode.asText().trim().toLowerCase();
                }

                JsonNode amtNode = dataObject.path("amount_total");
                if (amtNode.isNumber()) {
                    amountReceived = amtNode.asLong();
                }

                JsonNode currNode = dataObject.path("currency");
                if (!currNode.isMissingNode() && !currNode.isNull() && !currNode.asText().isBlank()) {
                    currency = currNode.asText().trim().toLowerCase();
                }

                codigoOrden = dataObject.path("client_reference_id").asText(null);
                if (codigoOrden == null || codigoOrden.isBlank()) {
                    codigoOrden = dataObject.path("metadata").path("codigoOrden").asText(null);
                }
            } else if (eventType.startsWith("payment_intent.")) {
                paymentIntentId = dataObject.path("id").asText(null);

                JsonNode stNode = dataObject.path("status");
                if (!stNode.isMissingNode() && !stNode.isNull() && !stNode.asText().isBlank()) {
                    paymentIntentStatus = stNode.asText().trim().toLowerCase();
                }

                JsonNode amtNode = dataObject.path("amount");
                if (amtNode.isNumber()) {
                    amountReceived = amtNode.asLong();
                }

                JsonNode currNode = dataObject.path("currency");
                if (!currNode.isMissingNode() && !currNode.isNull() && !currNode.asText().isBlank()) {
                    currency = currNode.asText().trim().toLowerCase();
                }

                codigoOrden = dataObject.path("metadata").path("codigoOrden").asText(null);
            }
        } catch (Exception e) {
            log.error("Error analizando JSON del evento {}: {}", eventId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("JSON malformado");
        }

        // =========================================================================
        // CONTROL DE IDEMPOTENCIA Y CONCURRENCIA CON LEASE ATÓMICO (FASE 3)
        // =========================================================================
        StripeIdempotencyService.ResultadoAdquisicion adquisicion =
                stripeIdempotencyService.iniciarProcesamiento(eventId, eventType, sessionId, paymentIntentId, codigoOrden);

        if (adquisicion.getEstado() == StripeIdempotencyService.EstadoEventoResultado.YA_COMPLETADO) {
            log.info("Evento duplicado {} ya completado previamente. Retornando HTTP 200.", eventId);
            return ResponseEntity.ok("Evento duplicado ya completado: " + eventId);
        }

        if (adquisicion.getEstado() == StripeIdempotencyService.EstadoEventoResultado.EN_PROCESO) {
            log.warn("Evento {} en procesamiento activo por otro hilo/instancia. Retornando HTTP 429 para reintento controlado.", eventId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "5")
                    .body("Evento en procesamiento activo: reintentar en breve");
        }

        String ownerToken = adquisicion.getOwnerToken();

        try {
            // Localizar orden por cualquiera de los identificadores disponibles
            Optional<Orden> ordenOpt = Optional.empty();
            if (sessionId != null && !sessionId.isBlank()) {
                ordenOpt = orderRepository.buscarPorCualquierIdentificador(sessionId);
            }
            if (ordenOpt.isEmpty() && paymentIntentId != null && !paymentIntentId.isBlank()) {
                ordenOpt = orderRepository.buscarPorCualquierIdentificador(paymentIntentId);
            }
            if (ordenOpt.isEmpty() && codigoOrden != null && !codigoOrden.isBlank()) {
                ordenOpt = orderRepository.buscarPorCodigo(codigoOrden);
            }

            if (ordenOpt.isEmpty()) {
                log.warn("No se encontró orden asociada al evento Stripe {} (Session: {}, PI: {}, Codigo: {})",
                        eventId, sessionId, paymentIntentId, codigoOrden);
                stripeIdempotencyService.marcarFallido(eventId, ownerToken, "Orden no encontrada en base de datos");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Orden no encontrada");
            }

            Orden orden = ordenOpt.get();

            // Validación estricta de pertenencia de orden
            if (codigoOrden != null && !codigoOrden.isBlank() && !codigoOrden.equalsIgnoreCase(orden.getCodigoOrden())) {
                log.error("DISCREPANCIA DE ORDEN en evento {}: Stripe refiere {} pero orden encontrada es {}",
                        eventId, codigoOrden, orden.getCodigoOrden());
                stripeIdempotencyService.marcarFallido(eventId, ownerToken,
                        "Discrepancia: El pago refiere orden " + codigoOrden + " pero se localizó " + orden.getCodigoOrden());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Discrepancia de orden vinculada");
            }

            // =========================================================================
            // FASE 3 — ASOCIACIÓN SEGURA DE IDENTIFICADORES Y PREVENCIÓN DE CONFLICTOS
            // =========================================================================
            if (paymentIntentId != null && !paymentIntentId.isBlank()) {
                Optional<Orden> otraOrden = orderRepository.buscarPorStripePaymentIntentId(paymentIntentId);
                if (otraOrden.isPresent() && !otraOrden.get().getIdOrden().equals(orden.getIdOrden())) {
                    log.error("CONFLICTO DE IDENTIFICADORES: PaymentIntent {} ya está asociado a otra orden ({})",
                            paymentIntentId, otraOrden.get().getCodigoOrden());
                    stripeIdempotencyService.marcarFallido(eventId, ownerToken, "PaymentIntent ya vinculado a otra orden distinta");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflicto de identificadores financieros");
                }
            }

            if (sessionId != null && !sessionId.isBlank() && orden.getStripeSessionId() != null && !orden.getStripeSessionId().equals(sessionId)) {
                log.error("CONFLICTO DE SESIÓN: Orden {} ya tiene sessionId {} incompatible con {}",
                        orden.getCodigoOrden(), orden.getStripeSessionId(), sessionId);
                stripeIdempotencyService.marcarFallido(eventId, ownerToken, "Sesión de checkout incompatible con la orden");
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflicto de sesión de pago incompatible");
            }

            // =========================================================================
            // FASE 1 Y 2 — VALIDACIÓN FINANCIERA ESTRICTA (IMPORTE, MONEDA Y ESTADO)
            // =========================================================================
            boolean esEventoConfirmacionPago = "checkout.session.completed".equals(eventType)
                    || "checkout.session.async_payment_succeeded".equals(eventType)
                    || "payment_intent.succeeded".equals(eventType);

            if (esEventoConfirmacionPago) {
                // 1. Exigencia de moneda obligatoria
                if (currency == null || currency.isBlank()) {
                    log.error("DISCREPANCIA FINANCIERA: Moneda ausente en evento de confirmación {}", eventId);
                    stripeIdempotencyService.marcarFallido(eventId, ownerToken, "Moneda obligatoria ausente en el evento de pago");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Moneda ausente");
                }

                if (orden.getMoneda() == null || !currency.equalsIgnoreCase(orden.getMoneda())) {
                    log.error("DISCREPANCIA FINANCIERA DE MONEDA en evento {}: Stripe={} vs Orden={}",
                            eventId, currency, orden.getMoneda());
                    stripeIdempotencyService.marcarFallido(eventId, ownerToken,
                            "Discrepancia de moneda: Stripe=" + currency + " vs Orden=" + orden.getMoneda());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Discrepancia financiera de moneda");
                }

                // 2. Exigencia de importe obligatorio y comparación exacta en unidades mínimas (centavos)
                if (amountReceived == null || amountReceived <= 0) {
                    log.error("DISCREPANCIA FINANCIERA: Importe ausente o cero en evento de confirmación {}", eventId);
                    stripeIdempotencyService.marcarFallido(eventId, ownerToken, "Importe ausente o cero en el evento de confirmación");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Importe ausente o inválido");
                }

                long expectedCents = (orden.getTotal() != null)
                        ? orden.getTotal().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue()
                        : -1L;

                if (expectedCents <= 0 || amountReceived != expectedCents) {
                    log.error("DISCREPANCIA FINANCIERA DE MONTO en evento {}: Stripe={} centavos vs Orden={} centavos",
                            eventId, amountReceived, expectedCents);
                    stripeIdempotencyService.marcarFallido(eventId, ownerToken,
                            "Discrepancia de monto: Stripe=" + amountReceived + " centavos vs Orden=" + expectedCents + " centavos");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Discrepancia financiera de importe");
                }

                // 3. FASE 2: Validación estricta del estado de pago por tipo de evento
                if (eventType.startsWith("checkout.session.")) {
                    if (paymentStatus == null || paymentStatus.isBlank()) {
                        log.error("DISCREPANCIA FINANCIERA: payment_status ausente en sesión de checkout {}", eventId);
                        stripeIdempotencyService.marcarFallido(eventId, ownerToken, "payment_status ausente en sesión de checkout");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Estado de pago ausente");
                    }

                    if ("unpaid".equalsIgnoreCase(paymentStatus)) {
                        log.info("Sesión de checkout {} completada pero estado de pago es 'unpaid'. Esperando pago asíncrono.", sessionId);
                        boolean completadoOk = stripeIdempotencyService.marcarCompletado(
                                eventId, ownerToken, orden.getIdOrden(), orden.getCodigoOrden(), sessionId, paymentIntentId);
                        if (!completadoOk) {
                            return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflicto de concurrencia: lease expirado");
                        }
                        return ResponseEntity.ok("Sesión completada, esperando pago asíncrono");
                    }

                    if ("no_payment_required".equalsIgnoreCase(paymentStatus)) {
                        log.error("DISCREPANCIA FINANCIERA: payment_status 'no_payment_required' rechazado para orden con importe positivo {}", eventId);
                        stripeIdempotencyService.marcarFallido(eventId, ownerToken, "payment_status no_payment_required no permitido para órdenes con costo");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Estado no_payment_required no permitido para órdenes con importe");
                    }

                    if (!"paid".equalsIgnoreCase(paymentStatus)) {
                        log.error("DISCREPANCIA FINANCIERA: payment_status '{}' no válido para confirmación en {}", paymentStatus, eventId);
                        stripeIdempotencyService.marcarFallido(eventId, ownerToken, "payment_status inválido: " + paymentStatus);
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Estado de pago no reconocido o inválido: " + paymentStatus);
                    }

                } else if ("payment_intent.succeeded".equals(eventType)) {
                    if (paymentIntentStatus == null || paymentIntentStatus.isBlank()) {
                        log.error("DISCREPANCIA FINANCIERA: status ausente en payment_intent.succeeded {}", eventId);
                        stripeIdempotencyService.marcarFallido(eventId, ownerToken, "status ausente en Payment Intent");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Estado de Payment Intent ausente");
                    }

                    if (!"succeeded".equalsIgnoreCase(paymentIntentStatus)) {
                        log.error("DISCREPANCIA FINANCIERA: status de Payment Intent es '{}' en vez de 'succeeded'", paymentIntentStatus);
                        stripeIdempotencyService.marcarFallido(eventId, ownerToken, "Payment Intent status inválido: " + paymentIntentStatus);
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Estado de Payment Intent no es succeeded: " + paymentIntentStatus);
                    }
                }
            }

            // =========================================================================
            // PERSISTENCIA DE IDENTIFICADORES SOLO TRAS VALIDACIÓN FINANCIERA EXITOSA
            // =========================================================================
            if (esEventoConfirmacionPago) {
                boolean cambioIds = false;
                if (sessionId != null && !sessionId.isBlank() && orden.getStripeSessionId() == null) {
                    orden.setStripeSessionId(sessionId);
                    cambioIds = true;
                }
                if (paymentIntentId != null && !paymentIntentId.isBlank() && orden.getStripePaymentIntentId() == null) {
                    orden.setStripePaymentIntentId(paymentIntentId);
                    cambioIds = true;
                }
                if (cambioIds) {
                    orden = orderRepository.guardar(orden);
                    log.info("Relación cs/pi validada y actualizada en orden {}: SessionId={}, PaymentIntentId={}",
                            orden.getCodigoOrden(), orden.getStripeSessionId(), orden.getStripePaymentIntentId());
                }
            }

            // =========================================================================
            // MANEJO DE EVENTOS DE PAGO CONFIRMADO
            // =========================================================================
            if (esEventoConfirmacionPago) {

                // Si la orden ya está pagada y sincronizada, garantizar idempotencia
                if (orden.getEstadoOrden() == EstadoOrden.PAGADO && orden.isMoodleMatriculaSincronizada()) {
                    log.info("Orden {} ya pagada y matriculada previamente. Evento {} completado idempotentemente.",
                            orden.getCodigoOrden(), eventId);
                    boolean completadoOk = stripeIdempotencyService.marcarCompletado(
                            eventId, ownerToken, orden.getIdOrden(), orden.getCodigoOrden(), sessionId, paymentIntentId);
                    if (!completadoOk) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflicto de concurrencia: lease expirado");
                    }
                    return ResponseEntity.ok("Orden ya pagada y matriculada previamente");
                }

                // Ejecutar pago y matrícula bajo protección de bloqueo pesimista en BuyCourseService
                buyCourseUseCase.procesarPagoExitoso(orden.getCodigoOrden());

                // Verificar explícitamente el resultado de marcar completado (Fase 3.D)
                boolean completadoOk = stripeIdempotencyService.marcarCompletado(
                        eventId, ownerToken, orden.getIdOrden(), orden.getCodigoOrden(), sessionId, paymentIntentId);
                if (!completadoOk) {
                    log.error("ALERTA: No se pudo marcar COMPLETADO el evento {}: lease expirado o transferido", eventId);
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflicto de concurrencia: el arrendamiento del evento expiró");
                }

                return ResponseEntity.ok("Pago, emisión y matrícula procesados exitosamente");

            // =========================================================================
            // MANEJO DE EVENTOS DE PAGO FALLIDO O EXPIRADO (FASE 2 Y 3)
            // =========================================================================
            } else if ("payment_intent.payment_failed".equals(eventType)
                    || "checkout.session.async_payment_failed".equals(eventType)
                    || "checkout.session.expired".equals(eventType)) {

                if (orden.getEstadoOrden() == EstadoOrden.PAGADO) {
                    log.info("Ignorando evento tardío {} para orden {}: ya se encuentra confirmada como PAGADA",
                            eventType, orden.getCodigoOrden());
                    stripeIdempotencyService.marcarCompletado(eventId, ownerToken, orden.getIdOrden(), orden.getCodigoOrden(), sessionId, paymentIntentId);
                    return ResponseEntity.ok("Evento tardío ignorado: orden ya pagada");
                }

                EstadoOrden nuevoEstado = "checkout.session.expired".equals(eventType)
                        ? EstadoOrden.CANCELADO
                        : EstadoOrden.FALLIDO;

                orden.setEstadoOrden(nuevoEstado);
                orderFinancialTransactionService.registrarPagoFallidoOExpirado(
                        orden.getCodigoOrden(),
                        paymentIntentId != null ? paymentIntentId : sessionId,
                        nuevoEstado,
                        "Evento Stripe recibido: " + eventType
                );

                boolean completadoOk = stripeIdempotencyService.marcarCompletado(
                        eventId, ownerToken, orden.getIdOrden(), orden.getCodigoOrden(), sessionId, paymentIntentId);
                if (!completadoOk) {
                    log.error("ALERTA: No se pudo marcar COMPLETADO el evento {}: lease expirado o transferido", eventId);
                    return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflicto de concurrencia: el arrendamiento del evento expiró");
                }

                return ResponseEntity.ok("Evento de fallo registrado para orden: " + orden.getCodigoOrden());
            }

            boolean completadoOk = stripeIdempotencyService.marcarCompletado(
                    eventId, ownerToken, orden.getIdOrden(), orden.getCodigoOrden(), sessionId, paymentIntentId);
            if (!completadoOk) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Conflicto de concurrencia: lease expirado");
            }
            return ResponseEntity.ok("Evento procesado");

        } catch (Exception e) {
            log.error("Excepción al procesar evento {}: {}", eventId, e.getMessage(), e);
            stripeIdempotencyService.marcarFallido(eventId, ownerToken, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Error procesando evento: " + e.getMessage());
        }
    }
}

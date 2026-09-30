package com.lysandri.adapters.out.payment;

import com.lysandri.domain.model.DetalleOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.out.PaymentPort;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class StripePaymentAdapter implements PaymentPort {

    private final String apiKey;
    private final String successUrl;
    private final String cancelUrl;

    public StripePaymentAdapter(
            @Value("${stripe.api-key:sk_test_mock}") String apiKey,
            @Value("${stripe.success-url:http://localhost:5173/checkout/success?session_id={CHECKOUT_SESSION_ID}}") String successUrl,
            @Value("${stripe.cancel-url:http://localhost:5173/checkout/cancel}") String cancelUrl) {
        this.apiKey = apiKey;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        if (apiKey != null && !apiKey.startsWith("sk_test_mock")) {
            Stripe.apiKey = apiKey;
        }
    }

    @Override
    public PaymentSessionResult crearSesionPago(Orden orden, String customerEmail, String customerName) {
        log.info("Creando sesión de pago Stripe para orden {} - Usuario: {}", orden.getCodigoOrden(), customerEmail);

        // Si la clave es de desarrollo/mock, simular respuesta de Stripe
        if (apiKey == null || apiKey.startsWith("sk_test_mock") || apiKey.equals("mock")) {
            String mockSessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");
            String mockUrl = successUrl.replace("{CHECKOUT_SESSION_ID}", mockSessionId);
            log.info("[MODO DEV / MOCK STRIPE] Sesión simulada generada: {}", mockSessionId);
            return new PaymentSessionResult(mockSessionId, mockUrl, "pi_mock_" + UUID.randomUUID().toString().substring(0, 8));
        }

        try {
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(customerEmail)
                    .setClientReferenceId(orden.getCodigoOrden())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl);

            for (DetalleOrden item : orden.getItems()) {
                String itemTitle = (item.getPrograma() != null && item.getPrograma().getTitulo() != null)
                        ? item.getPrograma().getTitulo()
                        : "Programa Ejecutivo Lysandri";

                long unitAmountInCents = item.getPrecioUnitario().multiply(BigDecimal.valueOf(100)).longValue();

                paramsBuilder.addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity((long) item.getCantidad())
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(orden.getMoneda() != null ? orden.getMoneda().toLowerCase() : "usd")
                                                .setUnitAmount(unitAmountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName(itemTitle)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                );
            }

            Session session = Session.create(paramsBuilder.build());
            return new PaymentSessionResult(session.getId(), session.getUrl(), session.getPaymentIntent());
        } catch (Exception e) {
            log.error("Fallo al conectar con Stripe API: {}", e.getMessage(), e);
            throw new RuntimeException("Error al procesar la sesión de pago con Stripe: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verificarPagoCompletado(String sessionId) {
        if (sessionId == null) return false;
        if (apiKey == null || apiKey.startsWith("sk_test_mock") || apiKey.equals("mock")) {
            return true; // En modo mock siempre se considera exitoso
        }

        try {
            Session session = Session.retrieve(sessionId);
            return "paid".equalsIgnoreCase(session.getPaymentStatus());
        } catch (Exception e) {
            log.error("Error consultando estado de sesión Stripe {}: {}", sessionId, e.getMessage());
            return false;
        }
    }
}

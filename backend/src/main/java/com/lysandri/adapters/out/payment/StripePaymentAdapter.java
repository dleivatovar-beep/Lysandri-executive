package com.lysandri.adapters.out.payment;

import com.lysandri.domain.model.DetalleOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.out.PaymentPort;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Adaptador de producción para Stripe.
 * Solo activo en perfiles no-test (@Profile("!test")).
 * Prohíbe terminantemente confirmaciones simuladas automáticas.
 */
@Slf4j
@Component("stripePaymentAdapter")
@Profile("!test & !clone")
public class StripePaymentAdapter implements PaymentPort {

    private final String apiKey;
    private final String successUrl;
    private final String cancelUrl;
    private final Environment environment;

    public StripePaymentAdapter(
            @Value("${stripe.api-key:sk_test_mock}") String apiKey,
            @Value("${stripe.success-url:http://localhost:3000/checkout/success?session_id={CHECKOUT_SESSION_ID}}") String successUrl,
            @Value("${stripe.cancel-url:http://localhost:3000/checkout/cancel}") String cancelUrl,
            Environment environment) {
        this.apiKey = apiKey;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.environment = environment;

        if (apiKey != null && !apiKey.startsWith("sk_test_mock") && !apiKey.equals("mock")) {
            Stripe.apiKey = apiKey;
        }
    }

    @PostConstruct
    public void validarConfiguracionSegura() {
        if (isProductionEnvironment()) {
            if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("sk_test_mock") || apiKey.equals("mock")) {
                log.error("CONFIGURACIÓN INSEGURA CRÍTICA: Se intentó iniciar en entorno de producción sin credenciales reales de Stripe.");
                throw new IllegalStateException("CRÍTICO: No se permite operar en entorno de PRODUCCIÓN sin credenciales válidas de Stripe.");
            }
        }
    }

    private boolean isProductionEnvironment() {
        if (environment == null) return false;
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }

    @Override
    public PaymentSessionResult crearSesionPago(Orden orden, String customerEmail, String customerName) {
        log.info("Creando sesión de pago Stripe para orden {} - Usuario: {}", orden.getCodigoOrden(), customerEmail);

        boolean isMockKey = apiKey == null || apiKey.startsWith("sk_test_mock") || apiKey.equals("mock");

        // SEGURIDAD: En entorno de producción está estrictamente prohibido operar con credenciales simuladas
        if (isProductionEnvironment() && isMockKey) {
            log.error("INTENTO DE OPERACIÓN EN PRODUCCIÓN SIN CREDENCIALES VÁLIDAS DE STRIPE");
            throw new IllegalStateException("CRÍTICO: No se permite operar con credenciales Stripe mock en entorno de PRODUCCIÓN.");
        }

        if (isMockKey) {
            log.error("Fallo de configuración: StripePaymentAdapter requiere una clave válida de Stripe. Para pruebas use MockStripePaymentAdapter con @Profile('test').");
            throw new IllegalStateException("Credenciales de Stripe no configuradas o inválidas para el adaptador real.");
        }

        try {
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(customerEmail)
                    .setClientReferenceId(orden.getCodigoOrden())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .putMetadata("codigoOrden", orden.getCodigoOrden())
                    .putMetadata("idOrden", String.valueOf(orden.getIdOrden()))
                    .setPaymentIntentData(
                            SessionCreateParams.PaymentIntentData.builder()
                                    .putMetadata("codigoOrden", orden.getCodigoOrden())
                                    .putMetadata("idOrden", String.valueOf(orden.getIdOrden()))
                                    .build()
                    );

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
    public boolean verificarPagoCompletado(String sessionIdOrPaymentIntentId) {
        if (sessionIdOrPaymentIntentId == null || sessionIdOrPaymentIntentId.isBlank()) return false;

        boolean isMockKey = apiKey == null || apiKey.startsWith("sk_test_mock") || apiKey.equals("mock");

        if (isProductionEnvironment() && isMockKey) {
            log.error("INTENTO DE VERIFICACIÓN EN PRODUCCIÓN SIN CREDENCIALES VÁLIDAS DE STRIPE");
            throw new IllegalStateException("CRÍTICO: No se permite operar con credenciales Stripe mock en entorno de PRODUCCIÓN.");
        }

        if (isMockKey) {
            log.error("Fallo de configuración: No se pueden verificar pagos con credenciales mock en el adaptador real.");
            throw new IllegalStateException("Credenciales de Stripe no configuradas o inválidas para verificación.");
        }

        try {
            if (sessionIdOrPaymentIntentId.startsWith("pi_")) {
                PaymentIntent intent = PaymentIntent.retrieve(sessionIdOrPaymentIntentId);
                return "succeeded".equalsIgnoreCase(intent.getStatus());
            } else {
                Session session = Session.retrieve(sessionIdOrPaymentIntentId);
                return "paid".equalsIgnoreCase(session.getPaymentStatus());
            }
        } catch (Exception e) {
            log.error("Error consultando estado en Stripe para {}: {}", sessionIdOrPaymentIntentId, e.getMessage());
            return false;
        }
    }
}

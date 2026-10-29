package com.lysandri.adapters.out.payment;

import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.out.PaymentPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador simulado de Stripe para pruebas automatizadas e integración controlada.
 * Activo EXCLUSIVAMENTE bajo el perfil @Profile("test").
 * Impide bajo cualquier circunstancia su activación o uso en perfiles productivos (prod/production).
 */
@Slf4j
@Component("stripePaymentAdapter")
@Profile({"test", "clone"})
public class MockStripePaymentAdapter implements PaymentPort {

    private final String successUrl;
    private final Environment environment;
    private final ConcurrentHashMap<String, Boolean> transaccionesSimuladas = new ConcurrentHashMap<>();

    public MockStripePaymentAdapter(
            @Value("${stripe.success-url:http://localhost:3000/checkout/success?session_id={CHECKOUT_SESSION_ID}}") String successUrl,
            Environment environment) {
        this.successUrl = successUrl;
        this.environment = environment;
    }

    @PostConstruct
    public void validarSeguridadEntorno() {
        if (environment != null) {
            boolean esProduccion = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
            if (esProduccion) {
                log.error("VIOLACIÓN DE SEGURIDAD CRÍTICA: Intento de cargar MockStripePaymentAdapter bajo perfil de PRODUCCIÓN.");
                throw new IllegalStateException("CRÍTICO: MockStripePaymentAdapter está terminantemente prohibido en entornos de PRODUCCIÓN.");
            }
        }
        log.info("MockStripePaymentAdapter inicializado de forma segura bajo perfil 'test'.");
    }

    @Override
    public PaymentSessionResult crearSesionPago(Orden orden, String customerEmail, String customerName) {
        String sessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");
        String paymentIntentId = "pi_test_" + UUID.randomUUID().toString().substring(0, 8);
        String redirectUrl = successUrl.replace("{CHECKOUT_SESSION_ID}", sessionId);

        transaccionesSimuladas.put(sessionId, true);
        transaccionesSimuladas.put(paymentIntentId, true);

        log.debug("MockStripePaymentAdapter: Sesión generada para test - Session: {}, PI: {}", sessionId, paymentIntentId);
        return new PaymentSessionResult(sessionId, redirectUrl, paymentIntentId);
    }

    @Override
    public boolean verificarPagoCompletado(String sessionIdOrPaymentIntentId) {
        if (sessionIdOrPaymentIntentId == null || sessionIdOrPaymentIntentId.isBlank()) {
            return false;
        }
        return transaccionesSimuladas.getOrDefault(sessionIdOrPaymentIntentId, false);
    }

    /**
     * Método utilitario exclusivo para tests: permite simular transacciones exitosas o fallidas bajo demanda.
     */
    public void registrarEstadoTransaccionTest(String id, boolean completado) {
        transaccionesSimuladas.put(id, completado);
    }
}

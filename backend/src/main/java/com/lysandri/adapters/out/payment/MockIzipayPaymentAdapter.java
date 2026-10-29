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
 * Adaptador simulado de Izipay para entornos de pruebas locales e integración (@Profile({"test", "clone"})).
 * Queda terminantemente prohibido su uso o activación en entornos de producción (prod/production).
 */
@Slf4j
@Component("izipayPaymentAdapter")
@Profile({"test", "clone"})
public class MockIzipayPaymentAdapter implements PaymentPort {

    private final String successUrl;
    private final String publicKey;
    private final Environment environment;
    private final ConcurrentHashMap<String, Boolean> transaccionesSimuladas = new ConcurrentHashMap<>();

    public MockIzipayPaymentAdapter(
            @Value("${izipay.success-url:http://localhost:3000/checkout/success?session_id={CHECKOUT_SESSION_ID}}") String successUrl,
            @Value("${izipay.public-key:test_public_key_lysandri}") String publicKey,
            Environment environment) {
        this.successUrl = successUrl;
        this.publicKey = publicKey;
        this.environment = environment;
    }

    @PostConstruct
    public void validarSeguridadEntorno() {
        if (environment != null) {
            boolean esProduccion = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
            if (esProduccion) {
                log.error("VIOLACIÓN DE SEGURIDAD CRÍTICA: Intento de cargar MockIzipayPaymentAdapter bajo perfil de PRODUCCIÓN.");
                throw new IllegalStateException("CRÍTICO: MockIzipayPaymentAdapter está terminantemente prohibido en entornos de PRODUCCIÓN.");
            }
        }
        log.info("MockIzipayPaymentAdapter inicializado de forma segura bajo perfil autorizado.");
    }

    @Override
    public PaymentSessionResult crearSesionPago(Orden orden, String customerEmail, String customerName) {
        String sessionId = "izisess_mock_" + UUID.randomUUID().toString().replace("-", "");
        String txId = "izitx_mock_" + UUID.randomUUID().toString().substring(0, 10);
        String formToken = "MOCK_IZIPAY_FORM_TOKEN_" + UUID.randomUUID();
        String redirectUrl = successUrl.replace("{CHECKOUT_SESSION_ID}", sessionId);

        transaccionesSimuladas.put(sessionId, true);
        transaccionesSimuladas.put(txId, true);
        if (orden.getCodigoOrden() != null) {
            transaccionesSimuladas.put(orden.getCodigoOrden(), true);
        }

        log.debug("MockIzipayPaymentAdapter: Sesión generada para test - Session: {}, Tx: {}", sessionId, txId);
        return new PaymentSessionResult(sessionId, redirectUrl, txId, formToken, publicKey);
    }

    @Override
    public boolean verificarPagoCompletado(String sessionIdOrTxId) {
        if (sessionIdOrTxId == null || sessionIdOrTxId.isBlank()) {
            return false;
        }
        return transaccionesSimuladas.getOrDefault(sessionIdOrTxId.trim(), false);
    }

    /**
     * Utilidad para pruebas automatizadas: permite forzar el resultado de una transacción específica.
     */
    public void registrarEstadoTransaccionTest(String id, boolean completado) {
        transaccionesSimuladas.put(id, completado);
    }
}

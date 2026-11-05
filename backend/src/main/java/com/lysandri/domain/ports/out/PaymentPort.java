package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.Orden;

/**
 * Puerto de salida para pasarelas de pago internacionales y locales.
 * Diseñado bajo Arquitectura Hexagonal para desacoplar el dominio de proveedores como izipay Online.
 */
public interface PaymentPort {

    record PaymentSessionResult(
            String sessionId,
            String checkoutUrl,
            String paymentIntentId,
            String formToken,
            String publicKey
    ) {
        public PaymentSessionResult(String sessionId, String checkoutUrl, String paymentIntentId) {
            this(sessionId, checkoutUrl, paymentIntentId, null, null);
        }
    }

    /**
     * Crea una sesión de Checkout / Pago en la pasarela izipay Online.
     */
    PaymentSessionResult crearSesionPago(Orden orden, String customerEmail, String customerName);

    /**
     * Verifica si una transacción o sesión de pago fue completada exitosamente.
     */
    boolean verificarPagoCompletado(String sessionId);
}

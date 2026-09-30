package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.Orden;

public interface PaymentPort {

    record PaymentSessionResult(
            String sessionId,
            String checkoutUrl,
            String paymentIntentId
    ) {}

    /**
     * Crea una sesión de Checkout en la pasarela de pagos (Stripe).
     */
    PaymentSessionResult crearSesionPago(Orden orden, String customerEmail, String customerName);

    /**
     * Verifica si una sesión de pago fue completada exitosamente.
     */
    boolean verificarPagoCompletado(String sessionId);
}

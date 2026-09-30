package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.Orden;

import java.util.List;

public interface BuyCourseUseCase {

    record CreateOrderCommand(
            Long idUsuario,
            List<Long> programaIds
    ) {}

    record CheckoutSessionResponse(
            String codigoOrden,
            String stripeCheckoutUrl,
            String stripeSessionId
    ) {}

    /**
     * Crea una orden en estado PENDIENTE e inicia la sesión de Stripe Checkout.
     */
    CheckoutSessionResponse iniciarCompra(CreateOrderCommand command);

    /**
     * Confirma el pago de la orden (invocado por Webhook de Stripe o verificación directa)
     * y orquesta el aprovisionamiento de usuario y matrícula en Moodle.
     */
    Orden confirmarPagoYMatricular(String stripeSessionId);

    /**
     * Obtiene una orden por su código único.
     */
    Orden obtenerOrdenPorCodigo(String codigoOrden);
}

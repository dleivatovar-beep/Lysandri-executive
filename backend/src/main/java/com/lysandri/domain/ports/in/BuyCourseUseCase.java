package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.Orden;

import java.util.List;

public interface BuyCourseUseCase {

    record CreateOrderCommand(
            Long idUsuario,
            List<Long> programaIds,
            String guestEmail,
            String guestNombre,
            String tipoComprobante,
            String numeroDocumento,
            String nombreFacturacion
    ) {
        public CreateOrderCommand(Long idUsuario, List<Long> programaIds) {
            this(idUsuario, programaIds, null, null, null, null, null);
        }
    }

    record CheckoutSessionResponse(
            String codigoOrden,
            String stripeCheckoutUrl,
            String stripeSessionId,
            String checkoutUrl
    ) {
        public CheckoutSessionResponse(String codigoOrden, String stripeCheckoutUrl, String stripeSessionId) {
            this(codigoOrden, stripeCheckoutUrl, stripeSessionId, stripeCheckoutUrl);
        }
    }

    /**
     * Crea una orden en estado PENDIENTE e inicia la sesión de Stripe Checkout.
     */
    CheckoutSessionResponse iniciarCompra(CreateOrderCommand command);

    /**
     * Flujo de orquestación principal para la confirmación de pago exitoso (Stripe webhook o verificación):
     * a) Obtiene los datos del comprador almacenados en la orden (nombre, email, DNI/RUC).
     * b) Genera una contraseña segura aleatoria de 10 caracteres.
     * c) Llama a LmsClientPort para ejecutar core_user_create_users y enrol_manual_enrol_users en Moodle.
     * d) Llama a BillingPort para calcular base imponible, IGV (18%), correlativo y persistir ComprobantePago.
     * e) Cambia el estado de la Orden a 'PAGADO'.
     * f) Llama a NotificationPort para enviar el correo con enlace a Moodle, usuario, clave generada y comprobante.
     */
    Orden procesarPagoExitoso(String stripeSessionId);

    /**
     * Confirma el pago de la orden y matricula al usuario en Moodle.
     */
    Orden confirmarPagoYMatricular(String stripeSessionId);

    /**
     * Obtiene una orden por su código único.
     */
    Orden obtenerOrdenPorCodigo(String codigoOrden);
}

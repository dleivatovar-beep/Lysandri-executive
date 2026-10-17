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
            String nombreFacturacion,
            String metodoPago,
            String numeroOperacion
    ) {
        public CreateOrderCommand(Long idUsuario, List<Long> programaIds) {
            this(idUsuario, programaIds, null, null, null, null, null, "IZIPAY", null);
        }

        public CreateOrderCommand(
                Long idUsuario,
                List<Long> programaIds,
                String guestEmail,
                String guestNombre,
                String tipoComprobante,
                String numeroDocumento,
                String nombreFacturacion
        ) {
            this(idUsuario, programaIds, guestEmail, guestNombre, tipoComprobante, numeroDocumento, nombreFacturacion, "IZIPAY", null);
        }
    }

    record ConfirmarPagoManualCommand(
            String numeroOperacion,
            String metodoPago,
            String notasAdmin
    ) {}

    record CheckoutSessionResponse(
            String codigoOrden,
            String stripeCheckoutUrl,
            String stripeSessionId,
            String checkoutUrl,
            String formToken,
            String publicKey,
            String izipayTransactionId
    ) {
        public CheckoutSessionResponse(String codigoOrden, String checkoutUrl, String sessionId) {
            this(codigoOrden, checkoutUrl, sessionId, checkoutUrl, null, null, sessionId);
        }

        public CheckoutSessionResponse(String codigoOrden, String checkoutUrl, String sessionId, String formToken, String publicKey) {
            this(codigoOrden, checkoutUrl, sessionId, checkoutUrl, formToken, publicKey, sessionId);
        }

        public CheckoutSessionResponse(String codigoOrden, String stripeCheckoutUrl, String stripeSessionId, String checkoutUrl) {
            this(codigoOrden, stripeCheckoutUrl, stripeSessionId, checkoutUrl, null, null, stripeSessionId);
        }
    }

    /**
     * Crea una orden en estado PENDIENTE e inicia la sesión de pago con izipay Online
     * o registra la intención de pago manual (Yape, Plin, Transferencia).
     */
    CheckoutSessionResponse iniciarCompra(CreateOrderCommand command);

    /**
     * Flujo de orquestación principal para la confirmación de pago exitoso (izipay IPN webhook o verificación):
     * a) Obtiene los datos del comprador almacenados en la orden (nombre, email, DNI/RUC).
     * b) Genera una contraseña segura aleatoria de 10 caracteres.
     * c) Llama a LmsClientPort para ejecutar core_user_create_users y enrol_manual_enrol_users en Moodle LMS.
     * d) Llama a BillingPort para calcular base imponible, IGV (18%), correlativo y persistir ComprobantePago SUNAT.
     * e) Cambia el estado de la Orden a 'PAGADO'.
     * f) Llama a NotificationPort para enviar el correo y confirmación con enlace a Moodle, credenciales y comprobante.
     */
    Orden procesarPagoExitoso(String sessionIdOrTransactionId);

    /**
     * Confirma el pago de la orden y matricula al usuario en Moodle LMS.
     */
    Orden confirmarPagoYMatricular(String sessionIdOrTransactionId);

    /**
     * Aprobación administrativa de pagos manuales (Yape, Plin, Transferencia bancaria):
     * Valida el comprobante, emite la factura/boleta y matricula al alumno en el Campus Moodle.
     */
    Orden confirmarPagoManual(Long idOrden, ConfirmarPagoManualCommand command);

    /**
     * Lista órdenes de compra con filtro por estado (PENDIENTE, PAGADO) y búsqueda de texto.
     */
    List<Orden> listarOrdenes(String estado, String search);

    /**
     * Obtiene una orden por su código único.
     */
    Orden obtenerOrdenPorCodigo(String codigoOrden);
}

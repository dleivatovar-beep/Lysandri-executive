package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.ComprobantePago;

public interface NotificationPort {

    /**
     * Envía un correo electrónico transaccional con plantilla HTML al comprador conteniendo:
     * - Credenciales de acceso a la plataforma ejecutiva Moodle (URL, usuario y contraseña generada).
     * - Confirmación de matrícula en el curso/programa.
     * - Resumen y datos del comprobante de pago emitido (Boleta o Factura SUNAT).
     *
     * @param email Dirección de correo electrónico del comprador.
     * @param nombre Nombre completo o razón social del cliente.
     * @param cursoTitulo Título del programa o curso adquirido.
     * @param moodlePassword Contraseña generada para el acceso al campus.
     * @param comprobante Comprobante tributario emitido.
     */
    void enviarAccesosYFactura(
            String email,
            String nombre,
            String cursoTitulo,
            String moodlePassword,
            ComprobantePago comprobante
    );

    /**
     * Envía un correo electrónico de bienvenida institucional a un colaborador matriculado
     * en un programa corporativo In-Company con sus credenciales del Campus Moodle.
     */
    void enviarBienvenidaCorporativa(
            String email,
            String nombre,
            String empresaNombre,
            String cursoTitulo,
            String username,
            String moodlePassword
    );

    /**
     * Envía un correo electrónico con el enlace de activación seguro para clientes invitados o cuentas pendientes.
     *
     * @param email Dirección de correo electrónico del usuario.
     * @param nombre Nombre del usuario.
     * @param enlaceActivacion URL absoluta autorizada con el token para activar la cuenta.
     */
    void enviarEnlaceActivacion(
            String email,
            String nombre,
            String enlaceActivacion
    );

    /**
     * Sobrecarga de envío de activación que incluye el identificador único e inmutable del intento.
     *
     * @param email Dirección de correo electrónico del usuario.
     * @param nombre Nombre del usuario.
     * @param enlaceActivacion URL absoluta autorizada con el token para activar la cuenta.
     * @param idIntento Identificador técnico único e inmutable de este intento de envío.
     */
    default void enviarEnlaceActivacion(
            String email,
            String nombre,
            String enlaceActivacion,
            String idIntento
    ) {
        enviarEnlaceActivacion(email, nombre, enlaceActivacion);
    }

    /**
     * Aborta activamente los recursos de red (transporte/socket) asociados exclusivamente al intento indicado,
     * permitiendo una cancelación efectiva e inmediata sin afectar otros envíos al mismo u otros destinatarios.
     *
     * @param idIntento Identificador técnico único del intento a abortar.
     */
    default void abortarEnvio(String idIntento) {
        // Implementación por defecto no-op para adaptadores o pruebas que no gestionen sockets reales
    }
}

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
}

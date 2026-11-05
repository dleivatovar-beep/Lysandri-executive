package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.whatsapp.ResultadoLlamadaWhatsApp;

import java.util.List;

public interface WhatsAppCloudClientPort {

    /**
     * Envía un mensaje de texto simple a través de la API oficial de WhatsApp Cloud.
     * @return Objeto estructurado que distingue ACEPTADO (con wamid), FALLIDO_CONFIRMADO o RESULTADO_INCIERTO.
     */
    ResultadoLlamadaWhatsApp enviarMensajeTexto(String telefono, String texto);

    /**
     * Envía un mensaje de texto simple propagando idSolicitud en biz_opaque_callback_data para correlación con webhooks.
     */
    ResultadoLlamadaWhatsApp enviarMensajeTexto(String telefono, String texto, String idSolicitud);

    /**
     * Envía una plantilla aprobada a través de la API oficial de WhatsApp Cloud.
     * @return Objeto estructurado con el resultado de la llamada.
     */
    ResultadoLlamadaWhatsApp enviarPlantilla(String telefono, String templateName, String languageCode, List<String> parametros);

    /**
     * Envía una plantilla aprobada propagando idSolicitud en biz_opaque_callback_data para correlación con webhooks.
     */
    ResultadoLlamadaWhatsApp enviarPlantilla(String telefono, String templateName, String languageCode, List<String> parametros, String idSolicitud);

    /**
     * Valida la firma HMAC-SHA256 del webhook entrante utilizando el app-secret configurado.
     */
    boolean validarFirmaWebhook(String rawPayload, String signatureHeader);

    /**
     * Valida si el token de verificación coincide con el configurado.
     */
    boolean validarTokenVerificacion(String verifyToken);

    /**
     * Retorna si la integración se encuentra habilitada operativamente.
     */
    boolean isHabilitado();
}

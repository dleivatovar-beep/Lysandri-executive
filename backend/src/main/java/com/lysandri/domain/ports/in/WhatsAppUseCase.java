package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.whatsapp.MensajeWhatsApp;

import java.util.List;

public interface WhatsAppUseCase {

    String verificarWebhook(String mode, String token, String challenge);

    void procesarWebhook(String rawPayload, String signatureHeader);

    MensajeWhatsApp enviarMensajeOperador(String destinatario, String texto, String operador);

    MensajeWhatsApp enviarMensajeOperador(String destinatario, String texto, String operador, String idSolicitud);

    List<MensajeWhatsApp> consultarConversacion(String telefono);
}

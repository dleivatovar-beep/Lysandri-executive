package com.lysandri.domain.model.whatsapp;

/**
 * Resultado de una reserva atómica para despacho hacia el proveedor de WhatsApp.
 * Garantiza que exactamente un hilo o proceso quede autorizado para contactar a la red.
 */
public record ReservaEnvioWhatsApp(
        MensajeWhatsApp mensaje,
        boolean autorizadoParaEnvio,
        String tokenIntento
) {}


package com.lysandri.domain.model.whatsapp;

public record ResultadoLlamadaWhatsApp(
        TipoResultadoLlamada tipo,
        String wamid,
        String errorDetalle
) {
    public enum TipoResultadoLlamada {
        ACEPTADO,
        FALLIDO_CONFIRMADO,
        RESULTADO_INCIERTO
    }

    public static ResultadoLlamadaWhatsApp aceptado(String wamid) {
        return new ResultadoLlamadaWhatsApp(TipoResultadoLlamada.ACEPTADO, wamid, null);
    }

    public static ResultadoLlamadaWhatsApp fallido(String errorDetalle) {
        return new ResultadoLlamadaWhatsApp(TipoResultadoLlamada.FALLIDO_CONFIRMADO, null, errorDetalle);
    }

    public static ResultadoLlamadaWhatsApp incierto(String motivo) {
        return new ResultadoLlamadaWhatsApp(TipoResultadoLlamada.RESULTADO_INCIERTO, null, motivo);
    }
}


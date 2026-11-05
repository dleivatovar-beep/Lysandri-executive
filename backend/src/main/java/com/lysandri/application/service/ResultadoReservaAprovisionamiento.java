package com.lysandri.application.service;

import com.lysandri.domain.model.Orden;
import lombok.Value;

@Value
public class ResultadoReservaAprovisionamiento {
    public enum EstadoReserva {
        RESERVADO,
        YA_COMPLETADO,
        EN_PROCESO
    }

    EstadoReserva estado;
    Orden orden;
    String ownerToken;

    public static ResultadoReservaAprovisionamiento reservado(Orden orden, String ownerToken) {
        return new ResultadoReservaAprovisionamiento(EstadoReserva.RESERVADO, orden, ownerToken);
    }

    public static ResultadoReservaAprovisionamiento yaCompletado(Orden orden) {
        return new ResultadoReservaAprovisionamiento(EstadoReserva.YA_COMPLETADO, orden, null);
    }

    public static ResultadoReservaAprovisionamiento enProceso(Orden orden) {
        return new ResultadoReservaAprovisionamiento(EstadoReserva.EN_PROCESO, orden, null);
    }

    public boolean isReservado() {
        return estado == EstadoReserva.RESERVADO;
    }

    public boolean isYaCompletado() {
        return estado == EstadoReserva.YA_COMPLETADO;
    }

    public boolean isEnProceso() {
        return estado == EstadoReserva.EN_PROCESO;
    }
}

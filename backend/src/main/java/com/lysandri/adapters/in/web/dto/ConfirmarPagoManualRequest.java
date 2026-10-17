package com.lysandri.adapters.in.web.dto;

import lombok.Data;

@Data
public class ConfirmarPagoManualRequest {
    private String numeroOperacion;
    private String metodoPago;
    private String notasAdmin;
}

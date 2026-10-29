package com.lysandri.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmarPagoManualRequest {

    @NotBlank(message = "El número de operación es obligatorio")
    private String numeroOperacion;

    @NotBlank(message = "El banco de destino es obligatorio (ej. BCP, INTERBANK)")
    private String bancoDestino;

    @NotBlank(message = "La cuenta de destino es obligatoria")
    private String cuentaDestino;

    @NotNull(message = "El importe verificado es obligatorio")
    @Positive(message = "El importe verificado debe ser mayor a cero")
    private BigDecimal montoVerificado;

    @NotBlank(message = "La moneda verificada es obligatoria (ej. USD, PEN)")
    private String monedaVerificada;

    @NotNull(message = "La fecha de abono es obligatoria")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.lysandri.config.OffsetDateTimeFlexibleDeserializer.class)
    private OffsetDateTime fechaAbono;

    private String metodoPago;
    private String notasAdmin;
    private String observacionVerificacion;
}

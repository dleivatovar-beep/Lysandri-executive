package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleOrden {
    private Long idDetalle;
    private Long idOrden;
    private Long idPrograma;
    private Programa programa;
    private BigDecimal precioUnitario;
    private Integer cantidad;
    private BigDecimal subtotal;
    private boolean moodleMatriculado;
}

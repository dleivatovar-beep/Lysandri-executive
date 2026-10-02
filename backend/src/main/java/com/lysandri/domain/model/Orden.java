package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Orden {
    private Long idOrden;
    private Long idUser;
    private Usuario usuario;
    private String codigoOrden;
    private OffsetDateTime fechaOrden;
    private EstadoOrden estadoOrden;
    private BigDecimal total;
    private String moneda;
    private String metodoPago;
    private String stripeSessionId;
    private String stripePaymentIntentId;
    private boolean moodleMatriculaSincronizada;
    private OffsetDateTime fechaPago;

    private String tipoComprobanteSolicitado;
    private String numeroDocumentoCliente;
    private String nombreFacturacion;

    @Builder.Default
    private List<DetalleOrden> items = new ArrayList<>();
}

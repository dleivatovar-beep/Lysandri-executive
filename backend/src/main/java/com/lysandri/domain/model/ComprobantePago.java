package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprobantePago {
    private Long idComprobante;
    private Long idOrden;
    private TipoComprobante tipo;
    private String serie;
    private Integer correlativo;
    private String tipoDocumentoIdentidad;
    private String numeroDocumentoIdentidad;
    private String razonSocialONombre;
    private BigDecimal montoSubtotal;
    private BigDecimal montoIgv;
    private BigDecimal montoTotal;
    private String moneda;
    private LocalDateTime fechaEmision;
    private EstadoComprobante estado;
    private String pdfUrl;
    private String codigoHash;
    private BigDecimal montoDetraccion;
    private BigDecimal porcentajeDetraccion;
    private String medioPago;
    private String motivoAnulacion;
    private LocalDateTime fechaAnulacion;
}

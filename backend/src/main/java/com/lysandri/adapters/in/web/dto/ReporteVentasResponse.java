package com.lysandri.adapters.in.web.dto;

import com.lysandri.domain.model.ComprobantePago;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReporteVentasResponse {
    private BigDecimal totalVentas;
    private BigDecimal totalBaseImponible;
    private BigDecimal totalIgvRecaudado;
    private BigDecimal totalDetracciones;
    private int cantidadComprobantes;
    private int cantidadBoletas;
    private int cantidadFacturas;
    private int cantidadAnulados;
    private LiquidacionTributariaDto liquidacionTributaria;
    private List<VentaConsolidadaDto> ventasConsolidadas;
    private List<ComprobantePago> comprobantesEmitidos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LiquidacionTributariaDto {
        private String periodoTributario;
        private String regimenTributario;
        private String rucEmisor;
        private String razonSocialEmisor;
        private BigDecimal ventasNetasGravadas;
        private BigDecimal debitoFiscalIgv;
        private BigDecimal tasaRentaMype;
        private BigDecimal pagoACuentaRentaMype;
        private BigDecimal totalDetraccionesSpot;
        private BigDecimal impuestoTotalProyectado;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VentaConsolidadaDto {
        private Long idOrden;
        private String codigoOrden;
        private String cliente;
        private String documentoIdentidad;
        private String tipoComprobante;
        private String serieNumero;
        private String cuo;
        private BigDecimal subtotal;
        private BigDecimal igv;
        private BigDecimal total;
        private BigDecimal montoDetraccion;
        private BigDecimal porcentajeDetraccion;
        private String moneda;
        private String estadoOrden;
        private String estadoComprobante;
        private String medioPago;
        private String codigoHash;
        private OffsetDateTime fechaOrden;
        private LocalDateTime fechaEmision;
        private String motivoAnulacion;
        private String pdfUrl;
    }
}

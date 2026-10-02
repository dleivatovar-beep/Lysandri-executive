package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ReporteVentasResponse;
import com.lysandri.domain.model.ComprobantePago;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.TipoComprobante;
import com.lysandri.domain.ports.out.BillingPort;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/accounting")
@RequiredArgsConstructor
@Tag(name = "Contabilidad Interna y Auditoría", description = "Endpoints de auditoría contable y reporte tributario protegidos por PIN interno")
public class InternalAccountingController {

    private final BillingPort billingPort;
    private final OrderRepositoryPort orderRepository;

    @Value("${accounting.audit-pin:LYS-AUDIT-2026-SECURE}")
    private String auditPin;

    @GetMapping("/ventas")
    @Operation(
            summary = "Reporte consolidado de ventas, base imponible e IGV tributario",
            description = "Endpoint contable interno protegido exclusivamente mediante la cabecera X-Audit-PIN",
            parameters = {
                    @Parameter(
                            name = "X-Audit-PIN",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "PIN de auditoría interna contable"
                    )
            }
    )
    public ResponseEntity<?> obtenerReporteVentas(
            @RequestHeader(value = "X-Audit-PIN", required = false) String pinHeader) {

        log.info("Acceso solicitado a reporte de contabilidad interna");

        // Validación estricta de seguridad por PIN interno
        if (pinHeader == null || !pinHeader.trim().equals(auditPin.trim())) {
            log.warn("Intento de acceso contable no autorizado. PIN recibido: {}", pinHeader != null ? "****" : "NULO");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Acceso no autorizado",
                            "mensaje", "Cabecera X-Audit-PIN inválida o ausente",
                            "codigo", "AUDIT_PIN_INVALID"
                    ));
        }

        List<ComprobantePago> comprobantes = billingPort.listarComprobantes();
        List<Orden> ordenes = orderRepository.listarTodas();

        Map<Long, ComprobantePago> comprobantePorOrden = comprobantes.stream()
                .filter(c -> c.getIdOrden() != null)
                .collect(Collectors.toMap(ComprobantePago::getIdOrden, Function.identity(), (c1, c2) -> c1));

        BigDecimal totalVentas = BigDecimal.ZERO;
        BigDecimal totalBaseImponible = BigDecimal.ZERO;
        BigDecimal totalIgvRecaudado = BigDecimal.ZERO;
        int cantidadBoletas = 0;
        int cantidadFacturas = 0;

        for (ComprobantePago comp : comprobantes) {
            if (comp.getMontoTotal() != null) {
                totalVentas = totalVentas.add(comp.getMontoTotal());
            }
            if (comp.getMontoSubtotal() != null) {
                totalBaseImponible = totalBaseImponible.add(comp.getMontoSubtotal());
            }
            if (comp.getMontoIgv() != null) {
                totalIgvRecaudado = totalIgvRecaudado.add(comp.getMontoIgv());
            }
            if (comp.getTipo() == TipoComprobante.BOLETA) {
                cantidadBoletas++;
            } else if (comp.getTipo() == TipoComprobante.FACTURA) {
                cantidadFacturas++;
            }
        }

        List<ReporteVentasResponse.VentaConsolidadaDto> ventasConsolidadas = new ArrayList<>();
        for (Orden orden : ordenes) {
            ComprobantePago comp = comprobantePorOrden.get(orden.getIdOrden());

            String serieNumero = comp != null ? String.format("%s-%08d", comp.getSerie(), comp.getCorrelativo()) : "PENDIENTE";
            String tipoComp = comp != null ? comp.getTipo().name() : (orden.getTipoComprobanteSolicitado() != null ? orden.getTipoComprobanteSolicitado() : "BOLETA");
            BigDecimal subtotal = comp != null ? comp.getMontoSubtotal() : BigDecimal.ZERO;
            BigDecimal igv = comp != null ? comp.getMontoIgv() : BigDecimal.ZERO;
            BigDecimal total = comp != null ? comp.getMontoTotal() : orden.getTotal();

            ventasConsolidadas.add(ReporteVentasResponse.VentaConsolidadaDto.builder()
                    .idOrden(orden.getIdOrden())
                    .codigoOrden(orden.getCodigoOrden())
                    .cliente(orden.getNombreFacturacion() != null ? orden.getNombreFacturacion() : (orden.getUsuario() != null ? orden.getUsuario().getNombreCompleto() : "Invitado"))
                    .documentoIdentidad(orden.getNumeroDocumentoCliente() != null ? orden.getNumeroDocumentoCliente() : "N/A")
                    .tipoComprobante(tipoComp)
                    .serieNumero(serieNumero)
                    .subtotal(subtotal)
                    .igv(igv)
                    .total(total)
                    .moneda(orden.getMoneda() != null ? orden.getMoneda() : "PEN")
                    .estadoOrden(orden.getEstadoOrden() != null ? orden.getEstadoOrden().name() : "DESCONOCIDO")
                    .fechaOrden(orden.getFechaOrden())
                    .fechaEmision(comp != null ? comp.getFechaEmision() : null)
                    .pdfUrl(comp != null ? comp.getPdfUrl() : null)
                    .build());
        }

        ReporteVentasResponse reporte = ReporteVentasResponse.builder()
                .totalVentas(totalVentas)
                .totalBaseImponible(totalBaseImponible)
                .totalIgvRecaudado(totalIgvRecaudado)
                .cantidadComprobantes(comprobantes.size())
                .cantidadBoletas(cantidadBoletas)
                .cantidadFacturas(cantidadFacturas)
                .ventasConsolidadas(ventasConsolidadas)
                .comprobantesEmitidos(comprobantes)
                .build();

        return ResponseEntity.ok(reporte);
    }
}

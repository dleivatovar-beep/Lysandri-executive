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

import com.lysandri.domain.model.EstadoComprobante;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

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

    private static final String RUC_EMISOR = "20609812451";
    private static final String RAZON_SOCIAL_EMISOR = "YUNIX INGENIEROS E.I.R.L.";
    private static final DateTimeFormatter FORMATO_FECHA_SUNAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
        BigDecimal totalDetracciones = BigDecimal.ZERO;
        int cantidadBoletas = 0;
        int cantidadFacturas = 0;
        int cantidadAnulados = 0;

        for (ComprobantePago comp : comprobantes) {
            if (comp.getEstado() == EstadoComprobante.ANULADO) {
                cantidadAnulados++;
                continue; // Anulados no suman a base imponible fiscal
            }

            if (comp.getMontoTotal() != null) {
                totalVentas = totalVentas.add(comp.getMontoTotal());
            }
            if (comp.getMontoSubtotal() != null) {
                totalBaseImponible = totalBaseImponible.add(comp.getMontoSubtotal());
            }
            if (comp.getMontoIgv() != null) {
                totalIgvRecaudado = totalIgvRecaudado.add(comp.getMontoIgv());
            }
            if (comp.getMontoDetraccion() != null) {
                totalDetracciones = totalDetracciones.add(comp.getMontoDetraccion());
            }

            if (comp.getTipo() == TipoComprobante.BOLETA) {
                cantidadBoletas++;
            } else if (comp.getTipo() == TipoComprobante.FACTURA) {
                cantidadFacturas++;
            }
        }

        // Liquidación Tributaria SUNAT (Régimen MYPE Tributario)
        BigDecimal pagoACuentaRentaMype = totalBaseImponible.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal impuestoTotalProyectado = totalIgvRecaudado.add(pagoACuentaRentaMype);

        ReporteVentasResponse.LiquidacionTributariaDto liquidacion = ReporteVentasResponse.LiquidacionTributariaDto.builder()
                .periodoTributario("2026-10")
                .regimenTributario("Régimen MYPE Tributario (RMT) - SUNAT")
                .rucEmisor(RUC_EMISOR)
                .razonSocialEmisor(RAZON_SOCIAL_EMISOR)
                .ventasNetasGravadas(totalBaseImponible)
                .debitoFiscalIgv(totalIgvRecaudado)
                .tasaRentaMype(new BigDecimal("1.00"))
                .pagoACuentaRentaMype(pagoACuentaRentaMype)
                .totalDetraccionesSpot(totalDetracciones)
                .impuestoTotalProyectado(impuestoTotalProyectado)
                .build();

        List<ReporteVentasResponse.VentaConsolidadaDto> ventasConsolidadas = new ArrayList<>();
        int cuoContador = 1;
        for (Orden orden : ordenes) {
            ComprobantePago comp = comprobantePorOrden.get(orden.getIdOrden());

            String serieNumero = comp != null ? String.format("%s-%08d", comp.getSerie(), comp.getCorrelativo()) : "PENDIENTE";
            String tipoComp = comp != null ? comp.getTipo().name() : (orden.getTipoComprobanteSolicitado() != null ? orden.getTipoComprobanteSolicitado() : "BOLETA");
            BigDecimal subtotal = comp != null ? comp.getMontoSubtotal() : BigDecimal.ZERO;
            BigDecimal igv = comp != null ? comp.getMontoIgv() : BigDecimal.ZERO;
            BigDecimal total = comp != null ? comp.getMontoTotal() : orden.getTotal();
            BigDecimal montoDetraccion = comp != null ? comp.getMontoDetraccion() : BigDecimal.ZERO;
            BigDecimal porcDetraccion = comp != null ? comp.getPorcentajeDetraccion() : BigDecimal.ZERO;
            String estadoComp = comp != null ? comp.getEstado().name() : "PENDIENTE";
            String medioPago = comp != null && comp.getMedioPago() != null ? comp.getMedioPago() : (orden.getMetodoPago() != null ? orden.getMetodoPago() : "STRIPE_CHECKOUT");
            String hash = comp != null ? comp.getCodigoHash() : null;
            String motivoAnulacion = comp != null ? comp.getMotivoAnulacion() : null;
            String cuo = String.format("M%06d", cuoContador++);

            ventasConsolidadas.add(ReporteVentasResponse.VentaConsolidadaDto.builder()
                    .idOrden(orden.getIdOrden())
                    .codigoOrden(orden.getCodigoOrden())
                    .cliente(orden.getNombreFacturacion() != null ? orden.getNombreFacturacion() : (orden.getUsuario() != null ? orden.getUsuario().getNombreCompleto() : "Invitado"))
                    .documentoIdentidad(orden.getNumeroDocumentoCliente() != null ? orden.getNumeroDocumentoCliente() : "N/A")
                    .tipoComprobante(tipoComp)
                    .serieNumero(serieNumero)
                    .cuo(cuo)
                    .subtotal(subtotal)
                    .igv(igv)
                    .total(total)
                    .montoDetraccion(montoDetraccion)
                    .porcentajeDetraccion(porcDetraccion)
                    .moneda(orden.getMoneda() != null ? orden.getMoneda() : "PEN")
                    .estadoOrden(orden.getEstadoOrden() != null ? orden.getEstadoOrden().name() : "DESCONOCIDO")
                    .estadoComprobante(estadoComp)
                    .medioPago(medioPago)
                    .codigoHash(hash)
                    .fechaOrden(orden.getFechaOrden())
                    .fechaEmision(comp != null ? comp.getFechaEmision() : null)
                    .motivoAnulacion(motivoAnulacion)
                    .pdfUrl(comp != null ? comp.getPdfUrl() : null)
                    .build());
        }

        ReporteVentasResponse reporte = ReporteVentasResponse.builder()
                .totalVentas(totalVentas)
                .totalBaseImponible(totalBaseImponible)
                .totalIgvRecaudado(totalIgvRecaudado)
                .totalDetracciones(totalDetracciones)
                .cantidadComprobantes(comprobantes.size())
                .cantidadBoletas(cantidadBoletas)
                .cantidadFacturas(cantidadFacturas)
                .cantidadAnulados(cantidadAnulados)
                .liquidacionTributaria(liquidacion)
                .ventasConsolidadas(ventasConsolidadas)
                .comprobantesEmitidos(comprobantes)
                .build();

        return ResponseEntity.ok(reporte);
    }

    @GetMapping(value = "/ventas/export-sire", produces = "text/plain;charset=UTF-8")
    @Operation(
            summary = "Generación de Libro Electrónico RVIE SIRE 14.1 para SUNAT",
            description = "Descarga del archivo estructurado con delimitador pipe (|) según el formato SUNAT SIRE 14.1 RVIE"
    )
    public ResponseEntity<?> exportarTxtSire(
            @RequestHeader(value = "X-Audit-PIN", required = false) String pinHeader) {

        if (pinHeader == null || !pinHeader.trim().equals(auditPin.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Acceso denegado: PIN de auditoría inválido");
        }

        List<ComprobantePago> comprobantes = billingPort.listarComprobantes();
        StringBuilder txtSire = new StringBuilder();

        int cuo = 1;
        for (ComprobantePago c : comprobantes) {
            String tipoCompCodigo = (c.getTipo() == TipoComprobante.FACTURA) ? "01" : "03";
            String tipoDocIdCodigo = "6"; // 6 = RUC
            if ("DNI".equalsIgnoreCase(c.getTipoDocumentoIdentidad())) {
                tipoDocIdCodigo = "1";
            }

            String fechaEmisionStr = c.getFechaEmision() != null ? c.getFechaEmision().format(FORMATO_FECHA_SUNAT) : "01/10/2026";
            String estadoCodigo = (c.getEstado() == EstadoComprobante.ANULADO) ? "2" : "1";
            String cuoStr = String.format("M%06d", cuo++);

            // Estructura SUNAT SIRE RVIE Formato 14.1
            txtSire.append(RUC_EMISOR).append("|")
                    .append(RAZON_SOCIAL_EMISOR).append("|")
                    .append("20261000").append("|") // Periodo YYYYMM00
                    .append(cuoStr).append("|") // CUO
                    .append(fechaEmisionStr).append("|") // Fecha Emisión
                    .append(fechaEmisionStr).append("|") // Fecha Vcto
                    .append(tipoCompCodigo).append("|") // 01=Factura, 03=Boleta
                    .append(c.getSerie()).append("|")
                    .append(String.format("%08d", c.getCorrelativo())).append("|")
                    .append("|") // Nro final (vacío)
                    .append(tipoDocIdCodigo).append("|")
                    .append(c.getNumeroDocumentoIdentidad()).append("|")
                    .append(c.getRazonSocialONombre().replace("|", " ")).append("|")
                    .append("0.00").append("|") // Exportación
                    .append(c.getEstado() == EstadoComprobante.ANULADO ? "0.00" : c.getMontoSubtotal()).append("|") // Base Gravada
                    .append("0.00").append("|") // Descuento BI
                    .append(c.getEstado() == EstadoComprobante.ANULADO ? "0.00" : c.getMontoIgv()).append("|") // IGV
                    .append("0.00").append("|") // Descuento IGV
                    .append("0.00").append("|") // Exonerado
                    .append("0.00").append("|") // Inafecto
                    .append("0.00").append("|") // ISC
                    .append("0.00").append("|") // IVAP
                    .append("0.00").append("|") // ICBPER
                    .append("0.00").append("|") // Otros cargos
                    .append(c.getEstado() == EstadoComprobante.ANULADO ? "0.00" : c.getMontoTotal()).append("|") // Total
                    .append("PEN").append("|") // Moneda
                    .append("1.000").append("|") // TC
                    .append("|").append("|").append("|").append("|") // Ref notas crédito
                    .append(estadoCodigo).append("|") // 1=Vigente, 2=Anulado
                    .append(c.getCodigoHash() != null ? c.getCodigoHash() : "").append("|")
                    .append("\r\n");
        }

        String fileName = "LE2060981245120261000140100001111.txt";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(txtSire.toString());
    }

    @PostMapping("/comprobantes/{idComprobante}/anular")
    @Operation(summary = "Anular un comprobante de pago emitido por causa tributaria o nota de crédito")
    public ResponseEntity<?> anularComprobante(
            @RequestHeader(value = "X-Audit-PIN", required = false) String pinHeader,
            @PathVariable Long idComprobante,
            @RequestBody(required = false) Map<String, String> body) {

        if (pinHeader == null || !pinHeader.trim().equals(auditPin.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Acceso denegado: PIN de auditoría no válido"));
        }

        String motivo = (body != null && body.containsKey("motivo")) ? body.get("motivo") : "Anulación solicitada por administración contable";
        try {
            ComprobantePago comprobante = billingPort.anularComprobante(idComprobante, motivo);
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Comprobante anulado exitosamente",
                    "idComprobante", comprobante.getIdComprobante(),
                    "serie", comprobante.getSerie(),
                    "correlativo", comprobante.getCorrelativo(),
                    "estado", comprobante.getEstado().name(),
                    "motivoAnulacion", comprobante.getMotivoAnulacion()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}

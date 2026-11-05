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
import com.lysandri.domain.model.AuditoriaContable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import com.lysandri.domain.ports.out.AccountingAuditPort;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/accounting")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CONTABILIDAD', 'FINANZAS', 'AUDITOR')")
@Tag(name = "Contabilidad Interna y Auditoría", description = "Endpoints de auditoría contable y reporte tributario protegidos por PIN interno")
public class InternalAccountingController {

    private final BillingPort billingPort;
    private final OrderRepositoryPort orderRepository;
    private final AccountingAuditPort accountingAuditPort;

    @Value("${accounting.audit-pin:}")
    private String auditPin;

    private static final String RUC_EMISOR = "20609812451";
    private static final String RAZON_SOCIAL_EMISOR = "LYSANDRI";
    private static final DateTimeFormatter FORMATO_FECHA_SUNAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @GetMapping("/ventas")
    @Operation(
            summary = "Reporte consolidado de ventas, base imponible e IGV tributario",
            description = "Endpoint contable interno protegido mediante sesión autorizada y cabecera X-Audit-PIN",
            parameters = {
                    @Parameter(
                            name = "X-Audit-PIN",
                            in = ParameterIn.HEADER,
                            required = true,
                            description = "PIN de auditoría interna contable"
                    ),
                    @Parameter(
                            name = "periodo",
                            in = ParameterIn.QUERY,
                            required = false,
                            description = "Período tributario en formato YYYY-MM (ej. 2026-10)"
                    )
            }
    )
    public ResponseEntity<?> obtenerReporteVentas(
            @RequestHeader(value = "X-Audit-PIN", required = false) String pinHeader,
            @RequestParam(value = "periodo", required = false) String periodo) {

        log.info("Acceso solicitado a reporte de contabilidad interna para período: {}", periodo != null ? periodo : "TODOS");

        if (!validarPin(pinHeader)) {
            log.warn("Intento de acceso contable no autorizado. PIN recibido: {}", pinHeader != null ? "****" : "NULO");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Acceso no autorizado",
                            "mensaje", "Cabecera X-Audit-PIN inválida o ausente",
                            "codigo", "AUDIT_PIN_INVALID"
                    ));
        }

        YearMonth ym = null;
        LocalDateTime inicioPeriodo = null;
        LocalDateTime finPeriodo = null;

        if (periodo != null && !periodo.isBlank()) {
            String pTrim = periodo.trim();
            if (!pTrim.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "error", "Período inválido",
                                "mensaje", "El formato del período debe ser YYYY-MM (ej. 2026-10)",
                                "codigo", "PERIODO_INVALIDO"
                        ));
            }
            ym = YearMonth.parse(pTrim);
            inicioPeriodo = ym.atDay(1).atStartOfDay();
            finPeriodo = ym.atEndOfMonth().atTime(23, 59, 59, 999999999);
        }

        List<ComprobantePago> comprobantesTodos = billingPort.listarComprobantes();
        List<Orden> ordenesTodas = orderRepository.listarTodas();

        Map<Long, ComprobantePago> comprobantePorOrden = comprobantesTodos.stream()
                .filter(c -> c.getIdOrden() != null)
                .collect(Collectors.toMap(ComprobantePago::getIdOrden, Function.identity(), (c1, c2) -> c1));

        final LocalDateTime fInicio = inicioPeriodo;
        final LocalDateTime fFin = finPeriodo;

        // Filtrar comprobantes del período
        List<ComprobantePago> comprobantesPeriodo = comprobantesTodos.stream()
                .filter(c -> {
                    if (fInicio == null || fFin == null) return true;
                    if (c.getFechaEmision() == null) return false;
                    return !c.getFechaEmision().isBefore(fInicio) && !c.getFechaEmision().isAfter(fFin);
                })
                .toList();

        // Filtrar órdenes del período según fecha efectiva (fechaEmision si tiene comprobante, o fechaOrden)
        List<Orden> ordenesPeriodo = ordenesTodas.stream()
                .filter(o -> {
                    if (fInicio == null || fFin == null) return true;
                    ComprobantePago c = comprobantePorOrden.get(o.getIdOrden());
                    LocalDateTime fechaEfectiva = (c != null && c.getFechaEmision() != null)
                            ? c.getFechaEmision()
                            : (o.getFechaOrden() != null ? o.getFechaOrden().toLocalDateTime() : null);
                    if (fechaEfectiva == null) return false;
                    return !fechaEfectiva.isBefore(fInicio) && !fechaEfectiva.isAfter(fFin);
                })
                .toList();

        BigDecimal totalVentasPen = BigDecimal.ZERO;
        BigDecimal totalBaseImponiblePen = BigDecimal.ZERO;
        BigDecimal totalIgvRecaudadoPen = BigDecimal.ZERO;
        BigDecimal totalDetraccionesPen = BigDecimal.ZERO;

        BigDecimal totalVentasUsd = BigDecimal.ZERO;
        BigDecimal totalBaseImponibleUsd = BigDecimal.ZERO;
        BigDecimal totalIgvRecaudadoUsd = BigDecimal.ZERO;
        BigDecimal totalDetraccionesUsd = BigDecimal.ZERO;

        int cantidadBoletas = 0;
        int cantidadFacturas = 0;
        int cantidadAnulados = 0;

        for (ComprobantePago comp : comprobantesPeriodo) {
            if (comp.getEstado() == EstadoComprobante.ANULADO) {
                cantidadAnulados++;
                continue; // Anulados no suman a base imponible fiscal ni a ventas netas
            }

            String moneda = comp.getMoneda() != null ? comp.getMoneda().trim().toUpperCase() : "PEN";
            BigDecimal total = comp.getMontoTotal() != null ? comp.getMontoTotal() : BigDecimal.ZERO;
            BigDecimal subtotal = comp.getMontoSubtotal() != null ? comp.getMontoSubtotal() : BigDecimal.ZERO;
            BigDecimal igv = comp.getMontoIgv() != null ? comp.getMontoIgv() : BigDecimal.ZERO;
            BigDecimal detraccion = comp.getMontoDetraccion() != null ? comp.getMontoDetraccion() : BigDecimal.ZERO;

            if ("USD".equals(moneda)) {
                totalVentasUsd = totalVentasUsd.add(total);
                totalBaseImponibleUsd = totalBaseImponibleUsd.add(subtotal);
                totalIgvRecaudadoUsd = totalIgvRecaudadoUsd.add(igv);
                totalDetraccionesUsd = totalDetraccionesUsd.add(detraccion);
            } else {
                totalVentasPen = totalVentasPen.add(total);
                totalBaseImponiblePen = totalBaseImponiblePen.add(subtotal);
                totalIgvRecaudadoPen = totalIgvRecaudadoPen.add(igv);
                totalDetraccionesPen = totalDetraccionesPen.add(detraccion);
            }

            if (comp.getTipo() == TipoComprobante.BOLETA) {
                cantidadBoletas++;
            } else if (comp.getTipo() == TipoComprobante.FACTURA) {
                cantidadFacturas++;
            }
        }

        // Liquidación Tributaria SUNAT (Régimen MYPE Tributario) calculada exclusivamente sobre base imponible en PEN
        BigDecimal pagoACuentaRentaMype = totalBaseImponiblePen.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal impuestoTotalProyectado = totalIgvRecaudadoPen.add(pagoACuentaRentaMype);
        String periodoTributarioStr = (ym != null) ? ym.toString() : "TODOS";

        ReporteVentasResponse.LiquidacionTributariaDto liquidacion = ReporteVentasResponse.LiquidacionTributariaDto.builder()
                .periodoTributario(periodoTributarioStr)
                .regimenTributario("Régimen MYPE Tributario (RMT) - SUNAT")
                .rucEmisor(RUC_EMISOR)
                .razonSocialEmisor(RAZON_SOCIAL_EMISOR)
                .ventasNetasGravadas(totalBaseImponiblePen)
                .debitoFiscalIgv(totalIgvRecaudadoPen)
                .tasaRentaMype(new BigDecimal("1.00"))
                .pagoACuentaRentaMype(pagoACuentaRentaMype)
                .totalDetraccionesSpot(totalDetraccionesPen)
                .impuestoTotalProyectado(impuestoTotalProyectado)
                .build();

        List<ReporteVentasResponse.VentaConsolidadaDto> ventasConsolidadas = new ArrayList<>();
        int pendientesSinComprobante = 0;

        for (Orden orden : ordenesPeriodo) {
            ComprobantePago comp = comprobantePorOrden.get(orden.getIdOrden());

            if (comp == null) {
                pendientesSinComprobante++;
            }

            Long idComp = comp != null ? comp.getIdComprobante() : null;
            String serieNumero = comp != null ? String.format("%s-%08d", comp.getSerie(), comp.getCorrelativo()) : "PENDIENTE";
            String tipoComp = comp != null ? comp.getTipo().name() : (orden.getTipoComprobanteSolicitado() != null ? orden.getTipoComprobanteSolicitado() : "BOLETA");
            BigDecimal subtotal = comp != null ? comp.getMontoSubtotal() : BigDecimal.ZERO;
            BigDecimal igv = comp != null ? comp.getMontoIgv() : BigDecimal.ZERO;
            BigDecimal total = comp != null ? comp.getMontoTotal() : (orden.getTotal() != null ? orden.getTotal() : BigDecimal.ZERO);
            BigDecimal montoDetraccion = comp != null ? comp.getMontoDetraccion() : BigDecimal.ZERO;
            BigDecimal porcDetraccion = comp != null ? comp.getPorcentajeDetraccion() : BigDecimal.ZERO;
            String estadoComp = comp != null ? comp.getEstado().name() : "PENDIENTE";
            String medioPago = comp != null && comp.getMedioPago() != null ? comp.getMedioPago() : (orden.getMetodoPago() != null ? orden.getMetodoPago() : "IZIPAY_ONLINE");
            String hash = comp != null ? comp.getCodigoHash() : null;
            String motivoAnulacion = comp != null ? comp.getMotivoAnulacion() : null;
            // CUO determinístico y persistente basado en la entidad registrada
            String cuo = (comp != null && comp.getIdComprobante() != null)
                    ? String.format("CP%08d", comp.getIdComprobante())
                    : String.format("ORD%08d", orden.getIdOrden());
            String moneda = comp != null && comp.getMoneda() != null ? comp.getMoneda() : (orden.getMoneda() != null ? orden.getMoneda() : "PEN");

            ventasConsolidadas.add(ReporteVentasResponse.VentaConsolidadaDto.builder()
                    .idComprobante(idComp)
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
                    .moneda(moneda)
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
                .totalVentas(totalVentasPen)
                .totalBaseImponible(totalBaseImponiblePen)
                .totalIgvRecaudado(totalIgvRecaudadoPen)
                .totalDetracciones(totalDetraccionesPen)
                .totalVentasPen(totalVentasPen)
                .totalBaseImponiblePen(totalBaseImponiblePen)
                .totalIgvRecaudadoPen(totalIgvRecaudadoPen)
                .totalDetraccionesPen(totalDetraccionesPen)
                .totalVentasUsd(totalVentasUsd)
                .totalBaseImponibleUsd(totalBaseImponibleUsd)
                .totalIgvRecaudadoUsd(totalIgvRecaudadoUsd)
                .totalDetraccionesUsd(totalDetraccionesUsd)
                .cantidadPendientesSinComprobante(pendientesSinComprobante)
                .periodoSeleccionado(periodo != null ? periodo : "TODOS")
                .cantidadComprobantes(comprobantesPeriodo.size())
                .cantidadBoletas(cantidadBoletas)
                .cantidadFacturas(cantidadFacturas)
                .cantidadAnulados(cantidadAnulados)
                .liquidacionTributaria(liquidacion)
                .ventasConsolidadas(ventasConsolidadas)
                .comprobantesEmitidos(comprobantesPeriodo)
                .build();

        return ResponseEntity.ok(reporte);
    }

    public ResponseEntity<?> obtenerReporteVentas(String pinHeader) {
        return obtenerReporteVentas(pinHeader, null);
    }

    @GetMapping(value = "/ventas/export-sire")
    @Operation(
            summary = "Generación de Libro Electrónico RVIE SIRE 14.1 para SUNAT",
            description = "Descarga del archivo estructurado con delimitador pipe (|) según el formato SUNAT SIRE 14.1 RVIE para un período tributario mensual específico. Corresponde a una validación técnica interna del formato y no sustituye la constancia de recepción (CDR) emitida por la SUNAT."
    )
    public ResponseEntity<?> exportarTxtSire(
            @RequestHeader(value = "X-Audit-PIN", required = false) String pinHeader,
            @RequestParam(value = "periodo", required = false) String periodo) {

        if (!validarPin(pinHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Acceso denegado",
                            "mensaje", "PIN de auditoría contable inválido o ausente",
                            "codigo", "AUDIT_PIN_INVALID"
                    ));
        }

        // Exigir período explícito en formato YYYY-MM; rechazar vacíos, nulos o 'TODOS'
        if (periodo == null || periodo.isBlank() || "TODOS".equalsIgnoreCase(periodo.trim())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Período requerido para exportación SIRE",
                            "mensaje", "Debe especificar un período mensual tributario en formato YYYY-MM (ej. 2026-10). La exportación oficial SIRE no admite 'TODOS' ni valores vacíos.",
                            "codigo", "PERIODO_REQUERIDO_SIRE"
                    ));
        }

        String pTrim = periodo.trim();
        if (!pTrim.matches("^\\d{4}-(0[1-9]|1[0-2])$")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Período inválido",
                            "mensaje", "El formato del período debe ser YYYY-MM (ej. 2026-10)",
                            "codigo", "PERIODO_INVALIDO"
                    ));
        }

        YearMonth ym = YearMonth.parse(pTrim);
        LocalDateTime inicioPeriodo = ym.atDay(1).atStartOfDay();
        LocalDateTime finPeriodo = ym.atEndOfMonth().atTime(23, 59, 59, 999999999);
        String periodoSire = ym.format(DateTimeFormatter.ofPattern("yyyyMM")) + "00";

        List<ComprobantePago> todosComprobantes = billingPort.listarComprobantes();

        // 1. Detectar comprobantes emitidos o anulados sin fecha de emisión antes del filtro mensual.
        // Un documento emitido o anulado que carece de fecha obligatoria no puede asignarse con certeza
        // a ningún período tributario. Omitirlo silenciosamente generaría una exportación parcial o fraudulenta.
        List<Map<String, Object>> comprobantesSinFecha = new ArrayList<>();
        for (ComprobantePago c : todosComprobantes) {
            boolean esFiscalExigible = (c.getEstado() == EstadoComprobante.EMITIDO
                    || c.getEstado() == EstadoComprobante.ANULADO
                    || c.getEstado() == null);
            if (esFiscalExigible && c.getFechaEmision() == null) {
                comprobantesSinFecha.add(Map.of(
                        "idComprobante", c.getIdComprobante() != null ? c.getIdComprobante() : 0L,
                        "idOrden", c.getIdOrden() != null ? c.getIdOrden() : 0L,
                        "estado", c.getEstado() != null ? c.getEstado().name() : "DESCONOCIDO",
                        "camposFaltantes", List.of("fechaEmision"),
                        "motivo", "Comprobante emitido o anulado sin fecha de emisión: imposibilidad de determinar fehacientemente su período tributario"
                ));
            }
        }

        if (!comprobantesSinFecha.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Comprobantes fiscales con fecha de emisión indeterminada",
                            "mensaje", "No es posible exportar el período " + pTrim + " porque existen comprobantes emitidos o anulados que carecen de fecha de emisión obligatoria, impidiendo determinar fehacientemente su período tributario.",
                            "codigo", "COMPROBANTES_INCOMPLETOS",
                            "registrosIncompletos", comprobantesSinFecha
                    ));
        }

        // 2. Filtro mensual sobre comprobantes con fecha fehaciente comprobada
        List<ComprobantePago> comprobantes = todosComprobantes.stream()
                .filter(c -> c.getFechaEmision() != null
                        && !c.getFechaEmision().isBefore(inicioPeriodo)
                        && !c.getFechaEmision().isAfter(finPeriodo))
                .toList();

        if (comprobantes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Sin comprobantes emitidos",
                            "mensaje", "No existen comprobantes emitidos para el período " + pTrim + " para exportar a SIRE.",
                            "codigo", "SIN_COMPROBANTES_PERIODO"
                    ));
        }

        // Validación estricta de campos fiscales: no sustituir con valores de respaldo inventados
        List<Map<String, Object>> registrosIncompletos = new ArrayList<>();
        List<Map<String, Object>> registrosUsdSinTc = new ArrayList<>();

        for (ComprobantePago c : comprobantes) {
            List<String> camposFaltantes = new ArrayList<>();
            if (c.getSerie() == null || c.getSerie().isBlank()) {
                camposFaltantes.add("serie");
            }
            if (c.getCorrelativo() == null || c.getCorrelativo() <= 0) {
                camposFaltantes.add("correlativo");
            }
            if (c.getFechaEmision() == null) {
                camposFaltantes.add("fechaEmision");
            }
            if (c.getTipoDocumentoIdentidad() == null || c.getTipoDocumentoIdentidad().isBlank()) {
                camposFaltantes.add("tipoDocumentoIdentidad");
            }
            if (c.getNumeroDocumentoIdentidad() == null || c.getNumeroDocumentoIdentidad().isBlank()) {
                camposFaltantes.add("numeroDocumentoIdentidad");
            }
            if (c.getRazonSocialONombre() == null || c.getRazonSocialONombre().isBlank()) {
                camposFaltantes.add("razonSocialONombre");
            }
            if (c.getMontoTotal() == null) {
                camposFaltantes.add("montoTotal");
            }
            if (c.getMontoSubtotal() == null) {
                camposFaltantes.add("montoSubtotal");
            }
            if (c.getMontoIgv() == null) {
                camposFaltantes.add("montoIgv");
            }
            if (c.getMoneda() == null || c.getMoneda().isBlank()) {
                camposFaltantes.add("moneda");
            }

            if (!camposFaltantes.isEmpty()) {
                registrosIncompletos.add(Map.of(
                        "idComprobante", c.getIdComprobante() != null ? c.getIdComprobante() : 0L,
                        "idOrden", c.getIdOrden() != null ? c.getIdOrden() : 0L,
                        "camposFaltantes", camposFaltantes
                ));
            }

            String mon = (c.getMoneda() != null) ? c.getMoneda().trim().toUpperCase() : "";
            if ("USD".equals(mon)) {
                // Bloquear exportación si existen transacciones en USD sin tipo de cambio oficial verificado SBS/SUNAT
                registrosUsdSinTc.add(Map.of(
                        "idComprobante", c.getIdComprobante() != null ? c.getIdComprobante() : 0L,
                        "idOrden", c.getIdOrden() != null ? c.getIdOrden() : 0L,
                        "moneda", "USD",
                        "motivo", "Carece de tipo de cambio oficial de la fecha verificado por la SBS o SUNAT"
                ));
            }
        }

        if (!registrosIncompletos.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Registros fiscales incompletos",
                            "mensaje", "No es posible generar el libro SIRE: existen comprobantes con datos obligatorios incompletos o ausentes.",
                            "codigo", "COMPROBANTES_INCOMPLETOS",
                            "registrosIncompletos", registrosIncompletos
                    ));
        }

        if (!registrosUsdSinTc.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Transacciones en USD sin tipo de cambio oficial verificado",
                            "mensaje", "La exportación SIRE fue bloqueada porque existen transacciones en USD que no cuentan con tipo de cambio oficial verificado SBS/SUNAT.",
                            "codigo", "TIPO_CAMBIO_USD_NO_VERIFICADO",
                            "registrosSinTipoCambio", registrosUsdSinTc
                    ));
        }

        StringBuilder txtSire = new StringBuilder();

        for (ComprobantePago c : comprobantes) {
            String tipoCompCodigo = (c.getTipo() == TipoComprobante.FACTURA) ? "01" : "03";
            String tipoDocIdCodigo = "6"; // 6 = RUC
            if ("DNI".equalsIgnoreCase(c.getTipoDocumentoIdentidad())) {
                tipoDocIdCodigo = "1";
            }

            String fechaEmisionStr = c.getFechaEmision().format(FORMATO_FECHA_SUNAT);
            String estadoCodigo = (c.getEstado() == EstadoComprobante.ANULADO) ? "2" : "1";
            // Identificador CUO determinístico y persistente basado en el ID de comprobante registrado
            String cuoStr = String.format("CP%08d", c.getIdComprobante() != null ? c.getIdComprobante() : 0L);
            String moneda = c.getMoneda().trim().toUpperCase();

            // Estructura SUNAT SIRE RVIE Formato 14.1
            txtSire.append(RUC_EMISOR).append("|")
                    .append(RAZON_SOCIAL_EMISOR).append("|")
                    .append(periodoSire).append("|") // Periodo YYYYMM00
                    .append(cuoStr).append("|") // CUO determinístico
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
                    .append(moneda).append("|") // Moneda (PEN)
                    .append("1.000").append("|") // TC oficial para PEN
                    .append("|").append("|").append("|").append("|") // Ref notas crédito
                    .append(estadoCodigo).append("|") // 1=Vigente, 2=Anulado
                    .append(c.getCodigoHash() != null ? c.getCodigoHash() : "").append("|")
                    .append("\r\n");
        }

        String fileName = String.format("LE%s%s00140100001111.txt", RUC_EMISOR, ym.format(DateTimeFormatter.ofPattern("yyyyMM")));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(txtSire.toString());
    }

    public ResponseEntity<?> exportarTxtSire(String pinHeader) {
        return exportarTxtSire(pinHeader, null);
    }

    @PostMapping("/comprobantes/{idComprobante}/anular")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTABILIDAD')")
    @Transactional
    @Operation(summary = "Anular un comprobante de pago emitido por causa tributaria")
    public ResponseEntity<?> anularComprobante(
            @RequestHeader(value = "X-Audit-PIN", required = false) String pinHeader,
            @PathVariable Long idComprobante,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {

        if (!validarPin(pinHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Acceso denegado: PIN de auditoría no válido"));
        }

        String motivo = (body != null && body.containsKey("motivo")) ? body.get("motivo") : "Anulación solicitada por administración contable";
        try {
            ComprobantePago comprobante = billingPort.anularComprobante(idComprobante, motivo);
            accountingAuditPort.registrar(AuditoriaContable.builder()
                    .accion("COMPROBANTE_ANULADO")
                    .entidad("COMPROBANTE_PAGO")
                    .entidadId(idComprobante)
                    .actor(authentication != null ? authentication.getName() : "desconocido")
                    .motivo(motivo)
                    .fechaHora(java.time.OffsetDateTime.now())
                    .build());
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

    private boolean validarPin(String pinHeader) {
        if (pinHeader == null || pinHeader.isBlank() || auditPin == null || auditPin.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                pinHeader.trim().getBytes(StandardCharsets.UTF_8),
                auditPin.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}

package com.lysandri.adapters.out.billing;

import com.lysandri.adapters.out.database.entity.ComprobantePagoEntity;
import com.lysandri.adapters.out.database.repository.SpringDataComprobantePagoRepository;
import com.lysandri.domain.model.ComprobantePago;
import com.lysandri.domain.model.EstadoComprobante;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.TipoComprobante;
import com.lysandri.domain.ports.out.BillingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingServiceAdapter implements BillingPort {

    private final SpringDataComprobantePagoRepository repository;
    private static final BigDecimal DIVISOR_IGV = new BigDecimal("1.18");

    @Override
    @Transactional
    public ComprobantePago emitirComprobante(Orden orden) {
        log.info("Iniciando emisión de comprobante tributario para orden ID: {}", orden.getIdOrden());

        // Garantizar idempotencia si ya fue emitido
        Optional<ComprobantePagoEntity> existente = repository.findByIdOrden(orden.getIdOrden());
        if (existente.isPresent()) {
            log.info("Comprobante ya emitido previamente para orden ID: {} ({}-{})",
                    orden.getIdOrden(), existente.get().getSerie(), existente.get().getCorrelativo());
            return toDomain(existente.get());
        }

        // 1. Determinar tipo de comprobante y serie tributaria
        TipoComprobante tipo = TipoComprobante.BOLETA;
        if ("FACTURA".equalsIgnoreCase(orden.getTipoComprobanteSolicitado())) {
            tipo = TipoComprobante.FACTURA;
        }

        String serie = (tipo == TipoComprobante.FACTURA) ? "F001" : "B001";

        // 2. Generar correlativo secuencial único
        int siguienteCorrelativo = repository.findMaxCorrelativoBySerie(serie) + 1;

        // 3. Determinar documento de identidad y razón social / nombre
        String docCliente = orden.getNumeroDocumentoCliente();
        String tipoDoc = (tipo == TipoComprobante.FACTURA) ? "RUC" : "DNI";
        if (docCliente != null && docCliente.trim().length() == 11) {
            tipoDoc = "RUC";
        } else if (docCliente != null && docCliente.trim().length() == 8) {
            tipoDoc = "DNI";
        }

        if (docCliente == null || docCliente.isBlank()) {
            docCliente = (tipo == TipoComprobante.FACTURA) ? "20000000001" : "00000000";
        }

        String nombreCliente = orden.getNombreFacturacion();
        if (nombreCliente == null || nombreCliente.isBlank()) {
            if (orden.getUsuario() != null && orden.getUsuario().getNombreCompleto() != null) {
                nombreCliente = orden.getUsuario().getNombreCompleto();
            } else {
                nombreCliente = "Cliente Lysandri Executive";
            }
        }

        // 4. Cálculo de Base Imponible e IGV (18% incluido según normativa SUNAT)
        BigDecimal montoTotal = orden.getTotal() != null ? orden.getTotal() : BigDecimal.ZERO;
        BigDecimal montoSubtotal = montoTotal.divide(DIVISOR_IGV, 2, RoundingMode.HALF_UP);
        BigDecimal montoIgv = montoTotal.subtract(montoSubtotal);

        // 5. Cálculo SPOT Detracciones Banco de la Nación (Servicios de Capacitación y Asesoría > S/. 700.00 - 12%)
        BigDecimal montoDetraccion = BigDecimal.ZERO;
        BigDecimal porcentajeDetraccion = BigDecimal.ZERO;
        if (tipo == TipoComprobante.FACTURA && montoTotal.compareTo(new BigDecimal("700.00")) > 0) {
            porcentajeDetraccion = new BigDecimal("12.00");
            montoDetraccion = montoTotal.multiply(new BigDecimal("0.12")).setScale(2, RoundingMode.HALF_UP);
        }

        // 6. Generación de Hash Digital SUNAT (DigestValue simulado SHA-256)
        String rawDataToHash = String.format("20609812451|%s|%s|%08d|%s|%s|%s",
                tipoDoc, serie, siguienteCorrelativo, montoIgv, montoTotal, docCliente, LocalDateTime.now());
        String codigoHash = generarHashSha256(rawDataToHash);
        String medioPago = orden.getMetodoPago() != null ? orden.getMetodoPago() : "STRIPE_CHECKOUT";

        String pdfUrl = String.format("https://lysandri.com/comprobantes/%s-%08d.pdf", serie, siguienteCorrelativo);

        ComprobantePagoEntity entity = ComprobantePagoEntity.builder()
                .idOrden(orden.getIdOrden())
                .tipo(tipo)
                .serie(serie)
                .correlativo(siguienteCorrelativo)
                .tipoDocumentoIdentidad(tipoDoc)
                .numeroDocumentoIdentidad(docCliente.trim())
                .razonSocialONombre(nombreCliente.trim())
                .montoSubtotal(montoSubtotal)
                .montoIgv(montoIgv)
                .montoTotal(montoTotal)
                .moneda(orden.getMoneda() != null ? orden.getMoneda() : "PEN")
                .fechaEmision(LocalDateTime.now())
                .estado(EstadoComprobante.EMITIDO)
                .pdfUrl(pdfUrl)
                .codigoHash(codigoHash)
                .montoDetraccion(montoDetraccion)
                .porcentajeDetraccion(porcentajeDetraccion)
                .medioPago(medioPago)
                .build();

        ComprobantePagoEntity guardado = repository.save(entity);
        log.info("Comprobante emitido con éxito: Serie={}, Correlativo={}, Total={}, Detracción={}, Cliente={}",
                guardado.getSerie(), guardado.getCorrelativo(), guardado.getMontoTotal(), guardado.getMontoDetraccion(), guardado.getRazonSocialONombre());

        return toDomain(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComprobantePago> listarComprobantes() {
        return repository.findAllByOrderByFechaEmisionDesc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComprobantePago> buscarPorOrdenId(Long idOrden) {
        return repository.findByIdOrden(idOrden).map(this::toDomain);
    }

    @Override
    @Transactional
    public ComprobantePago anularComprobante(Long idComprobante, String motivo) {
        ComprobantePagoEntity entity = repository.findById(idComprobante)
                .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado con ID: " + idComprobante));

        entity.setEstado(EstadoComprobante.ANULADO);
        entity.setMotivoAnulacion(motivo != null ? motivo : "Anulación por error en emisión / emisión de Nota de Crédito");
        entity.setFechaAnulacion(LocalDateTime.now());

        ComprobantePagoEntity actualizado = repository.save(entity);
        log.warn("Comprobante anulado: Serie={}, Correlativo={}, Motivo={}",
                actualizado.getSerie(), actualizado.getCorrelativo(), actualizado.getMotivoAnulacion());
        return toDomain(actualizado);
    }

    private String generarHashSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash).substring(0, 28) + "=";
        } catch (Exception e) {
            return "HSh9821KxJ2y1aXm09B=";
        }
    }

    private ComprobantePago toDomain(ComprobantePagoEntity entity) {
        return ComprobantePago.builder()
                .idComprobante(entity.getIdComprobante())
                .idOrden(entity.getIdOrden())
                .tipo(entity.getTipo())
                .serie(entity.getSerie())
                .correlativo(entity.getCorrelativo())
                .tipoDocumentoIdentidad(entity.getTipoDocumentoIdentidad())
                .numeroDocumentoIdentidad(entity.getNumeroDocumentoIdentidad())
                .razonSocialONombre(entity.getRazonSocialONombre())
                .montoSubtotal(entity.getMontoSubtotal())
                .montoIgv(entity.getMontoIgv())
                .montoTotal(entity.getMontoTotal())
                .moneda(entity.getMoneda())
                .fechaEmision(entity.getFechaEmision())
                .estado(entity.getEstado())
                .pdfUrl(entity.getPdfUrl())
                .codigoHash(entity.getCodigoHash())
                .montoDetraccion(entity.getMontoDetraccion())
                .porcentajeDetraccion(entity.getPorcentajeDetraccion())
                .medioPago(entity.getMedioPago())
                .motivoAnulacion(entity.getMotivoAnulacion())
                .fechaAnulacion(entity.getFechaAnulacion())
                .build();
    }
}

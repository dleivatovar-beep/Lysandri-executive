package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ReporteVentasResponse;
import com.lysandri.domain.model.*;
import com.lysandri.domain.ports.out.BillingPort;
import com.lysandri.domain.ports.out.AccountingAuditPort;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAccountingControllerTest {

    @Mock
    private BillingPort billingPort;

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private AccountingAuditPort accountingAuditPort;

    @InjectMocks
    private InternalAccountingController accountingController;

    private final String VALID_PIN = "LYS-AUDIT-2026-SECURE";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(accountingController, "auditPin", VALID_PIN);
    }

    @Test
    void obtenerReporteVentas_sinPin_debeRetornarUnauthorized() {
        ResponseEntity<?> response = accountingController.obtenerReporteVentas(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("AUDIT_PIN_INVALID", body.get("codigo"));
    }

    @Test
    void obtenerReporteVentas_pinInvalido_debeRetornarUnauthorized() {
        ResponseEntity<?> response = accountingController.obtenerReporteVentas("PIN-ERRONEO");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void obtenerReporteVentas_periodoInvalido_debeRetornarBadRequest() {
        ResponseEntity<?> response = accountingController.obtenerReporteVentas(VALID_PIN, "2026-13");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("PERIODO_INVALIDO", body.get("codigo"));
    }

    @Test
    void obtenerReporteVentas_pinValido_debeRetornarReporteConsolidado() {
        ComprobantePago boleta = ComprobantePago.builder()
                .idComprobante(501L)
                .idOrden(100L)
                .tipo(TipoComprobante.BOLETA)
                .serie("B001")
                .correlativo(1)
                .montoSubtotal(new BigDecimal("100.00"))
                .montoIgv(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("118.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 10, 5, 10, 0))
                .estado(EstadoComprobante.EMITIDO)
                .build();

        ComprobantePago factura = ComprobantePago.builder()
                .idComprobante(502L)
                .idOrden(101L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(1)
                .montoSubtotal(new BigDecimal("200.00"))
                .montoIgv(new BigDecimal("36.00"))
                .montoTotal(new BigDecimal("236.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 10, 15, 15, 30))
                .estado(EstadoComprobante.EMITIDO)
                .build();

        Orden orden1 = Orden.builder()
                .idOrden(100L)
                .codigoOrden("ORD-100")
                .total(new BigDecimal("118.00"))
                .moneda("PEN")
                .estadoOrden(EstadoOrden.PAGADO)
                .fechaOrden(OffsetDateTime.of(2026, 10, 5, 9, 50, 0, 0, ZoneOffset.UTC))
                .nombreFacturacion("Juan Pérez")
                .numeroDocumentoCliente("45678901")
                .tipoComprobanteSolicitado("BOLETA")
                .build();

        Orden orden2 = Orden.builder()
                .idOrden(101L)
                .codigoOrden("ORD-101")
                .total(new BigDecimal("236.00"))
                .moneda("PEN")
                .estadoOrden(EstadoOrden.PAGADO)
                .fechaOrden(OffsetDateTime.of(2026, 10, 15, 15, 0, 0, 0, ZoneOffset.UTC))
                .nombreFacturacion("Inversiones SAC")
                .numeroDocumentoCliente("20555666777")
                .tipoComprobanteSolicitado("FACTURA")
                .build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(boleta, factura));
        when(orderRepository.listarTodas()).thenReturn(List.of(orden1, orden2));

        ResponseEntity<?> response = accountingController.obtenerReporteVentas(VALID_PIN, "2026-10");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ReporteVentasResponse);

        ReporteVentasResponse reporte = (ReporteVentasResponse) response.getBody();
        assertEquals(new BigDecimal("354.00"), reporte.getTotalVentasPen());
        assertEquals(new BigDecimal("300.00"), reporte.getTotalBaseImponiblePen());
        assertEquals(new BigDecimal("54.00"), reporte.getTotalIgvRecaudadoPen());
        assertEquals(2, reporte.getCantidadComprobantes());
        assertEquals(1, reporte.getCantidadBoletas());
        assertEquals(1, reporte.getCantidadFacturas());
        assertEquals(2, reporte.getVentasConsolidadas().size());

        // Comprobante ID real diferenciado del idOrden
        ReporteVentasResponse.VentaConsolidadaDto dto1 = reporte.getVentasConsolidadas().get(0);
        assertEquals(100L, dto1.getIdOrden());
        assertEquals(501L, dto1.getIdComprobante());
        assertNotEquals(dto1.getIdOrden(), dto1.getIdComprobante());

        assertNotNull(reporte.getLiquidacionTributaria());
        assertEquals(new BigDecimal("300.00"), reporte.getLiquidacionTributaria().getVentasNetasGravadas());
        assertEquals(new BigDecimal("54.00"), reporte.getLiquidacionTributaria().getDebitoFiscalIgv());
    }

    @Test
    void obtenerReporteVentas_filtraPorPeriodoYNoSumaMonedasDiferentes() {
        // Comprobante 1: Octubre 2026 en PEN
        ComprobantePago compPenOct = ComprobantePago.builder()
                .idComprobante(101L)
                .idOrden(1L)
                .tipo(TipoComprobante.BOLETA)
                .serie("B001")
                .correlativo(10)
                .montoSubtotal(new BigDecimal("100.00"))
                .montoIgv(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("118.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 10, 10, 10, 0))
                .estado(EstadoComprobante.EMITIDO)
                .build();

        // Comprobante 2: Octubre 2026 en USD (NO debe sumarse a PEN)
        ComprobantePago compUsdOct = ComprobantePago.builder()
                .idComprobante(102L)
                .idOrden(2L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(20)
                .montoSubtotal(new BigDecimal("500.00"))
                .montoIgv(new BigDecimal("0.00"))
                .montoTotal(new BigDecimal("500.00"))
                .moneda("USD")
                .fechaEmision(LocalDateTime.of(2026, 10, 20, 12, 0))
                .estado(EstadoComprobante.EMITIDO)
                .build();

        // Comprobante 3: Noviembre 2026 (FUERA del período 2026-10)
        ComprobantePago compNov = ComprobantePago.builder()
                .idComprobante(103L)
                .idOrden(3L)
                .tipo(TipoComprobante.BOLETA)
                .serie("B001")
                .correlativo(11)
                .montoSubtotal(new BigDecimal("200.00"))
                .montoIgv(new BigDecimal("36.00"))
                .montoTotal(new BigDecimal("236.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 11, 1, 9, 0))
                .estado(EstadoComprobante.EMITIDO)
                .build();

        Orden o1 = Orden.builder().idOrden(1L).codigoOrden("ORD-1").moneda("PEN").total(new BigDecimal("118.00")).fechaOrden(OffsetDateTime.of(2026, 10, 10, 9, 0, 0, 0, ZoneOffset.UTC)).build();
        Orden o2 = Orden.builder().idOrden(2L).codigoOrden("ORD-2").moneda("USD").total(new BigDecimal("500.00")).fechaOrden(OffsetDateTime.of(2026, 10, 20, 11, 0, 0, 0, ZoneOffset.UTC)).build();
        Orden o3 = Orden.builder().idOrden(3L).codigoOrden("ORD-3").moneda("PEN").total(new BigDecimal("236.00")).fechaOrden(OffsetDateTime.of(2026, 11, 1, 8, 0, 0, 0, ZoneOffset.UTC)).build();

        // Orden 4: Octubre 2026 SIN comprobante emitido (pendiente)
        Orden o4 = Orden.builder().idOrden(4L).codigoOrden("ORD-4-PEND").moneda("PEN").total(new BigDecimal("150.00")).fechaOrden(OffsetDateTime.of(2026, 10, 25, 14, 0, 0, 0, ZoneOffset.UTC)).build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(compPenOct, compUsdOct, compNov));
        when(orderRepository.listarTodas()).thenReturn(List.of(o1, o2, o3, o4));

        ResponseEntity<?> response = accountingController.obtenerReporteVentas(VALID_PIN, "2026-10");
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ReporteVentasResponse reporte = (ReporteVentasResponse) response.getBody();
        assertNotNull(reporte);

        // PEN y USD deben permanecer estrictamente separados
        assertEquals(new BigDecimal("118.00"), reporte.getTotalVentasPen(), "Total PEN no debe mezclarse con USD");
        assertEquals(new BigDecimal("500.00"), reporte.getTotalVentasUsd(), "Total USD debe reportarse por separado");

        // Solo 2 comprobantes en octubre 2026 (o3 de noviembre debe quedar excluido)
        assertEquals(2, reporte.getCantidadComprobantes());

        // Órdenes en el período: o1, o2 y o4 (o3 queda fuera)
        assertEquals(3, reporte.getVentasConsolidadas().size());

        // o4 no tiene comprobante emitido
        assertEquals(1, reporte.getCantidadPendientesSinComprobante());
        ReporteVentasResponse.VentaConsolidadaDto dtoPend = reporte.getVentasConsolidadas().stream()
                .filter(v -> v.getIdOrden().equals(4L))
                .findFirst()
                .orElseThrow();
        assertNull(dtoPend.getIdComprobante(), "Una orden sin comprobante emitido debe tener idComprobante nulo");
        assertEquals("PENDIENTE", dtoPend.getEstadoComprobante());
    }

    @Test
    void reporteVentas_pinNoConfigurado_deniegaAcceso() {
        ReflectionTestUtils.setField(accountingController, "auditPin", " ");

        ResponseEntity<?> response = accountingController.obtenerReporteVentas(VALID_PIN);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(billingPort, orderRepository);
    }

    @Test
    void exportarTxtSire_sinPeriodo_debeRetornarBadRequest() {
        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("PERIODO_REQUERIDO_SIRE", body.get("codigo"));
    }

    @Test
    void exportarTxtSire_periodoTodos_debeRetornarBadRequest() {
        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "TODOS");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("PERIODO_REQUERIDO_SIRE", body.get("codigo"));
    }

    @Test
    void exportarTxtSire_periodoInvalido_debeRetornarBadRequest() {
        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "2026-99");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("PERIODO_INVALIDO", body.get("codigo"));
    }

    @Test
    void exportarTxtSire_sinComprobantesEnPeriodo_debeRetornarUnprocessableEntity() {
        when(billingPort.listarComprobantes()).thenReturn(List.of());

        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "2026-10");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("SIN_COMPROBANTES_PERIODO", body.get("codigo"));
    }

    @Test
    void exportarTxtSire_comprobanteIncompleto_debeRetornarUnprocessableEntityConIdentificadores() {
        // Comprobante sin serie, sin correlativo y sin documento de identidad
        ComprobantePago incompleto = ComprobantePago.builder()
                .idComprobante(777L)
                .idOrden(888L)
                .tipo(TipoComprobante.FACTURA)
                .serie(null) // Falta serie
                .correlativo(null) // Falta correlativo
                .tipoDocumentoIdentidad("RUC")
                .numeroDocumentoIdentidad(null) // Falta número documento
                .razonSocialONombre("Empresa Incompleta SAC")
                .montoSubtotal(new BigDecimal("100.00"))
                .montoIgv(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("118.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 10, 8, 11, 0))
                .estado(EstadoComprobante.EMITIDO)
                .build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(incompleto));

        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "2026-10");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("COMPROBANTES_INCOMPLETOS", body.get("codigo"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> incompletos = (List<Map<String, Object>>) body.get("registrosIncompletos");
        assertNotNull(incompletos);
        assertEquals(1, incompletos.size());
        assertEquals(777L, incompletos.get(0).get("idComprobante"));
        assertEquals(888L, incompletos.get(0).get("idOrden"));

        @SuppressWarnings("unchecked")
        List<String> faltantes = (List<String>) incompletos.get(0).get("camposFaltantes");
        assertTrue(faltantes.contains("serie"));
        assertTrue(faltantes.contains("correlativo"));
        assertTrue(faltantes.contains("numeroDocumentoIdentidad"));
    }

    @Test
    void exportarTxtSire_transaccionUsdSinTipoCambioVerificado_debeBloquearExportacion() {
        ComprobantePago compUsd = ComprobantePago.builder()
                .idComprobante(401L)
                .idOrden(301L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(12)
                .tipoDocumentoIdentidad("RUC")
                .numeroDocumentoIdentidad("20100047218")
                .razonSocialONombre("Global Tech Corp")
                .montoSubtotal(new BigDecimal("1000.00"))
                .montoIgv(new BigDecimal("180.00"))
                .montoTotal(new BigDecimal("1180.00"))
                .moneda("USD") // Moneda USD sin tipo de cambio oficial
                .fechaEmision(LocalDateTime.of(2026, 10, 12, 10, 0))
                .estado(EstadoComprobante.EMITIDO)
                .build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(compUsd));

        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "2026-10");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("TIPO_CAMBIO_USD_NO_VERIFICADO", body.get("codigo"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sinTc = (List<Map<String, Object>>) body.get("registrosSinTipoCambio");
        assertNotNull(sinTc);
        assertEquals(1, sinTc.size());
        assertEquals(401L, sinTc.get(0).get("idComprobante"));
        assertEquals(301L, sinTc.get(0).get("idOrden"));
        assertEquals("USD", sinTc.get(0).get("moneda"));
    }

    @Test
    void exportarTxtSire_pinValido_debeGenerarFormatoSunatPipeConPeriodoDinamico() {
        ComprobantePago factura = ComprobantePago.builder()
                .idComprobante(2L)
                .idOrden(101L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(1)
                .tipoDocumentoIdentidad("RUC")
                .numeroDocumentoIdentidad("20555666777")
                .razonSocialONombre("Inversiones SAC")
                .montoSubtotal(new BigDecimal("200.00"))
                .montoIgv(new BigDecimal("36.00"))
                .montoTotal(new BigDecimal("236.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 10, 4, 12, 0))
                .estado(EstadoComprobante.EMITIDO)
                .codigoHash("A89B7F12C==")
                .build();

        ComprobantePago facturaAnulada = ComprobantePago.builder()
                .idComprobante(3L)
                .idOrden(102L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(2)
                .tipoDocumentoIdentidad("RUC")
                .numeroDocumentoIdentidad("20111222333")
                .razonSocialONombre("Anulada SAC")
                .montoSubtotal(new BigDecimal("100.00"))
                .montoIgv(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("118.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 10, 6, 14, 0))
                .estado(EstadoComprobante.ANULADO)
                .motivoAnulacion("Error de facturación")
                .build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(factura, facturaAnulada));

        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "2026-10");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof String);
        String txtSire = (String) response.getBody();

        // CUO determinístico y persistente CP00000002
        assertTrue(txtSire.contains("20609812451|LYSANDRI|20261000|CP00000002|"));
        assertTrue(txtSire.contains("|01|F001|00000001|"));
        assertTrue(txtSire.contains("|20555666777|Inversiones SAC|"));
        assertTrue(txtSire.contains("|200.00|0.00|36.00|"));
        assertTrue(txtSire.contains("|1|A89B7F12C==|")); // Vigente (estado 1)

        // Comprobante anulado: CUO CP00000003, importes 0.00, estado 2
        assertTrue(txtSire.contains("20609812451|LYSANDRI|20261000|CP00000003|"));
        assertTrue(txtSire.contains("|01|F001|00000002|"));
        assertTrue(txtSire.contains("|0.00|0.00|0.00|"));
        assertTrue(txtSire.contains("|2||")); // Anulado (estado 2)

        String disposition = response.getHeaders().getFirst("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("LE2060981245120261000140100001111.txt"));
    }

    @Test
    void anularComprobante_exitosoConIdComprobanteDiferenteAIdOrden() {
        ComprobantePago anulado = ComprobantePago.builder()
                .idComprobante(789L)
                .idOrden(456L)
                .serie("F001")
                .correlativo(5)
                .estado(EstadoComprobante.ANULADO)
                .motivoAnulacion("Error en RUC")
                .build();

        // Se envía idComprobante 789L (diferente a idOrden 456L)
        when(billingPort.anularComprobante(789L, "Error en RUC")).thenReturn(anulado);

        Authentication accountant = new UsernamePasswordAuthenticationToken(
                "accountant@lysandri.com", null, List.of(new SimpleGrantedAuthority("ROLE_CONTABILIDAD")));
        ResponseEntity<?> response = accountingController.anularComprobante(
                VALID_PIN, 789L, Map.of("motivo", "Error en RUC"), accountant);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("ANULADO", body.get("estado"));
        assertEquals(789L, body.get("idComprobante"));
        assertEquals("Error en RUC", body.get("motivoAnulacion"));
        verify(accountingAuditPort).registrar(argThat(audit ->
                audit.getAccion().equals("COMPROBANTE_ANULADO")
                        && audit.getActor().equals("accountant@lysandri.com")
                        && audit.getEntidadId().equals(789L)));
    }

    @Test
    void anularComprobante_inexistente_retornaNotFound() {
        when(billingPort.anularComprobante(999L, "Error en RUC"))
                .thenThrow(new IllegalArgumentException("Comprobante con ID 999 no encontrado"));

        Authentication accountant = new UsernamePasswordAuthenticationToken(
                "accountant@lysandri.com", null, List.of(new SimpleGrantedAuthority("ROLE_CONTABILIDAD")));
        ResponseEntity<?> response = accountingController.anularComprobante(
                VALID_PIN, 999L, Map.of("motivo", "Error en RUC"), accountant);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertTrue(((String) body.get("error")).contains("999 no encontrado"));
    }

    @Test
    void exportarTxtSire_comprobanteEmitidoSinFecha_juntoAComprobanteValido_debeRetornarUnprocessableEntity() {
        ComprobantePago validoOctubre = ComprobantePago.builder()
                .idComprobante(101L)
                .idOrden(201L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(1)
                .tipoDocumentoIdentidad("RUC")
                .numeroDocumentoIdentidad("20601234567")
                .razonSocialONombre("Empresa Valida SAC")
                .montoSubtotal(new BigDecimal("100.00"))
                .montoIgv(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("118.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 10, 15, 10, 0))
                .estado(EstadoComprobante.EMITIDO)
                .build();

        ComprobantePago emitidoSinFecha = ComprobantePago.builder()
                .idComprobante(102L)
                .idOrden(202L)
                .tipo(TipoComprobante.BOLETA)
                .serie("B001")
                .correlativo(2)
                .tipoDocumentoIdentidad("DNI")
                .numeroDocumentoIdentidad("44556677")
                .razonSocialONombre("Juan Perez")
                .montoSubtotal(new BigDecimal("50.00"))
                .montoIgv(new BigDecimal("9.00"))
                .montoTotal(new BigDecimal("59.00"))
                .moneda("PEN")
                .fechaEmision(null) // Fecha obligatoria ausente
                .estado(EstadoComprobante.EMITIDO)
                .build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(validoOctubre, emitidoSinFecha));

        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "2026-10");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("COMPROBANTES_INCOMPLETOS", body.get("codigo"));
        assertTrue(body.get("registrosIncompletos") instanceof List);
        List<?> registros = (List<?>) body.get("registrosIncompletos");
        assertEquals(1, registros.size());
        Map<?, ?> reg = (Map<?, ?>) registros.get(0);
        assertEquals(102L, reg.get("idComprobante"));
        assertEquals(202L, reg.get("idOrden"));
        List<?> faltantes = (List<?>) reg.get("camposFaltantes");
        assertTrue(faltantes.contains("fechaEmision"));
    }

    @Test
    void exportarTxtSire_comprobanteAnuladoSinFecha_debeRetornarUnprocessableEntity() {
        ComprobantePago anuladoSinFecha = ComprobantePago.builder()
                .idComprobante(103L)
                .idOrden(203L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(3)
                .tipoDocumentoIdentidad("RUC")
                .numeroDocumentoIdentidad("20609876543")
                .razonSocialONombre("Empresa Anulada SAC")
                .montoSubtotal(new BigDecimal("200.00"))
                .montoIgv(new BigDecimal("36.00"))
                .montoTotal(new BigDecimal("236.00"))
                .moneda("PEN")
                .fechaEmision(null) // Fecha obligatoria ausente
                .estado(EstadoComprobante.ANULADO)
                .motivoAnulacion("Anulado por error de emisión")
                .build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(anuladoSinFecha));

        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "2026-10");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("COMPROBANTES_INCOMPLETOS", body.get("codigo"));
        List<?> registros = (List<?>) body.get("registrosIncompletos");
        assertEquals(1, registros.size());
        Map<?, ?> reg = (Map<?, ?>) registros.get(0);
        assertEquals(103L, reg.get("idComprobante"));
        assertEquals("ANULADO", reg.get("estado"));
    }

    @Test
    void exportarTxtSire_comprobantesCompletosDeOtrosPeriodos_noBloqueanPeriodoActual() {
        ComprobantePago setiembreCompleto = ComprobantePago.builder()
                .idComprobante(104L)
                .idOrden(204L)
                .tipo(TipoComprobante.BOLETA)
                .serie("B001")
                .correlativo(10)
                .tipoDocumentoIdentidad("DNI")
                .numeroDocumentoIdentidad("77889900")
                .razonSocialONombre("Cliente Setiembre")
                .montoSubtotal(new BigDecimal("100.00"))
                .montoIgv(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("118.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 9, 20, 11, 0)) // Setiembre
                .estado(EstadoComprobante.EMITIDO)
                .build();

        ComprobantePago octubreCompleto = ComprobantePago.builder()
                .idComprobante(105L)
                .idOrden(205L)
                .tipo(TipoComprobante.BOLETA)
                .serie("B001")
                .correlativo(11)
                .tipoDocumentoIdentidad("DNI")
                .numeroDocumentoIdentidad("11223344")
                .razonSocialONombre("Cliente Octubre")
                .montoSubtotal(new BigDecimal("150.00"))
                .montoIgv(new BigDecimal("27.00"))
                .montoTotal(new BigDecimal("177.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.of(2026, 10, 10, 9, 30)) // Octubre
                .estado(EstadoComprobante.EMITIDO)
                .build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(setiembreCompleto, octubreCompleto));

        ResponseEntity<?> response = accountingController.exportarTxtSire(VALID_PIN, "2026-10");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof String);
        String txt = (String) response.getBody();
        assertTrue(txt.contains("CP00000105"));
        assertFalse(txt.contains("CP00000104")); // No incluye el de setiembre
    }
}

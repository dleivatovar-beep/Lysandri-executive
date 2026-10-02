package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ReporteVentasResponse;
import com.lysandri.domain.model.*;
import com.lysandri.domain.ports.out.BillingPort;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalAccountingControllerTest {

    @Mock
    private BillingPort billingPort;

    @Mock
    private OrderRepositoryPort orderRepository;

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
    void obtenerReporteVentas_pinValido_debeRetornarReporteConsolidado() {
        ComprobantePago boleta = ComprobantePago.builder()
                .idComprobante(1L)
                .idOrden(100L)
                .tipo(TipoComprobante.BOLETA)
                .serie("B001")
                .correlativo(1)
                .montoSubtotal(new BigDecimal("100.00"))
                .montoIgv(new BigDecimal("18.00"))
                .montoTotal(new BigDecimal("118.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.now())
                .estado(EstadoComprobante.EMITIDO)
                .build();

        ComprobantePago factura = ComprobantePago.builder()
                .idComprobante(2L)
                .idOrden(101L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(1)
                .montoSubtotal(new BigDecimal("200.00"))
                .montoIgv(new BigDecimal("36.00"))
                .montoTotal(new BigDecimal("236.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.now())
                .estado(EstadoComprobante.EMITIDO)
                .build();

        Orden orden1 = Orden.builder()
                .idOrden(100L)
                .codigoOrden("ORD-100")
                .total(new BigDecimal("118.00"))
                .moneda("PEN")
                .estadoOrden(EstadoOrden.PAGADO)
                .fechaOrden(OffsetDateTime.now())
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
                .fechaOrden(OffsetDateTime.now())
                .nombreFacturacion("Inversiones SAC")
                .numeroDocumentoCliente("20555666777")
                .tipoComprobanteSolicitado("FACTURA")
                .build();

        when(billingPort.listarComprobantes()).thenReturn(List.of(boleta, factura));
        when(orderRepository.listarTodas()).thenReturn(List.of(orden1, orden2));

        ResponseEntity<?> response = accountingController.obtenerReporteVentas(VALID_PIN);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ReporteVentasResponse);

        ReporteVentasResponse reporte = (ReporteVentasResponse) response.getBody();
        assertEquals(new BigDecimal("354.00"), reporte.getTotalVentas());
        assertEquals(new BigDecimal("300.00"), reporte.getTotalBaseImponible());
        assertEquals(new BigDecimal("54.00"), reporte.getTotalIgvRecaudado());
        assertEquals(2, reporte.getCantidadComprobantes());
        assertEquals(1, reporte.getCantidadBoletas());
        assertEquals(1, reporte.getCantidadFacturas());
        assertEquals(2, reporte.getVentasConsolidadas().size());
        assertEquals(2, reporte.getComprobantesEmitidos().size());
    }
}

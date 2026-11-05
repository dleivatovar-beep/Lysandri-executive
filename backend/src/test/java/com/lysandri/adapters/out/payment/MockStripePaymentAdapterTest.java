package com.lysandri.adapters.out.payment;

import com.lysandri.domain.model.DetalleOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.out.PaymentPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockStripePaymentAdapterTest {

    @Mock
    private Environment environment;

    private Orden crearOrdenPrueba() {
        return Orden.builder()
                .idOrden(200L)
                .codigoOrden("ORD-TEST-200")
                .moneda("USD")
                .total(new BigDecimal("199.00"))
                .items(List.of(
                        DetalleOrden.builder()
                                .idDetalle(1L)
                                .cantidad(1)
                                .precioUnitario(new BigDecimal("199.00"))
                                .programa(Programa.builder().idPrograma(20L).titulo("Finanzas").build())
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("MockStripePaymentAdapter lanza excepción si se intenta inicializar bajo perfil 'prod'")
    void validarSeguridadEntorno_enProduccion_lanzaIllegalStateException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        MockStripePaymentAdapter adapter = new MockStripePaymentAdapter(
                "http://localhost:3000/checkout/success",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::validarSeguridadEntorno);
        assertTrue(ex.getMessage().contains("terminantemente prohibido en entornos de PRODUCCIÓN"));
    }

    @Test
    @DisplayName("MockStripePaymentAdapter lanza excepción si se intenta inicializar bajo perfil 'production'")
    void validarSeguridadEntorno_enProduction_lanzaIllegalStateException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});

        MockStripePaymentAdapter adapter = new MockStripePaymentAdapter(
                "http://localhost:3000/checkout/success",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::validarSeguridadEntorno);
        assertTrue(ex.getMessage().contains("terminantemente prohibido en entornos de PRODUCCIÓN"));
    }

    @Test
    @DisplayName("MockStripePaymentAdapter genera sesión simulada correctamente bajo perfil 'test'")
    void crearSesionPago_enPerfilTest_generaTransaccionesSimuladas() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

        MockStripePaymentAdapter adapter = new MockStripePaymentAdapter(
                "http://localhost:3000/checkout/success?session_id={CHECKOUT_SESSION_ID}",
                environment
        );
        adapter.validarSeguridadEntorno();

        PaymentPort.PaymentSessionResult result = adapter.crearSesionPago(crearOrdenPrueba(), "alumno@test.com", "Alumno");

        assertNotNull(result);
        assertTrue(result.sessionId().startsWith("cs_test_"));
        assertTrue(result.checkoutUrl().contains(result.sessionId()));
        assertTrue(result.paymentIntentId().startsWith("pi_test_"));

        assertTrue(adapter.verificarPagoCompletado(result.sessionId()));
        assertTrue(adapter.verificarPagoCompletado(result.paymentIntentId()));
    }

    @Test
    @DisplayName("verificarPagoCompletado con id desconocido retorna false en MockStripePaymentAdapter")
    void verificarPagoCompletado_idDesconocido_retornaFalse() {
        MockStripePaymentAdapter adapter = new MockStripePaymentAdapter(
                "http://localhost:3000/checkout/success",
                environment
        );

        assertFalse(adapter.verificarPagoCompletado("cs_unknown_999"));
        assertFalse(adapter.verificarPagoCompletado(null));
        assertFalse(adapter.verificarPagoCompletado("   "));
    }

    @Test
    @DisplayName("registrarEstadoTransaccionTest permite simular pagos fallidos bajo demanda")
    void registrarEstadoTransaccionTest_permiteSimularFallo() {
        MockStripePaymentAdapter adapter = new MockStripePaymentAdapter(
                "http://localhost:3000/checkout/success",
                environment
        );

        adapter.registrarEstadoTransaccionTest("cs_failed_123", false);
        assertFalse(adapter.verificarPagoCompletado("cs_failed_123"));

        adapter.registrarEstadoTransaccionTest("cs_failed_123", true);
        assertTrue(adapter.verificarPagoCompletado("cs_failed_123"));
    }
}

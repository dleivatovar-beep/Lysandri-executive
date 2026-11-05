package com.lysandri.adapters.out.payment;

import com.lysandri.domain.model.DetalleOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.Programa;
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
class StripePaymentAdapterTest {

    @Mock
    private Environment environment;

    private Orden crearOrdenPrueba() {
        return Orden.builder()
                .idOrden(100L)
                .codigoOrden("ORD-TEST-100")
                .moneda("USD")
                .total(new BigDecimal("299.00"))
                .items(List.of(
                        DetalleOrden.builder()
                                .idDetalle(1L)
                                .cantidad(1)
                                .precioUnitario(new BigDecimal("299.00"))
                                .programa(Programa.builder().idPrograma(10L).titulo("Programa Liderazgo").build())
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("validarConfiguracionSegura en perfil 'prod' con clave mock falla en el inicio de la app")
    void validarConfiguracionSegura_enProduccionConMockKey_lanzaIllegalStateException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        StripePaymentAdapter adapter = new StripePaymentAdapter(
                "sk_test_mock",
                "https://lysandri.com/checkout/success",
                "https://lysandri.com/checkout/cancel",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, adapter::validarConfiguracionSegura);
        assertTrue(ex.getMessage().contains("No se permite operar en entorno de PRODUCCIÓN sin credenciales válidas"));
    }

    @Test
    @DisplayName("En entorno de producción ('prod'), prohíbe terminantemente crear sesión con mock key")
    void crearSesionPago_enEntornoProduccionConMockKey_lanzaIllegalStateException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        StripePaymentAdapter adapter = new StripePaymentAdapter(
                "sk_test_mock",
                "https://lysandri.com/checkout/success?session_id={CHECKOUT_SESSION_ID}",
                "https://lysandri.com/checkout/cancel",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adapter.crearSesionPago(crearOrdenPrueba(), "alumno@test.com", "Alumno Test")
        );

        assertTrue(ex.getMessage().contains("CRÍTICO: No se permite operar con credenciales Stripe mock en entorno de PRODUCCIÓN"));
    }

    @Test
    @DisplayName("En adaptador real sin credenciales válidas en dev, rechaza operar y exige MockStripePaymentAdapter")
    void crearSesionPago_conMockKeyEnAdaptadorReal_lanzaIllegalStateException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        StripePaymentAdapter adapter = new StripePaymentAdapter(
                "sk_test_mock",
                "http://localhost:3000/checkout/success?session_id={CHECKOUT_SESSION_ID}",
                "http://localhost:3000/checkout/cancel",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adapter.crearSesionPago(crearOrdenPrueba(), "alumno@test.com", "Alumno Test")
        );

        assertTrue(ex.getMessage().contains("Credenciales de Stripe no configuradas o inválidas"));
    }

    @Test
    @DisplayName("En entorno de producción ('production'), prohíbe verificar pagos con mock key")
    void verificarPagoCompletado_enEntornoProduccionConMockKey_lanzaIllegalStateException() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});

        StripePaymentAdapter adapter = new StripePaymentAdapter(
                "sk_test_mock",
                "https://lysandri.com/checkout/success?session_id={CHECKOUT_SESSION_ID}",
                "https://lysandri.com/checkout/cancel",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adapter.verificarPagoCompletado("cs_test_12345")
        );

        assertTrue(ex.getMessage().contains("CRÍTICO: No se permite operar con credenciales Stripe mock"));
    }

    @Test
    @DisplayName("El adaptador real NUNCA confirma pagos automáticamente con mock key")
    void verificarPagoCompletado_conMockKeyEnAdaptadorReal_noRetornaTrueAutomatico() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        StripePaymentAdapter adapter = new StripePaymentAdapter(
                "sk_test_mock",
                "http://localhost:3000/checkout/success?session_id={CHECKOUT_SESSION_ID}",
                "http://localhost:3000/checkout/cancel",
                environment
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                adapter.verificarPagoCompletado("cs_test_cualquiera")
        );

        assertTrue(ex.getMessage().contains("Credenciales de Stripe no configuradas o inválidas para verificación"));
    }

    @Test
    @DisplayName("Identificador nulo o vacío retorna false inmediatamente")
    void verificarPagoCompletado_conIdNulo_retornaFalse() {
        StripePaymentAdapter adapter = new StripePaymentAdapter(
                "sk_test_mock",
                "http://localhost:3000/checkout/success",
                "http://localhost:3000/checkout/cancel",
                environment
        );

        assertFalse(adapter.verificarPagoCompletado(null));
        assertFalse(adapter.verificarPagoCompletado("   "));
    }
}

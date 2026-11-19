package com.lysandri.config;

import com.lysandri.adapters.in.web.OrderController;
import com.lysandri.adapters.in.web.dto.CreateOrderRequest;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class DisponibilidadPagosTest {
    @Test
    void sinCredencialesNoOfreceTarjeta() {
        assertFalse(new DisponibilidadPagos(new MockEnvironment()).izipayDisponible());
    }

    @Test
    void requiereTodasLasCredencialesSinPublicarlas() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("izipay.shop-id", "comercio-prueba")
                .withProperty("izipay.api-key", "clave-prueba")
                .withProperty("izipay.public-key", "publica-prueba")
                .withProperty("izipay.sha256-key", "firma-prueba");
        DisponibilidadPagos disponibilidad = new DisponibilidadPagos(env);
        assertTrue(disponibilidad.izipayDisponible());
        env.setProperty("izipay.public-key", "test_public_key_lysandri");
        assertFalse(disponibilidad.izipayDisponible());
    }

    @Test
    void tarjetaNoConfiguradaNoIniciaCompraPeroTransferenciaSi() {
        java.util.concurrent.atomic.AtomicInteger llamadas = new java.util.concurrent.atomic.AtomicInteger();
        BuyCourseUseCase compras = (BuyCourseUseCase) java.lang.reflect.Proxy.newProxyInstance(
                BuyCourseUseCase.class.getClassLoader(), new Class<?>[]{BuyCourseUseCase.class},
                (proxy, method, args) -> { llamadas.incrementAndGet(); return null; });
        OrderController controller = new OrderController(compras, null, null);
        ReflectionTestUtils.setField(controller, "disponibilidadPagos",
                new DisponibilidadPagos(new MockEnvironment()));
        CreateOrderRequest request = new CreateOrderRequest();
        for (String metodo : new String[]{"IZIPAY", "TARJETA", "ONLINE", "", null}) {
            request.setMetodoPago(metodo);
            assertThrows(IllegalStateException.class, () -> controller.iniciarCompra(null, request));
        }
        assertEquals(0, llamadas.get());
        request.setMetodoPago("TRANSFERENCIA");
        controller.iniciarCompra(null, request);
        assertEquals(1, llamadas.get());
        assertEquals(java.util.Map.of("izipayDisponible", false), controller.metodosPago());
    }
}

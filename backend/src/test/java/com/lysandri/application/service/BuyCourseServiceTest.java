package com.lysandri.application.service;

import com.lysandri.domain.model.*;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuyCourseServiceTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private ProgramRepositoryPort programRepository;

    @Mock
    private PaymentPort paymentPort;

    @Mock
    private LmsClientPort lmsClientPort;

    @InjectMocks
    private BuyCourseService buyCourseService;

    private Usuario usuarioMock;
    private Programa programaMock;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .idUser(1L)
                .email("ceo@empresa.com")
                .nombres("Carlos")
                .apellidos("Mendoza")
                .rol(RolUsuario.CLIENTE)
                .build();

        programaMock = Programa.builder()
                .idPrograma(10L)
                .moodleCourseId(101L)
                .titulo("FinOps Empresarial")
                .precio(new BigDecimal("799.00"))
                .moneda("USD")
                .activo(true)
                .build();
    }

    @Test
    void iniciarCompra_debeCrearOrdenYRetornarUrlStripe() {
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentPort.crearSesionPago(any(Orden.class), anyString(), anyString()))
                .thenReturn(new PaymentPort.PaymentSessionResult("cs_test_123", "https://checkout.stripe.com/pay/cs_test_123", "pi_123"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(1L, List.of(10L));
        BuyCourseUseCase.CheckoutSessionResponse response = buyCourseService.iniciarCompra(command);

        assertNotNull(response);
        assertEquals("cs_test_123", response.stripeSessionId());
        assertEquals("https://checkout.stripe.com/pay/cs_test_123", response.stripeCheckoutUrl());
        verify(paymentPort).crearSesionPago(any(Orden.class), eq("ceo@empresa.com"), eq("Carlos Mendoza"));
    }

    @Test
    void confirmarPagoYMatricular_debeAprovisionarMoodleYMatricular() {
        Orden ordenMock = Orden.builder()
                .idOrden(1L)
                .idUser(1L)
                .codigoOrden("ORD-001")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(10L)
                        .programa(programaMock)
                        .precioUnitario(new BigDecimal("799.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("799.00"))
                        .build()))
                .build();

        when(orderRepository.buscarPorStripeSessionId("cs_test_123")).thenReturn(Optional.of(ordenMock));
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(lmsClientPort.buscarUsuarioPorEmail("ceo@empresa.com")).thenReturn(Optional.of(55L));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        Orden resultado = buyCourseService.confirmarPagoYMatricular("cs_test_123");

        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        assertTrue(resultado.isMoodleMatriculaSincronizada());
        verify(lmsClientPort).matricularUsuarioEnCurso(eq(55L), eq(101L), eq(5));
    }
}

package com.lysandri.application.service;

import com.lysandri.domain.model.*;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.out.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private PaymentPort izipayPaymentPort;

    @Mock
    private PaymentPort stripePaymentPort;

    @Mock
    private UserActivationPort userActivationPort;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private LmsClientPort lmsClientPort;

    @Mock
    private BillingPort billingPort;

    @Mock
    private NotificationPort notificationPort;

    @Mock
    private OrderFinancialTransactionService orderFinancialTransactionService;

    @Mock
    private ActivationOutboxService activationOutboxService;

    @Mock
    private com.lysandri.security.JwtService jwtService;

    private BuyCourseService buyCourseService;

    private Usuario usuarioMock;
    private Programa programaMock;
    private ComprobantePago comprobanteMock;

    @BeforeEach
    void setUp() {
        buyCourseService = new BuyCourseService(
                orderRepository,
                userRepository,
                programRepository,
                izipayPaymentPort,
                stripePaymentPort,
                lmsClientPort,
                billingPort,
                notificationPort,
                orderFinancialTransactionService,
                userActivationPort,
                passwordEncoder,
                activationOutboxService,
                jwtService,
                "http://localhost:5173"
        );

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
                .moneda("PEN")
                .activo(true)
                .precioAprobado(true)
                .build();

        lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedSecurePasswordRandom");
        lenient().when(userActivationPort.crearTokenActivacion(any())).thenReturn(null);
        lenient().when(izipayPaymentPort.crearSesionPago(any(), any(), any()))
                .thenReturn(new PaymentPort.PaymentSessionResult("izisess_default", "https://checkout.izipay.pe/pay", "izitx_default"));
        lenient().when(stripePaymentPort.crearSesionPago(any(), any(), any()))
                .thenReturn(new PaymentPort.PaymentSessionResult("cs_default", "https://checkout.stripe.com/pay", "pi_default"));

        comprobanteMock = ComprobantePago.builder()
                .idComprobante(1L)
                .idOrden(1L)
                .tipo(TipoComprobante.BOLETA)
                .serie("B001")
                .correlativo(1)
                .montoSubtotal(new BigDecimal("677.12"))
                .montoIgv(new BigDecimal("121.88"))
                .montoTotal(new BigDecimal("799.00"))
                .moneda("PEN")
                .fechaEmision(LocalDateTime.now())
                .estado(EstadoComprobante.EMITIDO)
                .pdfUrl("https://lysandri.com/comprobantes/B001-00000001.pdf")
                .build();

        lenient().when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        lenient().when(orderFinancialTransactionService.confirmarPagoFinanciero(anyString())).thenAnswer(invocation -> {
            String codigo = invocation.getArgument(0);
            Orden o = orderRepository.buscarPorCualquierIdentificador(codigo)
                    .or(() -> orderRepository.buscarPorCodigo(codigo))
                    .or(() -> orderRepository.buscarPorCodigoConBloqueo(codigo))
                    .orElse(null);

            if (o == null) {
                for (Long id : List.of(1L, 2L, 5L, 8L, 9L, 10L, 20L, 30L, 50L, 55L, 77L, 88L, 99L, 100L, 120L, 999L)) {
                    Optional<Orden> opt = orderRepository.buscarPorId(id);
                    if (opt.isPresent() && codigo.equals(opt.get().getCodigoOrden())) {
                        o = opt.get();
                        break;
                    }
                }
            }

            if (o != null) {
                o.setEstadoOrden(EstadoOrden.PAGADO);
                if (o.getFechaPago() == null) {
                    o.setFechaPago(OffsetDateTime.now());
                }
                return o;
            }

            return Orden.builder()
                    .idOrden(1L)
                    .idUser(1L)
                    .codigoOrden(codigo)
                    .estadoOrden(EstadoOrden.PAGADO)
                    .total(new BigDecimal("799.00"))
                    .moneda("PEN")
                    .items(List.of(DetalleOrden.builder()
                            .idPrograma(10L)
                            .programa(programaMock)
                            .precioUnitario(new BigDecimal("799.00"))
                            .cantidad(1)
                            .subtotal(new BigDecimal("799.00"))
                            .moodleMatriculado(false)
                            .build()))
                    .build();
        });

        lenient().when(orderFinancialTransactionService.reservarAprovisionamiento(anyString())).thenAnswer(invocation -> {
            String codigo = invocation.getArgument(0);
            Orden o = orderRepository.buscarPorCualquierIdentificador(codigo)
                    .or(() -> orderRepository.buscarPorCodigo(codigo))
                    .or(() -> orderRepository.buscarPorCodigoConBloqueo(codigo))
                    .orElse(null);

            if (o == null) {
                for (Long id : List.of(1L, 2L, 5L, 8L, 9L, 10L, 20L, 30L, 50L, 55L, 77L, 88L, 99L, 100L, 120L, 999L)) {
                    Optional<Orden> opt = orderRepository.buscarPorId(id);
                    if (opt.isPresent() && codigo.equals(opt.get().getCodigoOrden())) {
                        o = opt.get();
                        break;
                    }
                }
            }

            if (o == null) {
                o = Orden.builder()
                        .idOrden(1L)
                        .idUser(1L)
                        .codigoOrden(codigo)
                        .estadoOrden(EstadoOrden.PAGADO)
                        .total(new BigDecimal("799.00"))
                        .moneda("PEN")
                        .items(new ArrayList<>(List.of(DetalleOrden.builder()
                                .idPrograma(10L)
                                .programa(programaMock)
                                .precioUnitario(new BigDecimal("799.00"))
                                .cantidad(1)
                                .subtotal(new BigDecimal("799.00"))
                                .moodleMatriculado(false)
                                .build())))
                        .build();
            }
            return ResultadoReservaAprovisionamiento.reservado(o, "mock-lease-token-test");
        });

        lenient().when(orderFinancialTransactionService.completarAprovisionamiento(anyString(), anyString(), anyBoolean(), anyList())).thenAnswer(invocation -> {
            String codigo = invocation.getArgument(0);
            boolean sync = invocation.getArgument(2);
            Orden o = orderRepository.buscarPorCualquierIdentificador(codigo)
                    .or(() -> orderRepository.buscarPorCodigo(codigo))
                    .or(() -> orderRepository.buscarPorCodigoConBloqueo(codigo))
                    .orElse(null);

            if (o == null) {
                for (Long id : List.of(1L, 2L, 5L, 8L, 9L, 10L, 20L, 30L, 50L, 55L, 77L, 88L, 99L, 100L, 120L, 999L)) {
                    Optional<Orden> opt = orderRepository.buscarPorId(id);
                    if (opt.isPresent() && codigo.equals(opt.get().getCodigoOrden())) {
                        o = opt.get();
                        break;
                    }
                }
            }

            if (o != null) {
                o.setMoodleMatriculaSincronizada(sync);
                o.setEstadoAprovisionamiento(sync ? EstadoAprovisionamiento.COMPLETADO : EstadoAprovisionamiento.FALLIDO);
                return o;
            }

            return Orden.builder()
                    .idOrden(1L)
                    .idUser(1L)
                    .codigoOrden(codigo)
                    .estadoOrden(EstadoOrden.PAGADO)
                    .moodleMatriculaSincronizada(sync)
                    .estadoAprovisionamiento(sync ? EstadoAprovisionamiento.COMPLETADO : EstadoAprovisionamiento.FALLIDO)
                    .build();
        });

        lenient().when(orderFinancialTransactionService.actualizarResultadoAprovisionamiento(anyString(), anyBoolean(), anyList())).thenAnswer(invocation -> {
            String codigo = invocation.getArgument(0);
            boolean sync = invocation.getArgument(1);
            Orden o = orderRepository.buscarPorCualquierIdentificador(codigo)
                    .or(() -> orderRepository.buscarPorCodigo(codigo))
                    .or(() -> orderRepository.buscarPorCodigoConBloqueo(codigo))
                    .orElse(null);

            if (o == null) {
                for (Long id : List.of(1L, 2L, 5L, 8L, 9L, 10L, 20L, 30L, 50L, 55L, 77L, 88L, 99L, 100L, 120L, 999L)) {
                    Optional<Orden> opt = orderRepository.buscarPorId(id);
                    if (opt.isPresent() && codigo.equals(opt.get().getCodigoOrden())) {
                        o = opt.get();
                        break;
                    }
                }
            }

            if (o != null) {
                o.setMoodleMatriculaSincronizada(sync);
                return o;
            }

            return Orden.builder()
                    .idOrden(1L)
                    .idUser(1L)
                    .codigoOrden(codigo)
                    .estadoOrden(EstadoOrden.PAGADO)
                    .total(new BigDecimal("799.00"))
                    .moneda("PEN")
                    .moodleMatriculaSincronizada(sync)
                    .items(List.of(DetalleOrden.builder()
                            .idPrograma(10L)
                            .programa(programaMock)
                            .precioUnitario(new BigDecimal("799.00"))
                            .cantidad(1)
                            .subtotal(new BigDecimal("799.00"))
                            .moodleMatriculado(sync)
                            .build()))
                    .build();
        });
    }

    @Test
    void iniciarCompra_debeCrearOrdenYRetornarUrlStripe() {
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));
        when(stripePaymentPort.crearSesionPago(any(Orden.class), anyString(), anyString()))
                .thenReturn(new PaymentPort.PaymentSessionResult("cs_test_123", "https://checkout.stripe.com/pay/cs_test_123", "pi_123"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                1L, List.of(10L), "BOLETA", null, null, null, null, "STRIPE", null);
        BuyCourseUseCase.CheckoutSessionResponse response = buyCourseService.iniciarCompra(command);

        assertNotNull(response);
        assertEquals("cs_test_123", response.stripeSessionId());
        assertEquals("https://checkout.stripe.com/pay/cs_test_123", response.stripeCheckoutUrl());
        verify(stripePaymentPort).crearSesionPago(any(Orden.class), eq("ceo@empresa.com"), eq("Carlos Mendoza"));
    }

    @Test
    void iniciarCompra_debeCrearOrdenYRetornarUrlIzipay() {
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));
        when(izipayPaymentPort.crearSesionPago(any(Orden.class), anyString(), anyString()))
                .thenReturn(new PaymentPort.PaymentSessionResult("izisess_123", "https://checkout.izipay.pe/pay", "izitx_123"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(1L, List.of(10L));
        BuyCourseUseCase.CheckoutSessionResponse response = buyCourseService.iniciarCompra(command);

        assertNotNull(response);
        assertEquals("izisess_123", response.izipayTransactionId());
        verify(izipayPaymentPort).crearSesionPago(any(Orden.class), eq("ceo@empresa.com"), eq("Carlos Mendoza"));
    }

    @Test
    void iniciarCompra_programaUSD_debePreservarMonedaUSD() {
        Programa progUSD = Programa.builder()
                .idPrograma(20L)
                .moodleCourseId(202L)
                .titulo("Global Executive MBA")
                .precio(new BigDecimal("1500.00"))
                .moneda("USD")
                .activo(true)
                .precioAprobado(true)
                .build();

        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(programRepository.buscarPorIds(List.of(20L))).thenReturn(List.of(progUSD));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));
        when(izipayPaymentPort.crearSesionPago(any(Orden.class), anyString(), anyString()))
                .thenReturn(new PaymentPort.PaymentSessionResult("izisess_usd", "https://checkout.izipay.pe/pay", "izitx_usd"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(1L, List.of(20L));
        buyCourseService.iniciarCompra(command);

        org.mockito.ArgumentCaptor<Orden> ordenCaptor = org.mockito.ArgumentCaptor.forClass(Orden.class);
        verify(orderRepository, atLeastOnce()).guardar(ordenCaptor.capture());
        Orden ordenGuardada = ordenCaptor.getValue();

        assertEquals("USD", ordenGuardada.getMoneda());
        assertEquals(new BigDecimal("1500.00"), ordenGuardada.getTotal());
    }

    @Test
    void iniciarCompra_carritoMixtoMonedas_debeRechazarConExcepcion() {
        Programa progPEN = Programa.builder()
                .idPrograma(10L)
                .titulo("FinOps")
                .precio(new BigDecimal("799.00"))
                .moneda("PEN")
                .activo(true)
                .precioAprobado(true)
                .build();
        Programa progUSD = Programa.builder()
                .idPrograma(20L)
                .titulo("Global MBA")
                .precio(new BigDecimal("1500.00"))
                .moneda("USD")
                .activo(true)
                .precioAprobado(true)
                .build();

        when(programRepository.buscarPorIds(List.of(10L, 20L))).thenReturn(List.of(progPEN, progUSD));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(1L, List.of(10L, 20L));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseService.iniciarCompra(command));

        assertTrue(ex.getMessage().contains("No se permiten carritos con monedas diferentes"));
    }

    @Test
    void iniciarCompra_programaInactivo_debeRechazar() {
        Programa progInactivo = Programa.builder()
                .idPrograma(30L)
                .titulo("Programa Descontinuado")
                .precio(new BigDecimal("500.00"))
                .moneda("USD")
                .activo(false)
                .precioAprobado(true)
                .build();

        when(programRepository.buscarPorIds(List.of(30L))).thenReturn(List.of(progInactivo));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(1L, List.of(30L));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseService.iniciarCompra(command));

        assertTrue(ex.getMessage().contains("no está activo para la venta comercial"));
    }

    @Test
    void iniciarCompra_precioProvisionalNoAprobado_debeRechazar() {
        Programa progProvisional = Programa.builder()
                .idPrograma(40L)
                .titulo("Programa Sin Aprobar")
                .precio(new BigDecimal("499.00"))
                .moneda("USD")
                .activo(true)
                .precioAprobado(false)
                .build();

        when(programRepository.buscarPorIds(List.of(40L))).thenReturn(List.of(progProvisional));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(1L, List.of(40L));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseService.iniciarCompra(command));

        assertTrue(ex.getMessage().contains("tiene un precio provisional no aprobado comercialmente"));
    }

    @Test
    void iniciarCompra_programasConIdsDuplicados_debeRechazar() {
        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(1L, List.of(10L, 10L));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseService.iniciarCompra(command));

        assertTrue(ex.getMessage().contains("No se permiten programas duplicados"));
    }

    @Test
    void iniciarCompra_invitadoConEmailYaRegistrado_debeRechazarApropiacion() {
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(userRepository.buscarPorEmail("ceo@empresa.com")).thenReturn(Optional.of(usuarioMock));
        when(jwtService.validateGuestCheckoutToken("token-guest-valid-ceo"))
                .thenReturn(new com.lysandri.security.JwtService.GuestSessionClaims("sess-ceo-1", "ceo@empresa.com"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                null, List.of(10L), "ceo@empresa.com", "Carlos Intruso", "BOLETA", null, null, "IZIPAY", null, null, "token-guest-valid-ceo");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseService.iniciarCompra(command));

        assertTrue(ex.getMessage().contains("Ya existe una cuenta registrada con el correo"));
        verify(userRepository, never()).guardar(any(Usuario.class));
    }

    @Test
    void iniciarCompra_invitadoNuevo_creaPasswordAleatoriaYTokenActivacion() {
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(userRepository.buscarPorEmail("nuevo.invitado@empresa.com")).thenReturn(Optional.empty());
        when(jwtService.validateGuestCheckoutToken("token-guest-valid-ana"))
                .thenReturn(new com.lysandri.security.JwtService.GuestSessionClaims("sess-ana-1", "nuevo.invitado@empresa.com"));

        Usuario nuevoUsuario = Usuario.builder()
                .idUser(200L)
                .email("nuevo.invitado@empresa.com")
                .nombres("Ana")
                .apellidos("Gómez")
                .activo(false)
                .build();
        when(userRepository.guardar(any(Usuario.class))).thenReturn(nuevoUsuario);
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));
        when(izipayPaymentPort.crearSesionPago(any(Orden.class), anyString(), anyString()))
                .thenReturn(new PaymentPort.PaymentSessionResult("izisess_guest", "https://checkout.izipay.pe/pay", "izitx_guest"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                null, List.of(10L), "nuevo.invitado@empresa.com", "Ana Gómez", "BOLETA", null, null, "IZIPAY", null, null, "token-guest-valid-ana");

        BuyCourseUseCase.CheckoutSessionResponse response = buyCourseService.iniciarCompra(command);

        assertNotNull(response);
        verify(passwordEncoder).encode(argThat(rawPass -> rawPass != null && !rawPass.toString().contains("$2a$10$7EqJtq98hPqEX7fNZaFWoO.83.R3yR/Xh7b.d8Zt.m6qDk6H/H9G6")));
        verify(userActivationPort).crearTokenActivacion(eq(200L));
    }

    @Test
    @DisplayName("Invitado: Intento de compra sin token de sesión es rechazado con IllegalArgumentException")
    void iniciarCompra_invitadoSinTokenDeSesion_lanzaIllegalArgumentException() {
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                null, List.of(10L), "sin.token@empresa.com", "Sin Token", "BOLETA", null, null, "IZIPAY", null, null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseService.iniciarCompra(command));

        assertTrue(ex.getMessage().contains("Se requiere un token de sesión de compra de invitado válido"));
        verify(userRepository, never()).guardar(any(Usuario.class));
        verify(orderRepository, never()).guardar(any(Orden.class));
    }

    @Test
    @DisplayName("Invitado: Token de sesión expirado o inválido es rechazado con IllegalArgumentException")
    void iniciarCompra_invitadoConTokenExpiradoOInvalido_lanzaIllegalArgumentException() {
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(jwtService.validateGuestCheckoutToken("token-expirado")).thenReturn(null);

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                null, List.of(10L), "expirado@empresa.com", "Expirado", "BOLETA", null, null, "IZIPAY", null, null, "token-expirado");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseService.iniciarCompra(command));

        assertTrue(ex.getMessage().contains("ha expirado o es inválida"));
        verify(userRepository, never()).guardar(any(Usuario.class));
        verify(orderRepository, never()).guardar(any(Orden.class));
    }

    @Test
    @DisplayName("Invitado: Token de sesión con discrepancia de correo es rechazado")
    void iniciarCompra_invitadoConTokenDeOtroEmail_lanzaIllegalArgumentException() {
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(jwtService.validateGuestCheckoutToken("token-otro")).thenReturn(
                new com.lysandri.security.JwtService.GuestSessionClaims("sess-otro", "otro@empresa.com"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                null, List.of(10L), "yo@empresa.com", "Yo", "BOLETA", null, null, "IZIPAY", null, null, "token-otro");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                buyCourseService.iniciarCompra(command));

        assertTrue(ex.getMessage().contains("La sesión de compra no corresponde al correo"));
        verify(userRepository, never()).guardar(any(Usuario.class));
        verify(orderRepository, never()).guardar(any(Orden.class));
    }

    @Test
    void confirmarPagoYMatricular_debeAprovisionarMoodleYMatricular() {
        Orden ordenMock = Orden.builder()
                .idOrden(1L)
                .idUser(1L)
                .codigoOrden("ORD-001")
                .metodoPago("IZIPAY")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("799.00"))
                .moneda("PEN")
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(10L)
                        .programa(programaMock)
                        .precioUnitario(new BigDecimal("799.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("799.00"))
                        .build()))
                .build();

        when(izipayPaymentPort.verificarPagoCompletado("izisess_test_123")).thenReturn(true);
        when(orderRepository.buscarPorCualquierIdentificador("izisess_test_123")).thenReturn(Optional.of(ordenMock));
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(lmsClientPort.buscarUsuarioPorEmail("ceo@empresa.com")).thenReturn(Optional.of(55L));
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(comprobanteMock);

        Orden resultado = buyCourseService.confirmarPagoYMatricular("izisess_test_123");

        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        assertTrue(resultado.isMoodleMatriculaSincronizada());
        verify(lmsClientPort).matricularUsuarioEnCurso(eq(55L), eq(101L), eq(5));
        verify(billingPort).emitirComprobante(any(Orden.class));
        verify(notificationPort).enviarAccesosYFactura(eq("ceo@empresa.com"), anyString(), eq("FinOps Empresarial"), anyString(), eq(comprobanteMock));
    }

    @Test
    void confirmarPagoYMatricular_usuarioInvitadoPendienteActivacion_debeEnviarEnlaceActivacion() {
        Usuario invitadoMock = Usuario.builder()
                .idUser(2L)
                .email("guest@empresa.com")
                .nombres("Laura")
                .apellidos("Guest")
                .rol(RolUsuario.CLIENTE)
                .activo(false)
                .bloqueadoAdmin(false)
                .pendienteActivacion(true)
                .build();

        Orden ordenInvitado = Orden.builder()
                .idOrden(2L)
                .idUser(2L)
                .codigoOrden("ORD-002")
                .metodoPago("STRIPE")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("799.00"))
                .moneda("USD")
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(10L)
                        .programa(programaMock)
                        .precioUnitario(new BigDecimal("799.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("799.00"))
                        .build()))
                .build();

        when(stripePaymentPort.verificarPagoCompletado("cs_test_guest_123")).thenReturn(true);
        when(orderRepository.buscarPorCualquierIdentificador("cs_test_guest_123")).thenReturn(Optional.of(ordenInvitado));
        when(orderFinancialTransactionService.confirmarPagoFinanciero(ordenInvitado.getCodigoOrden())).thenAnswer(i -> {
            ordenInvitado.setEstadoOrden(EstadoOrden.PAGADO);
            return ordenInvitado;
        });
        when(orderFinancialTransactionService.reservarAprovisionamiento(ordenInvitado.getCodigoOrden()))
                .thenReturn(ResultadoReservaAprovisionamiento.reservado(ordenInvitado, "owner-token-guest"));
        when(userRepository.buscarPorId(2L)).thenReturn(Optional.of(invitadoMock));
        when(lmsClientPort.buscarUsuarioPorEmail("guest@empresa.com")).thenReturn(Optional.of(88L));
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(comprobanteMock);
        when(orderFinancialTransactionService.completarAprovisionamiento(any(), any(), anyBoolean(), any()))
                .thenReturn(ordenInvitado);

        Orden resultado = buyCourseService.confirmarPagoYMatricular("cs_test_guest_123");

        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        verify(orderFinancialTransactionService).confirmarPagoFinanciero(ordenInvitado.getCodigoOrden());
        verify(activationOutboxService, atLeastOnce()).procesarTareasPendientes();
    }

    @Test
    void confirmarPagoYMatricular_ordenPagoManual_debeRechazarConfirmacionOnline() {
        Orden ordenManual = Orden.builder()
                .idOrden(5L)
                .codigoOrden("ORD-MANUAL-005")
                .metodoPago("TRANSFERENCIA")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .build();
        when(orderRepository.buscarPorCualquierIdentificador("ORD-MANUAL-005")).thenReturn(Optional.of(ordenManual));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                buyCourseService.confirmarPagoYMatricular("ORD-MANUAL-005"));

        assertTrue(ex.getMessage().contains("método de pago manual"));
        verifyNoInteractions(izipayPaymentPort);
        verifyNoInteractions(stripePaymentPort);
    }

    @Test
    void procesarPagoExitoso_ordenPagoManual_debeRechazarProcesamientoOnline() {
        Orden ordenManual = Orden.builder()
                .idOrden(6L)
                .codigoOrden("ORD-YAPE-006")
                .metodoPago("YAPE")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .build();
        when(orderRepository.buscarPorCualquierIdentificador("ORD-YAPE-006")).thenReturn(Optional.of(ordenManual));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                buyCourseService.procesarPagoExitoso("ORD-YAPE-006"));

        assertTrue(ex.getMessage().contains("método de pago manual"));
    }

    @Test
    void confirmarPagoYMatricular_pagoNoConfirmadoPorPasarela_debeLanzarExcepcion() {
        Orden ordenMock = Orden.builder()
                .idOrden(1L)
                .idUser(1L)
                .codigoOrden("ORD-001")
                .metodoPago("IZIPAY")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("799.00"))
                .build();
        when(orderRepository.buscarPorCualquierIdentificador("cs_fake_unpaid")).thenReturn(Optional.of(ordenMock));
        when(izipayPaymentPort.verificarPagoCompletado("cs_fake_unpaid")).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                buyCourseService.confirmarPagoYMatricular("cs_fake_unpaid"));

        assertTrue(ex.getMessage().contains("no ha sido confirmado por la pasarela"));
        verifyNoInteractions(lmsClientPort);
        verifyNoInteractions(billingPort);
    }

    @Test
    void procesarPagoExitoso_flujoInvitadoConCreacionDeUsuarioEnMoodleYFacturacion() {
        Orden ordenInvitado = Orden.builder()
                .idOrden(2L)
                .idUser(1L)
                .codigoOrden("ORD-GUEST-002")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("799.00"))
                .moneda("PEN")
                .tipoComprobanteSolicitado("FACTURA")
                .numeroDocumentoCliente("20601234567")
                .nombreFacturacion("TechCorp SAC")
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(10L)
                        .programa(programaMock)
                        .precioUnitario(new BigDecimal("799.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("799.00"))
                        .build()))
                .build();

        ComprobantePago facturaMock = ComprobantePago.builder()
                .idComprobante(2L)
                .idOrden(2L)
                .tipo(TipoComprobante.FACTURA)
                .serie("F001")
                .correlativo(1)
                .tipoDocumentoIdentidad("RUC")
                .numeroDocumentoIdentidad("20601234567")
                .razonSocialONombre("TechCorp SAC")
                .montoSubtotal(new BigDecimal("677.12"))
                .montoIgv(new BigDecimal("121.88"))
                .montoTotal(new BigDecimal("799.00"))
                .moneda("PEN")
                .build();

        when(orderRepository.buscarPorCualquierIdentificador("cs_guest_999")).thenReturn(Optional.of(ordenInvitado));
        when(orderRepository.buscarPorCodigo("ORD-GUEST-002")).thenReturn(Optional.of(ordenInvitado));
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(lmsClientPort.buscarUsuarioPorEmail("ceo@empresa.com")).thenReturn(Optional.empty());
        when(lmsClientPort.crearUsuario(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(88L);
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(facturaMock);

        Orden resultado = buyCourseService.procesarPagoExitoso("cs_guest_999");

        assertNotNull(resultado);
        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        verify(lmsClientPort).crearUsuario(eq("ceoempresa.com"), anyString(), eq("Carlos"), eq("Mendoza"), eq("ceo@empresa.com"));
        verify(lmsClientPort).matricularUsuarioEnCurso(eq(88L), eq(101L), eq(5));
        verify(billingPort).emitirComprobante(ordenInvitado);
        verify(notificationPort).enviarAccesosYFactura(
                eq("ceo@empresa.com"),
                eq("TechCorp SAC"),
                eq("FinOps Empresarial"),
                anyString(),
                eq(facturaMock)
        );
    }

    @Test
    void confirmarPagoManual_exitoso() {
        BuyCourseUseCase.ConfirmarPagoManualCommand cmd = new BuyCourseUseCase.ConfirmarPagoManualCommand(
                "YAPE-OP-987654",
                "YAPE",
                "Voucher validado correctamente"
        );

        Orden ordenManual = Orden.builder()
                .idOrden(5L)
                .idUser(1L)
                .codigoOrden("ORD-YAPE-005")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .metodoPago("YAPE")
                .total(new BigDecimal("799.00"))
                .moneda("PEN")
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(10L)
                        .programa(programaMock)
                        .precioUnitario(new BigDecimal("799.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("799.00"))
                        .build()))
                .build();

        // orderRepository.buscarPorId not called by confirmarPagoManual
        when(orderFinancialTransactionService.confirmarPagoManualFinanciero(eq(5L), any())).thenAnswer(i -> {
            ordenManual.setEstadoOrden(EstadoOrden.PAGADO);
            ordenManual.setNumeroOperacion(cmd.numeroOperacion());
            return ordenManual;
        });
        when(orderFinancialTransactionService.reservarAprovisionamiento(eq("ORD-YAPE-005"))).thenReturn(
                ResultadoReservaAprovisionamiento.reservado(ordenManual, "token-123")
        );
        when(orderFinancialTransactionService.completarAprovisionamiento(eq("ORD-YAPE-005"), anyString(), anyBoolean(), anyList())).thenAnswer(i -> {
            ordenManual.setMoodleMatriculaSincronizada(i.getArgument(2));
            return ordenManual;
        });
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(lmsClientPort.buscarUsuarioPorEmail("ceo@empresa.com")).thenReturn(Optional.of(55L));
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(comprobanteMock);

        Orden resultado = buyCourseService.confirmarPagoManual(5L, cmd);

        assertNotNull(resultado);
        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        assertEquals("YAPE-OP-987654", resultado.getNumeroOperacion());
        assertTrue(resultado.isMoodleMatriculaSincronizada());
        verify(lmsClientPort).matricularUsuarioEnCurso(eq(55L), eq(101L), eq(5));
        verify(billingPort).emitirComprobante(ordenManual);
    }

    @Test
    void procesarPagoExitoso_cuandoMoodleFalla_debeDejarOrdenPagadaYSincronizadaFalse() {
        Orden ordenMock = Orden.builder()
                .idOrden(8L)
                .idUser(1L)
                .codigoOrden("ORD-008")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("799.00"))
                .moneda("PEN")
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(10L)
                        .programa(programaMock)
                        .precioUnitario(new BigDecimal("799.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("799.00"))
                        .build()))
                .build();

        when(orderRepository.buscarPorCualquierIdentificador("session_moodle_down")).thenReturn(Optional.of(ordenMock));
        when(orderFinancialTransactionService.confirmarPagoFinanciero("ORD-008")).thenAnswer(i -> {
            ordenMock.setEstadoOrden(EstadoOrden.PAGADO);
            return ordenMock;
        });
        when(orderFinancialTransactionService.completarAprovisionamiento(eq("ORD-008"), anyString(), anyBoolean(), anyList())).thenAnswer(i -> {
            ordenMock.setMoodleMatriculaSincronizada(i.getArgument(2));
            return ordenMock;
        });
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(lmsClientPort.buscarUsuarioPorEmail("ceo@empresa.com")).thenReturn(Optional.of(55L));
        doThrow(new RuntimeException("Moodle API timeout 504")).when(lmsClientPort).matricularUsuarioEnCurso(55L, 101L, 5);
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(comprobanteMock);

        Orden resultado = buyCourseService.procesarPagoExitoso("session_moodle_down");

        assertNotNull(resultado);
        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        assertFalse(resultado.isMoodleMatriculaSincronizada(), "La sincronización con Moodle debe ser falsa si falló la llamada al LMS");
        verify(billingPort).emitirComprobante(any(Orden.class));
    }

    @Test
    void reintentarMatricula_cuandoOrdenPagadaPeroNoSincronizada_debeReintentarYActualizar() {
        Orden ordenMock = Orden.builder()
                .idOrden(9L)
                .idUser(1L)
                .codigoOrden("ORD-009")
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("799.00"))
                .moneda("PEN")
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(10L)
                        .programa(programaMock)
                        .precioUnitario(new BigDecimal("799.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("799.00"))
                        .build()))
                .build();

        when(orderRepository.buscarPorId(9L)).thenReturn(Optional.of(ordenMock));
        when(orderFinancialTransactionService.completarAprovisionamiento(eq("ORD-009"), anyString(), anyBoolean(), anyList())).thenAnswer(i -> {
            ordenMock.setMoodleMatriculaSincronizada(i.getArgument(2));
            return ordenMock;
        });
        when(userRepository.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(lmsClientPort.buscarUsuarioPorEmail("ceo@empresa.com")).thenReturn(Optional.of(55L));
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(comprobanteMock);

        Orden resultado = buyCourseService.reintentarMatricula(9L);

        assertNotNull(resultado);
        assertTrue(resultado.isMoodleMatriculaSincronizada());
        verify(lmsClientPort).matricularUsuarioEnCurso(55L, 101L, 5);
    }

    @Test
    void procesarPagoExitoso_errorAlAdquirirBloqueo_lanzaExcepcion() {
        Orden ordenMock = Orden.builder()
                .idOrden(99L)
                .codigoOrden("ORD-LOCK-FAIL")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .build();
        when(orderRepository.buscarPorCualquierIdentificador("cs_lock_fail")).thenReturn(Optional.of(ordenMock));
        when(orderFinancialTransactionService.confirmarPagoFinanciero("ORD-LOCK-FAIL"))
                .thenThrow(new IllegalStateException("No se pudo adquirir el bloqueo pesimista exclusivo para la orden: ORD-LOCK-FAIL"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                buyCourseService.procesarPagoExitoso("cs_lock_fail"));

        assertTrue(ex.getMessage().contains("No se pudo adquirir el bloqueo pesimista exclusivo"));
        verifyNoInteractions(lmsClientPort);
        verifyNoInteractions(billingPort);
    }

    @Test
    void procesarPagoExitoso_ordenCancelada_lanzaExcepcion() {
        Orden ordenMock = Orden.builder()
                .idOrden(100L)
                .codigoOrden("ORD-CANCELADA")
                .estadoOrden(EstadoOrden.CANCELADO)
                .build();
        when(orderRepository.buscarPorCualquierIdentificador("cs_cancelada")).thenReturn(Optional.of(ordenMock));
        when(orderFinancialTransactionService.confirmarPagoFinanciero("ORD-CANCELADA"))
                .thenThrow(new IllegalStateException("No se puede confirmar el pago de una orden CANCELADA: ORD-CANCELADA"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                buyCourseService.procesarPagoExitoso("cs_cancelada"));

        assertTrue(ex.getMessage().contains("No se puede confirmar el pago de una orden CANCELADA"));
        verifyNoInteractions(lmsClientPort);
        verifyNoInteractions(billingPort);
    }

    @Test
    void procesarPagoExitoso_ordenFallida_lanzaExcepcion() {
        Orden ordenMock = Orden.builder()
                .idOrden(101L)
                .codigoOrden("ORD-FALLIDA")
                .estadoOrden(EstadoOrden.FALLIDO)
                .build();
        when(orderRepository.buscarPorCualquierIdentificador("cs_fallida")).thenReturn(Optional.of(ordenMock));
        when(orderFinancialTransactionService.confirmarPagoFinanciero("ORD-FALLIDA"))
                .thenThrow(new IllegalStateException("No se puede confirmar el pago de una orden en estado FALLIDO: ORD-FALLIDA"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                buyCourseService.procesarPagoExitoso("cs_fallida"));

        assertTrue(ex.getMessage().contains("No se puede confirmar el pago de una orden en estado FALLIDO"));
        verifyNoInteractions(lmsClientPort);
        verifyNoInteractions(billingPort);
    }

    @Test
    @DisplayName("Idempotencia Invitado: Reintento con clave y token de sesion valido recupera orden")
    void iniciarCompra_idempotenciaInvitadoConTokenValido_recuperaOrden() {
        String clave = "idemp-guest-valid-1";
        String sessionId = "sess-uuid-12345";
        String token = "valid-guest-jwt-token";
        
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        byte[] hashBytes = md.digest(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) sb.append(String.format("%02x", b));
        String expectedHash = sb.toString();

        Orden ordenExistente = Orden.builder()
                .idOrden(777L)
                .codigoOrden("ORD-GUEST-777")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("799.00"))
                .moneda("PEN")
                .metodoPago("TRANSFERENCIA")
                .claveIdempotencia(clave)
                .guestSessionHash(expectedHash)
                .items(List.of(DetalleOrden.builder().idPrograma(10L).subtotal(new BigDecimal("799.00")).build()))
                .build();

        when(orderRepository.buscarPorClaveIdempotencia(clave)).thenReturn(Optional.of(ordenExistente));
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(jwtService.validateGuestCheckoutToken(token))
                .thenReturn(new com.lysandri.security.JwtService.GuestSessionClaims(sessionId, "invitado@test.pe"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                null,
                List.of(10L),
                "invitado@test.pe",
                "Juan Invitado",
                "BOLETA",
                "70809010",
                "Juan Invitado",
                "TRANSFERENCIA",
                null,
                clave,
                token
        );

        BuyCourseUseCase.CheckoutSessionResponse res = buyCourseService.iniciarCompra(command);
        assertNotNull(res);
        assertEquals("ORD-GUEST-777", res.codigoOrden());
        assertEquals(token, res.guestSessionToken());
    }

    @Test
    @DisplayName("Idempotencia Invitado: Intento de recuperar con token invalido o de otra sesion es rechazado (403)")
    void iniciarCompra_idempotenciaInvitadoConTokenInvalido_lanzaAccessDeniedException() {
        String clave = "idemp-guest-invalid-1";
        Orden ordenExistente = Orden.builder()
                .idOrden(778L)
                .codigoOrden("ORD-GUEST-778")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("799.00"))
                .moneda("PEN")
                .metodoPago("TRANSFERENCIA")
                .claveIdempotencia(clave)
                .guestSessionHash("hash_de_otra_sesion_distinta")
                .items(List.of(DetalleOrden.builder().idPrograma(10L).subtotal(new BigDecimal("799.00")).build()))
                .build();

        when(orderRepository.buscarPorClaveIdempotencia(clave)).thenReturn(Optional.of(ordenExistente));
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));
        when(jwtService.validateGuestCheckoutToken("token-falso"))
                .thenReturn(new com.lysandri.security.JwtService.GuestSessionClaims("otra-sesion-diferente", "hacker@test.pe"));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                null,
                List.of(10L),
                "hacker@test.pe",
                "Atacante",
                "BOLETA",
                "00000000",
                "Atacante",
                "TRANSFERENCIA",
                null,
                clave,
                "token-falso"
        );

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                buyCourseService.iniciarCompra(command));
    }

    @Test
    @DisplayName("Idempotencia Invitado: Intento de recuperar aportando unicamente email sin token es rechazado (403)")
    void iniciarCompra_idempotenciaInvitadoSinToken_lanzaAccessDeniedException() {
        String clave = "idemp-guest-notoken-1";
        Orden ordenExistente = Orden.builder()
                .idOrden(779L)
                .codigoOrden("ORD-GUEST-779")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("799.00"))
                .moneda("PEN")
                .metodoPago("TRANSFERENCIA")
                .claveIdempotencia(clave)
                .guestSessionHash("hash_sesion_segura")
                .items(List.of(DetalleOrden.builder().idPrograma(10L).subtotal(new BigDecimal("799.00")).build()))
                .build();

        when(orderRepository.buscarPorClaveIdempotencia(clave)).thenReturn(Optional.of(ordenExistente));
        when(programRepository.buscarPorIds(List.of(10L))).thenReturn(List.of(programaMock));

        BuyCourseUseCase.CreateOrderCommand command = new BuyCourseUseCase.CreateOrderCommand(
                null,
                List.of(10L),
                "victima@test.pe",
                "Victima",
                "BOLETA",
                "11223344",
                "Victima",
                "TRANSFERENCIA",
                null,
                clave,
                null // Sin token de sesion
        );

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                buyCourseService.iniciarCompra(command));
    }
}

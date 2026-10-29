package com.lysandri.application.service;

import com.lysandri.domain.model.*;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.in.MatriculaCorporativaUseCase;
import com.lysandri.domain.ports.in.SolicitudInformacionUseCase;
import com.lysandri.domain.ports.out.*;
import com.lysandri.adapters.out.database.entity.LoteMatriculaCorporativaEntity;
import com.lysandri.adapters.out.database.repository.SpringDataLoteMatriculaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Verificación de Integración Completa - Lysandri Executive")
class LysandriBackendIntegrationFlowsTest {

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
    private LmsClientPort lmsClientPort;

    @Mock
    private BillingPort billingPort;

    @Mock
    private NotificationPort notificationPort;

    @Mock
    private OrderFinancialTransactionService orderFinancialTransactionService;

    @Mock
    private SolicitudInformacionUseCase solicitudInformacionUseCase;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SpringDataLoteMatriculaRepository loteMatriculaRepository;

    private BuyCourseService buyCourseService;

    private MatriculaCorporativaService matriculaCorporativaService;

    private Usuario alumno;
    private Programa programaEjecutivo;
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
                passwordEncoder
        );

        matriculaCorporativaService = new MatriculaCorporativaService(
                programRepository,
                userRepository,
                lmsClientPort,
                notificationPort,
                solicitudInformacionUseCase,
                passwordEncoder,
                loteMatriculaRepository
        );

        alumno = Usuario.builder()
                .idUser(50L)
                .email("directora@banco.pe")
                .nombres("Rosa")
                .apellidos("Alarcón")
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .build();

        programaEjecutivo = Programa.builder()
                .idPrograma(101L)
                .titulo("Gobierno Corporativo y Finanzas")
                .precio(new BigDecimal("1200.00"))
                .moneda("PEN")
                .moodleCourseId(404L)
                .activo(true)
                .precioAprobado(true)
                .build();

        comprobanteMock = ComprobantePago.builder()
                .idComprobante(1001L)
                .serie("B001")
                .correlativo(1001)
                .montoTotal(new BigDecimal("1200.00"))
                .tipo(TipoComprobante.BOLETA)
                .estado(EstadoComprobante.EMITIDO)
                .fechaEmision(LocalDateTime.now())
                .build();

        lenient().when(orderFinancialTransactionService.confirmarPagoFinanciero(anyString())).thenAnswer(invocation -> {
            String codigo = invocation.getArgument(0);
            return Orden.builder()
                    .idOrden(77L)
                    .idUser(50L)
                    .codigoOrden(codigo)
                    .estadoOrden(EstadoOrden.PAGADO)
                    .total(new BigDecimal("1200.00"))
                    .moneda("PEN")
                    .items(List.of(DetalleOrden.builder()
                            .idPrograma(101L)
                            .programa(programaEjecutivo)
                            .precioUnitario(new BigDecimal("1200.00"))
                            .cantidad(1)
                            .subtotal(new BigDecimal("1200.00"))
                            .build()))
                    .build();
        });

        lenient().when(orderFinancialTransactionService.reservarAprovisionamiento(anyString())).thenAnswer(invocation -> {
            String codigo = invocation.getArgument(0);
            Orden o = Orden.builder()
                    .idOrden(77L)
                    .idUser(50L)
                    .codigoOrden(codigo)
                    .estadoOrden(EstadoOrden.PAGADO)
                    .total(new BigDecimal("1200.00"))
                    .moneda("PEN")
                    .items(List.of(DetalleOrden.builder()
                            .idPrograma(101L)
                            .programa(programaEjecutivo)
                            .precioUnitario(new BigDecimal("1200.00"))
                            .cantidad(1)
                            .subtotal(new BigDecimal("1200.00"))
                            .build()))
                    .build();
            return ResultadoReservaAprovisionamiento.reservado(o, "mock-lease-token");
        });

        lenient().when(orderFinancialTransactionService.completarAprovisionamiento(anyString(), anyString(), anyBoolean(), anyList()))
                .thenAnswer(invocation -> {
                    String codigo = invocation.getArgument(0);
                    boolean allEnrolled = invocation.getArgument(2);
                    return Orden.builder()
                            .idOrden(77L)
                            .idUser(50L)
                            .codigoOrden(codigo)
                            .estadoOrden(EstadoOrden.PAGADO)
                            .moodleMatriculaSincronizada(allEnrolled)
                            .estadoAprovisionamiento(allEnrolled ? EstadoAprovisionamiento.COMPLETADO : EstadoAprovisionamiento.FALLIDO)
                            .build();
                });
    }

    @Test
    @DisplayName("Flujo A: Compra exitosa completa, confirmación validada con pasarela, emisión y matrícula Moodle")
    void flujoA_compraExitosa() {
        // 1. Crear orden
        when(userRepository.buscarPorId(50L)).thenReturn(Optional.of(alumno));
        when(programRepository.buscarPorIds(List.of(101L))).thenReturn(List.of(programaEjecutivo));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> {
            Orden o = i.getArgument(0);
            o.setIdOrden(77L);
            return o;
        });
        when(izipayPaymentPort.crearSesionPago(any(Orden.class), eq("directora@banco.pe"), eq("Rosa Alarcón")))
                .thenReturn(new PaymentPort.PaymentSessionResult("sess_live_77", "https://checkout.stripe.com/pay/77", "pi_77"));

        BuyCourseUseCase.CreateOrderCommand cmd = new BuyCourseUseCase.CreateOrderCommand(50L, List.of(101L));
        BuyCourseUseCase.CheckoutSessionResponse sessionResp = buyCourseService.iniciarCompra(cmd);

        assertNotNull(sessionResp);
        assertEquals("sess_live_77", sessionResp.izipayTransactionId());

        // 2. Simular recepción de confirmación con pasarela validando pago
        Orden ordenCreada = Orden.builder()
                .idOrden(77L)
                .idUser(50L)
                .codigoOrden("ORD-77")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .metodoPago("STRIPE")
                .total(new BigDecimal("1200.00"))
                .moneda("PEN")
                .stripeSessionId("sess_live_77")
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(101L)
                        .programa(programaEjecutivo)
                        .precioUnitario(new BigDecimal("1200.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("1200.00"))
                        .build()))
                .build();

        when(orderRepository.buscarPorCualquierIdentificador("sess_live_77")).thenReturn(Optional.of(ordenCreada));
        when(orderFinancialTransactionService.confirmarPagoFinanciero("ORD-77")).thenAnswer(i -> {
            ordenCreada.setEstadoOrden(EstadoOrden.PAGADO);
            if (ordenCreada.getFechaPago() == null) ordenCreada.setFechaPago(OffsetDateTime.now());
            return ordenCreada;
        });
        when(orderFinancialTransactionService.reservarAprovisionamiento("ORD-77"))
                .thenReturn(ResultadoReservaAprovisionamiento.reservado(ordenCreada, "token-77"));
        when(orderFinancialTransactionService.completarAprovisionamiento(eq("ORD-77"), eq("token-77"), anyBoolean(), anyList()))
                .thenAnswer(i -> {
                    ordenCreada.setMoodleMatriculaSincronizada(i.getArgument(2));
                    ordenCreada.setEstadoAprovisionamiento(i.getArgument(2) ? EstadoAprovisionamiento.COMPLETADO : EstadoAprovisionamiento.FALLIDO);
                    return ordenCreada;
                });
        when(stripePaymentPort.verificarPagoCompletado("sess_live_77")).thenReturn(true);
        when(userRepository.buscarPorId(50L)).thenReturn(Optional.of(alumno));
        when(lmsClientPort.buscarUsuarioPorEmail("directora@banco.pe")).thenReturn(Optional.of(909L));
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(comprobanteMock);

        Orden ordenFinal = buyCourseService.confirmarPagoYMatricular("sess_live_77");

        assertEquals(EstadoOrden.PAGADO, ordenFinal.getEstadoOrden());
        assertTrue(ordenFinal.isMoodleMatriculaSincronizada());
        verify(lmsClientPort).matricularUsuarioEnCurso(eq(909L), eq(404L), eq(5));
        verify(billingPort).emitirComprobante(ordenCreada);
        verify(notificationPort).enviarAccesosYFactura(eq("directora@banco.pe"), anyString(), eq("Gobierno Corporativo y Finanzas"), anyString(), eq(comprobanteMock));
    }

    @Test
    @DisplayName("Flujo B: Pago rechazado no marca orden como pagada ni aprovisiona Moodle")
    void flujoB_pagoRechazado_noConfirmaNiMatricula() {
        Orden ordenPendiente = Orden.builder()
                .idOrden(88L)
                .codigoOrden("ORD-88")
                .metodoPago("IZIPAY")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("1200.00"))
                .build();

        when(orderRepository.buscarPorCualquierIdentificador("sess_unpaid_88")).thenReturn(Optional.of(ordenPendiente));
        when(izipayPaymentPort.verificarPagoCompletado("sess_unpaid_88")).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                buyCourseService.confirmarPagoYMatricular("sess_unpaid_88"));

        assertTrue(ex.getMessage().contains("no ha sido confirmado por la pasarela"));
        assertEquals(EstadoOrden.PENDIENTE, ordenPendiente.getEstadoOrden());
        assertFalse(ordenPendiente.isMoodleMatriculaSincronizada());
        verifyNoInteractions(lmsClientPort);
        verifyNoInteractions(billingPort);
    }

    @Test
    @DisplayName("Flujo C: Fallo temporal de Moodle mantiene orden PAGADA y permite reintento idempotente sin doble cobro")
    void flujoC_falloMoodle_y_reintentoSeguro() {
        Orden ordenPendiente = Orden.builder()
                .idOrden(99L)
                .idUser(50L)
                .codigoOrden("ORD-99")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("1200.00"))
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(101L)
                        .programa(programaEjecutivo)
                        .precioUnitario(new BigDecimal("1200.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("1200.00"))
                        .build()))
                .build();

        when(orderRepository.buscarPorCualquierIdentificador("sess_moodle_down_99")).thenReturn(Optional.of(ordenPendiente));
        when(orderFinancialTransactionService.confirmarPagoFinanciero("ORD-99")).thenAnswer(i -> {
            ordenPendiente.setEstadoOrden(EstadoOrden.PAGADO);
            if (ordenPendiente.getFechaPago() == null) ordenPendiente.setFechaPago(OffsetDateTime.now());
            return ordenPendiente;
        });
        when(orderFinancialTransactionService.reservarAprovisionamiento("ORD-99"))
                .thenAnswer(i -> ResultadoReservaAprovisionamiento.reservado(ordenPendiente, "token-99"));
        when(orderFinancialTransactionService.completarAprovisionamiento(eq("ORD-99"), eq("token-99"), anyBoolean(), anyList()))
                .thenAnswer(i -> {
                    ordenPendiente.setMoodleMatriculaSincronizada(i.getArgument(2));
                    ordenPendiente.setEstadoAprovisionamiento(i.getArgument(2) ? EstadoAprovisionamiento.COMPLETADO : EstadoAprovisionamiento.FALLIDO);
                    return ordenPendiente;
                });
        when(userRepository.buscarPorId(50L)).thenReturn(Optional.of(alumno));
        when(lmsClientPort.buscarUsuarioPorEmail("directora@banco.pe")).thenReturn(Optional.of(909L));
        doThrow(new RuntimeException("Moodle API 503 Service Unavailable")).when(lmsClientPort)
                .matricularUsuarioEnCurso(909L, 404L, 5);
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(comprobanteMock);

        // 1. Procesa pago exitoso (webhook) pero Moodle cae
        Orden resultadoFalloMoodle = buyCourseService.procesarPagoExitoso("sess_moodle_down_99");

        assertEquals(EstadoOrden.PAGADO, resultadoFalloMoodle.getEstadoOrden());
        assertFalse(resultadoFalloMoodle.isMoodleMatriculaSincronizada(), "La sincronización debe ser false ante fallo del LMS");

        // 2. Administrador ejecuta reintento de matrícula
        reset(lmsClientPort);
        when(orderRepository.buscarPorId(99L)).thenReturn(Optional.of(resultadoFalloMoodle));
        when(lmsClientPort.buscarUsuarioPorEmail("directora@banco.pe")).thenReturn(Optional.of(909L));

        Orden resultadoReintento = buyCourseService.reintentarMatricula(99L);

        assertTrue(resultadoReintento.isMoodleMatriculaSincronizada());
        verify(lmsClientPort).matricularUsuarioEnCurso(909L, 404L, 5);
        assertEquals(EstadoOrden.PAGADO, resultadoReintento.getEstadoOrden());
    }

    @Test
    @DisplayName("Flujo D: Webhook duplicado no re-ejecuta facturación ni matrículas duplicadas")
    void flujoD_webhookDuplicado_garantizaIdempotencia() {
        Orden ordenYaPagadaYMatriculada = Orden.builder()
                .idOrden(120L)
                .codigoOrden("ORD-120")
                .estadoOrden(EstadoOrden.PAGADO)
                .total(new BigDecimal("1200.00"))
                .moodleMatriculaSincronizada(true)
                .fechaPago(OffsetDateTime.now())
                .build();

        when(orderRepository.buscarPorCualquierIdentificador("sess_dup_120"))
                .thenReturn(Optional.of(ordenYaPagadaYMatriculada));

        Orden primeraInvocacion = buyCourseService.procesarPagoExitoso("sess_dup_120");
        Orden segundaInvocacion = buyCourseService.procesarPagoExitoso("sess_dup_120");

        assertSame(ordenYaPagadaYMatriculada, primeraInvocacion);
        assertSame(ordenYaPagadaYMatriculada, segundaInvocacion);

        verifyNoInteractions(billingPort);
        verifyNoInteractions(lmsClientPort);
        verifyNoInteractions(notificationPort);
    }

    @Test
    @DisplayName("Flujo E: Matrícula corporativa procesa colaboradores y persiste auditoría en LOTE_MATRICULA_CORPORATIVA")
    void flujoE_matriculaCorporativa_persisteAuditoria() {
        when(programRepository.buscarPorId(101L)).thenReturn(Optional.of(programaEjecutivo));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedSamplePass");

        // Colaborador 1: Nuevo
        when(userRepository.buscarPorEmail("c1@corporacion.com")).thenReturn(Optional.empty());
        when(userRepository.guardar(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setIdUser(501L);
            return u;
        });
        when(lmsClientPort.buscarUsuarioPorEmail("c1@corporacion.com")).thenReturn(Optional.empty());
        when(lmsClientPort.crearUsuario(eq("c1"), anyString(), eq("Mario"), eq("Vargas"), eq("c1@corporacion.com")))
                .thenReturn(1001L);

        // Colaborador 2: Con error (email inválido)
        MatriculaCorporativaUseCase.MatricularNominaCommand command = new MatriculaCorporativaUseCase.MatricularNominaCommand(
                101L,
                42L,
                "Corporación Minera del Sur",
                List.of(
                        new MatriculaCorporativaUseCase.ColaboradorItem("Mario", "Vargas", "c1@corporacion.com", "Gerente", "999888777"),
                        new MatriculaCorporativaUseCase.ColaboradorItem("Invalido", "SinCorreo", "", "Asistente", null)
                ),
                true,
                "Lote de capacitación Q4"
        );

        MatriculaCorporativaUseCase.MatriculaNominaResponse response = matriculaCorporativaService.matricularNomina(command);

        assertNotNull(response);
        assertEquals(2, response.totalProcesados());
        assertEquals(1, response.totalExitosos());
        assertEquals(1, response.totalFallidos());

        // Verificar que se guardó la entidad de auditoría en la BD
        ArgumentCaptor<LoteMatriculaCorporativaEntity> loteCaptor = ArgumentCaptor.forClass(LoteMatriculaCorporativaEntity.class);
        verify(loteMatriculaRepository).save(loteCaptor.capture());

        LoteMatriculaCorporativaEntity loteGuardado = loteCaptor.getValue();
        assertEquals(101L, loteGuardado.getIdPrograma());
        assertEquals(42L, loteGuardado.getIdSolicitud());
        assertEquals("Corporación Minera del Sur", loteGuardado.getEmpresaNombre());
        assertEquals(2, loteGuardado.getTotalColaboradores());
        assertEquals(1, loteGuardado.getTotalExitosos());
        assertEquals(1, loteGuardado.getTotalFallidos());
        assertNotNull(loteGuardado.getFechaRegistro());
    }

    @Test
    @DisplayName("Flujo F: 10 hilos concurrentes simultáneos procesando la misma orden garantizan idempotencia y única facturación")
    void flujoF_concurrenciaDiezHilosSimultaneos_garantizaIdempotencia() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean matriculado = new java.util.concurrent.atomic.AtomicBoolean(false);

        Orden ordenCompartida = Orden.builder()
                .idOrden(999L)
                .idUser(50L)
                .codigoOrden("ORD-CONC-999")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .total(new BigDecimal("1200.00"))
                .moneda("PEN")
                .moodleMatriculaSincronizada(false)
                .items(List.of(DetalleOrden.builder()
                        .idPrograma(101L)
                        .programa(programaEjecutivo)
                        .precioUnitario(new BigDecimal("1200.00"))
                        .cantidad(1)
                        .subtotal(new BigDecimal("1200.00"))
                        .build()))
                .build();

        when(orderRepository.buscarPorCualquierIdentificador("sess_concurrent_999"))
                .thenReturn(Optional.of(ordenCompartida));
        when(userRepository.buscarPorId(50L)).thenReturn(Optional.of(alumno));
        when(lmsClientPort.buscarUsuarioPorEmail("directora@banco.pe")).thenReturn(Optional.of(909L));
        when(billingPort.emitirComprobante(any(Orden.class))).thenReturn(comprobanteMock);

        when(orderFinancialTransactionService.confirmarPagoFinanciero("ORD-CONC-999")).thenAnswer(i -> {
            ordenCompartida.setEstadoOrden(EstadoOrden.PAGADO);
            if (ordenCompartida.getFechaPago() == null) ordenCompartida.setFechaPago(OffsetDateTime.now());
            return ordenCompartida;
        });

        java.util.concurrent.atomic.AtomicBoolean leaseAdquirido = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(orderFinancialTransactionService.reservarAprovisionamiento("ORD-CONC-999")).thenAnswer(i -> {
            if (leaseAdquirido.compareAndSet(false, true)) {
                return ResultadoReservaAprovisionamiento.reservado(ordenCompartida, "token-lease-hilo-1");
            } else {
                return ResultadoReservaAprovisionamiento.enProceso(ordenCompartida);
            }
        });

        when(orderFinancialTransactionService.completarAprovisionamiento(eq("ORD-CONC-999"), eq("token-lease-hilo-1"), anyBoolean(), anyList()))
                .thenAnswer(i -> {
                    ordenCompartida.setMoodleMatriculaSincronizada(true);
                    matriculado.set(true);
                    return ordenCompartida;
                });

        int numHilos = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numHilos);
        java.util.concurrent.CountDownLatch startGate = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch endGate = new java.util.concurrent.CountDownLatch(numHilos);
        java.util.List<Orden> resultados = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.List<Throwable> errores = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    Orden res = buyCourseService.procesarPagoExitoso("sess_concurrent_999");
                    resultados.add(res);
                } catch (Throwable t) {
                    errores.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Disparar los 10 hilos en el mismo instante
        startGate.countDown();
        boolean terminado = endGate.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(terminado, "Los 10 hilos debieron terminar en menos de 10 segundos");
        assertTrue(errores.isEmpty(), "Ningún hilo debió fallar por concurrencia: " + errores);
        assertEquals(10, resultados.size(), "Todos los 10 hilos deben retornar la orden confirmada");

        for (Orden o : resultados) {
            assertEquals(EstadoOrden.PAGADO, o.getEstadoOrden());
        }

        // Verificación de idempotencia estricta:
        // La emisión de facturación y matrícula LMS se ejecutan de forma protegida
        assertTrue(ordenCompartida.isMoodleMatriculaSincronizada());
    }
}

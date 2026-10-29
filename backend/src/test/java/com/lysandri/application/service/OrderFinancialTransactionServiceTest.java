package com.lysandri.application.service;

import com.lysandri.domain.model.DetalleOrden;
import com.lysandri.domain.model.EstadoAprovisionamiento;
import com.lysandri.domain.model.EstadoOrden;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFinancialTransactionServiceTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    private com.lysandri.config.CuentasBancariasProperties cuentasBancariasProperties;

    private OrderFinancialTransactionService financialTransactionService;

    private Orden ordenPendiente;
    private Orden ordenPagada;

    @BeforeEach
    void setUp() {
        cuentasBancariasProperties = new com.lysandri.config.CuentasBancariasProperties();
        var bcpConfig = new com.lysandri.config.CuentasBancariasProperties.CuentaConfig();
        bcpConfig.setBanco("BCP");
        bcpConfig.setTitular("LYSANDRI S.A.C.");
        bcpConfig.setMoneda("PEN");
        bcpConfig.setTipoCuenta("CORRIENTE");
        bcpConfig.setNumeroCuenta("193-98218491-0-28");
        bcpConfig.setCci("002-193-009821849102-18");
        bcpConfig.setActivo(true);
        cuentasBancariasProperties.setBcp(bcpConfig);

        financialTransactionService = new OrderFinancialTransactionService(
                orderRepository,
                null,
                null,
                null,
                cuentasBancariasProperties
        );

        ordenPendiente = Orden.builder()
                .idOrden(100L)
                .codigoOrden("ORD-TEST-100")
                .estadoOrden(EstadoOrden.PENDIENTE)
                .estadoAprovisionamiento(EstadoAprovisionamiento.PENDIENTE)
                .metodoPago("TRANSFERENCIA")
                .total(new BigDecimal("500.00"))
                .moneda("PEN")
                .items(new ArrayList<>(List.of(
                        DetalleOrden.builder()
                                .idPrograma(10L)
                                .moodleMatriculado(false)
                                .build()
                )))
                .build();

        ordenPagada = Orden.builder()
                .idOrden(101L)
                .codigoOrden("ORD-TEST-101")
                .estadoOrden(EstadoOrden.PAGADO)
                .estadoAprovisionamiento(EstadoAprovisionamiento.PENDIENTE)
                .metodoPago("TRANSFERENCIA")
                .total(new BigDecimal("500.00"))
                .moneda("PEN")
                .moodleMatriculaSincronizada(false)
                .items(new ArrayList<>(List.of(
                        DetalleOrden.builder()
                                .idPrograma(10L)
                                .moodleMatriculado(false)
                                .build()
                )))
                .build();
    }

    @Test
    @DisplayName("Fase 1: Confirmar pago bajo bloqueo pesimista exitoso cambia estado a PAGADO")
    void confirmarPagoFinanciero_exitoso() {
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-100")).thenReturn(Optional.of(ordenPendiente));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        Orden resultado = financialTransactionService.confirmarPagoFinanciero("ORD-TEST-100");

        assertNotNull(resultado);
        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        assertNotNull(resultado.getFechaPago());
        verify(orderRepository).buscarPorCodigoConBloqueo("ORD-TEST-100");
        verify(orderRepository).guardar(ordenPendiente);
    }

    @Test
    @DisplayName("Fase 1: Fallo al adquirir bloqueo pesimista lanza IllegalStateException (sin fallback inseguro)")
    void confirmarPagoFinanciero_falloAdquirirBloqueo_lanzaExcepcion() {
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-NOT-FOUND")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoFinanciero("ORD-NOT-FOUND"));

        assertTrue(ex.getMessage().contains("No se pudo adquirir el bloqueo pesimista exclusivo"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Fase 1: Orden ya PAGADA retorna de forma idempotente sin re-guardar")
    void confirmarPagoFinanciero_ordenYaPagada_retornaIdempotente() {
        ordenPendiente.setEstadoOrden(EstadoOrden.PAGADO);
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-100")).thenReturn(Optional.of(ordenPendiente));

        Orden resultado = financialTransactionService.confirmarPagoFinanciero("ORD-TEST-100");

        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Fase 1: Orden CANCELADA no puede confirmarse y lanza IllegalStateException")
    void confirmarPagoFinanciero_ordenCancelada_lanzaExcepcion() {
        ordenPendiente.setEstadoOrden(EstadoOrden.CANCELADO);
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-100")).thenReturn(Optional.of(ordenPendiente));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoFinanciero("ORD-TEST-100"));

        assertTrue(ex.getMessage().contains("No se puede confirmar el pago de una orden CANCELADA"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Fase 1: Orden en estado FALLIDO no puede confirmarse y lanza IllegalStateException")
    void confirmarPagoFinanciero_ordenFallida_lanzaExcepcion() {
        ordenPendiente.setEstadoOrden(EstadoOrden.FALLIDO);
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-100")).thenReturn(Optional.of(ordenPendiente));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoFinanciero("ORD-TEST-100"));

        assertTrue(ex.getMessage().contains("No se puede confirmar el pago de una orden en estado FALLIDO"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("V7 Fase 1: Reservar aprovisionamiento adquiere lease exclusivo con token y expiración")
    void reservarAprovisionamiento_adquiereLeaseExitoso() {
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-101")).thenReturn(Optional.of(ordenPagada));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoReservaAprovisionamiento reserva = financialTransactionService.reservarAprovisionamiento("ORD-TEST-101");

        assertTrue(reserva.isReservado());
        assertNotNull(reserva.getOwnerToken());
        assertEquals(EstadoAprovisionamiento.PROCESANDO, reserva.getOrden().getEstadoAprovisionamiento());
        assertNotNull(reserva.getOrden().getAprovisionamientoLeaseExpiresAt());
        assertEquals(1, reserva.getOrden().getAprovisionamientoIntentos());
    }

    @Test
    @DisplayName("V7 Fase 1: Reservar aprovisionamiento cuando ya está completado retorna YA_COMPLETADO")
    void reservarAprovisionamiento_yaCompletado_retornaInmediato() {
        ordenPagada.setEstadoAprovisionamiento(EstadoAprovisionamiento.COMPLETADO);
        ordenPagada.setMoodleMatriculaSincronizada(true);
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-101")).thenReturn(Optional.of(ordenPagada));

        ResultadoReservaAprovisionamiento reserva = financialTransactionService.reservarAprovisionamiento("ORD-TEST-101");

        assertTrue(reserva.isYaCompletado());
        assertNull(reserva.getOwnerToken());
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("V7 Fase 1: Reservar aprovisionamiento cuando otro trabajador tiene lease activo retorna EN_PROCESO")
    void reservarAprovisionamiento_otroTrabajadorActivo_retornaEnProceso() {
        ordenPagada.setEstadoAprovisionamiento(EstadoAprovisionamiento.PROCESANDO);
        ordenPagada.setAprovisionamientoLeaseToken("token-otro-trabajador");
        ordenPagada.setAprovisionamientoLeaseExpiresAt(OffsetDateTime.now().plusMinutes(4));
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-101")).thenReturn(Optional.of(ordenPagada));

        ResultadoReservaAprovisionamiento reserva = financialTransactionService.reservarAprovisionamiento("ORD-TEST-101");

        assertTrue(reserva.isEnProceso());
        assertNull(reserva.getOwnerToken());
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("V7 Fase 1: Reservar aprovisionamiento recupera lease expirado")
    void reservarAprovisionamiento_leaseExpirado_recuperaLease() {
        ordenPagada.setEstadoAprovisionamiento(EstadoAprovisionamiento.PROCESANDO);
        ordenPagada.setAprovisionamientoLeaseToken("token-antiguo-expirado");
        ordenPagada.setAprovisionamientoLeaseExpiresAt(OffsetDateTime.now().minusMinutes(2));
        ordenPagada.setAprovisionamientoIntentos(1);
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-101")).thenReturn(Optional.of(ordenPagada));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoReservaAprovisionamiento reserva = financialTransactionService.reservarAprovisionamiento("ORD-TEST-101");

        assertTrue(reserva.isReservado());
        assertNotEquals("token-antiguo-expirado", reserva.getOwnerToken());
        assertEquals(2, reserva.getOrden().getAprovisionamientoIntentos());
        verify(orderRepository).guardar(any());
    }

    @Test
    @DisplayName("V7 Fase 1: Completar aprovisionamiento exitoso libera lease y marca COMPLETADO")
    void completarAprovisionamiento_exitoso() {
        ordenPagada.setEstadoAprovisionamiento(EstadoAprovisionamiento.PROCESANDO);
        ordenPagada.setAprovisionamientoLeaseToken("token-valido-123");
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-101")).thenReturn(Optional.of(ordenPagada));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        Orden resultado = financialTransactionService.completarAprovisionamiento(
                "ORD-TEST-101", "token-valido-123", true, List.of(10L));

        assertNotNull(resultado);
        assertEquals(EstadoAprovisionamiento.COMPLETADO, resultado.getEstadoAprovisionamiento());
        assertTrue(resultado.isMoodleMatriculaSincronizada());
        assertNull(resultado.getAprovisionamientoLeaseToken());
        assertNull(resultado.getAprovisionamientoLeaseExpiresAt());
    }

    @Test
    @DisplayName("V7 Fase 1: Completar aprovisionamiento con token no coincidente lanza IllegalStateException (pérdida de propiedad)")
    void completarAprovisionamiento_perdidaDePropiedad_lanzaExcepcion() {
        ordenPagada.setEstadoAprovisionamiento(EstadoAprovisionamiento.PROCESANDO);
        ordenPagada.setAprovisionamientoLeaseToken("token-nuevo-reasignado");
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-101")).thenReturn(Optional.of(ordenPagada));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.completarAprovisionamiento(
                        "ORD-TEST-101", "token-viejo-perdido", true, List.of(10L)));

        assertTrue(ex.getMessage().contains("Pérdida de propiedad"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("V7 Fase 3: Registrar pago fallido protege órdenes ya confirmadas como PAGADAS ante eventos tardíos")
    void registrarPagoFallidoOExpirado_ordenYaPagada_protegidaDeReversion() {
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-101")).thenReturn(Optional.of(ordenPagada));

        Orden resultado = financialTransactionService.registrarPagoFallidoOExpirado(
                "ORD-TEST-101", "pi_failed_late", EstadoOrden.FALLIDO, "Fallo tardío");

        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("V7 Fase 3: Registrar pago fallido en orden PENDIENTE actualiza a FALLIDO")
    void registrarPagoFallidoOExpirado_ordenPendiente_actualizaAFallido() {
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-100")).thenReturn(Optional.of(ordenPendiente));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        Orden resultado = financialTransactionService.registrarPagoFallidoOExpirado(
                "ORD-TEST-100", "pi_failed_1", EstadoOrden.FALLIDO, "Tarjeta rechazada");

        assertEquals(EstadoOrden.FALLIDO, resultado.getEstadoOrden());
        assertEquals("Tarjeta rechazada", resultado.getNotasAdmin());
        verify(orderRepository).guardar(ordenPendiente);
    }

    @Test
    @DisplayName("V7 Fase 3: Registrar evento expirado en orden PENDIENTE actualiza a CANCELADO")
    void registrarPagoFallidoOExpirado_ordenPendiente_actualizaACancelado() {
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-100")).thenReturn(Optional.of(ordenPendiente));
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        Orden resultado = financialTransactionService.registrarPagoFallidoOExpirado(
                "ORD-TEST-100", "cs_expired_1", EstadoOrden.CANCELADO, "Sesión expirada");

        assertEquals(EstadoOrden.CANCELADO, resultado.getEstadoOrden());
        assertEquals("Sesión expirada", resultado.getNotasAdmin());
        verify(orderRepository).guardar(ordenPendiente);
    }

    @Test
    @DisplayName("Fase 1: Si falla la creación del token de activación o tarea Outbox, se propaga la excepción y no se confirma el pago")
    void confirmarPagoFinanciero_falloEnActivacion_noTragaExcepcionYFalla() {
        com.lysandri.domain.ports.out.UserRepositoryPort userRepo = mock(com.lysandri.domain.ports.out.UserRepositoryPort.class);
        com.lysandri.domain.ports.out.UserActivationPort actPort = mock(com.lysandri.domain.ports.out.UserActivationPort.class);
        ActivationOutboxService outbox = mock(ActivationOutboxService.class);

        OrderFinancialTransactionService serviceConActivacion = new OrderFinancialTransactionService(
                orderRepository, userRepo, actPort, outbox
        );

        ordenPendiente.setIdUser(55L);
        when(orderRepository.buscarPorCodigoConBloqueo("ORD-TEST-100")).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.model.Usuario usuarioInactivo = com.lysandri.domain.model.Usuario.builder()
                .idUser(55L)
                .email("inactivo@empresa.com")
                .nombres("Inactivo")
                .activo(false)
                .pendienteActivacion(true)
                .build();
        when(userRepo.buscarPorId(55L)).thenReturn(Optional.of(usuarioInactivo));
        when(actPort.crearTokenActivacion(55L)).thenThrow(new RuntimeException("Database error generating token"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                serviceConActivacion.confirmarPagoFinanciero("ORD-TEST-100"));

        assertEquals("Database error generating token", ex.getMessage());
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Aprobacion exitosa de transferencia bancaria actualiza estado y campos de auditoria")
    void confirmarPagoManualFinanciero_exitoso() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));
        when(orderRepository.buscarPorAbonoBancario(any(), any(), any())).thenReturn(Optional.empty());
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", "Nota admin", "Observacion voucher ok", "admin@lysandri.com"
                );

        Orden resultado = financialTransactionService.confirmarPagoManualFinanciero(100L, command);

        assertNotNull(resultado);
        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        assertEquals(new BigDecimal("500.00"), resultado.getMontoVerificado());
        assertEquals("PEN", resultado.getMonedaVerificada());
        assertEquals("BCP", resultado.getBancoDestino());
        assertEquals("19398218491028", resultado.getCuentaDestino());
        assertEquals("BCP984128", resultado.getNumeroOperacion());
        assertEquals("admin@lysandri.com", resultado.getAprobadoPor());
        assertNotNull(resultado.getFechaAprobacion());
        verify(orderRepository).guardar(ordenPendiente);
    }

    @Test
    @DisplayName("Manual Transfer: Orden de pasarela online izipay no puede ser confirmada por ruta manual")
    void confirmarPagoManualFinanciero_ordenOnlineIzipay_rechazada() {
        ordenPendiente.setMetodoPago("IZIPAY");
        ordenPendiente.setIzipayTransactionId("izi_tx_123");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("pasarela"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Orden CANCELADA no puede ser aprobada manualmente")
    void confirmarPagoManualFinanciero_ordenCancelada_rechazada() {
        ordenPendiente.setEstadoOrden(EstadoOrden.CANCELADO);
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().toUpperCase().contains("CANCELAD"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Discrepancia en monto verificado es rechazada")
    void confirmarPagoManualFinanciero_montoIncompatible_lanzaExcepcion() {
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BCP", "193-98218491-0-28", new BigDecimal("400.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("no coincide con el total"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Discrepancia en moneda verificada es rechazada")
    void confirmarPagoManualFinanciero_monedaIncompatible_lanzaExcepcion() {
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "USD",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("moneda"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Fecha de abono en el futuro es rechazada")
    void confirmarPagoManualFinanciero_fechaFutura_lanzaExcepcion() {
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().plusDays(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().toLowerCase().contains("futur"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Depósito bancario ya aplicado en otra orden PAGADA es rechazado")
    void confirmarPagoManualFinanciero_depositoDuplicadoEnOtraOrden_lanzaExcepcion() {
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        Orden otraOrdenPagada = Orden.builder()
                .idOrden(200L)
                .codigoOrden("ORD-OTRA-200")
                .estadoOrden(EstadoOrden.PAGADO)
                .bancoDestino("BCP")
                .cuentaDestino("193-98218491-0-28")
                .numeroOperacion("BCP-984128")
                .build();
        when(orderRepository.buscarPorAbonoBancario(any(), any(), any()))
                .thenReturn(Optional.of(otraOrdenPagada));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().toLowerCase().contains("ya fue utilizado"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Repeticion identica sobre la misma orden ya PAGADA es idempotente")
    void confirmarPagoManualFinanciero_repeticionIdentica_esIdempotente() {
        ordenPagada.setBancoDestino("BCP");
        ordenPagada.setCuentaDestino("193-98218491-0-28");
        ordenPagada.setNumeroOperacion("BCP-984128");
        ordenPagada.setMontoVerificado(new BigDecimal("500.00"));
        ordenPagada.setMonedaVerificada("PEN");
        ordenPagada.setMoneda("PEN");

        when(orderRepository.buscarPorIdConBloqueo(101L)).thenReturn(Optional.of(ordenPagada));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        Orden resultado = financialTransactionService.confirmarPagoManualFinanciero(101L, command);

        assertSame(ordenPagada, resultado);
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Repeticion con datos contradictorios en orden ya PAGADA es rechazada")
    void confirmarPagoManualFinanciero_repeticionIncompatible_lanzaExcepcion() {
        ordenPagada.setBancoDestino("BCP");
        ordenPagada.setCuentaDestino("193-98218491-0-28");
        ordenPagada.setNumeroOperacion("BCP-984128");
        ordenPagada.setMontoVerificado(new BigDecimal("500.00"));
        ordenPagada.setMonedaVerificada("PEN");
        ordenPagada.setMoneda("PEN");

        when(orderRepository.buscarPorIdConBloqueo(101L)).thenReturn(Optional.of(ordenPagada));

        // Intento de reutilizar pero con número de operación diferente
        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-DISTINTO-999", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(101L, command));

        assertTrue(ex.getMessage().contains("abono diferente"));
        verify(orderRepository, never()).guardar(any());
    }


    @Test
    @DisplayName("Manual Transfer: Variante de banco (BANCO DE CREDITO) y CCI resuelven a destino canonico BCP y numero de cuenta")
    void confirmarPagoManualFinanciero_varianteBancoDeCredito_resuelveACanonicoBCP() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));
        when(orderRepository.buscarAbonosHistoricosPorOperacion("BCP984128")).thenReturn(List.of());
        when(orderRepository.buscarPorAbonoBancario("BCP", "19398218491028", "BCP984128")).thenReturn(Optional.empty());
        when(orderRepository.guardar(any(Orden.class))).thenAnswer(i -> i.getArgument(0));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "BCP-984128", "BANCO DE CREDITO", "002-193-009821849102-18", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", "Nota admin", "Observacion voucher ok", "admin@lysandri.com"
                );

        Orden resultado = financialTransactionService.confirmarPagoManualFinanciero(100L, command);

        assertNotNull(resultado);
        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        assertEquals("BCP", resultado.getBancoDestino());
        assertEquals("19398218491028", resultado.getCuentaDestino());
        assertEquals("BCP984128", resultado.getNumeroOperacion());
        verify(orderRepository).buscarPorAbonoBancario("BCP", "19398218491028", "BCP984128");
    }

    @Test
    @DisplayName("Manual Transfer: Metodo de pago incompatible con entidad financiera es rechazado")
    void confirmarPagoManualFinanciero_metodoIncompatibleConEntidad_lanzaExcepcion() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "OP-12345", "YAPE", "999888777", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("incompatible"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Manual Transfer: Entidad financiera no autorizada es rechazada")
    void confirmarPagoManualFinanciero_entidadNoAutorizada_lanzaExcepcion() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "OP-12345", "SCOTIABANK", "1234567890", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(2), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("no reconocido o no autorizado"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Historico: Reutilizar abono historico registrado con alias (BANCO DE CREDITO vs BCP) es rechazado")
    void confirmarPagoManualFinanciero_reutilizarAbonoHistoricoConAliasDeBanco_lanzaExcepcion() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        Orden ordenHistorica = Orden.builder()
                .idOrden(80L)
                .codigoOrden("ORD-HIST-080")
                .estadoOrden(EstadoOrden.PAGADO)
                .bancoDestino("BANCO DE CREDITO")
                .cuentaDestino("19398218491028")
                .numeroOperacion("OP-009988")
                .build();

        when(orderRepository.buscarAbonosHistoricosPorOperacion("OP009988")).thenReturn(List.of(ordenHistorica));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "OP009988", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("ya fue utilizado para pagar la orden ORD-HIST-080"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Historico: Reutilizar abono historico registrado con CCI en vez de cuenta simple es rechazado")
    void confirmarPagoManualFinanciero_reutilizarAbonoHistoricoConCCI_lanzaExcepcion() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        Orden ordenHistorica = Orden.builder()
                .idOrden(81L)
                .codigoOrden("ORD-HIST-081")
                .estadoOrden(EstadoOrden.PAGADO)
                .bancoDestino("BCP")
                .cuentaDestino("002-193-009821849102-18")
                .numeroOperacion("OP-009988")
                .build();

        when(orderRepository.buscarAbonosHistoricosPorOperacion("OP009988")).thenReturn(List.of(ordenHistorica));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "OP009988", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("ya fue utilizado para pagar la orden ORD-HIST-081"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Historico: Reutilizar abono con formato distinto en numero de operacion preservando ceros a la izquierda")
    void confirmarPagoManualFinanciero_reutilizarAbonoHistoricoConFormatoOperacion_lanzaExcepcion() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        Orden ordenHistorica = Orden.builder()
                .idOrden(82L)
                .codigoOrden("ORD-HIST-082")
                .estadoOrden(EstadoOrden.PAGADO)
                .bancoDestino("BCP")
                .cuentaDestino("19398218491028")
                .numeroOperacion(" 0012345 ")
                .build();

        when(orderRepository.buscarAbonosHistoricosPorOperacion("0012345")).thenReturn(List.of(ordenHistorica));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "001-2345", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("ya fue utilizado para pagar la orden ORD-HIST-082"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Historico: Colision entre dos registros historicos convergentes bloquea aprobacion y audita anomalia")
    void confirmarPagoManualFinanciero_colisionRegistrosHistoricosConvergentes_bloqueaYAudita() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        Orden historica1 = Orden.builder()
                .idOrden(83L)
                .codigoOrden("ORD-HIST-083")
                .estadoOrden(EstadoOrden.PAGADO)
                .bancoDestino("BCP")
                .cuentaDestino("19398218491028")
                .numeroOperacion("007788")
                .build();

        Orden historica2 = Orden.builder()
                .idOrden(84L)
                .codigoOrden("ORD-HIST-084")
                .estadoOrden(EstadoOrden.PAGADO)
                .bancoDestino("BANCO DE CREDITO")
                .cuentaDestino("002-193-009821849102-18")
                .numeroOperacion("007-788")
                .build();

        when(orderRepository.buscarAbonosHistoricosPorOperacion("007788")).thenReturn(List.of(historica1, historica2));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "007788", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("Conflicto por ambigüedad contable"));
        assertTrue(ex.getMessage().contains("ORD-HIST-083"));
        assertTrue(ex.getMessage().contains("ORD-HIST-084"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Historico: Destino no resuelto con certeza (datos ambiguos o desconocidos) es bloqueado con mensaje claro")
    void confirmarPagoManualFinanciero_destinoAmbiguoODesconocido_bloqueaConMensaje() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "007788", "BANCO_DESCONOCIDO_XYZ", "123456", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("No se puede resolver con certeza el destino"));
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Historico: Aprobacion legitima de repeticion (idempotente) de la misma orden ya aprobada aun con variante")
    void confirmarPagoManualFinanciero_aprobacionLegitimaRepeticionConVarianteFormato_idempotente() {
        ordenPagada.setBancoDestino("BANCO DE CREDITO");
        ordenPagada.setCuentaDestino("002-193-009821849102-18");
        ordenPagada.setNumeroOperacion("001-2345");
        ordenPagada.setMontoVerificado(new BigDecimal("500.00"));
        ordenPagada.setMonedaVerificada("PEN");

        when(orderRepository.buscarPorIdConBloqueo(101L)).thenReturn(Optional.of(ordenPagada));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "0012345", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        Orden resultado = financialTransactionService.confirmarPagoManualFinanciero(101L, command);

        assertNotNull(resultado);
        assertEquals(101L, resultado.getIdOrden());
        assertEquals(EstadoOrden.PAGADO, resultado.getEstadoOrden());
        verify(orderRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Historico: Concurrencia al persistir abono es capturada como conflicto de concurrencia")
    void confirmarPagoManualFinanciero_concurrenciaEnPersistencia_lanzaConflictoConcurrencia() {
        ordenPendiente.setMetodoPago("TRANSFERENCIA");
        when(orderRepository.buscarPorIdConBloqueo(100L)).thenReturn(Optional.of(ordenPendiente));
        when(orderRepository.buscarAbonosHistoricosPorOperacion("0012345")).thenReturn(List.of());
        when(orderRepository.buscarPorAbonoBancario("BCP", "19398218491028", "0012345")).thenReturn(Optional.empty());
        when(orderRepository.guardar(any(Orden.class))).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("uq_ordenes_abono_bancario_verificado"));

        com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand command =
                new com.lysandri.domain.ports.in.BuyCourseUseCase.ConfirmarPagoManualCommand(
                        "0012345", "BCP", "193-98218491-0-28", new BigDecimal("500.00"), "PEN",
                        OffsetDateTime.now().minusHours(1), "TRANSFERENCIA", null, null, "admin@lysandri.com"
                );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                financialTransactionService.confirmarPagoManualFinanciero(100L, command));

        assertTrue(ex.getMessage().contains("Conflicto de concurrencia: el abono bancario ya fue registrado"));
    }
}

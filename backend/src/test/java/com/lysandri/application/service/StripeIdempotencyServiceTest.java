package com.lysandri.application.service;

import com.lysandri.adapters.out.database.entity.StripeProcessedEventEntity;
import com.lysandri.adapters.out.database.repository.SpringDataStripeEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeIdempotencyServiceTest {

    @Mock
    private SpringDataStripeEventRepository stripeEventRepository;

    @InjectMocks
    private StripeIdempotencyService stripeIdempotencyService;

    @Test
    @DisplayName("Nuevo evento se registra en estado PROCESANDO con lease activo y retorna INICIADO")
    void iniciarProcesamiento_nuevoEvento_retornaAdquirido() {
        when(stripeEventRepository.findByIdEvento("evt_100")).thenReturn(Optional.empty());

        StripeIdempotencyService.ResultadoAdquisicion resultado = stripeIdempotencyService.iniciarProcesamiento(
                "evt_100", "checkout.session.completed", "cs_100", "pi_100", "ORD-100"
        );

        assertEquals(StripeIdempotencyService.EstadoEventoResultado.INICIADO, resultado.getEstado());
        assertNotNull(resultado.getOwnerToken());
        assertNotNull(resultado.getLeaseExpiresAt());

        ArgumentCaptor<StripeProcessedEventEntity> captor = ArgumentCaptor.forClass(StripeProcessedEventEntity.class);
        verify(stripeEventRepository).saveAndFlush(captor.capture());

        StripeProcessedEventEntity guardado = captor.getValue();
        assertEquals("evt_100", guardado.getIdEvento());
        assertEquals("PROCESANDO", guardado.getEstado());
        assertEquals("cs_100", guardado.getStripeSessionId());
        assertEquals("pi_100", guardado.getStripePaymentIntentId());
        assertEquals("ORD-100", guardado.getCodigoOrden());
        assertNotNull(guardado.getOwnerToken());
        assertNotNull(guardado.getLeaseExpiresAt());
    }

    @Test
    @DisplayName("Evento ya COMPLETADO retorna YA_COMPLETADO y no vuelve a guardar")
    void iniciarProcesamiento_eventoYaCompletado_retornaYaCompletado() {
        StripeProcessedEventEntity eventoCompletado = StripeProcessedEventEntity.builder()
                .idEvento("evt_200")
                .estado("COMPLETADO")
                .fechaRegistro(OffsetDateTime.now())
                .build();

        when(stripeEventRepository.findByIdEvento("evt_200")).thenReturn(Optional.of(eventoCompletado));

        StripeIdempotencyService.ResultadoAdquisicion resultado = stripeIdempotencyService.iniciarProcesamiento(
                "evt_200", "checkout.session.completed", "cs_200", null, "ORD-200"
        );

        assertEquals(StripeIdempotencyService.EstadoEventoResultado.YA_COMPLETADO, resultado.getEstado());
        verify(stripeEventRepository, never()).saveAndFlush(any());
        verify(stripeEventRepository, never()).adquirirLeaseEvento(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Evento en estado PROCESANDO con lease vigente retorna EN_PROCESO para evitar ejecuciones concurrentes")
    void iniciarProcesamiento_eventoEnProcesoConLeaseVigente_retornaEnProceso() {
        StripeProcessedEventEntity eventoEnProceso = StripeProcessedEventEntity.builder()
                .idEvento("evt_300")
                .estado("PROCESANDO")
                .ownerToken("token-activo-otro-hilo")
                .leaseExpiresAt(OffsetDateTime.now().plusMinutes(2))
                .build();

        when(stripeEventRepository.findByIdEvento("evt_300")).thenReturn(Optional.of(eventoEnProceso));

        StripeIdempotencyService.ResultadoAdquisicion resultado = stripeIdempotencyService.iniciarProcesamiento(
                "evt_300", "checkout.session.completed", "cs_300", null, "ORD-300"
        );

        assertEquals(StripeIdempotencyService.EstadoEventoResultado.EN_PROCESO, resultado.getEstado());
        verify(stripeEventRepository, never()).adquirirLeaseEvento(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Evento PROCESANDO pero con lease expirado permite recuperación y adquiere nuevo lease")
    void iniciarProcesamiento_eventoEnProcesoConLeaseExpirado_adquiereLease() {
        StripeProcessedEventEntity eventoExpirado = StripeProcessedEventEntity.builder()
                .idEvento("evt_350")
                .estado("PROCESANDO")
                .ownerToken("token-antiguo")
                .leaseExpiresAt(OffsetDateTime.now().minusMinutes(5)) // Expirado hace 5 min
                .build();

        when(stripeEventRepository.findByIdEvento("evt_350")).thenReturn(Optional.of(eventoExpirado));
        when(stripeEventRepository.adquirirLeaseEvento(eq("evt_350"), anyString(), any(), any())).thenReturn(1);

        StripeIdempotencyService.ResultadoAdquisicion resultado = stripeIdempotencyService.iniciarProcesamiento(
                "evt_350", "checkout.session.completed", "cs_350", null, "ORD-350"
        );

        assertEquals(StripeIdempotencyService.EstadoEventoResultado.INICIADO, resultado.getEstado());
        assertNotNull(resultado.getOwnerToken());
        verify(stripeEventRepository).adquirirLeaseEvento(eq("evt_350"), eq(resultado.getOwnerToken()), any(), any());
    }

    @Test
    @DisplayName("Evento previamente FALLIDO permite recuperación mediante adquisición atómica de lease")
    void iniciarProcesamiento_eventoPrevioFallido_permiteReintento() {
        StripeProcessedEventEntity eventoFallido = StripeProcessedEventEntity.builder()
                .idEvento("evt_400")
                .estado("FALLIDO")
                .mensajeError("Timeout de conexión previo")
                .build();

        when(stripeEventRepository.findByIdEvento("evt_400")).thenReturn(Optional.of(eventoFallido));
        when(stripeEventRepository.adquirirLeaseEvento(eq("evt_400"), anyString(), any(), any())).thenReturn(1);

        StripeIdempotencyService.ResultadoAdquisicion resultado = stripeIdempotencyService.iniciarProcesamiento(
                "evt_400", "checkout.session.completed", "cs_400", null, "ORD-400"
        );

        assertEquals(StripeIdempotencyService.EstadoEventoResultado.INICIADO, resultado.getEstado());
        assertNotNull(resultado.getOwnerToken());
        verify(stripeEventRepository).adquirirLeaseEvento(eq("evt_400"), eq(resultado.getOwnerToken()), any(), any());
    }

    @Test
    @DisplayName("Violación de restricción UNIQUE por concurrencia retorna EN_PROCESO de forma segura")
    void iniciarProcesamiento_conflictoConcurrencia_retornaEnProceso() {
        when(stripeEventRepository.findByIdEvento("evt_500")).thenReturn(Optional.empty());
        when(stripeEventRepository.saveAndFlush(any(StripeProcessedEventEntity.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key violation"));

        StripeIdempotencyService.ResultadoAdquisicion resultado = stripeIdempotencyService.iniciarProcesamiento(
                "evt_500", "checkout.session.completed", "cs_500", null, "ORD-500"
        );

        assertEquals(StripeIdempotencyService.EstadoEventoResultado.EN_PROCESO, resultado.getEstado());
    }

    @Test
    @DisplayName("marcarCompletado con token válido actualiza a COMPLETADO y retorna true")
    void marcarCompletado_conTokenValido_retornaTrue() {
        when(stripeEventRepository.marcarCompletadoConToken(eq("evt_600"), eq("token-valido"), eq(45L), eq("ORD-45"), eq("cs_600"), eq("pi_600"), any()))
                .thenReturn(1);

        boolean completado = stripeIdempotencyService.marcarCompletado("evt_600", "token-valido", 45L, "ORD-45", "cs_600", "pi_600");

        assertTrue(completado);
    }

    @Test
    @DisplayName("marcarCompletado con token inválido o lease transferido retorna false impidiendo sobreescritura")
    void marcarCompletado_conTokenInvalidoOLeaseExpirado_retornaFalse() {
        when(stripeEventRepository.marcarCompletadoConToken(eq("evt_600"), eq("token-antiguo-expirado"), any(), any(), any(), any(), any()))
                .thenReturn(0);

        boolean completado = stripeIdempotencyService.marcarCompletado("evt_600", "token-antiguo-expirado", 45L, "ORD-45", "cs_600", "pi_600");

        assertFalse(completado);
    }

    @Test
    @DisplayName("marcarFallido con token válido actualiza a FALLIDO y retorna true")
    void marcarFallido_conTokenValido_retornaTrue() {
        when(stripeEventRepository.marcarFallidoConToken(eq("evt_700"), eq("token-valido"), eq("Fallo al enrolar"), any()))
                .thenReturn(1);

        boolean fallido = stripeIdempotencyService.marcarFallido("evt_700", "token-valido", "Fallo al enrolar");

        assertTrue(fallido);
    }
}

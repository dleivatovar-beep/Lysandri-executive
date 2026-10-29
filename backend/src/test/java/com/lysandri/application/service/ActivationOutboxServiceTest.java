package com.lysandri.application.service;

import com.lysandri.adapters.out.database.entity.ActivationOutboxTaskEntity;
import com.lysandri.adapters.out.database.entity.UsuarioActivacionTokenEntity;
import com.lysandri.adapters.out.database.repository.SpringDataActivationOutboxTaskRepository;
import com.lysandri.adapters.out.database.repository.SpringDataUsuarioActivacionTokenRepository;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.security.ActivationCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias: ActivationOutboxService (Reserva Atómica, Transacciones, Desacoplamiento SMTP)")
class ActivationOutboxServiceTest {

    @Mock
    private SpringDataActivationOutboxTaskRepository outboxRepository;

    @Mock
    private SpringDataUsuarioActivacionTokenRepository tokenRepository;

    @Mock
    private NotificationPort notificationPort;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ActivationCryptoService cryptoService;
    private ActivationOutboxService outboxService;

    private static final String ACTIVATION_KEY = "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0";

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        cryptoService = new ActivationCryptoService(ACTIVATION_KEY);
        outboxService = new ActivationOutboxService(
                outboxRepository,
                tokenRepository,
                cryptoService,
                notificationPort,
                transactionManager
        );
    }

    @Test
    @DisplayName("Registro de intención: guarda enlace cifrado con AES-256-GCM y no en texto claro")
    void crearTareaActivacion_cifraEnlaceYPersisteEnBase() {
        String plainToken = "plain_secret_token_123";
        String claveIdempotencia = "ACTIVACION_ORDEN_100";

        when(outboxRepository.findByClaveIdempotencia(claveIdempotencia)).thenReturn(Optional.empty());
        when(outboxRepository.save(any(ActivationOutboxTaskEntity.class))).thenAnswer(i -> {
            ActivationOutboxTaskEntity entity = i.getArgument(0);
            entity.setIdTarea(1L);
            return entity;
        });

        ActivationOutboxTaskEntity task = outboxService.crearTareaActivacion(
                100L,
                "ORD-100",
                50L,
                10L,
                plainToken,
                "cliente@empresa.com",
                "Carlos Cliente",
                claveIdempotencia
        );

        assertNotNull(task);
        assertEquals(100L, task.getIdOrden());
        assertEquals("ORD-100", task.getCodigoOrden());
        assertEquals(50L, task.getIdUser());
        assertEquals(10L, task.getIdToken());
        assertEquals("PENDIENTE", task.getEstado());
        assertNotNull(task.getEnlaceCifrado());
        assertFalse(task.getEnlaceCifrado().contains(plainToken), "El enlace no debe guardarse en claro");

        // Desencriptar para verificar simetría y autenticidad del cifrado
        String desencriptado = cryptoService.decrypt(task.getEnlaceCifrado());
        assertTrue(desencriptado.contains("token=" + plainToken));
    }

    @Test
    @DisplayName("Idempotencia: confirmaciones repetidas no duplican la tarea para el mismo evento")
    void crearTareaActivacion_llamadasRepetidas_retornaTareaExistenteSinDuplicar() {
        String claveIdempotencia = "ACTIVACION_ORDEN_100";
        ActivationOutboxTaskEntity existente = ActivationOutboxTaskEntity.builder()
                .idTarea(1L)
                .idOrden(100L)
                .idUser(50L)
                .claveIdempotencia(claveIdempotencia)
                .estado("PENDIENTE")
                .build();
        when(outboxRepository.findByClaveIdempotencia(claveIdempotencia)).thenReturn(Optional.of(existente));

        ActivationOutboxTaskEntity resultado = outboxService.crearTareaActivacion(
                100L,
                "ORD-100",
                50L,
                10L,
                "plain_token_123",
                "cliente@empresa.com",
                "Carlos Cliente",
                claveIdempotencia
        );

        assertSame(existente, resultado);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("Procesamiento exitoso con reserva atómica: adquiere reserva, despacha SMTP y marca éxito")
    void procesarTareasPendientes_envioExitoso_marcaEnviado() {
        String plainLink = "http://localhost:5173/activar-cuenta?token=plain_token_xyz";
        String cipherLink = cryptoService.encrypt(plainLink);

        ActivationOutboxTaskEntity task = ActivationOutboxTaskEntity.builder()
                .idTarea(1L)
                .idOrden(100L)
                .idUser(50L)
                .idToken(10L)
                .destinatario("cliente@empresa.com")
                .nombreDestinatario("Carlos Cliente")
                .enlaceCifrado(cipherLink)
                .claveIdempotencia("ACTIVACION_ORDEN_100")
                .intentos(0)
                .maxIntentos(5)
                .estado("PENDIENTE")
                .proximoIntento(OffsetDateTime.now().minusMinutes(1))
                .build();

        UsuarioActivacionTokenEntity tokenVigente = UsuarioActivacionTokenEntity.builder()
                .idToken(10L)
                .idUser(50L)
                .token("sha256_hash_in_db")
                .usado(false)
                .revocado(false)
                .fechaExpiracion(OffsetDateTime.now().plusHours(24))
                .build();

        when(outboxRepository.findPendingTasksForProcessing(any(OffsetDateTime.class))).thenReturn(List.of(task));
        when(outboxRepository.reservarTareaAtomicamente(eq(1L), anyString(), any(), any())).thenReturn(1);
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(task));
        when(tokenRepository.findById(10L)).thenReturn(Optional.of(tokenVigente));
        when(outboxRepository.marcarExitoSiConservaReserva(eq(1L), anyString(), any())).thenReturn(1);

        int procesadas = outboxService.procesarTareasPendientes();

        assertEquals(1, procesadas);
        verify(outboxRepository).reservarTareaAtomicamente(eq(1L), anyString(), any(), any());
        verify(notificationPort).enviarEnlaceActivacion(eq("cliente@empresa.com"), eq("Carlos Cliente"), eq(plainLink), startsWith("attempt-1-"));
        verify(outboxRepository).marcarExitoSiConservaReserva(eq(1L), anyString(), any());
    }

    @Test
    @DisplayName("Concurrencia: si otro trabajador gana la reserva atómica, este trabajador aborta inmediatamente")
    void procesarTareasPendientes_siReservaFalla_otroWorkerGano_noDespachaSmtp() {
        ActivationOutboxTaskEntity task = ActivationOutboxTaskEntity.builder()
                .idTarea(5L)
                .estado("PENDIENTE")
                .proximoIntento(OffsetDateTime.now().minusMinutes(1))
                .build();

        when(outboxRepository.findPendingTasksForProcessing(any(OffsetDateTime.class))).thenReturn(List.of(task));
        // Simular que otro worker actualizó la fila primero (0 filas afectadas)
        when(outboxRepository.reservarTareaAtomicamente(eq(5L), anyString(), any(), any())).thenReturn(0);

        int procesadas = outboxService.procesarTareasPendientes();

        assertEquals(0, procesadas);
        verify(notificationPort, never()).enviarEnlaceActivacion(any(), any(), any());
        verify(outboxRepository, never()).marcarExitoSiConservaReserva(any(), any(), any());
    }

    @Test
    @DisplayName("Fallo inicial SMTP: registra fallo y reprograma reintento conservando la reserva")
    void procesarTareasPendientes_falloSmtp_reprogramaReintento() {
        String plainLink = "http://localhost:5173/activar-cuenta?token=plain_token_xyz";
        String cipherLink = cryptoService.encrypt(plainLink);

        ActivationOutboxTaskEntity task = ActivationOutboxTaskEntity.builder()
                .idTarea(2L)
                .idToken(11L)
                .destinatario("error@empresa.com")
                .nombreDestinatario("Usuario Error")
                .enlaceCifrado(cipherLink)
                .intentos(0)
                .maxIntentos(5)
                .estado("PENDIENTE")
                .build();

        UsuarioActivacionTokenEntity tokenVigente = UsuarioActivacionTokenEntity.builder()
                .idToken(11L)
                .usado(false)
                .revocado(false)
                .fechaExpiracion(OffsetDateTime.now().plusHours(1))
                .build();

        when(outboxRepository.findPendingTasksForProcessing(any(OffsetDateTime.class))).thenReturn(List.of(task));
        when(outboxRepository.reservarTareaAtomicamente(eq(2L), anyString(), any(), any())).thenReturn(1);
        when(outboxRepository.findById(2L)).thenReturn(Optional.of(task));
        when(tokenRepository.findById(11L)).thenReturn(Optional.of(tokenVigente));

        doThrow(new RuntimeException("SMTP connection timed out"))
                .when(notificationPort).enviarEnlaceActivacion(anyString(), anyString(), anyString());

        when(outboxRepository.registrarFalloSiConservaReserva(eq(2L), anyString(), eq("REINTENTO"), eq(1), any(), any(), any()))
                .thenReturn(1);

        int procesadas = outboxService.procesarTareasPendientes();

        assertEquals(0, procesadas);
        verify(outboxRepository).registrarFalloSiConservaReserva(eq(2L), anyString(), eq("REINTENTO"), eq(1), any(), any(), any());
    }

    @Test
    @DisplayName("Protección de renovación de enlace: si el token fue revocado o expiró, cancela sin enviar enlace obsoleto")
    void procesarTareasPendientes_tokenRevocadoOExpirado_cancelaTareaSinEnviar() {
        ActivationOutboxTaskEntity task = ActivationOutboxTaskEntity.builder()
                .idTarea(4L)
                .idToken(20L)
                .estado("PENDIENTE")
                .build();

        UsuarioActivacionTokenEntity tokenRevocado = UsuarioActivacionTokenEntity.builder()
                .idToken(20L)
                .usado(false)
                .revocado(true)
                .fechaExpiracion(OffsetDateTime.now().plusHours(24))
                .build();

        when(outboxRepository.findPendingTasksForProcessing(any(OffsetDateTime.class))).thenReturn(List.of(task));
        when(outboxRepository.reservarTareaAtomicamente(eq(4L), anyString(), any(), any())).thenReturn(1);
        when(outboxRepository.findById(4L)).thenReturn(Optional.of(task));
        when(tokenRepository.findById(20L)).thenReturn(Optional.of(tokenRevocado));

        outboxService.procesarTareasPendientes();

        verify(notificationPort, never()).enviarEnlaceActivacion(any(), any(), any());
        verify(outboxRepository).marcarCanceladoSiConservaReserva(eq(4L), anyString(), any());
    }
}

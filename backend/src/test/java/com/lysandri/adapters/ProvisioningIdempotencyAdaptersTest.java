package com.lysandri.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.adapters.out.billing.BillingServiceAdapter;
import com.lysandri.adapters.out.database.entity.ComprobantePagoEntity;
import com.lysandri.adapters.out.database.entity.NotificacionLogEntity;
import com.lysandri.adapters.out.database.repository.SpringDataComprobantePagoRepository;
import com.lysandri.adapters.out.database.repository.SpringDataNotificacionLogRepository;
import com.lysandri.adapters.out.moodle.MoodleServiceAdapter;
import com.lysandri.adapters.out.notification.EmailNotificationAdapter;
import com.lysandri.domain.model.ComprobantePago;
import com.lysandri.domain.model.EstadoComprobante;
import com.lysandri.domain.model.Orden;
import com.lysandri.domain.model.TipoComprobante;
import com.lysandri.domain.ports.out.CorrelativoComprobantePort;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProvisioningIdempotencyAdaptersTest {

    // --- Moodle Tests ---
    private MoodleServiceAdapter moodleServiceAdapter;
    private ObjectMapper objectMapper;

    // --- Billing Tests ---
    @Mock
    private SpringDataComprobantePagoRepository comprobanteRepository;

        @Mock
        private CorrelativoComprobantePort correlativoComprobantePort;

    @InjectMocks
    private BillingServiceAdapter billingServiceAdapter;

    // --- Notification Tests ---
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringDataNotificacionLogRepository notificacionLogRepository;
    @InjectMocks
    private EmailNotificationAdapter emailNotificationAdapter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        moodleServiceAdapter = spy(new MoodleServiceAdapter(
                "https://moodle.lysandri.com",
                "fake-token",
                org.springframework.web.reactive.function.client.WebClient.builder(),
                objectMapper));
    }

    // =========================================================================
    // 1. MOODLE IDEMPOTENCY & REMOTE VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("Moodle: cuando falla la llamada a matricular pero el usuario ya está matriculado remotamente, recupera de forma idempotente")
    void moodle_recuperaIdempotenteCuandoUsuarioYaMatriculadoRemoto() {
        doThrow(new RuntimeException("Moodle timeout 504"))
                .when(moodleServiceAdapter).executeMoodlePost(argThat(map -> "enrol_manual_enrol_users".equals(map.getFirst("wsfunction"))));

        doReturn(true).when(moodleServiceAdapter).estaUsuarioMatriculado(55L, 101L);

        assertDoesNotThrow(() -> moodleServiceAdapter.matricularUsuarioEnCurso(55L, 101L, 5));
        verify(moodleServiceAdapter).estaUsuarioMatriculado(55L, 101L);
    }

    @Test
    @DisplayName("Moodle: cuando falla la llamada y el usuario no está matriculado remotamente, propaga excepción para reintento posterior")
    void moodle_fallaDefinitivoCuandoNoEstaMatriculadoRemoto() {
        doThrow(new RuntimeException("Connection refused"))
                .when(moodleServiceAdapter).executeMoodlePost(argThat(map -> "enrol_manual_enrol_users".equals(map.getFirst("wsfunction"))));

        doReturn(false).when(moodleServiceAdapter).estaUsuarioMatriculado(55L, 101L);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                moodleServiceAdapter.matricularUsuarioEnCurso(55L, 101L, 5));

        assertTrue(ex.getMessage().contains("Fallo durante la matrícula en Moodle"));
        verify(moodleServiceAdapter).estaUsuarioMatriculado(55L, 101L);
    }

    // =========================================================================
    // 2. BILLING IDEMPOTENCY & RACE-CONDITION RECOVERY
    // =========================================================================

    @Test
    @DisplayName("Facturación: si ya existe comprobante para la orden, lo retorna sin generar nuevo correlativo")
    void facturacion_comprobanteYaExistente_retornaSinDuplicar() {
        ComprobantePagoEntity entityExistente = ComprobantePagoEntity.builder()
                .idComprobante(1L)
                .idOrden(500L)
                .serie("B001")
                .correlativo(1234)
                .montoTotal(new BigDecimal("799.00"))
                .estado(EstadoComprobante.EMITIDO)
                .tipo(TipoComprobante.BOLETA)
                .build();

        when(comprobanteRepository.findByIdOrden(500L)).thenReturn(Optional.of(entityExistente));

        Orden orden = Orden.builder()
                .idOrden(500L)
                .total(new BigDecimal("799.00"))
                .build();

        ComprobantePago resultado = billingServiceAdapter.emitirComprobante(orden);

        assertNotNull(resultado);
        assertEquals("B001", resultado.getSerie());
        assertEquals(1234, resultado.getCorrelativo());
        verify(comprobanteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Facturación: ante DataIntegrityViolationException por colisión concurrente, recupera comprobante persistido")
    void facturacion_colisionConcurrente_recuperaComprobantePersistido() {
        when(comprobanteRepository.findByIdOrden(501L)).thenReturn(Optional.empty());
        when(correlativoComprobantePort.reservarSiguiente("B001")).thenReturn(201);

        when(comprobanteRepository.save(any(ComprobantePagoEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uq_comprobante_id_orden"));

        ComprobantePagoEntity comprobanteConcurrente = ComprobantePagoEntity.builder()
                .idComprobante(2L)
                .idOrden(501L)
                .serie("B001")
                .correlativo(201)
                .montoTotal(new BigDecimal("899.00"))
                .estado(EstadoComprobante.EMITIDO)
                .tipo(TipoComprobante.BOLETA)
                .build();

        when(comprobanteRepository.findByIdOrden(501L))
                .thenReturn(Optional.empty()) // Primera verificación
                .thenReturn(Optional.of(comprobanteConcurrente)); // Recuperación en el catch

        Orden orden = Orden.builder()
                .idOrden(501L)
                .total(new BigDecimal("899.00"))
                .build();

        ComprobantePago resultado = billingServiceAdapter.emitirComprobante(orden);

        assertNotNull(resultado);
        assertEquals("B001", resultado.getSerie());
        assertEquals(201, resultado.getCorrelativo());
    }

    // =========================================================================
    // 3. PERSISTENT NOTIFICATION TRACKING & DEDUPLICATION
    // =========================================================================

    @Test
    @DisplayName("Notificaciones: omite reenvío si la notificación ya figura como ENVIADO en el log persistente")
    void notificaciones_omiteReenvioSiYaEnviada() {
        NotificacionLogEntity logEnviado = NotificacionLogEntity.builder()
                .idNotificacion(1L)
                .idOrden(600L)
                .destinatario("alumno@empresa.com")
                .claveIdempotencia("ACCESOS_ORDEN_600")
                .estado("ENVIADO")
                .intentos(1)
                .build();

        when(notificacionLogRepository.findByClaveIdempotencia("ACCESOS_ORDEN_600")).thenReturn(Optional.of(logEnviado));

        ComprobantePago comprobante = ComprobantePago.builder().idOrden(600L).build();

        emailNotificationAdapter.enviarAccesosYFactura(
                "alumno@empresa.com", "Juan Perez", "Diplomado", "secret123", comprobante);

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Notificaciones: registra intento y marca ENVIADO cuando el envío es exitoso")
    void notificaciones_registraIntentoYMarcaEnviadoExitoso() {
        ReflectionTestUtils.setField(emailNotificationAdapter, "fromEmail", "no-reply@lysandri.com");
        ReflectionTestUtils.setField(emailNotificationAdapter, "moodleUrl", "https://moodle.lysandri.com");

        when(notificacionLogRepository.findByClaveIdempotencia("ACCESOS_ORDEN_601")).thenReturn(Optional.empty());
        when(notificacionLogRepository.save(any(NotificacionLogEntity.class))).thenAnswer(i -> i.getArgument(0));

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        ComprobantePago comprobante = ComprobantePago.builder()
                .idOrden(601L)
                .serie("B001")
                .correlativo(100)
                .montoTotal(new BigDecimal("500.00"))
                .montoSubtotal(new BigDecimal("423.73"))
                .montoIgv(new BigDecimal("76.27"))
                .moneda("PEN")
                .tipo(TipoComprobante.BOLETA)
                .fechaEmision(LocalDateTime.now())
                .build();

        emailNotificationAdapter.enviarAccesosYFactura(
                "nuevo@empresa.com", "Maria Gomez", "Gestión Estratégica", "pass123", comprobante);

        verify(mailSender).send(mimeMessage);
        verify(notificacionLogRepository, atLeast(2)).save(argThat(entity ->
                "ACCESOS_ORDEN_601".equals(entity.getClaveIdempotencia())
        ));
    }
}

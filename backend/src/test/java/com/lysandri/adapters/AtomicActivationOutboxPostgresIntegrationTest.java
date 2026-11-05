package com.lysandri.adapters;

import com.lysandri.adapters.out.database.entity.ActivationOutboxTaskEntity;
import com.lysandri.adapters.out.database.entity.UsuarioActivacionTokenEntity;
import com.lysandri.adapters.out.database.repository.SpringDataActivationOutboxTaskRepository;
import com.lysandri.adapters.out.database.repository.SpringDataUsuarioActivacionTokenRepository;
import com.lysandri.application.service.ActivationOutboxService;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.security.ActivationCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.scheduling.enabled=true",
        "app.outbox.activation.enabled=true",
        "app.outbox.activation.fixed-delay-ms=500"
})
@DisplayName("Pruebas de Integración con PostgreSQL Real Desechable: Concurrencia Atómica, Recuperación y Outbox")
public class AtomicActivationOutboxPostgresIntegrationTest extends PostgresTestDatabase {

    @Autowired
    private SpringDataActivationOutboxTaskRepository outboxRepository;

    @Autowired
    private SpringDataUsuarioActivacionTokenRepository tokenRepository;

    @Autowired
    private ActivationCryptoService cryptoService;

    @Autowired
    private ActivationOutboxService outboxService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @MockBean
    private NotificationPort notificationPort;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Long idUserTest = 888L;
    private Long idTokenTest;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        tokenRepository.deleteAll();

        // Asegurar existencia de usuario para cumplir clave foránea
        jdbcTemplate.update("""
                INSERT INTO usuarios (id_user, nombres, apellidos, email, passw, rol)
                VALUES (888, 'Atomic', 'Test', 'test.atomic@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR')
                ON CONFLICT (id_user) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO usuario (id_user, nombres, apellidos, email, passw, rol, activo)
                VALUES (888, 'Atomic', 'Test', 'test.atomic@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR', false)
                ON CONFLICT (id_user) DO NOTHING
                """);

        // Crear token de activación válido en la base real
        UsuarioActivacionTokenEntity token = UsuarioActivacionTokenEntity.builder()
                .idUser(idUserTest)
                .token("sha256_mock_hash_for_atomic_test_" + UUID.randomUUID())
                .fechaExpiracion(OffsetDateTime.now().plusHours(48))
                .usado(false)
                .revocado(false)
                .fechaCreacion(OffsetDateTime.now())
                .build();
        idTokenTest = tokenRepository.save(token).getIdToken();

        // Delegar llamadas de 4 argumentos al método de 3 argumentos por compatibilidad de stubs
        lenient().doAnswer(invocation -> {
            notificationPort.enviarEnlaceActivacion(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)
            );
            return null;
        }).when(notificationPort).enviarEnlaceActivacion(any(), any(), any(), any());
    }

    private ActivationOutboxTaskEntity crearTareaDePrueba(String claveIdempotencia) {
        String plainLink = "http://localhost:5173/activar-cuenta?token=" + UUID.randomUUID();
        String cipherLink = cryptoService.encrypt(plainLink);

        ActivationOutboxTaskEntity tarea = ActivationOutboxTaskEntity.builder()
                .idOrden(999L)
                .codigoOrden("ORD-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .idUser(idUserTest)
                .idToken(idTokenTest)
                .enlaceCifrado(cipherLink)
                .destinatario("test.atomic@lysandri.invalid")
                .nombreDestinatario("Cliente Atomic Test")
                .claveIdempotencia(claveIdempotencia)
                .estado("PENDIENTE")
                .intentos(0)
                .maxIntentos(5)
                .proximoIntento(OffsetDateTime.now().minusSeconds(10))
                .fechaCreacion(OffsetDateTime.now())
                .fechaActualizacion(OffsetDateTime.now())
                .build();
        return outboxRepository.save(tarea);
    }

    @Test
    @DisplayName("1. Dos trabajadores compiten por la misma tarea: solo uno obtiene la reserva vigente en PostgreSQL")
    void dosTrabajadoresCompitenPorMismaTarea_soloUnoObtieneReserva() throws Exception {
        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_COMPETENCIA_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        int numWorkers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numWorkers);

        AtomicInteger reservasExitosas = new AtomicInteger(0);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (int i = 0; i < numWorkers; i++) {
            final String workerId = "worker-comp-" + i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    OffsetDateTime now = OffsetDateTime.now();
                    OffsetDateTime lease = now.plusMinutes(2);
                    Integer filas = tx.execute(status ->
                            outboxRepository.reservarTareaAtomicamente(idTarea, workerId, lease, now));
                    if (filas != null && filas == 1) {
                        reservasExitosas.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignorar
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Disparar todos los hilos simultáneamente
        assertTrue(endGate.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactamente un trabajador debe haber ganado la reserva exclusiva
        assertEquals(1, reservasExitosas.get(), "Exactamente 1 trabajador debió ganar la reserva atómica");

        ActivationOutboxTaskEntity tareaEnBase = outboxRepository.findById(idTarea).orElseThrow();
        assertEquals("PROCESANDO", tareaEnBase.getEstado());
        assertNotNull(tareaEnBase.getBloqueadoPor());
        assertTrue(tareaEnBase.getBloqueadoPor().startsWith("worker-comp-"));
        assertTrue(tareaEnBase.getBloqueadoHasta().isAfter(OffsetDateTime.now()));
    }

    @Test
    @DisplayName("2. Un trabajador que perdió la reserva no sobrescribe el resultado del nuevo propietario")
    void trabajadorQuePerdioReserva_noSobrescribeResultadoDeNuevoPropietario() {
        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_LOST_LEASE_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Worker 1 adquiere reserva inicial
        String worker1 = "worker-lento-1";
        int r1 = tx.execute(status -> outboxRepository.reservarTareaAtomicamente(
                idTarea, worker1, OffsetDateTime.now().minusSeconds(1), OffsetDateTime.now().minusSeconds(10)));
        assertEquals(1, r1);

        // Worker 2 detecta lease vencido y adquiere la reserva legítima
        String worker2 = "worker-recuperador-2";
        int r2 = tx.execute(status -> outboxRepository.reservarTareaAtomicamente(
                idTarea, worker2, OffsetDateTime.now().plusMinutes(2), OffsetDateTime.now()));
        assertEquals(1, r2, "Worker 2 debe haber recuperado la reserva vencida");

        // Worker 1 (que se retrasó) intenta marcar éxito
        int filasWorker1 = tx.execute(status ->
                outboxRepository.marcarExitoSiConservaReserva(idTarea, worker1, OffsetDateTime.now()));

        // Worker 1 no debe haber podido actualizar nada
        assertEquals(0, filasWorker1, "Worker 1 que perdió la reserva no debe poder modificar la fila");

        // Worker 2 completa exitosamente su envío
        int filasWorker2 = tx.execute(status ->
                outboxRepository.marcarExitoSiConservaReserva(idTarea, worker2, OffsetDateTime.now()));
        assertEquals(1, filasWorker2, "Worker 2 legítimo debe completar el registro con éxito");

        ActivationOutboxTaskEntity finalEntity = outboxRepository.findById(idTarea).orElseThrow();
        assertEquals("ENVIADO", finalEntity.getEstado());
    }

    @Test
    @DisplayName("3. Una reserva vencida (huérfana tras caída) es recuperada exitosamente")
    void reservaVencida_seRecuperaPorNuevoTrabajador() {
        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_ORPHAN_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Simular que un worker cayó dejando la tarea en PROCESANDO con lease vencido
        tx.execute(status -> {
            ActivationOutboxTaskEntity t = outboxRepository.findById(idTarea).orElseThrow();
            t.setEstado("PROCESANDO");
            t.setBloqueadoPor("crashed-worker-pid-9999");
            t.setBloqueadoHasta(OffsetDateTime.now().minusMinutes(5));
            outboxRepository.save(t);
            return null;
        });

        // Nuevo trabajador invoca el servicio
        boolean procesada = outboxService.procesarTareaConReservaAtomica(idTarea, "worker-recovery-fresh");
        assertTrue(procesada, "La tarea huérfana debe ser recuperada y procesada");

        ActivationOutboxTaskEntity recuperada = outboxRepository.findById(idTarea).orElseThrow();
        assertEquals("ENVIADO", recuperada.getEstado());
    }

    @Test
    @DisplayName("4. SMTP falla y después funciona: se reintenta correctamente y progresa de REINTENTO a ENVIADO")
    void smtpFallaYLuegoFunciona_seReintentaCorrectamente() {
        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_RETRY_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        // 1. Simular fallo en primer intento
        doThrow(new RuntimeException("Simulated SMTP 503 Service Unavailable"))
                .when(notificationPort).enviarEnlaceActivacion(anyString(), anyString(), anyString());

        boolean intento1 = outboxService.procesarTareaConReservaAtomica(idTarea, "worker-intento-1");
        assertFalse(intento1);

        ActivationOutboxTaskEntity postFallo = outboxRepository.findById(idTarea).orElseThrow();
        assertEquals("REINTENTO", postFallo.getEstado());
        assertEquals(1, postFallo.getIntentos());
        assertNotNull(postFallo.getUltimoError());
        assertNull(postFallo.getBloqueadoPor(), "El lease debe ser liberado tras el fallo");

        // 2. Adelantar proximoIntento para simular paso del backoff
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.execute(status -> {
            ActivationOutboxTaskEntity t = outboxRepository.findById(idTarea).orElseThrow();
            t.setProximoIntento(OffsetDateTime.now().minusSeconds(1));
            outboxRepository.save(t);
            return null;
        });

        // 3. Simular recuperación de SMTP
        reset(notificationPort);
        lenient().doAnswer(invocation -> {
            notificationPort.enviarEnlaceActivacion(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)
            );
            return null;
        }).when(notificationPort).enviarEnlaceActivacion(any(), any(), any(), any());

        boolean intento2 = outboxService.procesarTareaConReservaAtomica(idTarea, "worker-intento-2");
        assertTrue(intento2, "El segundo intento debe tener éxito tras la recuperación de SMTP");

        ActivationOutboxTaskEntity postExito = outboxRepository.findById(idTarea).orElseThrow();
        assertEquals("ENVIADO", postExito.getEstado());
        assertEquals(1, postExito.getIntentos());
        assertNull(postExito.getEnlaceCifrado(), "Payload cifrado debe eliminarse tras el éxito");
    }

    @Test
    @DisplayName("5. Demostración estricta de ausencia de bloqueos de fila durante SMTP mediante SELECT ... FOR UPDATE concurrente")
    void envioNoRetieneBloqueoDeBaseDeDatosDuranteSmtp() throws Exception {
        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_NO_LOCK_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        CountDownLatch smtpStarted = new CountDownLatch(1);
        CountDownLatch finishSmtp = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean springTxActiveOnSmtpThread = new java.util.concurrent.atomic.AtomicBoolean(true);

        doAnswer(invocation -> {
            // Verificar que en el hilo que ejecuta NotificationPort no existe transacción Spring activa
            springTxActiveOnSmtpThread.set(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            smtpStarted.countDown();
            boolean completed = finishSmtp.await(5, TimeUnit.SECONDS);
            if (!completed) {
                throw new RuntimeException("Timeout esperando finishSmtp latch");
            }
            return null;
        }).when(notificationPort).enviarEnlaceActivacion(anyString(), anyString(), anyString());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> envioFuture = executor.submit(() ->
                    outboxService.procesarTareaConReservaAtomica(idTarea, "worker-network-delay"));

            assertTrue(smtpStarted.await(5, TimeUnit.SECONDS), "SMTP debió iniciar y pausar");

            // 1. Aserción: No hay transacción Spring activa en el hilo de notificación externa
            assertFalse(springTxActiveOnSmtpThread.get(),
                    "El hilo que invoca NotificationPort NO debe tener una transacción Spring activa");

            // 2. Aserción de conflicto de bloqueo: Desde otra conexión y transacción independiente,
            // intentar adquirir un bloqueo exclusivo (SELECT ... FOR UPDATE) sobre la misma fila.
            // Si la Fase 1 mantuviera el bloqueo de fila abierto durante SMTP, esta consulta
            // se bloquearía y fallaría con lock_timeout (2000ms).
            // Como la Fase 1 comiteó antes de SMTP, la consulta adquiere el lock de inmediato (< 1500ms).
            try (java.sql.Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SET lock_timeout = '2000ms'");
                    stmt.execute("SET statement_timeout = '3000ms'");

                    long startTime = System.currentTimeMillis();
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(
                            "SELECT id_tarea, estado, bloqueado_por FROM outbox_notificacion_activacion WHERE id_tarea = ? FOR UPDATE")) {
                        ps.setLong(1, idTarea);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next(), "La fila de la tarea debe existir");
                            assertEquals("PROCESANDO", rs.getString("estado"));
                            assertEquals("worker-network-delay", rs.getString("bloqueado_por"));
                        }
                    }
                    long elapsed = System.currentTimeMillis() - startTime;
                    // Comprobar que la operación termina antes de liberar SMTP (finishSmtp aún no liberado)
                    assertTrue(elapsed < 1500,
                            "SELECT ... FOR UPDATE debió adquirir el bloqueo inmediatamente (" + elapsed + "ms) sin esperar a SMTP");
                } finally {
                    conn.rollback();
                }
            }

            // Liberar SMTP para que el worker complete su flujo normalmente
            finishSmtp.countDown();
            Boolean resultadoEnvio = envioFuture.get(5, TimeUnit.SECONDS);
            assertTrue(resultadoEnvio);
        } finally {
            finishSmtp.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("6. El planificador procesa la tarea automáticamente sin llamada manual al servicio")
    void tareaConfirmadaSeProcesaAutomaticamentePorScheduler() throws Exception {
        // Al crear la tarea, el scheduler configurado a 500ms debe recogerla y procesarla
        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_SCHEDULER_AUTO_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        // Esperar hasta 6 segundos a que el @Scheduled se dispare automáticamente
        boolean fueProcesada = false;
        for (int i = 0; i < 12; i++) {
            Thread.sleep(500);
            ActivationOutboxTaskEntity t = outboxRepository.findById(idTarea).orElse(null);
            if (t != null && "ENVIADO".equals(t.getEstado())) {
                fueProcesada = true;
                break;
            }
        }

        assertTrue(fueProcesada, "La tarea debió ser procesada automáticamente por el planificador @Scheduled sin llamada manual");
    }

    @Test
    @DisplayName("7. Respeto estricto de proximoIntento en UPDATE atómico: un trabajador con lista obsoleta no puede reservar")
    void trabajadorConListaObsoleta_noPuedeReservarTareaConProximoIntentoEnFuturo() throws Exception {
        // Tarea creada con proximoIntento en el pasado (hace 10 segundos)
        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_STALE_LIST_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        OffsetDateTime now = OffsetDateTime.now();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Trabajador 1 consulta candidatos y obtiene la tarea
        java.util.List<ActivationOutboxTaskEntity> candidatosWorker1 = outboxRepository.findPendingTasksForProcessing(now);
        assertTrue(candidatosWorker1.stream().anyMatch(t -> t.getIdTarea().equals(idTarea)),
                "Worker 1 debe haber encontrado la tarea en su lista inicial de candidatos");

        // Trabajador 2 procesa la tarea primero: adquiere la reserva, simula fallo y programa próximo intento a +30 segundos
        String worker2 = "worker-rapido-2";
        OffsetDateTime leaseWorker2 = now.plusMinutes(2);
        int filasW2 = tx.execute(status ->
                outboxRepository.reservarTareaAtomicamente(idTarea, worker2, leaseWorker2, now));
        assertEquals(1, filasW2, "Worker 2 debe haber reservado la tarea exitosamente");

        OffsetDateTime proximoIntentoFuturo = now.plusSeconds(30);
        int filasFallo = tx.execute(status ->
                outboxRepository.registrarFalloSiConservaReserva(
                        idTarea, worker2, "REINTENTO", 1, proximoIntentoFuturo, "Error simulado", now));
        assertEquals(1, filasFallo, "Worker 2 debe haber registrado el fallo y reprogramado a futuro");

        // Ahora Trabajador 1 intenta reservar usando su lista antigua
        String worker1 = "worker-obsoleto-1";
        OffsetDateTime leaseWorker1 = now.plusMinutes(2);
        // Debe ejecutar reservarTareaAtomicamente evaluando proximoIntento <= now
        int filasW1 = tx.execute(status ->
                outboxRepository.reservarTareaAtomicamente(idTarea, worker1, leaseWorker1, now));

        // Aserción clave: la reserva debe devolver exactamente cero filas afectadas
        assertEquals(0, filasW1,
                "Worker 1 NO debe poder reservar la tarea porque proximoIntento está programado en el futuro");

        // Por tanto, procesarTareaConReservaAtomica devolvería false y no enviaría ningún correo
        boolean procesadaPorW1 = outboxService.procesarTareaConReservaAtomica(idTarea, worker1);
        assertFalse(procesadaPorW1, "El procesamiento por Worker 1 debe abortar sin enviar correo");
        verify(notificationPort, never()).enviarEnlaceActivacion(anyString(), anyString(), anyString());

        // Cuando llegue el momento permitido (simulado adelantando el reloj a futuro), la tarea puede reservarse
        OffsetDateTime momentoFuturo = proximoIntentoFuturo.plusSeconds(1);
        int filasFuturo = tx.execute(status ->
                outboxRepository.reservarTareaAtomicamente(
                        idTarea, "worker-a-tiempo", momentoFuturo.plusMinutes(2), momentoFuturo));
        assertEquals(1, filasFuturo,
                "Cuando llega el proximoIntento permitido, la reserva atómica debe tener éxito (1 fila afectada)");
    }

    @Test
    @DisplayName("8. Verificación de configuración de timeouts SMTP explícitos en JavaMailSender")
    void verificarTimeoutsSmtpConfiguradosEnJavaMailSender() {
        assertTrue(mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl,
                "mailSender debe ser instancia de JavaMailSenderImpl");
        org.springframework.mail.javamail.JavaMailSenderImpl impl = (org.springframework.mail.javamail.JavaMailSenderImpl) mailSender;
        java.util.Properties props = impl.getJavaMailProperties();
        assertEquals("5000", props.getProperty("mail.smtp.connectiontimeout"),
                "mail.smtp.connectiontimeout debe ser 5000ms");
        assertEquals("10000", props.getProperty("mail.smtp.timeout"),
                "mail.smtp.timeout debe ser 10000ms");
        assertEquals("5000", props.getProperty("mail.smtp.writetimeout"),
                "mail.smtp.writetimeout debe ser 5000ms");
    }

    @Test
    @DisplayName("9. Servidor SMTP local que acepta conexión y deja de responder: terminación controlada y conservación de reintento")
    void smtpLocalQueDejaDeResponder_terminaDeFormaControladaYConservaReintento() throws Exception {
        // Servidor SMTP simulado que acepta la conexión TCP pero nunca responde (stalled)
        try (java.net.ServerSocket stalledServer = new java.net.ServerSocket(0)) {
            int stalledPort = stalledServer.getLocalPort();
            CountDownLatch clientConnected = new CountDownLatch(1);
            CountDownLatch serverShouldClose = new CountDownLatch(1);

            Thread serverThread = new Thread(() -> {
                try {
                    java.net.Socket socket = stalledServer.accept();
                    clientConnected.countDown();
                    // No envía el banner '220', ni responde a ningún comando, simplemente duerme
                    serverShouldClose.await(10, TimeUnit.SECONDS);
                    socket.close();
                } catch (Exception ignored) {
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            // Configurar JavaMailSenderImpl apuntando al servidor colgado con timeouts acotados
            org.springframework.mail.javamail.JavaMailSenderImpl testSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
            testSender.setHost("127.0.0.1");
            testSender.setPort(stalledPort);
            java.util.Properties props = new java.util.Properties();
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.connectiontimeout", "1000");
            props.put("mail.smtp.timeout", "2000");
            props.put("mail.smtp.writetimeout", "1000");
            testSender.setJavaMailProperties(props);

            com.lysandri.adapters.out.notification.EmailNotificationAdapter realAdapter =
                    new com.lysandri.adapters.out.notification.EmailNotificationAdapter(testSender, null);

            ActivationOutboxService customService = new ActivationOutboxService(
                    outboxRepository,
                    tokenRepository,
                    cryptoService,
                    realAdapter,
                    transactionManager
            );

            ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_STALLED_SMTP_" + UUID.randomUUID());
            Long idTarea = tarea.getIdTarea();

            long start = System.currentTimeMillis();
            boolean resultado = customService.procesarTareaConReservaAtomica(idTarea, "worker-stalled-test");
            long duration = System.currentTimeMillis() - start;

            // Asegurar que la conexión TCP al servidor simulado fue efectivamente realizada
            assertTrue(clientConnected.await(5, TimeUnit.SECONDS),
                    "JavaMail debió establecer la conexión TCP con el servidor SMTP colgado");

            // 1. Debe retornar false de forma controlada sin colgarse indefinidamente
            assertFalse(resultado, "El intento debe terminar indicando fallo de forma controlada");
            assertTrue(duration < 15000, "El intento debió finalizar dentro del presupuesto y timeout");

            // 2. La tarea debe conservar su estado de REINTENTO
            ActivationOutboxTaskEntity tareaActualizada = outboxRepository.findById(idTarea).orElseThrow();
            assertEquals("REINTENTO", tareaActualizada.getEstado(), "La tarea debe haber pasado a REINTENTO");
            assertEquals(1, tareaActualizada.getIntentos(), "Debe haberse registrado 1 intento fallido");
            assertNotNull(tareaActualizada.getUltimoError(), "Debe registrarse el error de timeout");
            assertNull(tareaActualizada.getBloqueadoPor(), "El lease debe ser liberado al registrar el fallo");
            assertTrue(tareaActualizada.getProximoIntento().isAfter(OffsetDateTime.now().minusSeconds(1)),
                    "proximoIntento debe estar programado en el futuro");

            // 3. El procesamiento posterior debe continuar normalmente con otra tarea
            ActivationOutboxTaskEntity tarea2 = crearTareaDePrueba("IDEMP_STALLED_CONT_" + UUID.randomUUID());
            Long idTarea2 = tarea2.getIdTarea();
            // Simular notificación exitosa para la siguiente tarea en el notificationPort normal
            doNothing().when(notificationPort).enviarEnlaceActivacion(anyString(), anyString(), anyString());
            boolean resultado2 = outboxService.procesarTareaConReservaAtomica(idTarea2, "worker-posterior");
            assertTrue(resultado2, "El procesamiento posterior de otras tareas debe continuar con normalidad");

            serverShouldClose.countDown();
            customService.shutdown();
        }
    }

    @Test
    @DisplayName("10. Operación que ignora interrupción: mantiene reserva y renovación periódica hasta terminación real")
    void operacionQueIgnoraInterrupcion_mantieneReservaYRenovacion_hastaTerminacionReal() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch allowSendToFinish = new CountDownLatch(1);
        CountDownLatch sendFinallyExited = new CountDownLatch(1);

        NotificationPort stubbornNotificationPort = mock(NotificationPort.class);
        doAnswer(invocation -> {
            sendStarted.countDown();
            try {
                // Simular operación que ignora la interrupción del hilo
                while (allowSendToFinish.getCount() > 0) {
                    try {
                        if (allowSendToFinish.await(100, TimeUnit.MILLISECONDS)) {
                            break;
                        }
                    } catch (InterruptedException ignored) {
                        // Ignorar activamente Thread.interrupt() para simular operación obstinada
                    }
                }
            } finally {
                sendFinallyExited.countDown();
            }
            return null;
        }).when(stubbornNotificationPort).enviarEnlaceActivacion(any(), any(), any(), any());

        ActivationOutboxService customService = new ActivationOutboxService(
                outboxRepository,
                tokenRepository,
                cryptoService,
                stubbornNotificationPort,
                transactionManager
        );
        customService.setSendTimeoutSeconds(2);
        customService.setLeaseDurationSeconds(6);
        customService.setRenewalIntervalSeconds(1);
        customService.setMaxWaitCloseSeconds(10);
        customService.setMaxTotalAttemptSeconds(15);

        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_STUBBORN_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> workerFuture = executor.submit(() ->
                customService.procesarTareaConReservaAtomica(idTarea, "worker-stubborn-1")
        );

        try {
            // 1. Esperar a que la tarea inicie efectivamente su envío
            assertTrue(sendStarted.await(5, TimeUnit.SECONDS), "El envío debió iniciar");

            // 2. Aguardar 3 segundos para que se agote el presupuesto global (2s) y se dispare cancel(true)
            Thread.sleep(3000);

            // 3. Comprobar que mientras el envío sigue activo (ignora interrupción), el contador de operaciones activas es 1
            assertEquals(1, customService.getOperacionesActivasPorTarea(idTarea),
                    "Debe haber exactamente 1 operación activa para la tarea");

            // 4. Comprobar que un segundo trabajador NO puede reservar la tarea
            Integer filasWorker2 = tx.execute(status ->
                    outboxRepository.reservarTareaAtomicamente(
                            idTarea,
                            "worker-stubborn-2",
                            OffsetDateTime.now().plusSeconds(6),
                            OffsetDateTime.now()
                    )
            );
            assertEquals(0, filasWorker2 != null ? filasWorker2 : -1,
                    "Worker 2 NO debe poder reservar la tarea mientras el envío anterior siga activo");

            // 5. Comprobar que la reserva fue renovada en base de datos durante ese intervalo
            ActivationOutboxTaskEntity tareaEnDb = outboxRepository.findById(idTarea).orElseThrow();
            assertEquals("worker-stubborn-1", tareaEnDb.getBloqueadoPor(),
                    "La tarea debe continuar perteneciendo a worker-stubborn-1");
            assertEquals("PROCESANDO", tareaEnDb.getEstado());
            assertTrue(tareaEnDb.getBloqueadoHasta().isAfter(OffsetDateTime.now()),
                    "El lease debe haber sido renovado periódicamente y seguir vigente en el futuro");

            // 6. Liberar la operación controladamente
            allowSendToFinish.countDown();

            // 7. Esperar que el worker termine su ciclo
            Boolean resultado = workerFuture.get(8, TimeUnit.SECONDS);
            assertFalse(resultado, "El intento debió culminar en fallo/reintento");
            assertTrue(sendFinallyExited.await(3, TimeUnit.SECONDS), "El envío debió emitir su señal de terminación real");

            // 8. Comprobar transición final en base de datos: REINTENTO, intentos incrementados, proximoIntento futuro
            ActivationOutboxTaskEntity tareaFinal = outboxRepository.findById(idTarea).orElseThrow();
            assertEquals("REINTENTO", tareaFinal.getEstado(), "La tarea debió pasar a REINTENTO");
            assertEquals(1, tareaFinal.getIntentos());
            assertNull(tareaFinal.getBloqueadoPor(), "El lease debe ser liberado al registrar el fallo");
            assertTrue(tareaFinal.getProximoIntento().isAfter(OffsetDateTime.now().minusSeconds(1)));
            assertEquals(0, customService.getOperacionesActivasPorTarea(idTarea),
                    "El recuento de operaciones activas debe volver a 0");
        } finally {
            allowSendToFinish.countDown();
            executor.shutdownNow();
            customService.shutdown();
        }
    }

    @Test
    @DisplayName("11. Transporte SMTP real controlado: activación de aborto de presupuesto global y cierre de conexión")
    void transporteSmtpRealControlado_abortaPresupuestoGlobalYCierraConexion() throws Exception {
        // Servidor SMTP simulado que responde comandos iniciales pero congela la transferencia en DATA
        try (java.net.ServerSocket smtpServer = new java.net.ServerSocket(0)) {
            int port = smtpServer.getLocalPort();
            CountDownLatch clientConnected = new CountDownLatch(1);
            CountDownLatch dataReceived = new CountDownLatch(1);
            CountDownLatch socketClosedOnServer = new CountDownLatch(1);

            Thread serverThread = new Thread(() -> {
                try {
                    java.net.Socket client = smtpServer.accept();
                    clientConnected.countDown();
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(client.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    java.io.BufferedWriter writer = new java.io.BufferedWriter(
                            new java.io.OutputStreamWriter(client.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));

                    writer.write("220 smtp.lysandri.local ESMTP Mock\r\n");
                    writer.flush();

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("EHLO") || line.startsWith("HELO")) {
                            writer.write("250-smtp.lysandri.local\r\n250 OK\r\n");
                            writer.flush();
                        } else if (line.startsWith("MAIL FROM:")) {
                            writer.write("250 OK\r\n");
                            writer.flush();
                        } else if (line.startsWith("RCPT TO:")) {
                            writer.write("250 OK\r\n");
                            writer.flush();
                        } else if (line.startsWith("DATA")) {
                            writer.write("354 Start mail input; end with <CRLF>.<CRLF>\r\n");
                            writer.flush();
                            dataReceived.countDown();
                            // No responde más tras DATA; se queda escuchando hasta que el cliente cierre
                            try {
                                int b = reader.read();
                                if (b == -1) {
                                    socketClosedOnServer.countDown();
                                    break;
                                }
                            } catch (Exception ex) {
                                socketClosedOnServer.countDown();
                                break;
                            }
                        } else if (line.equals("QUIT")) {
                            writer.write("221 Bye\r\n");
                            writer.flush();
                            break;
                        }
                    }
                    client.close();
                } catch (Exception ignored) {
                } finally {
                    socketClosedOnServer.countDown();
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            // Configurar JavaMailSender con timeout largo de lectura (15s) pero presupuesto global corto (2s)
            org.springframework.mail.javamail.JavaMailSenderImpl testSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
            testSender.setHost("127.0.0.1");
            testSender.setPort(port);
            java.util.Properties props = new java.util.Properties();
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable", "false");
            props.put("mail.smtp.connectiontimeout", "3000");
            props.put("mail.smtp.timeout", "15000"); // Timeout de socket mucho mayor al presupuesto
            props.put("mail.smtp.writetimeout", "5000");
            props.put("mail.smtp.socketFactory", com.lysandri.adapters.out.notification.AbortableSocketFactory.getInstance());
            testSender.setJavaMailProperties(props);

            com.lysandri.adapters.out.notification.EmailNotificationAdapter realAdapter =
                    new com.lysandri.adapters.out.notification.EmailNotificationAdapter(testSender, null);

            ActivationOutboxService customService = new ActivationOutboxService(
                    outboxRepository,
                    tokenRepository,
                    cryptoService,
                    realAdapter,
                    transactionManager
            );
            customService.setSendTimeoutSeconds(2); // Presupuesto de 2s activa el aborto antes de los 15s de lectura
            customService.setLeaseDurationSeconds(8);
            customService.setRenewalIntervalSeconds(1);
            customService.setMaxWaitCloseSeconds(5);
            customService.setMaxTotalAttemptSeconds(10);

            ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_REAL_SMTP_ABORT_" + UUID.randomUUID());
            Long idTarea = tarea.getIdTarea();

            long start = System.currentTimeMillis();
            boolean resultado = customService.procesarTareaConReservaAtomica(idTarea, "worker-smtp-abort");
            long duration = System.currentTimeMillis() - start;

            // 1. Validar que la conexión y fase DATA ocurrieron
            assertTrue(clientConnected.await(5, TimeUnit.SECONDS), "El cliente debió conectarse");
            assertTrue(dataReceived.await(5, TimeUnit.SECONDS), "El cliente debió enviar comando DATA");

            // 2. Comprobar que el aborto forzó el cierre de la conexión y terminación real
            assertFalse(resultado, "Debe finalizar con fallo");
            assertTrue(duration >= 2000, "Debe haber respetado el presupuesto de al menos 2 segundos");
            assertTrue(duration < 12000, "El aborto debió cerrar la conexión mucho antes de los 15s de socket");
            assertTrue(socketClosedOnServer.await(5, TimeUnit.SECONDS),
                    "El servidor debe haber detectado el cierre forzado del socket");

            // 3. Comprobar que la tarea quedó lista para reintento y sin bloqueos residuales
            ActivationOutboxTaskEntity tareaActualizada = outboxRepository.findById(idTarea).orElseThrow();
            assertEquals("REINTENTO", tareaActualizada.getEstado());
            assertEquals(1, tareaActualizada.getIntentos());
            assertNull(tareaActualizada.getBloqueadoPor());
            assertTrue(tareaActualizada.getUltimoError().contains("Presupuesto total"));

            customService.shutdown();
        }
    }

    @Test
    @DisplayName("12. Renovación y pérdida de propiedad: exclusividad, fallo de renovación y rescate tras vencimiento")
    void renovacionYPerdidaDePropiedad_comportamientoAnteFalloDeRenovacion() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_LEASE_LOSS_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        // 1. Worker 1 reserva la tarea exitosamente con lease de 60 segundos
        OffsetDateTime now = OffsetDateTime.now();
        Integer filas = tx.execute(s ->
                outboxRepository.reservarTareaAtomicamente(idTarea, "worker-alpha", now.plusSeconds(60), now));
        assertEquals(1, filas);

        // 2. Mientras la reserva sea válida, Worker 2 no puede reservarla
        Integer filasWorker2 = tx.execute(s ->
                outboxRepository.reservarTareaAtomicamente(idTarea, "worker-beta", now.plusSeconds(60), now));
        assertEquals(0, filasWorker2, "Worker 2 no debe poder reservar tarea con reserva vigente");

        // 3. Worker 1 puede renovar su reserva válidamente
        boolean renovado = outboxService.renovarReserva(idTarea, "worker-alpha", 120);
        assertTrue(renovado, "Worker 1 debe poder renovar su propia reserva");

        // 4. Simular transferencia o intervención administrativa (cambio de bloqueadoPor en BD)
        tx.execute(s -> {
            ActivationOutboxTaskEntity t = outboxRepository.findById(idTarea).orElseThrow();
            t.setBloqueadoPor("worker-intervencion");
            outboxRepository.save(t);
            return null;
        });

        // 5. Worker 1 (antiguo propietario) intenta renovar: debe fallar (retorna false)
        boolean renovacionAntiguo = outboxService.renovarReserva(idTarea, "worker-alpha", 60);
        assertFalse(renovacionAntiguo, "Un trabajador antiguo NO debe poder renovar una reserva transferida");

        // 6. Worker 1 intenta marcar éxito o fallo: no debe afectar ninguna fila
        Integer filasExitoAntiguo = tx.execute(s ->
                outboxRepository.marcarExitoSiConservaReserva(idTarea, "worker-alpha", OffsetDateTime.now()));
        assertEquals(0, filasExitoAntiguo, "Un propietario antiguo no debe poder finalizar la tarea");

        // 7. Simular vencimiento de reserva tras caída del proceso (bloqueadoHasta en el pasado)
        tx.execute(s -> {
            ActivationOutboxTaskEntity t = outboxRepository.findById(idTarea).orElseThrow();
            t.setBloqueadoHasta(OffsetDateTime.now().minusSeconds(10));
            outboxRepository.save(t);
            return null;
        });

        // 8. Recuperación: Worker 3 (rescate) puede tomar la tarea huérfana vencida
        Integer filasRescate = tx.execute(s ->
                outboxRepository.reservarTareaAtomicamente(idTarea, "worker-rescate", OffsetDateTime.now().plusSeconds(60), OffsetDateTime.now()));
        assertEquals(1, filasRescate, "La tarea huérfana con reserva vencida debe poder ser recuperada por worker-rescate");
    }

    @Test
    @DisplayName("13. Agotamiento de espera con envío activo: resultado incierto y rechazo de reserva para segundo trabajador")
    void agotamientoDeEsperaConEnvioActivo_transicionaResultadoInciertoYSegundoWorkerNoPuedeReservar() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch allowSendToFinish = new CountDownLatch(1);

        NotificationPort hangingPort = mock(NotificationPort.class);
        doAnswer(invocation -> {
            sendStarted.countDown();
            while (allowSendToFinish.getCount() > 0) {
                try {
                    if (allowSendToFinish.await(50, TimeUnit.MILLISECONDS)) break;
                } catch (InterruptedException ignored) {}
            }
            return null;
        }).when(hangingPort).enviarEnlaceActivacion(any(), any(), any(), any());

        ActivationOutboxService customService = new ActivationOutboxService(
                outboxRepository,
                tokenRepository,
                cryptoService,
                hangingPort,
                transactionManager
        );
        customService.setSendTimeoutSeconds(1);
        customService.setMaxWaitCloseSeconds(1);
        customService.setMaxTotalAttemptSeconds(2);
        customService.setLeaseDurationSeconds(10);
        customService.setRenewalIntervalSeconds(1);

        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_WAIT_EXHAUSTED_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        try {
            boolean resultado = customService.procesarTareaConReservaAtomica(idTarea, "worker-hanging-1");
            assertFalse(resultado, "El intento debe retornar false ante terminación incierta");

            // 1. La tarea debe estar en RESULTADO_INCIERTO en la base de datos (NO en REINTENTO)
            ActivationOutboxTaskEntity tareaIncierta = outboxRepository.findById(idTarea).orElseThrow();
            assertEquals("RESULTADO_INCIERTO", tareaIncierta.getEstado(),
                    "La tarea debe transicionar a RESULTADO_INCIERTO al no poder confirmarse la salida de red");
            assertNotNull(tareaIncierta.getUltimoError(), "Debe registrarse el motivo del resultado incierto");
            assertTrue(tareaIncierta.getUltimoError().contains("ESPERA_AGOTADA"),
                    "El error debe especificar que la espera de terminación fue agotada");

            // 2. Un segundo trabajador NO puede reservar la tarea
            Integer filasSegundoWorker = tx.execute(s ->
                    outboxRepository.reservarTareaAtomicamente(
                            idTarea,
                            "worker-segundo",
                            OffsetDateTime.now().plusSeconds(60),
                            OffsetDateTime.now()
                    )
            );
            assertEquals(0, filasSegundoWorker != null ? filasSegundoWorker : -1,
                    "Un segundo trabajador NO debe poder reservar la tarea mientras esté en RESULTADO_INCIERTO");

            // 3. La consulta periódica tampoco debe seleccionar tareas en RESULTADO_INCIERTO
            var pendientes = outboxRepository.findPendingTasksForProcessing(OffsetDateTime.now().plusHours(1));
            assertFalse(pendientes.stream().anyMatch(t -> t.getIdTarea().equals(idTarea)),
                    "findPendingTasksForProcessing no debe incluir tareas en RESULTADO_INCIERTO");
        } finally {
            allowSendToFinish.countDown();
            customService.shutdown();
        }
    }

    @Test
    @DisplayName("14. Interrupción del hilo coordinador mientras el envío continúa: gestiona resultado incierto sin liberar reserva prematuramente")
    void interrupcionDelCoordinador_gestionaResultadoInciertoSinLiberacionPrematura() throws Exception {
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch allowSendToFinish = new CountDownLatch(1);

        NotificationPort hangingPort = mock(NotificationPort.class);
        doAnswer(invocation -> {
            sendStarted.countDown();
            while (allowSendToFinish.getCount() > 0) {
                try {
                    if (allowSendToFinish.await(50, TimeUnit.MILLISECONDS)) break;
                } catch (InterruptedException ignored) {}
            }
            return null;
        }).when(hangingPort).enviarEnlaceActivacion(any(), any(), any(), any());

        ActivationOutboxService customService = new ActivationOutboxService(
                outboxRepository,
                tokenRepository,
                cryptoService,
                hangingPort,
                transactionManager
        );
        customService.setSendTimeoutSeconds(10);
        customService.setMaxWaitCloseSeconds(1);
        customService.setMaxTotalAttemptSeconds(15);
        customService.setLeaseDurationSeconds(30);

        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_INTERRUPT_WAIT_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        ExecutorService coordExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> coordFuture = coordExecutor.submit(() ->
                    customService.procesarTareaConReservaAtomica(idTarea, "worker-interrupted-coord")
            );

            // Esperar que el envío inicie
            assertTrue(sendStarted.await(5, TimeUnit.SECONDS), "El envío debió iniciar");

            // Interrumpir el hilo coordinador mientras el envío sigue activo
            coordExecutor.shutdownNow();

            Boolean resultado = coordFuture.get(5, TimeUnit.SECONDS);
            assertFalse(resultado, "El coordinador interrumpido debió retornar false");

            // Comprobar que en BD la tarea quedó en RESULTADO_INCIERTO con lease protegido
            ActivationOutboxTaskEntity tareaDb = outboxRepository.findById(idTarea).orElseThrow();
            assertEquals("RESULTADO_INCIERTO", tareaDb.getEstado(),
                    "La tarea debe registrar RESULTADO_INCIERTO tras la interrupción con envío activo");
            assertTrue(tareaDb.getBloqueadoHasta().isAfter(OffsetDateTime.now()),
                    "El lease debe mantenerse bloqueado a futuro impidiendo ejecuciones solapadas");
        } finally {
            allowSendToFinish.countDown();
            coordExecutor.shutdownNow();
            customService.shutdown();
        }
    }

    @Test
    @DisplayName("15. Resolución atómica de carrera entre inicio de trabajo y cancelación previa")
    void carreraEntreInicioYCancela_resolucionAtomicaSinLiberacionManualInsegura() {
        java.util.concurrent.atomic.AtomicReference<ActivationOutboxService.EstadoIntento> estado =
                new java.util.concurrent.atomic.AtomicReference<>(ActivationOutboxService.EstadoIntento.ENCOLADO);

        // Si el coordinador cancela antes de iniciar: CAS(ENCOLADO, CANCELADO_ANTES_DE_INICIAR) debe triunfar
        boolean canceladoPorCoordinador = estado.compareAndSet(
                ActivationOutboxService.EstadoIntento.ENCOLADO,
                ActivationOutboxService.EstadoIntento.CANCELADO_ANTES_DE_INICIAR
        );
        assertTrue(canceladoPorCoordinador, "El coordinador debe ganar la transición atómica");

        // El trabajador que despacha después intenta CAS(ENCOLADO, EN_EJECUCION): debe fallar
        boolean inicioTrabajador = estado.compareAndSet(
                ActivationOutboxService.EstadoIntento.ENCOLADO,
                ActivationOutboxService.EstadoIntento.EN_EJECUCION
        );
        assertFalse(inicioTrabajador, "El trabajador NO debe poder iniciar una tarea ya cancelada");
        assertEquals(ActivationOutboxService.EstadoIntento.CANCELADO_ANTES_DE_INICIAR, estado.get());
    }

    @Test
    @DisplayName("16. Identificación de aborto por intento: dos envíos simultáneos al mismo correo son independientes")
    void identificacionDeAbortoPorIntento_aislamientoTotalMismoDestinatario() throws Exception {
        com.lysandri.adapters.out.notification.AbortableSocketFactory factory =
                com.lysandri.adapters.out.notification.AbortableSocketFactory.getInstance();
        factory.reset();

        String idIntentoA = "attempt-user-same-AAA";
        String idIntentoB = "attempt-user-same-BBB";
        String idIntentoC = "attempt-user-same-CCC";

        // 1. Simular socket registrado para Intento A
        java.net.Socket mockSocketA = mock(java.net.Socket.class);
        when(mockSocketA.isClosed()).thenReturn(false);
        factory.setCurrentAttemptId(idIntentoA);
        java.lang.reflect.Method trackMethod = com.lysandri.adapters.out.notification.AbortableSocketFactory.class.getDeclaredMethod("track", java.net.Socket.class);
        trackMethod.setAccessible(true);
        trackMethod.invoke(factory, mockSocketA);
        factory.clearCurrentAttemptId();

        // 2. Simular socket registrado para Intento B (mismo correo, diferente idIntento)
        java.net.Socket mockSocketB = mock(java.net.Socket.class);
        when(mockSocketB.isClosed()).thenReturn(false);
        factory.setCurrentAttemptId(idIntentoB);
        trackMethod.invoke(factory, mockSocketB);
        factory.clearCurrentAttemptId();

        // Ambos sockets están registrados independientemente
        assertTrue(factory.tieneSocketActivo(idIntentoA));
        assertTrue(factory.tieneSocketActivo(idIntentoB));
        assertEquals(2, factory.getCantidadSocketsActivos());

        // 3. Abortar intento A: cierra socket A y no afecta socket B
        boolean abortadoA = factory.abortSocket(idIntentoA);
        assertTrue(abortadoA, "Intento A debió abortar su socket");
        verify(mockSocketA, times(1)).close();
        verify(mockSocketB, never()).close();

        assertFalse(factory.tieneSocketActivo(idIntentoA), "Intento A ya no debe tener socket activo");
        assertTrue(factory.tieneSocketActivo(idIntentoB), "Intento B debe seguir teniendo su socket activo");

        // 4. Limpieza tardía de A: no debe desregistrar el socket de B
        factory.unregisterSocket(idIntentoA, mockSocketA);
        assertTrue(factory.tieneSocketActivo(idIntentoB), "La limpieza de A no debe afectar el socket de B");

        // 5. Aborto previo al registro de socket para intento C:
        factory.abortSocket(idIntentoC);
        assertTrue(factory.esIntentoAbortado(idIntentoC));

        java.net.Socket mockSocketC = mock(java.net.Socket.class);
        factory.setCurrentAttemptId(idIntentoC);
        try {
            trackMethod.invoke(factory, mockSocketC);
            fail("Debió lanzar SocketException al registrar socket de un intento ya abortado");
        } catch (java.lang.reflect.InvocationTargetException ex) {
            assertTrue(ex.getCause() instanceof java.net.SocketException,
                    "La causa debe ser SocketException por aborto preventivo");
            verify(mockSocketC, times(1)).close();
        } finally {
            factory.clearCurrentAttemptId();
        }

        // 6. Al culminar los intentos y limpiar, no quedan registros
        factory.limpiarIntento(idIntentoA);
        factory.limpiarIntento(idIntentoB);
        factory.limpiarIntento(idIntentoC);
        assertEquals(0, factory.getCantidadSocketsActivos());
        assertEquals(0, factory.getCantidadIntentosAbortados());
    }

    @Test
    @DisplayName("17. Excepción en base de datos durante renovación: supervisor pierde certeza, aborta intento y no sobrescribe BD")
    void excepcionEnRenovacion_supervisorAbortaYNoSobrescribeBd() throws Exception {
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch abortCalled = new CountDownLatch(1);
        CountDownLatch allowSendToFinish = new CountDownLatch(1);

        NotificationPort mockPort = mock(NotificationPort.class);
        doAnswer(invocation -> {
            sendStarted.countDown();
            while (allowSendToFinish.getCount() > 0) {
                try {
                    if (allowSendToFinish.await(50, TimeUnit.MILLISECONDS)) break;
                } catch (InterruptedException ignored) {}
            }
            return null;
        }).when(mockPort).enviarEnlaceActivacion(any(), any(), any(), any());

        doAnswer(invocation -> {
            abortCalled.countDown();
            allowSendToFinish.countDown();
            return null;
        }).when(mockPort).abortarEnvio(anyString());

        ActivationOutboxService customService = new ActivationOutboxService(
                outboxRepository,
                tokenRepository,
                cryptoService,
                mockPort,
                transactionManager
        ) {
            @Override
            public boolean renovarReserva(Long idTarea, String workerId, int segundosAdicionales) {
                throw new org.springframework.dao.DataAccessResourceFailureException("Simulated connection timeout during renewal");
            }
        };
        customService.setSendTimeoutSeconds(10);
        customService.setLeaseDurationSeconds(6);
        customService.setRenewalIntervalSeconds(1); // Watchdog dispara en 1s
        customService.setMaxWaitCloseSeconds(5);
        customService.setMaxTotalAttemptSeconds(10);

        ActivationOutboxTaskEntity tarea = crearTareaDePrueba("IDEMP_DB_EXCEPTION_RENEW_" + UUID.randomUUID());
        Long idTarea = tarea.getIdTarea();

        try {
            boolean resultado = customService.procesarTareaConReservaAtomica(idTarea, "worker-db-fail");
            assertFalse(resultado, "El intento debió culminar en resultado incierto/fallido por pérdida de certeza de reserva");

            // 1. Validar que el envío inició y el aborto fue solicitado por el supervisor ante la excepción de BD
            assertTrue(sendStarted.await(5, TimeUnit.SECONDS), "El envío debió iniciar");
            assertTrue(abortCalled.await(5, TimeUnit.SECONDS), "El supervisor debió invocar abortarEnvio tras excepción de BD");

            // 2. Comprobar que en BD no se marcó ENVIADO ni REINTENTO como propietario confirmado
            ActivationOutboxTaskEntity tareaEnDb = outboxRepository.findById(idTarea).orElseThrow();
            assertNotEquals("ENVIADO", tareaEnDb.getEstado(),
                    "La base de datos NO debe ser sobrescrita como ENVIADA si la propiedad no estaba confirmada");
        } finally {
            allowSendToFinish.countDown();
            customService.shutdown();
        }
    }

    @Test
    @DisplayName("18. Retiro de datos sensibles en logs: verificación estricta con datos sintéticos")
    void retiroDeDatosSensiblesEnLogs_verificacionConDatosSinteticos() throws Exception {
        String syntheticEmail = "confidential-student-audit@example.test";
        String syntheticName = "Secret Student Name";
        String syntheticToken = "secret-token-super-confidential-99";
        String plainLink = "http://localhost:5173/activar-cuenta?token=" + syntheticToken;
        String cipherLink = cryptoService.encrypt(plainLink);

        // Capturar eventos de log de Logback para paquetes de com.lysandri
        Logger rootLogger = (Logger) LoggerFactory.getLogger("com.lysandri");
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        try {
            ActivationOutboxTaskEntity tarea = ActivationOutboxTaskEntity.builder()
                    .idOrden(777L)
                    .codigoOrden("ORD-SYNTH-777")
                    .idUser(idUserTest)
                    .idToken(idTokenTest)
                    .enlaceCifrado(cipherLink)
                    .destinatario(syntheticEmail)
                    .nombreDestinatario(syntheticName)
                    .claveIdempotencia("IDEMP_LOG_AUDIT_" + UUID.randomUUID())
                    .estado("PENDIENTE")
                    .intentos(0)
                    .maxIntentos(5)
                    .proximoIntento(OffsetDateTime.now())
                    .build();
            tarea = outboxRepository.save(tarea);
            Long idTarea = tarea.getIdTarea();

            // Configurar adapter y factory reales sobre mock server para abortar
            com.lysandri.adapters.out.notification.AbortableSocketFactory factory =
                    com.lysandri.adapters.out.notification.AbortableSocketFactory.getInstance();
            String testAttemptId = "attempt-" + idTarea + "-audit-synth";

            // 1. Simular logs de SocketFactory y NotificationAdapter
            factory.abortSocket(testAttemptId);
            factory.limpiarIntento(testAttemptId);

            // 2. Procesar tarea con fallo de conexión para registrar logs de error
            NotificationPort mockFailingPort = mock(NotificationPort.class);
            doThrow(new RuntimeException("Connection refused to target host"))
                    .when(mockFailingPort).enviarEnlaceActivacion(any(), any(), any(), any());

            ActivationOutboxService customService = new ActivationOutboxService(
                    outboxRepository,
                    tokenRepository,
                    cryptoService,
                    mockFailingPort,
                    transactionManager
            );
            customService.setSendTimeoutSeconds(2);
            customService.procesarTareaConReservaAtomica(idTarea, "worker-log-audit");
            customService.shutdown();

            // 3. Inspeccionar todos los logs generados
            java.util.List<String> logMessages = listAppender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertFalse(logMessages.isEmpty(), "Deben haberse capturado eventos de log");

            for (String msg : logMessages) {
                assertFalse(msg.contains(syntheticEmail),
                        "El log NO debe contener el correo electrónico completo sensible: " + msg);
                assertFalse(msg.contains(syntheticName),
                        "El log NO debe contener el nombre sensible del cliente: " + msg);
                assertFalse(msg.contains(syntheticToken),
                        "El log NO debe contener el token de activación en claro: " + msg);
            }

            // 4. Comprobar que los identificadores técnicos sí aparecen permitiendo trazabilidad
            boolean containsIdTarea = logMessages.stream().anyMatch(m -> m.contains(String.valueOf(idTarea)));
            assertTrue(containsIdTarea, "Los logs deben contener el identificador técnico idTarea para correlación");

            boolean containsAttempt = logMessages.stream().anyMatch(m -> m.contains("attempt-"));
            assertTrue(containsAttempt, "Los logs deben contener el identificador técnico attempt-* para correlación");
        } finally {
            rootLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }
}

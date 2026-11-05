package com.lysandri.application.service;

import com.lysandri.adapters.out.database.entity.ActivationOutboxTaskEntity;
import com.lysandri.adapters.out.database.entity.UsuarioActivacionTokenEntity;
import com.lysandri.adapters.out.database.repository.SpringDataActivationOutboxTaskRepository;
import com.lysandri.adapters.out.database.repository.SpringDataUsuarioActivacionTokenRepository;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.security.ActivationCryptoException;
import com.lysandri.security.ActivationCryptoService;
import com.lysandri.security.SanitizerUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Servicio desacoplado para gestión transaccional de notificaciones Outbox de activación.
 *
 * GARANTÍAS DE CONCURRENCIA, RESILIENCIA Y SEMÁNTICA DE ENTREGA:
 * 1. Reserva atómica condicional: Competición de hilos resuelta a nivel de base de datos
 *    mediante UPDATE con recuento de filas afectadas y validación de proximoIntento <= now.
 * 2. Transacciones cortas y delimitadas: La reserva transaccional se comitea antes de contactar SMTP.
 * 3. Cero retención de conexiones/bloqueos DB durante llamadas de red externas: SMTP se ejecuta
 *    fuera de cualquier transacción de Spring y sin mantener bloqueos de fila en PostgreSQL.
 * 4. Presupuesto total de tiempo coordinado con el lease exclusivo:
 *    El tiempo de reserva (ej. 120s) es estrictamente superior al presupuesto total de red (ej. 20s).
 *    Si el envío excede el presupuesto, se interrumpe y se espera la terminación de la operación de red
 *    antes de liberar la reserva. Ningún otro trabajador puede tomar la tarea mientras el envío previo
 *    siga activo o autorizado.
 * 5. Identificador de propietario único (workerId) por reserva con soporte de renovación segura (renovarReserva).
 * 6. Finalización y reprogramación condicionadas a conservar la propiedad de la reserva vigente.
 * 7. Recuperación automática de tareas huérfanas tras caída del proceso (lease expirado).
 * 8. Semántica de entrega 'Al menos una vez' (At-least-once delivery):
 *    No se garantiza entrega 'exactamente una vez'. Si el servidor SMTP acepta el mensaje (250 OK)
 *    pero el proceso cae de forma abrupta antes de que la FASE 3 cometa 'marcarExitoSiConservaReserva',
 *    la tarea permanecerá en PROCESANDO con lease vencido. Al expirar el lease o reiniciar el nodo,
 *    otro trabajador retomará la tarea y reenviará el correo, pudiendo originar un duplicado al destinatario.
 *    Esto previene la pérdida silenciosa de la notificación de activación.
 * 9. Protección de cargas sensibles: Fallos criptográficos preservan el payload sin destrucción inmediata.
 */
@Slf4j
@Service
public class ActivationOutboxService {

    public enum ResultadoEsperaTerminacion {
        TERMINADO,
        ESPERA_AGOTADA,
        ESPERA_INTERRUMPIDA
    }

    public enum EstadoIntento {
        ENCOLADO,
        EN_EJECUCION,
        CANCELADO_ANTES_DE_INICIAR,
        FINALIZADO
    }

    private final SpringDataActivationOutboxTaskRepository outboxRepository;
    private final SpringDataUsuarioActivacionTokenRepository tokenRepository;
    private final ActivationCryptoService cryptoService;
    private final NotificationPort notificationPort;
    private final TransactionTemplate transactionTemplate;

    private final ExecutorService sendExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "outbox-smtp-sender");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService watchdogExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "outbox-lease-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<Long, Integer> activeSendsPerTask = new ConcurrentHashMap<>();

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.outbox.activation.send-timeout-seconds:20}")
    private int sendTimeoutSeconds = 20;

    @Value("${app.outbox.activation.lease-duration-seconds:120}")
    private int leaseDurationSeconds = 120;

    @Value("${app.outbox.activation.renewal-interval-seconds:30}")
    private int renewalIntervalSeconds = 30;

    @Value("${app.outbox.activation.max-wait-close-seconds:5}")
    private int maxWaitCloseSeconds = 5;

    @Value("${app.outbox.activation.max-total-attempt-seconds:60}")
    private int maxTotalAttemptSeconds = 60;

    public void setSendTimeoutSeconds(int sendTimeoutSeconds) {
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    public void setLeaseDurationSeconds(int leaseDurationSeconds) {
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    public void setRenewalIntervalSeconds(int renewalIntervalSeconds) {
        this.renewalIntervalSeconds = renewalIntervalSeconds;
    }

    public void setMaxWaitCloseSeconds(int maxWaitCloseSeconds) {
        this.maxWaitCloseSeconds = maxWaitCloseSeconds;
    }

    public void setMaxTotalAttemptSeconds(int maxTotalAttemptSeconds) {
        this.maxTotalAttemptSeconds = maxTotalAttemptSeconds;
    }

    public int getOperacionesActivasPorTarea(Long idTarea) {
        return activeSendsPerTask.getOrDefault(idTarea, 0);
    }

    public ActivationOutboxService(
            SpringDataActivationOutboxTaskRepository outboxRepository,
            SpringDataUsuarioActivacionTokenRepository tokenRepository,
            ActivationCryptoService cryptoService,
            NotificationPort notificationPort,
            PlatformTransactionManager transactionManager
    ) {
        this.outboxRepository = outboxRepository;
        this.tokenRepository = tokenRepository;
        this.cryptoService = cryptoService;
        this.notificationPort = notificationPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Registra de forma duradera e idempotente la intención de enviar una activación de cuenta
     * dentro de la misma transacción en curso (Outbox pattern).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ActivationOutboxTaskEntity crearTareaActivacion(
            Long idOrden,
            String codigoOrden,
            Long idUser,
            Long idToken,
            String plainToken,
            String destinatario,
            String nombreDestinatario,
            String claveIdempotencia
    ) {
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("Destinatario es obligatorio para la tarea de activación");
        }
        if (plainToken == null || plainToken.isBlank()) {
            throw new IllegalArgumentException("Token en claro es obligatorio para generar el enlace");
        }

        // Idempotencia: si ya existe una tarea para esta clave, retornar la existente
        Optional<ActivationOutboxTaskEntity> existente = outboxRepository.findByClaveIdempotencia(claveIdempotencia);
        if (existente.isPresent()) {
            log.info("Tarea de activación outbox ya registrada previamente para clave: {}", claveIdempotencia);
            return existente.get();
        }

        String baseUrl = frontendUrl != null && !frontendUrl.isBlank()
                ? frontendUrl.replaceAll("/+$", "")
                : "http://localhost:5173";
        String enlaceActivacion = baseUrl + "/activar-cuenta?token=" + plainToken;

        // Cifrado fuerte AES-256-GCM del enlace para que nunca resida en claro en base de datos
        String enlaceCifrado = cryptoService.encrypt(enlaceActivacion);

        ActivationOutboxTaskEntity tarea = ActivationOutboxTaskEntity.builder()
                .idOrden(idOrden)
                .codigoOrden(codigoOrden)
                .idUser(idUser)
                .idToken(idToken)
                .enlaceCifrado(enlaceCifrado)
                .destinatario(destinatario.trim().toLowerCase())
                .nombreDestinatario(nombreDestinatario != null ? nombreDestinatario.trim() : "Cliente")
                .claveIdempotencia(claveIdempotencia)
                .estado("PENDIENTE")
                .intentos(0)
                .maxIntentos(5)
                .proximoIntento(OffsetDateTime.now())
                .fechaCreacion(OffsetDateTime.now())
                .fechaActualizacion(OffsetDateTime.now())
                .build();

        ActivationOutboxTaskEntity guardada = outboxRepository.save(tarea);
        log.info("Tarea Outbox de activación persistida duraderamente. ID: {}, Clave: {}",
                guardada.getIdTarea(), claveIdempotencia);
        return guardada;
    }

    /**
     * Procesa periódicamente tareas pendientes o recuperables.
     * Programado automáticamente por Spring si app.scheduling.enabled=true.
     */
    @Scheduled(fixedDelayString = "${app.outbox.activation.fixed-delay-ms:5000}")
    public int procesarTareasPendientes() {
        OffsetDateTime now = OffsetDateTime.now();
        List<ActivationOutboxTaskEntity> candidatos = outboxRepository.findPendingTasksForProcessing(now);
        if (candidatos.isEmpty()) {
            return 0;
        }

        int exitosas = 0;
        for (ActivationOutboxTaskEntity candidato : candidatos) {
            String workerId = "worker-" + UUID.randomUUID().toString();
            boolean resultado = procesarTareaConReservaAtomica(candidato.getIdTarea(), workerId);
            if (resultado) {
                exitosas++;
            }
        }
        return exitosas;
    }

    /**
     * Ejecuta el ciclo completo de procesamiento en tres fases estrictamente aisladas:
     * 1. FASE 1 (TX 1): Reserva atómica condicional con lease de 2 minutos. Si otro trabajador la ganó, aborta.
     * 2. FASE 2 (SIN TX): Llamada de red externa hacia SMTP. No mantiene conexión de BD ni bloqueo alguno.
     * 3. FASE 3 (TX 2): Registro del resultado condicionado a conservar la propiedad del lease.
     */
    public boolean procesarTareaConReservaAtomica(Long idTarea, String workerId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime leaseHasta = now.plusSeconds(leaseDurationSeconds);

        // -------------------------------------------------------------
        // FASE 1: Transacción de Reserva Atómica (comiteada antes de red)
        // -------------------------------------------------------------
        ActivationOutboxTaskEntity tareaReservada = transactionTemplate.execute(status -> {
            int filasActualizadas = outboxRepository.reservarTareaAtomicamente(idTarea, workerId, leaseHasta, now);
            if (filasActualizadas != 1) {
                // Otro hilo o worker adquirió la tarea o ya no está disponible
                return null;
            }

            Optional<ActivationOutboxTaskEntity> opt = outboxRepository.findById(idTarea);
            if (opt.isEmpty()) {
                return null;
            }

            ActivationOutboxTaskEntity tarea = opt.get();

            // Validar si el token ya fue consumido, revocado o caducó
            Optional<UsuarioActivacionTokenEntity> tokenOpt = tokenRepository.findById(tarea.getIdToken());
            if (tokenOpt.isEmpty() || tokenOpt.get().isUsado() || tokenOpt.get().isRevocado()
                    || tokenOpt.get().getFechaExpiracion().isBefore(now)) {
                log.info("Token ID: {} asociado a tarea ID: {} ha sido revocado o ya fue consumido. Cancelando tarea.",
                        tarea.getIdToken(), tarea.getIdTarea());
                outboxRepository.marcarCanceladoSiConservaReserva(idTarea, workerId, now);
                return null;
            }

            return tarea;
        });

        if (tareaReservada == null) {
            // No se obtuvo la reserva o el token ya fue invalidado/cancelado
            return false;
        }

        final String idIntento = "attempt-" + idTarea + "-" + UUID.randomUUID();
        log.info("Reserva exclusiva adquirida para tarea ID: {} (Intento: {}, Worker: {}). Transacción de reserva cerrada.",
                idTarea, idIntento, workerId);

        // -------------------------------------------------------------
        // FASE 2: Operaciones fuera de transacción de base de datos
        // -------------------------------------------------------------
        String plainLink;
        try {
            plainLink = cryptoService.decrypt(tareaReservada.getEnlaceCifrado());
        } catch (ActivationCryptoException ex) {
            log.error("Fallo criptográfico al descifrar tarea ID: {} (Intento: {}). Preservando carga cifrada para recuperación.", idTarea, idIntento);
            transactionTemplate.execute(status -> {
                outboxRepository.registrarFalloCriptograficoSiConservaReserva(
                        idTarea,
                        workerId,
                        SanitizerUtils.sanitizeMessage(ex.getMessage()),
                        OffsetDateTime.now()
                );
                return null;
            });
            return false;
        }

        final java.util.concurrent.atomic.AtomicReference<EstadoIntento> estadoIntento =
                new java.util.concurrent.atomic.AtomicReference<>(EstadoIntento.ENCOLADO);
        final CountDownLatch realTerminationLatch = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicBoolean leaseLost = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<String> errorEnvioRef = new java.util.concurrent.atomic.AtomicReference<>(null);
        final java.util.concurrent.atomic.AtomicBoolean envioExitosoRef = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Coordinación de renovación periódica desacoplada
        long intervaloRenovacion = Math.max(1, Math.min(leaseDurationSeconds / 3, renewalIntervalSeconds));
        long inicioEnvio = System.currentTimeMillis();
        long limiteAbsolutoMs = inicioEnvio + (maxTotalAttemptSeconds * 1000L);

        ScheduledFuture<?> watchdogFuture = watchdogExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (realTerminationLatch.getCount() == 0) {
                    return;
                }

                if (System.currentTimeMillis() > limiteAbsolutoMs) {
                    log.error("Límite máximo absoluto de intento ({} s) superado para tarea ID: {} (Intento: {}). Deteniendo renovación.",
                            maxTotalAttemptSeconds, idTarea, idIntento);
                    leaseLost.set(true);
                    try {
                        notificationPort.abortarEnvio(idIntento);
                    } catch (Throwable t) {
                        log.error("Error al abortar intento {} tras límite absoluto: {}", idIntento, SanitizerUtils.sanitizeMessage(t.getMessage()));
                    }
                    return;
                }

                boolean renovado = false;
                try {
                    renovado = renovarReserva(idTarea, workerId, leaseDurationSeconds);
                } catch (Throwable dbEx) {
                    log.error("Excepción en base de datos durante renovación periódica para tarea ID: {} (Intento: {}): {}. Certeza de propiedad de reserva perdida.",
                            idTarea, idIntento, SanitizerUtils.sanitizeMessage(dbEx.getMessage()));
                    leaseLost.set(true);
                    try {
                        notificationPort.abortarEnvio(idIntento);
                    } catch (Throwable t) {
                        log.error("Error al abortar intento {} tras fallo de base de datos en renovación: {}", idIntento, SanitizerUtils.sanitizeMessage(t.getMessage()));
                    }
                    return;
                }

                if (!renovado) {
                    log.warn("Pérdida de reserva detectada (cero filas afectadas) durante renovación periódica para tarea ID: {} (Intento: {}). Abortando transporte SMTP.",
                            idTarea, idIntento);
                    leaseLost.set(true);
                    try {
                        notificationPort.abortarEnvio(idIntento);
                    } catch (Throwable t) {
                        log.error("Error al abortar intento {} tras perder reserva: {}", idIntento, SanitizerUtils.sanitizeMessage(t.getMessage()));
                    }
                } else {
                    log.debug("Reserva renovada periódicamente por {} s para tarea ID: {} (Intento: {})",
                            leaseDurationSeconds, idTarea, idIntento);
                }
            } catch (Throwable uncaught) {
                log.error("Excepción no controlada en supervisor de renovación para tarea ID: {} (Intento: {}): {}",
                        idTarea, idIntento, SanitizerUtils.sanitizeMessage(uncaught.getMessage()));
                leaseLost.set(true);
                try {
                    notificationPort.abortarEnvio(idIntento);
                } catch (Throwable ignored) {}
            }
        }, intervaloRenovacion, intervaloRenovacion, TimeUnit.SECONDS);

        // Ejecución con presupuesto total acotado (sendTimeoutSeconds) independiente de los timeouts de socket
        final String linkParaEnvio = plainLink;
        Future<?> sendFuture = sendExecutor.submit(() -> {
            if (Thread.currentThread().isInterrupted() || leaseLost.get()) {
                if (estadoIntento.compareAndSet(EstadoIntento.ENCOLADO, EstadoIntento.CANCELADO_ANTES_DE_INICIAR)) {
                    realTerminationLatch.countDown();
                }
                return;
            }
            if (!estadoIntento.compareAndSet(EstadoIntento.ENCOLADO, EstadoIntento.EN_EJECUCION)) {
                // Ya fue cancelado antes de iniciar por el hilo coordinador
                return;
            }
            activeSendsPerTask.compute(idTarea, (k, v) -> v == null ? 1 : v + 1);
            try {
                notificationPort.enviarEnlaceActivacion(
                        tareaReservada.getDestinatario(),
                        tareaReservada.getNombreDestinatario(),
                        linkParaEnvio,
                        idIntento
                );
                envioExitosoRef.set(true);
            } catch (Throwable t) {
                errorEnvioRef.compareAndSet(null, SanitizerUtils.sanitizeMessage(t.getMessage()));
            } finally {
                estadoIntento.set(EstadoIntento.FINALIZADO);
                activeSendsPerTask.compute(idTarea, (k, v) -> (v == null || v <= 1) ? null : v - 1);
                realTerminationLatch.countDown();
            }
        });

        try {
            sendFuture.get(sendTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            log.warn("Presupuesto total de envío agotado tras {} s para tarea ID: {} (Intento: {}). Solicitando aborto y cancelación efectiva.",
                    sendTimeoutSeconds, idTarea, idIntento);
            sendFuture.cancel(true);
            boolean canceladoAntesDeIniciar = estadoIntento.compareAndSet(EstadoIntento.ENCOLADO, EstadoIntento.CANCELADO_ANTES_DE_INICIAR);
            if (canceladoAntesDeIniciar) {
                realTerminationLatch.countDown();
                errorEnvioRef.compareAndSet(null, "Presupuesto de envío agotado antes de iniciar");
            } else {
                try {
                    notificationPort.abortarEnvio(idIntento);
                } catch (Throwable t) {
                    log.error("Fallo al solicitar aborto de intento {}: {}", idIntento, SanitizerUtils.sanitizeMessage(t.getMessage()));
                }
                errorEnvioRef.compareAndSet(null, "Presupuesto total de envío agotado tras " + sendTimeoutSeconds + " segundos");

                // Espera de terminación REAL (mediante CountDownLatch, NO con isDone() ni sleeps fijos)
                ResultadoEsperaTerminacion resultadoTerminacion = esperarTerminacionReal(realTerminationLatch, idTarea, idIntento, maxWaitCloseSeconds, limiteAbsolutoMs);
                if (resultadoTerminacion != ResultadoEsperaTerminacion.TERMINADO) {
                    log.error("Resultado incierto: la salida de red no pudo confirmarse para tarea ID: {} (Intento: {}, Estado espera: {}). Impidiendo reintento solapado.",
                            idTarea, idIntento, resultadoTerminacion);
                    transactionTemplate.execute(status -> {
                        outboxRepository.marcarResultadoInciertoSiConservaReserva(
                                idTarea,
                                workerId,
                                "Presupuesto agotado y terminación real no confirmada (" + resultadoTerminacion + ")",
                                OffsetDateTime.now().plusDays(7),
                                OffsetDateTime.now()
                        );
                        return null;
                    });
                    return false;
                }
            }
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            errorEnvioRef.compareAndSet(null, SanitizerUtils.sanitizeMessage(cause.getMessage()));
            log.warn("Fallo en envío de correo para tarea ID: {} (Intento: {}): {}", idTarea, idIntento, errorEnvioRef.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Hilo coordinador interrumpido para tarea ID: {} (Intento: {}). Solicitando aborto.", idTarea, idIntento);
            sendFuture.cancel(true);
            boolean canceladoAntesDeIniciar = estadoIntento.compareAndSet(EstadoIntento.ENCOLADO, EstadoIntento.CANCELADO_ANTES_DE_INICIAR);
            if (canceladoAntesDeIniciar) {
                realTerminationLatch.countDown();
                errorEnvioRef.compareAndSet(null, "Envío cancelado antes de iniciar por interrupción");
            } else {
                try {
                    notificationPort.abortarEnvio(idIntento);
                } catch (Throwable t) {
                    log.error("Fallo al solicitar aborto de intento {}: {}", idIntento, SanitizerUtils.sanitizeMessage(t.getMessage()));
                }
                errorEnvioRef.compareAndSet(null, "Envío interrumpido");
                ResultadoEsperaTerminacion resultadoTerminacion = esperarTerminacionReal(realTerminationLatch, idTarea, idIntento, maxWaitCloseSeconds, limiteAbsolutoMs);
                if (resultadoTerminacion != ResultadoEsperaTerminacion.TERMINADO) {
                    log.error("Resultado incierto tras interrupción: la salida de red no pudo confirmarse para tarea ID: {} (Intento: {}, Estado espera: {}). Impidiendo reintento solapado.",
                            idTarea, idIntento, resultadoTerminacion);
                    transactionTemplate.execute(status -> {
                        outboxRepository.marcarResultadoInciertoSiConservaReserva(
                                idTarea,
                                workerId,
                                "Interrupción en envío y terminación real no confirmada (" + resultadoTerminacion + ")",
                                OffsetDateTime.now().plusDays(7),
                                OffsetDateTime.now()
                        );
                        return null;
                    });
                    return false;
                }
            }
        } finally {
            watchdogFuture.cancel(true);
        }

        // Si se perdió la reserva o falló la renovación, gestionar explícitamente el resultado incierto
        if (leaseLost.get()) {
            ResultadoEsperaTerminacion resLease = esperarTerminacionReal(realTerminationLatch, idTarea, idIntento, maxWaitCloseSeconds, limiteAbsolutoMs);
            log.error("Resultado incierto para tarea ID: {} (Intento: {}, Worker: {}): se perdió la reserva o expiró la certeza de propiedad. Terminación efectiva={}. No se sobrescribe la base de datos.",
                    idTarea, idIntento, workerId, resLease);
            return false;
        }

        // Comprobar terminación efectiva del intento antes de proseguir a FASE 3
        if (realTerminationLatch.getCount() > 0) {
            ResultadoEsperaTerminacion resPost = esperarTerminacionReal(realTerminationLatch, idTarea, idIntento, maxWaitCloseSeconds, limiteAbsolutoMs);
            if (resPost != ResultadoEsperaTerminacion.TERMINADO) {
                log.error("Resultado incierto: terminación no confirmada al cierre para tarea ID: {} (Intento: {}). Impidiendo reprogramación solapada.",
                        idTarea, idIntento);
                transactionTemplate.execute(status -> {
                    outboxRepository.marcarResultadoInciertoSiConservaReserva(
                            idTarea,
                            workerId,
                            "Terminación real de red no confirmada al cierre (" + resPost + ")",
                            OffsetDateTime.now().plusDays(7),
                            OffsetDateTime.now()
                    );
                    return null;
                });
                return false;
            }
        }

        boolean envioExitoso = envioExitosoRef.get() && errorEnvioRef.get() == null;
        String errorEnvio = errorEnvioRef.get();

        // -------------------------------------------------------------
        // FASE 3: Transacción de Confirmación / Reprogramación
        // -------------------------------------------------------------
        final boolean exito = envioExitoso;
        final String errorFinal = errorEnvio;

        Boolean actualizado = transactionTemplate.execute(status -> {
            OffsetDateTime fechaCierre = OffsetDateTime.now();

            if (exito) {
                int filas = outboxRepository.marcarExitoSiConservaReserva(idTarea, workerId, fechaCierre);
                if (filas == 1) {
                    log.info("Tarea Outbox ID: {} marcada exitosamente como ENVIADA (Intento: {}, Worker: {})", idTarea, idIntento, workerId);
                    return true;
                } else {
                    log.warn("Pérdida de reserva para tarea ID: {} (Intento: {}). Worker {} completó envío pero su lease expiró o fue transferido.",
                            idTarea, idIntento, workerId);
                    return false;
                }
            } else {
                int nuevosIntentos = tareaReservada.getIntentos() + 1;
                String nuevoEstado = (nuevosIntentos >= tareaReservada.getMaxIntentos()) ? "FALLIDO" : "REINTENTO";
                long backoffSeconds = calcularEsperaProgresivaSegundos(nuevosIntentos);
                OffsetDateTime proximo = fechaCierre.plusSeconds(backoffSeconds);

                int filas = outboxRepository.registrarFalloSiConservaReserva(
                        idTarea,
                        workerId,
                        nuevoEstado,
                        nuevosIntentos,
                        proximo,
                        errorFinal,
                        fechaCierre
                );
                if (filas == 1) {
                    log.info("Tarea Outbox ID: {} transicionada a {} (intento {}/{}) por worker: {} (Intento: {})",
                            idTarea, nuevoEstado, nuevosIntentos, tareaReservada.getMaxIntentos(), workerId, idIntento);
                } else {
                    log.warn("Pérdida de reserva al registrar fallo para tarea ID: {} (Intento: {}) por worker: {}", idTarea, idIntento, workerId);
                }
                return false;
            }
        });

        return Boolean.TRUE.equals(actualizado) && envioExitoso;
    }

    /**
     * Renovación segura de la reserva exclusiva mientras el trabajador continúa ejecutando el envío:
     * Solo tiene efecto si el trabajador aún posee la reserva y la tarea sigue en PROCESANDO.
     */
    public boolean renovarReserva(Long idTarea, String workerId, int segundosAdicionales) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime nuevoLease = now.plusSeconds(segundosAdicionales);
        Integer filas = transactionTemplate.execute(status ->
                outboxRepository.renovarReserva(idTarea, workerId, nuevoLease, now));
        return filas != null && filas == 1;
    }

    public ResultadoEsperaTerminacion esperarTerminacionReal(
            CountDownLatch latch,
            Long idTarea,
            String idIntento,
            int segundosEspera,
            long limiteAbsolutoMs
    ) {
        try {
            boolean cerrada = latch.await(segundosEspera, TimeUnit.SECONDS);
            if (cerrada) {
                return ResultadoEsperaTerminacion.TERMINADO;
            }
            log.warn("La operación de red para tarea ID: {} (Intento: {}) no terminó dentro de {} s tras aborto. Esperando hasta límite absoluto.",
                    idTarea, idIntento, segundosEspera);
            while (latch.getCount() > 0 && !watchdogExecutor.isShutdown()) {
                if (latch.await(200, TimeUnit.MILLISECONDS)) {
                    return ResultadoEsperaTerminacion.TERMINADO;
                }
                if (System.currentTimeMillis() > limiteAbsolutoMs) {
                    log.error("Límite absoluto de espera alcanzado para tarea ID: {} (Intento: {}). Salida efectiva de red no confirmada.",
                            idTarea, idIntento);
                    return ResultadoEsperaTerminacion.ESPERA_AGOTADA;
                }
                if (Thread.currentThread().isInterrupted()) {
                    log.warn("Hilo coordinador interrumpido mientras esperaba terminación real para tarea ID: {} (Intento: {})",
                            idTarea, idIntento);
                    return ResultadoEsperaTerminacion.ESPERA_INTERRUMPIDA;
                }
            }
            return latch.getCount() == 0 ? ResultadoEsperaTerminacion.TERMINADO : ResultadoEsperaTerminacion.ESPERA_AGOTADA;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupción capturada mientras se esperaba terminación real para tarea ID: {} (Intento: {})",
                    idTarea, idIntento);
            return ResultadoEsperaTerminacion.ESPERA_INTERRUMPIDA;
        }
    }

    @PreDestroy
    public void shutdown() {
        sendExecutor.shutdownNow();
        watchdogExecutor.shutdownNow();
    }

    /**
     * Espera progresiva (backoff exponencial controlado):
     * Intento 1: 5s, Intento 2: 15s, Intento 3: 45s, Intento 4: 120s, Intento 5: 300s
     */
    public static long calcularEsperaProgresivaSegundos(int intento) {
        return switch (intento) {
            case 1 -> 5L;
            case 2 -> 15L;
            case 3 -> 45L;
            case 4 -> 120L;
            default -> 300L;
        };
    }
}

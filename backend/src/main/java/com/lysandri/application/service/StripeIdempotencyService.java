package com.lysandri.application.service;

import com.lysandri.adapters.out.database.entity.StripeProcessedEventEntity;
import com.lysandri.adapters.out.database.repository.SpringDataStripeEventRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeIdempotencyService {

    private final SpringDataStripeEventRepository stripeEventRepository;

    public static final int DURACION_LEASE_MINUTOS = 3;

    public enum EstadoEventoResultado {
        INICIADO,
        YA_COMPLETADO,
        EN_PROCESO
    }

    @Getter
    public static class ResultadoAdquisicion {
        private final EstadoEventoResultado estado;
        private final String ownerToken;
        private final OffsetDateTime leaseExpiresAt;

        private ResultadoAdquisicion(EstadoEventoResultado estado, String ownerToken, OffsetDateTime leaseExpiresAt) {
            this.estado = estado;
            this.ownerToken = ownerToken;
            this.leaseExpiresAt = leaseExpiresAt;
        }

        public static ResultadoAdquisicion adquirido(String ownerToken, OffsetDateTime leaseExpiresAt) {
            return new ResultadoAdquisicion(EstadoEventoResultado.INICIADO, ownerToken, leaseExpiresAt);
        }

        public static ResultadoAdquisicion yaCompletado() {
            return new ResultadoAdquisicion(EstadoEventoResultado.YA_COMPLETADO, null, null);
        }

        public static ResultadoAdquisicion enProceso(OffsetDateTime leaseExpiresAt) {
            return new ResultadoAdquisicion(EstadoEventoResultado.EN_PROCESO, null, leaseExpiresAt);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoAdquisicion iniciarProcesamiento(
            String idEvento,
            String tipoEvento,
            String sessionId,
            String paymentIntentId,
            String codigoOrden) {

        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime nuevaExpiracion = ahora.plusMinutes(DURACION_LEASE_MINUTOS);
        String nuevoOwnerToken = UUID.randomUUID().toString();

        Optional<StripeProcessedEventEntity> existente = stripeEventRepository.findByIdEvento(idEvento);

        if (existente.isPresent()) {
            StripeProcessedEventEntity evento = existente.get();

            if ("COMPLETADO".equalsIgnoreCase(evento.getEstado())) {
                log.info("Evento Stripe {} ({}) ya fue completado previamente. Omitiendo duplicado.", idEvento, tipoEvento);
                return ResultadoAdquisicion.yaCompletado();
            }

            // Si está procesándose y el lease NO ha expirado, está en curso por otro hilo/worker activo
            if ("PROCESANDO".equalsIgnoreCase(evento.getEstado())
                    && evento.getLeaseExpiresAt() != null
                    && evento.getLeaseExpiresAt().isAfter(ahora)) {
                log.warn("Evento Stripe {} ({}) en proceso activo. Lease expira en: {}",
                        idEvento, tipoEvento, evento.getLeaseExpiresAt());
                return ResultadoAdquisicion.enProceso(evento.getLeaseExpiresAt());
            }

            // Estado FALLIDO o PROCESANDO con lease expirado (recuperación segura y atómica)
            log.info("Adquiriendo de forma atómica evento {} para reintento/recuperación (Estado actual: {}, Lease previo: {})",
                    idEvento, evento.getEstado(), evento.getLeaseExpiresAt());

            int filasActualizadas = stripeEventRepository.adquirirLeaseEvento(idEvento, nuevoOwnerToken, nuevaExpiracion, ahora);
            if (filasActualizadas == 1) {
                log.info("Lease de evento {} adquirido con éxito. Token: {}", idEvento, nuevoOwnerToken);
                return ResultadoAdquisicion.adquirido(nuevoOwnerToken, nuevaExpiracion);
            } else {
                log.warn("Conflicto concurrente al adquirir lease de evento {}. Otro hilo tomó el procesamiento.", idEvento);
                return ResultadoAdquisicion.enProceso(nuevaExpiracion);
            }
        }

        // Intento de registrar nuevo evento
        try {
            StripeProcessedEventEntity nuevoEvento = StripeProcessedEventEntity.builder()
                    .idEvento(idEvento)
                    .tipoEvento(tipoEvento)
                    .stripeSessionId(sessionId)
                    .stripePaymentIntentId(paymentIntentId)
                    .codigoOrden(codigoOrden)
                    .estado("PROCESANDO")
                    .ownerToken(nuevoOwnerToken)
                    .leaseExpiresAt(nuevaExpiracion)
                    .attemptCount(1)
                    .fechaRegistro(ahora)
                    .build();

            stripeEventRepository.saveAndFlush(nuevoEvento);
            log.info("Nuevo evento Stripe {} registrado en estado PROCESANDO con lease token: {}", idEvento, nuevoOwnerToken);
            return ResultadoAdquisicion.adquirido(nuevoOwnerToken, nuevaExpiracion);

        } catch (DataIntegrityViolationException e) {
            // Carrera atómica: otro hilo insertó la clave primaria idEvento simultáneamente
            log.warn("Colisión de clave primaria al insertar evento Stripe {}. Consultando estado concurrente.", idEvento);
            return stripeEventRepository.findByIdEvento(idEvento)
                    .map(ev -> "COMPLETADO".equalsIgnoreCase(ev.getEstado())
                            ? ResultadoAdquisicion.yaCompletado()
                            : ResultadoAdquisicion.enProceso(ev.getLeaseExpiresAt()))
                    .orElse(ResultadoAdquisicion.enProceso(nuevaExpiracion));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean marcarCompletado(
            String idEvento,
            String ownerToken,
            Long idOrden,
            String codigoOrden,
            String sessionId,
            String paymentIntentId) {

        OffsetDateTime ahora = OffsetDateTime.now();
        int filas = stripeEventRepository.marcarCompletadoConToken(
                idEvento, ownerToken, idOrden, codigoOrden, sessionId, paymentIntentId, ahora);

        if (filas > 0) {
            log.info("Evento Stripe {} marcado como COMPLETADO en auditoría de base de datos (Token: {})", idEvento, ownerToken);
            return true;
        } else {
            log.warn("No se pudo marcar COMPLETADO el evento {}: el lease expiró o fue transferido a otro proceso (Token: {})",
                    idEvento, ownerToken);
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean marcarFallido(String idEvento, String ownerToken, String mensajeError) {
        OffsetDateTime ahora = OffsetDateTime.now();
        int filas = stripeEventRepository.marcarFallidoConToken(idEvento, ownerToken, mensajeError, ahora);

        if (filas > 0) {
            log.warn("Evento Stripe {} marcado como FALLIDO: {} (Token: {})", idEvento, mensajeError, ownerToken);
            return true;
        } else {
            log.warn("No se pudo marcar FALLIDO el evento {}: el lease expiró o fue transferido a otro proceso (Token: {})",
                    idEvento, ownerToken);
            return false;
        }
    }
}

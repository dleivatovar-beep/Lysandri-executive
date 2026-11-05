package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.ActivationOutboxTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataActivationOutboxTaskRepository extends JpaRepository<ActivationOutboxTaskEntity, Long> {

    Optional<ActivationOutboxTaskEntity> findByClaveIdempotencia(String claveIdempotencia);

    /**
     * Consulta tareas disponibles para procesar:
     * - Tareas en estado PENDIENTE o REINTENTO con proximoIntento vencido o actual
     * - O tareas en PROCESANDO cuyo lease (bloqueadoHasta) ha expirado (recuperación tras caída)
     */
    @Query("SELECT t FROM ActivationOutboxTaskEntity t WHERE " +
            "((t.estado IN ('PENDIENTE', 'REINTENTO') AND (t.bloqueadoHasta IS NULL OR t.bloqueadoHasta < :now)) " +
            " OR (t.estado = 'PROCESANDO' AND t.bloqueadoHasta < :now)) " +
            "AND t.proximoIntento <= :now " +
            "ORDER BY t.fechaCreacion ASC")
    List<ActivationOutboxTaskEntity> findPendingTasksForProcessing(@Param("now") OffsetDateTime now);

    /**
     * Reserva atómica condicional:
     * Actualiza el estado a PROCESANDO y adquiere el lease exclusivo con workerId.
     * Verifica que proximoIntento <= now para evitar que un hilo con lista antigua
     * reserve una tarea ya reprogramada a futuro.
     * Retorna 1 si este hilo obtuvo la reserva exclusiva, 0 si la tarea no es elegible o ya fue tomada.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ActivationOutboxTaskEntity t SET " +
            "t.estado = 'PROCESANDO', " +
            "t.bloqueadoPor = :workerId, " +
            "t.bloqueadoHasta = :bloqueadoHasta, " +
            "t.fechaActualizacion = :now " +
            "WHERE t.idTarea = :idTarea " +
            "AND t.proximoIntento <= :now " +
            "AND ((t.estado IN ('PENDIENTE', 'REINTENTO') AND (t.bloqueadoHasta IS NULL OR t.bloqueadoHasta < :now)) " +
            "     OR (t.estado = 'PROCESANDO' AND t.bloqueadoHasta < :now))")
    int reservarTareaAtomicamente(
            @Param("idTarea") Long idTarea,
            @Param("workerId") String workerId,
            @Param("bloqueadoHasta") OffsetDateTime bloqueadoHasta,
            @Param("now") OffsetDateTime now
    );

    /**
     * Renovación segura de la reserva exclusiva mientras el trabajador continúa ejecutando el envío:
     * Solo tiene efecto si el trabajador aún posee la reserva y la tarea sigue en PROCESANDO.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ActivationOutboxTaskEntity t SET " +
            "t.bloqueadoHasta = :nuevoBloqueadoHasta, " +
            "t.fechaActualizacion = :now " +
            "WHERE t.idTarea = :idTarea AND t.bloqueadoPor = :workerId AND t.estado = 'PROCESANDO'")
    int renovarReserva(
            @Param("idTarea") Long idTarea,
            @Param("workerId") String workerId,
            @Param("nuevoBloqueadoHasta") OffsetDateTime nuevoBloqueadoHasta,
            @Param("now") OffsetDateTime now
    );

    /**
     * Finalización exitosa:
     * Transiciona a ENVIADO y limpia el contenido cifrado SOLO si el trabajador aún conserva la reserva.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ActivationOutboxTaskEntity t SET " +
            "t.estado = 'ENVIADO', " +
            "t.fechaEnvio = :now, " +
            "t.enlaceCifrado = NULL, " +
            "t.ultimoError = NULL, " +
            "t.bloqueadoHasta = NULL, " +
            "t.bloqueadoPor = NULL, " +
            "t.fechaActualizacion = :now " +
            "WHERE t.idTarea = :idTarea AND t.bloqueadoPor = :workerId")
    int marcarExitoSiConservaReserva(
            @Param("idTarea") Long idTarea,
            @Param("workerId") String workerId,
            @Param("now") OffsetDateTime now
    );

    /**
     * Registro de fallo o reintento progresivo:
     * Actualiza intentos, proximoIntento y estado SOLO si el trabajador aún conserva la reserva.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ActivationOutboxTaskEntity t SET " +
            "t.estado = :nuevoEstado, " +
            "t.intentos = :intentos, " +
            "t.proximoIntento = :proximoIntento, " +
            "t.ultimoError = :ultimoError, " +
            "t.enlaceCifrado = CASE WHEN :nuevoEstado = 'FALLIDO' THEN NULL ELSE t.enlaceCifrado END, " +
            "t.bloqueadoHasta = NULL, " +
            "t.bloqueadoPor = NULL, " +
            "t.fechaActualizacion = :now " +
            "WHERE t.idTarea = :idTarea AND t.bloqueadoPor = :workerId")
    int registrarFalloSiConservaReserva(
            @Param("idTarea") Long idTarea,
            @Param("workerId") String workerId,
            @Param("nuevoEstado") String nuevoEstado,
            @Param("intentos") int intentos,
            @Param("proximoIntento") OffsetDateTime proximoIntento,
            @Param("ultimoError") String ultimoError,
            @Param("now") OffsetDateTime now
    );

    /**
     * Fallo criptográfico controlado:
     * Preserva estrictamente el enlaceCifrado para permitir recuperación con la clave correcta,
     * sin destrucción silenciosa ni borrado inmediato.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ActivationOutboxTaskEntity t SET " +
            "t.estado = 'FALLIDO_CRIPTOGRAFICO', " +
            "t.ultimoError = :ultimoError, " +
            "t.bloqueadoHasta = NULL, " +
            "t.bloqueadoPor = NULL, " +
            "t.fechaActualizacion = :now " +
            "WHERE t.idTarea = :idTarea AND t.bloqueadoPor = :workerId")
    int registrarFalloCriptograficoSiConservaReserva(
            @Param("idTarea") Long idTarea,
            @Param("workerId") String workerId,
            @Param("ultimoError") String ultimoError,
            @Param("now") OffsetDateTime now
    );

    /**
     * Cancelación por obsolescencia (token ya revocado, consumido o expirado).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ActivationOutboxTaskEntity t SET " +
            "t.estado = 'CANCELADO_OBSOLETO', " +
            "t.enlaceCifrado = NULL, " +
            "t.bloqueadoHasta = NULL, " +
            "t.bloqueadoPor = NULL, " +
            "t.fechaActualizacion = :now " +
            "WHERE t.idTarea = :idTarea AND t.bloqueadoPor = :workerId")
    int marcarCanceladoSiConservaReserva(
            @Param("idTarea") Long idTarea,
            @Param("workerId") String workerId,
            @Param("now") OffsetDateTime now
    );

    /**
     * Resultado Incierto por agotamiento o interrupción de espera sin confirmación de salida de red:
     * Transiciona la tarea a RESULTADO_INCIERTO y extiende el lease, impidiendo que otro trabajador
     * tome la tarea y cause un envío solapado mientras el hilo de red anterior pudiera seguir activo.
     * Solo tiene efecto si el trabajador aún posee la reserva.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ActivationOutboxTaskEntity t SET " +
            "t.estado = 'RESULTADO_INCIERTO', " +
            "t.ultimoError = :ultimoError, " +
            "t.bloqueadoHasta = :bloqueadoHasta, " +
            "t.fechaActualizacion = :now " +
            "WHERE t.idTarea = :idTarea AND t.bloqueadoPor = :workerId")
    int marcarResultadoInciertoSiConservaReserva(
            @Param("idTarea") Long idTarea,
            @Param("workerId") String workerId,
            @Param("ultimoError") String ultimoError,
            @Param("bloqueadoHasta") OffsetDateTime bloqueadoHasta,
            @Param("now") OffsetDateTime now
    );
}


package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.StripeProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface SpringDataStripeEventRepository extends JpaRepository<StripeProcessedEventEntity, String> {

    Optional<StripeProcessedEventEntity> findByIdEvento(String idEvento);

    Optional<StripeProcessedEventEntity> findByStripePaymentIntentIdAndEstado(String stripePaymentIntentId, String estado);

    Optional<StripeProcessedEventEntity> findByStripeSessionIdAndEstado(String stripeSessionId, String estado);

    @Modifying
    @Query("UPDATE StripeProcessedEventEntity e SET e.estado = 'PROCESANDO', e.ownerToken = :ownerToken, " +
           "e.leaseExpiresAt = :leaseExpiresAt, e.attemptCount = e.attemptCount + 1, e.mensajeError = null " +
           "WHERE e.idEvento = :idEvento AND " +
           "(e.estado = 'FALLIDO' OR (e.estado = 'PROCESANDO' AND (e.leaseExpiresAt IS NULL OR e.leaseExpiresAt <= :ahora)))")
    int adquirirLeaseEvento(
            @Param("idEvento") String idEvento,
            @Param("ownerToken") String ownerToken,
            @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt,
            @Param("ahora") OffsetDateTime ahora
    );

    @Modifying
    @Query("UPDATE StripeProcessedEventEntity e SET e.estado = 'COMPLETADO', e.idOrden = :idOrden, " +
           "e.codigoOrden = COALESCE(:codigoOrden, e.codigoOrden), " +
           "e.stripeSessionId = COALESCE(:sessionId, e.stripeSessionId), " +
           "e.stripePaymentIntentId = COALESCE(:paymentIntentId, e.stripePaymentIntentId), " +
           "e.fechaProcesamiento = :ahora, e.leaseExpiresAt = null " +
           "WHERE e.idEvento = :idEvento AND e.ownerToken = :ownerToken AND (e.leaseExpiresAt IS NULL OR e.leaseExpiresAt > :ahora)")
    int marcarCompletadoConToken(
            @Param("idEvento") String idEvento,
            @Param("ownerToken") String ownerToken,
            @Param("idOrden") Long idOrden,
            @Param("codigoOrden") String codigoOrden,
            @Param("sessionId") String sessionId,
            @Param("paymentIntentId") String paymentIntentId,
            @Param("ahora") OffsetDateTime ahora
    );

    @Modifying
    @Query("UPDATE StripeProcessedEventEntity e SET e.estado = 'FALLIDO', e.mensajeError = :mensajeError, " +
           "e.fechaProcesamiento = :ahora, e.leaseExpiresAt = null " +
           "WHERE e.idEvento = :idEvento AND e.ownerToken = :ownerToken AND (e.leaseExpiresAt IS NULL OR e.leaseExpiresAt > :ahora)")
    int marcarFallidoConToken(
            @Param("idEvento") String idEvento,
            @Param("ownerToken") String ownerToken,
            @Param("mensajeError") String mensajeError,
            @Param("ahora") OffsetDateTime ahora
    );
}

package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.OrdenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrdenEntity, Long> {

    @Override
    @EntityGraph(attributePaths = {"items"})
    Optional<OrdenEntity> findById(Long id);

    @EntityGraph(attributePaths = {"items"})
    Optional<OrdenEntity> findByCodigoOrden(String codigoOrden);

    @EntityGraph(attributePaths = {"items"})
    Optional<OrdenEntity> findByStripeSessionId(String stripeSessionId);

    @EntityGraph(attributePaths = {"items"})
    Optional<OrdenEntity> findByStripePaymentIntentId(String stripePaymentIntentId);

    @EntityGraph(attributePaths = {"items"})
    Optional<OrdenEntity> findByIzipayTransactionId(String izipayTransactionId);

    @EntityGraph(attributePaths = {"items"})
    Optional<OrdenEntity> findByIzipayOrderId(String izipayOrderId);

    @EntityGraph(attributePaths = {"items"})
    Optional<OrdenEntity> findByClaveIdempotencia(String claveIdempotencia);

    // Listados administrativos ligeros (items lazy, sin N+1 ni joins pesados)
    List<OrdenEntity> findByIdUserOrderByFechaOrdenDesc(Long idUser);
    List<OrdenEntity> findAllByOrderByFechaOrdenDesc();
    List<OrdenEntity> findByEstadoOrdenOrderByFechaOrdenDesc(com.lysandri.domain.model.EstadoOrden estadoOrden);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrdenEntity o WHERE o.idOrden = :id")
    Optional<OrdenEntity> findByIdWithLock(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrdenEntity o WHERE o.codigoOrden = :codigo")
    Optional<OrdenEntity> findByCodigoOrdenWithLock(@Param("codigo") String codigo);

    @Query("SELECT o FROM OrdenEntity o WHERE UPPER(o.bancoDestino) = UPPER(:bancoDestino) AND UPPER(o.cuentaDestino) = UPPER(:cuentaDestino) AND TRIM(o.numeroOperacion) = TRIM(:numeroOperacion) AND o.estadoOrden = com.lysandri.domain.model.EstadoOrden.PAGADO")
    List<OrdenEntity> findAbonosBancariosPagados(
            @Param("bancoDestino") String bancoDestino,
            @Param("cuentaDestino") String cuentaDestino,
            @Param("numeroOperacion") String numeroOperacion
    );

    @Query(value = "SELECT * FROM ordenes o WHERE o.estado_orden = 'PAGADO' AND o.numero_operacion IS NOT NULL AND translate(UPPER(o.numero_operacion), ' -./_', '') = :opNorm", nativeQuery = true)
    List<OrdenEntity> findAbonosHistoricosCandidatos(@Param("opNorm") String opNorm);

    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(:operation, 0))", nativeQuery = true)
    Object lockManualPaymentOperation(@Param("operation") String operation);
}

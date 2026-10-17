package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.OrdenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrdenEntity, Long> {
    Optional<OrdenEntity> findByCodigoOrden(String codigoOrden);
    Optional<OrdenEntity> findByStripeSessionId(String stripeSessionId);
    Optional<OrdenEntity> findByIzipayTransactionId(String izipayTransactionId);
    Optional<OrdenEntity> findByIzipayOrderId(String izipayOrderId);
    List<OrdenEntity> findByIdUserOrderByFechaOrdenDesc(Long idUser);
    List<OrdenEntity> findAllByOrderByFechaOrdenDesc();
    List<OrdenEntity> findByEstadoOrdenOrderByFechaOrdenDesc(com.lysandri.domain.model.EstadoOrden estadoOrden);
}

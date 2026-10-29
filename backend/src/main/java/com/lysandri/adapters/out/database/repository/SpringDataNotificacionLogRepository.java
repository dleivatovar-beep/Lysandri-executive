package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.NotificacionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataNotificacionLogRepository extends JpaRepository<NotificacionLogEntity, Long> {

    Optional<NotificacionLogEntity> findByClaveIdempotencia(String claveIdempotencia);

    Optional<NotificacionLogEntity> findByIdOrdenAndTipoNotificacion(Long idOrden, String tipoNotificacion);
}

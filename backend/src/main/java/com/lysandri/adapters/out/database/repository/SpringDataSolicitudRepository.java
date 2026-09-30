package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.SolicitudInformacionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataSolicitudRepository extends JpaRepository<SolicitudInformacionEntity, Long> {
    List<SolicitudInformacionEntity> findAllByOrderByFechaCreacionDesc();
}

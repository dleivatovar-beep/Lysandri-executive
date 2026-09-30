package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.HistorialConsultaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataHistorialRepository extends JpaRepository<HistorialConsultaEntity, Long> {
    List<HistorialConsultaEntity> findBySesionIdOrderByFechaConsultaAsc(String sesionId);
}

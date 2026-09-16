package com.lysandri.api.repository;

import com.lysandri.api.model.entity.ActividadBloque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActividadBloqueRepository
        extends JpaRepository<ActividadBloque, Integer> {

    List<ActividadBloque> findByBloqueIdBloqueOrderByOrdenAsc(
            Integer bloqueId
    );

    Optional<ActividadBloque> findByIdActividadAndBloqueIdBloque(
            Integer actividadId,
            Integer bloqueId
    );

    boolean existsByBloqueIdBloqueAndOrden(
            Integer bloqueId,
            Integer orden
    );

    boolean existsByBloqueIdBloqueAndOrdenAndIdActividadNot(
            Integer bloqueId,
            Integer orden,
            Integer actividadId
    );
}
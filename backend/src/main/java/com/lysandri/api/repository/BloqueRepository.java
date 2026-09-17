package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Bloque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BloqueRepository extends JpaRepository<Bloque, Integer> {

    List<Bloque> findByProgramaIdProgramaOrderByOrdenAsc(
            Integer programaId
    );

    Optional<Bloque> findByIdBloqueAndProgramaIdPrograma(
            Integer bloqueId,
            Integer programaId
    );

    boolean existsByProgramaIdProgramaAndOrden(
            Integer programaId,
            Integer orden
    );

    boolean existsByProgramaIdProgramaAndOrdenAndIdBloqueNot(
            Integer programaId,
            Integer orden,
            Integer bloqueId
    );
}
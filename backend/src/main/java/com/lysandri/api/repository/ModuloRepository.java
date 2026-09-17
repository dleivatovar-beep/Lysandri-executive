package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Modulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModuloRepository extends JpaRepository<Modulo, Integer> {

    List<Modulo> findByBloqueIdBloqueOrderByOrdenAsc(
            Integer bloqueId
    );

    Optional<Modulo> findByIdModuloAndBloqueIdBloque(
            Integer moduloId,
            Integer bloqueId
    );

    boolean existsByBloqueIdBloqueAndOrden(
            Integer bloqueId,
            Integer orden
    );

    boolean existsByBloqueIdBloqueAndOrdenAndIdModuloNot(
            Integer bloqueId,
            Integer orden,
            Integer moduloId
    );
}
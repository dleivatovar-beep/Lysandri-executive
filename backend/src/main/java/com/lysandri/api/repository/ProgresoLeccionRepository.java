package com.lysandri.api.repository;

import com.lysandri.api.model.entity.ProgresoLeccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgresoLeccionRepository extends JpaRepository<ProgresoLeccion, Integer> {

    List<ProgresoLeccion> findByUsuarioIdUser(Integer idUser);

    Optional<ProgresoLeccion> findByUsuarioIdUserAndLeccionIdLeccion(Integer idUser, Integer idLeccion);

    long countByUsuarioIdUserAndLeccionProgramaIdProgramaAndCompletadoTrue(Integer idUser, Integer idPrograma);
}

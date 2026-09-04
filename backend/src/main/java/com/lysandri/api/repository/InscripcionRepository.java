package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Integer> {

    List<Inscripcion> findByUsuarioIdUser(Integer idUser);

    List<Inscripcion> findByProgramaIdPrograma(Integer idPrograma);

    Optional<Inscripcion> findByUsuarioIdUserAndProgramaIdPrograma(Integer idUser, Integer idPrograma);
}

package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Programa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramaRepository extends JpaRepository<Programa, Integer> {

    List<Programa> findByInstructorIdInstructorDni(String idInstructorDni);

    List<Programa> findByTipoPrograma(String tipoPrograma);

    List<Programa> findByLevel(String level);
}

package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Leccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeccionRepository extends JpaRepository<Leccion, Integer> {

    List<Leccion> findByProgramaIdProgramaOrderByIdLeccionAsc(Integer idPrograma);
}

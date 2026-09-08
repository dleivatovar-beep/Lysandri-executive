package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Leccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeccionRepository extends JpaRepository<Leccion, Integer> {

    List<Leccion> findByProgramaIdProgramaOrderByOrdenAsc(Integer idPrograma);

    List<Leccion> findByProgramaIdProgramaOrderByIdLeccionAsc(Integer idPrograma);

    long countByProgramaIdPrograma(Integer idPrograma);

    @Query("SELECT l FROM Leccion l WHERE l.programa.idPrograma = :programaId ORDER BY COALESCE(l.orden, l.idLeccion) ASC")
    List<Leccion> findByProgramaIdOrderByOrdenAsc(@Param("programaId") Integer programaId);

    default List<Leccion> findByProgramaIdOrderByOrdenAsc(Long programaId) {
        return findByProgramaIdOrderByOrdenAsc(programaId != null ? programaId.intValue() : null);
    }

    default long countByProgramaIdPrograma(Long programaId) {
        return countByProgramaIdPrograma(programaId != null ? programaId.intValue() : null);
    }
}

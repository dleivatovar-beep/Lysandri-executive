
package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Leccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeccionRepository
        extends JpaRepository<Leccion, Integer> {

    /*
     * Consultas antiguas por programa.
     * Se mantienen para no romper las funciones existentes.
     */
    List<Leccion> findByProgramaIdProgramaOrderByOrdenAsc(
            Integer programaId
    );

    List<Leccion> findByProgramaIdProgramaOrderByIdLeccionAsc(
            Integer programaId
    );

    long countByProgramaIdPrograma(
            Integer programaId
    );

    @Query("""
            SELECT l
            FROM Leccion l
            WHERE l.programa.idPrograma = :programaId
            ORDER BY COALESCE(l.orden, l.idLeccion) ASC
            """)
    List<Leccion> findByProgramaIdOrderByOrdenAsc(
            @Param("programaId") Integer programaId
    );

    /*
     * Nueva consulta para obtener los contenidos
     * pertenecientes a un módulo.
     */
    List<Leccion> findByModuloIdModuloOrderByOrdenAsc(
            Integer moduloId
    );

    boolean existsByModuloIdModuloAndOrden(
            Integer moduloId,
            Integer orden
    );

    boolean existsByModuloIdModuloAndOrdenAndIdLeccionNot(
            Integer moduloId,
            Integer orden,
            Integer leccionId
    );

    /*
     * Métodos de compatibilidad con servicios que usan Long.
     */
    default List<Leccion> findByProgramaIdOrderByOrdenAsc(
            Long programaId
    ) {
        return findByProgramaIdOrderByOrdenAsc(
                programaId != null
                        ? programaId.intValue()
                        : null
        );
    }

    default long countByProgramaIdPrograma(
            Long programaId
    ) {
        return countByProgramaIdPrograma(
                programaId != null
                        ? programaId.intValue()
                        : null
        );
    }
}
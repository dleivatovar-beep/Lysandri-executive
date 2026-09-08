package com.lysandri.api.repository;

import com.lysandri.api.model.entity.ProgresoLeccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgresoLeccionRepository extends JpaRepository<ProgresoLeccion, Integer> {

    List<ProgresoLeccion> findByUsuarioIdUser(Integer idUser);

    Optional<ProgresoLeccion> findByUsuarioIdUserAndLeccionIdLeccion(Integer idUser, Integer idLeccion);

    long countByUsuarioIdUserAndLeccionProgramaIdProgramaAndCompletadoTrue(Integer idUser, Integer idPrograma);

    @Query("SELECT p FROM ProgresoLeccion p WHERE p.usuario.idUser = :usuarioId AND p.leccion.idLeccion = :leccionId")
    Optional<ProgresoLeccion> findByUsuarioIdAndLeccionId(@Param("usuarioId") Integer usuarioId, @Param("leccionId") Integer leccionId);

    @Query("SELECT COUNT(p) FROM ProgresoLeccion p WHERE p.usuario.idUser = :usuarioId AND p.leccion.programa.idPrograma = :programaId AND p.completado = true")
    long countLeccionesCompletadas(@Param("usuarioId") Integer usuarioId, @Param("programaId") Integer programaId);
}

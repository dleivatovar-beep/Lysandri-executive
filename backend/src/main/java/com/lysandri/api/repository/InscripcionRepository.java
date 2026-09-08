package com.lysandri.api.repository;

import com.lysandri.api.model.entity.Inscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Integer> {

    List<Inscripcion> findByUsuarioIdUser(Integer idUser);

    List<Inscripcion> findByProgramaIdPrograma(Integer idPrograma);

    Optional<Inscripcion> findByUsuarioIdUserAndProgramaIdPrograma(Integer idUser, Integer idPrograma);

    boolean existsByUsuarioIdUserAndProgramaIdPrograma(Integer idUser, Integer idPrograma);

    @Query("SELECT COUNT(i) > 0 FROM Inscripcion i WHERE i.usuario.idUser = :usuarioId AND i.programa.idPrograma = :programaId")
    boolean existsByUsuarioIdAndProgramaId(@Param("usuarioId") Integer usuarioId, @Param("programaId") Integer programaId);

    @Query("SELECT i FROM Inscripcion i WHERE i.usuario.idUser = :usuarioId")
    List<Inscripcion> findByUsuarioId(@Param("usuarioId") Integer usuarioId);
}

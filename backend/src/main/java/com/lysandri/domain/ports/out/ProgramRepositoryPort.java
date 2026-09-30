package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.Programa;

import java.util.List;
import java.util.Optional;

public interface ProgramRepositoryPort {

    Programa guardar(Programa programa);

    Optional<Programa> buscarPorId(Long idPrograma);

    Optional<Programa> buscarPorSlug(String slug);

    Optional<Programa> buscarPorMoodleCourseId(Long moodleCourseId);

    List<Programa> listarActivos();

    List<Programa> buscarPorIds(List<Long> ids);
}

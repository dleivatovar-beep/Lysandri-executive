package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.Programa;

import java.util.List;

public interface CatalogUseCase {

    List<Programa> listarProgramasActivos();

    Programa obtenerProgramaPorSlug(String slug);

    Programa obtenerProgramaPorId(Long idPrograma);
}

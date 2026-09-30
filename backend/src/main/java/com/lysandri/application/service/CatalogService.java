package com.lysandri.application.service;

import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.in.CatalogUseCase;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService implements CatalogUseCase {

    private final ProgramRepositoryPort programRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Programa> listarProgramasActivos() {
        return programRepository.listarActivos();
    }

    @Override
    @Transactional(readOnly = true)
    public Programa obtenerProgramaPorSlug(String slug) {
        return programRepository.buscarPorSlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Programa no encontrado para el slug: " + slug));
    }

    @Override
    @Transactional(readOnly = true)
    public Programa obtenerProgramaPorId(Long idPrograma) {
        return programRepository.buscarPorId(idPrograma)
                .orElseThrow(() -> new IllegalArgumentException("Programa no encontrado con ID: " + idPrograma));
    }
}

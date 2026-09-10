package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.ProgramaRequest;
import com.lysandri.api.dto.response.ProgramaResponse;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Instructor;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.repository.InstructorRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.service.ProgramaService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramaServiceImpl
        implements ProgramaService {

    private final ProgramaRepository
            programaRepository;

    private final InstructorRepository
            instructorRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProgramaResponse>
    getAllProgramas() {
        return programaRepository
            .findAll()
            .stream()
            .map(this::mapToProgramaResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramaResponse
    getProgramaById(Integer id) {
        Programa programa =
            programaRepository
                .findById(id)
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Programa no encontrado con id: "
                                + id
                        )
                );

        return mapToProgramaResponse(
            programa
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProgramaResponse>
    getProgramasByInstructorUserId(
            Integer usuarioId
    ) {
        Instructor instructor =
            instructorRepository
                .findByUsuarioIdUser(
                    usuarioId
                )
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "No existe un perfil de instructor "
                                + "para el usuario autenticado"
                        )
                );

        return programaRepository
            .findByInstructorIdInstructorDni(
                instructor
                    .getIdInstructorDni()
            )
            .stream()
            .map(this::mapToProgramaResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProgramaResponse createPrograma(
            ProgramaRequest request
    ) {
        Instructor instructor =
            instructorRepository
                .findById(
                    request
                        .getIdInstructorDni()
                )
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Instructor no encontrado con DNI: "
                                + request
                                    .getIdInstructorDni()
                        )
                );

        Programa programa =
            Programa.builder()
                .instructor(instructor)
                .tituloPrograma(
                    request
                        .getTituloPrograma()
                )
                .duracionPrograma(
                    request
                        .getDuracionPrograma()
                )
                .tipoPrograma(
                    request
                        .getTipoPrograma()
                )
                .level(
                    request.getLevel()
                )
                .fechaInicioGlobal(
                    request
                        .getFechaInicioGlobal()
                )
                .fechaFinalGlobal(
                    request
                        .getFechaFinalGlobal()
                )
                .requisitos(
                    request.getRequisitos()
                )
                .metodologia(
                    request.getMetodologia()
                )
                .build();

        Programa guardado =
            programaRepository.save(
                programa
            );

        return mapToProgramaResponse(
            guardado
        );
    }

    @Override
    @Transactional
    public ProgramaResponse updatePrograma(
            Integer id,
            ProgramaRequest request
    ) {
        Programa programa =
            programaRepository
                .findById(id)
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Programa no encontrado con id: "
                                + id
                        )
                );

        Instructor instructor =
            instructorRepository
                .findById(
                    request
                        .getIdInstructorDni()
                )
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Instructor no encontrado con DNI: "
                                + request
                                    .getIdInstructorDni()
                        )
                );

        programa.setInstructor(
            instructor
        );

        programa.setTituloPrograma(
            request.getTituloPrograma()
        );

        programa.setDuracionPrograma(
            request.getDuracionPrograma()
        );

        programa.setTipoPrograma(
            request.getTipoPrograma()
        );

        programa.setLevel(
            request.getLevel()
        );

        programa.setFechaInicioGlobal(
            request.getFechaInicioGlobal()
        );

        programa.setFechaFinalGlobal(
            request.getFechaFinalGlobal()
        );

        programa.setRequisitos(
            request.getRequisitos()
        );

        programa.setMetodologia(
            request.getMetodologia()
        );

        Programa actualizado =
            programaRepository.save(
                programa
            );

        return mapToProgramaResponse(
            actualizado
        );
    }

    @Override
    @Transactional
    public void deletePrograma(
            Integer id
    ) {
        if (
            !programaRepository
                .existsById(id)
        ) {
            throw new ResourceNotFoundException(
                "Programa no encontrado con id: "
                    + id
            );
        }

        programaRepository.deleteById(id);
    }

    private ProgramaResponse
    mapToProgramaResponse(
            Programa programa
    ) {
        String nombreInstructor =
            programa.getInstructor() != null
            && programa
                .getInstructor()
                .getUsuario() != null
                ? programa
                    .getInstructor()
                    .getUsuario()
                    .getNombreCompleto()
                : "Sin asignar";

        return ProgramaResponse.builder()
            .idPrograma(
                programa.getIdPrograma()
            )
            .idInstructorDni(
                programa.getInstructor()
                    != null
                    ? programa
                        .getInstructor()
                        .getIdInstructorDni()
                    : null
            )
            .nombreInstructor(
                nombreInstructor
            )
            .tituloPrograma(
                programa
                    .getTituloPrograma()
            )
            .duracionPrograma(
                programa
                    .getDuracionPrograma()
            )
            .tipoPrograma(
                programa
                    .getTipoPrograma()
            )
            .level(
                programa.getLevel()
            )
            .fechaInicioGlobal(
                programa
                    .getFechaInicioGlobal()
            )
            .fechaFinalGlobal(
                programa
                    .getFechaFinalGlobal()
            )
            .requisitos(
                programa.getRequisitos()
            )
            .metodologia(
                programa.getMetodologia()
            )
            .build();
    }
}
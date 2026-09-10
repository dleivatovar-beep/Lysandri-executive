package com.lysandri.api.service.impl;

import com.lysandri.api.dto.response.EstudianteInscritoResponse;
import com.lysandri.api.dto.response.InscripcionResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Inscripcion;
import com.lysandri.api.model.entity.Instructor;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.model.enums.Rol;
import com.lysandri.api.repository.InscripcionRepository;
import com.lysandri.api.repository.InstructorRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.service.InscripcionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InscripcionServiceImpl
        implements InscripcionService {

    private final InscripcionRepository
            inscripcionRepository;

    private final UsuarioRepository
            usuarioRepository;

    private final ProgramaRepository
            programaRepository;

    private final InstructorRepository
            instructorRepository;

    @Override
    @Transactional
    public InscripcionResponse
    inscribirEstudiante(
            Long usuarioId,
            Long programaId
    ) {
        if (usuarioId == null) {
            throw new BadRequestException(
                "El ID del usuario es obligatorio "
                    + "para la inscripción"
            );
        }

        if (programaId == null) {
            throw new BadRequestException(
                "El ID del programa es obligatorio "
                    + "para la inscripción"
            );
        }

        Usuario usuario =
            usuarioRepository
                .findById(
                    usuarioId.intValue()
                )
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Usuario no encontrado con id: "
                                + usuarioId
                        )
                );

        Programa programa =
            programaRepository
                .findById(
                    programaId.intValue()
                )
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Programa no encontrado con id: "
                                + programaId
                        )
                );

        if (
            usuario.getRol()
                != Rol.ESTUDIANTE
        ) {
            throw new BadRequestException(
                "Solo los usuarios con rol "
                    + "ESTUDIANTE pueden inscribirse"
            );
        }

        boolean yaInscrito =
            inscripcionRepository
                .existsByUsuarioIdAndProgramaId(
                    usuarioId.intValue(),
                    programaId.intValue()
                );

        if (yaInscrito) {
            throw new BadRequestException(
                "El estudiante ya se encuentra "
                    + "inscrito en este programa"
            );
        }

        Inscripcion inscripcion =
            Inscripcion.builder()
                .usuario(usuario)
                .programa(programa)
                .fechaInscripcion(
                    OffsetDateTime.now()
                )
                .estatus("ACTIVO")
                .porcentajeProgreso(
                    BigDecimal.ZERO
                )
                .build();

        Inscripcion guardada =
            inscripcionRepository.save(
                inscripcion
            );

        return mapToInscripcionResponse(
            guardada
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InscripcionResponse>
    listarCursosDeEstudiante(
            Long usuarioId
    ) {
        if (
            !usuarioRepository.existsById(
                usuarioId.intValue()
            )
        ) {
            throw new ResourceNotFoundException(
                "Usuario no encontrado con id: "
                    + usuarioId
            );
        }

        return inscripcionRepository
            .findByUsuarioId(
                usuarioId.intValue()
            )
            .stream()
            .map(
                this::
                mapToInscripcionResponse
            )
            .collect(
                Collectors.toList()
            );
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstudianteInscritoResponse>
    listarEstudiantesDelProfesor(
            Integer usuarioInstructorId,
            Long programaId
    ) {
        Instructor instructor =
            instructorRepository
                .findByUsuarioIdUser(
                    usuarioInstructorId
                )
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "No existe un perfil de "
                                + "instructor para el usuario "
                                + "autenticado"
                        )
                );

        Programa programa =
            programaRepository
                .findById(
                    programaId.intValue()
                )
                .orElseThrow(
                    () ->
                        new ResourceNotFoundException(
                            "Programa no encontrado con id: "
                                + programaId
                        )
                );

        String dniDelPrograma =
            programa
                .getInstructor()
                .getIdInstructorDni();

        String dniAutenticado =
            instructor
                .getIdInstructorDni();

        if (
            !dniDelPrograma.equals(
                dniAutenticado
            )
        ) {
            throw new BadRequestException(
                "No tienes permisos para consultar "
                    + "los estudiantes de este programa"
            );
        }

        return inscripcionRepository
            .findByProgramaIdPrograma(
                programaId.intValue()
            )
            .stream()
            .map(
                this::
                mapToEstudianteInscritoResponse
            )
            .collect(
                Collectors.toList()
            );
    }

    private InscripcionResponse
    mapToInscripcionResponse(
            Inscripcion inscripcion
    ) {
        return InscripcionResponse.builder()
            .id(
                inscripcion
                    .getIdInscripcion()
                    != null
                    ? Long.valueOf(
                        inscripcion
                            .getIdInscripcion()
                    )
                    : null
            )
            .usuarioId(
                inscripcion.getUsuario()
                    != null
                    ? Long.valueOf(
                        inscripcion
                            .getUsuario()
                            .getIdUser()
                    )
                    : null
            )
            .nombreEstudiante(
                inscripcion.getUsuario()
                    != null
                    ? inscripcion
                        .getUsuario()
                        .getNombreCompleto()
                    : null
            )
            .programaId(
                inscripcion.getPrograma()
                    != null
                    ? Long.valueOf(
                        inscripcion
                            .getPrograma()
                            .getIdPrograma()
                    )
                    : null
            )
            .tituloPrograma(
                inscripcion.getPrograma()
                    != null
                    ? inscripcion
                        .getPrograma()
                        .getTituloPrograma()
                    : null
            )
            .fechaInscripcion(
                inscripcion
                    .getFechaInscripcion()
            )
            .estado(
                inscripcion.getEstatus()
            )
            .build();
    }

    private EstudianteInscritoResponse
    mapToEstudianteInscritoResponse(
            Inscripcion inscripcion
    ) {
        Usuario usuario =
            inscripcion.getUsuario();

        Programa programa =
            inscripcion.getPrograma();

        return EstudianteInscritoResponse
            .builder()
            .idInscripcion(
                Long.valueOf(
                    inscripcion
                        .getIdInscripcion()
                )
            )
            .usuarioId(
                Long.valueOf(
                    usuario.getIdUser()
                )
            )
            .nombres(
                usuario.getNombres()
            )
            .apellidos(
                usuario.getApellidos()
            )
            .email(
                usuario.getEmail()
            )
            .telefono(
                usuario.getTelefono()
            )
            .programaId(
                Long.valueOf(
                    programa.getIdPrograma()
                )
            )
            .tituloPrograma(
                programa.getTituloPrograma()
            )
            .fechaInscripcion(
                inscripcion
                    .getFechaInscripcion()
            )
            .estado(
                inscripcion.getEstatus()
            )
            .porcentajeProgreso(
                inscripcion
                    .getPorcentajeProgreso()
            )
            .build();
    }
}
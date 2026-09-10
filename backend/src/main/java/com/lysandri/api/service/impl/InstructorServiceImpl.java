package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.InstructorRequest;
import com.lysandri.api.dto.response.InstructorResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Instructor;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.model.enums.Rol;
import com.lysandri.api.repository.InstructorRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.service.InstructorService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InstructorServiceImpl
        implements InstructorService {

    private final InstructorRepository
            instructorRepository;

    private final UsuarioRepository
            usuarioRepository;

    private final ProgramaRepository
            programaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InstructorResponse>
    listarInstructores() {
        return instructorRepository
                .findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorResponse obtenerInstructor(
            String dni
    ) {
        return mapToResponse(
                buscarPorDni(dni)
        );
    }

    @Override
    @Transactional
    public InstructorResponse crearInstructor(
            InstructorRequest request
    ) {
        String dni =
                request
                    .getIdInstructorDni()
                    .trim();

        if (
            instructorRepository
                .existsById(dni)
        ) {
            throw new BadRequestException(
                "Ya existe un instructor con DNI: "
                    + dni
            );
        }

        Usuario usuario =
                validarUsuarioInstructor(
                    request.getUsuarioId(),
                    null
                );

        Instructor instructor =
                Instructor.builder()
                    .idInstructorDni(dni)
                    .usuario(usuario)
                    .especialidad(
                        limpiar(
                            request
                                .getEspecialidad()
                        )
                    )
                    .direccionInstructor(
                        limpiar(
                            request
                                .getDireccionInstructor()
                        )
                    )
                    .build();

        Instructor guardado =
                instructorRepository.save(
                    instructor
                );

        return mapToResponse(guardado);
    }

    @Override
    @Transactional
    public InstructorResponse
    actualizarInstructor(
            String dni,
            InstructorRequest request
    ) {
        Instructor instructor =
                buscarPorDni(dni);

        Usuario usuario =
                validarUsuarioInstructor(
                    request.getUsuarioId(),
                    instructor
                        .getIdInstructorDni()
                );

        instructor.setUsuario(usuario);

        instructor.setEspecialidad(
            limpiar(
                request.getEspecialidad()
            )
        );

        instructor.setDireccionInstructor(
            limpiar(
                request
                    .getDireccionInstructor()
            )
        );

        Instructor actualizado =
                instructorRepository.save(
                    instructor
                );

        return mapToResponse(actualizado);
    }

    @Override
    @Transactional
    public void eliminarInstructor(
            String dni
    ) {
        Instructor instructor =
                buscarPorDni(dni);

        boolean tieneProgramas =
                !programaRepository
                    .findByInstructorIdInstructorDni(
                        dni
                    )
                    .isEmpty();

        if (tieneProgramas) {
            throw new BadRequestException(
                "No se puede eliminar el instructor "
                    + "porque tiene programas asignados"
            );
        }

        instructorRepository.delete(
            instructor
        );
    }

    private Instructor buscarPorDni(
            String dni
    ) {
        return instructorRepository
            .findById(dni)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Instructor no encontrado con DNI: "
                            + dni
                    )
            );
    }

    private Usuario validarUsuarioInstructor(
            Integer usuarioId,
            String dniActual
    ) {
        Usuario usuario =
                usuarioRepository
                    .findById(usuarioId)
                    .orElseThrow(
                        () ->
                            new ResourceNotFoundException(
                                "Usuario no encontrado con id: "
                                    + usuarioId
                            )
                    );

        if (
            usuario.getRol()
                != Rol.INSTRUCTOR
        ) {
            throw new BadRequestException(
                "El usuario seleccionado debe "
                    + "tener el rol INSTRUCTOR"
            );
        }

        instructorRepository
            .findByUsuarioIdUser(usuarioId)
            .ifPresent(
                existente -> {
                    boolean esOtroInstructor =
                            dniActual == null
                            || !existente
                                .getIdInstructorDni()
                                .equals(dniActual);

                    if (esOtroInstructor) {
                        throw new BadRequestException(
                            "El usuario ya está asociado "
                                + "al instructor con DNI: "
                                + existente
                                    .getIdInstructorDni()
                        );
                    }
                }
            );

        return usuario;
    }

    private String limpiar(
            String valor
    ) {
        if (
            valor == null
            || valor.trim().isEmpty()
        ) {
            return null;
        }

        return valor.trim();
    }

    private InstructorResponse mapToResponse(
            Instructor instructor
    ) {
        Usuario usuario =
                instructor.getUsuario();

        return InstructorResponse.builder()
            .idInstructorDni(
                instructor
                    .getIdInstructorDni()
            )
            .usuarioId(
                usuario.getIdUser()
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
            .especialidad(
                instructor.getEspecialidad()
            )
            .direccionInstructor(
                instructor
                    .getDireccionInstructor()
            )
            .build();
    }
}
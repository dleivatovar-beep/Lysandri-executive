
package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.InstructorRequest;
import com.lysandri.api.dto.response.InstructorResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Instructor;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.model.enums.Rol;
import com.lysandri.api.repository.InstructorRepository;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.service.InstructorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InstructorServiceImpl
        implements InstructorService {

    private final InstructorRepository instructorRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public InstructorResponse crearInstructor(
            InstructorRequest request
    ) {
        validarRequest(request);

        String instructorDni =
                request.getIdInstructorDni().trim();

        if (instructorRepository.existsById(instructorDni)) {
            throw new BadRequestException(
                    "Ya existe un instructor con el DNI: "
                            + instructorDni
            );
        }

        Usuario usuario =
                usuarioRepository
                        .findById(request.getUsuarioId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Usuario no encontrado con id: "
                                                + request.getUsuarioId()
                                )
                        );

        if (usuario.getRol() != Rol.INSTRUCTOR) {
            throw new BadRequestException(
                    "El usuario seleccionado no tiene el rol INSTRUCTOR"
            );
        }

        boolean usuarioYaAsignado =
                instructorRepository
                        .findByUsuarioIdUser(
                                usuario.getIdUser()
                        )
                        .isPresent();

        if (usuarioYaAsignado) {
            throw new BadRequestException(
                    "El usuario ya tiene un perfil de instructor"
            );
        }

        Instructor instructor =
                new Instructor();

        instructor.setIdInstructorDni(
                instructorDni
        );
        instructor.setUsuario(
                usuario
        );
        instructor.setEspecialidad(
                request.getEspecialidad()
        );
        instructor.setDireccionInstructor(
                request.getDireccionInstructor()
        );

        Instructor guardado =
                instructorRepository.save(instructor);

        return convertirRespuesta(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstructorResponse> listarInstructores() {
        List<Instructor> instructores =
                instructorRepository.findAll();

        List<InstructorResponse> respuestas =
                new ArrayList<>();

        for (Instructor instructor : instructores) {
            respuestas.add(
                    convertirRespuesta(instructor)
            );
        }

        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public InstructorResponse obtenerInstructor(
            String instructorDni
    ) {
        if (
                instructorDni == null
                        || instructorDni.isBlank()
        ) {
            throw new BadRequestException(
                    "El DNI del instructor es obligatorio"
            );
        }

        Instructor instructor =
                instructorRepository
                        .findById(instructorDni.trim())
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Instructor no encontrado con DNI: "
                                                + instructorDni
                                )
                        );

        return convertirRespuesta(instructor);
    }

    private void validarRequest(
            InstructorRequest request
    ) {
        if (request == null) {
            throw new BadRequestException(
                    "Los datos del instructor son obligatorios"
            );
        }

        if (
                request.getIdInstructorDni() == null
                        || request
                        .getIdInstructorDni()
                        .isBlank()
        ) {
            throw new BadRequestException(
                    "El DNI del instructor es obligatorio"
            );
        }

        if (request.getUsuarioId() == null) {
            throw new BadRequestException(
                    "El identificador del usuario es obligatorio"
            );
        }
    }

    private InstructorResponse convertirRespuesta(
            Instructor instructor
    ) {
        Usuario usuario =
                instructor.getUsuario();

        InstructorResponse response =
                new InstructorResponse();

        response.setIdInstructorDni(
                instructor.getIdInstructorDni()
        );
        response.setUsuarioId(
                usuario.getIdUser()
        );
        response.setNombreCompleto(
                usuario.getNombreCompleto()
        );
        response.setEmail(
                usuario.getEmail()
        );
        response.setEspecialidad(
                instructor.getEspecialidad()
        );
        response.setDireccionInstructor(
                instructor.getDireccionInstructor()
        );

        return response;
    }
}
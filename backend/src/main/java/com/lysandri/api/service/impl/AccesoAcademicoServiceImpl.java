
package com.lysandri.api.service.impl;

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
import com.lysandri.api.service.AccesoAcademicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AccesoAcademicoServiceImpl
        implements AccesoAcademicoService {

    private final UsuarioRepository usuarioRepository;
    private final ProgramaRepository programaRepository;
    private final InstructorRepository instructorRepository;
    private final InscripcionRepository inscripcionRepository;

    @Override
    @Transactional(readOnly = true)
    public void validarLecturaPrograma(Integer programaId) {
        Usuario usuario = obtenerUsuarioAutenticado();
        Programa programa = buscarPrograma(programaId);

        if (usuario.getRol() == Rol.ADMIN) {
            return;
        }

        if (usuario.getRol() == Rol.INSTRUCTOR) {
            validarProgramaDelInstructor(
                    usuario,
                    programa
            );
            return;
        }

        if (usuario.getRol() == Rol.ESTUDIANTE) {
            validarInscripcionActiva(
                    usuario,
                    programa
            );
            return;
        }

        throw new AccessDeniedException(
                "No tienes permiso para acceder a este programa"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public void validarGestionPrograma(Integer programaId) {
        Usuario usuario = obtenerUsuarioAutenticado();
        Programa programa = buscarPrograma(programaId);

        if (usuario.getRol() == Rol.ADMIN) {
            return;
        }

        if (usuario.getRol() == Rol.INSTRUCTOR) {
            validarProgramaDelInstructor(
                    usuario,
                    programa
            );
            return;
        }

        throw new AccessDeniedException(
                "No tienes permiso para administrar este programa"
        );
    }

    private Usuario obtenerUsuarioAutenticado() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (
                authentication == null
                        || !authentication.isAuthenticated()
                        || authentication
                        instanceof AnonymousAuthenticationToken
        ) {
            throw new AccessDeniedException(
                    "Debes iniciar sesión para acceder"
            );
        }

        String email = authentication.getName();

        return usuarioRepository
                .findByEmail(email)
                .orElseThrow(
                        () -> new AccessDeniedException(
                                "El usuario autenticado no existe"
                        )
                );
    }

    private Programa buscarPrograma(Integer programaId) {
        if (programaId == null) {
            throw new ResourceNotFoundException(
                    "Programa no encontrado"
            );
        }

        return programaRepository
                .findById(programaId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Programa no encontrado con id: "
                                        + programaId
                        )
                );
    }

    private void validarProgramaDelInstructor(
            Usuario usuario,
            Programa programa
    ) {
        Instructor instructor =
                instructorRepository
                        .findByUsuarioIdUser(
                                usuario.getIdUser()
                        )
                        .orElseThrow(
                                () -> new AccessDeniedException(
                                        "El usuario no tiene un perfil de instructor"
                                )
                        );

        Instructor instructorAsignado =
                programa.getInstructor();

        boolean esPropietario =
                instructorAsignado != null
                        && instructorAsignado
                        .getIdInstructorDni()
                        .equals(
                                instructor.getIdInstructorDni()
                        );

        if (!esPropietario) {
            throw new AccessDeniedException(
                    "El programa no está asignado a este instructor"
            );
        }
    }

    private void validarInscripcionActiva(
            Usuario usuario,
            Programa programa
    ) {
        Inscripcion inscripcion =
                inscripcionRepository
                        .findByUsuarioIdUserAndProgramaIdPrograma(
                                usuario.getIdUser(),
                                programa.getIdPrograma()
                        )
                        .orElseThrow(
                                () -> new AccessDeniedException(
                                        "El estudiante no está inscrito en este programa"
                                )
                        );

        if (
                inscripcion.getEstatus() == null
                        || !"ACTIVO".equalsIgnoreCase(
                        inscripcion.getEstatus()
                )
        ) {
            throw new AccessDeniedException(
                    "La inscripción no está activa"
            );
        }

        LocalDate fechaLimite =
                inscripcion.getFechaLimiteAcceso();

        if (
                fechaLimite != null
                        && fechaLimite.isBefore(
                        LocalDate.now()
                )
        ) {
            throw new AccessDeniedException(
                    "El acceso al programa ha vencido"
            );
        }
    }
}
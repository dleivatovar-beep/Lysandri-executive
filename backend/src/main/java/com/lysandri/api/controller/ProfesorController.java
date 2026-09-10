package com.lysandri.api.controller;

import com.lysandri.api.dto.response.EstudianteInscritoResponse;
import com.lysandri.api.dto.response.ProgramaResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.service.InscripcionService;
import com.lysandri.api.service.ProgramaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profesor")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTRUCTOR')")
@Tag(name = "Área del profesor")
public class ProfesorController {

    private final ProgramaService programaService;
    private final InscripcionService inscripcionService;

    @GetMapping("/mis-cursos")
    @Operation(summary = "Listar programas del profesor autenticado")
    public ResponseEntity<List<ProgramaResponse>> misCursos(
            @AuthenticationPrincipal Usuario usuario
    ) {
        validarSesion(usuario);

        return ResponseEntity.ok(
                programaService.getProgramasByInstructorUserId(
                        usuario.getIdUser()
                )
        );
    }

    @GetMapping("/programas/{programaId}/estudiantes")
    @Operation(summary = "Listar estudiantes inscritos en un programa del profesor")
    public ResponseEntity<List<EstudianteInscritoResponse>> estudiantes(
            @PathVariable Long programaId,
            @AuthenticationPrincipal Usuario usuario
    ) {
        validarSesion(usuario);

        return ResponseEntity.ok(
                inscripcionService.listarEstudiantesDelProfesor(
                        usuario.getIdUser(),
                        programaId
                )
        );
    }

    private void validarSesion(Usuario usuario) {
        if (usuario == null) {
            throw new BadRequestException(
                    "Sesión no válida o usuario no autenticado"
            );
        }
    }
}
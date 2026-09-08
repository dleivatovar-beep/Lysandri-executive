package com.lysandri.api.controller;

import com.lysandri.api.dto.request.InscripcionRequest;
import com.lysandri.api.dto.response.InscripcionResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.model.enums.Rol;
import com.lysandri.api.service.InscripcionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Inscripciones", description = "Gestión de matrículas y programas académicos")
public class InscripcionController {

    private final InscripcionService inscripcionService;

    @PostMapping("/api/v1/inscripciones")
    @PreAuthorize("hasAnyRole('ESTUDIANTE', 'ADMIN', 'ADMINISTRADOR')")
    @Operation(summary = "Inscribir usuario a un programa")
    public ResponseEntity<InscripcionResponse> inscribir(
            @Valid @RequestBody InscripcionRequest request,
            @AuthenticationPrincipal Usuario usuario
    ) {
        if (usuario == null) {
            throw new BadRequestException("Sesión no válida o usuario no autenticado");
        }

        boolean esAdmin = usuario.getRol() == Rol.ADMIN;
        Long targetUserId = (esAdmin && request.getUsuarioId() != null)
                ? request.getUsuarioId()
                : Long.valueOf(usuario.getIdUser());

        return new ResponseEntity<>(
                inscripcionService.inscribirEstudiante(targetUserId, request.getProgramaId()),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/api/v1/estudiante/mis-cursos")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    @Operation(summary = "Listar cursos del estudiante autenticado")
    public ResponseEntity<List<InscripcionResponse>> misCursos(
            @AuthenticationPrincipal Usuario usuario
    ) {
        if (usuario == null) {
            throw new BadRequestException("Sesión no válida o usuario no autenticado");
        }

        return ResponseEntity.ok(
                inscripcionService.listarCursosDeEstudiante(Long.valueOf(usuario.getIdUser()))
        );
    }
}

package com.lysandri.api.controller;

import com.lysandri.api.dto.response.ProgresoPorcentajeResponse;
import com.lysandri.api.dto.response.ProgresoResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.service.ProgresoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Progreso", description = "Seguimiento del avance académico del estudiante")
public class ProgresoController {

    private final ProgresoService progresoService;

    @PostMapping("/api/v1/lecciones/{leccionId}/completar")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    @Operation(summary = "Marcar lección como completada")
    public ResponseEntity<ProgresoResponse> marcarCompletada(
            @PathVariable Long leccionId,
            @AuthenticationPrincipal Usuario usuario
    ) {
        if (usuario == null) {
            throw new BadRequestException("Sesión no válida o usuario no autenticado");
        }
        return ResponseEntity.ok(
                progresoService.marcarLeccionCompletada(Long.valueOf(usuario.getIdUser()), leccionId)
        );
    }

    @GetMapping("/api/v1/programas/{programaId}/progreso")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    @Operation(summary = "Obtener porcentaje de avance del programa")
    public ResponseEntity<ProgresoPorcentajeResponse> obtenerProgreso(
            @PathVariable Long programaId,
            @AuthenticationPrincipal Usuario usuario
    ) {
        if (usuario == null) {
            throw new BadRequestException("Sesión no válida o usuario no autenticado");
        }
        return ResponseEntity.ok(
                progresoService.obtenerPorcentajeAvance(Long.valueOf(usuario.getIdUser()), programaId)
        );
    }
}

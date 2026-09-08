package com.lysandri.api.controller;

import com.lysandri.api.dto.request.LeccionRequest;
import com.lysandri.api.dto.response.LeccionResponse;
import com.lysandri.api.service.LeccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Lecciones", description = "Gestión de lecciones de programas académicos")
public class LeccionController {

    private final LeccionService leccionService;

    @PostMapping("/api/v1/programas/{programaId}/lecciones")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'ADMINISTRADOR')")
    @Operation(summary = "Crear lección en programa")
    public ResponseEntity<LeccionResponse> crearLeccion(
            @PathVariable Long programaId,
            @Valid @RequestBody LeccionRequest request
    ) {
        return new ResponseEntity<>(leccionService.crearLeccion(programaId, request), HttpStatus.CREATED);
    }

    @GetMapping("/api/v1/programas/{programaId}/lecciones")
    @Operation(summary = "Listar lecciones de un programa")
    public ResponseEntity<List<LeccionResponse>> listarPorPrograma(@PathVariable Long programaId) {
        return ResponseEntity.ok(leccionService.listarPorPrograma(programaId));
    }

    @PutMapping("/api/v1/lecciones/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'ADMINISTRADOR')")
    @Operation(summary = "Actualizar lección")
    public ResponseEntity<LeccionResponse> actualizarLeccion(
            @PathVariable Long id,
            @Valid @RequestBody LeccionRequest request
    ) {
        return ResponseEntity.ok(leccionService.actualizarLeccion(id, request));
    }

    @DeleteMapping("/api/v1/lecciones/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'ADMINISTRADOR')")
    @Operation(summary = "Eliminar lección")
    public ResponseEntity<Void> eliminarLeccion(@PathVariable Long id) {
        leccionService.eliminarLeccion(id);
        return ResponseEntity.noContent().build();
    }
}

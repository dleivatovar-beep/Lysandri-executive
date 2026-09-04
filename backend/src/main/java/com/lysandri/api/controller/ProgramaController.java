package com.lysandri.api.controller;

import com.lysandri.api.dto.request.ProgramaRequest;
import com.lysandri.api.dto.response.ProgramaResponse;
import com.lysandri.api.service.ProgramaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/programas")
@RequiredArgsConstructor
@Tag(name = "Programas Académicos", description = "Endpoints para la gestión de cursos y programas de estudio")
public class ProgramaController {

    private final ProgramaService programaService;

    @GetMapping
    @Operation(summary = "Listar todos los programas académicos")
    public ResponseEntity<List<ProgramaResponse>> getAllProgramas() {
        return ResponseEntity.ok(programaService.getAllProgramas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de un programa académico por ID")
    public ResponseEntity<ProgramaResponse> getProgramaById(@PathVariable Integer id) {
        return ResponseEntity.ok(programaService.getProgramaById(id));
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo programa académico")
    public ResponseEntity<ProgramaResponse> createPrograma(@Valid @RequestBody ProgramaRequest request) {
        return new ResponseEntity<>(programaService.createPrograma(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un programa académico existente")
    public ResponseEntity<ProgramaResponse> updatePrograma(@PathVariable Integer id, @Valid @RequestBody ProgramaRequest request) {
        return ResponseEntity.ok(programaService.updatePrograma(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un programa académico")
    public ResponseEntity<Void> deletePrograma(@PathVariable Integer id) {
        programaService.deletePrograma(id);
        return ResponseEntity.noContent().build();
    }
}

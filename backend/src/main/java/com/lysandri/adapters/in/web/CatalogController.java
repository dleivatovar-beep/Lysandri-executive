package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ActualizarProgramaRequest;
import com.lysandri.adapters.in.web.dto.CrearProgramaRequest;
import com.lysandri.domain.model.MoodleCourseVerification;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.in.CatalogUseCase;
import com.lysandri.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/programas")
@RequiredArgsConstructor
@Tag(name = "Catálogo de Programas", description = "Programas ejecutivos, mallas y precios")
public class CatalogController {

    private final CatalogUseCase catalogUseCase;

    @GetMapping
    @Operation(summary = "Lista todos los programas ejecutivos activos y con precio aprobado en la tienda pública")
    public ResponseEntity<List<Programa>> listarProgramas() {
        return ResponseEntity.ok(catalogUseCase.listarProgramasActivos());
    }

    @GetMapping("/admin/todos")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO', 'TRABAJADOR')")
    @Operation(summary = "Lista todos los programas (incluidos borradores e inactivos) para gestión administrativa")
    public ResponseEntity<List<Programa>> listarTodosAdmin() {
        return ResponseEntity.ok(catalogUseCase.listarTodosAdmin());
    }

    @GetMapping("/admin/inventario-moodle")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO', 'TRABAJADOR')")
    @Operation(summary = "Inventario de solo lectura de programas y estado de vinculación con cursos de Moodle LMS")
    public ResponseEntity<List<Programa>> listarInventarioMoodle() {
        return ResponseEntity.ok(catalogUseCase.listarInventarioMoodle());
    }

    @PostMapping("/admin/verificar-curso-moodle")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Consulta directamente en Moodle LMS la existencia y datos de un curso por su ID")
    public ResponseEntity<MoodleCourseVerification> verificarCursoMoodle(@RequestParam Long moodleCourseId) {
        return ResponseEntity.ok(catalogUseCase.verificarCursoMoodle(moodleCourseId));
    }

    @PostMapping("/admin/{id}/verificar-moodle")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Verifica y actualiza el estado de vinculación a Moodle de un programa específico")
    public ResponseEntity<Programa> verificarYActualizarMoodlePrograma(@PathVariable Long id) {
        return ResponseEntity.ok(catalogUseCase.verificarYActualizarMoodlePrograma(id));
    }

    @PostMapping("/admin/{id}/desvincular-moodle")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Desvincula voluntariamente un programa académico de cualquier curso de Moodle LMS y lo desactiva")
    public ResponseEntity<Programa> desvincularCursoMoodle(@PathVariable Long id) {
        return ResponseEntity.ok(catalogUseCase.desvincularCursoMoodle(id));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Obtiene los detalles de un programa por su slug o ID numérico")
    public ResponseEntity<Programa> obtenerPorSlug(@PathVariable String slug) {
        if (slug != null && slug.matches("^\\d+$")) {
            try {
                Long id = Long.parseLong(slug);
                return ResponseEntity.ok(catalogUseCase.obtenerProgramaPorId(id));
            } catch (ResourceNotFoundException e) {
                // Si no se encuentra por ID, intentar por slug por si acaso el slug fuera exactamente numérico
            }
        }
        return ResponseEntity.ok(catalogUseCase.obtenerProgramaPorSlug(slug));
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "Obtiene los detalles de un programa público por su ID")
    public ResponseEntity<Programa> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(catalogUseCase.obtenerProgramaPorId(id));
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO', 'TRABAJADOR')")
    @Operation(summary = "Obtiene los detalles de cualquier programa para administración interna")
    public ResponseEntity<Programa> obtenerAdminPorId(@PathVariable Long id) {
        return ResponseEntity.ok(catalogUseCase.obtenerProgramaAdminPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Crea un nuevo programa académico desde el panel administrativo")
    public ResponseEntity<Programa> crearPrograma(@Valid @RequestBody CrearProgramaRequest request) {
        Programa creado = catalogUseCase.crearPrograma(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Actualiza los datos de un programa académico")
    public ResponseEntity<Programa> actualizarPrograma(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarProgramaRequest request) {
        Programa actualizado = catalogUseCase.actualizarPrograma(id, request.toCommand());
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINADOR_ACADEMICO')")
    @Operation(summary = "Desactiva un programa académico sin eliminar registros históricos")
    public ResponseEntity<Void> desactivarPrograma(@PathVariable Long id) {
        catalogUseCase.desactivarPrograma(id);
        return ResponseEntity.noContent().build();
    }
}

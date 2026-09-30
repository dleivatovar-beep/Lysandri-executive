package com.lysandri.adapters.in.web;

import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.in.CatalogUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/programas")
@RequiredArgsConstructor
@Tag(name = "Catálogo de Programas", description = "Programas ejecutivos, mallas y precios")
public class CatalogController {

    private final CatalogUseCase catalogUseCase;

    @GetMapping
    @Operation(summary = "Lista todos los programas ejecutivos activos en la tienda")
    public ResponseEntity<List<Programa>> listarProgramas() {
        return ResponseEntity.ok(catalogUseCase.listarProgramasActivos());
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Obtiene los detalles de un programa por su slug")
    public ResponseEntity<Programa> obtenerPorSlug(@PathVariable String slug) {
        return ResponseEntity.ok(catalogUseCase.obtenerProgramaPorSlug(slug));
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "Obtiene los detalles de un programa por su ID")
    public ResponseEntity<Programa> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(catalogUseCase.obtenerProgramaPorId(id));
    }
}

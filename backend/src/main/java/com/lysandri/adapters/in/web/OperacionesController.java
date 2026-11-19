package com.lysandri.adapters.in.web;

import com.lysandri.application.service.IncidenciasService;
import com.lysandri.application.service.ReporteOperativoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/operaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS','AUDITOR')")
public class OperacionesController {
    private final ReporteOperativoService reportes;
    private final IncidenciasService incidencias;

    public record Cambio(@NotNull @Pattern(regexp = "ABIERTA|SEGUIMIENTO|RESUELTA") String estado,
                         @NotBlank @Size(max = 1000) String comentario) {}

    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> solicitudInvalida() {
        return org.springframework.http.ResponseEntity.badRequest()
                .body(java.util.Map.of("message", "Revisa las fechas y el identificador de incidencia."));
    }

    @GetMapping("/reporte")
    public Object reporte(@RequestParam LocalDate desde, @RequestParam LocalDate hasta) {
        return reportes.consultar(desde, hasta);
    }

    @GetMapping("/incidencias")
    public Object incidencias() { return incidencias.listar(); }

    @GetMapping("/incidencias/{id}/historial")
    public Object historial(@PathVariable UUID id) { return incidencias.historial(id); }

    @PostMapping("/incidencias/revisar")
    @PreAuthorize("hasRole('ADMIN')")
    public void revisar() { incidencias.revisar(); }

    @PostMapping("/incidencias/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void cambiar(@PathVariable UUID id, @Valid @RequestBody Cambio datos, Authentication usuario) {
        incidencias.cambiar(id, datos.estado(), datos.comentario(), usuario.getName());
    }
}

package com.lysandri.adapters.in.web;

import com.lysandri.application.service.DocumentoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/conocimiento")
@RequiredArgsConstructor
public class DocumentoController {
    private final DocumentoService documentos;

    public record Solicitud(@NotBlank @Size(max = 200) String titulo,
                            @NotBlank @Size(max = 40) String version,
                            @NotNull @Pattern(regexp = "PROPUESTA|VIGENTE") String estado,
                            @Positive Long idPrograma,
                            @NotBlank @Size(max = 100000) String contenido) {}

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentoService.Resultado cargar(@Valid @RequestBody Solicitud solicitud) {
        return documentos.cargar(new DocumentoService.Carga(solicitud.titulo(), solicitud.version(),
                solicitud.estado(), solicitud.idPrograma(), solicitud.contenido()));
    }
}

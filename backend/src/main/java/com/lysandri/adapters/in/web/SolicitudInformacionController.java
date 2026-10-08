package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.SolicitudRequest;
import com.lysandri.domain.model.SolicitudInformacion;
import com.lysandri.domain.ports.in.SolicitudInformacionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/solicitudes-informacion", "/api/v1/admin/solicitudes-informacion"})
@RequiredArgsConstructor
@Tag(name = "Solicitudes de Información", description = "Contacto corporativo B2B y solicitudes de información In-Company")
public class SolicitudInformacionController {

    private final SolicitudInformacionUseCase solicitudUseCase;

    @PostMapping
    @Operation(summary = "Registra una solicitud de información corporativa")
    public ResponseEntity<SolicitudInformacion> crearSolicitud(@Valid @RequestBody SolicitudRequest request) {
        SolicitudInformacionUseCase.CreateSolicitudCommand command = new SolicitudInformacionUseCase.CreateSolicitudCommand(
                request.getIdPrograma(),
                request.getNombreCompleto(),
                request.getEmail(),
                request.getTelefono(),
                request.getEmpresa(),
                request.getCargo(),
                request.getMensaje()
        );
        return new ResponseEntity<>(solicitudUseCase.registrarSolicitud(command), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Lista todas las solicitudes corporativas")
    public ResponseEntity<List<SolicitudInformacion>> listarSolicitudes(
            @RequestParam(required = false) String estado
    ) {
        List<SolicitudInformacion> lista = solicitudUseCase.listarSolicitudes();
        if (estado != null && !estado.isBlank()) {
            lista = lista.stream()
                    .filter(s -> estado.equalsIgnoreCase(s.getEstado()))
                    .toList();
        }
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una solicitud de información por ID")
    public ResponseEntity<SolicitudInformacion> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(solicitudUseCase.obtenerPorId(id));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Actualiza el estado de una solicitud (PENDIENTE, CONTACTADA, CERRADA)")
    public ResponseEntity<SolicitudInformacion> actualizarEstado(
            @PathVariable Long id,
            @RequestParam String estado
    ) {
        return ResponseEntity.ok(solicitudUseCase.actualizarEstado(id, estado));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina una solicitud de información")
    public ResponseEntity<Void> eliminarSolicitud(@PathVariable Long id) {
        solicitudUseCase.eliminarSolicitud(id);
        return ResponseEntity.noContent().build();
    }
}

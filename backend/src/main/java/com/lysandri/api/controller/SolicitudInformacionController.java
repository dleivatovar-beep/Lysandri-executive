package com.lysandri.api.controller;

import com.lysandri.api.dto.request.SolicitudInformacionRequest;
import com.lysandri.api.dto.response.SolicitudInformacionResponse;
import com.lysandri.api.service.SolicitudInformacionService;
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
@Tag(name = "Solicitudes de Información", description = "Gestión de prospectos comerciales y solicitudes de información de programas")
public class SolicitudInformacionController {

    private final SolicitudInformacionService solicitudService;

    @PostMapping("/api/v1/solicitudes-informacion")
    @Operation(summary = "Registrar nueva solicitud de información (acceso público)")
    public ResponseEntity<SolicitudInformacionResponse> registrar(
            @Valid @RequestBody SolicitudInformacionRequest request
    ) {
        return new ResponseEntity<>(
                solicitudService.registrarSolicitud(request),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/api/v1/admin/solicitudes-informacion")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")
    @Operation(summary = "Listar solicitudes de información (solo administradores)")
    public ResponseEntity<List<SolicitudInformacionResponse>> listar(
            @RequestParam(required = false) String estado
    ) {
        return ResponseEntity.ok(solicitudService.listarPorEstado(estado));
    }

    @GetMapping("/api/v1/admin/solicitudes-informacion/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")
    @Operation(summary = "Obtener detalle de una solicitud por ID")
    public ResponseEntity<SolicitudInformacionResponse> obtenerPorId(
            @PathVariable Integer id
    ) {
        return ResponseEntity.ok(solicitudService.obtenerPorId(id));
    }

    @PatchMapping("/api/v1/admin/solicitudes-informacion/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")
    @Operation(summary = "Actualizar estado y notas de una solicitud")
    public ResponseEntity<SolicitudInformacionResponse> actualizarEstado(
            @PathVariable Integer id,
            @RequestParam String estado,
            @RequestParam(required = false) String notasAdmin
    ) {
        return ResponseEntity.ok(solicitudService.actualizarEstado(id, estado, notasAdmin));
    }

    @DeleteMapping("/api/v1/admin/solicitudes-informacion/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRADOR')")
    @Operation(summary = "Eliminar una solicitud de información")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        solicitudService.eliminarSolicitud(id);
        return ResponseEntity.noContent().build();
    }
}

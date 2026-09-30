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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/solicitudes-informacion")
@RequiredArgsConstructor
@Tag(name = "Solicitudes de Información", description = "Contacto corporativo B2B y dudas para In-Company")
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista todas las solicitudes corporativas (Solo Administradores)")
    public ResponseEntity<List<SolicitudInformacion>> listarSolicitudes() {
        return ResponseEntity.ok(solicitudUseCase.listarSolicitudes());
    }
}

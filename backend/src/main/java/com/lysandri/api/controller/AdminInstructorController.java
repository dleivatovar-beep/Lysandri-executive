package com.lysandri.api.controller;

import com.lysandri.api.dto.request.InstructorRequest;
import com.lysandri.api.dto.response.InstructorResponse;
import com.lysandri.api.service.InstructorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(
    "/api/v1/admin/instructores"
)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(
    name = "Administración de instructores"
)
public class AdminInstructorController {

    private final InstructorService
            instructorService;

    @GetMapping
    @Operation(
        summary = "Listar instructores"
    )
    public ResponseEntity<
        List<InstructorResponse>
    > listar() {
        return ResponseEntity.ok(
            instructorService
                .listarInstructores()
        );
    }

    @GetMapping("/{dni}")
    @Operation(
        summary = "Obtener instructor por DNI"
    )
    public ResponseEntity<
        InstructorResponse
    > obtener(
            @PathVariable String dni
    ) {
        return ResponseEntity.ok(
            instructorService
                .obtenerInstructor(dni)
        );
    }

    @PostMapping
    @Operation(
        summary = "Crear perfil de instructor"
    )
    public ResponseEntity<
        InstructorResponse
    > crear(
            @Valid
            @RequestBody
            InstructorRequest request
    ) {
        return new ResponseEntity<>(
            instructorService
                .crearInstructor(request),
            HttpStatus.CREATED
        );
    }

    @PutMapping("/{dni}")
    @Operation(
        summary = "Actualizar perfil de instructor"
    )
    public ResponseEntity<
        InstructorResponse
    > actualizar(
            @PathVariable String dni,

            @Valid
            @RequestBody
            InstructorRequest request
    ) {
        return ResponseEntity.ok(
            instructorService
                .actualizarInstructor(
                    dni,
                    request
                )
        );
    }

    @DeleteMapping("/{dni}")
    @Operation(
        summary =
            "Eliminar instructor sin programas asignados"
    )
    public ResponseEntity<Void>
    eliminar(
            @PathVariable String dni
    ) {
        instructorService
            .eliminarInstructor(dni);

        return ResponseEntity
            .noContent()
            .build();
    }
}
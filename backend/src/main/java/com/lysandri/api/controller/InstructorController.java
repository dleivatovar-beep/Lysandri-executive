
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/instructores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(
        name = "Instructores",
        description = "Administración de perfiles de instructores"
)
public class InstructorController {

    private final InstructorService instructorService;

    @PostMapping
    @Operation(summary = "Crear un perfil de instructor")
    public ResponseEntity<InstructorResponse> crearInstructor(
            @Valid @RequestBody InstructorRequest request
    ) {
        InstructorResponse response =
                instructorService.crearInstructor(request);

        return new ResponseEntity<>(
                response,
                HttpStatus.CREATED
        );
    }

    @GetMapping
    @Operation(summary = "Listar todos los instructores")
    public ResponseEntity<List<InstructorResponse>>
    listarInstructores() {
        List<InstructorResponse> response =
                instructorService.listarInstructores();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{instructorDni}")
    @Operation(summary = "Obtener un instructor por su DNI")
    public ResponseEntity<InstructorResponse>
    obtenerInstructor(
            @PathVariable String instructorDni
    ) {
        InstructorResponse response =
                instructorService.obtenerInstructor(
                        instructorDni
                );

        return ResponseEntity.ok(response);
    }
}
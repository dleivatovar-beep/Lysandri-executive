package com.lysandri.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstructorRequest {

    @NotBlank(
        message = "El DNI del instructor es obligatorio"
    )
    private String idInstructorDni;

    @NotNull(
        message = "El ID del usuario es obligatorio"
    )
    private Integer usuarioId;

    private String especialidad;

    private String direccionInstructor;
}
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstructorRequest {

    @NotBlank(message = "El DNI del instructor es obligatorio")
    private String idInstructorDni;

    @NotNull(message = "El identificador del usuario es obligatorio")
    private Integer usuarioId;

    private String especialidad;

    private String direccionInstructor;
}
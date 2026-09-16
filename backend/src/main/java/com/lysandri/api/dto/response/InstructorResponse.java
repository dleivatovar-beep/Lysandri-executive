package com.lysandri.api.dto.response;

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
public class InstructorResponse {

    private String idInstructorDni;

    private Integer usuarioId;

    private String nombreCompleto;

    private String email;

    private String especialidad;

    private String direccionInstructor;
}
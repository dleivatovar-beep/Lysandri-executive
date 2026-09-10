package com.lysandri.api.dto.response;

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
public class InstructorResponse {

    private String idInstructorDni;

    private Integer usuarioId;

    private String nombres;

    private String apellidos;

    private String email;

    private String especialidad;

    private String direccionInstructor;
}
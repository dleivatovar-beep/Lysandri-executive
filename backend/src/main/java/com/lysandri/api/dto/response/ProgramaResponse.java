package com.lysandri.api.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramaResponse {

    private Integer idPrograma;
    private String idInstructorDni;
    private String nombreInstructor;
    private String tituloPrograma;
    private String duracionPrograma;
    private String tipoPrograma;
    private String level;
    private LocalDate fechaInicioGlobal;
    private LocalDate fechaFinalGlobal;
    private String requisitos;
    private String metodologia;
}

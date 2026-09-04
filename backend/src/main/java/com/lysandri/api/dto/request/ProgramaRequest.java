package com.lysandri.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramaRequest {

    @NotBlank(message = "El DNI del instructor es obligatorio")
    private String idInstructorDni;

    @NotBlank(message = "El título del programa es obligatorio")
    private String tituloPrograma;

    private String duracionPrograma;
    private String tipoPrograma;
    private String level;
    private LocalDate fechaInicioGlobal;
    private LocalDate fechaFinalGlobal;
    private String requisitos;
    private String metodologia;
}

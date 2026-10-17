package com.lysandri.adapters.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatricularNominaRequest {

    @NotNull(message = "El ID del programa a matricular es obligatorio")
    private Long idPrograma;

    private Long idSolicitud;

    @NotBlank(message = "El nombre de la empresa u organización es obligatorio")
    private String empresaNombre;

    @NotEmpty(message = "Debe enviar al menos un colaborador en la nómina")
    @Valid
    private List<ColaboradorRequest> colaboradores;

    @Builder.Default
    private Boolean enviarCredencialesEmail = true;

    private String notasAdmin;
}

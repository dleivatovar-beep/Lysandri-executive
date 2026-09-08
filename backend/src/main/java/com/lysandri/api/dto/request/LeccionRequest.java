package com.lysandri.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeccionRequest {

    @NotBlank(message = "El título de la lección es obligatorio")
    private String titulo;

    private String descripcion;

    private String contenidoUrl;

    private Integer duracionMinutos;

    @NotNull(message = "El orden de la lección es obligatorio")
    private Integer orden;
}

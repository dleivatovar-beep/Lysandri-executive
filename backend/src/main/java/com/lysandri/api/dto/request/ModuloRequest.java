package com.lysandri.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuloRequest {

    @NotBlank(message = "El título del módulo es obligatorio")
    private String titulo;

    private String descripcion;

    @NotNull(message = "El orden del módulo es obligatorio")
    @Positive(message = "El orden del módulo debe ser mayor que cero")
    private Integer orden;

    private Boolean activo;
}
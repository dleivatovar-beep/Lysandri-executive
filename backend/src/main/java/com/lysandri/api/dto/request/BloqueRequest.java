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
public class BloqueRequest {

    @NotBlank(message = "El título del bloque es obligatorio")
    private String titulo;

    private String descripcion;

    @NotNull(message = "El orden del bloque es obligatorio")
    @Positive(message = "El orden del bloque debe ser mayor que cero")
    private Integer orden;

    private Boolean activo;
}
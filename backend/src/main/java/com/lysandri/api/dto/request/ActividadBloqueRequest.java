package com.lysandri.api.dto.request;

import com.lysandri.api.model.enums.TipoActividad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActividadBloqueRequest {

    @NotBlank(message = "El título de la actividad es obligatorio")
    private String titulo;

    private String descripcion;

    private String instrucciones;

    @NotNull(message = "El tipo de actividad es obligatorio")
    private TipoActividad tipoActividad;

    private String contenidoUrl;

    @NotNull(message = "El orden de la actividad es obligatorio")
    @Positive(message = "El orden de la actividad debe ser mayor que cero")
    private Integer orden;

    private Boolean activo;
}
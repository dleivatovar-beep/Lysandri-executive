package com.lysandri.api.dto.request;

import com.lysandri.api.model.enums.TipoContenido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    /*
     * Se mantiene opcional para no romper peticiones antiguas.
     * Si no se envía, el servicio utilizará VIDEO.
     */
    private TipoContenido tipoContenido;

    private String contenidoUrl;

    @Positive(message = "La duración debe ser mayor que cero")
    private Integer duracionMinutos;

    @NotNull(message = "El orden de la lección es obligatorio")
    @Positive(message = "El orden de la lección debe ser mayor que cero")
    private Integer orden;
}
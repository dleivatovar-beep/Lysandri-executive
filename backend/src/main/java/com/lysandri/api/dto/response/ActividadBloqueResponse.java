package com.lysandri.api.dto.response;

import com.lysandri.api.model.enums.TipoActividad;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActividadBloqueResponse {

    private Integer idActividad;
    private Integer bloqueId;
    private String titulo;
    private String descripcion;
    private String instrucciones;
    private TipoActividad tipoActividad;
    private String contenidoUrl;
    private Integer orden;
    private Boolean activo;
}
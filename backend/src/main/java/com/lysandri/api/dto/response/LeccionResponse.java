package com.lysandri.api.dto.response;

import com.lysandri.api.model.enums.TipoContenido;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeccionResponse {

    /*
     * Se mantienen Long para conservar compatibilidad
     * con los controladores y servicios actuales.
     */
    private Long id;
    private Long programaId;
    private Long moduloId;
    private String titulo;
    private String descripcion;
    private TipoContenido tipoContenido;
    private String contenidoUrl;
    private Integer duracionMinutos;
    private Integer orden;
}
package com.lysandri.api.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeccionResponse {

    private Long id;
    private Long programaId;
    private String titulo;
    private String descripcion;
    private String contenidoUrl;
    private Integer duracionMinutos;
    private Integer orden;
}

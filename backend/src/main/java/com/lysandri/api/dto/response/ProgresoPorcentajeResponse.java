package com.lysandri.api.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgresoPorcentajeResponse {

    private Long programaId;
    private Long totalLecciones;
    private Long leccionesCompletadas;
    private Double porcentaje;
}

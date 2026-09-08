package com.lysandri.api.dto.response;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgresoResponse {

    private Long id;
    private Long usuarioId;
    private Long leccionId;
    private Boolean completado;
    private OffsetDateTime fechaCompletado;
}

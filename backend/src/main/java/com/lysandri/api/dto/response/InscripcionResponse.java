package com.lysandri.api.dto.response;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscripcionResponse {

    private Long id;
    private Long usuarioId;
    private String nombreEstudiante;
    private Long programaId;
    private String tituloPrograma;
    private OffsetDateTime fechaInscripcion;
    private String estado;
}

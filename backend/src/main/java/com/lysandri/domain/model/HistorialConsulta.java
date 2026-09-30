package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialConsulta {
    private Long idHistorial;
    private Long idUser;
    private Long idPrograma;
    private String sesionId;
    private String pregunta;
    private String respuestaIa;
    private List<String> chunksReferenciados;
    private Integer tokensPrompt;
    private Integer tokensCompletion;
    private Integer tiempoRespuestaMs;
    private Integer calificacionUsuario;
    private OffsetDateTime fechaConsulta;
}

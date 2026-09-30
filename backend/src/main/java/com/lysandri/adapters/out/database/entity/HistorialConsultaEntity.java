package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "HISTORIAL_CONSULTA")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialConsultaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @Column(name = "id_user")
    private Long idUser;

    @Column(name = "id_programa")
    private Long idPrograma;

    @Column(name = "sesion_id", nullable = false, length = 64)
    private String sesionId;

    @Column(name = "pregunta", nullable = false, columnDefinition = "TEXT")
    private String pregunta;

    @Column(name = "respuesta_ia", nullable = false, columnDefinition = "TEXT")
    private String respuestaIa;

    @Column(name = "tokens_prompt")
    private Integer tokensPrompt;

    @Column(name = "tokens_completion")
    private Integer tokensCompletion;

    @Column(name = "tiempo_respuesta_ms")
    private Integer tiempoRespuestaMs;

    @Column(name = "calificacion_usuario")
    private Integer calificacionUsuario;

    @Column(name = "fecha_consulta", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaConsulta = OffsetDateTime.now();
}

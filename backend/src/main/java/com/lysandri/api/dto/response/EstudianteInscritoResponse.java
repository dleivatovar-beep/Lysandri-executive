package com.lysandri.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstudianteInscritoResponse {

    private Long idInscripcion;

    private Long usuarioId;

    private String nombres;

    private String apellidos;

    private String email;

    private String telefono;

    private Long programaId;

    private String tituloPrograma;

    private OffsetDateTime fechaInscripcion;

    private String estado;

    private BigDecimal porcentajeProgreso;
}
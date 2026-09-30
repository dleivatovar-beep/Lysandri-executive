package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Programa {
    private Long idPrograma;
    private Long moodleCourseId;
    private String titulo;
    private String slug;
    private String subtitulo;
    private String descripcionCorta;
    private String descripcionDetallada;
    private BigDecimal precio;
    private String moneda;
    private String imagenPortadaUrl;
    private String syllabusUrl;
    private String instructorNombre;
    private String instructorBio;
    private String nivel;
    private Integer duracionHoras;
    private boolean activo;
    private OffsetDateTime fechaCreacion;
}

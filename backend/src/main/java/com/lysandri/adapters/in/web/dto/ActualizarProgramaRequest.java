package com.lysandri.adapters.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lysandri.domain.ports.in.CatalogUseCase.ActualizarProgramaCommand;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActualizarProgramaRequest {

    @JsonAlias({"tituloPrograma", "title"})
    private String titulo;

    private String slug;

    private String subtitulo;

    @JsonAlias({"descripcion", "descripcionCorta", "descripcionDetallada", "description"})
    private String descripcion;

    private BigDecimal precio;

    private String moneda;

    @JsonAlias({"coverUrl", "imagenPortadaUrl"})
    private String imagenPortadaUrl;

    private String syllabusUrl;

    @JsonAlias({"nombreInstructor", "instructorNombre", "instructor"})
    private String instructorNombre;

    private String instructorBio;

    @JsonAlias({"level", "nivel"})
    private String nivel;

    private Integer duracionHoras;

    @JsonAlias({"duracionPrograma", "duracion", "duration"})
    private String duracionPrograma;

    private Boolean activo;

    private Boolean precioAprobado;

    private Boolean esGratuito;

    private Long moodleCourseId;

    private Boolean desvincularMoodle;

    public ActualizarProgramaCommand toCommand() {
        return new ActualizarProgramaCommand(
                titulo,
                slug,
                subtitulo,
                descripcion,
                descripcion,
                precio,
                moneda,
                imagenPortadaUrl,
                syllabusUrl,
                instructorNombre,
                instructorBio,
                nivel,
                duracionHoras,
                duracionPrograma,
                activo,
                precioAprobado,
                esGratuito,
                moodleCourseId,
                desvincularMoodle
        );
    }
}


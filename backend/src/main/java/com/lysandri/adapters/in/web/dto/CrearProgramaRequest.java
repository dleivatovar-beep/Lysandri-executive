package com.lysandri.adapters.in.web.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lysandri.domain.ports.in.CatalogUseCase.CrearProgramaCommand;
import jakarta.validation.constraints.NotBlank;
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
public class CrearProgramaRequest {

    // No se permite suministrar ID en creación para evitar sobrescribir registros existentes
    private Long idPrograma;

    @NotBlank(message = "El título del programa es obligatorio")
    @JsonAlias({"tituloPrograma", "title"})
    private String titulo;

    private String slug;

    private String subtitulo;

    @JsonAlias({"descripcion", "descripcionCorta", "descripcionDetallada", "description"})
    private String descripcion;

    private BigDecimal precio;

    @Builder.Default
    private String moneda = "USD";

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

    public CrearProgramaCommand toCommand() {
        return new CrearProgramaCommand(
                titulo,
                slug,
                subtitulo,
                descripcion,
                descripcion,
                precio != null ? precio : BigDecimal.ZERO,
                moneda != null && !moneda.isBlank() ? moneda.trim().toUpperCase() : "USD",
                imagenPortadaUrl,
                syllabusUrl,
                instructorNombre,
                instructorBio,
                nivel,
                duracionHoras,
                duracionPrograma,
                activo != null ? activo : Boolean.TRUE,
                precioAprobado != null ? precioAprobado : Boolean.FALSE,
                esGratuito,
                moodleCourseId
        );
    }
}


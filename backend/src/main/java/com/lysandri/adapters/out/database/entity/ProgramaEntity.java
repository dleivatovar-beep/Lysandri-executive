package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "PROGRAMA")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_programa")
    private Long idPrograma;

    @Column(name = "moodle_course_id", nullable = false, unique = true)
    private Long moodleCourseId;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "slug", nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "subtitulo", length = 300)
    private String subtitulo;

    @Column(name = "descripcion_corta", columnDefinition = "TEXT")
    private String descripcionCorta;

    @Column(name = "descripcion_detallada", columnDefinition = "TEXT")
    private String descripcionDetallada;

    @Column(name = "precio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "moneda", nullable = false, length = 3)
    @Builder.Default
    private String moneda = "USD";

    @Column(name = "imagen_portada_url", length = 500)
    private String imagenPortadaUrl;

    @Column(name = "syllabus_url", length = 500)
    private String syllabusUrl;

    @Column(name = "instructor_nombre", length = 150)
    private String instructorNombre;

    @Column(name = "instructor_bio", columnDefinition = "TEXT")
    private String instructorBio;

    @Column(name = "nivel", length = 50)
    private String nivel;

    @Column(name = "duracion_horas")
    private Integer duracionHoras;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();
}

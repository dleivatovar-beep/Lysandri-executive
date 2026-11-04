package com.lysandri.adapters.out.database.entity;

import com.lysandri.domain.model.MoodleVerificationStatus;
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

    @Column(name = "moodle_course_id", unique = true)
    private Long moodleCourseId;

    @Column(name = "moodle_course_nombre")
    private String moodleCourseNombre;

    @Column(name = "moodle_verificado", nullable = false)
    @Builder.Default
    private boolean moodleVerificado = false;

    @Column(name = "moodle_verificado_en")
    private OffsetDateTime moodleVerificadoEn;

    @Column(name = "moodle_ultimo_intento_en")
    private OffsetDateTime moodleUltimoIntentoEn;

    @Column(name = "moodle_vinculacion_revision", nullable = false)
    @Builder.Default
    private Long moodleVinculacionRevision = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "moodle_estado_verificacion", length = 50, nullable = false)
    @Builder.Default
    private MoodleVerificationStatus moodleEstadoVerificacion = MoodleVerificationStatus.PENDIENTE_VERIFICACION;

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

    @Column(name = "imagen_portada_url", columnDefinition = "TEXT")
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

    @Column(name = "precio_aprobado", nullable = false)
    @Builder.Default
    private boolean precioAprobado = false;

    @Column(name = "es_gratuito", nullable = false)
    @Builder.Default
    private boolean esGratuito = false;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();
}

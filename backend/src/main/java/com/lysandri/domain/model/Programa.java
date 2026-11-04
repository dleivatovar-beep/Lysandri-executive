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
    private String moodleCourseNombre;
    private boolean moodleVerificado;
    private OffsetDateTime moodleVerificadoEn;
    private OffsetDateTime moodleUltimoIntentoEn;
    @Builder.Default
    private Long moodleVinculacionRevision = 1L;
    private MoodleVerificationStatus moodleEstadoVerificacion;
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
    private boolean precioAprobado;
    private boolean esGratuito;
    private OffsetDateTime fechaCreacion;

    public boolean isVerificacionVigente(java.time.Clock clock, long ttlHours) {
        if (!moodleVerificado || moodleVerificadoEn == null) {
            return false;
        }
        OffsetDateTime cutoff = OffsetDateTime.now(clock != null ? clock : java.time.Clock.systemUTC()).minusHours(ttlHours);
        return !moodleVerificadoEn.isBefore(cutoff);
    }

    public boolean isMoodleVerificacionExpirada(java.time.Clock clock, long ttlHours) {
        return !isVerificacionVigente(clock, ttlHours);
    }

    public boolean isMoodleVerificacionExpirada() {
        return isMoodleVerificacionExpirada(java.time.Clock.systemUTC(), 168L);
    }

    public boolean isComercialmenteDisponible(java.time.Clock clock, long ttlHours) {
        return activo && precioAprobado && isVerificacionVigente(clock, ttlHours);
    }
}

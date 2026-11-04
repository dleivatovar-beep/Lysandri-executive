package com.lysandri.domain.model;

import java.time.OffsetDateTime;

public record MoodleCourseVerification(
        MoodleVerificationStatus status,
        Long moodleCourseId,
        String courseFullName,
        String courseShortName,
        String detalle,
        OffsetDateTime verificadoEn
) {
    public static MoodleCourseVerification sinVinculacion() {
        return new MoodleCourseVerification(
                MoodleVerificationStatus.SIN_VINCULACION,
                null,
                null,
                null,
                "Programa sin vinculación a curso de Moodle LMS",
                null
        );
    }

    public static MoodleCourseVerification verificado(Long id, String fullname, String shortname) {
        return new MoodleCourseVerification(
                MoodleVerificationStatus.VERIFICADO,
                id,
                fullname,
                shortname,
                "Curso verificado exitosamente en Moodle LMS",
                OffsetDateTime.now()
        );
    }

    public static MoodleCourseVerification noEncontrado(Long id) {
        return new MoodleCourseVerification(
                MoodleVerificationStatus.NO_ENCONTRADO,
                id,
                null,
                null,
                "El curso con ID " + id + " no existe en Moodle LMS",
                null
        );
    }

    public static MoodleCourseVerification noDisponible(Long id, String motivo) {
        return new MoodleCourseVerification(
                MoodleVerificationStatus.NO_DISPONIBLE,
                id,
                null,
                null,
                motivo,
                null
        );
    }
}


package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.MoodleCourseVerification;
import com.lysandri.domain.model.Programa;

import java.math.BigDecimal;
import java.util.List;

public interface CatalogUseCase {
    List<Programa> listarProgramasActivos();
    List<Programa> listarTodosAdmin();
    Programa obtenerProgramaPorSlug(String slug);
    Programa obtenerProgramaPorId(Long idPrograma);
    Programa obtenerProgramaAdminPorId(Long idPrograma);
    Programa crearPrograma(Programa programa);
    Programa crearPrograma(CrearProgramaCommand command);
    Programa actualizarPrograma(Long idPrograma, Programa programa);
    Programa actualizarPrograma(Long idPrograma, ActualizarProgramaCommand command);
    void desactivarPrograma(Long idPrograma);

    MoodleCourseVerification verificarCursoMoodle(Long moodleCourseId);
    Programa verificarYActualizarMoodlePrograma(Long idPrograma);
    Programa desvincularCursoMoodle(Long idPrograma);
    List<Programa> listarInventarioMoodle();

    record CrearProgramaCommand(
            String titulo,
            String slug,
            String subtitulo,
            String descripcionCorta,
            String descripcionDetallada,
            BigDecimal precio,
            String moneda,
            String imagenPortadaUrl,
            String syllabusUrl,
            String instructorNombre,
            String instructorBio,
            String nivel,
            Integer duracionHoras,
            String duracionPrograma,
            Boolean activo,
            Boolean precioAprobado,
            Boolean esGratuito,
            Long moodleCourseId
    ) {}

    record ActualizarProgramaCommand(
            String titulo,
            String slug,
            String subtitulo,
            String descripcionCorta,
            String descripcionDetallada,
            BigDecimal precio,
            String moneda,
            String imagenPortadaUrl,
            String syllabusUrl,
            String instructorNombre,
            String instructorBio,
            String nivel,
            Integer duracionHoras,
            String duracionPrograma,
            Boolean activo,
            Boolean precioAprobado,
            Boolean esGratuito,
            Long moodleCourseId,
            Boolean desvincularMoodle
    ) {
        public ActualizarProgramaCommand(
                String titulo,
                String slug,
                String subtitulo,
                String descripcionCorta,
                String descripcionDetallada,
                BigDecimal precio,
                String moneda,
                String imagenPortadaUrl,
                String syllabusUrl,
                String instructorNombre,
                String instructorBio,
                String nivel,
                Integer duracionHoras,
                String duracionPrograma,
                Boolean activo,
                Boolean precioAprobado,
                Boolean esGratuito,
                Long moodleCourseId
        ) {
            this(titulo, slug, subtitulo, descripcionCorta, descripcionDetallada, precio,
                    moneda, imagenPortadaUrl, syllabusUrl, instructorNombre, instructorBio,
                    nivel, duracionHoras, duracionPrograma, activo, precioAprobado,
                    esGratuito, moodleCourseId, false);
        }
    }
}

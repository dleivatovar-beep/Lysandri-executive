package com.lysandri.api.service.impl;

import com.lysandri.api.dto.response.ProgresoPorcentajeResponse;
import com.lysandri.api.dto.response.ProgresoResponse;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Leccion;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.model.entity.ProgresoLeccion;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.repository.InscripcionRepository;
import com.lysandri.api.repository.LeccionRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.repository.ProgresoLeccionRepository;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.service.ProgresoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ProgresoServiceImpl implements ProgresoService {

    private final ProgresoLeccionRepository progresoLeccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final LeccionRepository leccionRepository;
    private final ProgramaRepository programaRepository;
    private final InscripcionRepository inscripcionRepository;

    @Override
    @Transactional
    public ProgresoResponse marcarLeccionCompletada(Long usuarioId, Long leccionId) {
        Usuario usuario = usuarioRepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + usuarioId));

        Leccion leccion = leccionRepository.findById(leccionId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Lección no encontrada con id: " + leccionId));

        ProgresoLeccion progreso = progresoLeccionRepository.findByUsuarioIdAndLeccionId(usuarioId.intValue(), leccionId.intValue())
                .orElseGet(() -> ProgresoLeccion.builder()
                        .usuario(usuario)
                        .leccion(leccion)
                        .build());

        progreso.setCompletado(true);
        progreso.setFechaCompletado(OffsetDateTime.now());

        ProgresoLeccion guardado = progresoLeccionRepository.save(progreso);

        Programa programa = leccion.getPrograma();
        if (programa != null) {
            actualizarProgresoInscripcion(usuario.getIdUser(), programa.getIdPrograma());
        }

        return ProgresoResponse.builder()
                .id(guardado.getIdProgreso() != null ? Long.valueOf(guardado.getIdProgreso()) : null)
                .usuarioId(usuarioId)
                .leccionId(leccionId)
                .completado(guardado.getCompletado())
                .fechaCompletado(guardado.getFechaCompletado())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProgresoPorcentajeResponse obtenerPorcentajeAvance(Long usuarioId, Long programaId) {
        if (!usuarioRepository.existsById(usuarioId.intValue())) {
            throw new ResourceNotFoundException("Usuario no encontrado con id: " + usuarioId);
        }

        if (!programaRepository.existsById(programaId.intValue())) {
            throw new ResourceNotFoundException("Programa no encontrado con id: " + programaId);
        }

        long totalLecciones = leccionRepository.countByProgramaIdPrograma(programaId.intValue());
        long completadas = progresoLeccionRepository.countLeccionesCompletadas(usuarioId.intValue(), programaId.intValue());

        double porcentaje = totalLecciones > 0
                ? ((double) completadas / totalLecciones) * 100.0
                : 0.0;

        double porcentajeRedondeado = Math.round(porcentaje * 100.0) / 100.0;

        return ProgresoPorcentajeResponse.builder()
                .programaId(programaId)
                .totalLecciones(totalLecciones)
                .leccionesCompletadas(completadas)
                .porcentaje(porcentajeRedondeado)
                .build();
    }

    private void actualizarProgresoInscripcion(Integer idUser, Integer idPrograma) {
        inscripcionRepository.findByUsuarioIdUserAndProgramaIdPrograma(idUser, idPrograma)
                .ifPresent(inscripcion -> {
                    long total = leccionRepository.countByProgramaIdPrograma(idPrograma);
                    if (total > 0) {
                        long comp = progresoLeccionRepository.countLeccionesCompletadas(idUser, idPrograma);
                        double pct = ((double) comp / total) * 100.0;
                        double redondeado = Math.round(pct * 100.0) / 100.0;
                        inscripcion.setPorcentajeProgreso(BigDecimal.valueOf(redondeado));
                        inscripcionRepository.save(inscripcion);
                    }
                });
    }
}

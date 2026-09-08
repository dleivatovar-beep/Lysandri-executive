package com.lysandri.api.service.impl;

import com.lysandri.api.dto.response.InscripcionResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Inscripcion;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.repository.InscripcionRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.service.InscripcionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InscripcionServiceImpl implements InscripcionService {

    private final InscripcionRepository inscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProgramaRepository programaRepository;

    @Override
    @Transactional
    public InscripcionResponse inscribirEstudiante(Long usuarioId, Long programaId) {
        if (usuarioId == null) {
            throw new BadRequestException("El ID del usuario es obligatorio para la inscripción");
        }
        if (programaId == null) {
            throw new BadRequestException("El ID del programa es obligatorio para la inscripción");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + usuarioId));

        Programa programa = programaRepository.findById(programaId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con id: " + programaId));

        if (inscripcionRepository.existsByUsuarioIdAndProgramaId(usuarioId.intValue(), programaId.intValue())) {
            throw new BadRequestException("El estudiante ya se encuentra inscrito en este programa");
        }

        Inscripcion inscripcion = Inscripcion.builder()
                .usuario(usuario)
                .programa(programa)
                .fechaInscripcion(OffsetDateTime.now())
                .estatus("ACTIVO")
                .porcentajeProgreso(BigDecimal.ZERO)
                .build();

        Inscripcion guardada = inscripcionRepository.save(inscripcion);
        return mapToInscripcionResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InscripcionResponse> listarCursosDeEstudiante(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId.intValue())) {
            throw new ResourceNotFoundException("Usuario no encontrado con id: " + usuarioId);
        }

        return inscripcionRepository.findByUsuarioId(usuarioId.intValue()).stream()
                .map(this::mapToInscripcionResponse)
                .collect(Collectors.toList());
    }

    private InscripcionResponse mapToInscripcionResponse(Inscripcion i) {
        return InscripcionResponse.builder()
                .id(i.getIdInscripcion() != null ? Long.valueOf(i.getIdInscripcion()) : null)
                .usuarioId(i.getUsuario() != null ? Long.valueOf(i.getUsuario().getIdUser()) : null)
                .nombreEstudiante(i.getUsuario() != null ? i.getUsuario().getNombreCompleto() : null)
                .programaId(i.getPrograma() != null ? Long.valueOf(i.getPrograma().getIdPrograma()) : null)
                .tituloPrograma(i.getPrograma() != null ? i.getPrograma().getTituloPrograma() : null)
                .fechaInscripcion(i.getFechaInscripcion())
                .estado(i.getEstatus())
                .build();
    }
}

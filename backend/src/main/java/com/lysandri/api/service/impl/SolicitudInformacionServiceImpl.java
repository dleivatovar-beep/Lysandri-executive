package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.SolicitudInformacionRequest;
import com.lysandri.api.dto.response.SolicitudInformacionResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.model.entity.SolicitudInformacion;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.repository.SolicitudInformacionRepository;
import com.lysandri.api.service.SolicitudInformacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SolicitudInformacionServiceImpl implements SolicitudInformacionService {

    private final SolicitudInformacionRepository solicitudRepository;
    private final ProgramaRepository programaRepository;

    @Override
    @Transactional
    public SolicitudInformacionResponse registrarSolicitud(SolicitudInformacionRequest request) {
        Programa programa = null;
        if (request.getProgramaId() != null) {
            programa = programaRepository.findById(request.getProgramaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con ID: " + request.getProgramaId()));
        }

        SolicitudInformacion solicitud = SolicitudInformacion.builder()
                .nombreCompleto(request.getNombreCompleto().trim())
                .email(request.getEmail().trim().toLowerCase())
                .telefono(request.getTelefono() != null ? request.getTelefono().trim() : null)
                .programa(programa)
                .mensaje(request.getMensaje() != null ? request.getMensaje().trim() : null)
                .estado("PENDIENTE")
                .build();

        SolicitudInformacion guardada = solicitudRepository.save(solicitud);
        return mapToResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudInformacionResponse> listarTodas() {
        return solicitudRepository.findAllByOrderByFechaCreacionDesc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SolicitudInformacionResponse> listarPorEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return listarTodas();
        }
        return solicitudRepository.findByEstadoOrderByFechaCreacionDesc(estado.trim().toUpperCase())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SolicitudInformacionResponse obtenerPorId(Integer id) {
        SolicitudInformacion solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de información no encontrada con ID: " + id));
        return mapToResponse(solicitud);
    }

    @Override
    @Transactional
    public SolicitudInformacionResponse actualizarEstado(Integer id, String nuevoEstado, String notasAdmin) {
        if (nuevoEstado == null || nuevoEstado.isBlank()) {
            throw new BadRequestException("El nuevo estado es obligatorio");
        }

        SolicitudInformacion solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de información no encontrada con ID: " + id));

        solicitud.setEstado(nuevoEstado.trim().toUpperCase());
        solicitud.setFechaAtencion(OffsetDateTime.now());
        if (notasAdmin != null) {
            solicitud.setNotasAdmin(notasAdmin.trim());
        }

        SolicitudInformacion actualizada = solicitudRepository.save(solicitud);
        return mapToResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminarSolicitud(Integer id) {
        SolicitudInformacion solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de información no encontrada con ID: " + id));
        solicitudRepository.delete(solicitud);
    }

    private SolicitudInformacionResponse mapToResponse(SolicitudInformacion entity) {
        return SolicitudInformacionResponse.builder()
                .idSolicitud(entity.getIdSolicitud())
                .nombreCompleto(entity.getNombreCompleto())
                .email(entity.getEmail())
                .telefono(entity.getTelefono())
                .programaId(entity.getPrograma() != null ? entity.getPrograma().getIdPrograma() : null)
                .tituloPrograma(entity.getPrograma() != null ? entity.getPrograma().getTituloPrograma() : null)
                .mensaje(entity.getMensaje())
                .estado(entity.getEstado())
                .fechaSolicitud(entity.getFechaCreacion())
                .fechaActualizacion(entity.getFechaAtencion() != null ? entity.getFechaAtencion() : entity.getFechaCreacion())
                .fechaCreacion(entity.getFechaCreacion())
                .fechaAtencion(entity.getFechaAtencion())
                .notasAdmin(entity.getNotasAdmin())
                .build();
    }
}

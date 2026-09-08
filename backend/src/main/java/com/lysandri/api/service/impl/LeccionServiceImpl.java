package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.LeccionRequest;
import com.lysandri.api.dto.response.LeccionResponse;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Leccion;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.repository.LeccionRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.service.LeccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeccionServiceImpl implements LeccionService {

    private final LeccionRepository leccionRepository;
    private final ProgramaRepository programaRepository;

    @Override
    @Transactional
    public LeccionResponse crearLeccion(Long programaId, LeccionRequest request) {
        Programa programa = programaRepository.findById(programaId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con id: " + programaId));

        Leccion leccion = Leccion.builder()
                .programa(programa)
                .tituloLeccion(request.getTitulo())
                .descripcion(request.getDescripcion())
                .tipoContenido("VIDEO")
                .mediaUrl(request.getContenidoUrl())
                .duracionLeccion(request.getDuracionMinutos() != null ? String.valueOf(request.getDuracionMinutos()) : null)
                .orden(request.getOrden())
                .build();

        Leccion guardada = leccionRepository.save(leccion);
        return mapToLeccionResponse(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeccionResponse> listarPorPrograma(Long programaId) {
        if (!programaRepository.existsById(programaId.intValue())) {
            throw new ResourceNotFoundException("Programa no encontrado con id: " + programaId);
        }

        return leccionRepository.findByProgramaIdOrderByOrdenAsc(programaId.intValue()).stream()
                .map(this::mapToLeccionResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LeccionResponse actualizarLeccion(Long leccionId, LeccionRequest request) {
        Leccion leccion = leccionRepository.findById(leccionId.intValue())
                .orElseThrow(() -> new ResourceNotFoundException("Lección no encontrada con id: " + leccionId));

        leccion.setTituloLeccion(request.getTitulo());
        leccion.setDescripcion(request.getDescripcion());
        leccion.setMediaUrl(request.getContenidoUrl());
        leccion.setDuracionLeccion(request.getDuracionMinutos() != null ? String.valueOf(request.getDuracionMinutos()) : null);
        leccion.setOrden(request.getOrden());

        Leccion actualizada = leccionRepository.save(leccion);
        return mapToLeccionResponse(actualizada);
    }

    @Override
    @Transactional
    public void eliminarLeccion(Long leccionId) {
        if (!leccionRepository.existsById(leccionId.intValue())) {
            throw new ResourceNotFoundException("Lección no encontrada con id: " + leccionId);
        }
        leccionRepository.deleteById(leccionId.intValue());
    }

    private LeccionResponse mapToLeccionResponse(Leccion leccion) {
        Integer duracionMinutos = null;
        if (leccion.getDuracionLeccion() != null) {
            try {
                String soloDigitos = leccion.getDuracionLeccion().replaceAll("[^0-9]", "");
                if (!soloDigitos.isEmpty()) {
                    duracionMinutos = Integer.parseInt(soloDigitos);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return LeccionResponse.builder()
                .id(leccion.getIdLeccion() != null ? Long.valueOf(leccion.getIdLeccion()) : null)
                .programaId(leccion.getPrograma() != null ? Long.valueOf(leccion.getPrograma().getIdPrograma()) : null)
                .titulo(leccion.getTituloLeccion())
                .descripcion(leccion.getDescripcion())
                .contenidoUrl(leccion.getMediaUrl())
                .duracionMinutos(duracionMinutos)
                .orden(leccion.getOrden())
                .build();
    }
}

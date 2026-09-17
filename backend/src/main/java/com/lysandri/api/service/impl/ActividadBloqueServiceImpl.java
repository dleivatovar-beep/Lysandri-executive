package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.ActividadBloqueRequest;
import com.lysandri.api.dto.response.ActividadBloqueResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.ActividadBloque;
import com.lysandri.api.model.entity.Bloque;
import com.lysandri.api.repository.ActividadBloqueRepository;
import com.lysandri.api.repository.BloqueRepository;
import com.lysandri.api.service.AccesoAcademicoService;
import com.lysandri.api.service.ActividadBloqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActividadBloqueServiceImpl
        implements ActividadBloqueService {

    private final ActividadBloqueRepository actividadRepository;
    private final BloqueRepository bloqueRepository;
    private final AccesoAcademicoService accesoAcademicoService;

    @Override
    @Transactional
    public ActividadBloqueResponse crearActividad(
            Integer bloqueId,
            ActividadBloqueRequest request
    ) {
        Bloque bloque = buscarBloque(bloqueId);

        validarRequest(request);

        boolean ordenOcupado =
                actividadRepository
                        .existsByBloqueIdBloqueAndOrden(
                                bloqueId,
                                request.getOrden()
                        );

        if (ordenOcupado) {
            throw new BadRequestException(
                    "Ya existe una actividad con el orden "
                            + request.getOrden()
                            + " dentro del bloque"
            );
        }

        ActividadBloque actividad =
                new ActividadBloque();

        actividad.setBloque(bloque);
        actividad.setTitulo(
                request.getTitulo().trim()
        );
        actividad.setDescripcion(
                request.getDescripcion()
        );
        actividad.setInstrucciones(
                request.getInstrucciones()
        );
        actividad.setTipoActividad(
                request.getTipoActividad()
        );
        actividad.setContenidoUrl(
                request.getContenidoUrl()
        );
        actividad.setOrden(
                request.getOrden()
        );
        actividad.setActivo(
                request.getActivo() != null
                        ? request.getActivo()
                        : true
        );

        ActividadBloque guardada =
                actividadRepository.save(actividad);

        return convertirRespuesta(guardada);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActividadBloqueResponse> listarPorBloque(
            Integer bloqueId
    ) {
        buscarBloque(bloqueId);

        List<ActividadBloque> actividades =
                actividadRepository
                        .findByBloqueIdBloqueOrderByOrdenAsc(
                                bloqueId
                        );

        List<ActividadBloqueResponse> respuestas =
                new ArrayList<>();

        for (ActividadBloque actividad : actividades) {
            respuestas.add(
                    convertirRespuesta(actividad)
            );
        }

        return respuestas;
    }

    @Override
    @Transactional(readOnly = true)
    public ActividadBloqueResponse obtenerActividad(
            Integer actividadId
    ) {
        ActividadBloque actividad =
                buscarActividad(actividadId);

        return convertirRespuesta(actividad);
    }

    @Override
    @Transactional
    public ActividadBloqueResponse actualizarActividad(
            Integer actividadId,
            ActividadBloqueRequest request
    ) {
        ActividadBloque actividad =
                buscarActividad(actividadId);

        validarRequest(request);

        Integer bloqueId =
                actividad.getBloque().getIdBloque();

        boolean ordenOcupado =
                actividadRepository
                        .existsByBloqueIdBloqueAndOrdenAndIdActividadNot(
                                bloqueId,
                                request.getOrden(),
                                actividadId
                        );

        if (ordenOcupado) {
            throw new BadRequestException(
                    "Ya existe otra actividad con el orden "
                            + request.getOrden()
                            + " dentro del bloque"
            );
        }

        actividad.setTitulo(
                request.getTitulo().trim()
        );
        actividad.setDescripcion(
                request.getDescripcion()
        );
        actividad.setInstrucciones(
                request.getInstrucciones()
        );
        actividad.setTipoActividad(
                request.getTipoActividad()
        );
        actividad.setContenidoUrl(
                request.getContenidoUrl()
        );
        actividad.setOrden(
                request.getOrden()
        );

        if (request.getActivo() != null) {
            actividad.setActivo(
                    request.getActivo()
            );
        }

        ActividadBloque actualizada =
                actividadRepository.save(actividad);

        return convertirRespuesta(actualizada);
    }

    @Override
    @Transactional
    public void eliminarActividad(
            Integer actividadId
    ) {
        ActividadBloque actividad =
                buscarActividad(actividadId);

        actividadRepository.delete(actividad);
    }

    private Bloque buscarBloque(
            Integer bloqueId
    ) {
        if (bloqueId == null) {
            throw new BadRequestException(
                    "El identificador del bloque es obligatorio"
            );
        }

        Bloque bloque =
                bloqueRepository
                        .findById(bloqueId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Bloque no encontrado con id: "
                                                + bloqueId
                                )
                        );

        accesoAcademicoService.validarGestionPrograma(
                bloque.getPrograma().getIdPrograma()
        );

        return bloque;
    }

    private ActividadBloque buscarActividad(
            Integer actividadId
    ) {
        if (actividadId == null) {
            throw new BadRequestException(
                    "El identificador de la actividad es obligatorio"
            );
        }

        ActividadBloque actividad =
                actividadRepository
                        .findById(actividadId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException(
                                        "Actividad no encontrada con id: "
                                                + actividadId
                                )
                        );

        accesoAcademicoService.validarGestionPrograma(
                actividad.getBloque()
                        .getPrograma()
                        .getIdPrograma()
        );

        return actividad;
    }

    private void validarRequest(
            ActividadBloqueRequest request
    ) {
        if (request == null) {
            throw new BadRequestException(
                    "Los datos de la actividad son obligatorios"
            );
        }

        if (
                request.getTitulo() == null
                        || request.getTitulo().isBlank()
        ) {
            throw new BadRequestException(
                    "El título de la actividad es obligatorio"
            );
        }

        if (request.getTipoActividad() == null) {
            throw new BadRequestException(
                    "El tipo de actividad es obligatorio"
            );
        }

        if (
                request.getOrden() == null
                        || request.getOrden() <= 0
        ) {
            throw new BadRequestException(
                    "El orden de la actividad debe ser mayor que cero"
            );
        }
    }

    private ActividadBloqueResponse convertirRespuesta(
            ActividadBloque actividad
    ) {
        ActividadBloqueResponse response =
                new ActividadBloqueResponse();

        response.setIdActividad(
                actividad.getIdActividad()
        );
        response.setBloqueId(
                actividad.getBloque().getIdBloque()
        );
        response.setTitulo(
                actividad.getTitulo()
        );
        response.setDescripcion(
                actividad.getDescripcion()
        );
        response.setInstrucciones(
                actividad.getInstrucciones()
        );
        response.setTipoActividad(
                actividad.getTipoActividad()
        );
        response.setContenidoUrl(
                actividad.getContenidoUrl()
        );
        response.setOrden(
                actividad.getOrden()
        );
        response.setActivo(
                actividad.getActivo()
        );

        return response;
    }
}
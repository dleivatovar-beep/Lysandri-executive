package com.lysandri.api.service;

import com.lysandri.api.dto.request.ActividadBloqueRequest;
import com.lysandri.api.dto.response.ActividadBloqueResponse;

import java.util.List;

public interface ActividadBloqueService {

    ActividadBloqueResponse crearActividad(
            Integer bloqueId,
            ActividadBloqueRequest request
    );

    List<ActividadBloqueResponse> listarPorBloque(
            Integer bloqueId
    );

    ActividadBloqueResponse obtenerActividad(
            Integer actividadId
    );

    ActividadBloqueResponse actualizarActividad(
            Integer actividadId,
            ActividadBloqueRequest request
    );

    void eliminarActividad(
            Integer actividadId
    );
}
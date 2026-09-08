package com.lysandri.api.service;

import com.lysandri.api.dto.request.LeccionRequest;
import com.lysandri.api.dto.response.LeccionResponse;

import java.util.List;

public interface LeccionService {

    LeccionResponse crearLeccion(Long programaId, LeccionRequest request);

    List<LeccionResponse> listarPorPrograma(Long programaId);

    LeccionResponse actualizarLeccion(Long leccionId, LeccionRequest request);

    void eliminarLeccion(Long leccionId);
}

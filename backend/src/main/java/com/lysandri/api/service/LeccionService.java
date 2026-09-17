package com.lysandri.api.service;

import com.lysandri.api.dto.request.LeccionRequest;
import com.lysandri.api.dto.response.LeccionResponse;

import java.util.List;

public interface LeccionService {

    /*
     * Funciones antiguas por programa.
     */
    LeccionResponse crearLeccion(
            Long programaId,
            LeccionRequest request
    );

    List<LeccionResponse> listarPorPrograma(
            Long programaId
    );

    /*
     * Nuevas funciones por módulo.
     */
    LeccionResponse crearLeccionEnModulo(
            Integer moduloId,
            LeccionRequest request
    );

    List<LeccionResponse> listarPorModulo(
            Integer moduloId
    );

    LeccionResponse actualizarLeccion(
            Long leccionId,
            LeccionRequest request
    );

    void eliminarLeccion(
            Long leccionId
    );
}
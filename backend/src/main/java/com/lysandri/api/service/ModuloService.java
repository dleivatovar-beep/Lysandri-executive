
package com.lysandri.api.service;

import com.lysandri.api.dto.request.ModuloRequest;
import com.lysandri.api.dto.response.ModuloResponse;

import java.util.List;

public interface ModuloService {

    ModuloResponse crearModulo(
            Integer bloqueId,
            ModuloRequest request
    );

    List<ModuloResponse> listarPorBloque(
            Integer bloqueId
    );

    ModuloResponse obtenerModulo(
            Integer moduloId
    );

    ModuloResponse actualizarModulo(
            Integer moduloId,
            ModuloRequest request
    );

    void eliminarModulo(
            Integer moduloId
    );
}
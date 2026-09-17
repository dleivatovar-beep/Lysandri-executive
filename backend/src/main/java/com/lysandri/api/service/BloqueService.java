package com.lysandri.api.service;

import com.lysandri.api.dto.request.BloqueRequest;
import com.lysandri.api.dto.response.BloqueResponse;

import java.util.List;

public interface BloqueService {

    BloqueResponse crearBloque(
            Integer programaId,
            BloqueRequest request
    );

    List<BloqueResponse> listarPorPrograma(
            Integer programaId
    );

    BloqueResponse obtenerBloque(
            Integer bloqueId
    );

    BloqueResponse actualizarBloque(
            Integer bloqueId,
            BloqueRequest request
    );

    void eliminarBloque(
            Integer bloqueId
    );
}
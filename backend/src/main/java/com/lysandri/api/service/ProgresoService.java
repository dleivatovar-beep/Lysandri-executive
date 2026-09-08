package com.lysandri.api.service;

import com.lysandri.api.dto.response.ProgresoPorcentajeResponse;
import com.lysandri.api.dto.response.ProgresoResponse;

public interface ProgresoService {

    ProgresoResponse marcarLeccionCompletada(Long usuarioId, Long leccionId);

    ProgresoPorcentajeResponse obtenerPorcentajeAvance(Long usuarioId, Long programaId);
}

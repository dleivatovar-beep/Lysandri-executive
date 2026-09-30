package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.HistorialConsulta;

public interface HistorialConsultaRepositoryPort {

    HistorialConsulta guardar(HistorialConsulta historial);
}

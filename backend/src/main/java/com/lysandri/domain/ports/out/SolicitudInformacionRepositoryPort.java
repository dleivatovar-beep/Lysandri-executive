package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.SolicitudInformacion;

import java.util.List;

public interface SolicitudInformacionRepositoryPort {

    SolicitudInformacion guardar(SolicitudInformacion solicitud);

    List<SolicitudInformacion> listarTodas();
}

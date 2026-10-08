package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.SolicitudInformacion;

import java.util.List;
import java.util.Optional;

public interface SolicitudInformacionRepositoryPort {

    SolicitudInformacion guardar(SolicitudInformacion solicitud);

    List<SolicitudInformacion> listarTodas();

    Optional<SolicitudInformacion> buscarPorId(Long id);

    void eliminar(Long id);
}

package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.SolicitudInformacion;

import java.util.List;

public interface SolicitudInformacionUseCase {

    record CreateSolicitudCommand(
            Long idPrograma,
            String nombreCompleto,
            String email,
            String telefono,
            String empresa,
            String cargo,
            String mensaje
    ) {}

    SolicitudInformacion registrarSolicitud(CreateSolicitudCommand command);

    List<SolicitudInformacion> listarSolicitudes();

    SolicitudInformacion obtenerPorId(Long id);

    SolicitudInformacion actualizarEstado(Long id, String estado);

    void eliminarSolicitud(Long id);
}

package com.lysandri.api.service;

import com.lysandri.api.dto.request.SolicitudInformacionRequest;
import com.lysandri.api.dto.response.SolicitudInformacionResponse;

import java.util.List;

public interface SolicitudInformacionService {

    SolicitudInformacionResponse registrarSolicitud(SolicitudInformacionRequest request);

    List<SolicitudInformacionResponse> listarTodas();

    List<SolicitudInformacionResponse> listarPorEstado(String estado);

    SolicitudInformacionResponse obtenerPorId(Integer id);

    SolicitudInformacionResponse actualizarEstado(Integer id, String nuevoEstado, String notasAdmin);

    void eliminarSolicitud(Integer id);
}

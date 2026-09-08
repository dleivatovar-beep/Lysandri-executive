package com.lysandri.api.service;

import com.lysandri.api.dto.response.InscripcionResponse;

import java.util.List;

public interface InscripcionService {

    InscripcionResponse inscribirEstudiante(Long usuarioId, Long programaId);

    List<InscripcionResponse> listarCursosDeEstudiante(Long usuarioId);
}

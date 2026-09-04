package com.lysandri.api.service;

import com.lysandri.api.dto.request.UsuarioRequest;
import com.lysandri.api.dto.response.UsuarioResponse;

import java.util.List;

public interface UsuarioService {

    List<UsuarioResponse> getAllUsuarios();

    UsuarioResponse getUsuarioById(Integer id);

    UsuarioResponse createUsuario(UsuarioRequest request);
}

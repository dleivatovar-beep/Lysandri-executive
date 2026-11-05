package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.UsuarioActivacionToken;
import java.util.Optional;

public interface UserActivationPort {
    UsuarioActivacionToken crearTokenActivacion(Long idUser);
    Optional<UsuarioActivacionToken> buscarPorToken(String plainToken);
    Optional<UsuarioActivacionToken> buscarPorTokenConBloqueo(String plainToken);
    void marcarTokenUsado(Long idToken);
    void revocarTokensUsuario(Long idUser);
}

package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.Usuario;

import java.util.Optional;

public interface UserRepositoryPort {

    Usuario guardar(Usuario usuario);

    Optional<Usuario> buscarPorId(Long idUser);

    Optional<Usuario> buscarPorEmail(String email);

    boolean existePorEmail(String email);
}

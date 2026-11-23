package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {

    Usuario guardar(Usuario usuario);

    Optional<Usuario> buscarPorId(Long idUser);

    Optional<Usuario> buscarPorEmail(String email);

    boolean existePorEmail(String email);

    /** Solo para registros propios con consentimiento, nunca cuentas suspendidas o invitadas. */
    default boolean activarRegistroPropio(Long idUser) { return false; }

    List<Usuario> listarTodos();

    List<Usuario> listarPorRol(RolUsuario rol);

    List<Usuario> buscarPorTexto(String query);

    void eliminar(Long idUser);
}

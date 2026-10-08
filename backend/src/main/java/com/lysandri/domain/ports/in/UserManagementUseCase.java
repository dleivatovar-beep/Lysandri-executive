package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;

import java.util.List;

public interface UserManagementUseCase {

    List<Usuario> listarUsuarios(String search, RolUsuario rol);

    Usuario obtenerPorId(Long id);

    Usuario crearUsuario(Usuario usuario);

    Usuario actualizarUsuario(Long id, Usuario usuario);

    void eliminarUsuario(Long id);
}

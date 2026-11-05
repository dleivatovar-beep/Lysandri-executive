package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.model.UsuarioCambioAudit;

import java.util.List;

public interface UserManagementUseCase {

    List<Usuario> listarUsuarios(String search, RolUsuario rol);

    List<Usuario> listarTrabajadores();

    Usuario obtenerPorId(Long id);

    Usuario crearUsuario(Usuario usuario);

    Usuario actualizarUsuario(Long id, Usuario usuario);

    Usuario actualizarRol(Long id, RolUsuario nuevoRol, String motivo, String ejecutadoPor);

    Usuario desactivarUsuario(Long id, String motivo, String ejecutadoPor);

    List<UsuarioCambioAudit> obtenerHistorialCambios(Long id);

    void eliminarUsuario(Long id);
}

package com.lysandri.application.service;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.model.UsuarioCambioAudit;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import com.lysandri.domain.ports.out.UserAuditRepositoryPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserManagementService implements UserManagementUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final UserAuditRepositoryPort userAuditRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final com.lysandri.domain.ports.out.UserActivationPort userActivationPort;

    @Override
    @Transactional(readOnly = true)
    public List<Usuario> listarUsuarios(String search, RolUsuario rol) {
        if (search != null && !search.trim().isBlank()) {
            List<Usuario> resultados = userRepositoryPort.buscarPorTexto(search.trim());
            if (rol != null) {
                return resultados.stream()
                        .filter(u -> u.getRol() == rol)
                        .toList();
            }
            return resultados;
        }

        if (rol != null) {
            return userRepositoryPort.listarPorRol(rol);
        }

        return userRepositoryPort.listarTodos();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Usuario> listarTrabajadores() {
        return userRepositoryPort.listarTodos().stream()
                .filter(u -> u.getRol() != null && u.getRol().esRolInterno())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario obtenerPorId(Long id) {
        return userRepositoryPort.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
    }

    @Override
    @Transactional
    public Usuario crearUsuario(Usuario usuario) {
        if (usuario.getEmail() == null || usuario.getEmail().trim().isBlank()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio.");
        }

        String emailLimpio = usuario.getEmail().trim().toLowerCase();
        if (userRepositoryPort.existePorEmail(emailLimpio)) {
            throw new IllegalStateException("El correo ya se encuentra registrado: " + emailLimpio);
        }

        usuario.setEmail(emailLimpio);

        // Validar no duplicado de número de teléfono
        if (usuario.getTelefono() != null && !usuario.getTelefono().trim().isBlank()) {
            String telLimpio = usuario.getTelefono().replaceAll("\\D", "");
            if (telLimpio.length() >= 7) {
                boolean telDuplicado = userRepositoryPort.listarTodos().stream()
                        .filter(u -> u.getTelefono() != null && !u.getTelefono().isBlank())
                        .anyMatch(u -> {
                            String t = u.getTelefono().replaceAll("\\D", "");
                            return t.length() >= 7 && (t.endsWith(telLimpio) || telLimpio.endsWith(t));
                        });
                if (telDuplicado) {
                    throw new IllegalStateException("El número de teléfono ya se encuentra registrado por otra cuenta.");
                }
            }
        }

        if (usuario.getPassw() != null && !usuario.getPassw().isBlank()) {
            usuario.setPassw(passwordEncoder.encode(usuario.getPassw()));
        } else {
            String securePass = "Lys#" + java.util.UUID.randomUUID().toString().substring(0, 6) + "!";
            usuario.setPassw(passwordEncoder.encode(securePass));
        }

        if (usuario.getRol() == null) {
            usuario.setRol(RolUsuario.CLIENTE);
        }

        usuario.setActivo(true);
        usuario.setFechaCreacion(OffsetDateTime.now());
        usuario.setFechaActualizacion(OffsetDateTime.now());

        return userRepositoryPort.guardar(usuario);
    }

    @Override
    @Transactional
    public Usuario actualizarUsuario(Long id, Usuario datos) {
        Usuario existente = obtenerPorId(id);

        if (datos.getEmail() != null && !datos.getEmail().isBlank()) {
            String nuevoEmail = datos.getEmail().trim().toLowerCase();
            if (!nuevoEmail.equalsIgnoreCase(existente.getEmail())) {
                if (userRepositoryPort.existePorEmail(nuevoEmail)) {
                    throw new IllegalStateException("El nuevo correo ya está en uso por otra cuenta: " + nuevoEmail);
                }
                existente.setEmail(nuevoEmail);
            }
        }

        if (datos.getNombres() != null && !datos.getNombres().isBlank()) {
            existente.setNombres(datos.getNombres().trim());
        }

        if (datos.getApellidos() != null && !datos.getApellidos().isBlank()) {
            existente.setApellidos(datos.getApellidos().trim());
        }

        if (datos.getTelefono() != null) {
            String telLimpio = datos.getTelefono().replaceAll("\\D", "");
            if (telLimpio.length() >= 7) {
                boolean telDuplicado = userRepositoryPort.listarTodos().stream()
                        .filter(u -> !u.getIdUser().equals(id) && u.getTelefono() != null && !u.getTelefono().isBlank())
                        .anyMatch(u -> {
                            String t = u.getTelefono().replaceAll("\\D", "");
                            return t.length() >= 7 && (t.endsWith(telLimpio) || telLimpio.endsWith(t));
                        });
                if (telDuplicado) {
                    throw new IllegalStateException("El número de teléfono ya está en uso por otra cuenta.");
                }
            }
            existente.setTelefono(datos.getTelefono().trim());
        }

        if (datos.getPassw() != null && !datos.getPassw().isBlank()) {
            existente.setPassw(passwordEncoder.encode(datos.getPassw()));
        }

        existente.setFechaActualizacion(OffsetDateTime.now());

        return userRepositoryPort.guardar(existente);
    }

    @Override
    @Transactional
    public Usuario actualizarRol(Long id, RolUsuario nuevoRol, String motivo, String ejecutadoPor) {
        Usuario existente = obtenerPorId(id);
        if (nuevoRol == null) {
            throw new IllegalArgumentException("El rol nuevo es obligatorio.");
        }
        if (nuevoRol == RolUsuario.CLIENTE || nuevoRol == RolUsuario.ESTUDIANTE || nuevoRol == RolUsuario.INSTRUCTOR) {
            throw new IllegalStateException("No se permiten roles públicos para cuentas internas de personal.");
        }

        RolUsuario anterior = existente.getRol() != null ? existente.getRol() : RolUsuario.CLIENTE;
        existente.setRol(nuevoRol);
        existente.setFechaActualizacion(OffsetDateTime.now());
        Usuario actualizado = userRepositoryPort.guardar(existente);

        userAuditRepositoryPort.registrar(UsuarioCambioAudit.builder()
                .idUsuario(actualizado.getIdUser())
                .email(actualizado.getEmail())
                .accion("ROL_ACTUALIZADO")
                .rolAnterior(anterior)
                .rolNuevo(nuevoRol)
                .motivo(motivo != null ? motivo : "Actualización de permisos")
                .ejecutadoPor(ejecutadoPor != null ? ejecutadoPor : "system")
                .fechaHora(OffsetDateTime.now())
                .build());

        return actualizado;
    }

    @Override
    @Transactional
    public Usuario desactivarUsuario(Long id, String motivo, String ejecutadoPor) {
        Usuario existente = obtenerPorId(id);
        if (existente.isBloqueadoAdmin() && !existente.isActivo()) {
            return existente;
        }

        existente.setActivo(false);
        existente.setBloqueadoAdmin(true);
        existente.setPendienteActivacion(false);
        existente.setFechaActualizacion(OffsetDateTime.now());
        Usuario actualizado = userRepositoryPort.guardar(existente);

        // Invalidar tokens previos que pudieran reactivar la cuenta
        userActivationPort.revocarTokensUsuario(id);

        userAuditRepositoryPort.registrar(UsuarioCambioAudit.builder()
                .idUsuario(actualizado.getIdUser())
                .email(actualizado.getEmail())
                .accion("CUENTA_DESACTIVADA")
                .rolAnterior(actualizado.getRol())
                .rolNuevo(actualizado.getRol())
                .motivo(motivo != null ? motivo : "Desactivación por administración")
                .ejecutadoPor(ejecutadoPor != null ? ejecutadoPor : "system")
                .fechaHora(OffsetDateTime.now())
                .build());

        return actualizado;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioCambioAudit> obtenerHistorialCambios(Long id) {
        return userAuditRepositoryPort.listarPorUsuario(id);
    }

    @Override
    @Transactional
    public void eliminarUsuario(Long id) {
        obtenerPorId(id);
        userRepositoryPort.eliminar(id);
    }
}

package com.lysandri.application.service;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.UserManagementUseCase;
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
    private final PasswordEncoder passwordEncoder;

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

        if (datos.getRol() != null) {
            existente.setRol(datos.getRol());
        }

        if (datos.getPassw() != null && !datos.getPassw().isBlank()) {
            existente.setPassw(passwordEncoder.encode(datos.getPassw()));
        }

        existente.setFechaActualizacion(OffsetDateTime.now());

        return userRepositoryPort.guardar(existente);
    }

    @Override
    @Transactional
    public void eliminarUsuario(Long id) {
        // Verificar que exista
        obtenerPorId(id);
        userRepositoryPort.eliminar(id);
    }
}

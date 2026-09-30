package com.lysandri.application.service;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.AuthUseCase;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import com.lysandri.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements AuthUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    @Override
    @Transactional
    public AuthResponse register(RegisterCommand command) {
        log.info("Registrando nuevo cliente ejecutivo: {}", command.email());

        if (userRepository.existePorEmail(command.email())) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada con el correo: " + command.email());
        }

        Usuario usuario = Usuario.builder()
                .nombres(command.nombres())
                .apellidos(command.apellidos())
                .email(command.email().trim().toLowerCase())
                .passw(passwordEncoder.encode(command.password()))
                .telefono(command.telefono())
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .fechaCreacion(OffsetDateTime.now())
                .fechaActualizacion(OffsetDateTime.now())
                .build();

        usuario = userRepository.guardar(usuario);

        var userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token,
                usuario.getIdUser(),
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                usuario.getRol().name(),
                usuario.getMoodleUserId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginCommand command) {
        log.info("Intento de login para usuario: {}", command.email());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(command.email().trim().toLowerCase(), command.password())
        );

        Usuario usuario = userRepository.buscarPorEmail(command.email().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales no válidas"));

        var userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token,
                usuario.getIdUser(),
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                usuario.getRol().name(),
                usuario.getMoodleUserId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario obtenerUsuarioAutenticado(String email) {
        return userRepository.buscarPorEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con email: " + email));
    }
}

package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.LoginRequest;
import com.lysandri.api.dto.request.RegisterRequest;
import com.lysandri.api.dto.response.AuthResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.security.JwtService;
import com.lysandri.api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + request.getEmail()));

        String token = jwtService.generateToken(usuario);

        return AuthResponse.builder()
                .token(token)
                .id(usuario.getId())
                .nombre(usuario.getNombreCompleto())
                .email(usuario.getEmail())
                .rol(usuario.getRol())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("El correo electrónico ya se encuentra registrado");
        }

        String nombres = request.getNombre() != null ? request.getNombre().trim() : "";
        String apellidos = request.getApellidos() != null ? request.getApellidos().trim() : "";

        if (apellidos.isBlank() && nombres.contains(" ")) {
            String[] parts = nombres.split("\\s+", 2);
            nombres = parts[0];
            apellidos = parts.length > 1 ? parts[1] : "";
        }

        Usuario usuario = Usuario.builder()
                .nombres(nombres)
                .apellidos(apellidos)
                .email(request.getEmail())
                .passw(passwordEncoder.encode(request.getPassword()))
                .telefono(request.getTelefono())
                .rol(request.getRol())
                .build();

        Usuario saved = usuarioRepository.save(usuario);
        String token = jwtService.generateToken(saved);

        return AuthResponse.builder()
                .token(token)
                .id(saved.getId())
                .nombre(saved.getNombreCompleto())
                .email(saved.getEmail())
                .rol(saved.getRol())
                .build();
    }
}

package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.UsuarioRequest;
import com.lysandri.api.dto.response.UsuarioResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UsuarioResponse> getAllUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse getUsuarioById(Integer id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + id));
        return mapToResponse(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponse createUsuario(UsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("El correo ya se encuentra registrado");
        }

        Usuario usuario = Usuario.builder()
                .nombres(request.getNombres())
                .apellidos(request.getApellidos())
                .email(request.getEmail())
                .passw(request.getPassw())
                .telefono(request.getTelefono())
                .rol(request.getRol())
                .build();

        Usuario saved = usuarioRepository.save(usuario);
        return mapToResponse(saved);
    }

    private UsuarioResponse mapToResponse(Usuario u) {
        return UsuarioResponse.builder()
                .idUser(u.getIdUser())
                .nombres(u.getNombres())
                .apellidos(u.getApellidos())
                .email(u.getEmail())
                .telefono(u.getTelefono())
                .rol(u.getRol())
                .build();
    }
}

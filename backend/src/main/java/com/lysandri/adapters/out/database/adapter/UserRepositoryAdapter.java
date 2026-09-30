package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.adapters.out.database.repository.SpringDataUserRepository;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    @Override
    public Usuario guardar(Usuario usuario) {
        UsuarioEntity entity = toEntity(usuario);
        UsuarioEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Usuario> buscarPorId(Long idUser) {
        return repository.findById(idUser).map(this::toDomain);
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public boolean existePorEmail(String email) {
        return repository.existsByEmail(email);
    }

    private UsuarioEntity toEntity(Usuario d) {
        return UsuarioEntity.builder()
                .idUser(d.getIdUser())
                .moodleUserId(d.getMoodleUserId())
                .nombres(d.getNombres())
                .apellidos(d.getApellidos())
                .email(d.getEmail())
                .passw(d.getPassw())
                .telefono(d.getTelefono())
                .rol(d.getRol())
                .activo(d.isActivo())
                .fechaCreacion(d.getFechaCreacion())
                .fechaActualizacion(d.getFechaActualizacion())
                .build();
    }

    public Usuario toDomain(UsuarioEntity e) {
        return Usuario.builder()
                .idUser(e.getIdUser())
                .moodleUserId(e.getMoodleUserId())
                .nombres(e.getNombres())
                .apellidos(e.getApellidos())
                .email(e.getEmail())
                .passw(e.getPassw())
                .telefono(e.getTelefono())
                .rol(e.getRol())
                .activo(e.isActivo())
                .fechaCreacion(e.getFechaCreacion())
                .fechaActualizacion(e.getFechaActualizacion())
                .build();
    }
}

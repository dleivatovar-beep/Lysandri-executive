package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.UsuarioCambioAuditEntity;
import com.lysandri.adapters.out.database.repository.SpringDataUserAuditRepository;
import com.lysandri.domain.model.UsuarioCambioAudit;
import com.lysandri.domain.ports.out.UserAuditRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserAuditRepositoryAdapter implements UserAuditRepositoryPort {

    private final SpringDataUserAuditRepository repository;

    @Override
    public UsuarioCambioAudit registrar(UsuarioCambioAudit cambio) {
        UsuarioCambioAuditEntity entity = repository.save(toEntity(cambio));
        return toDomain(entity);
    }

    @Override
    public List<UsuarioCambioAudit> listarPorUsuario(Long idUsuario) {
        return repository.findByIdUsuarioOrderByFechaHoraDesc(idUsuario).stream()
                .map(this::toDomain)
                .toList();
    }

    private UsuarioCambioAuditEntity toEntity(UsuarioCambioAudit cambio) {
        if (cambio == null) {
            return null;
        }

        return UsuarioCambioAuditEntity.builder()
                .idAuditoria(cambio.getId())
                .idUsuario(cambio.getIdUsuario())
                .email(cambio.getEmail())
                .accion(cambio.getAccion())
                .rolAnterior(cambio.getRolAnterior())
                .rolNuevo(cambio.getRolNuevo())
                .motivo(cambio.getMotivo())
                .ejecutadoPor(cambio.getEjecutadoPor())
                .fechaHora(cambio.getFechaHora())
                .build();
    }

    private UsuarioCambioAudit toDomain(UsuarioCambioAuditEntity entity) {
        if (entity == null) {
            return null;
        }

        return UsuarioCambioAudit.builder()
                .id(entity.getIdAuditoria())
                .idUsuario(entity.getIdUsuario())
                .email(entity.getEmail())
                .accion(entity.getAccion())
                .rolAnterior(entity.getRolAnterior())
                .rolNuevo(entity.getRolNuevo())
                .motivo(entity.getMotivo())
                .ejecutadoPor(entity.getEjecutadoPor())
                .fechaHora(entity.getFechaHora())
                .build();
    }
}

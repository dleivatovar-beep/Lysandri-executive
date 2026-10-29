package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.AuditoriaContableEntity;
import com.lysandri.adapters.out.database.repository.SpringDataAccountingAuditRepository;
import com.lysandri.domain.model.AuditoriaContable;
import com.lysandri.domain.ports.out.AccountingAuditPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountingAuditRepositoryAdapter implements AccountingAuditPort {

    private final SpringDataAccountingAuditRepository repository;

    @Override
    public AuditoriaContable registrar(AuditoriaContable auditoria) {
        AuditoriaContableEntity saved = repository.save(AuditoriaContableEntity.builder()
                .accion(auditoria.getAccion())
                .entidad(auditoria.getEntidad())
                .entidadId(auditoria.getEntidadId())
                .actor(auditoria.getActor())
                .motivo(auditoria.getMotivo())
                .fechaHora(auditoria.getFechaHora())
                .build());
        return AuditoriaContable.builder()
                .id(saved.getIdAuditoria())
                .accion(saved.getAccion())
                .entidad(saved.getEntidad())
                .entidadId(saved.getEntidadId())
                .actor(saved.getActor())
                .motivo(saved.getMotivo())
                .fechaHora(saved.getFechaHora())
                .build();
    }
}

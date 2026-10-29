package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.UsuarioCambioAudit;

import java.util.List;

public interface UserAuditRepositoryPort {
    UsuarioCambioAudit registrar(UsuarioCambioAudit cambio);

    List<UsuarioCambioAudit> listarPorUsuario(Long idUsuario);
}

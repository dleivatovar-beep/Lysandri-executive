package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.AuditoriaContable;

public interface AccountingAuditPort {
    AuditoriaContable registrar(AuditoriaContable auditoria);
}

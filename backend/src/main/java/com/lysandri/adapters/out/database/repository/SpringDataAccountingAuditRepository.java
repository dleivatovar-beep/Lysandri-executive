package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.AuditoriaContableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataAccountingAuditRepository extends JpaRepository<AuditoriaContableEntity, Long> {
}

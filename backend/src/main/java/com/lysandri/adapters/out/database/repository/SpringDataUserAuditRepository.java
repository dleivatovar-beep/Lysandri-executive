package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.UsuarioCambioAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataUserAuditRepository extends JpaRepository<UsuarioCambioAuditEntity, Long> {
    List<UsuarioCambioAuditEntity> findByIdUsuarioOrderByFechaHoraDesc(Long idUsuario);
}

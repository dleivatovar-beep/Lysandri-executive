package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.LoteMatriculaCorporativaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataLoteMatriculaRepository extends JpaRepository<LoteMatriculaCorporativaEntity, Long> {
    List<LoteMatriculaCorporativaEntity> findByIdProgramaOrderByFechaRegistroDesc(Long idPrograma);
    List<LoteMatriculaCorporativaEntity> findByEmpresaNombreIgnoreCaseOrderByFechaRegistroDesc(String empresaNombre);
    List<LoteMatriculaCorporativaEntity> findAllByOrderByFechaRegistroDesc();
}

package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.ComprobantePagoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataComprobantePagoRepository extends JpaRepository<ComprobantePagoEntity, Long> {

    Optional<ComprobantePagoEntity> findByIdOrden(Long idOrden);

    Optional<ComprobantePagoEntity> findBySerieAndCorrelativo(String serie, Integer correlativo);

    @Query("SELECT COALESCE(MAX(c.correlativo), 0) FROM ComprobantePagoEntity c WHERE c.serie = :serie")
    Integer findMaxCorrelativoBySerie(@Param("serie") String serie);

    List<ComprobantePagoEntity> findAllByOrderByFechaEmisionDesc();
}

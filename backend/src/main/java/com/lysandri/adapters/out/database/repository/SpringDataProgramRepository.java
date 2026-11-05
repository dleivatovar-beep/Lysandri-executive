package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.ProgramaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataProgramRepository extends JpaRepository<ProgramaEntity, Long> {

    Optional<ProgramaEntity> findBySlug(String slug);

    Optional<ProgramaEntity> findByMoodleCourseId(Long moodleCourseId);

    List<ProgramaEntity> findByActivoTrue();

    Optional<ProgramaEntity> findBySlugAndActivoTrueAndPrecioAprobadoTrue(String slug);

    Optional<ProgramaEntity> findByIdProgramaAndActivoTrueAndPrecioAprobadoTrue(Long idPrograma);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProgramaEntity p WHERE p.idPrograma = :id")
    Optional<ProgramaEntity> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT p FROM ProgramaEntity p WHERE p.activo = true AND p.precioAprobado = true AND p.moodleVerificado = true AND p.moodleVerificadoEn IS NOT NULL AND p.moodleVerificadoEn >= :cutoff")
    List<ProgramaEntity> findComercialmenteDisponibles(@Param("cutoff") OffsetDateTime cutoff);

    @Query("SELECT p FROM ProgramaEntity p WHERE p.idPrograma = :idPrograma AND p.activo = true AND p.precioAprobado = true AND p.moodleVerificado = true AND p.moodleVerificadoEn IS NOT NULL AND p.moodleVerificadoEn >= :cutoff")
    Optional<ProgramaEntity> findComercialmenteDisponiblePorId(@Param("idPrograma") Long idPrograma, @Param("cutoff") OffsetDateTime cutoff);

    @Query("SELECT p FROM ProgramaEntity p WHERE p.slug = :slug AND p.activo = true AND p.precioAprobado = true AND p.moodleVerificado = true AND p.moodleVerificadoEn IS NOT NULL AND p.moodleVerificadoEn >= :cutoff")
    Optional<ProgramaEntity> findComercialmenteDisponiblePorSlug(@Param("slug") String slug, @Param("cutoff") OffsetDateTime cutoff);
}

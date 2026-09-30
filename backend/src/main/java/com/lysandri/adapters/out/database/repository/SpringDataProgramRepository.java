package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.ProgramaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataProgramRepository extends JpaRepository<ProgramaEntity, Long> {
    Optional<ProgramaEntity> findBySlug(String slug);
    Optional<ProgramaEntity> findByMoodleCourseId(Long moodleCourseId);
    List<ProgramaEntity> findByActivoTrue();
}

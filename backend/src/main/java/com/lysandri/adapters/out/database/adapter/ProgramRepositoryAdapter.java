package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.ProgramaEntity;
import com.lysandri.adapters.out.database.repository.SpringDataProgramRepository;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProgramRepositoryAdapter implements ProgramRepositoryPort {

    private final SpringDataProgramRepository repository;

    @Override
    public Programa guardar(Programa programa) {
        ProgramaEntity entity = toEntity(programa);
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Programa> buscarPorId(Long idPrograma) {
        return repository.findById(idPrograma).map(this::toDomain);
    }

    @Override
    public Optional<Programa> buscarPorSlug(String slug) {
        return repository.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public Optional<Programa> buscarPorMoodleCourseId(Long moodleCourseId) {
        return repository.findByMoodleCourseId(moodleCourseId).map(this::toDomain);
    }

    @Override
    public List<Programa> listarActivos() {
        return repository.findByActivoTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Programa> buscarPorIds(List<Long> ids) {
        return repository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    public ProgramaEntity toEntity(Programa d) {
        return ProgramaEntity.builder()
                .idPrograma(d.getIdPrograma())
                .moodleCourseId(d.getMoodleCourseId())
                .titulo(d.getTitulo())
                .slug(d.getSlug())
                .subtitulo(d.getSubtitulo())
                .descripcionCorta(d.getDescripcionCorta())
                .descripcionDetallada(d.getDescripcionDetallada())
                .precio(d.getPrecio())
                .moneda(d.getMoneda() != null ? d.getMoneda() : "USD")
                .imagenPortadaUrl(d.getImagenPortadaUrl())
                .syllabusUrl(d.getSyllabusUrl())
                .instructorNombre(d.getInstructorNombre())
                .instructorBio(d.getInstructorBio())
                .nivel(d.getNivel())
                .duracionHoras(d.getDuracionHoras())
                .activo(d.isActivo())
                .fechaCreacion(d.getFechaCreacion())
                .build();
    }

    public Programa toDomain(ProgramaEntity e) {
        return Programa.builder()
                .idPrograma(e.getIdPrograma())
                .moodleCourseId(e.getMoodleCourseId())
                .titulo(e.getTitulo())
                .slug(e.getSlug())
                .subtitulo(e.getSubtitulo())
                .descripcionCorta(e.getDescripcionCorta())
                .descripcionDetallada(e.getDescripcionDetallada())
                .precio(e.getPrecio())
                .moneda(e.getMoneda())
                .imagenPortadaUrl(e.getImagenPortadaUrl())
                .syllabusUrl(e.getSyllabusUrl())
                .instructorNombre(e.getInstructorNombre())
                .instructorBio(e.getInstructorBio())
                .nivel(e.getNivel())
                .duracionHoras(e.getDuracionHoras())
                .activo(e.isActivo())
                .fechaCreacion(e.getFechaCreacion())
                .build();
    }
}

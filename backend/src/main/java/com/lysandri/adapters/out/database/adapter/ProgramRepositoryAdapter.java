package com.lysandri.adapters.out.database.adapter;

import com.lysandri.adapters.out.database.entity.ProgramaEntity;
import com.lysandri.adapters.out.database.repository.SpringDataProgramRepository;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class ProgramRepositoryAdapter implements ProgramRepositoryPort {

    private final SpringDataProgramRepository repository;
    private final Clock clock;
    private final long ttlHours;

    public ProgramRepositoryAdapter(SpringDataProgramRepository repository) {
        this(repository, Clock.systemUTC(), 168L);
    }

    @Autowired
    public ProgramRepositoryAdapter(
            SpringDataProgramRepository repository,
            @Autowired(required = false) Clock clock,
            @Value("${moodle.verification.ttl-hours:168}") long ttlHours
    ) {
        this.repository = repository;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.ttlHours = ttlHours;
    }

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
    @Transactional
    public Optional<Programa> buscarPorIdConBloqueo(Long idPrograma) {
        return repository.findByIdWithLock(idPrograma).map(this::toDomain);
    }

    @Override
    public Optional<Programa> buscarPorSlug(String slug) {
        return repository.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public Optional<Programa> buscarActivoYAprobadoPorId(Long idPrograma) {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusHours(ttlHours);
        return repository.findComercialmenteDisponiblePorId(idPrograma, cutoff).map(this::toDomain);
    }

    @Override
    public Optional<Programa> buscarActivoYAprobadoPorSlug(String slug) {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusHours(ttlHours);
        return repository.findComercialmenteDisponiblePorSlug(slug, cutoff).map(this::toDomain);
    }

    @Override
    public Optional<Programa> buscarPorMoodleCourseId(Long moodleCourseId) {
        return repository.findByMoodleCourseId(moodleCourseId).map(this::toDomain);
    }

    @Override
    public List<Programa> listarActivos() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusHours(ttlHours);
        return repository.findComercialmenteDisponibles(cutoff).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Programa> listarTodos() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Programa> buscarPorIds(List<Long> ids) {
        return repository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    public ProgramaEntity toEntity(Programa d) {
        return ProgramaEntity.builder()
                .idPrograma(d.getIdPrograma())
                .titulo(d.getTitulo())
                .slug(d.getSlug())
                .subtitulo(d.getSubtitulo())
                .descripcionCorta(d.getDescripcionCorta())
                .descripcionDetallada(d.getDescripcionDetallada())
                .precio(d.getPrecio())
                .moneda(d.getMoneda())
                .imagenPortadaUrl(d.getImagenPortadaUrl())
                .syllabusUrl(d.getSyllabusUrl())
                .instructorNombre(d.getInstructorNombre())
                .instructorBio(d.getInstructorBio())
                .nivel(d.getNivel())
                .duracionHoras(d.getDuracionHoras())
                .activo(d.isActivo())
                .precioAprobado(d.isPrecioAprobado())
                .esGratuito(d.isEsGratuito())
                .moodleCourseId(d.getMoodleCourseId())
                .moodleCourseNombre(d.getMoodleCourseNombre())
                .moodleVerificado(d.isMoodleVerificado())
                .moodleVerificadoEn(d.getMoodleVerificadoEn())
                .moodleUltimoIntentoEn(d.getMoodleUltimoIntentoEn())
                .moodleVinculacionRevision(d.getMoodleVinculacionRevision() != null ? d.getMoodleVinculacionRevision() : 1L)
                .moodleEstadoVerificacion(d.getMoodleEstadoVerificacion())
                .build();
    }

    public Programa toDomain(ProgramaEntity e) {
        return Programa.builder()
                .idPrograma(e.getIdPrograma())
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
                .precioAprobado(e.isPrecioAprobado())
                .esGratuito(e.isEsGratuito())
                .moodleCourseId(e.getMoodleCourseId())
                .moodleCourseNombre(e.getMoodleCourseNombre())
                .moodleVerificado(e.isMoodleVerificado())
                .moodleVerificadoEn(e.getMoodleVerificadoEn())
                .moodleUltimoIntentoEn(e.getMoodleUltimoIntentoEn())
                .moodleVinculacionRevision(e.getMoodleVinculacionRevision() != null ? e.getMoodleVinculacionRevision() : 1L)
                .moodleEstadoVerificacion(e.getMoodleEstadoVerificacion())
                .build();
    }
}

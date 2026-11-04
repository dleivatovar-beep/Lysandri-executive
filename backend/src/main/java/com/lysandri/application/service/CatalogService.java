package com.lysandri.application.service;

import com.lysandri.domain.model.MoodleCourseVerification;
import com.lysandri.domain.model.MoodleVerificationStatus;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.in.CatalogUseCase;
import com.lysandri.domain.ports.out.LmsClientPort;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import com.lysandri.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class CatalogService implements CatalogUseCase {

    private final ProgramRepositoryPort programRepository;
    private final LmsClientPort lmsClientPort;
    private final Clock clock;
    private final long ttlHours;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    public CatalogService(
            ProgramRepositoryPort programRepository,
            LmsClientPort lmsClientPort,
            @Autowired(required = false) Clock clock,
            @Value("${moodle.verification.ttl-hours:168}") long ttlHours,
            org.springframework.transaction.support.TransactionTemplate transactionTemplate
    ) {
        this.programRepository = java.util.Objects.requireNonNull(programRepository, "programRepository es obligatorio");
        this.lmsClientPort = java.util.Objects.requireNonNull(lmsClientPort, "lmsClientPort es obligatorio");
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.ttlHours = ttlHours;
        this.transactionTemplate = java.util.Objects.requireNonNull(transactionTemplate, "transactionTemplate es obligatorio para garantizar transacciones reales");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Programa> listarProgramasActivos() {
        return programRepository.listarActivos();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Programa> listarTodosAdmin() {
        return programRepository.listarTodos();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Programa> listarInventarioMoodle() {
        return programRepository.listarTodos();
    }

    @Override
    @Transactional(readOnly = true)
    public MoodleCourseVerification verificarCursoMoodle(Long moodleCourseId) {
        if (moodleCourseId == null || moodleCourseId <= 0) {
            return MoodleCourseVerification.sinVinculacion();
        }
        return lmsClientPort.verificarCurso(moodleCourseId);
    }

    @Override
    public Programa verificarYActualizarMoodlePrograma(Long idPrograma) {
        Programa inicial = programRepository.buscarPorId(idPrograma)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con ID: " + idPrograma));

        if (inicial.getMoodleCourseId() == null || inicial.getMoodleCourseId() <= 0) {
            return desvincularCursoMoodle(idPrograma);
        }

        Long expectedCourseId = inicial.getMoodleCourseId();
        Long expectedRevision = inicial.getMoodleVinculacionRevision() != null ? inicial.getMoodleVinculacionRevision() : 1L;
        OffsetDateTime inicioVerificacion = OffsetDateTime.now(clock);

        // Llamada de red a Moodle LMS estrictamente fuera de transaccion y fuera de bloqueos
        MoodleCourseVerification verif = lmsClientPort.verificarCurso(expectedCourseId);

        // Aplicacion transaccional garantizada mediante TransactionTemplate (independiente de proxy Spring)
        return transactionTemplate.execute(status ->
                aplicarResultadoVerificacionTransaccional(idPrograma, expectedCourseId, expectedRevision, inicioVerificacion, verif)
        );
    }

    public Programa aplicarResultadoVerificacionTransaccional(Long idPrograma, Long expectedCourseId, Long expectedRevision, OffsetDateTime inicioVerificacion, MoodleCourseVerification verif) {
        // Adquirir bloqueo pesimista en la base de datos dentro de la transaccion activa
        Programa actual = programRepository.buscarPorIdConBloqueo(idPrograma)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con ID: " + idPrograma));

        // 1. Proteger frente a cambios concurrentes de vinculacion o desvinculacion
        if (!java.util.Objects.equals(actual.getMoodleCourseId(), expectedCourseId)) {
            log.warn("Respuesta tardia de Moodle descartada para programa ID {}: el curso asociado cambio concurrentemente de {} a {}",
                    idPrograma, expectedCourseId, actual.getMoodleCourseId());
            return actual;
        }

        // 2. Proteger frente a cambios A->B->A (la revision de vinculacion vario durante la consulta externa)
        if (!java.util.Objects.equals(actual.getMoodleVinculacionRevision(), expectedRevision)) {
            log.warn("Respuesta tardia de Moodle descartada para programa ID {}: la revision de vinculacion cambio de {} a {} (cambio A->B->A evitado)",
                    idPrograma, expectedRevision, actual.getMoodleVinculacionRevision());
            return actual;
        }

        // 3. Proteger frente a respuestas fuera de orden
        if (actual.getMoodleUltimoIntentoEn() != null && actual.getMoodleUltimoIntentoEn().isAfter(inicioVerificacion)) {
            log.warn("Respuesta tardia de Moodle descartada para programa ID {}: existe un intento mas reciente registrado en {}",
                    idPrograma, actual.getMoodleUltimoIntentoEn());
            return actual;
        }

        actual.setMoodleUltimoIntentoEn(inicioVerificacion);

        if (verif.status() == MoodleVerificationStatus.VERIFICADO) {
            actual.setMoodleVerificado(true);
            actual.setMoodleCourseNombre(verif.courseFullName());
            actual.setMoodleVerificadoEn(verif.verificadoEn() != null ? verif.verificadoEn() : inicioVerificacion);
            actual.setMoodleEstadoVerificacion(MoodleVerificationStatus.VERIFICADO);
            // Conserva precio, activo y titulo actuales intactos
        } else if (verif.status() == MoodleVerificationStatus.NO_ENCONTRADO) {
            actual.setMoodleVerificado(false);
            actual.setMoodleCourseNombre(null);
            actual.setMoodleEstadoVerificacion(MoodleVerificationStatus.NO_ENCONTRADO);
            actual.setActivo(false); // Inactivar comercialmente si el curso no existe en Moodle
        } else if (verif.status() == MoodleVerificationStatus.NO_DISPONIBLE) {
            OffsetDateTime cutoff = inicioVerificacion.minusHours(ttlHours);
            boolean expirada = actual.getMoodleVerificadoEn() == null || actual.getMoodleVerificadoEn().isBefore(cutoff);
            if (expirada || !actual.isMoodleVerificado()) {
                actual.setMoodleVerificado(false);
                actual.setMoodleEstadoVerificacion(MoodleVerificationStatus.NO_DISPONIBLE);
                actual.setActivo(false);
            } else {
                log.warn("Moodle temporalmente no disponible al verificar programa ID {}. Se conserva la verificacion previa vigente hasta expiracion del TTL.", idPrograma);
                // Conserva moodleVerificado = true, moodleEstadoVerificacion = VERIFICADO y activo
            }
        }

        return programRepository.guardar(actual);
    }

    public Programa aplicarResultadoVerificacion(Long idPrograma, Long expectedCourseId, OffsetDateTime inicioVerificacion, MoodleCourseVerification verif) {
        Programa snap = programRepository.buscarPorId(idPrograma).orElse(null);
        Long rev = snap != null && snap.getMoodleVinculacionRevision() != null ? snap.getMoodleVinculacionRevision() : 1L;
        return transactionTemplate.execute(status ->
                aplicarResultadoVerificacionTransaccional(idPrograma, expectedCourseId, rev, inicioVerificacion, verif)
        );
    }

    @Override
    @Transactional
    public Programa desvincularCursoMoodle(Long idPrograma) {
        Programa p = programRepository.buscarPorIdConBloqueo(idPrograma)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con ID: " + idPrograma));

        OffsetDateTime now = OffsetDateTime.now(clock);
        long currentRev = p.getMoodleVinculacionRevision() != null ? p.getMoodleVinculacionRevision() : 0L;
        p.setMoodleVinculacionRevision(currentRev + 1);
        p.setMoodleCourseId(null);
        p.setMoodleCourseNombre(null);
        p.setMoodleVerificado(false);
        p.setMoodleVerificadoEn(null);
        p.setMoodleUltimoIntentoEn(now);
        p.setMoodleEstadoVerificacion(MoodleVerificationStatus.SIN_VINCULACION);
        p.setActivo(false); // No puede comercializarse sin curso vinculado
        log.info("Programa ID {} desvinculado explicitamente de Moodle. Desactivado correctamente.", idPrograma);
        return programRepository.guardar(p);
    }

    @Override
    @Transactional(readOnly = true)
    public Programa obtenerProgramaPorSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ResourceNotFoundException("El slug no puede estar vacío");
        }
        return programRepository.buscarActivoYAprobadoPorSlug(slug.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado o no disponible para el slug: " + slug));
    }

    @Override
    @Transactional(readOnly = true)
    public Programa obtenerProgramaPorId(Long idPrograma) {
        if (idPrograma == null || idPrograma <= 0) {
            throw new ResourceNotFoundException("ID de programa inválido: " + idPrograma);
        }
        return programRepository.buscarActivoYAprobadoPorId(idPrograma)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado o no disponible con ID: " + idPrograma));
    }

    @Override
    @Transactional(readOnly = true)
    public Programa obtenerProgramaAdminPorId(Long idPrograma) {
        if (idPrograma == null || idPrograma <= 0) {
            throw new ResourceNotFoundException("ID de programa inválido: " + idPrograma);
        }
        return programRepository.buscarPorId(idPrograma)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con ID: " + idPrograma));
    }

    @Override
    @Transactional
    public Programa crearPrograma(CrearProgramaCommand cmd) {
        if (cmd == null || cmd.titulo() == null || cmd.titulo().isBlank()) {
            throw new IllegalArgumentException("El título del programa es obligatorio");
        }

        String titulo = cmd.titulo().trim();
        String slug = cmd.slug();
        if (slug == null || slug.isBlank()) {
            slug = java.text.Normalizer.normalize(titulo, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
        } else {
            slug = java.text.Normalizer.normalize(slug.trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
        }

        if (programRepository.buscarPorSlug(slug).isPresent()) {
            throw new IllegalArgumentException("Ya existe un programa con el slug: " + slug);
        }

        BigDecimal precio = cmd.precio() != null ? cmd.precio() : BigDecimal.ZERO;
        if (precio.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El precio del programa no puede ser negativo");
        }

        boolean esGratuito = Boolean.TRUE.equals(cmd.esGratuito()) || precio.compareTo(BigDecimal.ZERO) == 0;
        if (Boolean.TRUE.equals(cmd.esGratuito()) && precio.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException("Un programa gratuito no puede tener un precio mayor a cero");
        }
        if (esGratuito) {
            precio = BigDecimal.ZERO;
        }

        String moneda = cmd.moneda() != null && !cmd.moneda().isBlank()
                ? cmd.moneda().trim().toUpperCase()
                : "USD";

        Long moodleCourseId = null;
        String moodleCourseNombre = null;
        boolean moodleVerificado = false;
        OffsetDateTime moodleVerificadoEn = null;
        OffsetDateTime moodleUltimoIntentoEn = null;
        MoodleVerificationStatus moodleEstadoVerificacion = MoodleVerificationStatus.SIN_VINCULACION;
        boolean activoDeseado = Boolean.TRUE.equals(cmd.activo());

        if (cmd.moodleCourseId() != null && cmd.moodleCourseId() > 0) {
            Long targetCourseId = cmd.moodleCourseId();
            programRepository.buscarPorMoodleCourseId(targetCourseId).ifPresent(otro -> {
                throw new IllegalArgumentException("El curso de Moodle con ID " + targetCourseId
                        + " ya está vinculado al programa '" + otro.getTitulo() + "' (ID: " + otro.getIdPrograma() + ")");
            });

            OffsetDateTime now = OffsetDateTime.now(clock);
            moodleUltimoIntentoEn = now;
            MoodleCourseVerification verif = lmsClientPort.verificarCurso(targetCourseId);
            moodleCourseId = targetCourseId;

            if (verif.status() == MoodleVerificationStatus.VERIFICADO) {
                moodleCourseNombre = verif.courseFullName();
                moodleVerificado = true;
                moodleVerificadoEn = verif.verificadoEn() != null ? verif.verificadoEn() : now;
                moodleEstadoVerificacion = MoodleVerificationStatus.VERIFICADO;
            } else if (verif.status() == MoodleVerificationStatus.NO_ENCONTRADO) {
                moodleEstadoVerificacion = MoodleVerificationStatus.NO_ENCONTRADO;
                if (activoDeseado) {
                    throw new IllegalArgumentException("No se puede publicar el programa: El curso de Moodle con ID "
                            + targetCourseId + " no existe.");
                }
                activoDeseado = false;
            } else { // NO_DISPONIBLE
                moodleEstadoVerificacion = MoodleVerificationStatus.NO_DISPONIBLE;
                if (activoDeseado) {
                    throw new IllegalArgumentException("No se puede publicar el programa: Verificación no disponible en Moodle ("
                            + verif.detalle() + "). Guárdelo como borrador hasta verificar el curso.");
                }
                activoDeseado = false;
            }
        } else {
            if (activoDeseado) {
                throw new IllegalArgumentException("No se puede publicar un programa sin vinculación verificada a un curso existente en Moodle LMS. Guárdelo como borrador (activo = false) o vincule y verifique un curso válido.");
            }
            activoDeseado = false;
        }

        Integer duracionHoras = cmd.duracionHoras();
        if (duracionHoras == null && cmd.duracionPrograma() != null) {
            String digits = cmd.duracionPrograma().replaceAll("\\D+", "");
            if (!digits.isBlank()) {
                try {
                    duracionHoras = Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {}
            }
        }

        Programa programa = Programa.builder()
                .titulo(titulo)
                .slug(slug)
                .subtitulo(cmd.subtitulo() != null ? cmd.subtitulo().trim() : null)
                .descripcionCorta(cmd.descripcionCorta() != null ? cmd.descripcionCorta().trim() : null)
                .descripcionDetallada(cmd.descripcionDetallada() != null ? cmd.descripcionDetallada().trim() : null)
                .precio(precio)
                .moneda(moneda)
                .imagenPortadaUrl(cmd.imagenPortadaUrl() != null ? cmd.imagenPortadaUrl().trim() : null)
                .syllabusUrl(cmd.syllabusUrl() != null ? cmd.syllabusUrl().trim() : null)
                .instructorNombre(cmd.instructorNombre() != null ? cmd.instructorNombre().trim() : null)
                .instructorBio(cmd.instructorBio() != null ? cmd.instructorBio().trim() : null)
                .nivel(cmd.nivel() != null ? cmd.nivel().trim() : null)
                .duracionHoras(duracionHoras)
                .activo(activoDeseado)
                .precioAprobado(cmd.precioAprobado() != null ? cmd.precioAprobado() : false)
                .esGratuito(esGratuito)
                .moodleCourseId(moodleCourseId)
                .moodleCourseNombre(moodleCourseNombre)
                .moodleVerificado(moodleVerificado)
                .moodleVerificadoEn(moodleVerificadoEn)
                .moodleUltimoIntentoEn(moodleUltimoIntentoEn)
                .moodleEstadoVerificacion(moodleEstadoVerificacion)
                .build();

        log.info("Creando programa académico: {} (slug: {}, moodleCourseId: {}, verificado: {})",
                programa.getTitulo(), programa.getSlug(), programa.getMoodleCourseId(), programa.isMoodleVerificado());
        return programRepository.guardar(programa);
    }

    @Override
    @Transactional
    public Programa crearPrograma(Programa programa) {
        if (programa == null) {
            throw new IllegalArgumentException("El programa no puede ser nulo");
        }
        return crearPrograma(new CrearProgramaCommand(
                programa.getTitulo(),
                programa.getSlug(),
                programa.getSubtitulo(),
                programa.getDescripcionCorta(),
                programa.getDescripcionDetallada(),
                programa.getPrecio(),
                programa.getMoneda(),
                programa.getImagenPortadaUrl(),
                programa.getSyllabusUrl(),
                programa.getInstructorNombre(),
                programa.getInstructorBio(),
                programa.getNivel(),
                programa.getDuracionHoras(),
                null,
                programa.isActivo(),
                programa.isPrecioAprobado(),
                programa.isEsGratuito(),
                programa.getMoodleCourseId()
        ));
    }

    @Override
    @Transactional
    public Programa actualizarPrograma(Long idPrograma, ActualizarProgramaCommand cmd) {
        Programa existente = programRepository.buscarPorId(idPrograma)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con ID: " + idPrograma));

        if (cmd.titulo() != null && !cmd.titulo().isBlank()) {
            existente.setTitulo(cmd.titulo().trim());
        }
        if (cmd.slug() != null && !cmd.slug().isBlank()) {
            String nuevoSlug = java.text.Normalizer.normalize(cmd.slug().trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
            if (!nuevoSlug.equals(existente.getSlug())) {
                if (programRepository.buscarPorSlug(nuevoSlug).isPresent()) {
                    throw new IllegalArgumentException("Ya existe otro programa con el slug: " + nuevoSlug);
                }
                existente.setSlug(nuevoSlug);
            }
        }
        if (cmd.subtitulo() != null) {
            existente.setSubtitulo(cmd.subtitulo().trim());
        }
        if (cmd.descripcionCorta() != null) {
            existente.setDescripcionCorta(cmd.descripcionCorta().trim());
        }
        if (cmd.descripcionDetallada() != null) {
            existente.setDescripcionDetallada(cmd.descripcionDetallada().trim());
        }
        if (cmd.moneda() != null && !cmd.moneda().isBlank()) {
            existente.setMoneda(cmd.moneda().trim().toUpperCase());
        }
        if (cmd.imagenPortadaUrl() != null) {
            existente.setImagenPortadaUrl(cmd.imagenPortadaUrl().trim());
        }
        if (cmd.syllabusUrl() != null) {
            existente.setSyllabusUrl(cmd.syllabusUrl().trim());
        }
        if (cmd.instructorNombre() != null) {
            existente.setInstructorNombre(cmd.instructorNombre().trim());
        }
        if (cmd.instructorBio() != null) {
            existente.setInstructorBio(cmd.instructorBio().trim());
        }
        if (cmd.nivel() != null) {
            existente.setNivel(cmd.nivel().trim());
        }
        if (cmd.duracionHoras() != null) {
            existente.setDuracionHoras(cmd.duracionHoras());
        } else if (cmd.duracionPrograma() != null) {
            String digits = cmd.duracionPrograma().replaceAll("\\D+", "");
            if (!digits.isBlank()) {
                try {
                    existente.setDuracionHoras(Integer.parseInt(digits));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Gestión de gratuidad y precio
        if (cmd.esGratuito() != null) {
            existente.setEsGratuito(cmd.esGratuito());
            if (cmd.esGratuito()) {
                existente.setPrecio(BigDecimal.ZERO);
            }
        }

        if (cmd.precio() != null) {
            if (cmd.precio().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El precio del programa no puede ser negativo");
            }
            if (existente.isEsGratuito() && cmd.precio().compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("Un programa marcado como gratuito no puede tener un precio mayor a cero");
            }

            boolean precioHaCambiado = existente.getPrecio() == null || existente.getPrecio().compareTo(cmd.precio()) != 0;
            existente.setPrecio(cmd.precio());

            if (precioHaCambiado) {
                if (cmd.precioAprobado() != null) {
                    existente.setPrecioAprobado(cmd.precioAprobado());
                } else {
                    log.info("Precio modificado para programa ID {}. Reiniciando precioAprobado a false por gobernanza comercial.", idPrograma);
                    existente.setPrecioAprobado(false);
                }
            }
        }

        if (cmd.precioAprobado() != null && cmd.precio() == null) {
            existente.setPrecioAprobado(cmd.precioAprobado());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        // Gestión y verificación de vinculación a curso Moodle
        if (Boolean.TRUE.equals(cmd.desvincularMoodle())) {
            long currentRev = existente.getMoodleVinculacionRevision() != null ? existente.getMoodleVinculacionRevision() : 0L;
            existente.setMoodleVinculacionRevision(currentRev + 1);
            existente.setMoodleCourseId(null);
            existente.setMoodleCourseNombre(null);
            existente.setMoodleVerificado(false);
            existente.setMoodleVerificadoEn(null);
            existente.setMoodleUltimoIntentoEn(now);
            existente.setMoodleEstadoVerificacion(MoodleVerificationStatus.SIN_VINCULACION);
            existente.setActivo(false);
            if (Boolean.TRUE.equals(cmd.activo())) {
                throw new IllegalArgumentException("No se puede mantener activo un programa sin curso vinculado en Moodle LMS.");
            }
        } else if (cmd.moodleCourseId() != null) {
            if (cmd.moodleCourseId() <= 0) {
                throw new IllegalArgumentException("El identificador del curso Moodle debe ser un número positivo. Para desvincular un curso utilice la acción explícita.");
            }
            Long nuevoMoodleCourseId = cmd.moodleCourseId();
            boolean cursoCambio = !nuevoMoodleCourseId.equals(existente.getMoodleCourseId());

            if (cursoCambio) {
                long currentRev = existente.getMoodleVinculacionRevision() != null ? existente.getMoodleVinculacionRevision() : 0L;
                existente.setMoodleVinculacionRevision(currentRev + 1);
                // Validar unicidad del curso Moodle
                programRepository.buscarPorMoodleCourseId(nuevoMoodleCourseId).ifPresent(otro -> {
                    if (!otro.getIdPrograma().equals(idPrograma)) {
                        throw new IllegalArgumentException("El curso de Moodle con ID " + nuevoMoodleCourseId
                                + " ya está vinculado al programa '" + otro.getTitulo() + "' (ID: " + otro.getIdPrograma() + ")");
                    }
                });

                // Invalidar inmediatamente la verificación previa por cambio de ID
                existente.setMoodleCourseId(nuevoMoodleCourseId);
                existente.setMoodleCourseNombre(null);
                existente.setMoodleVerificado(false);
                existente.setMoodleVerificadoEn(null);
                existente.setMoodleUltimoIntentoEn(now);
                existente.setMoodleEstadoVerificacion(MoodleVerificationStatus.PENDIENTE_VERIFICACION);

                MoodleCourseVerification verif = lmsClientPort.verificarCurso(nuevoMoodleCourseId);
                if (verif.status() == MoodleVerificationStatus.VERIFICADO) {
                    existente.setMoodleCourseNombre(verif.courseFullName());
                    existente.setMoodleVerificado(true);
                    existente.setMoodleVerificadoEn(verif.verificadoEn() != null ? verif.verificadoEn() : now);
                    existente.setMoodleEstadoVerificacion(MoodleVerificationStatus.VERIFICADO);
                } else if (verif.status() == MoodleVerificationStatus.NO_ENCONTRADO) {
                    existente.setMoodleEstadoVerificacion(MoodleVerificationStatus.NO_ENCONTRADO);
                    existente.setActivo(false);
                    if (Boolean.TRUE.equals(cmd.activo())) {
                        throw new IllegalArgumentException("No se puede publicar el programa: El curso de Moodle con ID "
                                + nuevoMoodleCourseId + " no existe.");
                    }
                } else { // NO_DISPONIBLE
                    existente.setMoodleEstadoVerificacion(MoodleVerificationStatus.NO_DISPONIBLE);
                    existente.setActivo(false);
                    if (Boolean.TRUE.equals(cmd.activo())) {
                        throw new IllegalArgumentException("No se puede activar el programa: Verificación no disponible en Moodle ("
                                + verif.detalle() + ").");
                    }
                }
            }
        }

        // Reglas de activación y publicación comercial
        if (cmd.activo() != null) {
            if (cmd.activo()) {
                if (existente.getMoodleCourseId() == null) {
                    throw new IllegalArgumentException("No se puede publicar un programa sin vinculación a un curso de Moodle LMS. Vincule un curso antes de activarlo.");
                }
                OffsetDateTime cutoff = now.minusHours(ttlHours);
                boolean expirada = existente.getMoodleVerificadoEn() == null || existente.getMoodleVerificadoEn().isBefore(cutoff);

                if (!existente.isMoodleVerificado() || expirada) {
                    // Intento de revalidación al momento de publicar
                    existente.setMoodleUltimoIntentoEn(now);
                    MoodleCourseVerification verif = lmsClientPort.verificarCurso(existente.getMoodleCourseId());
                    if (verif.status() == MoodleVerificationStatus.VERIFICADO) {
                        existente.setMoodleVerificado(true);
                        existente.setMoodleCourseNombre(verif.courseFullName());
                        existente.setMoodleVerificadoEn(verif.verificadoEn() != null ? verif.verificadoEn() : now);
                        existente.setMoodleEstadoVerificacion(MoodleVerificationStatus.VERIFICADO);
                        existente.setActivo(true);
                    } else {
                        existente.setActivo(false);
                        throw new IllegalArgumentException("No se puede publicar el programa porque la verificación del curso en Moodle ha expirado o no pudo ser confirmada: " + verif.detalle());
                    }
                } else {
                    existente.setActivo(true);
                }
            } else {
                existente.setActivo(false);
            }
        }

        log.info("Actualizando programa académico ID {}: {} (activo: {}, verificado: {}, moodleCourseId: {})",
                idPrograma, existente.getTitulo(), existente.isActivo(), existente.isMoodleVerificado(), existente.getMoodleCourseId());
        return programRepository.guardar(existente);
    }

    @Override
    @Transactional
    public Programa actualizarPrograma(Long idPrograma, Programa datos) {
        if (datos == null) {
            throw new IllegalArgumentException("Los datos de actualización no pueden ser nulos");
        }
        return actualizarPrograma(idPrograma, new ActualizarProgramaCommand(
                datos.getTitulo(),
                datos.getSlug(),
                datos.getSubtitulo(),
                datos.getDescripcionCorta(),
                datos.getDescripcionDetallada(),
                datos.getPrecio(),
                datos.getMoneda(),
                datos.getImagenPortadaUrl(),
                datos.getSyllabusUrl(),
                datos.getInstructorNombre(),
                datos.getInstructorBio(),
                datos.getNivel(),
                datos.getDuracionHoras(),
                null,
                datos.isActivo(),
                datos.isPrecioAprobado(),
                datos.isEsGratuito(),
                datos.getMoodleCourseId(),
                false
        ));
    }

    @Override
    @Transactional
    public void desactivarPrograma(Long idPrograma) {
        Programa existente = programRepository.buscarPorId(idPrograma)
                .orElseThrow(() -> new ResourceNotFoundException("Programa no encontrado con ID: " + idPrograma));
        existente.setActivo(false);
        programRepository.guardar(existente);
        log.info("Programa ID {} desactivado correctamente (no eliminado físicamente)", idPrograma);
    }
}

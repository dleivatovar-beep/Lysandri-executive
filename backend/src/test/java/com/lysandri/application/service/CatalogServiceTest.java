package com.lysandri.application.service;

import com.lysandri.domain.model.MoodleCourseVerification;

import com.lysandri.domain.model.MoodleVerificationStatus;

import com.lysandri.domain.model.Programa;

import com.lysandri.domain.ports.in.CatalogUseCase.ActualizarProgramaCommand;

import com.lysandri.domain.ports.in.CatalogUseCase.CrearProgramaCommand;

import com.lysandri.domain.ports.out.LmsClientPort;

import com.lysandri.domain.ports.out.ProgramRepositoryPort;

import com.lysandri.exception.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;

import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import java.time.OffsetDateTime;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

@DisplayName("Pruebas Unitarias: CatalogService con Vinculación a Cursos Moodle LMS")

class CatalogServiceTest {

    @Mock

    private ProgramRepositoryPort programRepository;

    @Mock

    private LmsClientPort lmsClientPort;

    private static final java.time.Instant NOW = java.time.Instant.parse("2026-11-01T12:00:00Z");

    private static final java.time.ZoneId UTC = java.time.ZoneId.of("UTC");

    private final java.time.Clock clock = java.time.Clock.fixed(NOW, UTC);

    private final org.springframework.transaction.support.TransactionTemplate testTransactionTemplate =
            new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                        @Override
                        protected Object doGetTransaction() { return new Object(); }
                        @Override
                        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {}
                        @Override
                        protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                        @Override
                        protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {}
                    }
            );

    private CatalogService catalogService;

    private Programa programaBase;

    @BeforeEach

    void setUp() {

        catalogService = new CatalogService(programRepository, lmsClientPort, clock, 168L, testTransactionTemplate);

        lenient().when(programRepository.buscarPorIdConBloqueo(anyLong()))

                .thenAnswer(inv -> programRepository.buscarPorId(inv.getArgument(0)));

        programaBase = Programa.builder()

                .idPrograma(10L)

                .titulo("Gobierno Corporativo")

                .slug("gobierno-corporativo")

                .precio(new BigDecimal("950.00"))

                .moneda("USD")

                .activo(true)

                .precioAprobado(true)

                .esGratuito(false)

                .moodleCourseId(101L)

                .moodleCourseNombre("Curso Moodle 101: Gobierno Corporativo")

                .moodleVerificado(true)

                .moodleVerificadoEn(OffsetDateTime.ofInstant(NOW, UTC))

                .moodleEstadoVerificacion(MoodleVerificationStatus.VERIFICADO)

                .build();

    }

    @Test

    @DisplayName("Crear borrador sin vinculación a Moodle (activo = false) debe guardarse como SIN_VINCULACION")

    void crearPrograma_sinMoodleId_borrador_debeGuardarComoSinVinculacion() {

        CrearProgramaCommand cmd = new CrearProgramaCommand(

                "Liderazgo Estratégico",

                null,

                "Subtítulo",

                "Desc corta",

                "Desc detallada",

                new BigDecimal("600.00"),

                "USD",

                null, null, "Profesor", null, "Avanzado", 40, "40h",

                false, false, false, null // activo = false (borrador)

        );

        when(programRepository.buscarPorSlug("liderazgo-estrategico")).thenReturn(Optional.empty());

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        Programa creado = catalogService.crearPrograma(cmd);

        assertNotNull(creado);

        assertNull(creado.getMoodleCourseId(), "Los borradores no vinculados explícitamente deben conservar moodleCourseId como null");

        assertFalse(creado.isActivo(), "Un borrador sin vinculación debe permanecer inactivo");

        assertFalse(creado.isMoodleVerificado());

        assertEquals(MoodleVerificationStatus.SIN_VINCULACION, creado.getMoodleEstadoVerificacion());

        verify(programRepository).guardar(any(Programa.class));

        verifyNoInteractions(lmsClientPort);

    }

    @Test

    @DisplayName("Intentar publicar (activo = true) un programa sin vincular curso de Moodle debe lanzar excepción")

    void crearPrograma_sinMoodleId_activoTrue_debeRechazarPublicacion() {

        CrearProgramaCommand cmd = new CrearProgramaCommand(

                "Liderazgo Estratégico",

                null, null, null, null,

                new BigDecimal("600.00"),

                "USD",

                null, null, null, null, null, 40, "40h",

                true, false, false, null // activo = true sin moodleCourseId

        );

        when(programRepository.buscarPorSlug("liderazgo-estrategico")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> catalogService.crearPrograma(cmd));

        assertTrue(ex.getMessage().contains("No se puede publicar un programa sin vinculación verificada"));

        verify(programRepository, never()).guardar(any(Programa.class));

    }

    @Test

    @DisplayName("Crear programa con curso existente en Moodle debe verificarlo y guardarlo como VERIFICADO")

    void crearPrograma_conMoodleIdExistente_debeVerificarYGuardar() {

        CrearProgramaCommand cmd = new CrearProgramaCommand(

                "Gestión de Riesgos",

                null, null, null, null,

                new BigDecimal("750.00"),

                "USD",

                null, null, null, null, null, 30, null,

                true, true, false, 202L

        );

        when(programRepository.buscarPorSlug("gestion-de-riesgos")).thenReturn(Optional.empty());

        when(programRepository.buscarPorMoodleCourseId(202L)).thenReturn(Optional.empty());

        when(lmsClientPort.verificarCurso(202L)).thenReturn(

                MoodleCourseVerification.verificado(202L, "Gestión de Riesgos Moodle LMS", "RIESGOS-202")

        );

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        Programa creado = catalogService.crearPrograma(cmd);

        assertNotNull(creado);

        assertEquals(202L, creado.getMoodleCourseId());

        assertEquals("Gestión de Riesgos Moodle LMS", creado.getMoodleCourseNombre());

        assertTrue(creado.isMoodleVerificado());

        assertTrue(creado.isActivo());

        assertEquals(MoodleVerificationStatus.VERIFICADO, creado.getMoodleEstadoVerificacion());

    }

    @Test

    @DisplayName("Crear programa con curso que no existe en Moodle intentando activar debe ser rechazado")

    void crearPrograma_conMoodleIdInexistente_activoTrue_debeRechazar() {

        CrearProgramaCommand cmd = new CrearProgramaCommand(

                "Curso Fantasma",

                null, null, null, null,

                new BigDecimal("750.00"),

                "USD",

                null, null, null, null, null, 30, null,

                true, true, false, 9999L

        );

        when(programRepository.buscarPorSlug("curso-fantasma")).thenReturn(Optional.empty());

        when(programRepository.buscarPorMoodleCourseId(9999L)).thenReturn(Optional.empty());

        when(lmsClientPort.verificarCurso(9999L)).thenReturn(

                MoodleCourseVerification.noEncontrado(9999L)

        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> catalogService.crearPrograma(cmd));

        assertTrue(ex.getMessage().contains("no existe"));

        verify(programRepository, never()).guardar(any(Programa.class));

    }

    @Test

    @DisplayName("Crear programa con curso Moodle ya vinculado a otro programa debe ser rechazado por unicidad")

    void crearPrograma_conMoodleIdDuplicado_debeRechazarPorUnicidad() {

        CrearProgramaCommand cmd = new CrearProgramaCommand(

                "Segundo Programa Mismo Curso",

                null, null, null, null,

                new BigDecimal("750.00"),

                "USD",

                null, null, null, null, null, 30, null,

                false, false, false, 101L

        );

        when(programRepository.buscarPorSlug("segundo-programa-mismo-curso")).thenReturn(Optional.empty());

        when(programRepository.buscarPorMoodleCourseId(101L)).thenReturn(Optional.of(programaBase));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> catalogService.crearPrograma(cmd));

        assertTrue(ex.getMessage().contains("ya está vinculado al programa"));

        verify(programRepository, never()).guardar(any(Programa.class));

    }

    @Test

    @DisplayName("Actualizar curso Moodle a uno nuevo invalida verificación previa y ejecuta nueva verificación")

    void actualizarPrograma_cuandoCambiaCursoMoodle_debeReverificar() {

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(programRepository.buscarPorMoodleCourseId(303L)).thenReturn(Optional.empty());

        when(lmsClientPort.verificarCurso(303L)).thenReturn(

                MoodleCourseVerification.verificado(303L, "Nuevo Curso Moodle 303", "NC-303")

        );

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarProgramaCommand cmd = new ActualizarProgramaCommand(

                null, null, null, null, null,

                null, null, null, null, null, null, null, null, null,

                null, null, null, 303L

        );

        Programa actualizado = catalogService.actualizarPrograma(10L, cmd);

        assertEquals(303L, actualizado.getMoodleCourseId());

        assertEquals("Nuevo Curso Moodle 303", actualizado.getMoodleCourseNombre());

        assertTrue(actualizado.isMoodleVerificado());

        assertEquals(MoodleVerificationStatus.VERIFICADO, actualizado.getMoodleEstadoVerificacion());

    }

    @Test

    @DisplayName("Cuando Moodle tiene un timeout temporal para un curso ya verificado, se conserva la verificación previa")

    void verificarYActualizarMoodlePrograma_cuandoMoodleNoDisponible_siYaVerificado_debeConservarVerificacion() {

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(lmsClientPort.verificarCurso(101L)).thenReturn(

                MoodleCourseVerification.noDisponible(101L, "Connection timed out")

        );

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        Programa actualizado = catalogService.verificarYActualizarMoodlePrograma(10L);

        assertTrue(actualizado.isMoodleVerificado(), "No debe borrar verificación previa ante timeout temporal");

        assertEquals("Curso Moodle 101: Gobierno Corporativo", actualizado.getMoodleCourseNombre());

        assertEquals(MoodleVerificationStatus.VERIFICADO, actualizado.getMoodleEstadoVerificacion());

    }

    @Test

    @DisplayName("Desvincular curso Moodle explícitamente vía método dedicado debe limpiar campos y desactivar programa")

    void desvincularCursoMoodle_debeLimpiarCamposDeMoodleYDesactivarPrograma() {

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        Programa desvinculado = catalogService.desvincularCursoMoodle(10L);

        assertNull(desvinculado.getMoodleCourseId());

        assertNull(desvinculado.getMoodleCourseNombre());

        assertFalse(desvinculado.isMoodleVerificado());

        assertNull(desvinculado.getMoodleVerificadoEn());

        assertNotNull(desvinculado.getMoodleUltimoIntentoEn());

        assertFalse(desvinculado.isActivo(), "Al desvincular curso, el programa debe desactivarse automáticamente");

        assertEquals(MoodleVerificationStatus.SIN_VINCULACION, desvinculado.getMoodleEstadoVerificacion());

        verify(programRepository).guardar(any(Programa.class));

    }

    @Test

    @DisplayName("Actualizar programa con señal explícita desvincularMoodle=true debe limpiar campos y desactivar")

    void actualizarPrograma_conDesvincularMoodleTrue_debeLimpiarCamposYDesactivar() {

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarProgramaCommand cmd = new ActualizarProgramaCommand(

                null, null, null, null, null,

                null, null, null, null, null, null, null, null, null,

                null, null, null, null, true // desvincularMoodle = true

        );

        Programa actualizado = catalogService.actualizarPrograma(10L, cmd);

        assertNull(actualizado.getMoodleCourseId());

        assertNull(actualizado.getMoodleCourseNombre());

        assertFalse(actualizado.isMoodleVerificado());

        assertFalse(actualizado.isActivo(), "Al desvincular curso, el programa debe desactivarse");

        assertEquals(MoodleVerificationStatus.SIN_VINCULACION, actualizado.getMoodleEstadoVerificacion());

    }

    @Test

    @DisplayName("Actualizar programa con moodleCourseId menor o igual a cero debe ser rechazado para evitar IDs ficticios")

    void actualizarPrograma_conMoodleIdCero_debeLanzarExcepcion() {

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        ActualizarProgramaCommand cmd = new ActualizarProgramaCommand(

                null, null, null, null, null,

                null, null, null, null, null, null, null, null, null,

                null, null, null, 0L, false

        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,

                () -> catalogService.actualizarPrograma(10L, cmd));

        assertTrue(ex.getMessage().contains("número positivo"), "Debe indicar que el ID de Moodle debe ser positivo");

    }

    @Test

    @DisplayName("Actualizar programa sin enviar moodleCourseId ni desvincularMoodle debe conservar el curso existente")

    void actualizarPrograma_sinEnviarMoodleId_debeConservarCursoExistente() {

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarProgramaCommand cmd = new ActualizarProgramaCommand(

                "Gobierno Corporativo Actualizado", null, null, null, null,

                null, null, null, null, null, null, null, null, null,

                null, null, null, null, false // no cambia moodleCourseId

        );

        Programa actualizado = catalogService.actualizarPrograma(10L, cmd);

        assertEquals(101L, actualizado.getMoodleCourseId(), "Debe conservar el curso existente");

        assertTrue(actualizado.isMoodleVerificado());

        assertEquals("Gobierno Corporativo Actualizado", actualizado.getTitulo());

    }

    @Test

    @DisplayName("Actualizar programa: cuando la verificación expiró, intentar activar revalida en Moodle y si falla rechaza")

    void actualizarPrograma_cuandoVerificacionExpirada_intentarActivar_siMoodleFallaDebeRechazar() {

        programaBase.setMoodleVerificadoEn(OffsetDateTime.ofInstant(NOW.minus(java.time.Duration.ofDays(10)), UTC)); // Expirada (10 días > 7)

        programaBase.setActivo(false); // Estaba inactivo

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(lmsClientPort.verificarCurso(101L)).thenReturn(

                MoodleCourseVerification.noEncontrado(101L)

        );

        ActualizarProgramaCommand cmd = new ActualizarProgramaCommand(

                null, null, null, null, null,

                null, null, null, null, null, null, null, null, null,

                true, null, null, null, false // intentar activar

        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,

                () -> catalogService.actualizarPrograma(10L, cmd));

        assertTrue(ex.getMessage().contains("ha expirado o no pudo ser confirmada"));

    }

    @Test

    @DisplayName("Actualizar programa: cuando la verificación expiró, intentar activar revalida en Moodle y si es exitoso activa")

    void actualizarPrograma_cuandoVerificacionExpirada_intentarActivar_siMoodleValidaDebeActivar() {

        programaBase.setMoodleVerificadoEn(OffsetDateTime.ofInstant(NOW.minus(java.time.Duration.ofDays(10)), UTC)); // Expirada (10 días > 7)

        programaBase.setActivo(false);

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(lmsClientPort.verificarCurso(101L)).thenReturn(

                MoodleCourseVerification.verificado(101L, "Gobierno Corporativo Revalidado", "GC-REV")

        );

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarProgramaCommand cmd = new ActualizarProgramaCommand(

                null, null, null, null, null,

                null, null, null, null, null, null, null, null, null,

                true, null, null, null, false // intentar activar

        );

        Programa actualizado = catalogService.actualizarPrograma(10L, cmd);

        assertTrue(actualizado.isActivo(), "Debe activarse tras revalidación exitosa");

        assertTrue(actualizado.isMoodleVerificado());

        assertEquals("Gobierno Corporativo Revalidado", actualizado.getMoodleCourseNombre());

        assertEquals(MoodleVerificationStatus.VERIFICADO, actualizado.getMoodleEstadoVerificacion());

    }

    @Test

    void actualizarPrograma_cuandoCambiaPrecioSinAprobacionExplicita_debeReiniciarPrecioAprobadoAFalse() {

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarProgramaCommand cmd = new ActualizarProgramaCommand(

                null, null, null, null, null,

                new BigDecimal("1200.00"), // Precio nuevo

                null, null, null, null, null, null, null, null,

                null, null, null, null

        );

        Programa actualizado = catalogService.actualizarPrograma(10L, cmd);

        assertEquals(new BigDecimal("1200.00"), actualizado.getPrecio());

        assertFalse(actualizado.isPrecioAprobado(), "Cambio de precio sin aprobación explícita debe reiniciar precioAprobado a false");

    }

    @Test

    void actualizarPrograma_cuandoCambiaPrecioConAprobacionExplicita_debeConservarAprobacion() {

        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaBase));

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarProgramaCommand cmd = new ActualizarProgramaCommand(

                null, null, null, null, null,

                new BigDecimal("1200.00"),

                null, null, null, null, null, null, null, null,

                null, true, null, null // precioAprobado explícito true

        );

        Programa actualizado = catalogService.actualizarPrograma(10L, cmd);

        assertEquals(new BigDecimal("1200.00"), actualizado.getPrecio());

        assertTrue(actualizado.isPrecioAprobado());

    }

    @Test

    void obtenerProgramaPorSlug_inexistente_debeLanzarResourceNotFoundException() {

        when(programRepository.buscarActivoYAprobadoPorSlug("no-existe")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> catalogService.obtenerProgramaPorSlug("no-existe"));

    }

    @Test

    @DisplayName("Concurrencia: Verificar curso A mientras otro proceso desvincula -> descarta respuesta tardía")

    void concurrencia_verificarA_mientrasOtroDesvincula_debeDescartarRespuesta() {

        OffsetDateTime inicio = OffsetDateTime.ofInstant(NOW, UTC);

        MoodleCourseVerification verifA = MoodleCourseVerification.verificado(101L, "Curso A", "CA");

        // Al consultar con bloqueo en la base de datos, el programa ya fue desvinculado por otro proceso

        Programa desvinculado = Programa.builder()

                .idPrograma(10L)

                .titulo("Gobierno Corporativo")

                .moodleCourseId(null) // Desvinculado concurrentemente

                .activo(false)

                .moodleVerificado(false)

                .moodleEstadoVerificacion(MoodleVerificationStatus.SIN_VINCULACION)

                .build();

        when(programRepository.buscarPorIdConBloqueo(10L)).thenReturn(Optional.of(desvinculado));

        Programa res = catalogService.aplicarResultadoVerificacion(10L, 101L, inicio, verifA);

        assertNotNull(res);

        assertNull(res.getMoodleCourseId());

        assertFalse(res.isActivo());

        assertFalse(res.isMoodleVerificado());

        verify(programRepository, never()).guardar(any());

    }

    @Test

    @DisplayName("Concurrencia: Verificar curso A mientras otro proceso asocia curso B -> descarta respuesta de A")

    void concurrencia_verificarA_mientrasOtroCambiaAB_debeDescartarRespuestaA() {

        OffsetDateTime inicio = OffsetDateTime.ofInstant(NOW, UTC);

        MoodleCourseVerification verifA = MoodleCourseVerification.verificado(101L, "Curso A", "CA");

        // El programa ahora tiene el curso 202L en base de datos

        Programa conCursoB = Programa.builder()

                .idPrograma(10L)

                .titulo("Gobierno Corporativo")

                .moodleCourseId(202L)

                .moodleCourseNombre("Curso B")

                .activo(false)

                .build();

        when(programRepository.buscarPorIdConBloqueo(10L)).thenReturn(Optional.of(conCursoB));

        Programa res = catalogService.aplicarResultadoVerificacion(10L, 101L, inicio, verifA);

        assertNotNull(res);

        assertEquals(202L, res.getMoodleCourseId(), "Debe conservar el curso B y no restaurar el A");

        verify(programRepository, never()).guardar(any());

    }

    @Test

    @DisplayName("Concurrencia: Respuesta de verificación más antigua llega después de una más reciente -> descartar la antigua")

    void concurrencia_respuestaFueraDeOrden_debeDescartarIntentoAntiguo() {

        OffsetDateTime inicioAntiguo = OffsetDateTime.ofInstant(NOW.minus(java.time.Duration.ofMinutes(5)), UTC);

        OffsetDateTime intentoMasReciente = OffsetDateTime.ofInstant(NOW, UTC);

        MoodleCourseVerification verif = MoodleCourseVerification.verificado(101L, "Curso A", "CA");

        Programa programaConIntentoReciente = Programa.builder()

                .idPrograma(10L)

                .moodleCourseId(101L)

                .moodleUltimoIntentoEn(intentoMasReciente) // Más reciente que inicioAntiguo

                .build();

        when(programRepository.buscarPorIdConBloqueo(10L)).thenReturn(Optional.of(programaConIntentoReciente));

        Programa res = catalogService.aplicarResultadoVerificacion(10L, 101L, inicioAntiguo, verif);

        assertNotNull(res);

        verify(programRepository, never()).guardar(any());

    }

    @Test

    @DisplayName("Concurrencia: Modificación de precio durante verificación -> conserva precio nuevo")

    void concurrencia_edicionDePrecioDuranteVerificacion_debePreservarPrecioNuevo() {

        OffsetDateTime inicio = OffsetDateTime.ofInstant(NOW, UTC);

        MoodleCourseVerification verif = MoodleCourseVerification.verificado(101L, "Curso A", "CA");

        Programa programaConPrecioNuevo = Programa.builder()

                .idPrograma(10L)

                .moodleCourseId(101L)

                .precio(new BigDecimal("1500.00")) // Precio actualizado por administrador

                .precioAprobado(true)

                .activo(true)

                .build();

        when(programRepository.buscarPorIdConBloqueo(10L)).thenReturn(Optional.of(programaConPrecioNuevo));

        when(programRepository.guardar(any(Programa.class))).thenAnswer(inv -> inv.getArgument(0));

        Programa res = catalogService.aplicarResultadoVerificacion(10L, 101L, inicio, verif);

        assertNotNull(res);

        assertEquals(new BigDecimal("1500.00"), res.getPrecio(), "El precio nuevo no debe ser sobreescrito");

        assertTrue(res.isPrecioAprobado());

        assertTrue(res.isMoodleVerificado());

        verify(programRepository).guardar(programaConPrecioNuevo);

    }

    @Test

    @DisplayName("Concurrencia: Cambio A->B->A durante consulta a Moodle -> descarta respuesta por cambio de revisión")

    void concurrencia_cambioABA_debeDescartarRespuestaTardiaPorRevisionDiferente() {

        OffsetDateTime inicio = OffsetDateTime.ofInstant(NOW, UTC);

        MoodleCourseVerification verif = MoodleCourseVerification.verificado(101L, "Curso A", "CA");

        Programa programaABA = Programa.builder()

                .idPrograma(10L)

                .moodleCourseId(101L)

                .moodleVinculacionRevision(3L)

                .activo(false)

                .build();

        when(programRepository.buscarPorIdConBloqueo(10L)).thenReturn(Optional.of(programaABA));

        Programa res = catalogService.aplicarResultadoVerificacionTransaccional(10L, 101L, 1L, inicio, verif);

        assertNotNull(res);

        verify(programRepository, never()).guardar(any());

        assertEquals(3L, res.getMoodleVinculacionRevision());

    }



    @Test
    @DisplayName("Gobernanza transaccional: El contexto no puede crear el servicio si falta la dependencia transaccional")
    void constructor_fallaSiFaltaDependenciaTransaccional() {
        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                new CatalogService(programRepository, lmsClientPort, clock, 168L, null));
        assertTrue(ex.getMessage().contains("transactionTemplate es obligatorio para garantizar transacciones reales"));
    }

    @Test
    @DisplayName("Gobernanza transaccional: Constructor exige repositorios obligatorios")
    void constructor_exigeDependenciasObligatorias() {
        assertThrows(NullPointerException.class, () ->
                new CatalogService(null, lmsClientPort, clock, 168L, testTransactionTemplate));
        assertThrows(NullPointerException.class, () ->
                new CatalogService(programRepository, null, clock, 168L, testTransactionTemplate));
    }
}
package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ActualizarProgramaRequest;
import com.lysandri.adapters.in.web.dto.CrearProgramaRequest;
import com.lysandri.domain.model.MoodleCourseVerification;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.in.CatalogUseCase;
import com.lysandri.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias: CatalogController con Endpoints de Moodle")
class CatalogControllerTest {

    @Mock
    private CatalogUseCase catalogUseCase;

    @InjectMocks
    private CatalogController catalogController;

    private Programa progActivo;

    @BeforeEach
    void setUp() {
        progActivo = Programa.builder()
                .idPrograma(1L)
                .moodleCourseId(101L)
                .moodleCourseNombre("FinOps Empresarial Moodle")
                .moodleVerificado(true)
                .titulo("FinOps Empresarial")
                .slug("finops-empresarial")
                .precio(new BigDecimal("799.00"))
                .moneda("USD")
                .activo(true)
                .precioAprobado(true)
                .build();
    }

    @Test
    void listarProgramas_debeRetornarSoloActivosYAprobados() {
        when(catalogUseCase.listarProgramasActivos()).thenReturn(List.of(progActivo));

        ResponseEntity<List<Programa>> res = catalogController.listarProgramas();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().size());
        assertEquals("finops-empresarial", res.getBody().get(0).getSlug());
        verify(catalogUseCase).listarProgramasActivos();
    }

    @Test
    void listarInventarioMoodle_debeRetornarInventarioCompleto() {
        when(catalogUseCase.listarInventarioMoodle()).thenReturn(List.of(progActivo));

        ResponseEntity<List<Programa>> res = catalogController.listarInventarioMoodle();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().size());
        verify(catalogUseCase).listarInventarioMoodle();
    }

    @Test
    void verificarCursoMoodle_debeRetornarResultadoDeVerificacion() {
        MoodleCourseVerification verif = MoodleCourseVerification.verificado(101L, "Curso 101", "C-101");
        when(catalogUseCase.verificarCursoMoodle(101L)).thenReturn(verif);

        ResponseEntity<MoodleCourseVerification> res = catalogController.verificarCursoMoodle(101L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(101L, res.getBody().moodleCourseId());
        assertEquals("Curso 101", res.getBody().courseFullName());
        verify(catalogUseCase).verificarCursoMoodle(101L);
    }

    @Test
    void verificarYActualizarMoodlePrograma_debeRetornarProgramaActualizado() {
        when(catalogUseCase.verificarYActualizarMoodlePrograma(1L)).thenReturn(progActivo);

        ResponseEntity<Programa> res = catalogController.verificarYActualizarMoodlePrograma(1L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().isMoodleVerificado());
        verify(catalogUseCase).verificarYActualizarMoodlePrograma(1L);
    }

    @Test
    void obtenerPorSlug_cuandoExisteYAprobado_debeRetornarOk() {
        when(catalogUseCase.obtenerProgramaPorSlug("finops-empresarial")).thenReturn(progActivo);

        ResponseEntity<Programa> res = catalogController.obtenerPorSlug("finops-empresarial");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1L, res.getBody().getIdPrograma());
    }

    @Test
    void obtenerPorSlug_cuandoEsNumerico_debeIntentarPorId() {
        when(catalogUseCase.obtenerProgramaPorId(1L)).thenReturn(progActivo);

        ResponseEntity<Programa> res = catalogController.obtenerPorSlug("1");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1L, res.getBody().getIdPrograma());
        verify(catalogUseCase).obtenerProgramaPorId(1L);
    }

    @Test
    void obtenerPorSlug_cuandoInactivoOInexistente_debeLanzarResourceNotFound() {
        when(catalogUseCase.obtenerProgramaPorSlug("curso-inactivo"))
                .thenThrow(new ResourceNotFoundException("Programa no encontrado o no disponible para el slug: curso-inactivo"));

        assertThrows(ResourceNotFoundException.class, () -> catalogController.obtenerPorSlug("curso-inactivo"));
    }

    @Test
    void obtenerPorId_cuandoExisteYAprobado_debeRetornarOk() {
        when(catalogUseCase.obtenerProgramaPorId(1L)).thenReturn(progActivo);

        ResponseEntity<Programa> res = catalogController.obtenerPorId(1L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals("finops-empresarial", res.getBody().getSlug());
    }

    @Test
    void obtenerPorId_cuandoInactivoOInexistente_debeLanzarResourceNotFound() {
        when(catalogUseCase.obtenerProgramaPorId(999L))
                .thenThrow(new ResourceNotFoundException("Programa no encontrado o no disponible con ID: 999"));

        assertThrows(ResourceNotFoundException.class, () -> catalogController.obtenerPorId(999L));
    }

    @Test
    void crearPrograma_debeRetornar201Created() {
        CrearProgramaRequest request = CrearProgramaRequest.builder()
                .titulo("FinOps Empresarial")
                .precio(new BigDecimal("799.00"))
                .moneda("USD")
                .build();

        when(catalogUseCase.crearPrograma(any(CatalogUseCase.CrearProgramaCommand.class))).thenReturn(progActivo);

        ResponseEntity<Programa> res = catalogController.crearPrograma(request);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1L, res.getBody().getIdPrograma());
        verify(catalogUseCase).crearPrograma(any(CatalogUseCase.CrearProgramaCommand.class));
    }

    @Test
    void actualizarPrograma_debeRetornar200Ok() {
        ActualizarProgramaRequest request = ActualizarProgramaRequest.builder()
                .titulo("FinOps Empresarial Avanzado")
                .precio(new BigDecimal("899.00"))
                .build();

        when(catalogUseCase.actualizarPrograma(eq(1L), any(CatalogUseCase.ActualizarProgramaCommand.class))).thenReturn(progActivo);

        ResponseEntity<Programa> res = catalogController.actualizarPrograma(1L, request);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        verify(catalogUseCase).actualizarPrograma(eq(1L), any(CatalogUseCase.ActualizarProgramaCommand.class));
    }

    @Test
    void desactivarPrograma_debeRetornar204NoContent() {
        doNothing().when(catalogUseCase).desactivarPrograma(1L);

        ResponseEntity<Void> res = catalogController.desactivarPrograma(1L);

        assertEquals(HttpStatus.NO_CONTENT, res.getStatusCode());
        verify(catalogUseCase).desactivarPrograma(1L);
    }

    @Test
    void desvincularCursoMoodle_debeRetornarProgramaDesvinculado() {
        Programa progDesvinculado = Programa.builder()
                .idPrograma(1L)
                .moodleCourseId(null)
                .moodleCourseNombre(null)
                .moodleVerificado(false)
                .moodleEstadoVerificacion(com.lysandri.domain.model.MoodleVerificationStatus.SIN_VINCULACION)
                .activo(false)
                .build();
        when(catalogUseCase.desvincularCursoMoodle(1L)).thenReturn(progDesvinculado);

        ResponseEntity<Programa> res = catalogController.desvincularCursoMoodle(1L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertNull(res.getBody().getMoodleCourseId());
        assertFalse(res.getBody().isActivo());
        verify(catalogUseCase).desvincularCursoMoodle(1L);
    }
}

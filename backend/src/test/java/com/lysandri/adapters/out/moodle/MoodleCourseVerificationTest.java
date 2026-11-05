package com.lysandri.adapters.out.moodle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.model.MoodleCourseVerification;
import com.lysandri.domain.model.MoodleVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias: MoodleServiceAdapter - Verificación de Cursos con Moodle API")
class MoodleCourseVerificationTest {

    private MoodleServiceAdapter moodleServiceAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        moodleServiceAdapter = spy(new MoodleServiceAdapter(
                "http://localhost:8080/moodle",
                "dummy-test-token",
                WebClient.builder(),
                objectMapper
        ));
    }

    @Test
    @DisplayName("Cuando ID es nulo o menor o igual a cero, retorna SIN_VINCULACION sin llamar a la API")
    void verificarCurso_conIdNuloOMenorIgualCero_retornaSinVinculacion() {
        MoodleCourseVerification resNull = moodleServiceAdapter.verificarCurso(null);
        assertEquals(MoodleVerificationStatus.SIN_VINCULACION, resNull.status());
        assertNull(resNull.moodleCourseId());

        MoodleCourseVerification resZero = moodleServiceAdapter.verificarCurso(0L);
        assertEquals(MoodleVerificationStatus.SIN_VINCULACION, resZero.status());

        MoodleCourseVerification resNeg = moodleServiceAdapter.verificarCurso(-5L);
        assertEquals(MoodleVerificationStatus.SIN_VINCULACION, resNeg.status());

        verify(moodleServiceAdapter, never()).executeMoodlePost(any());
    }

    @Test
    @DisplayName("Cuando Moodle devuelve curso existente en 'courses', retorna VERIFICADO con metadatos")
    void verificarCurso_cuandoExisteEnMoodle_retornaVerificado() {
        String jsonRespuesta = """
                {
                    "courses": [
                        {
                            "id": 15,
                            "fullname": "Master Executive en Ciberseguridad",
                            "displayname": "Master Executive en Ciberseguridad",
                            "shortname": "MEC-2026"
                        }
                    ],
                    "warnings": []
                }
                """;

        doReturn(jsonRespuesta).when(moodleServiceAdapter).executeMoodlePost(any());

        MoodleCourseVerification res = moodleServiceAdapter.verificarCurso(15L);

        assertNotNull(res);
        assertEquals(MoodleVerificationStatus.VERIFICADO, res.status());
        assertEquals(15L, res.moodleCourseId());
        assertEquals("Master Executive en Ciberseguridad", res.courseFullName());
        assertEquals("MEC-2026", res.courseShortName());
        assertNotNull(res.verificadoEn());
    }

    @Test
    @DisplayName("Cuando Moodle devuelve lista vacía en 'courses', retorna NO_ENCONTRADO")
    void verificarCurso_cuandoNoExisteEnMoodle_retornaNoEncontrado() {
        String jsonRespuesta = """
                {
                    "courses": [],
                    "warnings": []
                }
                """;

        doReturn(jsonRespuesta).when(moodleServiceAdapter).executeMoodlePost(any());

        MoodleCourseVerification res = moodleServiceAdapter.verificarCurso(999L);

        assertNotNull(res);
        assertEquals(MoodleVerificationStatus.NO_ENCONTRADO, res.status());
        assertEquals(999L, res.moodleCourseId());
        assertNull(res.courseFullName());
    }

    @Test
    @DisplayName("Cuando Moodle devuelve exception o errorcode (ej. token inválido), retorna NO_DISPONIBLE")
    void verificarCurso_cuandoMoodleDevuelveExcepcion_retornaNoDisponible() {
        String jsonRespuesta = """
                {
                    "exception": "moodle_exception",
                    "errorcode": "invalidtoken",
                    "message": "Token de servicio web no válido o expirado"
                }
                """;

        doReturn(jsonRespuesta).when(moodleServiceAdapter).executeMoodlePost(any());

        MoodleCourseVerification res = moodleServiceAdapter.verificarCurso(15L);

        assertNotNull(res);
        assertEquals(MoodleVerificationStatus.NO_DISPONIBLE, res.status());
        assertEquals(15L, res.moodleCourseId());
        assertTrue(res.detalle().contains("invalidtoken"));
    }

    @Test
    @DisplayName("Cuando ocurre timeout o falla de red hacia Moodle, retorna NO_DISPONIBLE sanitizado")
    void verificarCurso_cuandoFallaRedOTimeout_retornaNoDisponibleSanitizado() {
        doThrow(new RuntimeException("Connection timed out to http://localhost:8080/moodle with wstoken=secret-tok-123"))
                .when(moodleServiceAdapter).executeMoodlePost(any());

        MoodleCourseVerification res = moodleServiceAdapter.verificarCurso(15L);

        assertNotNull(res);
        assertEquals(MoodleVerificationStatus.NO_DISPONIBLE, res.status());
        assertEquals(15L, res.moodleCourseId());
        assertTrue(res.detalle().contains("Verificación no disponible"));
        assertFalse(res.detalle().contains("secret-tok-123"), "El mensaje no debe exponer tokens ni secretos");
    }
}


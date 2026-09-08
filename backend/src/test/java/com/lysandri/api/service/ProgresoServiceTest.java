package com.lysandri.api.service;

import com.lysandri.api.dto.response.ProgresoPorcentajeResponse;
import com.lysandri.api.dto.response.ProgresoResponse;
import com.lysandri.api.model.entity.Leccion;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.model.entity.ProgresoLeccion;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.model.enums.Rol;
import com.lysandri.api.repository.InscripcionRepository;
import com.lysandri.api.repository.LeccionRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.repository.ProgresoLeccionRepository;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.service.impl.ProgresoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgresoServiceTest {

    @Mock
    private ProgresoLeccionRepository progresoLeccionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private LeccionRepository leccionRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private InscripcionRepository inscripcionRepository;

    @InjectMocks
    private ProgresoServiceImpl progresoService;

    private Usuario estudiante;
    private Programa programa;
    private Leccion leccion;
    private ProgresoLeccion progreso;

    @BeforeEach
    void setUp() {
        estudiante = Usuario.builder()
                .idUser(7)
                .nombres("Ana")
                .apellidos("Gómez")
                .rol(Rol.ESTUDIANTE)
                .build();

        programa = Programa.builder()
                .idPrograma(3)
                .tituloPrograma("Marketing Digital")
                .build();

        leccion = Leccion.builder()
                .idLeccion(25)
                .programa(programa)
                .tituloLeccion("SEO Avanzado")
                .build();

        progreso = ProgresoLeccion.builder()
                .idProgreso(50)
                .usuario(estudiante)
                .leccion(leccion)
                .completado(true)
                .fechaCompletado(OffsetDateTime.now())
                .build();
    }

    @Test
    void marcarLeccionCompletada_Exito() {
        when(usuarioRepository.findById(7)).thenReturn(Optional.of(estudiante));
        when(leccionRepository.findById(25)).thenReturn(Optional.of(leccion));
        when(progresoLeccionRepository.findByUsuarioIdAndLeccionId(7, 25)).thenReturn(Optional.empty());
        when(progresoLeccionRepository.save(any(ProgresoLeccion.class))).thenReturn(progreso);
        when(inscripcionRepository.findByUsuarioIdUserAndProgramaIdPrograma(7, 3)).thenReturn(Optional.empty());

        ProgresoResponse response = progresoService.marcarLeccionCompletada(7L, 25L);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals(7L, response.getUsuarioId());
        assertEquals(25L, response.getLeccionId());
        assertTrue(response.getCompletado());
        verify(progresoLeccionRepository).save(any(ProgresoLeccion.class));
    }

    @Test
    void obtenerPorcentajeAvance_Exito() {
        when(usuarioRepository.existsById(7)).thenReturn(true);
        when(programaRepository.existsById(3)).thenReturn(true);
        when(leccionRepository.countByProgramaIdPrograma(3)).thenReturn(4L);
        when(progresoLeccionRepository.countLeccionesCompletadas(7, 3)).thenReturn(2L);

        ProgresoPorcentajeResponse response = progresoService.obtenerPorcentajeAvance(7L, 3L);

        assertNotNull(response);
        assertEquals(3L, response.getProgramaId());
        assertEquals(4L, response.getTotalLecciones());
        assertEquals(2L, response.getLeccionesCompletadas());
        assertEquals(50.0, response.getPorcentaje());
    }

    @Test
    void obtenerPorcentajeAvance_CeroLecciones_RetornaCero() {
        when(usuarioRepository.existsById(7)).thenReturn(true);
        when(programaRepository.existsById(3)).thenReturn(true);
        when(leccionRepository.countByProgramaIdPrograma(3)).thenReturn(0L);
        when(progresoLeccionRepository.countLeccionesCompletadas(7, 3)).thenReturn(0L);

        ProgresoPorcentajeResponse response = progresoService.obtenerPorcentajeAvance(7L, 3L);

        assertNotNull(response);
        assertEquals(0.0, response.getPorcentaje());
    }
}

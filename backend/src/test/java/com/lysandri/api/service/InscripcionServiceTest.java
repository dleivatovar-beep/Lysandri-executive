package com.lysandri.api.service;

import com.lysandri.api.dto.response.InscripcionResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Inscripcion;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.model.enums.Rol;
import com.lysandri.api.repository.InscripcionRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.repository.UsuarioRepository;
import com.lysandri.api.service.impl.InscripcionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InscripcionServiceTest {

    @Mock
    private InscripcionRepository inscripcionRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @InjectMocks
    private InscripcionServiceImpl inscripcionService;

    private Usuario estudiante;
    private Programa programa;
    private Inscripcion inscripcion;

    @BeforeEach
    void setUp() {
        estudiante = Usuario.builder()
                .idUser(5)
                .nombres("Juan")
                .apellidos("Pérez")
                .email("juan@test.com")
                .rol(Rol.ESTUDIANTE)
                .build();

        programa = Programa.builder()
                .idPrograma(2)
                .tituloPrograma("Finanzas Corporativas")
                .build();

        inscripcion = Inscripcion.builder()
                .idInscripcion(100)
                .usuario(estudiante)
                .programa(programa)
                .fechaInscripcion(OffsetDateTime.now())
                .estatus("ACTIVO")
                .build();
    }

    @Test
    void inscribirEstudiante_Exito() {
        when(usuarioRepository.findById(5)).thenReturn(Optional.of(estudiante));
        when(programaRepository.findById(2)).thenReturn(Optional.of(programa));
        when(inscripcionRepository.existsByUsuarioIdAndProgramaId(5, 2)).thenReturn(false);
        when(inscripcionRepository.save(any(Inscripcion.class))).thenReturn(inscripcion);

        InscripcionResponse response = inscripcionService.inscribirEstudiante(5L, 2L);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(5L, response.getUsuarioId());
        assertEquals("Juan Pérez", response.getNombreEstudiante());
        assertEquals("ACTIVO", response.getEstado());
    }

    @Test
    void inscribirEstudiante_YaInscrito_LanzaBadRequestException() {
        when(usuarioRepository.findById(5)).thenReturn(Optional.of(estudiante));
        when(programaRepository.findById(2)).thenReturn(Optional.of(programa));
        when(inscripcionRepository.existsByUsuarioIdAndProgramaId(5, 2)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> inscripcionService.inscribirEstudiante(5L, 2L));
    }

    @Test
    void listarCursosDeEstudiante_Exito() {
        when(usuarioRepository.existsById(5)).thenReturn(true);
        when(inscripcionRepository.findByUsuarioId(5)).thenReturn(List.of(inscripcion));

        List<InscripcionResponse> lista = inscripcionService.listarCursosDeEstudiante(5L);

        assertEquals(1, lista.size());
        assertEquals("Finanzas Corporativas", lista.get(0).getTituloPrograma());
    }
}

package com.lysandri.api.service;

import com.lysandri.api.dto.request.LeccionRequest;
import com.lysandri.api.dto.response.LeccionResponse;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Leccion;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.repository.LeccionRepository;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.service.impl.LeccionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeccionServiceTest {

    @Mock
    private LeccionRepository leccionRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @Mock
    private AccesoAcademicoService accesoAcademicoService;

    @InjectMocks
    private LeccionServiceImpl leccionService;

    private Programa programa;
    private Leccion leccion;

    @BeforeEach
    void setUp() {
        programa = Programa.builder()
                .idPrograma(1)
                .tituloPrograma("Estrategia Ejecutiva")
                .build();

        leccion = Leccion.builder()
                .idLeccion(10)
                .programa(programa)
                .tituloLeccion("Introducción")
                .descripcion("Primera lección")
                .mediaUrl("https://media.com/1")
                .duracionLeccion("30 min")
                .orden(1)
                .build();
    }

    @Test
    void crearLeccion_Exito() {
        LeccionRequest request = LeccionRequest.builder()
                .titulo("Introducción")
                .descripcion("Primera lección")
                .contenidoUrl("https://media.com/1")
                .duracionMinutos(30)
                .orden(1)
                .build();

        when(programaRepository.findById(1)).thenReturn(Optional.of(programa));
        doNothing().when(accesoAcademicoService).validarGestionPrograma(1);
        when(leccionRepository.save(any(Leccion.class))).thenReturn(leccion);

        LeccionResponse response = leccionService.crearLeccion(1L, request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("Introducción", response.getTitulo());
        assertEquals(30, response.getDuracionMinutos());
        verify(programaRepository).findById(1);
        verify(accesoAcademicoService).validarGestionPrograma(1);
        verify(leccionRepository).save(any(Leccion.class));
    }

    @Test
    void crearLeccion_ProgramaNoExiste_LanzaResourceNotFound() {
        LeccionRequest request = LeccionRequest.builder().titulo("Test").orden(1).build();
        when(programaRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> leccionService.crearLeccion(99L, request));
    }

    @Test
    void listarPorPrograma_Exito() {
        when(programaRepository.findById(1)).thenReturn(Optional.of(programa));
        when(leccionRepository.findByProgramaIdOrderByOrdenAsc(1)).thenReturn(List.of(leccion));

        List<LeccionResponse> lista = leccionService.listarPorPrograma(1L);

        assertEquals(1, lista.size());
        assertEquals(10L, lista.get(0).getId());
    }

    @Test
    void eliminarLeccion_Exito() {
        when(leccionRepository.findById(10)).thenReturn(Optional.of(leccion));
        doNothing().when(accesoAcademicoService).validarGestionPrograma(1);
        doNothing().when(leccionRepository).delete(leccion);

        assertDoesNotThrow(() -> leccionService.eliminarLeccion(10L));
        verify(leccionRepository).findById(10);
        verify(leccionRepository).delete(leccion);
    }
}

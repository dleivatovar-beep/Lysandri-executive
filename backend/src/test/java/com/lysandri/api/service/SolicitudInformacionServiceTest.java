package com.lysandri.api.service;

import com.lysandri.api.dto.request.SolicitudInformacionRequest;
import com.lysandri.api.dto.response.SolicitudInformacionResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.exception.ResourceNotFoundException;
import com.lysandri.api.model.entity.Programa;
import com.lysandri.api.model.entity.SolicitudInformacion;
import com.lysandri.api.repository.ProgramaRepository;
import com.lysandri.api.repository.SolicitudInformacionRepository;
import com.lysandri.api.service.impl.SolicitudInformacionServiceImpl;
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
class SolicitudInformacionServiceTest {

    @Mock
    private SolicitudInformacionRepository solicitudRepository;

    @Mock
    private ProgramaRepository programaRepository;

    @InjectMocks
    private SolicitudInformacionServiceImpl solicitudService;

    private Programa programa;
    private SolicitudInformacion solicitud;

    @BeforeEach
    void setUp() {
        programa = Programa.builder()
                .idPrograma(1)
                .tituloPrograma("Liderazgo Ejecutivo")
                .build();

        solicitud = SolicitudInformacion.builder()
                .idSolicitud(10)
                .nombreCompleto("María García")
                .email("maria@empresa.com")
                .telefono("+51 987654321")
                .programa(programa)
                .mensaje("Deseo más información")
                .estado("PENDIENTE")
                .fechaCreacion(OffsetDateTime.now())
                .build();
    }

    @Test
    void registrarSolicitud_Exitoso() {
        SolicitudInformacionRequest request = SolicitudInformacionRequest.builder()
                .nombreCompleto(" María García ")
                .email(" MARIA@EMPRESA.COM ")
                .telefono(" +51 987654321 ")
                .programaId(1)
                .mensaje(" Deseo más información ")
                .build();

        when(programaRepository.findById(1)).thenReturn(Optional.of(programa));
        when(solicitudRepository.save(any(SolicitudInformacion.class))).thenReturn(solicitud);

        SolicitudInformacionResponse response = solicitudService.registrarSolicitud(request);

        assertNotNull(response);
        assertEquals(10, response.getIdSolicitud());
        assertEquals("María García", response.getNombreCompleto());
        assertEquals("maria@empresa.com", response.getEmail());
        assertEquals("+51 987654321", response.getTelefono());
        assertEquals(1, response.getProgramaId());
        assertEquals("Liderazgo Ejecutivo", response.getTituloPrograma());
        assertEquals("PENDIENTE", response.getEstado());
        assertNotNull(response.getFechaSolicitud());
        verify(solicitudRepository, times(1)).save(any(SolicitudInformacion.class));
    }

    @Test
    void registrarSolicitud_SinPrograma_Exitoso() {
        SolicitudInformacionRequest request = SolicitudInformacionRequest.builder()
                .nombreCompleto("Carlos Ruiz")
                .email("carlos@empresa.com")
                .telefono("")
                .build();

        SolicitudInformacion solicitudSinPrograma = SolicitudInformacion.builder()
                .idSolicitud(11)
                .nombreCompleto("Carlos Ruiz")
                .email("carlos@empresa.com")
                .estado("PENDIENTE")
                .fechaCreacion(OffsetDateTime.now())
                .build();

        when(solicitudRepository.save(any(SolicitudInformacion.class))).thenReturn(solicitudSinPrograma);

        SolicitudInformacionResponse response = solicitudService.registrarSolicitud(request);

        assertNotNull(response);
        assertNull(response.getProgramaId());
        assertNull(response.getTelefono());
        verify(programaRepository, never()).findById(any());
        verify(solicitudRepository, times(1)).save(any(SolicitudInformacion.class));
    }

    @Test
    void registrarSolicitud_ProgramaNoExiste_LanzaException() {
        SolicitudInformacionRequest request = SolicitudInformacionRequest.builder()
                .nombreCompleto("Test")
                .email("test@test.com")
                .programaId(999)
                .build();

        when(programaRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> solicitudService.registrarSolicitud(request));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void listarTodas_RetornaLista() {
        when(solicitudRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of(solicitud));

        List<SolicitudInformacionResponse> responses = solicitudService.listarTodas();

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("María García", responses.get(0).getNombreCompleto());
        verify(solicitudRepository, times(1)).findAllByOrderByFechaCreacionDesc();
    }

    @Test
    void listarPorEstado_RetornaFiltrado() {
        when(solicitudRepository.findByEstadoOrderByFechaCreacionDesc("PENDIENTE")).thenReturn(List.of(solicitud));

        List<SolicitudInformacionResponse> responses = solicitudService.listarPorEstado("pendiente");

        assertEquals(1, responses.size());
        verify(solicitudRepository, times(1)).findByEstadoOrderByFechaCreacionDesc("PENDIENTE");
    }

    @Test
    void actualizarEstado_Exitoso() {
        when(solicitudRepository.findById(10)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any(SolicitudInformacion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitudInformacionResponse response = solicitudService.actualizarEstado(10, "CONTACTADA", "Se le llamó por teléfono");

        assertNotNull(response);
        assertEquals("CONTACTADA", response.getEstado());
        assertEquals("Se le llamó por teléfono", response.getNotasAdmin());
        assertNotNull(response.getFechaAtencion());
        verify(solicitudRepository, times(1)).save(solicitud);
    }

    @Test
    void actualizarEstado_EstadoInvalido_LanzaBadRequest() {
        assertThrows(BadRequestException.class, () -> solicitudService.actualizarEstado(10, "ESTADO_INEXISTENTE", null));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void eliminarSolicitud_Exitoso() {
        when(solicitudRepository.findById(10)).thenReturn(Optional.of(solicitud));

        solicitudService.eliminarSolicitud(10);

        verify(solicitudRepository, times(1)).delete(solicitud);
    }
}

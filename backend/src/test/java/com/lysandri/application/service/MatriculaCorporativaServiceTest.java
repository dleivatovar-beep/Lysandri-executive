package com.lysandri.application.service;

import com.lysandri.domain.model.Programa;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.MatriculaCorporativaUseCase;
import com.lysandri.domain.ports.in.SolicitudInformacionUseCase;
import com.lysandri.domain.ports.out.LmsClientPort;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatriculaCorporativaServiceTest {

    @Mock
    private ProgramRepositoryPort programRepository;

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private LmsClientPort lmsClientPort;

    @Mock
    private NotificationPort notificationPort;

    @Mock
    private SolicitudInformacionUseCase solicitudInformacionUseCase;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MatriculaCorporativaService matriculaCorporativaService;

    private Programa programaMock;

    @BeforeEach
    void setUp() {
        programaMock = Programa.builder()
                .idPrograma(10L)
                .titulo("IA para Directores")
                .precio(new BigDecimal("1200.00"))
                .moodleCourseId(202L)
                .build();
    }

    @Test
    void matricularNomina_exitosoParaMultiplesColaboradores() {
        when(programRepository.buscarPorId(10L)).thenReturn(Optional.of(programaMock));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedPassword");

        // Colaborador 1: nuevo en DB y nuevo en Moodle
        when(userRepository.buscarPorEmail("c1@banco.com")).thenReturn(Optional.empty());
        when(userRepository.guardar(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setIdUser(100L);
            return u;
        });
        when(lmsClientPort.buscarUsuarioPorEmail("c1@banco.com")).thenReturn(Optional.empty());
        when(lmsClientPort.crearUsuario(eq("c1"), anyString(), eq("Carlos"), eq("Mendoza"), eq("c1@banco.com")))
                .thenReturn(701L);

        // Colaborador 2: ya existente en DB con moodleUserId
        Usuario usuarioExistente = Usuario.builder()
                .idUser(101L)
                .nombres("Ana")
                .apellidos("García")
                .email("ana@banco.com")
                .moodleUserId(702L)
                .rol(RolUsuario.CLIENTE)
                .build();
        when(userRepository.buscarPorEmail("ana@banco.com")).thenReturn(Optional.of(usuarioExistente));

        MatriculaCorporativaUseCase.MatricularNominaCommand command = new MatriculaCorporativaUseCase.MatricularNominaCommand(
                10L,
                55L,
                "Banco Continental",
                List.of(
                        new MatriculaCorporativaUseCase.ColaboradorItem("Carlos", "Mendoza", "c1@banco.com", "Jefe TI", "999111222"),
                        new MatriculaCorporativaUseCase.ColaboradorItem("Ana", "García", "ana@banco.com", "Analista", "999333444")
                ),
                true,
                "Lote inicial de 2 participantes"
        );

        MatriculaCorporativaUseCase.MatriculaNominaResponse respuesta = matriculaCorporativaService.matricularNomina(command);

        assertNotNull(respuesta);
        assertEquals(2, respuesta.totalProcesados());
        assertEquals(2, respuesta.totalExitosos());
        assertEquals(0, respuesta.totalFallidos());
        assertEquals("Banco Continental", respuesta.empresaNombre());
        assertEquals("IA para Directores", respuesta.programaTitulo());

        // Verificar matrículas en Moodle
        verify(lmsClientPort).matricularUsuarioEnCurso(eq(701L), eq(202L), eq(5));
        verify(lmsClientPort).matricularUsuarioEnCurso(eq(702L), eq(202L), eq(5));

        // Verificar actualización de solicitud In-Company
        verify(solicitudInformacionUseCase).actualizarEstado(eq(55L), eq("CERRADA"));
    }

    @Test
    void matricularNomina_errorSiProgramaNoExiste() {
        when(programRepository.buscarPorId(999L)).thenReturn(Optional.empty());

        MatriculaCorporativaUseCase.MatricularNominaCommand command = new MatriculaCorporativaUseCase.MatricularNominaCommand(
                999L,
                "Empresa X",
                List.of(new MatriculaCorporativaUseCase.ColaboradorItem("Juan", "Pérez", "juan@x.com", null, null))
        );

        assertThrows(IllegalArgumentException.class, () -> matriculaCorporativaService.matricularNomina(command));
    }
}

package com.lysandri.application.service;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserManagementService userManagementService;

    private Usuario usuarioMock;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .idUser(1L)
                .nombres("Carlos")
                .apellidos("Vargas")
                .email("carlos@lysandri.com")
                .passw("encoded123")
                .rol(RolUsuario.ESTUDIANTE)
                .activo(true)
                .build();
    }

    @Test
    void crearUsuario_exitoso() {
        Usuario nuevo = Usuario.builder()
                .nombres("Carlos")
                .apellidos("Vargas")
                .email("carlos@lysandri.com")
                .passw("RawPass123!")
                .rol(RolUsuario.ESTUDIANTE)
                .build();

        when(userRepositoryPort.existePorEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("RawPass123!")).thenReturn("encoded123");
        when(userRepositoryPort.guardar(any(Usuario.class))).thenReturn(usuarioMock);

        Usuario resultado = userManagementService.crearUsuario(nuevo);

        assertNotNull(resultado);
        assertEquals("carlos@lysandri.com", resultado.getEmail());
        assertEquals("encoded123", resultado.getPassw());
        verify(userRepositoryPort, times(1)).guardar(any(Usuario.class));
    }

    @Test
    void crearUsuario_emailDuplicado_lanzaExcepcion() {
        Usuario nuevo = Usuario.builder()
                .nombres("Carlos")
                .apellidos("Vargas")
                .email("carlos@lysandri.com")
                .build();

        when(userRepositoryPort.existePorEmail("carlos@lysandri.com")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> userManagementService.crearUsuario(nuevo));
        verify(userRepositoryPort, never()).guardar(any(Usuario.class));
    }

    @Test
    void listarUsuarios_todos() {
        when(userRepositoryPort.listarTodos()).thenReturn(List.of(usuarioMock));

        List<Usuario> lista = userManagementService.listarUsuarios(null, null);

        assertEquals(1, lista.size());
        assertEquals("Carlos", lista.get(0).getNombres());
        verify(userRepositoryPort, times(1)).listarTodos();
    }

    @Test
    void listarUsuarios_porRol() {
        when(userRepositoryPort.listarPorRol(RolUsuario.ESTUDIANTE)).thenReturn(List.of(usuarioMock));

        List<Usuario> lista = userManagementService.listarUsuarios(null, RolUsuario.ESTUDIANTE);

        assertEquals(1, lista.size());
        assertEquals(RolUsuario.ESTUDIANTE, lista.get(0).getRol());
        verify(userRepositoryPort, times(1)).listarPorRol(RolUsuario.ESTUDIANTE);
    }

    @Test
    void actualizarUsuario_exitoso() {
        Usuario datosNuevos = Usuario.builder()
                .nombres("Carlos Alberto")
                .build();

        when(userRepositoryPort.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        when(userRepositoryPort.guardar(any(Usuario.class))).thenReturn(usuarioMock);

        Usuario actualizado = userManagementService.actualizarUsuario(1L, datosNuevos);

        assertNotNull(actualizado);
        verify(userRepositoryPort, times(1)).guardar(any(Usuario.class));
    }

    @Test
    void eliminarUsuario_exitoso() {
        when(userRepositoryPort.buscarPorId(1L)).thenReturn(Optional.of(usuarioMock));
        doNothing().when(userRepositoryPort).eliminar(1L);

        assertDoesNotThrow(() -> userManagementService.eliminarUsuario(1L));
        verify(userRepositoryPort, times(1)).eliminar(1L);
    }
}

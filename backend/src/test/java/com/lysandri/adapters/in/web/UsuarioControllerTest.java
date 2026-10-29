package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.UsuarioResponseDto;
import com.lysandri.adapters.in.web.dto.UsuarioUpdateRequest;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock
    private UserManagementUseCase userManagementUseCase;

    @InjectMocks
    private UsuarioController usuarioController;

    private Usuario usuarioCliente;
    private Authentication clienteAuth;
    private Authentication adminAuth;

    @BeforeEach
    void setUp() {
        usuarioCliente = Usuario.builder()
                .idUser(10L)
                .email("cliente@empresa.com")
                .nombres("Juan")
                .apellidos("Pérez")
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .build();

        clienteAuth = new UsernamePasswordAuthenticationToken(
                "cliente@empresa.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
        );

        adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@lysandri.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    void obtenerPorId_cuandoEsMismoUsuario_permiteAcceso() {
        when(userManagementUseCase.obtenerPorId(10L)).thenReturn(usuarioCliente);

        ResponseEntity<UsuarioResponseDto> response = usuarioController.obtenerPorId(10L, clienteAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("cliente@empresa.com", response.getBody().getEmail());
    }

    @Test
    void obtenerPorId_cuandoEsOtroUsuarioNoAdmin_lanzaAccessDeniedException() {
        when(userManagementUseCase.obtenerPorId(10L)).thenReturn(usuarioCliente);

        Authentication otroClienteAuth = new UsernamePasswordAuthenticationToken(
                "hacker@empresa.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
        );

        assertThrows(AccessDeniedException.class, () ->
                usuarioController.obtenerPorId(10L, otroClienteAuth));
    }

    @Test
    void obtenerPorId_cuandoEsAdmin_permiteAccesoAInformacionDeTercero() {
        when(userManagementUseCase.obtenerPorId(10L)).thenReturn(usuarioCliente);

        ResponseEntity<UsuarioResponseDto> response = usuarioController.obtenerPorId(10L, adminAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("cliente@empresa.com", response.getBody().getEmail());
    }

    @Test
    void actualizarUsuario_noAdminIntentaCambiarRol_lanzaAccessDeniedException() {
        when(userManagementUseCase.obtenerPorId(10L)).thenReturn(usuarioCliente);

        UsuarioUpdateRequest request = new UsuarioUpdateRequest();
        request.setNombres("Juan");
        request.setRol("ADMIN"); // Intento de auto-escalamiento de privilegios

        assertThrows(AccessDeniedException.class, () ->
                usuarioController.actualizarUsuario(10L, request, clienteAuth));

        verify(userManagementUseCase, never()).actualizarUsuario(anyLong(), any());
    }

    @Test
    void actualizarUsuario_noAdminIntentaDesactivarCuenta_lanzaAccessDeniedException() {
        when(userManagementUseCase.obtenerPorId(10L)).thenReturn(usuarioCliente);

        UsuarioUpdateRequest request = new UsuarioUpdateRequest();
        request.setActivo(false);

        assertThrows(AccessDeniedException.class, () ->
                usuarioController.actualizarUsuario(10L, request, clienteAuth));

        verify(userManagementUseCase, never()).actualizarUsuario(anyLong(), any());
    }

    @Test
    void actualizarUsuario_adminNoPuedeEvitarAuditoriaCambiandoRolPorPut() {
        when(userManagementUseCase.obtenerPorId(10L)).thenReturn(usuarioCliente);

        UsuarioUpdateRequest request = new UsuarioUpdateRequest();
        request.setRol("TRABAJADOR");

        assertThrows(AccessDeniedException.class, () ->
                usuarioController.actualizarUsuario(10L, request, adminAuth));

        verify(userManagementUseCase, never()).actualizarUsuario(anyLong(), any());
        verify(userManagementUseCase, never()).actualizarRol(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void actualizarUsuario_clienteActualizaDatosPropiosValidos_exitoso() {
        when(userManagementUseCase.obtenerPorId(10L)).thenReturn(usuarioCliente);
        when(userManagementUseCase.actualizarUsuario(eq(10L), any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(1);
            u.setIdUser(10L);
            u.setEmail("cliente@empresa.com");
            u.setRol(RolUsuario.CLIENTE);
            u.setActivo(true);
            return u;
        });

        UsuarioUpdateRequest request = new UsuarioUpdateRequest();
        request.setNombres("Juan Carlos");
        request.setTelefono("987654321");

        ResponseEntity<UsuarioResponseDto> response = usuarioController.actualizarUsuario(10L, request, clienteAuth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Juan Carlos", response.getBody().getNombres());
        verify(userManagementUseCase).actualizarUsuario(eq(10L), any(Usuario.class));
    }

    @Test
    void actualizarRol_adminAsignaCuentaInterna_yRegistraCambio() {
        when(userManagementUseCase.actualizarRol(eq(10L), eq(RolUsuario.TRABAJADOR), anyString(), eq("admin@lysandri.com")))
                .thenAnswer(i -> {
                    Usuario u = usuarioCliente;
                    u.setRol(RolUsuario.TRABAJADOR);
                    return u;
                });

        ResponseEntity<UsuarioResponseDto> response = usuarioController.actualizarRol(
                10L,
                "TRABAJADOR",
                "Alta como trabajador del área comercial",
                adminAuth
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("TRABAJADOR", response.getBody().getRol());
        verify(userManagementUseCase).actualizarRol(eq(10L), eq(RolUsuario.TRABAJADOR), anyString(), eq("admin@lysandri.com"));
    }
}

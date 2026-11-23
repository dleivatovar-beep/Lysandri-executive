package com.lysandri.application.service;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.AuthUseCase;
import com.lysandri.domain.ports.out.*;
import com.lysandri.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicRegistrationSessionTest {
    final UserRepositoryPort users = mock(UserRepositoryPort.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final JwtService jwt = mock(JwtService.class);
    final AuthenticationManager manager = mock(AuthenticationManager.class);
    final UserDetailsService details = mock(UserDetailsService.class);
    final UserActivationPort activation = mock(UserActivationPort.class);
    final ConsentimientoPort consent = mock(ConsentimientoPort.class);
    AuthService service;

    @BeforeEach void prepare() {
        service = new AuthService(users, encoder, jwt, manager, details, activation,
                null, null, "http://127.0.0.1:3000", consent);
    }

    AuthUseCase.RegisterCommand command(String email, boolean accepted) {
        return new AuthUseCase.RegisterCommand("Cliente", "Prueba", email, "Prueba2026!",
                "999123456", accepted, accepted, AuthService.VERSION_CONDICIONES);
    }

    @Test void createsActiveClientWithRealSessionAndConsentWithoutSmtp() {
        when(encoder.encode("Prueba2026!")).thenReturn("hashed-password");
        when(users.guardar(any())).thenAnswer(invocation -> {
            Usuario user = invocation.getArgument(0);
            assertEquals(RolUsuario.CLIENTE, user.getRol());
            assertTrue(user.isActivo());
            assertFalse(user.isPendienteActivacion());
            assertFalse(user.isBloqueadoAdmin());
            assertEquals("cliente@lysandri.invalid", user.getEmail());
            user.setIdUser(101L);
            return user;
        });
        var principal = org.springframework.security.core.userdetails.User
                .withUsername("cliente@lysandri.invalid").password("hash").roles("CLIENTE").build();
        when(details.loadUserByUsername("cliente@lysandri.invalid")).thenReturn(principal);
        when(jwt.generateToken(principal)).thenReturn("signed-token");

        var result = service.register(command("  CLIENTE@LYSANDRI.INVALID  ", true));
        assertEquals("signed-token", result.token());
        assertEquals("CLIENTE", result.rol());
        assertFalse(result.pendienteActivacion());
        verify(consent).registrar(101L, AuthService.VERSION_CONDICIONES);
        verifyNoInteractions(activation);
    }

    @Test void rejectsDuplicateEmailWithoutCreatingAnotherAccount() {
        when(users.existePorEmail("cliente@lysandri.invalid")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.register(command("cliente@lysandri.invalid", true)));
        verify(users, never()).guardar(any());
        verifyNoInteractions(consent, jwt);
    }

    @Test void requiresConsentBeforeCreatingAccount() {
        assertThrows(IllegalArgumentException.class, () -> service.register(command("cliente@lysandri.invalid", false)));
        verifyNoInteractions(users, consent, jwt);
    }

    @Test void verificationModeDoesNotSilentlySkipUnavailableMail() {
        ReflectionTestUtils.setField(service, "requireEmailVerification", true);
        assertThrows(IllegalStateException.class, () -> service.register(command("cliente@lysandri.invalid", true)));
        verifyNoInteractions(users, consent, jwt);
    }

    Usuario pending() {
        return Usuario.builder().idUser(101L).email("cliente@lysandri.invalid")
                .rol(RolUsuario.CLIENTE).passw("hash").pendienteActivacion(true).activo(false).build();
    }

    @Test void existingSelfRegistrationCanEnterWithItsOwnPassword() {
        var user = pending();
        when(users.buscarPorEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("Prueba2026!", "hash")).thenReturn(true);
        when(users.activarRegistroPropio(101L)).thenReturn(true);
        var principal = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail()).password("hash").roles("CLIENTE").build();
        when(details.loadUserByUsername(user.getEmail())).thenReturn(principal);
        when(jwt.generateToken(principal)).thenReturn("signed-token");
        assertEquals("signed-token", service.login(new AuthUseCase.LoginCommand(user.getEmail(), "Prueba2026!")).token());
        verify(activation).revocarTokensUsuario(101L);
    }

    @Test void wrongPasswordDoesNotActivateExistingRegistration() {
        when(users.buscarPorEmail("cliente@lysandri.invalid")).thenReturn(Optional.of(pending()));
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> service.login(new AuthUseCase.LoginCommand("cliente@lysandri.invalid", "incorrecta")));
        verify(users, never()).activarRegistroPropio(anyLong());
    }

    @Test void adminBlockIsNotLiftedByClientLogin() {
        var user = pending();user.setBloqueadoAdmin(true);
        when(users.buscarPorEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(encoder.matches("Prueba2026!", "hash")).thenReturn(true);
        assertThrows(org.springframework.security.authentication.LockedException.class,
                () -> service.login(new AuthUseCase.LoginCommand(user.getEmail(), "Prueba2026!")));
        verify(users, never()).activarRegistroPropio(anyLong());
    }
}

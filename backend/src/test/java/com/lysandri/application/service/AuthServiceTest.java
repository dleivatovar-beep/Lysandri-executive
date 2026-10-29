package com.lysandri.application.service;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.model.UsuarioActivacionToken;
import com.lysandri.domain.ports.in.AuthUseCase;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.domain.ports.out.UserActivationPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import com.lysandri.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias: AuthService y flujo seguro de activación de cuenta")
class AuthServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private UserActivationPort activationPort;

    @Mock
    private NotificationPort notificationPort;

    @Mock
    private ActivationOutboxService activationOutboxService;

    @InjectMocks
    private AuthService authService;

    private Usuario usuario;
    private UsuarioActivacionToken tokenValido;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .idUser(10L)
                .email("invitado@empresa.com")
                .nombres("Invitado")
                .apellidos("Executive")
                .passw("$2a$10$temporaryRandomHash")
                .rol(RolUsuario.CLIENTE)
                .activo(false)
                .bloqueadoAdmin(false)
                .pendienteActivacion(true)
                .fechaCreacion(OffsetDateTime.now())
                .fechaActualizacion(OffsetDateTime.now())
                .build();

        tokenValido = UsuarioActivacionToken.builder()
                .idToken(1L)
                .idUser(10L)
                .token("valid_secure_activation_token_123456")
                .usado(false)
                .revocado(false)
                .fechaExpiracion(OffsetDateTime.now().plusDays(1))
                .fechaCreacion(OffsetDateTime.now())
                .build();

        lenient().when(userRepository.guardar(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Activación exitosa: establece nueva contraseña, activa usuario y marca token como usado")
    void activarCuenta_conTokenValido_activaUsuarioYActualizaPassword() {
        when(activationPort.buscarPorTokenConBloqueo("valid_secure_activation_token_123456"))
                .thenReturn(Optional.of(tokenValido));
        when(userRepository.buscarPorId(10L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("NuevaPasswordSegura2026!")).thenReturn("$2a$10$encodedNewPasswordHash");

        UserDetails userDetailsMock = mock(UserDetails.class);
        when(userDetailsService.loadUserByUsername(usuario.getEmail())).thenReturn(userDetailsMock);
        when(jwtService.generateToken(userDetailsMock)).thenReturn("jwt_mock_token_xyz");

        AuthUseCase.ActivarCuentaCommand cmd = new AuthUseCase.ActivarCuentaCommand(
                "valid_secure_activation_token_123456",
                "NuevaPasswordSegura2026!"
        );

        var response = assertDoesNotThrow(() -> authService.activarCuenta(cmd));

        assertNotNull(response);
        assertEquals("jwt_mock_token_xyz", response.token());
        assertEquals("invitado@empresa.com", response.email());
        assertTrue(usuario.isActivo());
        assertFalse(usuario.isPendienteActivacion());
        assertEquals("$2a$10$encodedNewPasswordHash", usuario.getPassw());
        verify(userRepository).guardar(usuario);
        verify(activationPort).marcarTokenUsado(1L);
    }

    @Test
    @DisplayName("Activación con token inexistente: lanza IllegalArgumentException")
    void activarCuenta_conTokenInexistente_lanzaExcepcion() {
        when(activationPort.buscarPorTokenConBloqueo("token_no_existe")).thenReturn(Optional.empty());

        AuthUseCase.ActivarCuentaCommand cmd = new AuthUseCase.ActivarCuentaCommand(
                "token_no_existe",
                "NuevaPasswordSegura2026!"
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authService.activarCuenta(cmd));

        assertTrue(ex.getMessage().contains("inválido") || ex.getMessage().contains("inexistente"));
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Activación con token ya utilizado: lanza IllegalStateException")
    void activarCuenta_conTokenYaUsado_lanzaExcepcion() {
        UsuarioActivacionToken tokenUsado = UsuarioActivacionToken.builder()
                .idToken(2L)
                .idUser(10L)
                .token("token_ya_usado")
                .usado(true)
                .revocado(false)
                .fechaExpiracion(OffsetDateTime.now().plusDays(1))
                .build();

        when(activationPort.buscarPorTokenConBloqueo("token_ya_usado")).thenReturn(Optional.of(tokenUsado));

        AuthUseCase.ActivarCuentaCommand cmd = new AuthUseCase.ActivarCuentaCommand(
                "token_ya_usado",
                "NuevaPasswordSegura2026!"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.activarCuenta(cmd));

        assertTrue(ex.getMessage().contains("ya ha sido utilizado"));
        verify(userRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Activación con token revocado: lanza IllegalStateException")
    void activarCuenta_conTokenRevocado_lanzaExcepcion() {
        UsuarioActivacionToken tokenRevocado = UsuarioActivacionToken.builder()
                .idToken(4L)
                .idUser(10L)
                .token("token_revocado")
                .usado(false)
                .revocado(true)
                .fechaExpiracion(OffsetDateTime.now().plusDays(1))
                .build();

        when(activationPort.buscarPorTokenConBloqueo("token_revocado")).thenReturn(Optional.of(tokenRevocado));

        AuthUseCase.ActivarCuentaCommand cmd = new AuthUseCase.ActivarCuentaCommand(
                "token_revocado",
                "NuevaPasswordSegura2026!"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.activarCuenta(cmd));

        assertTrue(ex.getMessage().contains("ha sido revocado") || ex.getMessage().contains("utilizado"));
        verify(userRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Activación rechazada en cuenta con bloqueo administrativo: no levanta el bloqueo")
    void activarCuenta_conUsuarioBloqueadoAdmin_lanzaExcepcion() {
        usuario.setBloqueadoAdmin(true);
        usuario.setActivo(false);

        when(activationPort.buscarPorTokenConBloqueo("valid_secure_activation_token_123456"))
                .thenReturn(Optional.of(tokenValido));
        when(userRepository.buscarPorId(10L)).thenReturn(Optional.of(usuario));

        AuthUseCase.ActivarCuentaCommand cmd = new AuthUseCase.ActivarCuentaCommand(
                "valid_secure_activation_token_123456",
                "NuevaPasswordSegura2026!"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.activarCuenta(cmd));

        assertTrue(ex.getMessage().contains("deshabilitada administrativamente"));
        assertFalse(usuario.isActivo(), "La cuenta debe permanecer inactiva");
        assertTrue(usuario.isBloqueadoAdmin(), "El bloqueo administrativo debe persistir");
        verify(userRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Activación con token caducado: lanza IllegalStateException")
    void activarCuenta_conTokenCaducado_lanzaExcepcion() {
        UsuarioActivacionToken tokenCaducado = UsuarioActivacionToken.builder()
                .idToken(3L)
                .idUser(10L)
                .token("token_caducado")
                .usado(false)
                .revocado(false)
                .fechaExpiracion(OffsetDateTime.now().minusHours(2))
                .build();

        when(activationPort.buscarPorTokenConBloqueo("token_caducado")).thenReturn(Optional.of(tokenCaducado));

        AuthUseCase.ActivarCuentaCommand cmd = new AuthUseCase.ActivarCuentaCommand(
                "token_caducado",
                "NuevaPasswordSegura2026!"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                authService.activarCuenta(cmd));

        assertTrue(ex.getMessage().contains("ha caducado"));
        verify(userRepository, never()).guardar(any());
    }

    @Test
    @DisplayName("Login con cuenta con bloqueo administrativo: lanza LockedException con mensaje uniforme")
    void login_cuentaBloqueadaAdmin_lanzaLockedException() {
        usuario.setBloqueadoAdmin(true);
        usuario.setActivo(false);
        when(userRepository.buscarPorEmail("invitado@empresa.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("cualquierPassword", usuario.getPassw())).thenReturn(true);

        AuthUseCase.LoginCommand cmd = new AuthUseCase.LoginCommand("invitado@empresa.com", "cualquierPassword");

        LockedException ex = assertThrows(LockedException.class, () ->
                authService.login(cmd));

        assertEquals(AuthService.MENSAJE_ERROR_LOGIN, ex.getMessage());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    @DisplayName("Login con cuenta no activada (con hash provisional invalidado): rechaza con mensaje uniforme")
    void login_cuentaNoActivadaConHashProvisional_rechazaLogin() {
        usuario.setActivo(true);
        usuario.setPassw("$2a$10$INVALIDATED_PROVISIONAL_PASSWORD_HASH_ABC");
        when(userRepository.buscarPorEmail("invitado@empresa.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("cualquierPassword", usuario.getPassw())).thenReturn(true);

        AuthUseCase.LoginCommand cmd = new AuthUseCase.LoginCommand("invitado@empresa.com", "cualquierPassword");

        DisabledException ex = assertThrows(DisabledException.class, () ->
                authService.login(cmd));

        assertEquals(AuthService.MENSAJE_ERROR_LOGIN, ex.getMessage());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    @DisplayName("Login con cuenta inactiva: rechaza con mensaje uniforme")
    void login_cuentaInactiva_rechazaLogin() {
        usuario.setActivo(false);
        usuario.setBloqueadoAdmin(false);
        usuario.setPendienteActivacion(false);
        when(userRepository.buscarPorEmail("invitado@empresa.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("cualquierPassword", usuario.getPassw())).thenReturn(true);

        AuthUseCase.LoginCommand cmd = new AuthUseCase.LoginCommand("invitado@empresa.com", "cualquierPassword");

        DisabledException ex = assertThrows(DisabledException.class, () ->
                authService.login(cmd));

        assertEquals(AuthService.MENSAJE_ERROR_LOGIN, ex.getMessage());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    @DisplayName("Solicitar nuevo enlace de activacion: respuesta publica uniforme y envio de enlace")
    void solicitarNuevoEnlaceActivacion_generaTokenYEnviaEnlace() {
        when(userRepository.buscarPorEmail("invitado@empresa.com")).thenReturn(Optional.of(usuario));
        when(activationPort.crearTokenActivacion(10L)).thenReturn(tokenValido);

        AuthUseCase.SolicitarActivacionCommand cmd = new AuthUseCase.SolicitarActivacionCommand("invitado@empresa.com");

        AuthUseCase.MensajePublicoResponse res = authService.solicitarNuevoEnlaceActivacion(cmd);

        assertNotNull(res);
        assertEquals("Si el correo electrónico corresponde a una cuenta pendiente de activación, se ha enviado un enlace a su bandeja de entrada.", res.mensaje());
        verify(activationPort).crearTokenActivacion(10L);
        verify(activationOutboxService).crearTareaActivacion(
                isNull(),
                isNull(),
                eq(10L),
                eq(tokenValido.getIdToken()),
                eq(tokenValido.getToken()),
                eq("invitado@empresa.com"),
                eq("Invitado"),
                eq("ACTIVACION_REENVIO_USER_10_" + tokenValido.getIdToken())
        );
        verify(notificationPort, never()).enviarEnlaceActivacion(any(), any(), any());
    }

    @Test
    @DisplayName("Solicitar nuevo enlace con correo inexistente: respuesta uniforme idéntica sin filtrar existencia")
    void solicitarNuevoEnlaceActivacion_correoInexistente_respuestaUniforme() {
        when(userRepository.buscarPorEmail("inexistente@empresa.com")).thenReturn(Optional.empty());

        AuthUseCase.SolicitarActivacionCommand cmd = new AuthUseCase.SolicitarActivacionCommand("inexistente@empresa.com");

        AuthUseCase.MensajePublicoResponse res = authService.solicitarNuevoEnlaceActivacion(cmd);

        assertNotNull(res);
        assertEquals("Si el correo electrónico corresponde a una cuenta pendiente de activación, se ha enviado un enlace a su bandeja de entrada.", res.mensaje());
        verify(activationPort, never()).crearTokenActivacion(any());
        verify(notificationPort, never()).enviarEnlaceActivacion(any(), any(), any());
    }
    @Test
    @DisplayName("Login con cuenta activa y credenciales correctas: inicio de sesión exitoso y genera JWT")
    void login_cuentaActiva_credencialesCorrectas_retornaToken() {
        usuario.setActivo(true);
        usuario.setBloqueadoAdmin(false);
        usuario.setPendienteActivacion(false);
        usuario.setPassw("$2a$10$encodedRealPasswordHash");

        when(userRepository.buscarPorEmail("invitado@empresa.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordCorrecto123!", "$2a$10$encodedRealPasswordHash")).thenReturn(true);

        UserDetails userDetailsMock = mock(UserDetails.class);
        when(userDetailsService.loadUserByUsername(usuario.getEmail())).thenReturn(userDetailsMock);
        when(jwtService.generateToken(userDetailsMock)).thenReturn("jwt_access_token_ok");

        AuthUseCase.LoginCommand cmd = new AuthUseCase.LoginCommand("invitado@empresa.com", "PasswordCorrecto123!");

        AuthUseCase.AuthResponse response = authService.login(cmd);

        assertNotNull(response);
        assertEquals("jwt_access_token_ok", response.token());
        assertEquals("invitado@empresa.com", response.email());
        assertEquals("Invitado Executive", response.nombreCompleto());
        assertEquals("CLIENTE", response.rol());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Login con contraseña incorrecta: lanza BadCredentialsException con mensaje uniforme y no emite JWT")
    void login_passwordIncorrecto_lanzaBadCredentialsException() {
        usuario.setActivo(true);
        usuario.setBloqueadoAdmin(false);
        usuario.setPendienteActivacion(false);
        usuario.setPassw("$2a$10$encodedRealPasswordHash");

        when(userRepository.buscarPorEmail("invitado@empresa.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordErroneo999!", "$2a$10$encodedRealPasswordHash")).thenReturn(false);

        AuthUseCase.LoginCommand cmd = new AuthUseCase.LoginCommand("invitado@empresa.com", "PasswordErroneo999!");

        org.springframework.security.authentication.BadCredentialsException ex =
                assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
                        authService.login(cmd));

        assertEquals(AuthService.MENSAJE_ERROR_LOGIN, ex.getMessage());
        verifyNoInteractions(authenticationManager);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Login con cuenta inexistente: lanza BadCredentialsException idéntico sin revelar si el correo existe")
    void login_cuentaInexistente_lanzaBadCredentialsException() {
        when(userRepository.buscarPorEmail("noexiste@empresa.com")).thenReturn(Optional.empty());

        AuthUseCase.LoginCommand cmd = new AuthUseCase.LoginCommand("noexiste@empresa.com", "PasswordCualquiera123!");

        org.springframework.security.authentication.BadCredentialsException ex =
                assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
                        authService.login(cmd));

        assertEquals(AuthService.MENSAJE_ERROR_LOGIN, ex.getMessage());
        verifyNoInteractions(authenticationManager);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Login con cuenta pendiente de activación: rechaza con mensaje uniforme y no emite JWT")
    void login_cuentaPendienteActivacion_rechazaLogin() {
        usuario.setActivo(true);
        usuario.setBloqueadoAdmin(false);
        usuario.setPendienteActivacion(true);
        usuario.setPassw("$2a$10$temporaryPass");

        when(userRepository.buscarPorEmail("invitado@empresa.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("PasswordCualquiera123!", "$2a$10$temporaryPass")).thenReturn(true);

        AuthUseCase.LoginCommand cmd = new AuthUseCase.LoginCommand("invitado@empresa.com", "PasswordCualquiera123!");

        DisabledException ex = assertThrows(DisabledException.class, () ->
                authService.login(cmd));

        assertEquals(AuthService.MENSAJE_ERROR_LOGIN, ex.getMessage());
        verifyNoInteractions(authenticationManager);
        verifyNoInteractions(jwtService);
    }
}

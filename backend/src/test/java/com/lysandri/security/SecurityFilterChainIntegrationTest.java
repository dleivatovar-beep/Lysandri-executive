package com.lysandri.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.adapters.in.web.AuthController;
import com.lysandri.adapters.in.web.OrderController;
import com.lysandri.adapters.in.web.UsuarioController;
import com.lysandri.adapters.in.web.dto.ActivarCuentaRequest;
import com.lysandri.adapters.in.web.dto.LoginRequest;
import com.lysandri.adapters.in.web.dto.SolicitarActivacionRequest;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.ports.in.AuthUseCase;
import com.lysandri.domain.ports.in.BuyCourseUseCase;
import com.lysandri.domain.ports.in.UserManagementUseCase;
import com.lysandri.domain.ports.out.OrderRepositoryPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import com.lysandri.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        UsuarioController.class,
        OrderController.class,
        com.lysandri.adapters.in.web.WhatsAppWebhookController.class,
        com.lysandri.adapters.in.web.WhatsAppOperadorController.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, GlobalExceptionHandler.class, com.lysandri.config.CuentasBancariasProperties.class})
@TestPropertySource(properties = {
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A25432A",
        "jwt.expiration=3600000"
})
@DisplayName("Pruebas HTTP de Cadena de Seguridad: Filtros JWT, Distinción 401 vs 403, Inicio de Sesión y Activación")
class SecurityFilterChainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AuthUseCase authUseCase;

    @MockBean
    private UserManagementUseCase userManagementUseCase;

    @MockBean
    private BuyCourseUseCase buyCourseUseCase;

    @MockBean
    private OrderRepositoryPort orderRepository;

    @MockBean
    private UserRepositoryPort userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private AuthenticationProvider authenticationProvider;

    @MockBean
    private com.lysandri.domain.ports.in.WhatsAppUseCase whatsAppUseCase;

    private final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A25432A";
    private final String WRONG_SECRET = "994E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A25432B";

    private UsuarioEntity clienteUser;
    private UsuarioEntity adminUser;

    @BeforeEach
    void setUp() {
        clienteUser = UsuarioEntity.builder()
                .idUser(10L)
                .email("cliente@empresa.com")
                .nombres("Cliente")
                .apellidos("Ejecutivo")
                .passw("$2a$10$encodedHash")
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .bloqueadoAdmin(false)
                .pendienteActivacion(false)
                .build();

        adminUser = UsuarioEntity.builder()
                .idUser(1L)
                .email("admin@lysandri.com")
                .nombres("Admin")
                .apellidos("General")
                .passw("$2a$10$encodedHash")
                .rol(RolUsuario.ADMIN)
                .activo(true)
                .bloqueadoAdmin(false)
                .pendienteActivacion(false)
                .build();

        when(userDetailsService.loadUserByUsername("cliente@empresa.com")).thenReturn(clienteUser);
        when(userDetailsService.loadUserByUsername("admin@lysandri.com")).thenReturn(adminUser);
    }

    // =========================================================================
    // 1. Sesiones y Validación de JWT
    // =========================================================================

    @Test
    @DisplayName("Petición sin autenticación a ruta protegida (/api/v1/auth/me): retorna 401 Unauthorized")
    void rutaProtegida_sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Autenticación requerida o token inválido"));
    }

    @Test
    @DisplayName("Petición con token JWT vencido: retorna 401 Unauthorized")
    void rutaProtegida_conTokenVencido_retorna401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        String expiredToken = Jwts.builder()
                .subject("cliente@empresa.com")
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Autenticación requerida o token inválido"));
    }

    @Test
    @DisplayName("Petición con token JWT alterado o corrupto: retorna 401 Unauthorized")
    void rutaProtegida_conTokenAlterado_retorna401() throws Exception {
        String validToken = jwtService.generateToken(clienteUser);
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Petición con token JWT firmado con otra clave secreta: retorna 401 Unauthorized")
    void rutaProtegida_conTokenFirmadoConOtraClave_retorna401() throws Exception {
        SecretKey rogueKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(WRONG_SECRET));
        String rogueToken = Jwts.builder()
                .subject("cliente@empresa.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(rogueKey)
                .compact();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + rogueToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Usuario con rol CLIENTE intenta acceder a ruta de administración (/api/v1/usuarios): retorna 403 Forbidden")
    void usuarioCliente_accedeRutaAdmin_retorna403Forbidden() throws Exception {
        String clientToken = jwtService.generateToken(clienteUser);

        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Acceso denegado: permisos insuficientes"));
    }

    @Test
    @DisplayName("Usuario con rol ADMIN accede a ruta de administración (/api/v1/usuarios): acceso permitido (200 OK)")
    void usuarioAdmin_accedeRutaAdmin_retorna200Ok() throws Exception {
        String adminToken = jwtService.generateToken(adminUser);
        when(userManagementUseCase.listarUsuarios(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El cliente no puede auto-asignarse privilegios: permisos provienen del servidor, no del token")
    void permisosProcedenDelServidor_tokenForjadoNoOtorgaPrivilegios() throws Exception {
        String forgedClaimToken = jwtService.generateToken(java.util.Map.of("rol", "ADMIN"), clienteUser);

        mockMvc.perform(get("/api/v1/usuarios")
                        .header("Authorization", "Bearer " + forgedClaimToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acceso denegado: permisos insuficientes"));
    }

    // =========================================================================
    // 2. Inicio de Sesión HTTP
    // =========================================================================

    @Test
    @DisplayName("Login exitoso con cuenta activa y credenciales correctas: retorna 200 OK y token")
    void login_credencialesCorrectas_retorna200YToken() throws Exception {
        AuthUseCase.AuthResponse authResponse = new AuthUseCase.AuthResponse(
                "jwt_token_valido",
                10L,
                "cliente@empresa.com",
                "Cliente Ejecutivo",
                "CLIENTE",
                null
        );
        when(authUseCase.login(any(AuthUseCase.LoginCommand.class))).thenReturn(authResponse);

        LoginRequest req = new LoginRequest();
        req.setEmail("cliente@empresa.com");
        req.setPassword("PasswordSegura2026!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt_token_valido"))
                .andExpect(jsonPath("$.email").value("cliente@empresa.com"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    @DisplayName("Login con contraseña incorrecta: retorna 401 Unauthorized con mensaje público uniforme")
    void login_passwordIncorrecto_retorna401() throws Exception {
        when(authUseCase.login(any(AuthUseCase.LoginCommand.class)))
                .thenThrow(new BadCredentialsException("No se pudo iniciar sesión con los datos proporcionados"));

        LoginRequest req = new LoginRequest();
        req.setEmail("cliente@empresa.com");
        req.setPassword("PasswordErronea!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No se pudo iniciar sesión con los datos proporcionados"))
                .andExpect(jsonPath("$.passw").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("Login con cuenta inexistente: retorna 401 Unauthorized con mensaje público idéntico (anti-enumeración)")
    void login_cuentaInexistente_retorna401Identico() throws Exception {
        when(authUseCase.login(any(AuthUseCase.LoginCommand.class)))
                .thenThrow(new BadCredentialsException("No se pudo iniciar sesión con los datos proporcionados"));

        LoginRequest req = new LoginRequest();
        req.setEmail("noexiste@empresa.com");
        req.setPassword("CualquierPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No se pudo iniciar sesión con los datos proporcionados"))
                .andExpect(jsonPath("$.passw").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("Login con cuenta pendiente de activación: retorna 401 con mensaje público uniforme")
    void login_cuentaPendienteActivacion_retorna401() throws Exception {
        when(authUseCase.login(any(AuthUseCase.LoginCommand.class)))
                .thenThrow(new DisabledException("No se pudo iniciar sesión con los datos proporcionados"));

        LoginRequest req = new LoginRequest();
        req.setEmail("pendiente@empresa.com");
        req.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No se pudo iniciar sesión con los datos proporcionados"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("Login con cuenta bloqueada por administración: retorna 401 con mensaje público uniforme")
    void login_cuentaBloqueadaAdmin_retorna401() throws Exception {
        when(authUseCase.login(any(AuthUseCase.LoginCommand.class)))
                .thenThrow(new LockedException("No se pudo iniciar sesión con los datos proporcionados"));

        LoginRequest req = new LoginRequest();
        req.setEmail("bloqueado@empresa.com");
        req.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No se pudo iniciar sesión con los datos proporcionados"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("Login con cuenta inactiva: retorna 401 con mensaje público uniforme")
    void login_cuentaInactiva_retorna401() throws Exception {
        when(authUseCase.login(any(AuthUseCase.LoginCommand.class)))
                .thenThrow(new DisabledException("No se pudo iniciar sesión con los datos proporcionados"));

        LoginRequest req = new LoginRequest();
        req.setEmail("inactivo@empresa.com");
        req.setPassword("Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No se pudo iniciar sesión con los datos proporcionados"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // =========================================================================
    // 3. Activación de Cuenta HTTP
    // =========================================================================

    @Test
    @DisplayName("Activación con enlace y token válido: retorna 200 OK y token de sesión")
    void activarCuenta_tokenValido_retorna200() throws Exception {
        AuthUseCase.AuthResponse authResponse = new AuthUseCase.AuthResponse(
                "jwt_post_activacion",
                10L,
                "nuevo@empresa.com",
                "Nuevo Alumno",
                "CLIENTE",
                null
        );
        when(authUseCase.activarCuenta(any(AuthUseCase.ActivarCuentaCommand.class))).thenReturn(authResponse);

        ActivarCuentaRequest req = new ActivarCuentaRequest();
        req.setToken("valid_activation_token_xyz");
        req.setPassword("NuevaPasswordSegura2026!");

        mockMvc.perform(post("/api/v1/auth/activar-cuenta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt_post_activacion"));
    }

    @Test
    @DisplayName("Activación con token vencido: retorna 409 Conflict")
    void activarCuenta_tokenVencido_retorna409() throws Exception {
        when(authUseCase.activarCuenta(any(AuthUseCase.ActivarCuentaCommand.class)))
                .thenThrow(new IllegalStateException("El token de activación ha caducado"));

        ActivarCuentaRequest req = new ActivarCuentaRequest();
        req.setToken("expired_token");
        req.setPassword("NuevaPasswordSegura2026!");

        mockMvc.perform(post("/api/v1/auth/activar-cuenta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("El token de activación ha caducado"));
    }

    @Test
    @DisplayName("Activación con token ya utilizado: retorna 409 Conflict")
    void activarCuenta_tokenYaUsado_retorna409() throws Exception {
        when(authUseCase.activarCuenta(any(AuthUseCase.ActivarCuentaCommand.class)))
                .thenThrow(new IllegalStateException("El token de activación ya ha sido utilizado"));

        ActivarCuentaRequest req = new ActivarCuentaRequest();
        req.setToken("already_used_token");
        req.setPassword("NuevaPasswordSegura2026!");

        mockMvc.perform(post("/api/v1/auth/activar-cuenta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("El token de activación ya ha sido utilizado"));
    }

    @Test
    @DisplayName("Solicitar activación devuelve respuesta uniforme sin filtrar si el correo existe")
    void solicitarActivacion_respuestaUniforme() throws Exception {
        AuthUseCase.MensajePublicoResponse msg = new AuthUseCase.MensajePublicoResponse(
                "Si el correo electrónico corresponde a una cuenta pendiente de activación, se ha enviado un enlace a su bandeja de entrada."
        );
        when(authUseCase.solicitarNuevoEnlaceActivacion(any(AuthUseCase.SolicitarActivacionCommand.class))).thenReturn(msg);

        SolicitarActivacionRequest req = new SolicitarActivacionRequest();
        req.setEmail("cualquiera@empresa.com");

        mockMvc.perform(post("/api/v1/auth/solicitar-activacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Si el correo electrónico corresponde a una cuenta pendiente de activación, se ha enviado un enlace a su bandeja de entrada."));
    }

    // =========================================================================
    // 4. WhatsApp: Webhook público vs Operaciones de operador protegidas
    // =========================================================================

    @Test
    @DisplayName("WhatsApp Webhook (GET): es público y accesible sin token")
    void whatsappWebhook_publicoSinToken() throws Exception {
        when(whatsAppUseCase.verificarWebhook("subscribe", "mytoken", "challenge_abc"))
                .thenReturn("challenge_abc");

        mockMvc.perform(get("/api/v1/whatsapp/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "mytoken")
                        .param("hub.challenge", "challenge_abc"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge_abc"));
    }

    @Test
    @DisplayName("WhatsApp Operador (/mensajes): sin token retorna 401 Unauthorized")
    void whatsappOperador_sinToken_retorna401() throws Exception {
        com.lysandri.adapters.in.web.dto.EnviarWhatsAppRequest req =
                new com.lysandri.adapters.in.web.dto.EnviarWhatsAppRequest("51999888777", "Hola cliente");

        mockMvc.perform(post("/api/v1/whatsapp/mensajes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Autenticación requerida o token inválido"));
    }

    @Test
    @DisplayName("WhatsApp Operador (/mensajes): usuario con rol CLIENTE recibe 403 Forbidden")
    void whatsappOperador_conTokenCliente_retorna403() throws Exception {
        String clienteToken = jwtService.generateToken(clienteUser);

        com.lysandri.adapters.in.web.dto.EnviarWhatsAppRequest req =
                new com.lysandri.adapters.in.web.dto.EnviarWhatsAppRequest("51999888777", "Hola cliente");

        mockMvc.perform(post("/api/v1/whatsapp/mensajes")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acceso denegado: permisos insuficientes"));
    }

    @Test
    @DisplayName("WhatsApp Operador (/mensajes): usuario con rol ADMIN puede enviar mensaje (201 Created)")
    void whatsappOperador_conTokenAdmin_retorna201() throws Exception {
        String adminToken = jwtService.generateToken(adminUser);

        com.lysandri.adapters.in.web.dto.EnviarWhatsAppRequest req =
                new com.lysandri.adapters.in.web.dto.EnviarWhatsAppRequest("51999888777", "Mensaje oficial");

        com.lysandri.domain.model.whatsapp.MensajeWhatsApp respuesta = com.lysandri.domain.model.whatsapp.MensajeWhatsApp.builder()
                .id(1L)
                .idProveedor("wamid.mock123")
                .telefonoRemitente("LYSANDRI_OFFICIAL")
                .telefonoDestinatario("51999888777")
                .direccion(com.lysandri.domain.model.whatsapp.DireccionMensajeWhatsApp.SALIENTE)
                .contenido("Mensaje oficial")
                .tipo(com.lysandri.domain.model.whatsapp.TipoMensajeWhatsApp.TEXTO)
                .estado(com.lysandri.domain.model.whatsapp.EstadoMensajeWhatsApp.ENVIADO)
                .timestampMensaje(java.time.OffsetDateTime.now())
                .operador("admin@lysandri.com")
                .build();

        when(whatsAppUseCase.enviarMensajeOperador(any(), any(), any(), any()))
                .thenReturn(respuesta);

        mockMvc.perform(post("/api/v1/whatsapp/mensajes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idProveedor").value("wamid.mock123"))
                .andExpect(jsonPath("$.estado").value("ENVIADO"));
    }
}

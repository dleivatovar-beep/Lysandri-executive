package com.lysandri.application.service;

import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.model.UsuarioActivacionToken;
import com.lysandri.domain.ports.in.AuthUseCase;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.domain.ports.out.UserActivationPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import com.lysandri.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AuthService implements AuthUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserActivationPort userActivationPort;
    private final NotificationPort notificationPort;
    private final ActivationOutboxService activationOutboxService;
    private final String frontendUrl;

    public static final String MENSAJE_ERROR_LOGIN = "No se pudo iniciar sesión con los datos proporcionados";
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$wK1nK8v6eE89P4l0F5B7nOBK.wD8vJtLkWsR82e.k1P3F4x5r1y8e";

    // Memoria interna para límites de solicitudes de activación (Rate Limiting)
    private final Map<String, List<Instant>> rateLimitAttempts = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final long WINDOW_SECONDS = 900; // 15 minutos

    public AuthService(
            UserRepositoryPort userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            UserActivationPort userActivationPort
    ) {
        this(userRepository, passwordEncoder, jwtService, authenticationManager, userDetailsService, userActivationPort, null, null, "http://localhost:5173");
    }

    public AuthService(
            UserRepositoryPort userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            UserActivationPort userActivationPort,
            NotificationPort notificationPort,
            String frontendUrl
    ) {
        this(userRepository, passwordEncoder, jwtService, authenticationManager, userDetailsService, userActivationPort, notificationPort, null, frontendUrl);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            UserRepositoryPort userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            UserActivationPort userActivationPort,
            NotificationPort notificationPort,
            ActivationOutboxService activationOutboxService,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userActivationPort = userActivationPort;
        this.notificationPort = notificationPort;
        this.activationOutboxService = activationOutboxService;
        this.frontendUrl = frontendUrl != null && !frontendUrl.isBlank() ? frontendUrl.replaceAll("/+$", "") : "http://localhost:5173";
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterCommand command) {
        log.info("Registrando nuevo cliente ejecutivo (solicitud recibida)");

        if (userRepository.existePorEmail(command.email())) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada con el correo: " + command.email());
        }

        Usuario usuario = Usuario.builder()
                .nombres(command.nombres())
                .apellidos(command.apellidos())
                .email(command.email().trim().toLowerCase())
                .passw(passwordEncoder.encode(command.password()))
                .telefono(command.telefono())
                .rol(RolUsuario.CLIENTE)
                .activo(true)
                .bloqueadoAdmin(false)
                .pendienteActivacion(false)
                .fechaCreacion(OffsetDateTime.now())
                .fechaActualizacion(OffsetDateTime.now())
                .build();

        usuario = userRepository.guardar(usuario);

        var userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token,
                usuario.getIdUser(),
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                usuario.getRol().name(),
                usuario.getMoodleUserId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginCommand command) {
        log.info("Intento de login para usuario (solicitud recibida)");

        String email = command.email() != null ? command.email().trim().toLowerCase() : "";
        Optional<Usuario> usuarioOpt = userRepository.buscarPorEmail(email);

        if (usuarioOpt.isEmpty()) {
            passwordEncoder.matches(command.password() != null ? command.password() : "", DUMMY_BCRYPT_HASH);
            log.warn("Fallo de login: usuario no encontrado en base de datos");
            throw new BadCredentialsException(MENSAJE_ERROR_LOGIN);
        }

        Usuario usuario = usuarioOpt.get();

        // 1. Verificación de credenciales (contraseña) previa a comprobar estado
        boolean passwordValida = passwordEncoder.matches(
                command.password() != null ? command.password() : "",
                usuario.getPassw()
        );

        if (!passwordValida) {
            log.warn("Fallo de login: contraseña incorrecta [userId: {}]", usuario.getIdUser());
            throw new BadCredentialsException(MENSAJE_ERROR_LOGIN);
        }

        // 2. Si la contraseña es válida, comprobar las restricciones de estado de la cuenta
        if (usuario.isBloqueadoAdmin()) {
            log.warn("Fallo de login: cuenta bloqueada por administración [userId: {}]", usuario.getIdUser());
            throw new LockedException(MENSAJE_ERROR_LOGIN);
        }

        if (usuario.isPendienteActivacion()
                || (usuario.getPassw() != null && usuario.getPassw().startsWith("$2a$10$INVALIDATED_PROVISIONAL_PASSWORD_HASH"))) {
            log.warn("Fallo de login: cuenta pendiente de activación [userId: {}]", usuario.getIdUser());
            throw new DisabledException(MENSAJE_ERROR_LOGIN);
        }

        if (!usuario.isActivo()) {
            log.warn("Fallo de login: cuenta inactiva [userId: {}]", usuario.getIdUser());
            throw new DisabledException(MENSAJE_ERROR_LOGIN);
        }

        // 3. Autenticación con authenticationManager para registrar contexto Spring Security
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, command.password())
        );

        var userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String token = jwtService.generateToken(userDetails);

        return new AuthResponse(
                token,
                usuario.getIdUser(),
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                usuario.getRol().name(),
                usuario.getMoodleUserId()
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthResponse activarCuenta(ActivarCuentaCommand command) {
        log.info("Procesando activación atómica de cuenta...");
        if (command == null || command.token() == null || command.token().isBlank()) {
            throw new IllegalArgumentException("Token de activación inválido o ausente");
        }
        if (command.nuevaPassword() == null || command.nuevaPassword().length() < 8) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres");
        }

        // 1. Bloqueo pesimista sobre el token de activación en base de datos (SELECT ... FOR UPDATE)
        // Impide estrictamente que dos peticiones concurrentes consuman el mismo token.
        UsuarioActivacionToken token = userActivationPort.buscarPorTokenConBloqueo(command.token().trim())
                .orElseThrow(() -> new IllegalArgumentException("Token de activación inválido o inexistente"));

        if (token.isUsado()) {
            throw new IllegalStateException("El token de activación ya ha sido utilizado");
        }

        if (token.isRevocado()) {
            throw new IllegalStateException("El token de activación ha sido revocado. Solicite un nuevo enlace.");
        }

        if (token.getFechaExpiracion().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException("El token de activación ha caducado");
        }

        Usuario usuario = userRepository.buscarPorId(token.getIdUser())
                .orElseThrow(() -> new IllegalStateException("Usuario asociado al token no existe"));

        // 2. Comprobación estricta de bloqueo administrativo:
        // Un token de activación NUNCA puede levantar un bloqueo administrativo.
        if (usuario.isBloqueadoAdmin()) {
            throw new IllegalStateException("La cuenta está deshabilitada administrativamente y no puede ser activada.");
        }

        // 3. Confirmación atómica conjunta: Contraseña, estado activo y token consumido se comitean juntos
        usuario.setPassw(passwordEncoder.encode(command.nuevaPassword()));
        usuario.setActivo(true);
        usuario.setPendienteActivacion(false);
        usuario.setFechaActualizacion(OffsetDateTime.now());
        usuario = userRepository.guardar(usuario);

        userActivationPort.marcarTokenUsado(token.getIdToken());
        log.info("Cuenta de usuario ID: {} activada exitosamente de forma atómica.", usuario.getIdUser());

        var userDetails = userDetailsService.loadUserByUsername(usuario.getEmail());
        String jwtToken = jwtService.generateToken(userDetails);

        return new AuthResponse(
                jwtToken,
                usuario.getIdUser(),
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                usuario.getRol().name(),
                usuario.getMoodleUserId()
        );
    }

    @Override
    @Transactional
    public MensajePublicoResponse solicitarNuevoEnlaceActivacion(SolicitarActivacionCommand command) {
        log.info("Solicitud pública de reenvío de enlace de activación recibida");
        if (command == null || command.email() == null || command.email().trim().isBlank()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio");
        }

        String emailLimpio = command.email().trim().toLowerCase();

        // 1. Límites de solicitudes para evitar abuso (Rate Limiting)
        Instant now = Instant.now();
        List<Instant> attempts = rateLimitAttempts.computeIfAbsent(emailLimpio, k -> new ArrayList<>());
        synchronized (attempts) {
            attempts.removeIf(instant -> instant.isBefore(now.minusSeconds(WINDOW_SECONDS)));
            if (attempts.size() >= MAX_REQUESTS_PER_WINDOW) {
                log.warn("Límite de solicitudes de activación excedido para la solicitud recibida");
                throw new IllegalStateException("Ha excedido el límite de solicitudes de activación. Por favor, intente nuevamente en 15 minutos.");
            }
            attempts.add(now);
        }

        // 2. Respuesta pública uniforme (no revela si un correo está registrado)
        final MensajePublicoResponse respuestaUniforme = new MensajePublicoResponse(
                "Si el correo electrónico corresponde a una cuenta pendiente de activación, se ha enviado un enlace a su bandeja de entrada."
        );

        Optional<Usuario> usuarioOpt = userRepository.buscarPorEmail(emailLimpio);
        if (usuarioOpt.isEmpty()) {
            log.info("Correo no registrado. Omitiendo envío pero devolviendo respuesta uniforme.");
            return respuestaUniforme;
        }

        Usuario usuario = usuarioOpt.get();

        // Si la cuenta está bloqueada administrativamente, NUNCA enviar enlace de activación
        if (usuario.isBloqueadoAdmin()) {
            log.warn("Cuenta con bloqueo administrativo intentó solicitar activación. Omitiendo envío.");
            return respuestaUniforme;
        }

        // Si la cuenta requiere activación (invitado pendiente, cuenta inactiva legítima, o hash provisional invalidado por V26)
        boolean requiereActivacion = !usuario.isActivo()
                || usuario.isPendienteActivacion()
                || (usuario.getPassw() != null && usuario.getPassw().startsWith("$2a$10$INVALIDATED_PROVISIONAL_PASSWORD_HASH"));

        if (requiereActivacion) {
            // Invalidar tokens previos y generar uno nuevo
            UsuarioActivacionToken nuevoToken = userActivationPort.crearTokenActivacion(usuario.getIdUser());

            if (activationOutboxService != null) {
                activationOutboxService.crearTareaActivacion(
                        null,
                        null,
                        usuario.getIdUser(),
                        nuevoToken.getIdToken(),
                        nuevoToken.getToken(),
                        usuario.getEmail(),
                        usuario.getNombres(),
                        "ACTIVACION_REENVIO_USER_" + usuario.getIdUser() + "_" + nuevoToken.getIdToken()
                );
                log.info("Tarea de reenvío de activación persistida duraderamente en Outbox para usuario ID: {}", usuario.getIdUser());
            }
        } else {
            log.info("La cuenta ya se encuentra activa. Omitiendo envío de enlace.");
        }

        return respuestaUniforme;
    }

    @Override
    @Transactional(readOnly = true)
    public Usuario obtenerUsuarioAutenticado(String email) {
        return userRepository.buscarPorEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con email: " + email));
    }
}

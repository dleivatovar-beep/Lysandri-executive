package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.LoginRequest;
import com.lysandri.adapters.in.web.dto.RegisterRequest;
import com.lysandri.adapters.in.web.dto.InternalRegisterRequest;
import com.lysandri.application.service.AuthService;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.AuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Registro e inicio de sesión de clientes y administradores")
public class AuthController {

    private static final Profiles REGISTRO_DE_PRUEBA = Profiles.of(
            "sandbox & (original-dev | clone | test) & !prod & !production");

    private final AuthUseCase authUseCase;
    private final Environment environment;

    @Value("${app.internal-registration.enabled:false}")
    private boolean internalRegistrationEnabled;

    @GetMapping("/condiciones-registro")
    public ResponseEntity<Map<String, String>> condicionesRegistro() {
        if (!registroDePruebaHabilitado()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "version", AuthService.VERSION_CONDICIONES,
                "terminos", "Crea una cuenta de prueba para verificar el registro y el acceso. No uses datos reales.",
                "privacidad", "Los datos de prueba se guardan en el entorno habilitado. "
                        + "El correo ingresado se usa para enviar el enlace de activación."
        ));
    }

    @PostMapping("/register")
    @Operation(summary = "Registra una nueva cuenta de cliente")
    public ResponseEntity<AuthUseCase.AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (!registroDePruebaHabilitado()) {
            return ResponseEntity.notFound().build();
        }
        AuthUseCase.RegisterCommand command = new AuthUseCase.RegisterCommand(
                request.getNombres(),
                request.getApellidos(),
                request.getEmail(),
                request.getPassword(),
                request.getTelefono(),
                request.isAceptaTerminos(),
                request.isAceptaPrivacidad(),
                request.getVersion()
        );
        return new ResponseEntity<>(authUseCase.register(command), HttpStatus.CREATED);
    }

    private boolean registroDePruebaHabilitado() {
        String smtp = environment.getProperty("spring.mail.host", "");
        return environment.acceptsProfiles(REGISTRO_DE_PRUEBA)
                && ("127.0.0.1".equals(smtp) || "localhost".equalsIgnoreCase(smtp) || "::1".equals(smtp));
    }

    @PostMapping("/register-interno")
    @Operation(summary = "Registra personal desde la red privada habilitada")
    public ResponseEntity<AuthUseCase.AuthResponse> registerInternal(
            @Valid @RequestBody InternalRegisterRequest request,
            HttpServletRequest httpRequest) {
        if (!internalRegistrationEnabled || !esRedPrivada(httpRequest.getRemoteAddr())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "El registro interno solo está habilitado desde la red privada autorizada.");
        }

        AuthUseCase.RegisterCommand command = new AuthUseCase.RegisterCommand(
                request.getNombres(), request.getApellidos(), request.getEmail(),
                request.getPassword(), request.getTelefono());
        RolUsuario role = switch (request.getArea()) {
            case "ADMINISTRACION" -> RolUsuario.ADMIN;
            case "CONTABILIDAD" -> RolUsuario.CONTABILIDAD;
            case "DOCENCIA" -> RolUsuario.INSTRUCTOR;
            case "ESTUDIANTE" -> RolUsuario.ESTUDIANTE;
            default -> throw new IllegalArgumentException("Área no válida.");
        };
        return ResponseEntity.status(HttpStatus.CREATED).body(authUseCase.registerInternal(command, role));
    }

    private boolean esRedPrivada(String address) {
        try {
            InetAddress ip = InetAddress.getByName(address);
            return ip.isLoopbackAddress() || ip.isSiteLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión con credenciales")
    public ResponseEntity<AuthUseCase.AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthUseCase.LoginCommand command = new AuthUseCase.LoginCommand(
                request.getEmail(),
                request.getPassword()
        );
        return ResponseEntity.ok(authUseCase.login(command));
    }

    @PostMapping("/activar-cuenta")
    @Operation(summary = "Activa una cuenta de usuario y establece su contraseña inicial")
    public ResponseEntity<AuthUseCase.AuthResponse> activarCuenta(@Valid @RequestBody com.lysandri.adapters.in.web.dto.ActivarCuentaRequest request) {
        AuthUseCase.ActivarCuentaCommand command = new AuthUseCase.ActivarCuentaCommand(
                request.getToken(),
                request.getPassword()
        );
        return ResponseEntity.ok(authUseCase.activarCuenta(command));
    }

    @PostMapping("/solicitar-activacion")
    @Operation(summary = "Solicita el reenvío de un enlace de activación de cuenta")
    public ResponseEntity<AuthUseCase.MensajePublicoResponse> solicitarActivacion(@Valid @RequestBody com.lysandri.adapters.in.web.dto.SolicitarActivacionRequest request) {
        AuthUseCase.SolicitarActivacionCommand command = new AuthUseCase.SolicitarActivacionCommand(
                request.getEmail()
        );
        return ResponseEntity.ok(authUseCase.solicitarNuevoEnlaceActivacion(command));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtiene los datos del usuario autenticado")
    public ResponseEntity<Usuario> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authUseCase.obtenerUsuarioAutenticado(authentication.getName()));
    }
}

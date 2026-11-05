package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.LoginRequest;
import com.lysandri.adapters.in.web.dto.RegisterRequest;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.AuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Registro e inicio de sesión de clientes y administradores")
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/register")
    @Operation(summary = "Registra una nueva cuenta de cliente")
    public ResponseEntity<AuthUseCase.AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthUseCase.RegisterCommand command = new AuthUseCase.RegisterCommand(
                request.getNombres(),
                request.getApellidos(),
                request.getEmail(),
                request.getPassword(),
                request.getTelefono()
        );
        return new ResponseEntity<>(authUseCase.register(command), HttpStatus.CREATED);
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

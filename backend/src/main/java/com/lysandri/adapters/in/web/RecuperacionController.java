package com.lysandri.adapters.in.web;

import com.lysandri.application.service.RecuperacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RecuperacionController {
    private final RecuperacionService servicio;
    public record Solicitud(@NotBlank @Email @Size(max=150) String email, @Size(max=500) String origen) {}
    public record Cambio(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
                         @NotBlank @Size(min=10, max=72) String password) {}

    @PostMapping("/recuperar-contrasena")
    public Map<String, String> solicitar(@Valid @RequestBody Solicitud solicitud, HttpServletRequest request) {
        return Map.of("mensaje", servicio.solicitar(solicitud.email(), solicitud.origen(), request.getRemoteAddr()));
    }

    @PostMapping("/restablecer-contrasena")
    public Map<String, String> confirmar(@Valid @RequestBody Cambio cambio, HttpServletRequest request) {
        servicio.confirmar(cambio.token(), cambio.password(), request.getRemoteAddr());
        return Map.of("mensaje", "Tu contraseña se actualizó. Ya puedes iniciar sesión con la nueva contraseña.");
    }
}

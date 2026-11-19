package com.lysandri.adapters.in.web;
import com.lysandri.application.service.CuentaService;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/cuenta") @RequiredArgsConstructor
public class CuentaController {
 private final CuentaService cuentas;
 public record Cambio(@NotBlank @Size(max=200) String actual, @NotBlank @Size(min=10,max=72) String nueva) {}
 @PostMapping("/password") @PreAuthorize("isAuthenticated()")
 public Map<String,String> cambiar(@AuthenticationPrincipal UsuarioEntity usuario,@Valid @RequestBody Cambio cambio) {
  if(usuario==null) throw new org.springframework.security.access.AccessDeniedException("Autenticación requerida.");
  cuentas.cambiarPassword(usuario.getIdUser(),cambio.actual(),cambio.nueva());
  return Map.of("mensaje","Contraseña actualizada. Por seguridad, vuelve a iniciar sesión. Se enviará un aviso al correo registrado.");
 }
}

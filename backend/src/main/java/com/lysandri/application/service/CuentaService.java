package com.lysandri.application.service;
import com.lysandri.domain.ports.out.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Service @RequiredArgsConstructor @Slf4j
public class CuentaService {
 private final UserRepositoryPort usuarios;
 private final CuentaPort cuentas;
 private final PasswordEncoder passwords;
 private final NotificationPort notificaciones;
 public void cambiarPassword(long id, String actual, String nueva) {
  if(actual==null || actual.isBlank() || nueva==null || nueva.length()<10 || nueva.getBytes(StandardCharsets.UTF_8).length>72)
   throw new IllegalArgumentException("Introduce la contraseña actual y una nueva de al menos 10 caracteres y hasta 72 bytes.");
  var usuario=usuarios.buscarPorId(id).orElseThrow(()->new IllegalArgumentException("No se pudo autorizar el cambio."));
  if(!usuario.isActivo() || usuario.isBloqueadoAdmin() || usuario.isPendienteActivacion() || !passwords.matches(actual,usuario.getPassw()))
   throw new IllegalArgumentException("No se pudo autorizar el cambio. Revisa la contraseña actual.");
  if(passwords.matches(nueva,usuario.getPassw())) throw new IllegalArgumentException("La nueva contraseña debe ser diferente de la actual.");
  if(!cuentas.cambiarPassword(id,usuario.getPassw(),passwords.encode(nueva))) throw new IllegalStateException("La cuenta cambió durante la solicitud. Vuelve a iniciar sesión.");
 }
 @Scheduled(fixedDelayString="${app.account.mail-delay-ms:5000}")
 public void enviarAvisos() {
  for(var aviso:cuentas.tomarAvisos()) {
   boolean enviado=false;
   try {notificaciones.enviarAvisoCuenta(aviso.correo(),aviso.tipo());enviado=true;}
   catch(Exception e) {log.warn("Entrega del aviso de seguridad {} no confirmada; requiere revisión.",aviso.id());}
   cuentas.confirmarAviso(aviso.id(),enviado);
  }
 }
}

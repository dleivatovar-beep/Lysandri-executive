package com.lysandri.application.service;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.out.*;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CuentaServiceTest {
 UserRepositoryPort usuarios=mock(UserRepositoryPort.class);
 CuentaPort cuentas=mock(CuentaPort.class);
 NotificationPort notificaciones=mock(NotificationPort.class);
 BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
 CuentaService servicio=new CuentaService(usuarios,cuentas,encoder,notificaciones);
 Usuario usuario;
 @BeforeEach void preparar(){
  usuario=Usuario.builder().idUser(7L).activo(true).passw(encoder.encode("AnteriorPrueba2026")).build();
  when(usuarios.buscarPorId(7L)).thenReturn(Optional.of(usuario));
 }
 @Test void cambiaSoloElUsuarioAutorizadoYUsaHash(){
  when(cuentas.cambiarPassword(eq(7L),eq(usuario.getPassw()),anyString())).thenReturn(true);
  servicio.cambiarPassword(7L,"AnteriorPrueba2026","NuevaPrueba2026");
  verify(cuentas).cambiarPassword(eq(7L),eq(usuario.getPassw()),argThat(h->encoder.matches("NuevaPrueba2026",h)));
  verifyNoInteractions(notificaciones);
 }
 @Test void rechazaClaveActualIncorrecta(){
  assertThrows(IllegalArgumentException.class,()->servicio.cambiarPassword(7L,"Incorrecta2026","NuevaPrueba2026"));
  verifyNoInteractions(cuentas,notificaciones);
 }
 @Test void rechazaReutilizarLaClave(){
  assertThrows(IllegalArgumentException.class,()->servicio.cambiarPassword(7L,"AnteriorPrueba2026","AnteriorPrueba2026"));
  verifyNoInteractions(cuentas);
 }
 @Test void rechazaCuentaBloqueada(){
  usuario.setBloqueadoAdmin(true);
  assertThrows(IllegalArgumentException.class,()->servicio.cambiarPassword(7L,"AnteriorPrueba2026","NuevaPrueba2026"));
  verifyNoInteractions(cuentas);
 }
 @Test void validaLongitudEnBytes(){
  assertThrows(IllegalArgumentException.class,()->servicio.cambiarPassword(7L,"AnteriorPrueba2026","corta"));
  assertThrows(IllegalArgumentException.class,()->servicio.cambiarPassword(7L,"AnteriorPrueba2026","é".repeat(37)));
  verifyNoInteractions(cuentas);
 }
 @Test void rechazaConflictoConcurrenteSinDeclararExito(){
  when(cuentas.cambiarPassword(anyLong(),anyString(),anyString())).thenReturn(false);
  assertThrows(IllegalStateException.class,()->servicio.cambiarPassword(7L,"AnteriorPrueba2026","NuevaPrueba2026"));
 }
 @Test void envioConfirmadoYFalloSonEstadosDistintos(){
  when(cuentas.tomarAvisos()).thenReturn(List.of(new CuentaPort.Aviso(1L,"ana@prueba.local","PASSWORD_CAMBIADA"),new CuentaPort.Aviso(2L,"bea@prueba.local","CUENTA_RECUPERADA")));
  doThrow(new IllegalStateException("Sin SMTP")).when(notificaciones).enviarAvisoCuenta("bea@prueba.local","CUENTA_RECUPERADA");
  servicio.enviarAvisos();
  verify(cuentas).confirmarAviso(1L,true);verify(cuentas).confirmarAviso(2L,false);
 }
}

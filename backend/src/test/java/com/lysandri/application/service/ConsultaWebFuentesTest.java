package com.lysandri.application.service;
import com.lysandri.domain.ports.out.WebSearchPort.Resultado;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ConsultaWebFuentesTest {
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings = {"{\"respuesta\":\"Dato\",\"fuentes\":[9]}", "{\"respuesta\":\"Dato\",\"fuentes\":[1.5]}", "{\"respuesta\":\"Dato\",\"fuentes\":[]}", "{\"respuesta\":\"\",\"fuentes\":[]}"})
 void respuestaEstructuradaNoAceptaFuentesInexistentesNiAusenciaDeEvidencia(String contenido) {
  var web = org.mockito.Mockito.mock(com.lysandri.domain.ports.out.WebSearchPort.class);
  var modelo = org.mockito.Mockito.mock(com.lysandri.domain.ports.out.LlmClientPort.class);
  org.mockito.Mockito.when(web.buscar("pregunta general")).thenReturn(List.of(new Resultado("Fuente", "https://ejemplo.test/a", "Dato.")));
  org.mockito.Mockito.when(modelo.completarChat(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(0.0),org.mockito.ArgumentMatchers.eq(256),org.mockito.ArgumentMatchers.anyMap()))
    .thenReturn(new com.lysandri.domain.ports.out.LlmClientPort.LlmCompletion(contenido,0,0,0));
  assertNull(ConsultaWeb.responder("pregunta general",web,modelo));
 }
 @Test void enlaceClaroNoDependeDeLaInterpretacionDelModelo() {
  var web = org.mockito.Mockito.mock(com.lysandri.domain.ports.out.WebSearchPort.class);
  var modelo = org.mockito.Mockito.mock(com.lysandri.domain.ports.out.LlmClientPort.class);
  String pregunta = "la cancion de mike towers de girl donde puedo escucharla";
  var cancion = new Resultado("Girl - Canción de Myke Towers | Spotify", "https://ejemplo.test/cancion", "Escucha Girl en Spotify.");
  var persona = new Resultado("Mike Towers", "https://ejemplo.test/persona", "Perfil profesional.");
  org.mockito.Mockito.when(web.buscar(pregunta)).thenReturn(List.of(cancion, persona, persona, persona));
  var respuesta = ConsultaWeb.responder(pregunta, web, modelo);
  assertNotNull(respuesta);
  assertTrue(respuesta.texto().contains(cancion.url()));
  assertEquals(cancion.url(), respuesta.citas().get(0).url());
  org.mockito.Mockito.verifyNoInteractions(modelo);
 }
 @Test void temaEspecificoPrevaleceSobrePersonasConElMismoNombre() {
  var cancion = new Resultado("Girl - Canción de Myke Towers | Spotify", "https://ejemplo.test/cancion", "Escucha Girl en Spotify.");
  var persona = new Resultado("Mike Towers", "https://ejemplo.test/persona", "Perfil profesional.");
  var otra = new Resultado("Mike Towers", "https://ejemplo.test/otra", "Perfil profesional.");
  var tercera = new Resultado("Mike Towers", "https://ejemplo.test/tercera", "Perfil profesional.");
  assertEquals(List.of(cancion), ConsultaWeb.fuentesPertinentes("la cancion de mike towers de girl donde puedo escucharla", List.of(cancion,persona,otra,tercera)));
 }
 @Test void conservaFuentesCuandoNoHayUnaCoincidenciaTematicaClara() {
  var fuentes = List.of(new Resultado("Fuente oficial", "https://ejemplo.test/a", "Dato confirmado."), new Resultado("Otra fuente", "https://ejemplo.test/b", "Dato confirmado."));
  assertEquals(fuentes, ConsultaWeb.fuentesPertinentes("quién ocupa el papado actualmente?", fuentes));
 }
 @Test void noDescartaResultadosQueCompartenTodosLosTerminos() {
  var fuentes = List.of(new Resultado("Windows y Linux", "https://ejemplo.test/a", "Comparación."), new Resultado("Linux y Windows", "https://ejemplo.test/b", "Comparación."));
  assertEquals(fuentes, ConsultaWeb.fuentesPertinentes("diferencias Windows Linux", fuentes));
 }
}

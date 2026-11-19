package com.lysandri.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.domain.ports.out.RecuperacionPort;
import com.lysandri.security.ActivationCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecuperacionServiceTest {
    private RecuperacionPort repositorio;
    private NotificationPort correos;
    private RecuperacionService servicio;
    private ActivationCryptoService cifrado;
    @TempDir Path carpeta;

    @BeforeEach void preparar() {
        repositorio = mock(RecuperacionPort.class);
        correos = mock(NotificationPort.class);
        cifrado = new ActivationCryptoService("clave-exclusiva-de-pruebas-de-recuperacion-2026");
        servicio = new RecuperacionService(repositorio, new BCryptPasswordEncoder(4), cifrado, correos, new ObjectMapper());
    }

    @Test void respuestaNoRevelaSiExisteElCorreo() {
        when(repositorio.guardarSolicitud(eq("existe@prueba.local"), anyString(), anyString())).thenReturn(true);
        assertEquals(servicio.solicitar("existe@prueba.local", "http://127.0.0.1:3000", "1"),
                servicio.solicitar("ausente@prueba.local", "http://127.0.0.1:3000", "1"));
    }

    @Test void guardaHashYEnlaceCifradoSinDevolverElToken() {
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        var enlace = org.mockito.ArgumentCaptor.forClass(String.class);
        String respuesta = servicio.solicitar(" ADMIN@PRUEBA.LOCAL ", "http://localhost:3000", "1");
        verify(repositorio).guardarSolicitud(eq("admin@prueba.local"), hash.capture(), enlace.capture());
        String plano = cifrado.decrypt(enlace.getValue());
        String token = plano.substring(plano.indexOf("recuperar=") + 10);
        assertEquals(43, token.length());
        assertEquals(RecuperacionService.hash(token), hash.getValue());
        assertFalse(enlace.getValue().contains(token));
        assertFalse(respuesta.contains(token));
    }

    @Test void noAdmiteEnlacesDeUnOrigenNoAutorizado() {
        assertThrows(IllegalArgumentException.class, () -> servicio.solicitar("a@prueba.local", "https://atacante.trycloudflare.com", "1"));
        verifyNoInteractions(repositorio);
    }

    @Test void aceptaSoloElTunelCreadoEnElEquipo() throws Exception {
        Path archivo = carpeta.resolve("tunel.json");
        Files.writeString(archivo, "{\"enlace\":\"https://mi-prueba.trycloudflare.com\"}");
        ReflectionTestUtils.setField(servicio, "archivoTunel", archivo.toString());
        servicio.solicitar("a@prueba.local", "https://mi-prueba.trycloudflare.com", "1");
        verify(repositorio).guardarSolicitud(anyString(), anyString(), anyString());
        assertThrows(IllegalArgumentException.class, () -> servicio.solicitar("a@prueba.local", "https://otro.trycloudflare.com", "1"));
    }

    @Test void limitaSolicitudesSinCambiarElMensajePublico() {
        for (int i=0; i<6; i++) assertEquals(RecuperacionService.MENSAJE, servicio.solicitar("a@prueba.local", null, "1"));
        verify(repositorio, times(3)).guardarSolicitud(anyString(), anyString(), anyString());
    }

    @Test void noPermitePresentarElHashAlmacenadoComoToken() {
        assertThrows(IllegalArgumentException.class, () -> servicio.confirmar("a".repeat(64), "ContrasenaNueva2026", "1"));
        verifyNoInteractions(repositorio);
    }

    @Test void rechazaPasswordsCortasOLargasEnBytes() {
        assertThrows(IllegalArgumentException.class, () -> servicio.confirmar("a".repeat(43), "corta", "1"));
        assertThrows(IllegalArgumentException.class, () -> servicio.confirmar("a".repeat(43), "á".repeat(37), "1"));
        verifyNoInteractions(repositorio);
    }

    @Test void confirmaConBcryptYTokenHasheado() {
        when(repositorio.confirmar(anyString(), anyString())).thenReturn(true);
        servicio.confirmar("a".repeat(43), "ContrasenaNueva2026", "1");
        verify(repositorio).confirmar(eq(RecuperacionService.hash("a".repeat(43))), argThat(v -> new BCryptPasswordEncoder().matches("ContrasenaNueva2026", v)));
    }

    @Test void entregaElCorreoFueraDeLaTransaccionDeReserva() {
        when(repositorio.tomarPendientes()).thenReturn(List.of(new RecuperacionPort.CorreoPendiente(1, "a@prueba.local", "Ana", cifrado.encrypt("http://127.0.0.1:3000/intranet#recuperar=token"))));
        servicio.enviarPendientes();
        var orden = inOrder(repositorio, correos);
        orden.verify(repositorio).tomarPendientes();
        orden.verify(correos).enviarRecuperacion(eq("a@prueba.local"), eq("Ana"), contains("#recuperar="));
        orden.verify(repositorio).registrarEnvio(1, true);
    }

    @Test void falloDeCorreoQuedaParaRevisionSinReintentoAutomatico() {
        when(repositorio.tomarPendientes()).thenReturn(List.of(new RecuperacionPort.CorreoPendiente(1, "a@prueba.local", "Ana", cifrado.encrypt("http://127.0.0.1:3000/intranet#recuperar=token"))));
        doThrow(new IllegalStateException()).when(correos).enviarRecuperacion(anyString(), anyString(), anyString());
        servicio.enviarPendientes();
        verify(repositorio).registrarEnvio(1, false);
    }
}

package com.lysandri.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.domain.ports.out.RecuperacionPort;
import com.lysandri.security.ActivationCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecuperacionService {
    public static final String MENSAJE = "Si el correo corresponde a una cuenta habilitada, recibirás un enlace para cambiar tu contraseña. Revisa también la carpeta de spam.";
    private final RecuperacionPort recuperaciones;
    private final PasswordEncoder passwords;
    private final ActivationCryptoService cifrado;
    private final NotificationPort notificaciones;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Ventana> solicitudes = new ConcurrentHashMap<>();
    private record Ventana(long inicio, int cantidad) {}

    @Value("${app.recovery.frontend-url:http://127.0.0.1:3000}")
    private String frontend = "http://127.0.0.1:3000";
    @Value("${app.recovery.tunnel-state-file:}")
    private String archivoTunel = "";

    public String solicitar(String correo, String origen, String ip) {
        String url = validarOrigen(origen);
        String email = correo.trim().toLowerCase(java.util.Locale.ROOT);
        if (!permitir("ip:" + ip, 30) || !permitir("correo:" + hash(email), 3)) return MENSAJE;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        recuperaciones.guardarSolicitud(email, hash(token), cifrado.encrypt(url + "/intranet#recuperar=" + token));
        return MENSAJE;
    }

    public void confirmar(String token, String password, String ip) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")
                || password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Usa un enlace válido y una contraseña de al menos 10 caracteres y hasta 72 bytes.");
        }
        if (!permitir("confirmacion:" + ip, 15)) throw new IllegalArgumentException("Espera unos minutos antes de volver a intentarlo.");
        if (!recuperaciones.confirmar(hash(token), passwords.encode(password))) {
            throw new IllegalArgumentException("El enlace no es válido, ya se utilizó o ha vencido. Solicita uno nuevo.");
        }
    }

    @Scheduled(fixedDelayString = "${app.recovery.mail-delay-ms:5000}")
    public void enviarPendientes() {
        for (var correo : recuperaciones.tomarPendientes()) {
            boolean enviado = false;
            try {
                notificaciones.enviarRecuperacion(correo.correo(), correo.nombre(), cifrado.decrypt(correo.enlaceCifrado()));
                enviado = true;
            } catch (Exception error) {
                log.warn("No se confirmó la entrega del correo de recuperación {}. Puede solicitarse otro enlace.", correo.id());
            }
            // Un envío incierto no se repite automáticamente: podría haber llegado al destinatario.
            recuperaciones.registrarEnvio(correo.id(), enviado);
        }
    }

    private synchronized boolean permitir(String clave, int maximo) {
        long ahora = System.currentTimeMillis();
        if (solicitudes.size() >= 10000) solicitudes.entrySet().removeIf(e -> ahora - e.getValue().inicio() > 900000);
        Ventana anterior = solicitudes.get(clave);
        if (anterior == null && solicitudes.size() >= 10000) return false;
        if (anterior == null || ahora - anterior.inicio() > 900000) anterior = new Ventana(ahora, 0);
        if (anterior.cantidad() >= maximo) return false;
        solicitudes.put(clave, new Ventana(anterior.inicio(), anterior.cantidad() + 1));
        return true;
    }

    private String validarOrigen(String recibido) {
        String autorizado = origen(frontend);
        String solicitado = recibido == null || recibido.isBlank() ? autorizado : origen(recibido);
        if (solicitado.equals(autorizado)) return solicitado;
        URI base = URI.create(autorizado);
        URI destino = URI.create(solicitado);
        if (java.util.Set.of("localhost", "127.0.0.1").contains(base.getHost())
                && java.util.Set.of("localhost", "127.0.0.1").contains(destino.getHost())
                && base.getPort() == destino.getPort() && base.getScheme().equals(destino.getScheme())) return solicitado;
        if (!archivoTunel.isBlank()) {
            try {
                String enlace = mapper.readTree(Files.readString(Path.of(archivoTunel))).path("enlace").asText();
                URI tunel = URI.create(origen(enlace));
                if ("https".equals(tunel.getScheme()) && tunel.getHost().endsWith(".trycloudflare.com")
                        && solicitado.equals(tunel.toString())) return solicitado;
            } catch (Exception ignored) { /* Solo se admiten enlaces creados localmente por el coordinador. */ }
        }
        throw new IllegalArgumentException("Abre la recuperación desde la dirección autorizada de la plataforma.");
    }

    private String origen(String valor) {
        try {
            URI uri = URI.create(valor);
            if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) throw new IllegalArgumentException();
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (Exception error) { throw new IllegalArgumentException("Dirección de recuperación no válida."); }
    }

    public static String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}

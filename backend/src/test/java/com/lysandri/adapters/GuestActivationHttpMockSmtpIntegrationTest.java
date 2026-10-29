package com.lysandri.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.adapters.in.web.dto.CreateOrderRequest;
import com.lysandri.domain.ports.in.AuthUseCase;
import com.lysandri.domain.ports.out.LmsClientPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GuestActivationHttpMockSmtpIntegrationTest extends PostgresTestDatabase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LmsClientPort lmsClientPort;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static SimpleMockSmtpServer smtpServer;

    @BeforeAll
    static void startMockSmtp() throws IOException {
        smtpServer = new SimpleMockSmtpServer(1025);
    }

    @AfterAll
    static void stopMockSmtp() {
        if (smtpServer != null) {
            smtpServer.close();
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void setupTestData() {
        jdbcTemplate.update("""
                INSERT INTO usuarios (id_user, nombres, apellidos, email, passw, rol)
                VALUES (99, 'Profesor', 'Test', 'profesor.guesttest@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR')
                ON CONFLICT (id_user) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO usuario (id_user, nombres, apellidos, email, passw, rol, activo)
                VALUES (99, 'Profesor', 'Test', 'profesor.guesttest@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR', true)
                ON CONFLICT (id_user) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO instructor (id_instructor_dni, id_user, especialidad, direccion_instructor)
                VALUES ('DNI88888888', 99, 'Liderazgo', 'Av. Central 456')
                ON CONFLICT (id_instructor_dni) DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO programa (id_programa, id_instructor_dni, titulo, precio, moneda, activo, moodle_course_id, slug, duracion_programa, tipo_programa, nivel, precio_aprobado)
                VALUES (101, 'DNI88888888', 'Liderazgo Ejecutivo', 499.00, 'USD', true, 101, 'liderazgo-ejecutivo-test', '30 horas', 'CURSO', 'EJECUTIVO', true)
                ON CONFLICT (id_programa) DO UPDATE SET precio_aprobado = true, activo = true
                """);
    }

    @Test
    @DisplayName("Flujo completo E2E: Checkout invitado -> Correo SMTP local -> Enlace activación -> Establecer contraseña -> Login OK")
    void testFlujoCompletoCheckoutInvitadoActivacionYLogin() throws Exception {
        // 1. Configurar LMS mock para simular éxito en Moodle
        when(lmsClientPort.buscarUsuarioPorEmail(anyString())).thenReturn(Optional.empty());
        when(lmsClientPort.crearUsuario(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(9901L);
        when(lmsClientPort.estaUsuarioMatriculado(anyLong(), anyLong())).thenReturn(false);

        smtpServer.clear();

        // 2. Checkout como invitado (POST /api/v1/orders/checkout)
        String guestEmail = "guest_e2e_" + UUID.randomUUID() + "@lysandri.test";
        String guestName = "Laura E2E Guest";

        CreateOrderRequest checkoutReq = new CreateOrderRequest();
        checkoutReq.setProgramaIds(List.of(101L));
        checkoutReq.setEmail(guestEmail);
        checkoutReq.setNombreCompleto(guestName);
        checkoutReq.setTipoComprobante("BOLETA");
        checkoutReq.setNumeroDocumento("74859612");
        checkoutReq.setNombreFacturacion(guestName);
        checkoutReq.setMetodoPago("STRIPE");

        // Obtener token oficial previo de sesión de compra de invitado
        String sessionRes = mockMvc.perform(post("/api/v1/orders/guest-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + guestEmail + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String guestToken = objectMapper.readTree(sessionRes).get("guestSessionToken").asText();

        String checkoutResponseJson = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("X-Guest-Session-Token", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkoutReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode checkoutNode = objectMapper.readTree(checkoutResponseJson);
        String sessionId = checkoutNode.hasNonNull("stripeSessionId")
                ? checkoutNode.get("stripeSessionId").asText()
                : (checkoutNode.hasNonNull("sessionId") ? checkoutNode.get("sessionId").asText() : checkoutNode.get("codigoOrden").asText());
        assertNotNull(sessionId, "La respuesta de checkout debe incluir sessionId o identificador de orden");

        // 3. Confirmar pago (POST /api/v1/orders/confirm/{sessionId})
        mockMvc.perform(post("/api/v1/orders/confirm/" + sessionId))
                .andExpect(status().isOk());

        // 4. Capturar correo de activación en el servidor SMTP mock local
        String activationEmailBody = smtpServer.waitForActivationEmail(guestEmail, 10, TimeUnit.SECONDS);
        assertNotNull(activationEmailBody, "El servidor SMTP mock debe haber recibido el correo de activación");
        assertTrue(activationEmailBody.contains("Subject: Activa tu cuenta en Lysandri Executive Education")
                || activationEmailBody.contains("Subject: =?UTF-8?Q?Activa_tu_cuenta_en_Lysandri_Executive_Education?="));

        // 5. Extraer enlace y token de activación directamente del cuerpo del correo SMTP
        String decodedBody = activationEmailBody.replace("=3D", "=").replace("=\r\n", "").replace("=\n", "");
        Pattern linkPattern = Pattern.compile("http://localhost:5173/activar-cuenta\\?token=([a-zA-Z0-9_-]+)");
        Matcher matcher = linkPattern.matcher(decodedBody);
        assertTrue(matcher.find(), "El correo recibido debe contener el enlace de activación configurado");
        String plainToken = matcher.group(1);
        assertNotNull(plainToken, "El token de activación debe estar presente en el enlace");
        assertFalse(plainToken.isBlank());

        // 6. Activar la cuenta estableciendo una nueva contraseña (POST /api/v1/auth/activar-cuenta)
        String newPassword = "GuestStrongPassword2026!";
        com.lysandri.adapters.in.web.dto.ActivarCuentaRequest activarReq =
                new com.lysandri.adapters.in.web.dto.ActivarCuentaRequest(plainToken, newPassword);

        mockMvc.perform(post("/api/v1/auth/activar-cuenta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activarReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(guestEmail));

        // 7. Iniciar sesión con las nuevas credenciales (POST /api/v1/auth/login)
        AuthUseCase.LoginCommand loginCmd = new AuthUseCase.LoginCommand(guestEmail, newPassword);

        String loginResponseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginCmd)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode loginNode = objectMapper.readTree(loginResponseJson);
        String jwtToken = loginNode.get("token").asText();
        assertNotNull(jwtToken, "El login exitoso debe retornar un JWT token válido");
        assertEquals(guestEmail, loginNode.get("email").asText());

        // 8. Consultar perfil con el JWT obtenido (GET /api/v1/auth/me)
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(guestEmail))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    @DisplayName("Flujo de solicitud de nuevo enlace: respuesta uniforme y límites de tasa (Rate Limiting)")
    void testSolicitarNuevoEnlaceActivacionYRateLimit() throws Exception {
        // 1. Correo no existente: respuesta uniforme idéntica sin revelar existencia
        String nonExistentEmail = "nonexistent_" + UUID.randomUUID() + "@lysandri.test";
        AuthUseCase.SolicitarActivacionCommand cmdNoExistente = new AuthUseCase.SolicitarActivacionCommand(nonExistentEmail);

        mockMvc.perform(post("/api/v1/auth/solicitar-activacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmdNoExistente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Si el correo electrónico corresponde a una cuenta pendiente de activación, se ha enviado un enlace a su bandeja de entrada."));

        // 2. Correo existente bajo rate limiting: máximo 3 solicitudes por ventana de 15 minutos
        String rateLimitEmail = "ratelimit_" + UUID.randomUUID() + "@lysandri.test";
        AuthUseCase.SolicitarActivacionCommand rateLimitCmd = new AuthUseCase.SolicitarActivacionCommand(rateLimitEmail);

        // Intentos 1, 2 y 3 permitidos
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/solicitar-activacion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(rateLimitCmd)))
                    .andExpect(status().isOk());
        }

        // Intento 4 bloqueado por exceso de tasa
        mockMvc.perform(post("/api/v1/auth/solicitar-activacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rateLimitCmd)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Ha excedido el límite de solicitudes de activación. Por favor, intente nuevamente en 15 minutos."));
    }

    /**
     * Servidor SMTP mock ligero y no intrusivo en memoria.
     */
    static class SimpleMockSmtpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final List<String> receivedMessages = new CopyOnWriteArrayList<>();
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private volatile boolean running = true;

        public SimpleMockSmtpServer(int port) throws IOException {
            this.serverSocket = new ServerSocket(port);
            executor.submit(this::listen);
        }

        private void listen() {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handleClient(socket));
                } catch (IOException e) {
                    if (!running) break;
                }
            }
        }

        private void handleClient(Socket socket) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                writer.write("220 localhost SimpleMockSmtpServer ready\r\n");
                writer.flush();

                StringBuilder currentData = new StringBuilder();
                boolean readingData = false;

                String line;
                while ((line = reader.readLine()) != null) {
                    if (readingData) {
                        if (".".equals(line)) {
                            readingData = false;
                            receivedMessages.add(currentData.toString());
                            writer.write("250 2.0.0 OK: message queued\r\n");
                            writer.flush();
                        } else {
                            currentData.append(line).append("\n");
                        }
                    } else {
                        String upper = line.toUpperCase(Locale.ROOT);
                        if (upper.startsWith("HELO") || upper.startsWith("EHLO")) {
                            writer.write("250-localhost Hello\r\n250 OK\r\n");
                            writer.flush();
                        } else if (upper.startsWith("MAIL FROM:")) {
                            writer.write("250 2.1.0 OK\r\n");
                            writer.flush();
                        } else if (upper.startsWith("RCPT TO:")) {
                            writer.write("250 2.1.5 OK\r\n");
                            writer.flush();
                        } else if (upper.equals("DATA")) {
                            readingData = true;
                            currentData.setLength(0);
                            writer.write("354 Start mail input; end with <CRLF>.<CRLF>\r\n");
                            writer.flush();
                        } else if (upper.equals("QUIT")) {
                            writer.write("221 2.0.0 Bye\r\n");
                            writer.flush();
                            break;
                        } else if (upper.startsWith("RSET") || upper.startsWith("NOOP")) {
                            writer.write("250 OK\r\n");
                            writer.flush();
                        } else {
                            writer.write("250 OK\r\n");
                            writer.flush();
                        }
                    }
                }
            } catch (IOException ignored) {
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        public String waitForActivationEmail(String recipient, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
            while (System.currentTimeMillis() < deadline) {
                for (String msg : receivedMessages) {
                    if (msg.contains(recipient) && msg.contains("activar-cuenta")) {
                        return msg;
                    }
                }
                Thread.sleep(100);
            }
            return null;
        }

        public void clear() {
            receivedMessages.clear();
        }

        @Override
        public void close() {
            running = false;
            try { serverSocket.close(); } catch (IOException ignored) {}
            executor.shutdownNow();
        }
    }
}

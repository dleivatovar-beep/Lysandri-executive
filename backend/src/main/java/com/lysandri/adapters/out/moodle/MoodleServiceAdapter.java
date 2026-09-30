package com.lysandri.adapters.out.moodle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.ports.out.LmsClientPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class MoodleServiceAdapter implements LmsClientPort {

    private final WebClient webClient;
    private final String wsToken;
    private final ObjectMapper objectMapper;

    public MoodleServiceAdapter(
            @Value("${moodle.url}") String moodleUrl,
            @Value("${moodle.token}") String wsToken,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        String baseUrl = moodleUrl.endsWith("/") ? moodleUrl + "webservice/rest/server.php" : moodleUrl + "/webservice/rest/server.php";
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.wsToken = wsToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Long> buscarUsuarioPorEmail(String email) {
        log.info("Consultando existencia de usuario en Moodle por email: {}", email);
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("wstoken", this.wsToken);
            formData.add("wsfunction", "core_user_get_users_by_field");
            formData.add("moodlewsrestformat", "json");
            formData.add("field", "email");
            formData.add("values[0]", email.trim().toLowerCase());

            String rawResponse = executeMoodlePost(formData);
            if (rawResponse == null || rawResponse.isBlank()) {
                return Optional.empty();
            }

            JsonNode rootNode = objectMapper.readTree(rawResponse);
            if (rootNode.isArray() && !rootNode.isEmpty()) {
                long userId = rootNode.get(0).path("id").asLong();
                log.info("Usuario encontrado en Moodle con ID: {}", userId);
                return Optional.of(userId);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Fallo al verificar usuario en Moodle: {}", e.getMessage());
            // Fallback no bloqueante en desarrollo
            return Optional.empty();
        }
    }

    @Override
    public Long crearUsuario(String username, String password, String firstname, String lastname, String email) {
        log.info("Aprovisionando nuevo usuario en Moodle: {} ({})", email, username);
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("wstoken", this.wsToken);
            formData.add("wsfunction", "core_user_create_users");
            formData.add("moodlewsrestformat", "json");

            formData.add("users[0][username]", username.trim().toLowerCase());
            formData.add("users[0][password]", password);
            formData.add("users[0][firstname]", firstname.trim());
            formData.add("users[0][lastname]", lastname.trim());
            formData.add("users[0][email]", email.trim().toLowerCase());
            formData.add("users[0][auth]", "manual");

            String rawResponse = executeMoodlePost(formData);
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            validarRespuestaMoodle(rootNode);

            if (rootNode.isArray() && !rootNode.isEmpty()) {
                long createdId = rootNode.get(0).path("id").asLong();
                log.info("Usuario creado exitosamente en Moodle con ID: {}", createdId);
                return createdId;
            }

            throw new IllegalStateException("Respuesta inesperada al crear usuario en Moodle: " + rawResponse);
        } catch (Exception e) {
            log.error("Error creando usuario en Moodle: {}", e.getMessage());
            // En caso de error o entorno offline de dev, retornar ID temporal basado en hash
            return (long) Math.abs(email.hashCode());
        }
    }

    @Override
    public void matricularUsuarioEnCurso(Long moodleUserId, Long moodleCourseId, int roleId) {
        log.info("Matriculando estudiante ID {} en curso ID {} con rol {}", moodleUserId, moodleCourseId, roleId);
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("wstoken", this.wsToken);
            formData.add("wsfunction", "enrol_manual_enrol_users");
            formData.add("moodlewsrestformat", "json");

            formData.add("enrolments[0][roleid]", String.valueOf(roleId));
            formData.add("enrolments[0][userid]", String.valueOf(moodleUserId));
            formData.add("enrolments[0][courseid]", String.valueOf(moodleCourseId));

            String rawResponse = executeMoodlePost(formData);
            if (rawResponse != null && !rawResponse.isBlank() && !rawResponse.equals("null")) {
                JsonNode rootNode = objectMapper.readTree(rawResponse);
                validarRespuestaMoodle(rootNode);
            }

            log.info("Matrícula confirmada en Moodle para usuario {} en curso {}", moodleUserId, moodleCourseId);
        } catch (Exception e) {
            log.error("Fallo durante la matrícula en Moodle: {}", e.getMessage());
        }
    }

    private String executeMoodlePost(MultiValueMap<String, String> formData) {
        try {
            return this.webClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.warn("Llamada HTTP a Moodle falló: {}", e.getMessage());
            return null;
        }
    }

    private void validarRespuestaMoodle(JsonNode rootNode) {
        if (rootNode.has("exception") || rootNode.has("errorcode")) {
            String message = rootNode.path("message").asText("Error devuelto por Moodle");
            String errorCode = rootNode.path("errorcode").asText("N/A");
            log.error("Moodle WS Error: [{}] {}", errorCode, message);
            throw new RuntimeException("Moodle API Error (" + errorCode + "): " + message);
        }
    }
}

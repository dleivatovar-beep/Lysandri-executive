package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ChatRequest;
import com.lysandri.adapters.out.database.entity.ChatSessionEntity;
import com.lysandri.adapters.out.database.entity.HistorialConsultaEntity;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.adapters.out.database.repository.SpringDataChatSessionRepository;
import com.lysandri.adapters.out.database.repository.SpringDataHistorialRepository;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.in.ChatRagUseCase;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/chat", "/api/chat"})
@RequiredArgsConstructor
@Tag(name = "Asistente Virtual RAG", description = "Consultas semánticas a los manuales y sílabos con pgvector")
public class ChatController {

    private final ChatRagUseCase chatRagUseCase;
    private final ProgramRepositoryPort programRepository;
    private final SpringDataHistorialRepository historialRepository;
    private final SpringDataChatSessionRepository chatSessionRepository;

    @PostMapping({"", "/", "/rag"})
    @Operation(summary = "Realiza una consulta técnica o curricular al Asistente Inteligente")
    public ResponseEntity<Map<String, Object>> consultar(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @RequestHeader(value = "X-Chat-Session-Token", required = false) String headerSessionToken,
            @RequestBody ChatRequest request) {

        String consulta = request.obtenerTextoConsulta();
        if (consulta.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "La pregunta o mensaje no puede estar vacío"
            ));
        }

        if (consulta.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "La pregunta o mensaje no puede exceder los 1000 caracteres"
            ));
        }

        Long authUserId = usuario != null ? usuario.getIdUser() : null;

        // Protección contra suplantación IDOR: no se confía en idUsuario enviado por cliente
        if (request.getIdUsuario() != null) {
            if (authUserId == null || !request.getIdUsuario().equals(authUserId)) {
                log.warn("Violación IDOR en Chat: Cliente envió idUsuario={} pero usuario autenticado es={}",
                        request.getIdUsuario(), authUserId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "No está autorizado a enviar consultas en nombre de otro usuario"
                ));
            }
        }

        // Validación de existencia y disponibilidad del programa académico consultado
        if (request.getIdPrograma() != null) {
            if (request.getIdPrograma() <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "El ID del programa debe ser un número positivo"
                ));
            }

            Optional<Programa> programaOpt = programRepository.buscarPorId(request.getIdPrograma());
            if (programaOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "El programa especificado no existe"
                ));
            }

            Programa programa = programaOpt.get();
            if (!programa.isActivo() || !programa.isPrecioAprobado()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "El programa especificado no está activo ni disponible para consultas"
                ));
            }
        }

        String effectiveToken = (request.getSessionToken() != null && !request.getSessionToken().isBlank())
                ? request.getSessionToken().trim()
                : (headerSessionToken != null && !headerSessionToken.isBlank() ? headerSessionToken.trim() : null);

        String sesionId = request.getSesionId();
        String returnToken;

        if (sesionId != null && !sesionId.isBlank()) {
            sesionId = sesionId.trim();
            if (sesionId.length() > 64) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "El identificador de sesión excede la longitud máxima permitida"
                ));
            }

            Optional<ChatSessionEntity> sessionOpt = chatSessionRepository.findByIdSesion(sesionId);
            if (sessionOpt.isPresent()) {
                ChatSessionEntity session = sessionOpt.get();

                if (session.getIdUser() != null) {
                    // Sesión registrada a nombre de un usuario autenticado
                    if (authUserId == null || !authUserId.equals(session.getIdUser())) {
                        log.warn("Violación de propiedad de sesión conversacional: sesionId={} pertenece a usuario={} pero fue invocada por={}",
                                sesionId, session.getIdUser(), authUserId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                                "error", "No autorizado para interactuar con esta sesión de conversación"
                        ));
                    }
                    if (effectiveToken != null && !hashToken(effectiveToken).equals(session.getSessionTokenHash())) {
                        log.warn("Token de sesión inválido para usuario={} en sesionId={}", authUserId, sesionId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                                "error", "Token de sesión conversacional inválido"
                        ));
                    }
                } else {
                    // Sesión anónima: no se permite suplantación por usuario autenticado ni hijacking sin token
                    if (authUserId != null) {
                        log.warn("Intento de reutilizar sesión anónima sesionId={} por usuario autenticado={}", sesionId, authUserId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                                "error", "No está permitido transferir sesiones anónimas a cuentas autenticadas. Inicie una nueva conversación."
                        ));
                    }
                    if (effectiveToken == null || !hashToken(effectiveToken).equals(session.getSessionTokenHash())) {
                        log.warn("Violación de token para sesión anónima sesionId={}", sesionId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                                "error", "Token de sesión anónima inválido o ausente"
                        ));
                    }
                }

                session.setUltimaActividad(OffsetDateTime.now());
                chatSessionRepository.save(session);
                returnToken = effectiveToken;
            } else {
                // Sesión no existente previamente en tabla chat_session
                returnToken = generateSecureToken();
                ChatSessionEntity newSession = ChatSessionEntity.builder()
                        .idSesion(sesionId)
                        .idUser(authUserId)
                        .sessionTokenHash(hashToken(returnToken))
                        .fechaCreacion(OffsetDateTime.now())
                        .ultimaActividad(OffsetDateTime.now())
                        .build();
                try {
                    chatSessionRepository.save(newSession);
                } catch (DataIntegrityViolationException ex) {
                    log.warn("Colisión concurrente creando chat_session {}: {}", sesionId, ex.getMessage());
                }
            }
        } else {
            sesionId = UUID.randomUUID().toString();
            returnToken = generateSecureToken();
            ChatSessionEntity newSession = ChatSessionEntity.builder()
                    .idSesion(sesionId)
                    .idUser(authUserId)
                    .sessionTokenHash(hashToken(returnToken))
                    .fechaCreacion(OffsetDateTime.now())
                    .ultimaActividad(OffsetDateTime.now())
                    .build();
            chatSessionRepository.save(newSession);
        }

        ChatRagUseCase.ChatRagCommand command = new ChatRagUseCase.ChatRagCommand(
                consulta,
                sesionId,
                authUserId,
                request.getIdPrograma()
        );

        ChatRagUseCase.ChatRagResponse response = chatRagUseCase.procesarConsulta(command);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("respuesta", response.respuesta());
        responseBody.put("mensaje", response.respuesta());
        responseBody.put("fuentes", response.fuentes());
        responseBody.put("sources", response.fuentes());
        responseBody.put("tokensUsados", response.tokensUsados());
        responseBody.put("latenciaMs", response.latenciaMs());
        responseBody.put("sesionId", sesionId);
        if (returnToken != null) {
            responseBody.put("sessionToken", returnToken);
        }

        return ResponseEntity.ok(responseBody);
    }

    private String generateSecureToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private String hashToken(String token) {
        if (token == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}

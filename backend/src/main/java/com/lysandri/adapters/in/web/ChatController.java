package com.lysandri.adapters.in.web;

import com.lysandri.adapters.in.web.dto.ChatRequest;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.ports.in.ChatRagUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/chat", "/api/chat"})
@RequiredArgsConstructor
@Tag(name = "Asistente Virtual RAG", description = "Consultas semánticas a los manuales y sílabos con pgvector")
public class ChatController {

    private final ChatRagUseCase chatRagUseCase;

    @PostMapping({"", "/", "/rag"})
    @Operation(summary = "Realiza una consulta técnica o curricular al Asistente Inteligente")
    public ResponseEntity<Map<String, Object>> consultar(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @RequestBody ChatRequest request) {

        String consulta = request.obtenerTextoConsulta();
        if (consulta.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "La pregunta o mensaje no puede estar vacío"
            ));
        }

        String sesionId = (request.getSesionId() != null && !request.getSesionId().isBlank())
                ? request.getSesionId()
                : UUID.randomUUID().toString();

        Long idUsuario = usuario != null ? usuario.getIdUser() : null;

        ChatRagUseCase.ChatRagCommand command = new ChatRagUseCase.ChatRagCommand(
                consulta,
                sesionId,
                idUsuario,
                request.getIdPrograma()
        );

        ChatRagUseCase.ChatRagResponse response = chatRagUseCase.procesarConsulta(command);

        // Mapeo con alias duales para total compatibilidad con cualquier vista frontend
        Map<String, Object> responseBody = Map.of(
                "respuesta", response.respuesta(),
                "mensaje", response.respuesta(),
                "fuentes", response.fuentes(),
                "sources", response.fuentes(),
                "tokensUsados", response.tokensUsados(),
                "latenciaMs", response.latenciaMs(),
                "sesionId", sesionId
        );

        return ResponseEntity.ok(responseBody);
    }
}

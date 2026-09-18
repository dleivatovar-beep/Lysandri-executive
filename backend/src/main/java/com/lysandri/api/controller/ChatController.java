package com.lysandri.api.controller;

import com.lysandri.api.dto.request.ChatRequest;
import com.lysandri.api.dto.response.ChatResponse;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chatbot RAG", description = "Endpoints para interacción con el Asistente IA RAG y consulta de base de conocimiento")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(
            summary = "Procesar consulta de chat",
            description = "Recibe la consulta del usuario autenticado, recupera contexto documental de vector store y retorna la respuesta con fuentes fundamentadas",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ChatResponse> procesarMensaje(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal Usuario usuario
    ) {
        ChatResponse response = chatService.procesarMensaje(request, usuario);
        return ResponseEntity.ok(response);
    }
}

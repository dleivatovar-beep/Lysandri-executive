package com.lysandri.api.service;

import com.lysandri.api.dto.request.ChatRequest;
import com.lysandri.api.dto.response.ChatResponse;
import com.lysandri.api.model.entity.Usuario;

public interface ChatService {

    /**
     * Procesa la consulta del usuario, consulta fuentes documentales o contextuales,
     * registra la interacción en el historial del chat y genera la respuesta fundamentada.
     *
     * @param request Datos de la consulta enviada por el usuario
     * @param usuario Usuario autenticado que emite la petición
     * @return ChatResponse con la respuesta generada, fuentes y timestamp
     */
    ChatResponse procesarMensaje(ChatRequest request, Usuario usuario);
}

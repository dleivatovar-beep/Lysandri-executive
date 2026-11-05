package com.lysandri.adapters.in.web.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String pregunta;
    private String mensaje; // Alias para compatibilidad con clientes existentes
    private String sesionId;
    private String sessionToken;
    private Long idPrograma;
    private Long idUsuario; // Protegido: el backend valida contra la sesión autenticada

    public String obtenerTextoConsulta() {
        if (pregunta != null && !pregunta.isBlank()) {
            return pregunta.trim();
        }
        if (mensaje != null && !mensaje.isBlank()) {
            return mensaje.trim();
        }
        return "";
    }
}

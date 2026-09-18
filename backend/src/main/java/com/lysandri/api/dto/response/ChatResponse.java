package com.lysandri.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "DTO de respuesta devuelta por el Asistente IA RAG")
public class ChatResponse {

    @Schema(description = "Contenido de la respuesta redactada por el asistente IA", example = "El ROI de migrar a Kafka se fundamenta en desacoplamiento de servicios y reducción de latencia...")
    private String respuesta;

    @Schema(description = "Lista de fuentes y documentos indexados que fundamentan la respuesta", example = "[\"Manual de Arquitectura Cloud Lysandri\", \"Playbook FinOps & EKS\"]")
    private List<String> fuentes;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Fecha y hora en que se emitió la respuesta", example = "2026-09-17T11:55:00")
    private LocalDateTime fechaEnvio;
}

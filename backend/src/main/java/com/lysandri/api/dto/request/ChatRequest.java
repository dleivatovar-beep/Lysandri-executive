package com.lysandri.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "DTO de solicitud para interacción con el Asistente IA RAG")
public class ChatRequest {

    @NotBlank(message = "El mensaje no puede estar vacío")
    @Schema(description = "Consulta o mensaje enviado por el usuario", example = "¿Cuál es el ROI de migrar a arquitectura orientada a eventos con Kafka?")
    private String mensaje;

    @Schema(description = "Identificador opcional del programa para contextualizar la búsqueda", example = "1")
    private Long programaId;
}

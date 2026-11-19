package com.lysandri.adapters.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.application.service.DocumentoService;
import com.lysandri.domain.ports.out.LlmClientPort;
import com.lysandri.exception.RagUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiLlmAdapter implements LlmClientPort {
    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String embeddingModel;
    private final ObjectMapper mapper;

    public OpenAiLlmAdapter(@Value("${llm.api-key:}") String apiKey,
                            @Value("${llm.model:gpt-4o-mini}") String model,
                            @Value("${llm.embedding-model:text-embedding-3-small}") String embeddingModel,
                            WebClient.Builder builder, ObjectMapper mapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.mapper = mapper;
        this.webClient = builder.clone().baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey).build();
    }

    private void comprobarConfiguracion() {
        if (apiKey.isBlank() || apiKey.startsWith("mock")) {
            throw new RagUnavailableException("El asistente necesita un proveedor de IA configurado. Configure OPENAI_API_KEY en el backend.");
        }
        if (!"text-embedding-3-small".equals(embeddingModel)) {
            throw new RagUnavailableException("El índice RAG requiere text-embedding-3-small y 1536 dimensiones.");
        }
    }

    @Override
    public float[] generarEmbedding(String texto) {
        comprobarConfiguracion();
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("El texto no puede estar vacío.");
        try {
            JsonNode root = solicitar("/embeddings", Map.of("input", texto, "model", embeddingModel,
                    "dimensions", 1536, "encoding_format", "float"), 15);
            JsonNode datos = root.path("data").path(0).path("embedding");
            if (!datos.isArray()) throw new IllegalArgumentException("Respuesta de embeddings inválida.");
            float[] vector = new float[datos.size()];
            for (int i = 0; i < vector.length; i++) {
                if (!datos.get(i).isNumber()) throw new IllegalArgumentException("Vector inválido.");
                vector[i] = (float) datos.get(i).asDouble();
            }
            DocumentoService.validarVector(vector);
            return vector;
        } catch (Exception ex) {
            throw new RagUnavailableException("No se pudo obtener el vector de la consulta o del documento. Revise el proveedor de IA.");
        }
    }

    @Override
    public LlmCompletion completarChat(String systemPrompt, String userPrompt, double temperatura) {
        comprobarConfiguracion();
        try {
            JsonNode root = solicitar("/chat/completions", Map.of("model", model, "temperature", temperatura,
                    "messages", List.of(Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt))), 35);
            String respuesta = root.path("choices").path(0).path("message").path("content").asText("");
            if (respuesta.isBlank()) throw new IllegalArgumentException("Respuesta vacía.");
            JsonNode uso = root.path("usage");
            return new LlmCompletion(respuesta, uso.path("prompt_tokens").asInt(),
                    uso.path("completion_tokens").asInt(), uso.path("total_tokens").asInt());
        } catch (Exception ex) {
            throw new RagUnavailableException("El servicio de IA no pudo generar una respuesta. Inténtelo nuevamente.");
        }
    }

    private JsonNode solicitar(String ruta, Object cuerpo, int segundos) throws Exception {
        String respuesta = webClient.post().uri(ruta).contentType(MediaType.APPLICATION_JSON).bodyValue(cuerpo)
                .retrieve().bodyToMono(String.class).timeout(Duration.ofSeconds(segundos)).block();
        return mapper.readTree(respuesta);
    }
}

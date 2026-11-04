package com.lysandri.adapters.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.ports.out.LlmClientPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
public class OpenAiLlmAdapter implements LlmClientPort {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String embeddingModel;
    private final boolean mockAllowed;
    private final ObjectMapper objectMapper;

    public OpenAiLlmAdapter(
            @Value("${llm.api-key:mock-key}") String apiKey,
            @Value("${llm.model:gpt-4o-mini}") String model,
            @Value("${llm.embedding-model:text-embedding-3-small}") String embeddingModel,
            @Value("${llm.mock-allowed:false}") boolean mockAllowed,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model;
        this.embeddingModel = embeddingModel;
        this.mockAllowed = mockAllowed;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
                .build();
    }

    private boolean isMockKey() {
        return apiKey.isBlank() || apiKey.startsWith("mock");
    }

    @Override
    public float[] generarEmbedding(String texto) {
        if (isMockKey()) {
            if (!mockAllowed) {
                log.error("CONFIGURACIÓN LLM INSEGURA: Se intentó generar embeddings con mock-key pero llm.mock-allowed está deshabilitado en este entorno.");
                throw new IllegalStateException("El proveedor de LLM no está configurado para producción. Configure OPENAI_API_KEY o active llm.mock-allowed=true exclusivamente en entornos de prueba.");
            }
            log.debug("Generando vector determinístico simulado (entorno de desarrollo/prueba) para texto longitud: {}", texto != null ? texto.length() : 0);
            return generarEmbeddingDeterministico(texto, 1536);
        }

        try {
            Map<String, Object> body = Map.of(
                    "input", texto != null ? texto : "",
                    "model", embeddingModel
            );

            String responseJson = webClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode embeddingArray = root.path("data").get(0).path("embedding");

            float[] vector = new float[embeddingArray.size()];
            for (int i = 0; i < embeddingArray.size(); i++) {
                vector[i] = (float) embeddingArray.get(i).asDouble();
            }
            return vector;
        } catch (Exception e) {
            log.error("Fallo llamada a OpenAI Embeddings: {}", e.getMessage(), e);
            if (mockAllowed) {
                log.warn("Usando fallback de embedding local debido a fallo de red en entorno de prueba.");
                return generarEmbeddingDeterministico(texto, 1536);
            }
            throw new IllegalStateException("Error de comunicación con el servicio de embeddings de OpenAI: " + e.getMessage(), e);
        }
    }

    @Override
    public LlmCompletion completarChat(String systemPrompt, String userPrompt, double temperatura) {
        if (isMockKey()) {
            if (!mockAllowed) {
                log.error("CONFIGURACIÓN LLM INSEGURA: Se intentó completar chat con mock-key pero llm.mock-allowed está deshabilitado.");
                throw new IllegalStateException("El proveedor de LLM no está configurado para producción. Configure OPENAI_API_KEY o active llm.mock-allowed=true exclusivamente en entornos de prueba.");
            }
            log.debug("Generando respuesta asistida simulada (entorno de prueba) basada en el contexto provisto.");
            String simulatedResponse = "Basado en los lineamientos académicos oficiales de Lysandri Executive, nuestros programas combinan sesiones síncronas de alta dirección con seguimiento en el Campus Virtual y certificación oficial. Si requieres evaluar el plan de estudios o coordinar una propuesta corporativa in-company, puedes contactar con nuestro equipo de admisiones.";
            return new LlmCompletion(simulatedResponse, 120, 45, 165);
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("temperature", temperatura);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));

            String responseJson = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            String text = root.path("choices").get(0).path("message").path("content").asText();
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
            int compTokens = root.path("usage").path("completion_tokens").asInt(0);
            int total = root.path("usage").path("total_tokens").asInt(0);

            return new LlmCompletion(text, promptTokens, compTokens, total);
        } catch (Exception e) {
            log.error("Fallo al llamar a OpenAI Chat Completion: {}", e.getMessage(), e);
            if (mockAllowed) {
                log.warn("Usando fallback de completion en entorno de prueba debido a fallo de red.");
                String fallback = "Estimado ejecutivo, en este momento el servicio de inferencia está experimentando alta latencia. Los contenidos del programa pueden consultarse directamente en el sílabo descargable.";
                return new LlmCompletion(fallback, 50, 20, 70);
            }
            throw new IllegalStateException("Error de comunicación con el servicio de inferencia LLM de OpenAI: " + e.getMessage(), e);
        }
    }

    private float[] generarEmbeddingDeterministico(String text, int dim) {
        float[] vector = new float[dim];
        Random rand = new Random(text != null ? text.hashCode() : 42);
        float norm = 0.0f;
        for (int i = 0; i < dim; i++) {
            vector[i] = rand.nextFloat() - 0.5f;
            norm += vector[i] * vector[i];
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }
}

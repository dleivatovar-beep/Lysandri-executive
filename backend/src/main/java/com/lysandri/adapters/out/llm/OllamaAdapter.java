package com.lysandri.adapters.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.lysandri.application.service.DocumentoService;
import com.lysandri.domain.ports.out.LlmClientPort;
import com.lysandri.exception.RagUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAdapter implements LlmClientPort {
    private final WebClient cliente;
    private final String modelo;
    private final String modeloVector;
    private final int dimensiones;
    private final int capasGpu;
    private final RespuestasRecientes recientes = new RespuestasRecientes();
    private final Map<String, CompletableFuture<String>> respuestasEnCurso = new ConcurrentHashMap<>();
    private final VectoresRecientes vectores = new VectoresRecientes();
    @Value("${llm.ollama.keep-alive:10m}") private String permanencia = "10m";

    public OllamaAdapter(WebClient.Builder builder,
                         @Value("${llm.ollama.url:http://127.0.0.1:11434}") String url,
                         @Value("${llm.ollama.model:llama3:latest}") String modelo,
                         @Value("${llm.ollama.embedding-model:embeddinggemma:latest}") String modeloVector,
                         @Value("${llm.ollama.dimensions:768}") int dimensiones,
                         @Value("${llm.ollama.gpu-layers:0}") int capasGpu) {
        if (modelo.isBlank() || modeloVector.isBlank() || dimensiones <= 0) {
            throw new IllegalArgumentException("Configure los modelos y dimensiones de Ollama.");
        }
        this.cliente = builder.clone().baseUrl(url).build();
        this.modelo = modelo;
        this.modeloVector = modeloVector;
        this.dimensiones = dimensiones;
        this.capasGpu = capasGpu;
    }

    @Override
    public float[] generarEmbedding(String texto) {
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("El texto no puede estar vacío.");
        try {
            String clave = clave(modeloVector, Integer.toString(dimensiones), texto);
            float[] guardado = vectores.obtener(clave);
            if (guardado != null) return guardado;
            JsonNode datos = solicitar("/api/embed", Map.of("model", modeloVector, "input", texto,
                    "truncate", false, "keep_alive", permanencia, "options", opcionesModelo()), 45).path("embeddings");
            if (!datos.isArray() || datos.size() != 1 || !datos.get(0).isArray()) {
                throw new IllegalArgumentException("Respuesta de embeddings inválida.");
            }
            JsonNode valores = datos.get(0);
            float[] vector = new float[valores.size()];
            for (int i = 0; i < vector.length; i++) {
                if (!valores.get(i).isNumber()) throw new IllegalArgumentException("Vector inválido.");
                vector[i] = (float) valores.get(i).asDouble();
            }
            DocumentoService.validarVector(vector, dimensiones);
            if (texto.length() <= 2000) vectores.guardar(clave, vector);
            return vector;
        } catch (Exception e) {
            throw new RagUnavailableException("Ollama no pudo generar el vector. Compruebe el servicio y el modelo de embeddings instalado.");
        }
    }

    @Override
    public LlmCompletion completarChat(String sistema, String pregunta, double temperatura) {
        return completarChat(sistema, pregunta, temperatura, 400);
    }

    @Override
    public LlmCompletion completarChat(String sistema, String pregunta, double temperatura, int maxTokens) {
        return completarChat(sistema, pregunta, temperatura, maxTokens, Map.of());
    }

    @Override
    public LlmCompletion completarChat(String sistema, String pregunta, double temperatura, int maxTokens, Map<String, Object> formato) {
        if (maxTokens < 1 || maxTokens > 400) throw new IllegalArgumentException("Límite de respuesta inválido.");
        try {
            String clave = clave(modelo, sistema, pregunta, Double.toHexString(temperatura), Integer.toString(maxTokens), formato.toString());
            String guardada = recientes.obtener(clave);
            if (guardada != null) return new LlmCompletion(guardada, 0, 0, 0);
            var enCurso = new CompletableFuture<String>();
            var existente = respuestasEnCurso.putIfAbsent(clave, enCurso);
            if (existente != null) return new LlmCompletion(existente.join(), 0, 0, 0);
            try {
                guardada = recientes.obtener(clave);
                if (guardada != null) {
                    enCurso.complete(guardada);
                    return new LlmCompletion(guardada, 0, 0, 0);
                }
                var opciones = new HashMap<>(opcionesModelo());
                opciones.put("temperature", temperatura);
                opciones.put("num_predict", maxTokens);
                var cuerpo = new HashMap<String, Object>();
                cuerpo.put("model", modelo);
                cuerpo.put("stream", false);
                cuerpo.put("keep_alive", permanencia);
                cuerpo.put("messages", List.of(Map.of("role", "system", "content", sistema),
                        Map.of("role", "user", "content", pregunta)));
                cuerpo.put("options", opciones);
                if (!formato.isEmpty()) cuerpo.put("format", formato);
                JsonNode datos = solicitar("/api/chat", cuerpo, 120);
                String respuesta = datos.path("message").path("content").asText("");
                if (!datos.path("done").asBoolean() || respuesta.isBlank() || "length".equals(datos.path("done_reason").asText())) {
                    throw new IllegalArgumentException("Respuesta incompleta de Ollama.");
                }
                int entrada = datos.path("prompt_eval_count").asInt();
                int salida = datos.path("eval_count").asInt();
                recientes.guardar(clave, respuesta);
                enCurso.complete(respuesta);
                return new LlmCompletion(respuesta, entrada, salida, entrada + salida);
            } catch (RuntimeException | Error e) {
                enCurso.completeExceptionally(e);
                throw e;
            } finally {
                respuestasEnCurso.remove(clave, enCurso);
            }
        } catch (Exception e) {
            throw new RagUnavailableException("Ollama no pudo generar la respuesta. Compruebe el servicio y el modelo de lenguaje instalado.");
        }
    }

    private Map<String, Object> opcionesModelo() {
        return capasGpu < 0 ? Map.of() : Map.of("num_gpu", capasGpu);
    }

    private String clave(String... valores) throws java.security.NoSuchAlgorithmException {
        var hash = java.security.MessageDigest.getInstance("SHA-256");
        // El contexto completo forma parte de la clave: un cambio documental obliga a generar otra respuesta.
        for (String valor : valores) {
            byte[] bytes = valor.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
            hash.update(bytes);
        }
        return java.util.HexFormat.of().formatHex(hash.digest());
    }

    private JsonNode solicitar(String ruta, Object cuerpo, int segundos) {
        return cliente.post().uri(ruta).contentType(MediaType.APPLICATION_JSON).bodyValue(cuerpo)
                .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(segundos)).block();
    }
}

package com.lysandri.adapters.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.lysandri.domain.ports.out.WebSearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BraveWebSearchAdapter implements WebSearchPort {
    private final WebClient cliente;
    private final String clave;

    public BraveWebSearchAdapter(WebClient.Builder builder, @Value("${web-search.brave-api-key:}") String clave) {
        this.cliente = builder.clone().baseUrl("https://api.search.brave.com").build();
        this.clave = clave.strip();
    }

    @Override
    public boolean disponible() {
        return !clave.isBlank();
    }

    @Override
    public List<Resultado> buscar(String pregunta) {
        if (!disponible()) return List.of();
        try {
            JsonNode respuesta = cliente.post().uri("/res/v1/llm/context")
                    .header("X-Subscription-Token", clave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("q", pregunta, "search_lang", "es", "maximum_number_of_urls", 4,
                            "maximum_number_of_tokens", 2048, "maximum_number_of_tokens_per_url", 512,
                            "context_threshold_mode", "strict", "enable_local", false, "operators", false))
                    .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(7)).block();
            if (respuesta == null) return List.of();

            Map<String, Resultado> resultados = new LinkedHashMap<>();
            for (JsonNode item : respuesta.path("grounding").path("generic")) {
                String url = item.path("url").asText("");
                if (!esEnlaceValido(url)) continue;
                StringBuilder pasajes = new StringBuilder();
                for (JsonNode pasaje : item.path("snippets")) {
                    String contenido = pasaje.asText("").strip();
                    if (contenido.isEmpty()) continue;
                    if (!pasajes.isEmpty()) pasajes.append('\n');
                    pasajes.append(contenido, 0, Math.min(contenido.length(), 1500 - pasajes.length()));
                    if (pasajes.length() >= 1499) break;
                }
                if (pasajes.isEmpty()) continue;
                String titulo = item.path("title").asText("").strip();
                resultados.putIfAbsent(url, new Resultado(titulo.isEmpty() ? URI.create(url).getHost() : titulo,
                        url, pasajes.toString()));
                if (resultados.size() == 4) break;
            }
            return List.copyOf(resultados.values());
        } catch (RuntimeException e) {
            log.warn("La búsqueda web no devolvió resultados. Tipo de error: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private static boolean esEnlaceValido(String url) {
        try {
            URI uri = URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

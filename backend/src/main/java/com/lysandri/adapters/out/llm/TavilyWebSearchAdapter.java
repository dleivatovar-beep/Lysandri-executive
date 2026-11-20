package com.lysandri.adapters.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.lysandri.domain.ports.out.WebSearchPort;
import com.lysandri.exception.WebSearchUnavailableException;
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
public class TavilyWebSearchAdapter implements WebSearchPort {
    private final WebClient cliente;
    private final String clave;

    public TavilyWebSearchAdapter(WebClient.Builder builder, @Value("${web-search.tavily-api-key:}") String clave) {
        this.cliente = builder.clone().baseUrl("https://api.tavily.com").build();
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
            JsonNode respuesta = cliente.post().uri("/search")
                    .headers(headers -> headers.setBearerAuth(clave))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("query", pregunta, "search_depth", "basic", "max_results", 4,
                            "language", "es", "topic", "general", "auto_parameters", false,
                            "include_answer", false, "include_raw_content", false, "include_images", false))
                    .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(6))
                    .retryWhen(reactor.util.retry.Retry.max(1).filter(TavilyWebSearchAdapter::esTransitorio)
                            .onRetryExhaustedThrow((regla, estado) -> estado.failure())).block();
            if (respuesta == null) return List.of();

            Map<String, Resultado> resultados = new LinkedHashMap<>();
            for (JsonNode item : respuesta.path("results")) {
                String url = item.path("url").asText("");
                if (!esEnlaceValido(url)) continue;
                String contenido = item.path("content").asText("").strip();
                if (contenido.isEmpty()) continue;
                String pasaje = contenido.substring(0, Math.min(contenido.length(), 1500));
                String titulo = item.path("title").asText("").strip();
                resultados.putIfAbsent(url, new Resultado(titulo.isEmpty() ? URI.create(url).getHost() : titulo,
                        url, pasaje));
                if (resultados.size() == 4) break;
            }
            return List.copyOf(resultados.values());
        } catch (RuntimeException e) {
            log.warn("La búsqueda web no devolvió resultados. Tipo de error: {}", e.getClass().getSimpleName());
            throw new WebSearchUnavailableException("La búsqueda web no está disponible temporalmente.");
        }
    }

    private static boolean esTransitorio(Throwable error) {
        return error instanceof java.util.concurrent.TimeoutException
                || error instanceof org.springframework.web.reactive.function.client.WebClientRequestException
                || (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException http
                    && http.getStatusCode().is5xxServerError());
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

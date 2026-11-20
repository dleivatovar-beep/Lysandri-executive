package com.lysandri.adapters.out.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "LYSANDRI_PRUEBA_WEB_REAL", matches = "true")
class TavilyRealIntegrationTest {
    @Test
    void consultaRealDevuelveTextoYEnlaces() {
        String clave = System.getenv("TAVILY_API_KEY");
        assertNotNull(clave);
        var builder = WebClient.builder().filter((solicitud, siguiente) -> siguiente.exchange(solicitud)
                .doOnNext(respuesta -> System.out.println("Tavily HTTP: " + respuesta.statusCode().value()))
                .doOnError(error -> {
                    Throwable causa = error;
                    for (int i = 0; causa != null && i < 5; i++, causa = causa.getCause()) {
                        System.out.println("Tipo de fallo web: " + causa.getClass().getSimpleName());
                    }
                }));
        var resultados = new TavilyWebSearchAdapter(builder, clave).buscar("Qué ciudad es la capital de Australia?");
        System.out.println("Resultados de Tavily: " + resultados.size());
        assertFalse(resultados.isEmpty());
        assertTrue(resultados.stream().anyMatch(resultado -> resultado.texto().contains("Canberra")));
    }
}

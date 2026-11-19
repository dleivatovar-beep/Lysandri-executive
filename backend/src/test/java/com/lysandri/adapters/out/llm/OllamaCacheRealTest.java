package com.lysandri.adapters.out.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.reactive.function.client.WebClient;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="ollama.real", matches="true")
class OllamaCacheRealTest {
    @Test void repeticionReutilizaRespuestaYCambioDeFuenteGeneraOtra() {
        var llm = new OllamaAdapter(WebClient.builder(), "http://127.0.0.1:11434", "llama3:latest", "embeddinggemma:latest", 768, 0);
        String sistema = "Responde en español con una sola frase, solo con el dato del contexto. No inventes información.";
        String pregunta = "Contexto de prueba: El curso dura 8 horas. Pregunta: Cuánto dura el curso?";
        long inicio=System.nanoTime();
        var primera=llm.completarChat(sistema,pregunta,0.2);
        double primeraMs=(System.nanoTime()-inicio)/1_000_000.0;
        inicio=System.nanoTime();
        var repetida=llm.completarChat(sistema,pregunta,0.2);
        double repetidaMs=(System.nanoTime()-inicio)/1_000_000.0;
        assertEquals(primera.textoRespuesta(),repetida.textoRespuesta());
        assertTrue(primera.textoRespuesta().contains("8"));
        assertTrue(primera.totalTokens()>0);
        assertEquals(0,repetida.totalTokens());
        var cambiada=llm.completarChat(sistema,pregunta.replace("8 horas","10 horas"),0.2);
        assertTrue(cambiada.totalTokens()>0);
        assertTrue(cambiada.textoRespuesta().contains("10"));
        System.out.printf(java.util.Locale.ROOT,"OLLAMA REAL: primera=%.2f ms; repetida=%.2f ms; fuente modificada vuelve a generar.%n",primeraMs,repetidaMs);
    }
}

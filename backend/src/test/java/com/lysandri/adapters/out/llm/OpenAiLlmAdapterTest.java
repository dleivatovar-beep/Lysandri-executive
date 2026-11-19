package com.lysandri.adapters.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.exception.RagUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiLlmAdapterTest {
    @Test
    void sinClaveNoGeneraVectoresAleatoriosNiRespuestasFicticias() {
        var adapter = new OpenAiLlmAdapter("mock-key", "gpt-4o-mini", "text-embedding-3-small", WebClient.builder(), new ObjectMapper());
        assertThrows(RagUnavailableException.class, () -> adapter.generarEmbedding("Pregunta"));
        assertThrows(RagUnavailableException.class, () -> adapter.completarChat("Sistema", "Pregunta", 0.2));
    }

    @Test
    void otroModeloNoPuedeMezclarseConElIndice() {
        var adapter = new OpenAiLlmAdapter("configured-for-test", "gpt-4o-mini", "otro-modelo", WebClient.builder(), new ObjectMapper());
        assertThrows(RagUnavailableException.class, () -> adapter.generarEmbedding("Pregunta"));
    }
}

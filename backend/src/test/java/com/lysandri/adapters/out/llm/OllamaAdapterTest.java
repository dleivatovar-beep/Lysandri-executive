package com.lysandri.adapters.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.exception.RagUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

class OllamaAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer servidor;
    private JsonNode cuerpo;
    private final java.util.concurrent.atomic.AtomicInteger llamadas = new java.util.concurrent.atomic.AtomicInteger();
    private final CountDownLatch solicitudRecibida = new CountDownLatch(1);
    private CountDownLatch responder = new CountDownLatch(0);
    @AfterEach void cerrar() { if (servidor != null) servidor.stop(0); }
    private OllamaAdapter adapter(String ruta, int estado, String respuesta) throws Exception {
        return adapter(ruta, estado, respuesta, 0);
    }

    private OllamaAdapter adapter(String ruta, int estado, String respuesta, int capasGpu) throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext(ruta, request -> {
            llamadas.incrementAndGet();
            cuerpo = mapper.readTree(request.getRequestBody());
            solicitudRecibida.countDown();
            try {
                if (!responder.await(10, TimeUnit.SECONDS)) throw new java.io.IOException("Tiempo agotado en la simulación");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException(e);
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(estado, bytes.length);
            request.getResponseBody().write(bytes); request.close();
        });
        servidor.start();
        return new OllamaAdapter(WebClient.builder(), "http://127.0.0.1:" + servidor.getAddress().getPort(),
                "llama3:latest", "embeddinggemma:latest", 3, capasGpu);
    }
    @Test void usaApiLocalYValidaDimension() throws Exception {
        var llm = adapter("/api/embed", 200, "{\"embeddings\":[[1,0.5,0.2]]}");
        assertArrayEquals(new float[]{1,0.5f,0.2f}, llm.generarEmbedding("Contenido"));
        assertEquals("embeddinggemma:latest", cuerpo.path("model").asText());
        assertFalse(cuerpo.path("truncate").asBoolean());
        assertEquals(0, cuerpo.path("options").path("num_gpu").asInt());
    }
    @Test void embeddingRepetidoReutilizaVectorSinCompartirElArreglo() throws Exception {
        var llm = adapter("/api/embed", 200, "{\"embeddings\":[[1,0.5,0.2]]}");
        float[] primera = llm.generarEmbedding("Precio de FinOps?");
        primera[0] = 99;
        assertArrayEquals(new float[]{1, 0.5f, 0.2f}, llm.generarEmbedding("Precio de FinOps?"));
        assertEquals(1, llamadas.get());
        llm.generarEmbedding("Duración de FinOps?");
        assertEquals(2, llamadas.get());
    }

    @Test void embeddingInvalidoNoSeGuarda() throws Exception {
        var llm = adapter("/api/embed", 200, "{\"embeddings\":[[1,0]]}");
        assertThrows(RagUnavailableException.class, () -> llm.generarEmbedding("Precio"));
        assertThrows(RagUnavailableException.class, () -> llm.generarEmbedding("Precio"));
        assertEquals(2, llamadas.get());
    }

    @Test void noAceptaVectoresDeOtraDimension() throws Exception {
        var llm = adapter("/api/embed", 200, "{\"embeddings\":[[1,0]]}");
        assertThrows(RagUnavailableException.class, () -> llm.generarEmbedding("Contenido"));
    }
    @Test void falloDelModeloNoSeConvierteEnRespuestaInventada() throws Exception {
        var llm = adapter("/api/embed", 404, "{\"error\":\"model not found\"}");
        assertThrows(RagUnavailableException.class, () -> llm.generarEmbedding("Contenido"));
    }
    @Test void chatSinStreamingConservaRespuestaYUso() throws Exception {
        var llm = adapter("/api/chat", 200, "{\"message\":{\"content\":\"Respuesta documentada\"},\"done\":true,\"prompt_eval_count\":8,\"eval_count\":4}");
        var respuesta = llm.completarChat("Reglas", "Pregunta", 0.2);
        assertEquals("Respuesta documentada", respuesta.textoRespuesta());
        assertEquals(12, respuesta.totalTokens());
        assertFalse(cuerpo.path("stream").asBoolean());
        assertEquals("system", cuerpo.path("messages").get(0).path("role").asText());
    }
    @Test void permiteSeleccionAutomaticaDeGpu() throws Exception {
        var llm = adapter("/api/chat", 200,
                "{\"message\":{\"content\":\"Respuesta documentada\"},\"done\":true}", -1);
        llm.completarChat("Reglas", "Pregunta", 0.2);
        assertFalse(cuerpo.path("options").has("num_gpu"));
        assertEquals(400, cuerpo.path("options").path("num_predict").asInt());
    }

    @Test void limiteBreveSeEnviaAlModeloYNoComparteCacheConLimiteNormal() throws Exception {
        var llm = adapter("/api/chat", 200, "{\"message\":{\"content\":\"Respuesta breve\"},\"done\":true}");
        llm.completarChat("Reglas", "Pregunta", 0.0, 256);
        assertEquals(256, cuerpo.path("options").path("num_predict").asInt());
        llm.completarChat("Reglas", "Pregunta", 0.0, 256);
        assertEquals(1, llamadas.get());
        llm.completarChat("Reglas", "Pregunta", 0.0);
        assertEquals(400, cuerpo.path("options").path("num_predict").asInt());
        assertEquals(2, llamadas.get());
    }
    @Test void esquemaDeFuentesSeEnviaAlModeloYSuCacheEsIndependiente() throws Exception {
        var llm = adapter("/api/chat", 200, "{\"message\":{\"content\":\"{}\"},\"done\":true}");
        var esquema = java.util.Map.<String,Object>of("type", "object");
        llm.completarChat("Reglas", "Pregunta", 0.0, 256, esquema);
        assertEquals("object", cuerpo.path("format").path("type").asText());
        llm.completarChat("Reglas", "Pregunta", 0.0, 256, esquema);
        assertEquals(1, llamadas.get());
        llm.completarChat("Reglas", "Pregunta", 0.0, 256);
        assertFalse(cuerpo.has("format"));
        assertEquals(2, llamadas.get());
    }
    @Test void respuestaIncompletaSeRechaza() throws Exception {
        var llm = adapter("/api/chat", 200, "{\"message\":{\"content\":\"Parcial\"},\"done\":false}");
        assertThrows(RagUnavailableException.class, () -> llm.completarChat("Reglas", "Pregunta", 0.2));
    }
    @Test void preguntaYContextoIdenticosNoVuelvenAGenerarse() throws Exception {
        var llm = adapter("/api/chat", 200, "{\"message\":{\"content\":\"Respuesta documentada\"},\"done\":true,\"done_reason\":\"stop\",\"prompt_eval_count\":8,\"eval_count\":4}");
        assertEquals(12,llm.completarChat("Reglas","Documento vigente y pregunta",0.2).totalTokens());
        assertEquals(0,llm.completarChat("Reglas","Documento vigente y pregunta",0.2).totalTokens());
        assertEquals(1,llamadas.get());
        assertEquals("10m",cuerpo.path("keep_alive").asText());
        llm.completarChat("Reglas","Documento actualizado y pregunta",0.2);
        llm.completarChat("Otras reglas","Documento actualizado y pregunta",0.2);
        llm.completarChat("Otras reglas","Documento actualizado y pregunta",0.3);
        assertEquals(4,llamadas.get());
    }
    @Test void solicitudesIgualesEnCursoCompartenUnaGeneracion() throws Exception {
        responder = new CountDownLatch(1);
        var llm = adapter("/api/chat", 200, """
                {"message":{"content":"Respuesta documentada"},"done":true,"prompt_eval_count":8,"eval_count":4}
                """);
        var ejecutor = Executors.newFixedThreadPool(2);
        try {
            var primera = ejecutor.submit(() -> llm.completarChat("Reglas", "Pregunta", 0.2));
            assertTrue(solicitudRecibida.await(5, TimeUnit.SECONDS));
            var segunda = ejecutor.submit(() -> llm.completarChat("Reglas", "Pregunta", 0.2));
            assertThrows(TimeoutException.class, () -> segunda.get(250, TimeUnit.MILLISECONDS));
            responder.countDown();
            assertEquals(12, primera.get(5, TimeUnit.SECONDS).totalTokens());
            var compartida = segunda.get(5, TimeUnit.SECONDS);
            assertEquals("Respuesta documentada", compartida.textoRespuesta());
            assertEquals(0, compartida.totalTokens());
            assertEquals(1, llamadas.get());
        } finally {
            responder.countDown();
            ejecutor.shutdownNow();
        }
    }

    @Test void textoRecortadoNoSeAceptaNiSeGuarda() throws Exception {
        var llm=adapter("/api/chat",200,"{\"message\":{\"content\":\"Respuesta cortada\"},\"done\":true,\"done_reason\":\"length\"}");
        assertThrows(RagUnavailableException.class,()->llm.completarChat("Reglas","Pregunta",0.2));
        assertThrows(RagUnavailableException.class,()->llm.completarChat("Reglas","Pregunta",0.2));
        assertEquals(2,llamadas.get());
    }
}

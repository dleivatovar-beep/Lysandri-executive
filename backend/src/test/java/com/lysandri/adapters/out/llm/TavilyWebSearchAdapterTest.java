package com.lysandri.adapters.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.ports.out.WebSearchPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TavilyWebSearchAdapterTest {
    @Test void reintentaUnaVezUnFalloTemporalDelProveedor() {
        var intentos = new java.util.concurrent.atomic.AtomicInteger();
        var builder = WebClient.builder().exchangeFunction(peticion -> {
            if (intentos.incrementAndGet() == 1) return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build());
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body("{\"results\":[{\"title\":\"Fuente\",\"url\":\"https://ejemplo.test/a\",\"content\":\"Dato confirmado.\"}]}").build());
        });
        assertEquals(1, new TavilyWebSearchAdapter(builder,"clave-prueba").buscar("consulta").size());
        assertEquals(2, intentos.get());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={401,403,429})
    void noReintentaErroresDeClavePermisosOCuota(int estado) {
        var intentos = new java.util.concurrent.atomic.AtomicInteger();
        var builder = WebClient.builder().exchangeFunction(p -> {
            intentos.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.valueOf(estado)).build());
        });
        assertThrows(com.lysandri.exception.WebSearchUnavailableException.class,
                () -> new TavilyWebSearchAdapter(builder,"clave-prueba").buscar("consulta"));
        assertEquals(1, intentos.get());
    }
    @Test void falloPersistenteNoProduceUnBucleDeReintentos() {
        var intentos = new java.util.concurrent.atomic.AtomicInteger();
        var builder = WebClient.builder().exchangeFunction(p -> {
            intentos.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).build());
        });
        assertThrows(com.lysandri.exception.WebSearchUnavailableException.class,
                () -> new TavilyWebSearchAdapter(builder,"clave-prueba").buscar("consulta"));
        assertEquals(2, intentos.get());
    }

    @Test
    void usaBusquedaBasicaYTraduceSoloFuentesValidas() {
        var builder = WebClient.builder().exchangeFunction(peticion -> {
            assertEquals("https://api.tavily.com/search", peticion.url().toString());
            assertEquals("Bearer clave-de-prueba", peticion.headers().getFirst("Authorization"));
            var cuerpo = new MockClientHttpRequest(HttpMethod.POST, peticion.url());
            peticion.body().insert(cuerpo, new BodyInserter.Context() {
                @Override public List<HttpMessageWriter<?>> messageWriters() {
                    return ExchangeStrategies.withDefaults().messageWriters();
                }
                @Override public Optional<ServerHttpRequest> serverRequest() { return Optional.empty(); }
                @Override public Map<String, Object> hints() { return Map.of(); }
            }).block();
            JsonNode datos;
            try {
                datos = new ObjectMapper().readTree(cuerpo.getBodyAsString().block());
            } catch (java.io.IOException e) {
                return Mono.error(e);
            }
            assertEquals("quién es el papa actual?", datos.path("query").asText());
            assertEquals("basic", datos.path("search_depth").asText());
            assertTrue(datos.has("auto_parameters"));
            assertFalse(datos.path("auto_parameters").asBoolean());
            assertFalse(datos.path("include_answer").asBoolean());
            assertFalse(datos.path("include_raw_content").asBoolean());
            assertEquals(4, datos.path("max_results").asInt());
            assertFalse(datos.has("api_key"));

            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json").body("""
                    {"results":[
                        {"title":"No usar","url":"javascript:alert(1)","content":"No es una fuente web."},
                        {"title":"Fuente oficial","url":"https://ejemplo.test/pagina","content":"Pasaje documentado."},
                        {"title":"Duplicado","url":"https://ejemplo.test/pagina","content":"Copia."},
                        {"title":"Sin texto","url":"https://ejemplo.test/vacio","content":""}
                    ]}
                    """).build());
        });
        var adaptador = new TavilyWebSearchAdapter(builder, "clave-de-prueba");
        assertEquals(List.of(new WebSearchPort.Resultado("Fuente oficial", "https://ejemplo.test/pagina",
                "Pasaje documentado.")), adaptador.buscar("quién es el papa actual?"));
    }

    @Test
    void sinClaveNoEmiteSolicitudes() {
        var adaptador = new TavilyWebSearchAdapter(WebClient.builder().exchangeFunction(peticion -> {
            fail("El adaptador consultó internet sin clave.");
            return Mono.empty();
        }), "");
        assertFalse(adaptador.disponible());
        assertTrue(adaptador.buscar("quién es el papa actual?").isEmpty());
    }

    @Test
    void cuotaAgotadaSeDistingueDeUnaBusquedaSinResultados() {
        var builder = WebClient.builder().exchangeFunction(peticion -> Mono.just(
                ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build()));
        assertThrows(com.lysandri.exception.RagUnavailableException.class,
                () -> new TavilyWebSearchAdapter(builder, "clave-de-prueba").buscar("una consulta pública"));
    }

    @Test
    void limitaLasFuentesYElTextoEnviadoAlModelo() throws Exception {
        String contenido = "a".repeat(1700);
        var filas = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> Map.of("title", "Fuente " + i, "url", "https://ejemplo.test/" + i, "content", contenido))
                .toList();
        String respuesta = new ObjectMapper().writeValueAsString(Map.of("results", filas));
        var builder = WebClient.builder().exchangeFunction(peticion -> Mono.just(
                ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(respuesta).build()));
        var resultados = new TavilyWebSearchAdapter(builder, "clave-de-prueba").buscar("una consulta pública");
        assertEquals(4, resultados.size());
        assertTrue(resultados.stream().allMatch(resultado -> resultado.texto().length() == 1500));
    }
}

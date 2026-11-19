package com.lysandri.adapters.out.llm;

import com.lysandri.domain.ports.out.WebSearchPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BraveWebSearchAdapterTest {
    @Test
    void traduceElContextoDelProveedorYDescartaEnlacesInvalidos() {
        var builder = WebClient.builder().exchangeFunction(peticion -> {
            assertEquals("https://api.search.brave.com/res/v1/llm/context", peticion.url().toString());
            assertEquals("clave-de-prueba", peticion.headers().getFirst("X-Subscription-Token"));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json").body("""
                    {"grounding":{"generic":[
                        {"title":"No usar","url":"javascript:alert(1)","snippets":["No es una fuente web."]},
                        {"title":"Fuente oficial","url":"https://ejemplo.test/pagina","snippets":["Primer pasaje.","Segundo pasaje."]},
                        {"title":"Duplicado","url":"https://ejemplo.test/pagina","snippets":["Copia."]}
                    ]}}
                    """).build());
        });
        var adaptador = new BraveWebSearchAdapter(builder, "clave-de-prueba");

        assertEquals(List.of(new WebSearchPort.Resultado("Fuente oficial", "https://ejemplo.test/pagina",
                "Primer pasaje.\nSegundo pasaje.")), adaptador.buscar("quién es el papa actual?"));
    }

    @Test
    void sinClaveNoEmiteSolicitudes() {
        var adaptador = new BraveWebSearchAdapter(WebClient.builder().exchangeFunction(peticion -> {
            fail("El adaptador consultó internet sin clave.");
            return Mono.empty();
        }), "");
        assertFalse(adaptador.disponible());
        assertTrue(adaptador.buscar("quién es el papa actual?").isEmpty());
    }
}

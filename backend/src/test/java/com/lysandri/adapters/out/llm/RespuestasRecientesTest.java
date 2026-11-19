package com.lysandri.adapters.out.llm;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RespuestasRecientesTest {
    @Test void venceSinConservarRespuestasAntiguas() {
        var tiempo = new AtomicLong(); var cache = new RespuestasRecientes(tiempo::get,10,2);
        cache.guardar("a","respuesta"); assertEquals("respuesta",cache.obtener("a"));
        tiempo.set(10); assertNull(cache.obtener("a"));
    }
    @Test void limitaMemoriaYNoConservaTextosVaciosOExcesivos() {
        var cache = new RespuestasRecientes(()->0L,10,2);
        cache.guardar("a","uno");cache.guardar("b","dos");cache.guardar("c","tres");
        assertNull(cache.obtener("a"));assertEquals("dos",cache.obtener("b"));
        cache.guardar("d"," ");cache.guardar("e","x".repeat(12001));
        assertNull(cache.obtener("d"));assertNull(cache.obtener("e"));
    }
}

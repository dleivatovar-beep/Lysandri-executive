package com.lysandri.adapters.out.llm;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VectoresRecientesTest {
    @Test void venceYLimitaMemoriaSinCompartirLosVectores() {
        var reloj = new AtomicLong();
        var cache = new VectoresRecientes(reloj::get, 10, 2);
        float[] entrada = {1, 2};
        cache.guardar("a", entrada);
        entrada[0] = 99;
        assertArrayEquals(new float[]{1, 2}, cache.obtener("a"));
        cache.obtener("a")[0] = 88;
        assertArrayEquals(new float[]{1, 2}, cache.obtener("a"));
        cache.guardar("b", new float[]{3});
        cache.guardar("c", new float[]{4});
        assertNull(cache.obtener("a"));
        assertArrayEquals(new float[]{3}, cache.obtener("b"));
        reloj.set(10);
        assertNull(cache.obtener("b"));
        assertNull(cache.obtener("c"));
    }
}

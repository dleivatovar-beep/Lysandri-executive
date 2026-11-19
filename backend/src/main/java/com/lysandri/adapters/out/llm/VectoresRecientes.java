package com.lysandri.adapters.out.llm;

import java.util.LinkedHashMap;
import java.util.function.LongSupplier;

final class VectoresRecientes {
    private record Entrada(float[] vector, long vence) {}

    private final LinkedHashMap<String, Entrada> entradas = new LinkedHashMap<>();
    private final LongSupplier reloj;
    private final long duracion;
    private final int capacidad;

    VectoresRecientes() {
        this(System::nanoTime, 300_000_000_000L, 128);
    }

    VectoresRecientes(LongSupplier reloj, long duracion, int capacidad) {
        this.reloj = reloj;
        this.duracion = duracion;
        this.capacidad = capacidad;
    }

    synchronized float[] obtener(String clave) {
        entradas.entrySet().removeIf(e -> e.getValue().vence() <= reloj.getAsLong());
        Entrada entrada = entradas.get(clave);
        return entrada == null ? null : entrada.vector().clone();
    }

    synchronized void guardar(String clave, float[] vector) {
        entradas.entrySet().removeIf(e -> e.getValue().vence() <= reloj.getAsLong());
        entradas.remove(clave);
        while (entradas.size() >= capacidad) {
            entradas.remove(entradas.keySet().iterator().next());
        }
        entradas.put(clave, new Entrada(vector.clone(), reloj.getAsLong() + duracion));
    }
}

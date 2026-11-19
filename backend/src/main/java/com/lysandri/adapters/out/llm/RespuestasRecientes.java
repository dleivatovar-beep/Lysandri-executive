package com.lysandri.adapters.out.llm;

import java.util.LinkedHashMap;
import java.util.function.LongSupplier;

final class RespuestasRecientes {
    private record Entrada(String texto, long vence) {}
    private final LinkedHashMap<String, Entrada> entradas = new LinkedHashMap<>();
    private final LongSupplier reloj;
    private final long duracion;
    private final int capacidad;

    RespuestasRecientes() { this(System::nanoTime, 300_000_000_000L, 32); }
    RespuestasRecientes(LongSupplier reloj, long duracion, int capacidad) {
        this.reloj = reloj; this.duracion = duracion; this.capacidad = capacidad;
    }
    synchronized String obtener(String clave) {
        entradas.entrySet().removeIf(e -> e.getValue().vence() <= reloj.getAsLong());
        var entrada = entradas.get(clave);
        return entrada == null ? null : entrada.texto();
    }
    synchronized void guardar(String clave, String texto) {
        if (texto == null || texto.isBlank() || texto.length() > 12000) return;
        entradas.entrySet().removeIf(e -> e.getValue().vence() <= reloj.getAsLong());
        entradas.remove(clave);
        while (entradas.size() >= capacidad) entradas.remove(entradas.keySet().iterator().next());
        entradas.put(clave, new Entrada(texto, reloj.getAsLong() + duracion));
    }
}

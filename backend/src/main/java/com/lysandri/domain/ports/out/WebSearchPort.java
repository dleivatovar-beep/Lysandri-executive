package com.lysandri.domain.ports.out;

import java.util.List;

public interface WebSearchPort {
    record Resultado(String titulo, String url, String texto) {}

    boolean disponible();
    List<Resultado> buscar(String pregunta);
}

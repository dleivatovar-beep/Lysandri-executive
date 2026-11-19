package com.lysandri.application.service;

import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.ports.out.DocumentoRepositoryPort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class ComparacionCatalogo {
    private static final Pattern CONSULTA = Pattern.compile(
            "(?iu)^(?:(no tengo experiencia en redes) y )?quiero estudiar a mi ritmo[.,]\\s*"
            + "según (?:el )?(catálogo [^,?\\n]{1,180}),\\s*¿?qué cursos puedo llevar y cuál cuesta menos\\??$");
    private static final Pattern PRECIO = Pattern.compile("^Precio \\((USD|PEN)\\): ([0-9]+(?:\\.[0-9]{1,2})?)$");

    record Comparacion(String respuesta, DocumentoChunk fuente) {}
    private record Curso(String nombre, BigDecimal precio) {}

    static Comparacion buscar(String pregunta, DocumentoRepositoryPort documentos) {
        var consulta = CONSULTA.matcher(pregunta.strip());
        if (!consulta.matches()) return null;
        var fuentes = documentos.buscarPorTitulo(consulta.group(2).strip(), 2);
        if (fuentes.size() != 1) return null;
        var fuente = fuentes.get(0);
        var datos = fuente.getMetadata();
        if (datos == null || !"VIGENTE".equals(datos.get("estado"))
                || !String.valueOf(datos.get("seccion")).startsWith("Hoja ")) return null;

        List<Curso> disponibles = new ArrayList<>();
        String moneda = null;
        boolean encabezados = false;
        for (String linea : fuente.getContenido().split("\\R")) {
            if (linea.isBlank()) continue;
            if (!encabezados) {
                encabezados = linea.equals("Código | Programa | Duración (h) | Modalidad | Requisito | Precio (USD)")
                        || linea.equals("Código | Programa | Duración (h) | Modalidad | Requisito | Precio (PEN)");
                continue;
            }
            String[] celdas = linea.split(" \\| ", -1);
            if (celdas.length != 6 || !celdas[0].startsWith("Código: ")
                    || !celdas[1].startsWith("Programa: ") || !celdas[2].startsWith("Duración (h): ")
                    || !celdas[3].startsWith("Modalidad: ") || !celdas[4].startsWith("Requisito: ")) return null;
            var precio = PRECIO.matcher(celdas[5]);
            if (!precio.matches()) return null;
            if (moneda != null && !moneda.equals(precio.group(1))) return null;
            moneda = precio.group(1);

            String modalidad = celdas[3].substring("Modalidad: ".length());
            if (!modalidad.equalsIgnoreCase("Virtual a tu ritmo")) continue;
            String requisito = celdas[4].substring("Requisito: ".length());
            if (consulta.group(1) != null && !requisito.equalsIgnoreCase("Ninguno")) {
                if (requisito.equalsIgnoreCase("Nociones básicas de redes")) continue;
                return null;
            }
            disponibles.add(new Curso(celdas[1].substring("Programa: ".length()), new BigDecimal(precio.group(2))));
        }
        if (disponibles.isEmpty()) return null;
        BigDecimal minimo = disponibles.stream().map(Curso::precio).min(BigDecimal::compareTo).orElseThrow();
        var nombres = disponibles.stream().map(Curso::nombre).toList();
        var economicos = disponibles.stream().filter(curso -> curso.precio().compareTo(minimo) == 0)
                .map(Curso::nombre).toList();
        String respuesta = "Puedes estudiar a tu ritmo con " + enumerar(nombres) + ". "
                + (economicos.size() == 1 ? "El curso más económico es " : "Los cursos más económicos son ")
                + enumerar(economicos) + " (" + minimo.stripTrailingZeros().toPlainString() + " " + moneda + ").";
        return new Comparacion(respuesta, fuente);
    }

    private static String enumerar(List<String> nombres) {
        int ultimo = nombres.size() - 1;
        return ultimo == 0 ? nombres.get(0) : String.join(", ", nombres.subList(0, ultimo)) + " y " + nombres.get(ultimo);
    }
}

package com.lysandri.application.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Expande escritura informal sin alterar enlaces, correos, nombres ni cifras. */
final class LenguajeConsulta {
    private static final Pattern PARTES = Pattern.compile("https?://\\S+|[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}|[\\p{L}\\p{N}_]+", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> FORMAS = Map.ofEntries(
            Map.entry("q", "que"), Map.entry("ke", "que"),
            Map.entry("xq", "por que"), Map.entry("xk", "por que"), Map.entry("pq", "por que"),
            Map.entry("dnd", "donde"), Map.entry("dnde", "donde"), Map.entry("onde", "donde"),
            Map.entry("cmo", "como"), Map.entry("cuals", "cuales"),
            Map.entry("cuantoz", "cuantos"), Map.entry("cuantoo", "cuanto"),
            Map.entry("kiero", "quiero"), Map.entry("kiereo", "quiero"),
            Map.entry("tngo", "tengo"), Map.entry("toy", "estoy"),
            Map.entry("tmb", "tambien"), Map.entry("tb", "tambien"),
            Map.entry("xfa", "por favor"), Map.entry("porfa", "por favor"),
            Map.entry("pa", "para"), Map.entry("pal", "para el"), Map.entry("cn", "con"),
            Map.entry("info", "informacion"), Map.entry("inf", "informacion"),
            Map.entry("curzo", "curso"), Map.entry("curzos", "cursos"),
            Map.entry("presio", "precio"), Map.entry("presios", "precios"),
            Map.entry("silavo", "silabo"), Map.entry("silavos", "silabos"),
            Map.entry("durasion", "duracion"), Map.entry("inscrivir", "inscribir"),
            Map.entry("escuxar", "escuchar"), Map.entry("eskuchar", "escuchar"),
            Map.entry("contrasenia", "contraseña"), Map.entry("contraseña", "contraseña"));

    private LenguajeConsulta() {}

    static String interpretar(String texto) {
        var partes = PARTES.matcher(texto);
        return partes.replaceAll(parte -> java.util.regex.Matcher.quoteReplacement(
                FORMAS.getOrDefault(parte.group().toLowerCase(Locale.ROOT), parte.group())));
    }
}

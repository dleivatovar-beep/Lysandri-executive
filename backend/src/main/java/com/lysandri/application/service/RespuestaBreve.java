package com.lysandri.application.service;

import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.model.Programa;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

final class RespuestaBreve {
    private RespuestaBreve() {}

    record Modulo(String titulo, String detalle, String linea) {
        String normalizado() {
            return normalizar(titulo + " " + detalle);
        }
    }

    static List<Modulo> modulos(String contenido) {
        var fila = Pattern.compile("(?m)^([1-9][0-9]*)\\.\\h+([^:\\n]+):\\h*(.+)$").matcher(contenido);
        List<Modulo> modulos = new ArrayList<>();
        while (fila.find()) {
            if (!fila.group(1).equals(Integer.toString(modulos.size() + 1))) return List.of();
            modulos.add(new Modulo(fila.group(2).strip(), fila.group(3).strip(), fila.group().strip()));
        }
        return modulos.size() >= 2 ? modulos : List.of();
    }

    static String malla(String nombre, String contenido, boolean pideAplicacion) {
        var modulos = modulos(contenido);
        if (modulos.isEmpty()) return null;
        String moduloPlan = null;
        if (pideAplicacion) {
            for (Modulo modulo : modulos) {
                if (modulo.titulo().toLowerCase(Locale.ROOT).startsWith("plan de ")) moduloPlan = modulo.titulo();
            }
            if (moduloPlan == null) return null;
        }
        String respuesta = "En " + nombre + " verás estos módulos:\n\n" + String.join("\n", modulos.stream().map(Modulo::linea).toList());
        if (pideAplicacion) {
            respuesta += "\n\nPara ponerlo en práctica, elijan en equipo una situación real"
                    + " y elaboren un plan siguiendo el módulo «" + moduloPlan + "».";
        }
        return respuesta;
    }

    static String relacionModulos(String pregunta, String curso, String contenido) {
        String consulta = normalizar(pregunta);
        if (!consulta.matches(".*\\b(relacion[a-z]*|conecta[a-z]*|veria sobre)\\b.*")
                || consulta.matches(".*\\b(precio|cuesta|duracion|fecha|docente|certific[a-z]*|requisito[a-z]*|garantiz[a-z]*)\\b.*")) return null;
        var elegidos = modulos(contenido).stream()
                .filter(modulo -> (" " + consulta + " ").contains(" " + normalizar(modulo.titulo()) + " ")).toList();
        if (elegidos.size() < 2) return null;
        String respuesta = "En " + curso + " trabajarás estos temas:\n\n"
                + elegidos.stream().map(modulo -> "- " + modulo.titulo() + ": " + modulo.detalle())
                        .collect(java.util.stream.Collectors.joining("\n"));
        return respuesta + "\n\nPuedes relacionarlos al trabajar sobre un mismo caso de tu empresa,"
                + " utilizando los aspectos que describe cada módulo.";
    }

    static String temasRelevantes(String pregunta, String curso, String contenido) {
        String consulta = normalizar(pregunta);
        if (!consulta.matches(".*\\b(aprender[a-z]*|temas|practicas?|veria|ensen[a-z]*)\\b.*")
                || consulta.matches(".*\\b(precios?|cuestan?|duracion|horas|fechas?|docentes?|certific[a-z]*|requisitos?|garanti[a-z]*)\\b.*")) return null;
        var modulos = modulos(contenido);
        if (modulos.isEmpty()) return null;
        consulta = consulta.replace(normalizar(curso), "");
        Set<String> comunes = Set.of("puedo", "podria", "curso", "programa", "empresa", "aprender", "aprenderia",
                "temas", "saber", "quiero", "sobre", "antes", "despues", "equipo", "trabajo", "primero",
                "tenemos", "varios", "mismo", "nuestro", "estos", "tengo", "estamos", "hacer", "haria");
        Set<String> palabras = Arrays.stream(consulta.split(" ")).filter(palabra -> palabra.length() >= 5)
                .filter(palabra -> !comunes.contains(palabra)).map(palabra -> palabra.substring(0, 5))
                .collect(java.util.stream.Collectors.toSet());
        var vocabularios = modulos.stream().map(modulo -> Arrays.stream(modulo.normalizado().split(" "))
                .filter(palabra -> palabra.length() >= 5).map(palabra -> palabra.substring(0, 5))
                .collect(java.util.stream.Collectors.toSet())).toList();
        palabras.removeIf(palabra -> vocabularios.stream().filter(vocabulario -> vocabulario.contains(palabra)).count()
                > modulos.size() / 2);
        var puntos = consulta.split("\\s+y\\s+(?=(?:como|que|cual|cuando|para que|evaluar|definir|revisar|medir|comparar|preparar|rastrear|organizar|conducir|limitar|priorizar|recuperar|decidir)\\b)");
        for (String punto : puntos) {
            boolean respaldado = Arrays.stream(punto.split(" ")).filter(palabra -> palabra.length() >= 5)
                    .map(palabra -> palabra.substring(0, 5)).filter(palabras::contains)
                    .anyMatch(palabra -> vocabularios.stream().anyMatch(vocabulario -> vocabulario.contains(palabra)));
            if (!respaldado) return null;
        }
        var puntuaciones = vocabularios.stream().map(vocabulario -> palabras.stream().filter(vocabulario::contains).count()).toList();
        if (puntuaciones.stream().mapToLong(Long::longValue).max().orElse(0) < 2) return null;
        List<Modulo> pertinentes = new ArrayList<>();
        for (int i = 0; i < modulos.size(); i++) {
            if (puntuaciones.get(i) > 0) pertinentes.add(modulos.get(i));
        }
        if (pertinentes.size() > 3) return null;
        return "En " + curso + " verás estos temas relacionados con tu consulta:\n\n"
                + pertinentes.stream().map(modulo -> "- " + modulo.titulo() + ": " + modulo.detalle())
                        .collect(java.util.stream.Collectors.joining("\n"));
    }

    static String obtener(String pregunta, Long programa, List<DocumentoChunk> fuentes) {
        String consulta = normalizar(pregunta);
        if (programa != null) consulta = consulta.replaceFirst("^y ", "").replaceFirst(" ese (curso|programa)$", " este curso");
        if (programa != null && Set.of("cuanto dura", "cuanto cuesta", "cuanto vale").contains(consulta)) consulta += " este curso";
        if (programa != null && Set.of("que voy a aprender", "que temas tiene este curso", "que temas ofrece este curso", "cuales son los modulos", "que temas incluye", "que temas incluye este curso", "cual es el temario", "cual es el temario de este curso").contains(consulta)) {
            Set<String> temarios = new LinkedHashSet<>();
            for (var fuente : fuentes) {
                var datos = fuente.getMetadata();
                if (!programa.equals(fuente.getIdPrograma()) || datos == null
                        || !Set.of("PROPUESTA", "VIGENTE").contains(datos.get("estado"))
                        || !"Temario propuesto".equals(datos.get("seccion"))) continue;
                var lineas = Pattern.compile("(?m)^([1-4])\\. +([^:\\n]+):").matcher(fuente.getContenido());
                Map<Integer, String> modulos = new LinkedHashMap<>();
                while (lineas.find()) modulos.put(Integer.parseInt(lineas.group(1)), lineas.group(2).strip());
                if (modulos.keySet().equals(Set.of(1,2,3,4))) {
                    temarios.add("Los módulos son:\n" + modulos.entrySet().stream()
                            .map(m -> m.getKey() + ". " + m.getValue())
                            .collect(java.util.stream.Collectors.joining("\n")));
                }
            }
            return temarios.size() == 1 ? temarios.iterator().next() : null;
        }
        var intencion = Pattern.compile("^(cuanto cuesta|cuanto vale|cual es el precio de|precio de|cuanto dura|cuantas horas dura|cual es la duracion de) (.+)$").matcher(consulta);
        if (!intencion.matches()) return null;
        boolean precio = intencion.group(1).contains("cuesta") || intencion.group(1).contains("vale") || intencion.group(1).contains("precio");
        String objeto = intencion.group(2).replaceFirst("^(el curso|el programa) ", "");
        Set<String> respuestas = new LinkedHashSet<>();
        for (var fuente : fuentes) {
            var datos = fuente.getMetadata();
            if (datos == null || !"VIGENTE".equals(datos.get("estado"))) continue;
            if (programa != null && !programa.equals(fuente.getIdPrograma())) continue;
            String titulo = String.valueOf(datos.getOrDefault("titulo", ""));
            if (!titulo.startsWith("Curso: ")) continue;
            String nombre = titulo.substring(7).strip();
            boolean seleccionado = programa != null && Set.of("este curso", "el curso", "este programa", "el programa").contains(objeto);
            if (!seleccionado && !normalizar(nombre).equals(objeto)) continue;
            String contenido = normalizarDatos(fuente.getContenido());
            if (!precio && "duracion".equals(normalizar(String.valueOf(datos.getOrDefault("seccion", ""))))) {
                contenido = "Duracion registrada de " + contenido;
            }
            var patron = precio
                    ? Pattern.compile("precio registrado (?:de|es) (USD|PEN) ([0-9]+\\.[0-9]{2})(?![0-9])", Pattern.CASE_INSENSITIVE)
                    : Pattern.compile("duracion registrada (?:de|es) ([0-9]+) horas\\b", Pattern.CASE_INSENSITIVE);
            var coincidencia = patron.matcher(contenido);
            while (coincidencia.find()) respuestas.add(precio
                    ? nombre + " cuesta " + coincidencia.group(1) + " " + coincidencia.group(2) + "."
                    : nombre + " dura " + coincidencia.group(1) + " horas.");
        }
        // Ante discrepancias se conserva la respuesta contextual del modelo.
        return respuestas.size() == 1 ? respuestas.iterator().next() : null;
    }

    static boolean esConsultaCatalogo(String pregunta) {
        return Set.of("que cursos tienen", "que cursos hay", "que cursos ofrecen", "que cursos tienen disponibles",
                "cuales son los cursos", "que programas tienen", "que programas ofrecen", "cuales son los programas",
                "que temas ofrecen", "cuanto cuestan los cursos", "cuanto cuestan los programas",
                "cuales son los precios", "cuanto duran los cursos", "cuanto duran los programas")
                .contains(normalizar(pregunta));
    }

    private record DatoCurso(String nombre, String moneda, BigDecimal precio, int horas) {}

    static String catalogo(String pregunta, List<DocumentoChunk> fuentes, List<Programa> programas) {
        if (!esConsultaCatalogo(pregunta) || programas.isEmpty()) return null;
        Map<String, DatoCurso> datos = new LinkedHashMap<>();
        Set<Long> documentos = new HashSet<>();
        Pattern fila = Pattern.compile("(?m)^- (.+): (USD|PEN) ([0-9]+\\.[0-9]{2})(?:, |\\. Duracion registrada: )([0-9]{1,6}) horas\\.\\s*$");
        for (var fuente : fuentes) {
            var metadata = fuente.getMetadata();
            if (fuente.getIdPrograma() != null || metadata == null
                    || !"VIGENTE".equals(metadata.get("estado"))
                    || !"catalogo de lysandri executive".equals(normalizar(String.valueOf(metadata.get("titulo"))))) continue;
            documentos.add(fuente.getIdDocumento());
            var lineas = fila.matcher(fuente.getContenido());
            while (lineas.find()) {
                var dato = new DatoCurso(lineas.group(1), lineas.group(2), new BigDecimal(lineas.group(3)),
                        Integer.parseInt(lineas.group(4)));
                var anterior = datos.putIfAbsent(normalizar(dato.nombre()), dato);
                if (anterior != null && !anterior.equals(dato)) return null;
            }
        }
        if (documentos.size() != 1 || datos.size() != programas.size()) return null;
        for (var programa : programas) {
            var dato = datos.get(normalizar(programa.getTitulo()));
            if (dato == null || programa.getPrecio() == null || programa.getDuracionHoras() == null
                    || dato.precio().compareTo(programa.getPrecio()) != 0
                    || !dato.moneda().equals(programa.getMoneda())
                    || dato.horas() != programa.getDuracionHoras()) return null;
        }
        String consulta = normalizar(pregunta);
        boolean precios = consulta.contains("cuestan") || consulta.contains("precios");
        boolean duracion = consulta.contains("duran");
        List<String> lineas = new ArrayList<>();
        for (var dato : datos.values()) {
            String detalle = "";
            if (precios) {
                detalle = ": " + dato.moneda() + " " + dato.precio().toPlainString();
            } else if (duracion) {
                detalle = ": " + dato.horas() + " horas";
            }
            lineas.add("- " + dato.nombre() + detalle);
        }
        String inicio = "Estos son los cursos que tenemos:";
        if (precios) {
            inicio = "Estos son los precios:";
        } else if (duracion) {
            inicio = "La duración de los cursos es:";
        }
        return inicio + "\n\n" + String.join("\n", lineas);
    }

    static String normalizar(String texto) {
        return normalizarDatos(LenguajeConsulta.interpretar(texto)).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").strip().replaceAll("\\s+", " ");
    }

    private static String normalizarDatos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}

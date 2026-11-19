package com.lysandri.application.service;

import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.model.Programa;
import com.lysandri.domain.ports.out.DocumentoRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ConsultaTemario {
    private static final Pattern OTROS_DATOS = Pattern.compile(
            "\\b(precios?|cuanto (?:me )?(?:cuestan?|valen?|costaria)|duracion|horas|fechas?|empieza|inicia|docentes?|profesor|modalidad|horarios?|certific[a-z]*|requisitos?|matricula|inscripci[a-z]*)\\b");
    private static final Pattern REVISION = Pattern.compile("\\b(revision humana|revisar (?:las |sus )?respuestas)\\b");
    private static final Pattern PILOTO = Pattern.compile("\\b(piloto|prueba piloto)\\b");
    private static final Pattern EVALUACION = Pattern.compile("\\b(indicadores|criterios|medir|evaluar|exitoso|funciono)\\b");
    private static final Pattern ELEGIR_CURSO = Pattern.compile("\\b(cual (?:curso|me conviene|se acerca|me ayudaria|encaja)|que curso (?:me conviene|me ayudaria)|cual elegir)\\b");
    private static final Pattern FLUJO = Pattern.compile(
            "\\b(visualizar (?:el )?(?:flujo|trabajo)|limitar (?:el )?trabajo en curso|(?:muchas(?: tareas)?|demasiadas tareas) (?:al mismo tiempo|a la vez))\\b");

    record Resultado(String respuesta, List<DocumentoChunk> fuentes) {}
    private record Temario(Programa curso, DocumentoChunk fuente, List<RespuestaBreve.Modulo> modulos) {}

    private ConsultaTemario() {}

    static Resultado buscar(String texto, Supplier<List<Programa>> activos, DocumentoRepositoryPort documentos) {
        String pregunta = RespuestaBreve.normalizar(texto);
        if (OTROS_DATOS.matcher(pregunta).find() || texto.indexOf('?') != texto.lastIndexOf('?')) return null;

        boolean revisionYPiloto = REVISION.matcher(pregunta).find() && PILOTO.matcher(pregunta).find()
                && EVALUACION.matcher(pregunta).find();
        boolean compararFlujo = ELEGIR_CURSO.matcher(pregunta).find() && FLUJO.matcher(pregunta).find();
        if (revisionYPiloto == compararFlujo) return null;

        List<Programa> cursos = activos.get().stream()
                .filter(curso -> !RespuestaBreve.normalizar(curso.getTitulo()).isBlank())
                .filter(curso -> (" " + pregunta + " ").contains(" " + RespuestaBreve.normalizar(curso.getTitulo()) + " "))
                .toList();
        if (cursos.size() != (revisionYPiloto ? 1 : 2)) return null;

        List<Temario> temarios = new ArrayList<>();
        for (Programa curso : cursos) {
            var opciones = documentos.buscarTemarios(curso.getIdPrograma()).stream()
                    .map(fuente -> new Temario(curso, fuente, RespuestaBreve.modulos(fuente.getContenido())))
                    .filter(temario -> !temario.modulos().isEmpty())
                    .toList();
            if (opciones.size() != 1) return null;
            temarios.add(opciones.get(0));
        }

        return revisionYPiloto ? revisionYPiloto(temarios.get(0)) : compararFlujo(temarios);
    }

    private static Resultado revisionYPiloto(Temario temario) {
        var revision = temario.modulos().stream().filter(m -> REVISION.matcher(m.normalizado()).find()).toList();
        var piloto = temario.modulos().stream().filter(m -> PILOTO.matcher(m.normalizado()).find()
                && EVALUACION.matcher(m.normalizado()).find()).toList();
        if (revision.size() != 1 || piloto.size() != 1 || revision.get(0).equals(piloto.get(0))) return null;

        String respuesta = "En " + temario.curso().getTitulo() + " estos son los módulos relacionados con tu consulta.\n\n"
                + "- " + revision.get(0).titulo() + ": " + revision.get(0).detalle() + "\n"
                + "- " + piloto.get(0).titulo() + ": " + piloto.get(0).detalle();
        return new Resultado(respuesta, List.of(temario.fuente()));
    }

    private static Resultado compararFlujo(List<Temario> temarios) {
        var pertinentes = temarios.stream().filter(temario -> temario.modulos().stream().anyMatch(modulo ->
                modulo.normalizado().contains("limites de trabajo en curso")
                        || modulo.normalizado().contains("visualizacion del flujo"))).toList();
        if (pertinentes.size() != 1) return null;

        Temario recomendado = pertinentes.get(0);
        var modulo = recomendado.modulos().stream().filter(m -> m.normalizado().contains("limites de trabajo en curso")
                || m.normalizado().contains("visualizacion del flujo")).findFirst().orElseThrow();
        String resumen = temarios.stream().map(temario -> "- " + temario.curso().getTitulo() + ": "
                + temario.modulos().stream().map(RespuestaBreve.Modulo::titulo).collect(Collectors.joining(", ")) + ".")
                .collect(Collectors.joining("\n"));
        String respuesta = "Para organizar el trabajo en curso, te conviene " + recomendado.curso().getTitulo()
                + ". Su módulo " + modulo.titulo() + " aborda directamente ese problema.\n\n"
                + modulo.detalle() + "\n\n" + resumen;
        return new Resultado(respuesta, temarios.stream().map(Temario::fuente).toList());
    }
}


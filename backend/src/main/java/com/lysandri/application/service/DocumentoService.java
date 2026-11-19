package com.lysandri.application.service;

import com.lysandri.domain.model.Documento;
import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.ports.out.DocumentoRepositoryPort;
import com.lysandri.domain.ports.out.LlmClientPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentoService {
    private final LlmClientPort llm;
    private final DocumentoRepositoryPort documentos;
    @Value("${llm.provider:ollama}") private String proveedor = "openai";
    @Value("${llm.embedding-model:text-embedding-3-small}") private String modelo = "text-embedding-3-small";
    @Value("${llm.ollama.embedding-model:embeddinggemma:latest}") private String modeloLocal = "embeddinggemma:latest";
    @Value("${llm.ollama.dimensions:768}") private int dimensionesLocales = 768;

    public record Carga(String titulo, String version, String estado, Long idPrograma, String contenido) {}
    public record Resultado(Long idDocumento, int fragmentos, String checksum, String version) {}

    public Resultado cargar(Carga carga) {
        if (carga.titulo() == null || carga.titulo().isBlank() || carga.titulo().length() > 200
                || carga.version() == null || carga.version().isBlank() || carga.version().length() > 40
                || !List.of("PROPUESTA", "VIGENTE").contains(carga.estado() == null ? "" : carga.estado())
                || (carga.idPrograma() != null && carga.idPrograma() <= 0)
                || carga.contenido() == null || carga.contenido().isBlank() || carga.contenido().length() > 100000) {
            throw new IllegalArgumentException("Indique título, versión, estado y texto válido de hasta 100000 caracteres.");
        }
        String texto = carga.contenido().replace("\r\n", "\n").replace("\r", "\n").strip();
        // Se generan los vectores antes de abrir la transacción de guardado.
        List<DocumentoChunk> fragmentos = dividir(texto).stream().map(parte -> {
            String contexto = carga.titulo().strip() + " | Versión " + carga.version().strip()
                    + " | Estado " + carga.estado() + " | " + parte.seccion() + "\n" + parte.texto();
            float[] vector = llm.generarEmbedding(contexto);
            validarVector(vector, "ollama".equals(proveedor) ? dimensionesLocales : 1536);
            return DocumentoChunk.builder().idPrograma(carga.idPrograma()).contenido(parte.texto())
                    .embedding(vector).metadata(Map.of("seccion", parte.seccion(), "version", carga.version().strip(),
                            "estado", carga.estado(), "embeddingModelo", "ollama".equals(proveedor) ? modeloLocal : modelo, "embeddingProveedor", proveedor)).build();
        }).toList();
        String checksum;
        try {
            checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (carga.version().strip() + "\n" + carga.estado() + "\n" + texto).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
        Documento documento = Documento.builder().titulo(carga.titulo().strip()).idPrograma(carga.idPrograma())
                .tipoDocumento("SYLLABUS").activo(true).checksumSha256(checksum).build();
        Long id = documentos.guardar(documento, fragmentos);
        return new Resultado(id, fragmentos.size(), checksum, carga.version().strip());
    }

    record Parte(String seccion, String texto) {}

    List<Parte> dividir(String texto) {
        List<Parte> partes = new ArrayList<>();
        String seccion = "Presentación";
        StringBuilder bloque = new StringBuilder();
        for (String linea : texto.split("\n")) {
            if (linea.matches("^#{1,6}\\s+.+")) {
                agregar(partes, seccion, bloque.toString());
                bloque.setLength(0);
                seccion = linea.replaceFirst("^#{1,6}\\s+", "").strip();
            } else {
                bloque.append(linea).append('\n');
            }
        }
        agregar(partes, seccion, bloque.toString());
        if (partes.isEmpty()) throw new IllegalArgumentException("El documento no contiene texto para indexar.");
        return partes;
    }

    private void agregar(List<Parte> partes, String seccion, String bloque) {
        String texto = bloque.strip();
        while (!texto.isEmpty()) {
            int fin = Math.min(1400, texto.length());
            if (fin < texto.length()) {
                int corte = texto.lastIndexOf(' ', fin);
                if (corte > 700) fin = corte;
            }
            partes.add(new Parte(seccion, texto.substring(0, fin).strip()));
            texto = texto.substring(fin).strip();
        }
    }

    public static void validarVector(float[] vector) {
        validarVector(vector, 1536);
    }

    public static void validarVector(float[] vector, int dimensiones) {
        if (dimensiones <= 0 || vector == null || vector.length != dimensiones) {
            throw new IllegalArgumentException("El vector no coincide con las dimensiones del modelo configurado.");
        }
        double norma = 0;
        for (float valor : vector) {
            if (!Float.isFinite(valor)) throw new IllegalArgumentException("El vector contiene valores inválidos.");
            norma += (double) valor * valor;
        }
        if (norma == 0) throw new IllegalArgumentException("El vector no puede ser nulo.");
    }
}

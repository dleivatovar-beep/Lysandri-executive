package com.lysandri.adapters.out.database.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.domain.model.Documento;
import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.ports.out.DocumentoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DocumentoRepositoryAdapter implements DocumentoRepositoryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentoChunk> buscarTemarios(Long idPrograma) {
        return jdbc.query("""
                SELECT c.id_chunk, c.id_documento, c.numero_pagina, c.contenido, d.titulo,
                       c.metadata->>'seccion' AS seccion, COALESCE(c.metadata->>'version', '') AS version,
                       c.metadata->>'estado' AS estado
                FROM documento_chunk c
                JOIN documento d ON d.id_documento = c.id_documento AND d.activo = true
                WHERE c.id_programa = ? AND d.id_programa = c.id_programa
                  AND c.metadata->>'seccion' IN ('Temario', 'Temario propuesto', 'Malla curricular')
                  AND c.metadata->>'estado' IN ('PROPUESTA', 'VIGENTE')
                ORDER BY c.id_chunk
                """, (row, index) -> DocumentoChunk.builder()
                .idChunk(row.getLong("id_chunk"))
                .idDocumento(row.getLong("id_documento"))
                .idPrograma(idPrograma)
                .numeroPagina(row.getObject("numero_pagina", Integer.class))
                .contenido(row.getString("contenido"))
                .metadata(java.util.Map.of("titulo", row.getString("titulo"), "seccion", row.getString("seccion"),
                        "version", row.getString("version"), "estado", row.getString("estado")))
                .build(), idPrograma);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentoChunk> buscarFicha(Long idPrograma) {
        return jdbc.query("""
                SELECT c.id_chunk, c.id_documento, c.contenido, d.titulo,
                       COALESCE(c.metadata->>'seccion', '') AS seccion,
                       COALESCE(c.metadata->>'version', '') AS version,
                       c.metadata->>'estado' AS estado
                FROM documento_chunk c
                JOIN documento d ON d.id_documento = c.id_documento AND d.activo
                WHERE c.id_programa = ? AND d.id_programa = c.id_programa
                  AND (
                      (d.titulo LIKE 'Curso: %' AND c.metadata->>'estado' = 'VIGENTE')
                      OR (c.metadata->>'seccion' = 'Condiciones por confirmar'
                          AND c.metadata->>'estado' IN ('PROPUESTA', 'VIGENTE'))
                  )
                ORDER BY c.id_chunk
                LIMIT 16
                """, (row, index) -> DocumentoChunk.builder()
                .idChunk(row.getLong("id_chunk")).idDocumento(row.getLong("id_documento")).idPrograma(idPrograma)
                .contenido(row.getString("contenido"))
                .metadata(java.util.Map.of("titulo", row.getString("titulo"), "seccion", row.getString("seccion"),
                        "version", row.getString("version"), "estado", row.getString("estado")))
                .build(), idPrograma);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentoChunk> buscarPorTitulo(String titulo, int limite) {
        return jdbc.query("""
                SELECT c.id_chunk, c.id_documento, c.numero_pagina, c.contenido, d.titulo,
                       COALESCE(c.metadata->>'seccion', '') AS seccion,
                       COALESCE(c.metadata->>'version', '') AS version,
                       COALESCE(c.metadata->>'estado', '') AS estado
                FROM documento_chunk c
                JOIN documento d ON d.id_documento = c.id_documento
                WHERE d.activo AND d.id_programa IS NULL AND lower(d.titulo) = lower(?)
                ORDER BY c.id_chunk
                LIMIT ?
                """, (row, index) -> DocumentoChunk.builder()
                .idChunk(row.getLong("id_chunk"))
                .idDocumento(row.getLong("id_documento"))
                .numeroPagina(row.getObject("numero_pagina", Integer.class))
                .contenido(row.getString("contenido"))
                .metadata(java.util.Map.of("titulo", row.getString("titulo"), "seccion", row.getString("seccion"),
                        "version", row.getString("version"), "estado", row.getString("estado")))
                .build(), titulo, limite);
    }

    @Override
    @Transactional
    public Long guardar(Documento documento, List<DocumentoChunk> fragmentos) {
        String clave = documento.getTitulo() + "|" + documento.getIdPrograma();
        // También bloquea la primera carga, cuando todavía no existe una fila que bloquear.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", clave);
        if (documento.getIdPrograma() != null && Boolean.FALSE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM programa WHERE id_programa = ?)", Boolean.class, documento.getIdPrograma()))) {
            throw new IllegalArgumentException("El programa indicado no existe.");
        }
        List<Long> existentes = jdbc.queryForList("""
                SELECT id_documento FROM documento
                WHERE titulo = ? AND id_programa IS NOT DISTINCT FROM CAST(? AS bigint)
                ORDER BY id_documento FOR UPDATE
                """, Long.class, documento.getTitulo(), documento.getIdPrograma());
        Long id;
        if (existentes.isEmpty()) {
            id = jdbc.queryForObject("""
                    INSERT INTO documento (titulo, id_programa, tipo_documento, checksum_sha256, activo)
                    VALUES (?, ?, ?, ?, true) RETURNING id_documento
                    """, Long.class, documento.getTitulo(), documento.getIdPrograma(), documento.getTipoDocumento(), documento.getChecksumSha256());
        } else {
            if (existentes.size() != 1) throw new IllegalStateException("Existen documentos duplicados; deben revisarse antes de cargar.");
            id = existentes.get(0);
            jdbc.update("DELETE FROM documento_chunk WHERE id_documento = ?", id);
            jdbc.update("UPDATE documento SET checksum_sha256 = ?, activo = true, fecha_subida = CURRENT_TIMESTAMP WHERE id_documento = ?",
                    documento.getChecksumSha256(), id);
        }
        for (DocumentoChunk fragmento : fragmentos) {
            boolean local = "ollama".equals(fragmento.getMetadata().get("embeddingProveedor"));
            String columna = local ? "embedding_local" : "embedding";
            try {
                jdbc.update("""
                        INSERT INTO documento_chunk (id_documento, id_programa, contenido, metadata, %s)
                        VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS vector))
                        """.formatted(columna), id, documento.getIdPrograma(), fragmento.getContenido(),
                        mapper.writeValueAsString(fragmento.getMetadata()), Arrays.toString(fragmento.getEmbedding()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("No se pudieron guardar las referencias del documento.", e);
            }
        }
        return id;
    }
}

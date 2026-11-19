package com.lysandri.adapters.out.database.adapter;

import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.ports.out.VectorStorePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PgVectorAdapter implements VectorStorePort {

    @PersistenceContext
    private final EntityManager entityManager;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Value("${llm.provider:ollama}") private String proveedor = "openai";
    @Value("${llm.embedding-model:text-embedding-3-small}") private String modelo = "text-embedding-3-small";
    @Value("${llm.ollama.embedding-model:embeddinggemma:latest}") private String modeloLocal = "embeddinggemma:latest";
    @Value("${llm.ollama.dimensions:768}") private int dimensionesLocales = 768;

    @Override
    @SuppressWarnings("unchecked")
    public List<DocumentoChunk> buscarSimilares(
            float[] queryEmbedding,
            int topK,
            double minSimilarity,
            Long programaId) {

        if (queryEmbedding == null || queryEmbedding.length == 0) {
            log.warn("Vector de embedding vacío para búsqueda semántica.");
            return List.of();
        }

        boolean local = "ollama".equals(proveedor);
        com.lysandri.application.service.DocumentoService.validarVector(queryEmbedding, local ? dimensionesLocales : 1536);
        String columna = local ? "embedding_local" : "embedding";
        String vectorLiteral = Arrays.toString(queryEmbedding);

        String sql = """
            SELECT 
                c.id_chunk,
                c.id_documento,
                c.id_programa,
                c.numero_pagina,
                c.contenido,
                (1.0 - (CASE WHEN vector_dims(c.embedding) = :dimensiones THEN c.embedding <=> cast(:vectorText as vector) ELSE NULL END)) AS score_similitud,
                d.titulo,
                COALESCE(c.metadata->>'seccion', ''),
                COALESCE(c.metadata->>'version', ''),
                COALESCE(c.metadata->>'estado', '')
            FROM documento_chunk c
            JOIN documento d ON d.id_documento = c.id_documento AND d.activo = true
            WHERE (CAST(:programaId AS bigint) IS NULL OR c.id_programa = :programaId)
              AND c.metadata->>'embeddingModelo' = :modelo
              AND COALESCE(c.metadata->>'embeddingProveedor', 'openai') = :proveedor
              AND vector_dims(c.embedding) = :dimensiones
              AND (1.0 - (CASE WHEN vector_dims(c.embedding) = :dimensiones THEN c.embedding <=> cast(:vectorText as vector) ELSE NULL END)) >= :minSimilarity
            ORDER BY CASE WHEN vector_dims(c.embedding) = :dimensiones THEN c.embedding <=> cast(:vectorText as vector) ELSE NULL END ASC
            LIMIT :topK
            """.replace("c.embedding", "c." + columna);

        try {
            Query nativeQuery = entityManager.createNativeQuery(sql);
            nativeQuery.setParameter("vectorText", vectorLiteral);
            nativeQuery.setParameter("programaId", programaId);
            nativeQuery.setParameter("minSimilarity", minSimilarity);
            nativeQuery.setParameter("topK", topK);
            nativeQuery.setParameter("modelo", local ? modeloLocal : modelo);
            nativeQuery.setParameter("proveedor", proveedor);
            nativeQuery.setParameter("dimensiones", queryEmbedding.length);

            List<Object[]> results = nativeQuery.getResultList();
            List<DocumentoChunk> chunks = new ArrayList<>();

            for (Object[] row : results) {
                chunks.add(DocumentoChunk.builder()
                        .idChunk(((Number) row[0]).longValue())
                        .idDocumento(((Number) row[1]).longValue())
                        .idPrograma(row[2] != null ? ((Number) row[2]).longValue() : null)
                        .numeroPagina(row[3] != null ? ((Number) row[3]).intValue() : null)
                        .contenido((String) row[4])
                        .similitud(((Number) row[5]).doubleValue())
                        .metadata(Map.of("titulo", (String) row[6], "seccion", (String) row[7],
                                "version", (String) row[8], "estado", (String) row[9]))
                        .build());
            }

            log.debug("pgvector: encontrados {} fragmentos con similitud >= {}", chunks.size(), minSimilarity);
            return chunks;
        } catch (Exception e) {
            log.error("Error ejecutando búsqueda vectorial en pgvector: {}", e.getMessage(), e);
            throw new IllegalStateException("Error al consultar el almacén vectorial pgvector: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void guardarChunk(DocumentoChunk chunk) {
        boolean local = "ollama".equals(proveedor);
        com.lysandri.application.service.DocumentoService.validarVector(chunk.getEmbedding(), local ? dimensionesLocales : 1536);
        String vectorLiteral = Arrays.toString(chunk.getEmbedding());
        String sql = """
            INSERT INTO documento_chunk (id_documento, id_programa, numero_pagina, contenido, %s, metadata)
            VALUES (:idDoc, :idProg, :numPag, :contenido, cast(:embedding as vector), cast(:metadata as jsonb))
            """.formatted(local ? "embedding_local" : "embedding");

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("idDoc", chunk.getIdDocumento());
        query.setParameter("idProg", chunk.getIdPrograma());
        query.setParameter("numPag", chunk.getNumeroPagina());
        query.setParameter("contenido", chunk.getContenido());
        query.setParameter("embedding", vectorLiteral);
        var metadata = new java.util.HashMap<String, Object>();
        if (chunk.getMetadata() != null) metadata.putAll(chunk.getMetadata());
        metadata.put("embeddingProveedor", proveedor);
        metadata.put("embeddingModelo", local ? modeloLocal : modelo);
        try {
            query.setParameter("metadata", mapper.writeValueAsString(metadata));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("No se pudieron serializar las referencias del fragmento.", e);
        }
        query.executeUpdate();
    }
}

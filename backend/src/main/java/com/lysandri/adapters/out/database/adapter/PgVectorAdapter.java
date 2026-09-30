package com.lysandri.adapters.out.database.adapter;

import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.ports.out.VectorStorePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PgVectorAdapter implements VectorStorePort {

    @PersistenceContext
    private final EntityManager entityManager;

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

        String vectorLiteral = Arrays.toString(queryEmbedding);

        String sql = """
            SELECT 
                c.id_chunk,
                c.id_documento,
                c.id_programa,
                c.numero_pagina,
                c.contenido,
                (1.0 - (c.embedding <=> cast(:vectorText as vector))) AS score_similitud
            FROM documento_chunk c
            WHERE (:programaId IS NULL OR c.id_programa = :programaId)
              AND (1.0 - (c.embedding <=> cast(:vectorText as vector))) >= :minSimilarity
            ORDER BY c.embedding <=> cast(:vectorText as vector) ASC
            LIMIT :topK
            """;

        try {
            Query nativeQuery = entityManager.createNativeQuery(sql);
            nativeQuery.setParameter("vectorText", vectorLiteral);
            nativeQuery.setParameter("programaId", programaId);
            nativeQuery.setParameter("minSimilarity", minSimilarity);
            nativeQuery.setParameter("topK", topK);

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
                        .build());
            }

            log.debug("pgvector: encontrados {} fragmentos con similitud >= {}", chunks.size(), minSimilarity);
            return chunks;
        } catch (Exception e) {
            log.error("Error ejecutando búsqueda vectorial en pgvector: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @Transactional
    public void guardarChunk(DocumentoChunk chunk) {
        String vectorLiteral = Arrays.toString(chunk.getEmbedding());
        String sql = """
            INSERT INTO documento_chunk (id_documento, id_programa, numero_pagina, contenido, embedding)
            VALUES (:idDoc, :idProg, :numPag, :contenido, cast(:embedding as vector))
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("idDoc", chunk.getIdDocumento());
        query.setParameter("idProg", chunk.getIdPrograma());
        query.setParameter("numPag", chunk.getNumeroPagina());
        query.setParameter("contenido", chunk.getContenido());
        query.setParameter("embedding", vectorLiteral);
        query.executeUpdate();
    }
}

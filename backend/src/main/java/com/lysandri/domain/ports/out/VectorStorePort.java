package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.DocumentoChunk;

import java.util.List;

public interface VectorStorePort {

    /**
     * Ejecuta una búsqueda semántica de vecinos más cercanos utilizando la distancia coseno (<=>).
     *
     * @param queryEmbedding Vector de 1536 dimensiones que representa la pregunta del usuario.
     * @param topK Cantidad máxima de fragmentos relevantes a recuperar.
     * @param minSimilarity Umbral mínimo de similitud coseno (entre 0.0 y 1.0).
     * @param programaId Filtro opcional por programa ejecutivo (puede ser null para búsqueda global).
     * @return Lista de fragmentos ordenados descendentemente por relevancia.
     */
    List<DocumentoChunk> buscarSimilares(
            float[] queryEmbedding,
            int topK,
            double minSimilarity,
            Long programaId
    );

    void guardarChunk(DocumentoChunk chunk);
}

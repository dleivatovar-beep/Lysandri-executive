package com.lysandri.adapters;

import com.lysandri.application.service.DocumentoService;
import com.lysandri.domain.ports.out.LlmClientPort;
import com.lysandri.domain.ports.out.VectorStorePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"llm.provider=ollama"})
@ActiveProfiles("test")
class OllamaPostgresIntegrationTest extends PostgresTestDatabase {
    @Autowired DocumentoService documentos;
    @Autowired VectorStorePort vectores;
    @Autowired JdbcTemplate jdbc;
    @MockBean LlmClientPort llm;

    @Test void conservaVectoresAnterioresYNoMezclaModelosODimensiones() {
        float[] consulta = new float[768]; consulta[0] = 1;
        when(llm.generarEmbedding(anyString())).thenReturn(consulta);
        var cargado = documentos.cargar(new DocumentoService.Carga("Documento local de prueba", "1", "PROPUESTA", null,
                "## Requisitos\nConocimientos básicos de gestión."));
        assertEquals(768, jdbc.queryForObject("SELECT vector_dims(embedding_local) FROM documento_chunk WHERE id_documento = ?", Integer.class, cargado.idDocumento()));
        assertNull(jdbc.queryForObject("SELECT embedding FROM documento_chunk WHERE id_documento = ?", String.class, cargado.idDocumento()));
        float[] anterior = new float[1536]; anterior[0] = 1;
        jdbc.update("INSERT INTO documento_chunk (id_documento, contenido, embedding, metadata) VALUES (?, 'Anterior', CAST(? AS vector), '{\"embeddingModelo\":\"text-embedding-3-small\"}')", cargado.idDocumento(), Arrays.toString(anterior));
        jdbc.update("INSERT INTO documento_chunk (id_documento, contenido, embedding_local, metadata) VALUES (?, 'Otro tamaño', '[1,0,0]', '{\"embeddingProveedor\":\"ollama\",\"embeddingModelo\":\"embeddinggemma:latest\"}')", cargado.idDocumento());
        jdbc.update("INSERT INTO documento_chunk (id_documento, contenido, embedding_local, metadata) VALUES (?, 'Otro modelo', CAST(? AS vector), '{\"embeddingProveedor\":\"ollama\",\"embeddingModelo\":\"otro\"}')", cargado.idDocumento(), Arrays.toString(consulta));
        var resultados = vectores.buscarSimilares(consulta, 4, 0.6, null);
        assertTrue(resultados.stream().anyMatch(c -> c.getIdDocumento().equals(cargado.idDocumento())));
        assertTrue(resultados.stream().noneMatch(c -> c.getContenido().equals("Anterior") || c.getContenido().equals("Otro tamaño") || c.getContenido().equals("Otro modelo")));
        assertEquals(1536, jdbc.queryForObject("SELECT vector_dims(embedding) FROM documento_chunk WHERE id_documento = ? AND contenido = 'Anterior'", Integer.class, cargado.idDocumento()));
    }
}

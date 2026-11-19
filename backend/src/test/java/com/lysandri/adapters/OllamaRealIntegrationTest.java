package com.lysandri.adapters;

import com.lysandri.application.service.DocumentoService;
import com.lysandri.domain.ports.in.ChatRagUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "llm.provider=ollama")
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "ollama.real", matches = "true")
class OllamaRealIntegrationTest extends PostgresTestDatabase {
    @Autowired DocumentoService documentos;
    @Autowired ChatRagUseCase chat;
    @Autowired JdbcTemplate jdbc;

    @Test void documentoModeloLocalYPostgresDevuelvenRespuestaConFuente() throws Exception {
        String texto;
        try (var archivo = getClass().getResourceAsStream("/rag/silabo.md")) {
            texto = new String(java.util.Objects.requireNonNull(archivo).readAllBytes(), StandardCharsets.UTF_8);
        }
        var carga = documentos.cargar(new DocumentoService.Carga(
                "Globalización de Mercados y Antropología del Consumidor", "2", "PROPUESTA", null, texto));
        String sesion = UUID.randomUUID().toString();
        var respuesta = chat.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                "¿Cuánto dura el programa de Globalización de Mercados y Antropología del Consumidor?", sesion, null, null));
        assertTrue(respuesta.respuesta().contains("24"));
        assertTrue(respuesta.fuentes().stream().anyMatch(f -> f.idDocumento().equals(carga.idDocumento())
                && f.seccion().startsWith("4 ") && f.estado().equals("PROPUESTA")));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM historial_consulta WHERE sesion_id = ?", Integer.class, sesion));
        var ajena = chat.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                "¿Cuál es la distancia entre la Tierra y la Luna?", UUID.randomUUID().toString(), null, null));
        assertTrue(ajena.fuentes().isEmpty());
        assertEquals(0, ajena.tokensUsados());
    }
}

package com.lysandri.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.application.service.DocumentoService;
import com.lysandri.adapters.out.database.adapter.DocumentoRepositoryAdapter;
import com.lysandri.domain.model.Documento;
import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.ports.out.LlmClientPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConocimientoPostgresIntegrationTest extends PostgresTestDatabase {
    @Autowired MockMvc web;
    @Autowired ObjectMapper mapper;
    @Autowired DocumentoService servicio;
    @Autowired DocumentoRepositoryAdapter repo;
    @Autowired JdbcTemplate jdbc;
    @MockBean LlmClientPort llm;
    private String silabo;

    @BeforeEach
    void preparar() throws Exception {
        try (var archivo = getClass().getResourceAsStream("/rag/silabo.md")) {
            silabo = new String(java.util.Objects.requireNonNull(archivo).readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbc.update("DELETE FROM documento WHERE titulo = ?", "Sílabo de prueba GM01");
        when(llm.generarEmbedding(anyString())).thenAnswer(inv -> {
            float[] vector = new float[1536];
            vector[inv.<String>getArgument(0).contains("astronomía") ? 1 : 0] = 1;
            return vector;
        });
        when(llm.completarChat(anyString(), anyString(), anyDouble()))
                .thenReturn(new LlmClientPort.LlmCompletion("Respuesta controlada para comprobar el recorrido HTTP.", 10, 10, 20));
    }

    private DocumentoService.Carga carga() {
        return new DocumentoService.Carga("Sílabo de prueba GM01", "2", "PROPUESTA", null, silabo);
    }

    @Test
    void cargaHttpYConsultaDevuelvenCitasDeSecciones() throws Exception {
        var peticion = mapper.writeValueAsString(carga());
        var resultado = web.perform(post("/api/v1/admin/conocimiento").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content(peticion)).andExpect(status().isOk()).andReturn();
        long id = mapper.readTree(resultado.getResponse().getContentAsString()).get("idDocumento").asLong();
        web.perform(post("/api/v1/chat/rag").contentType("application/json")
                .content("{\"pregunta\":\"¿Qué preparación previa solicita el programa?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fuentes[0].idDocumento").value(id))
                .andExpect(jsonPath("$.fuentes[0].titulo").value("Sílabo de prueba GM01"))
                .andExpect(jsonPath("$.fuentes[0].version").value("2"))
                .andExpect(jsonPath("$.fuentes[0].estado").value("PROPUESTA"))
                .andExpect(jsonPath("$.fuentes[0].seccion").isNotEmpty())
                .andExpect(jsonPath("$.sesionId").isNotEmpty());
    }

    @Test
    void comparaElCatalogoActualSinConsultarAlModelo() throws Exception {
        String titulo = "Catálogo de prueba";
        String contenido = """
                ## Hoja Cursos de prueba
                Catálogo de prueba
                Código | Programa | Duración (h) | Modalidad | Requisito | Precio (USD)
                Código: DEMO-01 | Programa: FinOps para equipos | Duración (h): 8 | Modalidad: Virtual en vivo | Requisito: Conocimientos básicos de nube | Precio (USD): 89
                Código: DEMO-02 | Programa: Ciberseguridad aplicada | Duración (h): 10 | Modalidad: Virtual en vivo | Requisito: Nociones básicas de redes | Precio (USD): 109
                Código: DEMO-03 | Programa: Fundamentos de gestión ágil | Duración (h): 6 | Modalidad: Virtual a tu ritmo | Requisito: Ninguno | Precio (USD): 49
                Código: DEMO-04 | Programa: IA para equipos de trabajo | Duración (h): 8 | Modalidad: Virtual a tu ritmo | Requisito: Ninguno | Precio (USD): 99
                """;
        String pregunta = "No tengo experiencia en redes y quiero estudiar a mi ritmo. Según el Catálogo de prueba, ¿qué cursos puedo llevar y cuál cuesta menos?";
        String solicitud = mapper.writeValueAsString(Map.of("pregunta", pregunta));
        try {
            var carga = servicio.cargar(new DocumentoService.Carga(titulo, "1", "VIGENTE", null, contenido));
            clearInvocations(llm);
            long inicio = System.nanoTime();
            web.perform(post("/api/v1/chat/rag").contentType("application/json").content(solicitud))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.respuesta").value("Puedes estudiar a tu ritmo con Fundamentos de gestión ágil y IA para equipos de trabajo. El curso más económico es Fundamentos de gestión ágil (49 USD)."))
                    .andExpect(jsonPath("$.fuentes[0].idDocumento").value(carga.idDocumento()))
                    .andExpect(jsonPath("$.fuentes[0].seccion").value("Hoja Cursos de prueba"))
                    .andExpect(jsonPath("$.tokensUsados").value(0));
            System.out.printf(java.util.Locale.ROOT, "Comparación del catálogo por HTTP: %.1f ms%n",
                    (System.nanoTime() - inicio) / 1_000_000.0);
            verifyNoInteractions(llm);

            servicio.cargar(new DocumentoService.Carga(titulo, "2", "VIGENTE", null,
                    contenido.replace("Precio (USD): 99", "Precio (USD): 29")));
            clearInvocations(llm);
            web.perform(post("/api/v1/chat/rag").contentType("application/json").content(solicitud))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.respuesta").value(org.hamcrest.Matchers.containsString("IA para equipos de trabajo (29 USD)")))
                    .andExpect(jsonPath("$.fuentes[0].version").value("2"));
            verifyNoInteractions(llm);
        } finally {
            jdbc.update("DELETE FROM documento WHERE titulo = ?", titulo);
        }
    }

    @Test
    void consultasLargasCitanLosTemariosYConservanLosCriteriosDocumentados() throws Exception {
        String ia = "IA para equipos de prueba";
        String agil = "Gestión Ágil de prueba";
        String liderazgo = "Liderazgo de prueba";
        var ids = new java.util.ArrayList<Long>();
        try {
            ids.add(cargarTemarioDePrueba(ia, "prueba-rag-ia", 999981, """
                    1. Gobierno de IA: Privacidad, revisión humana y calidad de respuestas.
                    2. Evaluación del proyecto: Indicadores, costos, beneficios esperados y criterios de aceptación del piloto.
                    """));
            ids.add(cargarTemarioDePrueba(agil, "prueba-rag-agil", 999982, """
                    1. Agilidad y valor: Entrega incremental.
                    2. Kanban y Lean: Visualización del flujo y límites de trabajo en curso.
                    """));
            ids.add(cargarTemarioDePrueba(liderazgo, "prueba-rag-lider", 999983, """
                    1. Decisiones: Comparación de alternativas.
                    2. Gestión del cambio: Coordinación de equipos.
                    """));
            clearInvocations(llm);

            long inicio = System.nanoTime();
            web.perform(post("/api/v1/chat/rag").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("pregunta",
                            "En mi empresa estamos preparando un piloto de IA. En " + ia + ", qué parte enseña a revisar las respuestas "
                                    + "antes de usarlas y qué criterios se estudian para decidir si el piloto fue exitoso?"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.respuesta").value(org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString("Gobierno de IA"),
                            org.hamcrest.Matchers.containsString("Indicadores, costos, beneficios esperados y criterios de aceptación del piloto"),
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("escalabilidad")))))
                    .andExpect(jsonPath("$.fuentes[0].titulo").value("Sílabo: " + ia))
                    .andExpect(jsonPath("$.tokensUsados").value(0));
            System.out.printf(java.util.Locale.ROOT, "Revisión humana y piloto por HTTP: %.1f ms%n",
                    (System.nanoTime() - inicio) / 1_000_000.0);

            inicio = System.nanoTime();
            web.perform(post("/api/v1/chat/rag").contentType("application/json")
                    .content(mapper.writeValueAsString(Map.of("pregunta",
                            "Somos un equipo chico y nos cuesta terminar tareas porque empezamos muchas al mismo tiempo. Entre "
                                    + agil + " y " + liderazgo + ", qué temas vería en cada curso y cuál se acerca más a mi problema?"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.respuesta").value(org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString("te conviene " + agil),
                            org.hamcrest.Matchers.containsString("límites de trabajo en curso"),
                            org.hamcrest.Matchers.containsString(liderazgo + ": Decisiones, Gestión del cambio"))))
                    .andExpect(jsonPath("$.fuentes[*].titulo").value(org.hamcrest.Matchers.containsInAnyOrder("Sílabo: " + agil, "Sílabo: " + liderazgo)))
                    .andExpect(jsonPath("$.tokensUsados").value(0));
            System.out.printf(java.util.Locale.ROOT, "Comparación de temarios por HTTP: %.1f ms%n",
                    (System.nanoTime() - inicio) / 1_000_000.0);
            verifyNoInteractions(llm);
        } finally {
            for (Long id : ids) {
                jdbc.update("DELETE FROM documento WHERE id_programa = ?", id);
                jdbc.update("DELETE FROM programa WHERE id_programa = ?", id);
            }
        }
    }

    private Long cargarTemarioDePrueba(String titulo, String slug, int idMoodle, String modulos) {
        Long id = jdbc.queryForObject("""
                INSERT INTO programa (titulo, slug, precio, moneda, moodle_course_id, activo)
                VALUES (?, ?, 89.00, 'USD', ?, true)
                RETURNING id_programa
                """, Long.class, titulo, slug, idMoodle);
        servicio.cargar(new DocumentoService.Carga("Sílabo: " + titulo, "1", "VIGENTE", id, "## Temario\n" + modulos));
        return id;
    }

    @Test
    void cargarExigeAdminInclusoParaContabilidad() throws Exception {
        String peticion = mapper.writeValueAsString(carga());
        web.perform(post("/api/v1/admin/conocimiento").contentType("application/json").content(peticion))
                .andExpect(status().isUnauthorized());
        web.perform(post("/api/v1/admin/conocimiento").with(user("contador").roles("CONTABILIDAD"))
                .contentType("application/json").content(peticion)).andExpect(status().isForbidden());
        verifyNoInteractions(llm);
    }

    @Test
    void consultaSinCoincidenciasNoInvocaGeneracion() throws Exception {
        servicio.cargar(carga());
        web.perform(post("/api/v1/chat/rag").contentType("application/json")
                .content("{\"pregunta\":\"¿Tienen un taller de astronomía?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fuentes").isEmpty())
                .andExpect(jsonPath("$.tokensUsados").value(0));
        verify(llm, never()).completarChat(anyString(), anyString(), anyDouble());
    }

    @Test
    void repetirYCompetirNoDuplicaDocumentosNiFragmentos() throws Exception {
        var primero = servicio.cargar(carga());
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        try {
            var a = ejecutor.submit(() -> servicio.cargar(carga()));
            var b = ejecutor.submit(() -> servicio.cargar(carga()));
            assertEquals(primero.idDocumento(), a.get(60, TimeUnit.SECONDS).idDocumento());
            assertEquals(primero.idDocumento(), b.get(60, TimeUnit.SECONDS).idDocumento());
        } finally { ejecutor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM documento WHERE titulo = ?", Integer.class, carga().titulo()));
        assertEquals(primero.fragmentos(), jdbc.queryForObject("SELECT count(*) FROM documento_chunk WHERE id_documento = ?",
                Integer.class, primero.idDocumento()));
    }

    @Test
    void errorDeGuardadoRestauraVersionAnterior() {
        var anterior = servicio.cargar(carga());
        var doc = Documento.builder().titulo(carga().titulo()).tipoDocumento("SYLLABUS").checksumSha256("nuevo").activo(true).build();
        var malo = DocumentoChunk.builder().contenido("No debe persistir").metadata(Map.of()).embedding(new float[384]).build();
        assertThrows(RuntimeException.class, () -> repo.guardar(doc, List.of(malo)));
        assertEquals(anterior.checksum(), jdbc.queryForObject("SELECT checksum_sha256 FROM documento WHERE id_documento = ?",
                String.class, anterior.idDocumento()));
        assertEquals(anterior.fragmentos(), jdbc.queryForObject("SELECT count(*) FROM documento_chunk WHERE id_documento = ?",
                Integer.class, anterior.idDocumento()));
    }

    @Test
    void documentoInactivoNoSeRecupera() throws Exception {
        var documento = servicio.cargar(carga());
        jdbc.update("UPDATE documento SET activo = false WHERE id_documento = ?", documento.idDocumento());
        web.perform(post("/api/v1/chat/rag").contentType("application/json").content("{\"pregunta\":\"Contenido del curso\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fuentes").isEmpty());
    }
}

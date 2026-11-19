package com.lysandri.application.service;

import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.ports.in.ChatRagUseCase;
import com.lysandri.domain.ports.out.HistorialConsultaRepositoryPort;
import com.lysandri.domain.ports.out.LlmClientPort;
import com.lysandri.domain.ports.out.VectorStorePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRagServiceTest {

    @Test
    void precioDocumentadoConservaLaFuenteSinEsperarGeneracion() {
        when(llmClientPort.generarEmbedding(anyString())).thenReturn(new float[]{1});
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), isNull())).thenReturn(List.of(
                DocumentoChunk.builder().idDocumento(2L).idChunk(1L).idPrograma(1L)
                        .contenido("El curso tiene un precio registrado de USD 89.00 y una duración registrada de 8 horas.")
                        .metadata(java.util.Map.of("estado", "VIGENTE", "titulo", "Curso: FinOps Empresarial", "seccion", "Información del curso", "version", "2")).build()));
        var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                "Cuánto cuesta FinOps Empresarial?", "rapida", null, null));
        assertEquals("FinOps Empresarial cuesta USD 89.00.", respuesta.respuesta());
        assertEquals(2L, respuesta.fuentes().get(0).idDocumento());
        assertEquals(0, respuesta.tokensUsados());
        verify(llmClientPort, never()).completarChat(anyString(), anyString(), anyDouble());
        verify(historialRepository).guardar(any());
    }

    @Test
    void precioNaturalUsaLasFuentesSinVolverAGenerarTexto() {
        when(llmClientPort.generarEmbedding(anyString())).thenReturn(new float[]{1});
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), isNull())).thenReturn(List.of(
                DocumentoChunk.builder().idDocumento(2L).idChunk(1L).idPrograma(1L)
                        .contenido("El curso tiene un precio registrado de USD 89.00 y una duración registrada de 8 horas.")
                        .metadata(java.util.Map.of("estado", "VIGENTE", "titulo", "Curso: FinOps Empresarial", "seccion", "Información del curso", "version", "2")).build()));
        for (String pregunta : List.of("Cuánto vale FinOps Empresarial?", "Precio de FinOps Empresarial", "¿Cuánto vale FinOps Empresarial?")) {
            var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                    pregunta, "natural", null, null));
            assertEquals("FinOps Empresarial cuesta USD 89.00.", respuesta.respuesta());
            assertFalse(respuesta.fuentes().isEmpty());
        }
        verify(llmClientPort, never()).completarChat(anyString(), anyString(), anyDouble());
    }

    @Test
    void duracionNaturalConservaLaUnidad() {
        var fuente = DocumentoChunk.builder().idDocumento(2L).idChunk(1L).idPrograma(1L)
                .contenido("Duración registrada de 8 horas.")
                .metadata(java.util.Map.of("estado", "VIGENTE", "titulo", "Curso: FinOps Empresarial")).build();
        assertEquals("FinOps Empresarial dura 8 horas.", RespuestaBreve.obtener(
                "Cuál es la duración de FinOps Empresarial?", null, List.of(fuente)));
        assertNull(RespuestaBreve.obtener("Cuánto vale FinOps Empresarial y cuándo inicia?", null, List.of(fuente)));
    }

    @Test
    void temarioNaturalUsaModulosDocumentadosYRechazaPreguntasCompuestas() {
        var fuente = DocumentoChunk.builder().idPrograma(1L)
                .contenido("1. Fundamentos: Uno.\n2. Visibilidad: Dos.\n3. Seguimiento: Tres.\n4. Presupuesto: Cuatro.")
                .metadata(java.util.Map.of("estado", "PROPUESTA", "seccion", "Temario propuesto")).build();
        for (String pregunta : List.of("Qué temas incluye?", "Cuál es el temario?", "que temas incluye este curso")) {
            assertEquals("Los módulos son:\n1. Fundamentos\n2. Visibilidad\n3. Seguimiento\n4. Presupuesto",
                    RespuestaBreve.obtener(pregunta, 1L, List.of(fuente)));
        }
        assertNull(RespuestaBreve.obtener("Qué temas incluye y cuándo inicia?", 1L, List.of(fuente)));
    }

    @Test
    void catalogoCompletoConservaFuentesYSeCompruebaConLosProgramasActivos() {
        var fuente = DocumentoChunk.builder().idDocumento(25L).idChunk(1L)
                .contenido("- FinOps Empresarial: USD 89.00, 8 horas.")
                .metadata(java.util.Map.of("estado", "VIGENTE", "titulo", "Catálogo de Lysandri Executive")).build();
        when(documentos.buscarPorTitulo("Catálogo de Lysandri Executive", 16)).thenReturn(List.of(fuente));
        when(programas.listarTodos()).thenReturn(List.of(
                com.lysandri.domain.model.Programa.builder().titulo("FinOps Empresarial")
                        .precio(new java.math.BigDecimal("89.00")).moneda("USD").duracionHoras(8).activo(true).build(),
                com.lysandri.domain.model.Programa.builder().titulo("Curso retirado").activo(false).build()));
        var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                "Cuánto cuestan los cursos?", "catalogo-vigente", null, null));
        assertEquals("Estos son los precios:\n\n- FinOps Empresarial: USD 89.00", respuesta.respuesta());
        assertEquals(25L, respuesta.fuentes().get(0).idDocumento());
        assertEquals(0, respuesta.tokensUsados());
        verifyNoInteractions(llmClientPort, vectorStorePort);
        verify(historialRepository).guardar(any());
    }

    @Test
    void sistemasNoSeConfundeConUnaPreguntaSobreTemas() {
        when(llmClientPort.generarEmbedding(anyString())).thenReturn(new float[]{1});
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), isNull())).thenReturn(List.of(
                DocumentoChunk.builder().idDocumento(18L).idChunk(1L).contenido("Precio USD 109.00")
                        .metadata(java.util.Map.of("estado", "PROPUESTA")).build()));
        when(llmClientPort.completarChat(anyString(), anyString(), anyDouble()))
                .thenReturn(new LlmClientPort.LlmCompletion("Cuesta USD 109.00.", 1, 1, 2));
        var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                "¿Cuánto cuesta DevOps Ejecutivo y Confiabilidad de Sistemas (SRE)?", "precio", null, null));
        assertEquals("Cuesta USD 109.00.", respuesta.respuesta());
    }

    @Test
    void temario_respondeDirectamenteYConservaLaFuente() {
        when(llmClientPort.generarEmbedding(anyString())).thenReturn(new float[]{1});
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), isNull())).thenReturn(List.of(
                DocumentoChunk.builder().idDocumento(1L).idChunk(1L).contenido("Temario: revisión de gastos en la nube.")
                        .metadata(java.util.Map.of("estado", "PROPUESTA")).build()));
        when(llmClientPort.completarChat(anyString(), anyString(), anyDouble()))
                .thenReturn(new LlmClientPort.LlmCompletion("Según el contexto documental, aprenderás a revisar el gasto.", 1, 1, 2));
        var respuesta = chatRagService.procesarConsulta(
                new ChatRagUseCase.ChatRagCommand("¿Qué voy a aprender?", "temario", null, null));
        assertEquals("Aprenderás a revisar el gasto.", respuesta.respuesta());
        assertEquals(1L, respuesta.fuentes().get(0).idDocumento());
        verify(historialRepository).guardar(argThat(h -> h.getRespuestaIa().equals(respuesta.respuesta())));
    }

    @Test
    void preguntaCorta_usaElCursoSeleccionadoParaRecuperarFuentes() {
        when(programas.buscarPorId(1L)).thenReturn(java.util.Optional.of(
                com.lysandri.domain.model.Programa.builder().titulo("FinOps Empresarial").build()));
        when(llmClientPort.generarEmbedding(anyString())).thenReturn(new float[]{1});
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), eq(1L))).thenReturn(List.of());
        chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand("¿Cuánto dura?", "corta", null, 1L));
        verify(llmClientPort).generarEmbedding("Curso: FinOps Empresarial. ¿Cuánto dura?");
    }

    @Test
    void consultasInformalesConservanLaBusquedaDelCatalogo() {
        when(llmClientPort.generarEmbedding(anyString())).thenReturn(new float[]{1});
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), isNull())).thenReturn(List.of());
        for (String pregunta : List.of("Qué cursos tienen?", "que cursos tienen?", "que cursos tienen")) {
            var respuesta = chatRagService.procesarConsulta(
                    new ChatRagUseCase.ChatRagCommand(pregunta, "informal", null, null));
            assertNotNull(respuesta.respuesta());
            verify(llmClientPort).generarEmbedding("Catálogo de Lysandri Executive. Cursos, precios y duración. " + pregunta);
        }
    }

    @Test
    void preguntaGeneral_buscaLaFuenteDelCatalogoCompleto() {
        when(llmClientPort.generarEmbedding(anyString())).thenReturn(new float[]{1});
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), isNull())).thenReturn(List.of());
        chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand("¿Qué cursos tienen?", "catalogo", null, null));
        verify(llmClientPort).generarEmbedding(startsWith("Catálogo de Lysandri Executive."));
        verifyNoInteractions(programas);
    }

    @Test
    void saludoSimple_respondeSinConsultarModeloNiInventarFuentes() {
        var respuesta = chatRagService.procesarConsulta(
                new ChatRagUseCase.ChatRagCommand("¡Hola!", "saludo", null, null));
        assertTrue(respuesta.respuesta().startsWith("¡Hola!"));
        assertTrue(respuesta.fuentes().isEmpty());
        assertEquals(0, respuesta.tokensUsados());
        verifyNoInteractions(llmClientPort, vectorStorePort);
        verify(historialRepository).guardar(any());
    }

    @Test
    void propuestaConservaTituloSeccionVersionEnContextoYCita() {
        float[] vector = new float[]{1};
        when(llmClientPort.generarEmbedding(anyString())).thenReturn(vector);
        var fragmento = DocumentoChunk.builder().idDocumento(3L).idChunk(1L)
                .contenido("Carga propuesta: 24 horas.").similitud(0.9)
                .metadata(java.util.Map.of("titulo", "Globalización", "seccion", "4 Duración",
                        "version", "2", "estado", "PROPUESTA")).build();
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), isNull())).thenReturn(List.of(fragmento));
        when(llmClientPort.completarChat(anyString(), anyString(), anyDouble()))
                .thenReturn(new LlmClientPort.LlmCompletion("Se proponen 24 horas.", 1, 1, 2));
        var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand("¿Cuánto estudio contempla?", "sesion", null, null));
        assertEquals("Globalización", respuesta.fuentes().get(0).titulo());
        assertEquals("4 Duración", respuesta.fuentes().get(0).seccion());
        assertEquals("2", respuesta.fuentes().get(0).version());
        assertEquals("PROPUESTA", respuesta.fuentes().get(0).estado());
        assertNull(respuesta.fuentes().get(0).pagina());
        verify(llmClientPort).completarChat(contains("habilidades y su aplicación práctica en lenguaje positivo"), contains("Versión 2 | Estado PROPUESTA"), eq(0.2));
    }

    @Test
    void informacionGeneralPriorizaMallaDelCursoMencionado() {
        var curso = com.lysandri.domain.model.Programa.builder().idPrograma(5L)
                .titulo("Inteligencia Artificial para Ejecutivos").activo(true).build();
        when(programas.listarTodos()).thenReturn(List.of(curso));
        when(documentos.buscarTemarios(5L)).thenReturn(List.of(DocumentoChunk.builder()
                .idDocumento(19L).idChunk(42L).idPrograma(5L)
                .contenido("1. Conceptos y posibilidades: IA generativa.\n2. Selección de casos de uso: Priorización.")
                .metadata(java.util.Map.of("titulo", "Sílabo de IA", "seccion", "Temario propuesto"))
                .build()));

        for (String pregunta : List.of("sobre el curso inteligencia artificial dame mas informacion",
                "qué aprenderé en Inteligencia Artificial para Ejecutivos?",
                "que conocimientos adquiero del curso Inteligencia Artificial para Ejecutivos",
                "estoy viendo el curso de Inteligencia Artificial para Ejecutivos, me cuentas qué temas se ven en cada módulo?")) {
            var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                    pregunta, "malla", null, null));
            assertTrue(respuesta.respuesta().contains("2. Selección de casos de uso: Priorización."));
            assertEquals(19L, respuesta.fuentes().get(0).idDocumento());
        }
        verifyNoInteractions(llmClientPort);
    }

    @Test
    void preguntaConContextoUsaElTemarioDeMetodologiasAgiles() {
        var curso = com.lysandri.domain.model.Programa.builder().idPrograma(4L)
                .titulo("Metodologías Ágiles").activo(true).build();
        when(programas.listarTodos()).thenReturn(List.of(curso));
        when(documentos.buscarTemarios(4L)).thenReturn(List.of(DocumentoChunk.builder()
                .idDocumento(20L).idChunk(43L).idPrograma(4L)
                .contenido("1. Scrum: Priorización del trabajo.\n2. Kanban y Lean: Visualización del flujo.")
                .build()));

        var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                "quiero mejorar cómo organizo el trabajo de mi equipo, qué aprendería en Metodologías Ágiles?",
                "agil", null, null));

        assertTrue(respuesta.respuesta().contains("Kanban y Lean: Visualización del flujo."));
        assertEquals(20L, respuesta.fuentes().get(0).idDocumento());
        verifyNoInteractions(llmClientPort, vectorStorePort);
    }

    @Test
    void preguntaDeAplicacionUsaElPlanDocumentadoSinOllama() {
        var curso = com.lysandri.domain.model.Programa.builder().idPrograma(3L)
                .titulo("Liderazgo Estratégico").activo(true).build();
        when(programas.listarTodos()).thenReturn(List.of(curso));
        when(documentos.buscarTemarios(3L)).thenReturn(List.of(DocumentoChunk.builder()
                .idDocumento(21L).idChunk(44L).idPrograma(3L)
                .contenido("1. Decisiones: Comparación de alternativas.\n"
                        + "2. Plan de liderazgo: Diseño de una intervención con objetivos, responsables y señales de avance.")
                .build()));

        var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(
                "qué conocimientos adquiero en Liderazgo Estratégico y cómo podría aplicarlos con mi equipo?",
                "liderazgo", null, null));

        assertTrue(respuesta.respuesta().contains("2. Plan de liderazgo: Diseño de una intervención con objetivos, responsables y señales de avance."));
        assertTrue(respuesta.respuesta().contains("elijan en equipo una situación real"));
        assertEquals(21L, respuesta.fuentes().get(0).idDocumento());
        verifyNoInteractions(llmClientPort, vectorStorePort);
    }

    @Test
    void preguntaLibreExtensaUsaEvidenciaDeAmbosCursosSinRepetirContexto() {
        String pregunta = "Tengo un equipo pequeño y nos cuesta ordenar las tareas. "
                + "Me das información sobre Metodologías Ágiles y Liderazgo Estratégico, y me dices cuál me ayudaría a ver el trabajo en curso?";
        var agil = DocumentoChunk.builder().idDocumento(15L).idChunk(1L)
                .contenido("Kanban: visualización del flujo y límites de trabajo en curso.")
                .metadata(java.util.Map.of("titulo", "Metodologías Ágiles", "seccion", "Temario", "version", "2", "estado", "VIGENTE"))
                .build();
        var copia = DocumentoChunk.builder().idDocumento(16L).idChunk(2L)
                .contenido(agil.getContenido()).metadata(agil.getMetadata()).build();
        var liderazgo = DocumentoChunk.builder().idDocumento(17L).idChunk(3L)
                .contenido("Liderazgo: análisis de alternativas y comunicación de decisiones.")
                .metadata(java.util.Map.of("titulo", "Liderazgo Estratégico", "seccion", "Temario", "version", "1", "estado", "VIGENTE"))
                .build();
        var vector = new float[]{1};
        when(llmClientPort.generarEmbedding(pregunta)).thenReturn(vector);
        when(vectorStorePort.buscarSimilares(eq(vector), eq(4), anyDouble(), isNull()))
                .thenReturn(List.of(agil, copia, liderazgo));
        var texto = "Metodologías Ágiles encaja mejor porque enseña a visualizar el flujo con Kanban. "
                + "Liderazgo Estratégico se centra en analizar alternativas y comunicar decisiones.";
        when(llmClientPort.completarChat(anyString(), anyString(), eq(0.2)))
                .thenReturn(new LlmClientPort.LlmCompletion(texto, 30, 30, 60));

        var resultado = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(pregunta, "comparacion-libre", null, null));

        assertEquals(texto, resultado.respuesta());
        assertEquals(List.of(15L, 16L, 17L), resultado.fuentes().stream().map(ChatRagUseCase.SourceCitation::idDocumento).toList());
        var contexto = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llmClientPort).completarChat(contains("Incluye todos los datos que pidan explícitamente"), contexto.capture(), eq(0.2));
        assertTrue(contexto.getValue().contains("[Metodologías Ágiles | Temario | v2, VIGENTE]"));
        assertTrue(contexto.getValue().contains(liderazgo.getContenido()));
        assertTrue(contexto.getValue().contains(pregunta));
        assertEquals(1, java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(agil.getContenido()))
                .matcher(contexto.getValue()).results().count());
        verifyNoInteractions(documentos);
    }

    @Mock
    private com.lysandri.domain.ports.out.DocumentoRepositoryPort documentos;

    @Mock
    private VectorStorePort vectorStorePort;

    @Mock
    private LlmClientPort llmClientPort;

    @Mock
    private HistorialConsultaRepositoryPort historialRepository;

    @Mock
    private com.lysandri.domain.ports.out.ProgramRepositoryPort programas;

    @Mock
    private com.lysandri.domain.ports.out.WebSearchPort webSearch;

    @InjectMocks
    private ChatRagService chatRagService;

    @Test
    void consultaExternaSoloCitaResultadosSeleccionados() {
        String pregunta = "quién ocupa el papado actualmente?";
        when(webSearch.disponible()).thenReturn(true);
        when(llmClientPort.generarEmbedding(eq(pregunta))).thenReturn(new float[]{1});
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), isNull())).thenReturn(List.of(
                DocumentoChunk.builder().idDocumento(1L).idChunk(10L).contenido("Temario de redes.").build()));
        when(llmClientPort.completarChat(contains("solo SIN_EVIDENCIA"), anyString(), eq(0.2)))
                .thenReturn(new LlmClientPort.LlmCompletion("SIN_EVIDENCIA", 6, 1, 7));
        when(webSearch.buscar(pregunta)).thenReturn(List.of(
                new com.lysandri.domain.ports.out.WebSearchPort.Resultado("Sin relación", "https://ejemplo.test/a", "Otro tema."),
                new com.lysandri.domain.ports.out.WebSearchPort.Resultado("Fuente institucional", "https://ejemplo.test/b", "La fuente confirma el dato solicitado.")));
        when(llmClientPort.completarChat(contains("evidencia web"), contains("https://ejemplo.test/b"), eq(0.0)))
                .thenReturn(new LlmClientPort.LlmCompletion("Respuesta respaldada [2].", 10, 4, 14));

        var respuesta = chatRagService.procesarConsulta(new ChatRagUseCase.ChatRagCommand(pregunta, "externa", null, null));

        assertEquals("Respuesta respaldada.", respuesta.respuesta());
        assertEquals(List.of("https://ejemplo.test/b"), respuesta.fuentes().stream().map(ChatRagUseCase.SourceCitation::url).toList());
        assertEquals(21, respuesta.tokensUsados());
    }

    @Test
    void preguntasDeInscripcionYDatosPersonalesQuedanEnElProyecto() {
        assertFalse(ConsultaWeb.permite("cómo me inscribo a un curso?", null));
        assertFalse(ConsultaWeb.permite("a quién pertenece el correo correo@ejemplo.test?", null));
        assertFalse(ConsultaWeb.permite("quién es el papa actual?", 5L));
        assertTrue(ConsultaWeb.permite("quién es el papa actual?", null));
    }

    @Test
    void procesarConsulta_debeRecuperarChunksYCompletarConLLM() {
        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        when(llmClientPort.generarEmbedding("¿Cuál es la duración del programa?")).thenReturn(mockVector);

        DocumentoChunk chunkMock = DocumentoChunk.builder()
                .idChunk(1L)
                .idDocumento(10L)
                .idPrograma(101L)
                .numeroPagina(3)
                .contenido("El programa tiene una duración de 8 semanas lectivas intensivas.")
                .similitud(0.89)
                .build();

        when(vectorStorePort.buscarSimilares(eq(mockVector), anyInt(), anyDouble(), eq(101L)))
                .thenReturn(List.of(chunkMock));

        when(llmClientPort.completarChat(anyString(), anyString(), anyDouble()))
                .thenReturn(new LlmClientPort.LlmCompletion("El programa dura 8 semanas lectivas.", 80, 25, 105));

        ChatRagUseCase.ChatRagCommand command = new ChatRagUseCase.ChatRagCommand(
                "¿Cuál es la duración del programa?",
                "sess_abc123",
                1L,
                101L
        );

        ChatRagUseCase.ChatRagResponse response = chatRagService.procesarConsulta(command);

        assertNotNull(response);
        assertEquals("El programa dura 8 semanas lectivas.", response.respuesta());
        assertEquals(1, response.fuentes().size());
        assertEquals(3, response.fuentes().get(0).pagina());
        assertEquals(105, response.tokensUsados());
        verify(vectorStorePort).buscarSimilares(eq(mockVector), eq(4), eq(0.60), eq(101L));
        verify(historialRepository).guardar(any());
    }

    @Test
    void procesarConsulta_cuandoNoHayChunks_debeReconocerFaltaDeInformacionSinLlamarLLM() {
        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        when(llmClientPort.generarEmbedding("¿Tienen curso de astronomía?")).thenReturn(mockVector);
        when(vectorStorePort.buscarSimilares(eq(mockVector), anyInt(), anyDouble(), isNull()))
                .thenReturn(List.of());

        ChatRagUseCase.ChatRagCommand command = new ChatRagUseCase.ChatRagCommand(
                "¿Tienen curso de astronomía?",
                "sess_general",
                null,
                null
        );

        ChatRagUseCase.ChatRagResponse response = chatRagService.procesarConsulta(command);

        assertNotNull(response);
        assertEquals(ChatRagService.RESPUESTA_SIN_INFORMACION_OFICIAL, response.respuesta());
        assertTrue(response.fuentes().isEmpty(), "Las fuentes deben estar vacías cuando falta información");
        assertEquals(0, response.tokensUsados());
        // No debe realizar llamada de inferencia a LLM cuando no hay contexto
        verify(llmClientPort, never()).completarChat(anyString(), anyString(), anyDouble());
        verify(historialRepository).guardar(any());
    }

    @Test
    void procesarConsulta_cuandoVectorStoreFalla_debePropagarErrorTecnico() {
        float[] mockVector = new float[]{0.1f, 0.2f, 0.3f};
        when(llmClientPort.generarEmbedding("consulta")).thenReturn(mockVector);
        when(vectorStorePort.buscarSimilares(any(), anyInt(), anyDouble(), any()))
                .thenThrow(new IllegalStateException("Error al consultar el almacén vectorial pgvector"));

        ChatRagUseCase.ChatRagCommand command = new ChatRagUseCase.ChatRagCommand(
                "consulta",
                "sess_err",
                null,
                101L
        );

        assertThrows(IllegalStateException.class, () -> chatRagService.procesarConsulta(command));
        verify(llmClientPort, never()).completarChat(anyString(), anyString(), anyDouble());
    }
}

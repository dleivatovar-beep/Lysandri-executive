package com.lysandri.application.service;

import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.model.HistorialConsulta;
import com.lysandri.domain.ports.in.ChatRagUseCase;
import com.lysandri.domain.ports.out.HistorialConsultaRepositoryPort;
import com.lysandri.domain.ports.out.LlmClientPort;
import com.lysandri.domain.ports.out.VectorStorePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRagService implements ChatRagUseCase {

    private final VectorStorePort vectorStorePort;
    private final LlmClientPort llmClientPort;
    private final HistorialConsultaRepositoryPort historialRepository;

    private static final int TOP_K = 4;
    private static final double MIN_SIMILARITY = 0.60;

    private static final String SYSTEM_PROMPT = """
        Eres el Asistente Ejecutivo de Admisiones y Asesoría Académica de 'Lysandri Executive' 
        (división de Yunix Ingenieros E.I.R.L.).
        Tu objetivo es resolver dudas de ejecutivos, líderes tecnológicos y gerentes sobre los 
        programas ejecutivos, mallas curriculares, metodologías, duraciones y requisitos de admisión.
        
        REGLAS DE RESPUESTA:
        1. Responde basándote en el contexto oficial del sílabo provisto.
        2. Si la información no se encuentra en el contexto, indícalo cortésmente y sugiere solicitar 
           asesoría corporativa personalizada a través del formulario de información.
        3. Mantén un tono formal, ejecutivo, conciso y de alto valor corporativo.
        4. Cita las secciones o páginas de los sílabos cuando aplique.
        """;

    @Override
    @Transactional
    public ChatRagResponse procesarConsulta(ChatRagCommand command) {
        long startTime = System.currentTimeMillis();
        log.info("Procesando consulta RAG. Sesión: {}, Programa: {}", command.sesionId(), command.idPrograma());

        // 1. Generar embedding vectorial de la pregunta (1536 dimensiones)
        float[] queryEmbedding = llmClientPort.generarEmbedding(command.pregunta());

        // 2. Búsqueda semántica por distancia coseno (<=>) en pgvector
        List<DocumentoChunk> fragmentosRelevantes = vectorStorePort.buscarSimilares(
                queryEmbedding,
                TOP_K,
                MIN_SIMILARITY,
                command.idPrograma()
        );

        // 3. Ensamblado del contexto oficial (Grounding)
        String contextoDocumentos = fragmentosRelevantes.isEmpty()
                ? "No se hallaron fragmentos técnicos específicos en la base de conocimiento para este programa."
                : fragmentosRelevantes.stream()
                        .map(chunk -> String.format("[Pág. %s]: %s", 
                                chunk.getNumeroPagina() != null ? chunk.getNumeroPagina() : "S/N", 
                                chunk.getContenido()))
                        .collect(Collectors.joining("\n---\n"));

        String userPromptCompleto = String.format("""
            CONTEXTO OFICIAL DEL SÍLABO:
            %s
            
            PREGUNTA DEL EJECUTIVO:
            %s
            """, contextoDocumentos, command.pregunta());

        // 4. Inferencia con LLM
        LlmClientPort.LlmCompletion completion = llmClientPort.completarChat(
                SYSTEM_PROMPT,
                userPromptCompleto,
                0.2
        );

        long latency = System.currentTimeMillis() - startTime;

        // 5. Citas de referencia
        List<SourceCitation> citas = fragmentosRelevantes.stream()
                .map(chunk -> new SourceCitation(
                        chunk.getIdDocumento(),
                        chunk.getNumeroPagina(),
                        chunk.getContenido().length() > 200 
                                ? chunk.getContenido().substring(0, 200) + "..." 
                                : chunk.getContenido(),
                        chunk.getSimilitud()
                ))
                .toList();

        // 6. Registro de auditoría en HistorialConsulta
        try {
            HistorialConsulta historial = HistorialConsulta.builder()
                    .idUser(command.idUsuario())
                    .idPrograma(command.idPrograma())
                    .sesionId(command.sesionId())
                    .pregunta(command.pregunta())
                    .respuestaIa(completion.textoRespuesta())
                    .chunksReferenciados(fragmentosRelevantes.stream()
                            .map(c -> "doc_" + c.getIdDocumento() + "_chunk_" + c.getIdChunk())
                            .toList())
                    .tokensPrompt(completion.tokensPrompt())
                    .tokensCompletion(completion.tokensCompletion())
                    .tiempoRespuestaMs((int) latency)
                    .fechaConsulta(OffsetDateTime.now())
                    .build();

            historialRepository.guardar(historial);
        } catch (Exception e) {
            log.warn("No se pudo guardar el registro de auditoría en historial: {}", e.getMessage());
        }

        return new ChatRagResponse(
                completion.textoRespuesta(),
                citas,
                completion.totalTokens(),
                latency
        );
    }
}

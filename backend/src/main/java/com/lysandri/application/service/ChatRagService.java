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

    public static final String RESPUESTA_SIN_INFORMACION_OFICIAL =
            "Estimado ejecutivo, la información solicitada no se encuentra disponible en los documentos oficiales o sílabos de la plataforma. Le sugerimos solicitar asesoría personalizada a través de nuestro formulario de contacto corporativo.";

    private static final String SYSTEM_PROMPT = """
        Eres el Asistente Ejecutivo de Admisiones y Asesoría Académica de 'Lysandri Executive'.
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
    public ChatRagResponse procesarConsulta(ChatRagCommand command) {
        long startTime = System.currentTimeMillis();
        log.info("Procesando consulta RAG. Sesión: {}, Programa: {}", command.sesionId(), command.idPrograma());

        // 1. Generar embedding vectorial de la pregunta (1536 dimensiones) sin retener transacción de BD
        float[] queryEmbedding = llmClientPort.generarEmbedding(command.pregunta());

        // 2. Búsqueda semántica por distancia coseno (<=>) en pgvector
        List<DocumentoChunk> fragmentosRelevantes = vectorStorePort.buscarSimilares(
                queryEmbedding,
                TOP_K,
                MIN_SIMILARITY,
                command.idPrograma()
        );

        long latency = System.currentTimeMillis() - startTime;

        // 3. Diferenciar falta de información de fallos técnicos: si no hay fragmentos oficiales, reconocerlo
        if (fragmentosRelevantes == null || fragmentosRelevantes.isEmpty()) {
            log.info("RAG: Sin fragmentos relevantes para la consulta. Reconociendo falta de información oficial.");
            guardarHistorialAuditoria(command, RESPUESTA_SIN_INFORMACION_OFICIAL, List.of(), 0, 0, (int) latency);
            return new ChatRagResponse(
                    RESPUESTA_SIN_INFORMACION_OFICIAL,
                    List.of(),
                    0,
                    latency
            );
        }

        // 4. Ensamblado del contexto oficial (Grounding)
        String contextoDocumentos = fragmentosRelevantes.stream()
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

        // 5. Inferencia con LLM sin transacción de BD abierta
        LlmClientPort.LlmCompletion completion = llmClientPort.completarChat(
                SYSTEM_PROMPT,
                userPromptCompleto,
                0.2
        );

        long totalLatency = System.currentTimeMillis() - startTime;

        // 6. Citas de referencia verificadas
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

        // 7. Registro de auditoría
        guardarHistorialAuditoria(
                command,
                completion.textoRespuesta(),
                fragmentosRelevantes.stream()
                        .map(c -> "doc_" + c.getIdDocumento() + "_chunk_" + c.getIdChunk())
                        .toList(),
                completion.tokensPrompt(),
                completion.tokensCompletion(),
                (int) totalLatency
        );

        return new ChatRagResponse(
                completion.textoRespuesta(),
                citas,
                completion.totalTokens(),
                totalLatency
        );
    }

    private void guardarHistorialAuditoria(
            ChatRagCommand command,
            String respuesta,
            List<String> chunksReferenciados,
            int tokensPrompt,
            int tokensCompletion,
            int latenciaMs) {
        try {
            HistorialConsulta historial = HistorialConsulta.builder()
                    .idUser(command.idUsuario())
                    .idPrograma(command.idPrograma())
                    .sesionId(command.sesionId())
                    .pregunta(command.pregunta())
                    .respuestaIa(respuesta)
                    .chunksReferenciados(chunksReferenciados)
                    .tokensPrompt(tokensPrompt)
                    .tokensCompletion(tokensCompletion)
                    .tiempoRespuestaMs(latenciaMs)
                    .fechaConsulta(OffsetDateTime.now())
                    .build();

            historialRepository.guardar(historial);
        } catch (Exception e) {
            log.warn("No se pudo guardar el registro de auditoría en historial: {}", e.getMessage());
        }
    }
}

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

    @Mock
    private VectorStorePort vectorStorePort;

    @Mock
    private LlmClientPort llmClientPort;

    @Mock
    private HistorialConsultaRepositoryPort historialRepository;

    @InjectMocks
    private ChatRagService chatRagService;

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
}

package com.lysandri.application.service;

import com.lysandri.domain.model.Documento;
import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.ports.out.DocumentoRepositoryPort;
import com.lysandri.domain.ports.out.LlmClientPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentoServiceTest {
    private final LlmClientPort llm = mock(LlmClientPort.class);
    private final DocumentoRepositoryPort repo = mock(DocumentoRepositoryPort.class);
    private final DocumentoService servicio = new DocumentoService(llm, repo);

    @Test
    void cargaLocalUsaDimensionYModeloConfigurados() {
        org.springframework.test.util.ReflectionTestUtils.setField(servicio, "proveedor", "ollama");
        org.springframework.test.util.ReflectionTestUtils.setField(servicio, "modeloLocal", "embeddinggemma:latest");
        float[] vector = new float[768]; vector[0] = 1;
        when(llm.generarEmbedding(anyString())).thenReturn(vector);
        when(repo.guardar(any(), anyList())).thenReturn(8L);
        servicio.cargar(new DocumentoService.Carga("Sílabo local", "1", "PROPUESTA", null, "Contenido"));
        ArgumentCaptor<List<DocumentoChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).guardar(any(), captor.capture());
        assertEquals("ollama", captor.getValue().get(0).getMetadata().get("embeddingProveedor"));
        assertEquals("embeddinggemma:latest", captor.getValue().get(0).getMetadata().get("embeddingModelo"));
        assertEquals(768, captor.getValue().get(0).getEmbedding().length);
    }

    @Test
    void conservaSeccionesYVersionSinInventarPaginas() {
        float[] vector = new float[1536]; vector[0] = 1;
        when(llm.generarEmbedding(anyString())).thenReturn(vector);
        when(repo.guardar(any(), anyList())).thenReturn(7L);
        var resultado = servicio.cargar(new DocumentoService.Carga("Sílabo", "2", "PROPUESTA", null,
                "# Curso\nPresentación.\n## Duración\n24 horas.\n## Precio\nPendiente."));
        ArgumentCaptor<List<DocumentoChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).guardar(any(Documento.class), captor.capture());
        assertEquals(7L, resultado.idDocumento());
        assertEquals(3, resultado.fragmentos());
        var fragmento = captor.getValue().get(1);
        assertEquals("Duración", fragmento.getMetadata().get("seccion"));
        assertEquals("2", fragmento.getMetadata().get("version"));
        assertNull(fragmento.getNumeroPagina());
        assertEquals(64, resultado.checksum().length());
    }

    @Test
    void rechazaVectoresDeOtroModeloAntesDeGuardar() {
        when(llm.generarEmbedding(anyString())).thenReturn(new float[384]);
        assertThrows(IllegalArgumentException.class, () -> servicio.cargar(
                new DocumentoService.Carga("Sílabo", "1", "PROPUESTA", null, "Contenido")));
        verifyNoInteractions(repo);
    }

    @Test
    void falloDelProveedorNoGuardaDocumentoParcial() {
        when(llm.generarEmbedding(anyString())).thenThrow(new IllegalStateException("Sin proveedor"));
        assertThrows(IllegalStateException.class, () -> servicio.cargar(
                new DocumentoService.Carga("Sílabo", "1", "PROPUESTA", null, "Contenido")));
        verifyNoInteractions(repo);
    }

    @Test
    void fragmentosLargosConservanTodoElTexto() {
        String texto = "palabra ".repeat(900).strip();
        var partes = servicio.dividir("## Actividad\n" + texto);
        assertTrue(partes.size() > 1);
        assertTrue(partes.stream().allMatch(p -> p.texto().length() <= 1400 && p.seccion().equals("Actividad")));
        assertEquals(texto, partes.stream().map(DocumentoService.Parte::texto).collect(java.util.stream.Collectors.joining(" ")));
    }

    @Test
    void tituloSinContenidoNoSeIndexa() {
        assertThrows(IllegalArgumentException.class, () -> servicio.cargar(
                new DocumentoService.Carga("Sílabo", "1", "PROPUESTA", null, "# Solo título")));
        verifyNoInteractions(repo, llm);
    }

    @Test
    void rechazaValoresNoFinitosYVectoresNulos() {
        float[] vector = new float[1536]; vector[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> DocumentoService.validarVector(vector));
        assertThrows(IllegalArgumentException.class, () -> DocumentoService.validarVector(new float[1536]));
    }
}

package com.lysandri.domain.ports.out;

import com.lysandri.domain.model.Documento;
import com.lysandri.domain.model.DocumentoChunk;
import java.util.List;

public interface DocumentoRepositoryPort {
    List<DocumentoChunk> buscarTemarios(Long idPrograma);

    List<DocumentoChunk> buscarFicha(Long idPrograma);

    List<DocumentoChunk> buscarPorTitulo(String titulo, int limite);

    Long guardar(Documento documento, List<DocumentoChunk> fragmentos);
}

package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoChunk {
    private Long idChunk;
    private Long idDocumento;
    private Long idPrograma;
    private Integer numeroPagina;
    private String contenido;
    private Map<String, Object> metadata;
    private float[] embedding;
    private Double similitud;
    private OffsetDateTime fechaCreacion;
}

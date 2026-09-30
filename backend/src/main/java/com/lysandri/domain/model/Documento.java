package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Documento {
    private Long idDocumento;
    private Long idPrograma;
    private String titulo;
    private String tipoDocumento;
    private String urlArchivo;
    private String checksumSha256;
    private boolean activo;
    private OffsetDateTime fechaSubida;
}

package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "DOCUMENTO_CHUNK")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_chunk")
    private Long idChunk;

    @Column(name = "id_documento", nullable = false)
    private Long idDocumento;

    @Column(name = "id_programa")
    private Long idPrograma;

    @Column(name = "numero_pagina")
    private Integer numeroPagina;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();
}

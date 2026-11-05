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
public class AuditoriaContable {
    private Long id;
    private String accion;
    private String entidad;
    private Long entidadId;
    private String actor;
    private String motivo;
    private OffsetDateTime fechaHora;
}

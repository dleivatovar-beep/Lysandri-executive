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
public class UsuarioCambioAudit {
    private Long id;
    private Long idUsuario;
    private String email;
    private String accion;
    private RolUsuario rolAnterior;
    private RolUsuario rolNuevo;
    private String motivo;
    private String ejecutadoPor;
    private OffsetDateTime fechaHora;
}

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
public class SolicitudInformacion {
    private Long idSolicitud;
    private Long idPrograma;
    private String nombreCompleto;
    private String email;
    private String telefono;
    private String empresa;
    private String cargo;
    private String mensaje;
    private String estado;
    private OffsetDateTime fechaCreacion;
}

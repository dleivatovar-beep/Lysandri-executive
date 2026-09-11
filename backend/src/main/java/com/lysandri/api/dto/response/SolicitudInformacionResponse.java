package com.lysandri.api.dto.response;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudInformacionResponse {

    private Integer idSolicitud;
    private String nombreCompleto;
    private String email;
    private String telefono;
    private Integer programaId;
    private String tituloPrograma;
    private String mensaje;
    private String estado;
    private OffsetDateTime fechaSolicitud;
    private OffsetDateTime fechaActualizacion;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaAtencion;
    private String notasAdmin;
}

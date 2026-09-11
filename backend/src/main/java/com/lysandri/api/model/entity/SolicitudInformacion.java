package com.lysandri.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "SOLICITUD_INFORMACION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudInformacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Integer idSolicitud;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programa")
    private Programa programa;

    @Column(name = "mensaje", columnDefinition = "TEXT")
    private String mensaje;

    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "fecha_atencion")
    private OffsetDateTime fechaAtencion;

    @Column(name = "notas_admin", columnDefinition = "TEXT")
    private String notasAdmin;
}

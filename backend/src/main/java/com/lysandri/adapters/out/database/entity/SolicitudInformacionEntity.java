package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "SOLICITUD_INFORMACION")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudInformacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @Column(name = "id_programa")
    private Long idPrograma;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "telefono", length = 25)
    private String telefono;

    @Column(name = "empresa", length = 150)
    private String empresa;

    @Column(name = "cargo", length = 100)
    private String cargo;

    @Column(name = "mensaje", columnDefinition = "TEXT")
    private String mensaje;

    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();
}

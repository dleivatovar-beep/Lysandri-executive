package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "auditoria_contable")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaContableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "accion", nullable = false, length = 50)
    private String accion;

    @Column(name = "entidad", nullable = false, length = 50)
    private String entidad;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @Column(name = "actor", nullable = false, length = 150)
    private String actor;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @Column(name = "fecha_hora", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaHora = OffsetDateTime.now();
}

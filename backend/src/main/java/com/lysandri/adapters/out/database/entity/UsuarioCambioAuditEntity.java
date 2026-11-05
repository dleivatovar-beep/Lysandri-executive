package com.lysandri.adapters.out.database.entity;

import com.lysandri.domain.model.RolUsuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "usuario_cambio_audit")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioCambioAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria")
    private Long idAuditoria;

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "accion", nullable = false, length = 50)
    private String accion;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_anterior", length = 30)
    private RolUsuario rolAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_nuevo", length = 30)
    private RolUsuario rolNuevo;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @Column(name = "ejecutado_por", nullable = false, length = 150)
    private String ejecutadoPor;

    @Column(name = "fecha_hora", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaHora = OffsetDateTime.now();
}

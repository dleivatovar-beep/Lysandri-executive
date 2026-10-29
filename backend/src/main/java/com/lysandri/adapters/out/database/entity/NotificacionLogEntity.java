package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notificaciones_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long idNotificacion;

    @Column(name = "id_orden")
    private Long idOrden;

    @Column(name = "codigo_orden", length = 36)
    private String codigoOrden;

    @Column(name = "destinatario", nullable = false, length = 255)
    private String destinatario;

    @Column(name = "tipo_notificacion", nullable = false, length = 50)
    private String tipoNotificacion;

    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 255)
    private String claveIdempotencia;

    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "intentos", nullable = false)
    @Builder.Default
    private int intentos = 0;

    @Column(name = "ultimo_error", columnDefinition = "TEXT")
    private String ultimoError;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();

    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;
}

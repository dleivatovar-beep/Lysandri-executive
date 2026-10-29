package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "outbox_notificacion_activacion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationOutboxTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tarea")
    private Long idTarea;

    @Column(name = "id_orden")
    private Long idOrden;

    @Column(name = "codigo_orden", length = 36)
    private String codigoOrden;

    @Column(name = "id_user", nullable = false)
    private Long idUser;

    @Column(name = "id_token", nullable = false)
    private Long idToken;

    @Column(name = "enlace_cifrado", columnDefinition = "TEXT")
    private String enlaceCifrado;

    @Column(name = "destinatario", nullable = false, length = 255)
    private String destinatario;

    @Column(name = "nombre_destinatario", length = 255)
    private String nombreDestinatario;

    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 255)
    private String claveIdempotencia;

    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private String estado = "PENDIENTE";

    @Column(name = "intentos", nullable = false)
    @Builder.Default
    private int intentos = 0;

    @Column(name = "max_intentos", nullable = false)
    @Builder.Default
    private int maxIntentos = 5;

    @Column(name = "proximo_intento", nullable = false)
    @Builder.Default
    private OffsetDateTime proximoIntento = OffsetDateTime.now();

    @Column(name = "bloqueado_hasta")
    private OffsetDateTime bloqueadoHasta;

    @Column(name = "bloqueado_por", length = 100)
    private String bloqueadoPor;

    @Column(name = "ultimo_error", columnDefinition = "TEXT")
    private String ultimoError;

    @Column(name = "fecha_creacion", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();

    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Column(name = "fecha_actualizacion", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaActualizacion = OffsetDateTime.now();
}

package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "STRIPE_PROCESSED_EVENT")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripeProcessedEventEntity {

    @Id
    @Column(name = "id_evento", length = 255)
    private String idEvento;

    @Column(name = "tipo_evento", nullable = false, length = 100)
    private String tipoEvento;

    @Column(name = "stripe_session_id", length = 255)
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "id_orden")
    private Long idOrden;

    @Column(name = "codigo_orden", length = 36)
    private String codigoOrden;

    @Column(name = "estado", nullable = false, length = 30)
    @Builder.Default
    private String estado = "RECIBIDO"; // RECIBIDO, PROCESANDO, COMPLETADO, FALLIDO

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;

    @Column(name = "lease_expires_at")
    private OffsetDateTime leaseExpiresAt;

    @Column(name = "owner_token", length = 64)
    private String ownerToken;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 1;

    @Column(name = "fecha_registro", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaRegistro = OffsetDateTime.now();

    @Column(name = "fecha_procesamiento")
    private OffsetDateTime fechaProcesamiento;
}

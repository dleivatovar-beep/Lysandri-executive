package com.lysandri.adapters.out.database.entity;

import com.lysandri.domain.model.EstadoOrden;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ORDENES")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden")
    private Long idOrden;

    @Column(name = "id_user", nullable = false)
    private Long idUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", insertable = false, updatable = false)
    private UsuarioEntity usuario;

    @Column(name = "codigo_orden", nullable = false, unique = true, length = 36)
    private String codigoOrden;

    @Column(name = "fecha_orden", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaOrden = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_orden", nullable = false, length = 30)
    @Builder.Default
    private EstadoOrden estadoOrden = EstadoOrden.PENDIENTE;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "moneda", nullable = false, length = 3)
    @Builder.Default
    private String moneda = "USD";

    @Column(name = "metodo_pago", nullable = false, length = 50)
    @Builder.Default
    private String metodoPago = "IZIPAY";

    @Column(name = "stripe_session_id", unique = true, length = 255)
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id", unique = true, length = 255)
    private String stripePaymentIntentId;

    @Column(name = "izipay_transaction_id", length = 255)
    private String izipayTransactionId;

    @Column(name = "izipay_order_id", length = 255)
    private String izipayOrderId;

    @Column(name = "izipay_uuid", length = 255)
    private String izipayUuid;

    @Column(name = "moodle_matricula_sincronizada", nullable = false)
    @Builder.Default
    private boolean moodleMatriculaSincronizada = false;

    @Column(name = "fecha_pago")
    private OffsetDateTime fechaPago;

    @Column(name = "tipo_comprobante_solicitado", length = 10)
    private String tipoComprobanteSolicitado;

    @Column(name = "numero_documento_cliente", length = 15)
    private String numeroDocumentoCliente;

    @Column(name = "nombre_facturacion", length = 200)
    private String nombreFacturacion;

    @Column(name = "numero_operacion", length = 100)
    private String numeroOperacion;

    @Column(name = "notas_admin", length = 500)
    private String notasAdmin;

    @Column(name = "clave_idempotencia", length = 150)
    private String claveIdempotencia;

    @Column(name = "guest_session_hash", length = 64)
    private String guestSessionHash;

    // Campos de auditoría y verificación de transferencias bancarias y pagos manuales
    @Column(name = "banco_destino", length = 50)
    private String bancoDestino;

    @Column(name = "cuenta_destino", length = 100)
    private String cuentaDestino;

    @Column(name = "monto_verificado", precision = 10, scale = 2)
    private BigDecimal montoVerificado;

    @Column(name = "moneda_verificada", length = 3)
    private String monedaVerificada;

    @Column(name = "fecha_abono")
    private OffsetDateTime fechaAbono;

    @Column(name = "aprobado_por", length = 150)
    private String aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private OffsetDateTime fechaAprobacion;

    @Column(name = "observacion_verificacion", length = 500)
    private String observacionVerificacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_aprovisionamiento", length = 30)
    @Builder.Default
    private com.lysandri.domain.model.EstadoAprovisionamiento estadoAprovisionamiento = com.lysandri.domain.model.EstadoAprovisionamiento.PENDIENTE;

    @Column(name = "aprovisionamiento_lease_token", length = 100)
    private String aprovisionamientoLeaseToken;

    @Column(name = "aprovisionamiento_lease_expires_at")
    private OffsetDateTime aprovisionamientoLeaseExpiresAt;

    @Column(name = "aprovisionamiento_intentos")
    @Builder.Default
    private int aprovisionamientoIntentos = 0;

    @Column(name = "aprovisionamiento_ultimo_error", columnDefinition = "TEXT")
    private String aprovisionamientoUltimoError;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DetalleOrdenEntity> items = new ArrayList<>();
}

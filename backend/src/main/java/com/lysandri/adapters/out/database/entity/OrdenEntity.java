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
    private String metodoPago = "STRIPE";

    @Column(name = "stripe_session_id", unique = true, length = 255)
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id", unique = true, length = 255)
    private String stripePaymentIntentId;

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

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DetalleOrdenEntity> items = new ArrayList<>();
}

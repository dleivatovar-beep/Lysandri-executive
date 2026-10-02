package com.lysandri.adapters.out.database.entity;

import com.lysandri.domain.model.EstadoComprobante;
import com.lysandri.domain.model.TipoComprobante;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "comprobante_pago")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprobantePagoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comprobante")
    private Long idComprobante;

    @Column(name = "id_orden", nullable = false, unique = true)
    private Long idOrden;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoComprobante tipo;

    @Column(name = "serie", nullable = false, length = 5)
    private String serie;

    @Column(name = "correlativo", nullable = false)
    private Integer correlativo;

    @Column(name = "tipo_documento_identidad", nullable = false, length = 10)
    private String tipoDocumentoIdentidad;

    @Column(name = "numero_documento_identidad", nullable = false, length = 15)
    private String numeroDocumentoIdentidad;

    @Column(name = "razon_social_o_nombre", nullable = false, length = 200)
    private String razonSocialONombre;

    @Column(name = "monto_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoSubtotal;

    @Column(name = "monto_igv", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoIgv;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "moneda", length = 3)
    @Builder.Default
    private String moneda = "PEN";

    @Column(name = "fecha_emision")
    @Builder.Default
    private LocalDateTime fechaEmision = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 10)
    @Builder.Default
    private EstadoComprobante estado = EstadoComprobante.EMITIDO;

    @Column(name = "pdf_url", length = 255)
    private String pdfUrl;
}

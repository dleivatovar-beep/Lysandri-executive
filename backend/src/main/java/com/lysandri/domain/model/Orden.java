package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Orden {
    private Long idOrden;
    private Long idUser;
    private Usuario usuario;
    private String codigoOrden;
    private OffsetDateTime fechaOrden;
    private EstadoOrden estadoOrden;
    private BigDecimal total;
    private String moneda;
    private String metodoPago;
    private String stripeSessionId;
    private String stripePaymentIntentId;
    private String izipayTransactionId;
    private String izipayOrderId;
    private String izipayUuid;
    private boolean moodleMatriculaSincronizada;
    private OffsetDateTime fechaPago;

    private String tipoComprobanteSolicitado;
    private String numeroDocumentoCliente;
    private String nombreFacturacion;

    private String numeroOperacion;
    private String notasAdmin;

    // Clave de idempotencia para compras y reintentos atómicos
    private String claveIdempotencia;
    // Hash criptográfico de la sesión de compra para invitados
    private String guestSessionHash;

    // Campos de auditoría y verificación para transferencias bancarias y pagos manuales
    private String bancoDestino;
    private String cuentaDestino;
    private BigDecimal montoVerificado;
    private String monedaVerificada;
    private OffsetDateTime fechaAbono;
    private String aprobadoPor;
    private OffsetDateTime fechaAprobacion;
    private String observacionVerificacion;

    @Builder.Default
    private EstadoAprovisionamiento estadoAprovisionamiento = EstadoAprovisionamiento.PENDIENTE;
    private String aprovisionamientoLeaseToken;
    private OffsetDateTime aprovisionamientoLeaseExpiresAt;
    private int aprovisionamientoIntentos;
    private String aprovisionamientoUltimoError;

    @Builder.Default
    private List<DetalleOrden> items = new ArrayList<>();
}

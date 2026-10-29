package com.lysandri.adapters.in.web.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    @NotEmpty(message = "Debe enviar al menos un ID de programa a comprar")
    private List<Long> programaIds;

    // Datos opcionales para compra como invitado y comprobante fiscal
    private String email;
    private String nombreCompleto;
    private String tipoComprobante; // "BOLETA" o "FACTURA"
    private String numeroDocumento; // DNI o RUC
    private String nombreFacturacion; // Razón social o Nombre completo
    private String metodoPago; // "STRIPE", "YAPE", "PLIN", "TRANSFERENCIA"
    private String numeroOperacion; // Número de constancia o voucher manual
    private String claveIdempotencia; // Clave de idempotencia única por intento de compra
    private String guestSessionToken; // Token seguro emitido por el backend para compras de invitados
}

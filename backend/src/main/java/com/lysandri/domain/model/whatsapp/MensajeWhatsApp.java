package com.lysandri.domain.model.whatsapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MensajeWhatsApp {
    private Long id;
    private String idProveedor;
    private String idSolicitud;
    private String telefonoRemitente;
    private String telefonoDestinatario;
    private DireccionMensajeWhatsApp direccion;
    private String contenido;
    private TipoMensajeWhatsApp tipo;
    private EstadoMensajeWhatsApp estado;
    private OffsetDateTime timestampMensaje;
    private String operador;
    private String tokenIntento;
    private OffsetDateTime bloqueadoHasta;
    @Builder.Default
    private Integer intentoCount = 0;
    private String errorDetalle;
    private OffsetDateTime fechaCreacion;

    /**
     * Retorna una representación enmascarada del teléfono para logs seguros.
     * Ejemplo: "+51999888777" -> "+51 9**** 8777"
     */
    public static String enmascararTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return "[desconocido]";
        }
        String clean = telefono.replaceAll("[^0-9+]", "");
        if (clean.length() <= 6) {
            return "***";
        }
        int len = clean.length();
        String prefix = clean.substring(0, Math.min(4, len - 4));
        String suffix = clean.substring(len - 4);
        return prefix + "****" + suffix;
    }
}

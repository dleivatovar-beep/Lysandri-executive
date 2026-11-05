package com.lysandri.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnviarWhatsAppRequest {

    @NotBlank(message = "El número de teléfono del destinatario es obligatorio")
    private String destinatario;

    @NotBlank(message = "El contenido del mensaje no puede estar vacío")
    private String mensaje;

    private String idSolicitud;

    public EnviarWhatsAppRequest(String destinatario, String mensaje) {
        this.destinatario = destinatario;
        this.mensaje = mensaje;
    }
}

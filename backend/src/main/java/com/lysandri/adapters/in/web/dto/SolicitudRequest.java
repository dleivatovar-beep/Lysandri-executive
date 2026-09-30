package com.lysandri.adapters.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SolicitudRequest {
    private Long idPrograma;

    @NotBlank(message = "El nombre completo es requerido")
    private String nombreCompleto;

    @NotBlank(message = "El correo es requerido")
    @Email(message = "Formato de email inválido")
    private String email;

    private String telefono;
    private String empresa;
    private String cargo;
    private String mensaje;
}

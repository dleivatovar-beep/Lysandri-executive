package com.lysandri.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudInformacionRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    @Size(max = 150, message = "El nombre no debe exceder 150 caracteres")
    private String nombreCompleto;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato de correo electrónico no es válido")
    @Size(max = 150, message = "El correo no debe exceder 150 caracteres")
    private String email;

    @Size(max = 20, message = "El teléfono no debe exceder 20 caracteres")
    private String telefono;

    private Integer programaId;

    private String mensaje;
}

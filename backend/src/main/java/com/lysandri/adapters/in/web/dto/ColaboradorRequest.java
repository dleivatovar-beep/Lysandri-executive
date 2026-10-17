package com.lysandri.adapters.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColaboradorRequest {

    @NotBlank(message = "El nombre del colaborador es obligatorio")
    private String nombres;

    @NotBlank(message = "El apellido del colaborador es obligatorio")
    private String apellidos;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "Formato de correo electrónico inválido")
    private String email;

    private String cargo;
    private String telefono;
}

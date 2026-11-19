package com.lysandri.adapters.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "Los nombres admiten hasta 100 caracteres")
    private String nombres;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "Los apellidos admiten hasta 100 caracteres")
    private String apellidos;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Size(max = 150, message = "El correo admite hasta 150 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres")
    private String password;

    @Size(max = 25, message = "El teléfono admite hasta 25 caracteres")
    private String telefono;
    @jakarta.validation.constraints.AssertTrue(message="Debes aceptar las condiciones de registro")
    private boolean aceptaTerminos;
    @jakarta.validation.constraints.AssertTrue(message="Debes aceptar el tratamiento de datos indicado")
    private boolean aceptaPrivacidad;
    @NotBlank(message = "La versión de las condiciones es obligatoria")
    private String version;
}

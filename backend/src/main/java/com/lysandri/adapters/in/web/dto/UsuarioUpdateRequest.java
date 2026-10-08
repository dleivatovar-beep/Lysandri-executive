package com.lysandri.adapters.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioUpdateRequest {

    private String nombres;

    private String apellidos;

    private String email;

    private String passw;

    private String telefono;

    private String rol;

    private Boolean activo;
}

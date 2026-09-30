package com.lysandri.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {
    private Long idUser;
    private Long moodleUserId;
    private String nombres;
    private String apellidos;
    private String email;
    private String passw;
    private String telefono;
    private RolUsuario rol;
    private boolean activo;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaActualizacion;

    public String getNombreCompleto() {
        return (nombres != null ? nombres : "") + " " + (apellidos != null ? apellidos : "");
    }
}

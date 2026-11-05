package com.lysandri.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    private String passw;
    private String telefono;
    private RolUsuario rol;
    private boolean activo;
    private boolean bloqueadoAdmin;
    private boolean pendienteActivacion;
    private OffsetDateTime fechaCreacion;
    private OffsetDateTime fechaActualizacion;

    public String getNombreCompleto() {
        return (nombres != null ? nombres : "") + " " + (apellidos != null ? apellidos : "");
    }
}

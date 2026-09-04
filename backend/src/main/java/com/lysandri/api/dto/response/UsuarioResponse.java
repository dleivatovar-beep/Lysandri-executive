package com.lysandri.api.dto.response;

import com.lysandri.api.model.enums.Rol;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioResponse {

    private Integer idUser;
    private String nombres;
    private String apellidos;
    private String email;
    private String telefono;
    private Rol rol;
}

package com.lysandri.api.dto.response;

import com.lysandri.api.model.enums.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {

    private String token;
    private Integer id;
    private String nombre;
    private String email;
    private Rol rol;
}

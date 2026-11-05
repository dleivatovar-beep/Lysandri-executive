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
public class UsuarioActivacionToken {
    private Long idToken;
    private Long idUser;
    private String token;
    private OffsetDateTime fechaExpiracion;
    private boolean usado;
    private OffsetDateTime fechaUso;
    private boolean revocado;
    private OffsetDateTime fechaRevocacion;
    private OffsetDateTime fechaCreacion;
}

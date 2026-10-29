package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "USUARIO_ACTIVACION_TOKEN")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioActivacionTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    private Long idToken;

    @Column(name = "id_user", nullable = false)
    private Long idUser;

    @Column(name = "token", nullable = false, unique = true, length = 128)
    private String token;

    @Column(name = "fecha_expiracion", nullable = false)
    private OffsetDateTime fechaExpiracion;

    @Column(name = "usado", nullable = false)
    @Builder.Default
    private boolean usado = false;

    @Column(name = "fecha_uso")
    private OffsetDateTime fechaUso;

    @Column(name = "revocado", nullable = false)
    @Builder.Default
    private boolean revocado = false;

    @Column(name = "fecha_revocacion")
    private OffsetDateTime fechaRevocacion;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();
}

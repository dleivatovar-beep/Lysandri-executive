package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_session")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionEntity {

    @Id
    @Column(name = "id_sesion", nullable = false, length = 64)
    private String idSesion;

    @Column(name = "id_user")
    private Long idUser;

    @Column(name = "session_token_hash", nullable = false, length = 128)
    private String sessionTokenHash;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();

    @Column(name = "ultima_actividad", nullable = false)
    @Builder.Default
    private OffsetDateTime ultimaActividad = OffsetDateTime.now();
}


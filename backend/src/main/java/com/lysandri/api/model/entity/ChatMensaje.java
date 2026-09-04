package com.lysandri.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "CHAT_MENSAJES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensaje")
    private Integer idMensaje;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private Usuario usuario;

    @Column(name = "pregunta", nullable = false, columnDefinition = "TEXT")
    private String pregunta;

    @Column(name = "respuesta_ia", nullable = false, columnDefinition = "TEXT")
    private String respuestaIa;

    @Column(name = "fecha", insertable = false, updatable = false)
    private OffsetDateTime fecha;
}

package com.lysandri.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "PROGRESO_LECCION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgresoLeccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_progreso")
    private Integer idProgreso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_leccion", nullable = false)
    private Leccion leccion;

    @Column(name = "completado")
    @Builder.Default
    private Boolean completado = false;

    @Column(name = "fecha_completado")
    private OffsetDateTime fechaCompletado;
}

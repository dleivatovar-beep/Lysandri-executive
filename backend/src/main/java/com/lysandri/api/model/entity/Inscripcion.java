package com.lysandri.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "INSCRIPCIONES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inscripcion")
    private Integer idInscripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programa", nullable = false)
    private Programa programa;

    @Column(name = "fecha_inscripcion", insertable = false, updatable = false)
    private OffsetDateTime fechaInscripcion;

    @Column(name = "fecha_limite_acceso")
    private LocalDate fechaLimiteAcceso;

    @Column(name = "estatus", length = 30)
    private String estatus;

    @Column(name = "porcentaje_progreso", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal porcentajeProgreso = BigDecimal.ZERO;
}

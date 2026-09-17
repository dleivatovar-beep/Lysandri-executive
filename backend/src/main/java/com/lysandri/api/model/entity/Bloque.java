package com.lysandri.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "BLOQUE",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_bloque_programa_orden",
                        columnNames = {"id_programa", "orden"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bloque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bloque")
    private Integer idBloque;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_programa", nullable = false)
    private Programa programa;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @OneToMany(
            mappedBy = "bloque",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("orden ASC")
    @Builder.Default
    private List<Modulo> modulos = new ArrayList<>();

    @OneToMany(
            mappedBy = "bloque",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("orden ASC")
    @Builder.Default
    private List<ActividadBloque> actividades = new ArrayList<>();
}
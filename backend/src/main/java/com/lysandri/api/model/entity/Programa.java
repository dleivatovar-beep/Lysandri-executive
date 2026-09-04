package com.lysandri.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PROGRAMA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Programa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_programa")
    private Integer idPrograma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_instructor_dni", nullable = false)
    private Instructor instructor;

    @Column(name = "titulo_programa", nullable = false, length = 200)
    private String tituloPrograma;

    @Column(name = "duracion_programa", length = 50)
    private String duracionPrograma;

    @Column(name = "tipo_programa", length = 50)
    private String tipoPrograma;

    @Column(name = "level", length = 20)
    private String level;

    @Column(name = "fecha_inicio_global")
    private LocalDate fechaInicioGlobal;

    @Column(name = "fecha_final_global")
    private LocalDate fechaFinalGlobal;

    @Column(name = "requisitos", columnDefinition = "TEXT")
    private String requisitos;

    @Column(name = "metodologia", columnDefinition = "TEXT")
    private String metodologia;

    @OneToMany(mappedBy = "programa", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Leccion> lecciones = new ArrayList<>();
}

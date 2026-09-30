package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "DETALLE_ORDENES")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleOrdenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long idDetalle;

    @Column(name = "id_orden", nullable = false)
    private Long idOrden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orden", insertable = false, updatable = false)
    private OrdenEntity orden;

    @Column(name = "id_programa", nullable = false)
    private Long idPrograma;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_programa", insertable = false, updatable = false)
    private ProgramaEntity programa;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "cantidad", nullable = false)
    @Builder.Default
    private Integer cantidad = 1;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "moodle_matriculado", nullable = false)
    @Builder.Default
    private boolean moodleMatriculado = false;
}

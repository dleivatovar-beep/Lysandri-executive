package com.lysandri.api.model.entity;

import com.lysandri.api.model.enums.TipoActividad;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "ACTIVIDAD_BLOQUE",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_actividad_bloque_orden",
                        columnNames = {"id_bloque", "orden"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActividadBloque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_actividad")
    private Integer idActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_bloque", nullable = false)
    private Bloque bloque;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "instrucciones", columnDefinition = "TEXT")
    private String instrucciones;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", nullable = false, length = 30)
    private TipoActividad tipoActividad;

    @Column(name = "contenido_url", length = 500)
    private String contenidoUrl;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;
}
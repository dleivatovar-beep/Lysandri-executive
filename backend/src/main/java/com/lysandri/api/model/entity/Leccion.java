package com.lysandri.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "LECCION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Leccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_leccion")
    private Integer idLeccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programa", nullable = false)
    private Programa programa;

    @Column(name = "titulo_leccion", nullable = false, length = 200)
    private String tituloLeccion;

    @Column(name = "tipo_contenido", length = 50)
    private String tipoContenido;

    @Column(name = "media_url", length = 255)
    private String mediaUrl;

    @Column(name = "duracion_leccion", length = 20)
    private String duracionLeccion;
}

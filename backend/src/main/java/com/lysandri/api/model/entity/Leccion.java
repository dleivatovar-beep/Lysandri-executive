package com.lysandri.api.model.entity;

import com.lysandri.api.model.enums.TipoContenido;
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

    /*
     * Se conserva la relación con Programa porque la columna
     * id_programa ya existe y actualmente es obligatoria.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_programa", nullable = false)
    private Programa programa;

    /*
     * Es nullable para mantener compatibilidad con las
     * lecciones antiguas que todavía no tienen módulo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_modulo")
    private Modulo modulo;

    @Column(name = "titulo_leccion", nullable = false, length = 200)
    private String tituloLeccion;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_contenido", length = 50)
    private TipoContenido tipoContenido;

    @Column(name = "media_url", length = 255)
    private String mediaUrl;

    @Column(name = "duracion_leccion", length = 20)
    private String duracionLeccion;

    @Column(name = "orden")
    private Integer orden;
}
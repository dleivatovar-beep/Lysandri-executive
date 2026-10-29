package com.lysandri.adapters.out.database.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "LOTE_MATRICULA_CORPORATIVA")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoteMatriculaCorporativaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lote")
    private Long idLote;

    @Column(name = "id_programa", nullable = false)
    private Long idPrograma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_programa", insertable = false, updatable = false)
    private ProgramaEntity programa;

    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @Column(name = "empresa_nombre", nullable = false, length = 200)
    private String empresaNombre;

    @Column(name = "total_colaboradores", nullable = false)
    private Integer totalColaboradores;

    @Column(name = "total_exitosos", nullable = false)
    private Integer totalExitosos;

    @Column(name = "total_fallidos", nullable = false)
    private Integer totalFallidos;

    @Column(name = "fecha_registro", nullable = false)
    @Builder.Default
    private OffsetDateTime fechaRegistro = OffsetDateTime.now();

    @Column(name = "notas_admin", columnDefinition = "TEXT")
    private String notasAdmin;
}

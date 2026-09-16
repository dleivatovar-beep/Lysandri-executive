package com.lysandri.api.dto.response;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueResponse {

    private Integer idBloque;
    private Integer programaId;
    private String titulo;
    private String descripcion;
    private Integer orden;
    private Boolean activo;

    @Builder.Default
    private List<ModuloResponse> modulos = new ArrayList<>();

    @Builder.Default
    private List<ActividadBloqueResponse> actividades =
            new ArrayList<>();
}
package com.lysandri.api.dto.response;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuloResponse {

    private Integer idModulo;
    private Integer bloqueId;
    private String titulo;
    private String descripcion;
    private Integer orden;
    private Boolean activo;

    @Builder.Default
    private List<LeccionResponse> lecciones = new ArrayList<>();
}
package com.lysandri.api.dto.response;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstructuraProgramaResponse {

    private Integer programaId;
    private String tituloPrograma;

    @Builder.Default
    private List<BloqueResponse> bloques = new ArrayList<>();
}
package com.lysandri.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InscripcionRequest {

    @NotNull(message = "El ID del programa es obligatorio")
    private Long programaId;

    private Long usuarioId;
}

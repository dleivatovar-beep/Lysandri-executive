package com.lysandri.adapters.in.web.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    @NotEmpty(message = "Debe enviar al menos un ID de programa a comprar")
    private List<Long> programaIds;
}

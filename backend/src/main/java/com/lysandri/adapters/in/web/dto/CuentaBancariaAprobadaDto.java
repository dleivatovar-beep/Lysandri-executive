package com.lysandri.adapters.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuentaBancariaAprobadaDto {
    private String id;
    private String banco;
    private String titular;
    private String moneda;
    private String tipoCuenta;
    private String numeroCuenta;
    private String cci;
    private boolean activo;
    private String estado;
    private String instrucciones;
}


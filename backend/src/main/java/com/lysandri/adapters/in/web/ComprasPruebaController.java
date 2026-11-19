package com.lysandri.adapters.in.web;

import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.application.service.ComprasPruebaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/pruebas/comerciales")
@RequiredArgsConstructor
@Profile("!prod & !production & (clone | test | original-dev) & sandbox")
@ConditionalOnProperty(name="purchase.sandbox.enabled",havingValue="true")
public class ComprasPruebaController {
    private final ComprasPruebaService compras;
    public record Resultado(UUID evento,String resultado) {}
    @GetMapping("/cliente")
    @PreAuthorize("hasRole('CLIENTE')")
    public Map<String, Boolean> cliente() {
        return Map.of("habilitado", true);
    }

    @PostMapping("/cliente/cotizar")
    @PreAuthorize("hasRole('CLIENTE')")
    public ComprasPruebaService.Cotizacion cotizarCliente(@RequestBody ComprasPruebaService.Solicitud solicitud,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return compras.cotizar(solicitud,usuario.getIdUser());
    }

    @PostMapping("/cliente/compras")
    @PreAuthorize("hasRole('CLIENTE')")
    public ComprasPruebaService.Compra registrarCliente(
            @AuthenticationPrincipal UsuarioEntity usuario, @RequestBody ComprasPruebaService.Solicitud solicitud) {
        return compras.registrar(usuario.getIdUser(), solicitud);
    }

    @GetMapping("/cliente/compras/{id}")
    @PreAuthorize("hasRole('CLIENTE')")
    public org.springframework.http.ResponseEntity<ComprasPruebaService.Seguimiento> seguimientoCliente(
            @AuthenticationPrincipal UsuarioEntity usuario, @PathVariable UUID id) {
        return org.springframework.http.ResponseEntity.of(compras.seguimiento(usuario.getIdUser(), id));
    }

    @GetMapping("/programas") @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public Object programas() {return compras.programas();}
    @GetMapping("/compras") @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public Object listar() {return compras.listar();}
    @GetMapping("/compras/{id}/cuotas") @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public Object cuotas(@PathVariable UUID id) {return compras.cuotas(id);}
    @PostMapping("/compras/{id}/cuotas/{numero}/resultado") @PreAuthorize("hasRole('ADMIN')")
    public Object cuota(@AuthenticationPrincipal UsuarioEntity usuario,@PathVariable UUID id,@PathVariable int numero,@RequestBody Resultado resultado) {
        return compras.registrarCuota(usuario.getIdUser(),id,numero,resultado.evento(),resultado.resultado());
    }
    @PostMapping("/beneficios") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,String> beneficio(@RequestBody ComprasPruebaService.Beneficio beneficio) {
        compras.guardarBeneficio(beneficio); return Map.of("mensaje","Beneficio de prueba guardado.");
    }
    @PostMapping("/cotizar") @PreAuthorize("hasRole('ADMIN')")
    public Object cotizar(@RequestBody ComprasPruebaService.Solicitud solicitud,@AuthenticationPrincipal UsuarioEntity usuario) {
        return compras.cotizar(solicitud,usuario.getIdUser());
    }
    @GetMapping("/beneficios") @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public Object beneficios() {return compras.beneficios();}
    @PostMapping("/compras") @PreAuthorize("hasRole('ADMIN')")
    public Object registrar(@AuthenticationPrincipal UsuarioEntity usuario,@RequestBody ComprasPruebaService.Solicitud solicitud) {
        return compras.registrar(usuario.getIdUser(),solicitud);
    }
    @PostMapping("/compras/{id}/resultado") @PreAuthorize("hasRole('ADMIN')")
    public Object resultado(@AuthenticationPrincipal UsuarioEntity usuario,@PathVariable UUID id,@RequestBody Resultado resultado) {
        return compras.resultado(usuario.getIdUser(),id,resultado.evento(),resultado.resultado());
    }
}

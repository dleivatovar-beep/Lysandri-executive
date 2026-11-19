package com.lysandri.adapters.in.web;

import com.lysandri.application.service.TesoreriaService;
import com.lysandri.application.service.TesoreriaService.Movimiento;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal/tesoreria")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS','AUDITOR')")
public class TesoreriaController {
    private final TesoreriaService tesoreria;

    public record Importacion(
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.constraints.Size(max = 500)
            java.util.List<@jakarta.validation.constraints.NotNull @Valid Movimiento> movimientos) {}

    @GetMapping
    public Map<String, Object> consultar(@RequestParam LocalDate desde, @RequestParam LocalDate hasta,
                                         @RequestParam String entorno) {
        return tesoreria.consultar(desde, hasta, entorno);
    }

    @PostMapping("/liquidaciones")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS')")
    public Map<String, Object> liquidacion(@Valid @RequestBody TesoreriaService.Liquidacion datos, Authentication usuario) {
        return tesoreria.registrar(datos, usuario.getName());
    }

    @PostMapping("/movimientos")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS')")
    public Map<String, Object> movimiento(@Valid @RequestBody TesoreriaService.Movimiento datos, Authentication usuario) {
        return tesoreria.registrar(datos, usuario.getName());
    }

    @PostMapping("/movimientos/importar")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS')")
    public Object importar(
            @Valid @RequestBody Importacion datos, Authentication usuario) {
        return tesoreria.importar(datos.movimientos(), usuario.getName());
    }

    @PostMapping("/conciliaciones/automaticas")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS')")
    public Map<String, Integer> automaticas(@RequestParam LocalDate desde, @RequestParam LocalDate hasta,
                                           @RequestParam String entorno, Authentication usuario) {
        return Map.of("conciliadas", tesoreria.conciliarCoincidencias(desde, hasta, entorno, usuario.getName()));
    }

    @PostMapping("/conciliaciones")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS')")
    public Map<String, Object> conciliar(@Valid @RequestBody TesoreriaService.Conciliacion datos, Authentication usuario) {
        return tesoreria.conciliar(datos, usuario.getName());
    }
}

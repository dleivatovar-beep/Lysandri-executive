package com.lysandri.adapters.in.web;

import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.application.service.ConversionPruebaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pruebas/conversion")
@RequiredArgsConstructor
@Profile("!prod & !production & (clone | test | original-dev) & sandbox")
@ConditionalOnProperty(name="purchase.sandbox.enabled",havingValue="true")
public class ConversionPruebaController {
    private final ConversionPruebaService conversion;
    public record Visita(UUID id,String dispositivo,String canal,String consentimiento) {}
    public record Inicio(UUID id,long programa) {}
    public record Compra(UUID id,UUID compra) {}

    @GetMapping("/disponible")
    public java.util.Map<String,Boolean> disponible() {return java.util.Map.of("habilitado",true);}

    @PostMapping("/visita") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void visitar(@RequestBody Visita datos) {conversion.visita(datos.id(),datos.dispositivo(),datos.canal(),datos.consentimiento());}

    @PostMapping("/inicio") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void iniciar(@RequestBody Inicio datos) {conversion.iniciar(datos.id(),datos.programa());}

    @DeleteMapping("/visita/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocar(@PathVariable UUID id) {conversion.revocar(id);}

    @PostMapping("/compra") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CLIENTE')")
    public void comprar(@AuthenticationPrincipal UsuarioEntity usuario,@RequestBody Compra datos) {
        conversion.asociar(datos.id(),datos.compra(),usuario.getIdUser());
    }

    @GetMapping("/reporte")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS','AUDITOR')")
    public List<ConversionPruebaService.Fila> reporte(@RequestParam LocalDate desde,@RequestParam LocalDate hasta) {
        return conversion.consultar(desde,hasta);
    }
}

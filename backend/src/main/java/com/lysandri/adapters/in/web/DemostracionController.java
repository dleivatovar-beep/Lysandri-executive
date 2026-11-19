package com.lysandri.adapters.in.web;

import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.application.service.CobrosPruebaService;
import com.lysandri.application.service.EmpresasPruebaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pruebas/demostracion")
@RequiredArgsConstructor
@Profile("!prod & !production & (clone | test | original-dev) & sandbox")
@ConditionalOnProperty(name = "purchase.sandbox.enabled", havingValue = "true")
public class DemostracionController {
    private final CobrosPruebaService cobros;
    private final EmpresasPruebaService empresas;

    public record Autorizacion(boolean acepta,String escenario) {}
    public record Estado(String estado) {}

    @GetMapping("/cliente/cobros/{id}")
    @PreAuthorize("hasRole('CLIENTE')")
    public Object cobros(@PathVariable UUID id, @AuthenticationPrincipal UsuarioEntity usuario) {
        return Map.of("planes",cobros.estado(id,usuario.getIdUser()),"intentos",cobros.historial(id,usuario.getIdUser()));
    }

    @PostMapping("/cliente/cobros/{id}")
    @PreAuthorize("hasRole('CLIENTE')")
    public void autorizar(@PathVariable UUID id,@AuthenticationPrincipal UsuarioEntity usuario,@RequestBody Autorizacion datos) {
        cobros.autorizar(id,usuario.getIdUser(),datos.acepta(),datos.escenario());
    }

    @DeleteMapping("/cliente/cobros/{id}")
    @PreAuthorize("hasRole('CLIENTE')")
    public void cancelar(@PathVariable UUID id,@AuthenticationPrincipal UsuarioEntity usuario) {
        cobros.cancelar(id,usuario.getIdUser());
    }

    @GetMapping("/pasarela")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS','AUDITOR')")
    public Object pasarela() { return cobros.conciliaciones(); }

    @PostMapping("/pasarela")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD')")
    public void importar(@RequestBody CobrosPruebaService.Reporte reporte) {
        cobros.guardarReporte(reporte);
        cobros.conciliar();
    }

    @GetMapping("/empresas")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS','AUDITOR')")
    public Object empresas() { return empresas.listar(null); }

    @GetMapping("/empresas/excel")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS','AUDITOR')")
    public ResponseEntity<byte[]> excel() { return hoja(null); }

    @PostMapping("/empresas")
    @PreAuthorize("hasRole('ADMIN')")
    public Object crear(@Valid @RequestBody EmpresasPruebaService.Orden orden,@AuthenticationPrincipal UsuarioEntity usuario) {
        return Map.of("id",empresas.registrar(orden,usuario.getIdUser()));
    }

    @PostMapping("/empresas/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public void cambiar(@PathVariable UUID id,@RequestBody Estado estado,@AuthenticationPrincipal UsuarioEntity usuario) {
        empresas.cambiar(id,estado.estado(),usuario.getIdUser());
    }

    @GetMapping("/empresas/{id}/participantes")
    @PreAuthorize("hasAnyRole('ADMIN','CONTABILIDAD','FINANZAS','AUDITOR')")
    public Object participantes(@PathVariable UUID id) { return empresas.participantes(id,null); }

    @GetMapping("/cliente/empresas")
    @PreAuthorize("hasRole('CLIENTE')")
    public Object propias(@AuthenticationPrincipal UsuarioEntity usuario) { return empresas.listar(usuario.getIdUser()); }

    @GetMapping("/cliente/empresas/excel")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<byte[]> excelPropio(@AuthenticationPrincipal UsuarioEntity usuario) {
        return hoja(usuario.getIdUser());
    }

    @GetMapping("/cliente/empresas/{id}/participantes")
    @PreAuthorize("hasRole('CLIENTE')")
    public Object nominaPropia(@PathVariable UUID id,@AuthenticationPrincipal UsuarioEntity usuario) {
        return empresas.participantes(id,usuario.getIdUser());
    }

    private ResponseEntity<byte[]> hoja(Long gestor) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition","attachment; filename=ordenes-empresa-prueba.xlsx")
                .body(HojaPrueba.generar(empresas.listar(gestor)));
    }
}

package com.lysandri.adapters.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/pmv")
@Profile("!prod & !production & pmv & sandbox & (original-dev | clone | test)")
@RequiredArgsConstructor
public class PmvController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public record Contacto(String dominioEjecutivo,String whatsapp,String correo) {}
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','CONTABILIDAD','CLIENTE','ESTUDIANTE')")
    public Object estado(@AuthenticationPrincipal UsuarioEntity usuario) throws Exception {
        var salida=new LinkedHashMap<String,Object>();
        salida.put("modo","PRUEBA");salida.put("rol",usuario.getRol().name());
        salida.put("contacto",json.readTree(jdbc.queryForObject("SELECT valor::text FROM configuracion_pmv WHERE clave='contacto'",String.class)));
        salida.put("pendientes",json.readTree(jdbc.queryForObject("SELECT valor::text FROM configuracion_pmv WHERE clave='pendientes'",String.class)));
        salida.put("correo","Buzón local; no se envían mensajes a Gmail");
        salida.put("moodle","Integración real pendiente de accesos; las compras de prueba no matriculan en un aula real");
        if (!List.of("CLIENTE","ESTUDIANTE").contains(usuario.getRol().name())) {
            salida.put("cuentas",json.readTree(jdbc.queryForObject("SELECT valor::text FROM configuracion_pmv WHERE clave='cuentas'",String.class)));
            salida.put("compras",jdbc.queryForList("SELECT estado,count(*) AS cantidad FROM compra_prueba WHERE id_usuario IN (SELECT id_user FROM usuario WHERE email LIKE '%.pmv@example.test') GROUP BY estado ORDER BY estado"));
        }
        return salida;
    }
    @PutMapping("/contacto")
    @PreAuthorize("hasRole('ADMIN')")
    public Object contacto(@RequestBody Contacto valor,@AuthenticationPrincipal UsuarioEntity usuario) throws Exception {
        if(valor.dominioEjecutivo()==null || valor.dominioEjecutivo().isBlank() || valor.dominioEjecutivo().length()>120
                || valor.whatsapp()==null || !valor.whatsapp().matches("[0-9]{8,15}")
                || valor.correo()==null || !valor.correo().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || valor.correo().length()>150)
            throw new IllegalArgumentException("Revisa el nombre, el número internacional y el correo.");
        jdbc.update("UPDATE configuracion_pmv SET valor=?::jsonb,operador=?,actualizado=CURRENT_TIMESTAMP WHERE clave='contacto'",json.writeValueAsString(valor),usuario.getIdUser());
        return Map.of("mensaje","Configuración de prueba guardada.");
    }
}

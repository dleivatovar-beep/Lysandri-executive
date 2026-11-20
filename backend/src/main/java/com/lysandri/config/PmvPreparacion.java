package com.lysandri.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.application.service.ComprasPruebaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@Profile("!prod & !production & pmv & sandbox & (original-dev | clone | test)")
@RequiredArgsConstructor
public class PmvPreparacion implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ObjectMapper json;
    private final ComprasPruebaService compras;
    @Override public void run(ApplicationArguments args) throws Exception {
        Path carpeta=Path.of(".pmv"); Files.createDirectories(carpeta);
        Path acceso=carpeta.resolve("accesos.txt");
        if (!Files.exists(acceso)) Files.writeString(acceso,"LYSANDRI - ACCESOS LOCALES DE PRUEBA\nIngreso: http://127.0.0.1:3000/intranet\nPanel: http://127.0.0.1:3000/pmv\nUsuarios ficticios; no utilizar en producción.\n\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        for (String[] persona : new String[][]{{"cliente","CLIENTE"},{"administrador","ADMIN"},{"contador","CONTABILIDAD"},{"gerente","GERENTE"}}) {
            String email=persona[0]+".pmv@example.test";
            if (jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email=?",Integer.class,email)>0) continue;
            byte[] azar=new byte[15];new SecureRandom().nextBytes(azar);
            String password="Pmv!"+Base64.getUrlEncoder().withoutPadding().encodeToString(azar);
            jdbc.update("INSERT INTO usuario(nombres,apellidos,email,passw,rol,activo,bloqueado_admin,pendiente_activacion) VALUES (?,?,?,?,?,true,false,false)", "Prueba",persona[0],email,encoder.encode(password),persona[1]);
            Files.writeString(acceso,persona[1]+"\nCorreo: "+email+"\nContraseña: "+password+"\n\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        }
        var cuentas=List.of(
            Map.of("banco","BCP","cuenta","355-38314750-0-04","cci","Pendiente","moneda","Por confirmar","titular","Por confirmar","habilitada",false),
            Map.of("banco","Interbank","cuenta","512-3100675547","cci","003-512-013100675547-17","moneda","Por confirmar","titular","Por confirmar","habilitada",false),
            Map.of("banco","Interbank","cuenta","488-3421127895","cci","003-488-013421127895-44","moneda","USD","titular","Por confirmar","habilitada",false));
        guardar("contacto",Map.of("dominioEjecutivo","Gestión empresarial — prueba","whatsapp","51954111044","correo","lysandriideaslab@gmail.com"));
        guardar("cuentas",cuentas);
        guardar("pendientes",List.of("Confirmar significado y nombre del dominio ejecutivo", "Confirmar titular y moneda de cuentas; celular de Yape/Plin", "Elegir pasarela y entregar credenciales", "Entregar acceso e identificadores de Moodle", "Configurar correo real antes de producción", "Definir servidor y responsables de operación"));
        var existentes=compras.beneficios().stream().map(b->String.valueOf(b.get("codigo"))).toList();
        for (String[] regla:new String[][]{{"PMV10","DESCUENTO","10"},{"PMVBECA","BECA","50"},{"PMVCONVENIO","CONVENIO","15"}}) {
            if (!existentes.contains(regla[0])) compras.guardarBeneficio(new ComprasPruebaService.Beneficio(regla[0],new BigDecimal(regla[2]),20,OffsetDateTime.now().plusDays(90),true,regla[1],null,null,null,2,false,null));
        }
        long cliente=jdbc.queryForObject("SELECT id_user FROM usuario WHERE email='cliente.pmv@example.test'",Long.class);
        long admin=jdbc.queryForObject("SELECT id_user FROM usuario WHERE email='administrador.pmv@example.test'",Long.class);
        for (int i=0;i<3;i++) {
            UUID clave=UUID.nameUUIDFromBytes(("pmv-inicial-"+i).getBytes(StandardCharsets.UTF_8));
            if (jdbc.queryForObject("SELECT count(*) FROM compra_prueba WHERE id_usuario=? AND clave=?",Integer.class,cliente,clave)>0) continue;
            var solicitud=new ComprasPruebaService.Solicitud(clave,List.of(1001L+i),"",i==1?3:1,"IZIPAY",true,null);
            var precio=compras.cotizar(solicitud,cliente);
            var compra=compras.registrar(cliente,new ComprasPruebaService.Solicitud(clave,solicitud.programas(),"",solicitud.cuotas(),"IZIPAY",true,precio.revision()));
            if(i!=1) compras.resultado(admin,compra.id(),UUID.randomUUID(),i==0?"APROBADA":"RECHAZADA");
        }
    }
    private void guardar(String clave,Object valor) throws Exception {
        jdbc.update("INSERT INTO configuracion_pmv(clave,valor) VALUES (?,?::jsonb) ON CONFLICT DO NOTHING",clave,json.writeValueAsString(valor));
    }
}

package com.lysandri.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.lysandri.adapters.in.web.ComprasPruebaController;
import com.lysandri.adapters.in.web.ConversionPruebaController;
import com.lysandri.application.service.ConversionPruebaService;
import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.application.service.ComprasPruebaService;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.exception.GlobalExceptionHandler;
import com.lysandri.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=PruebasHttpTest.Configuracion.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"purchase.sandbox.enabled=true","spring.flyway.enabled=false",
        "jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A25432A","jwt.expiration=3600000"})
@ActiveProfiles({"test","sandbox"})
class PruebasHttpTest {
    static PostgreSQLContainer<?> contenedor;
    @Configuration
    @EnableAutoConfiguration(exclude={HibernateJpaAutoConfiguration.class,JpaRepositoriesAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({com.lysandri.application.service.CobrosPruebaService.class,
            com.lysandri.application.service.EmpresasPruebaService.class,
            com.lysandri.adapters.in.web.DemostracionController.class,ComprasPruebaService.class,ComprasPruebaController.class,ConversionPruebaController.class,ConversionPruebaService.class,SecurityConfig.class,JwtAuthenticationFilter.class,JwtService.class,GlobalExceptionHandler.class})
    static class Configuracion {
        @Bean DataSource dataSource() throws Exception {
            String url=System.getProperty("comercial.test.url");
            String usuario=System.getProperty("comercial.test.user","recuperacion_test");
            String password=System.getProperty("comercial.test.password","");
            if(url==null) {
                contenedor=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("recuperacion_test").withUsername(usuario).withPassword(UUID.randomUUID().toString());
                contenedor.start();url=contenedor.getJdbcUrl();password=contenedor.getPassword();
            }
            if(!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/recuperacion_test(?:\\?.*)?") || url.contains(":5432/"))
                throw new IllegalArgumentException("El servidor de prueba solo admite su base desechable fuera de 5432.");
            var ds=new DriverManagerDataSource(url,usuario,password);
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            jdbc.execute("CREATE TABLE usuario(id_user BIGINT PRIMARY KEY,email TEXT UNIQUE,rol TEXT,version_credenciales INTEGER DEFAULT 0, activo BOOLEAN DEFAULT true, bloqueado_admin BOOLEAN DEFAULT false, pendiente_activacion BOOLEAN DEFAULT false)");
            jdbc.execute("INSERT INTO usuario(id_user,email,rol) VALUES (1,'admin@prueba.local','ADMIN'),(2,'contador@prueba.local','CONTABILIDAD'),(3,'cliente@prueba.local','CLIENTE'),(4,'otro@prueba.local','CLIENTE')");
            try(var archivo=new ClassPathResource("db/migration/V51__compras-prueba.sql").getInputStream()) {jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));}
            try(var archivo=new ClassPathResource("db/migration/V52__cuotas-prueba.sql").getInputStream()) {jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));}
            try(var archivo=new ClassPathResource("db/migration/V57__conversion-prueba.sql").getInputStream()) {jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));}
            try(var archivo=new ClassPathResource("db/migration/V58__cierre-demostracion.sql").getInputStream()) {jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));}
            return ds;
        }
        @Bean UserDetailsService usuarios(DataSource ds) {
            return correo->new JdbcTemplate(ds).queryForObject("SELECT * FROM usuario WHERE email=?",(rs,n)->UsuarioEntity.builder()
                .idUser(rs.getLong("id_user")).email(rs.getString("email")).rol(RolUsuario.valueOf(rs.getString("rol")))
                .versionCredenciales(rs.getInt("version_credenciales")).activo(true).bloqueadoAdmin(false).pendienteActivacion(false).build(),correo);
        }
        @Bean AuthenticationProvider autenticacion(UserDetailsService usuarios) {
            var provider=new DaoAuthenticationProvider();provider.setUserDetailsService(usuarios);provider.setPasswordEncoder(new BCryptPasswordEncoder(4));return provider;
        }
    }
    @Autowired TestRestTemplate http;
    @Autowired JwtService jwt;
    @Autowired UserDetailsService usuarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.lysandri.application.service.CobrosPruebaService cobros;
    @AfterAll static void cerrar() {if(contenedor!=null) contenedor.stop();}
    @BeforeEach void limpiar() {jdbc.execute("TRUNCATE evento_empresa_prueba,participante_empresa_prueba,orden_empresa_prueba,conciliacion_pasarela_prueba,reporte_pasarela_prueba,transaccion_pasarela_prueba,plan_cobro_prueba,visita_conversion_prueba,evento_cuota_prueba,cuota_compra_prueba,evento_compra_prueba,compra_prueba,beneficio_prueba");}
    ResponseEntity<JsonNode> llamar(String rol,HttpMethod metodo,String ruta,Object cuerpo) {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        if(rol!=null) headers.setBearerAuth(jwt.generateToken(usuarios.loadUserByUsername(rol+"@prueba.local")));
        return http.exchange(((ruta.startsWith("/conversion/") || ruta.startsWith("/demostracion/")) ? "/api/v1/pruebas" : "/api/v1/pruebas/comerciales")+ruta,metodo,new HttpEntity<>(cuerpo,headers),JsonNode.class);
    }
    @Test void recorridoHttpConservaCotizacionYRegistraUnSoloResultado() {
        assertEquals(200,llamar("admin",HttpMethod.POST,"/beneficios",Map.of("codigo","PRUEBA10","porcentaje",10,"cupos",5,"vence",OffsetDateTime.now().plusDays(30).toString(),"activo",true)).getStatusCode().value());
        Map<String,Object> datos=new HashMap<>(Map.of("clave",UUID.randomUUID().toString(),"programas",List.of(1001),"codigo","PRUEBA10","cuotas",3,"medio","INTERBANK","acepta",true));
        var cotizada=llamar("admin",HttpMethod.POST,"/cotizar",datos);
        assertEquals(200,cotizada.getStatusCode().value());assertEquals(80.10,cotizada.getBody().get("total").asDouble(),0.001);
        assertEquals(3,cotizada.getBody().get("cuotas").size());
        assertTrue(cotizada.getBody().get("cuotas").get(0).get("vence").isTextual());
        datos.put("revision",cotizada.getBody().get("revision").asText());
        var creada=llamar("admin",HttpMethod.POST,"/compras",datos);
        assertEquals(200,creada.getStatusCode().value());
        assertEquals("PENDIENTE",creada.getBody().get("estado").asText());
        var repetida=llamar("admin",HttpMethod.POST,"/compras",datos);
        assertEquals(creada.getBody(),repetida.getBody());
        String id=creada.getBody().get("id").asText();
        var resultado=Map.of("evento",UUID.randomUUID().toString(),"resultado","APROBADA");
        var aprobada=llamar("admin",HttpMethod.POST,"/compras/"+id+"/resultado",resultado);
        assertEquals(200,aprobada.getStatusCode().value());assertEquals("APROBADA",aprobada.getBody().get("estado").asText());
        assertEquals(aprobada.getBody(),llamar("admin",HttpMethod.POST,"/compras/"+id+"/resultado",resultado).getBody());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compra_prueba",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM evento_compra_prueba",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT usados FROM beneficio_prueba",Integer.class));
    }
    @Test void servidorRealRechazaInvitadosYSeparaContabilidadDeAdministracion() {
        assertEquals(401,llamar(null,HttpMethod.GET,"/compras",null).getStatusCode().value());
        assertEquals(403,llamar("cliente",HttpMethod.GET,"/compras",null).getStatusCode().value());
        assertEquals(200,llamar("contador",HttpMethod.GET,"/compras",null).getStatusCode().value());
        assertEquals(403,llamar("contador",HttpMethod.POST,"/cotizar",Map.of("programas",List.of(1001),"cuotas",1,"medio","BCP")).getStatusCode().value());
    }
    @Test void clienteRegistraCompraDePruebaYConsultaSoloSusCuotas() {
        assertEquals(401, llamar(null, HttpMethod.GET, "/cliente", null).getStatusCode().value());
        assertEquals(403, llamar("contador", HttpMethod.GET, "/cliente", null).getStatusCode().value());
        assertEquals(200, llamar("cliente", HttpMethod.GET, "/cliente", null).getStatusCode().value());
        var beneficio = Map.of("codigo", "PRUEBA15", "porcentaje", 15, "cupos", 1,
                "vence", OffsetDateTime.now().plusDays(2).toString(), "activo", true);
        assertEquals(200, llamar("admin", HttpMethod.POST, "/beneficios", beneficio).getStatusCode().value());

        var solicitud = new HashMap<String, Object>(Map.of(
                "clave", UUID.randomUUID(), "programas", List.of(1002),
                "codigo", "PRUEBA15", "cuotas", 2, "medio", "BCP", "acepta", true));
        var cotizacion = llamar("cliente", HttpMethod.POST, "/cliente/cotizar", solicitud);
        assertEquals(200, cotizacion.getStatusCode().value());
        assertEquals(92.65, cotizacion.getBody().get("total").asDouble(), 0.001);
        solicitud.put("revision", cotizacion.getBody().get("revision").asText());

        var compra = llamar("cliente", HttpMethod.POST, "/cliente/compras", solicitud);
        assertEquals(200, compra.getStatusCode().value());
        assertEquals(3, compra.getBody().get("usuario").asInt());
        assertEquals(compra.getBody(), llamar("cliente", HttpMethod.POST, "/cliente/compras", solicitud).getBody());
        assertEquals(1, jdbc.queryForObject("SELECT usados FROM beneficio_prueba", Integer.class));
        var id = compra.getBody().get("id").asText();
        var ruta = "/cliente/compras/" + id;
        var pago = Map.of("evento", UUID.randomUUID(), "resultado", "PAGADA");
        assertEquals(403, llamar("cliente", HttpMethod.POST, "/compras/" + id + "/cuotas/1/resultado", pago).getStatusCode().value());
        assertEquals(200, llamar("admin", HttpMethod.POST, "/compras/" + id + "/cuotas/1/resultado", pago).getStatusCode().value());
        var seguimiento = llamar("cliente", HttpMethod.GET, ruta, null);
        assertEquals(200, seguimiento.getStatusCode().value());
        assertEquals("EN_CUOTAS", seguimiento.getBody().get("compra").get("estado").asText());
        assertEquals("PAGADA", seguimiento.getBody().get("cuotas").get(0).get("estado").asText());
        assertEquals(404, llamar("otro", HttpMethod.GET, ruta, null).getStatusCode().value());
    }

    @Test void conversionRespetaConsentimientoPropiedadYRetiro() {
        String ruta="/conversion";
        UUID visita=UUID.randomUUID();
        Map<String,Object> registro=Map.of("id",visita,"dispositivo","MOVIL","canal","CAMPANA","consentimiento","prueba-1");
        assertEquals(400,llamar(null,HttpMethod.POST,ruta+"/visita",Map.of("id",visita,"dispositivo","MOVIL","canal","CAMPANA")).getStatusCode().value());
        for(int i=0;i<2;i++) assertEquals(204,llamar(null,HttpMethod.POST,ruta+"/visita",registro).getStatusCode().value());
        assertEquals(204,llamar(null,HttpMethod.POST,ruta+"/inicio",Map.of("id",visita,"programa",1001)).getStatusCode().value());
        var compra=new HashMap<String,Object>(Map.of("clave",UUID.randomUUID(),"programas",List.of(1001),"codigo","","cuotas",1,"medio","BCP","acepta",true));
        compra.put("revision",llamar("cliente",HttpMethod.POST,"/cliente/cotizar",compra).getBody().get("revision").asText());
        String id=llamar("cliente",HttpMethod.POST,"/cliente/compras",compra).getBody().get("id").asText();
        var asociacion=Map.of("id",visita,"compra",id);
        String fecha=java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        String informe=ruta+"/reporte?desde="+fecha+"&hasta="+fecha;
        assertEquals(401,llamar(null,HttpMethod.GET,informe,null).getStatusCode().value());
        assertEquals(403,llamar("cliente",HttpMethod.GET,informe,null).getStatusCode().value());
        llamar("otro",HttpMethod.POST,ruta+"/compra",asociacion);
        assertEquals(0,llamar("contador",HttpMethod.GET,informe,null).getBody().get(0).get("registradas").asInt());
        for(int i=0;i<2;i++) assertEquals(204,llamar("cliente",HttpMethod.POST,ruta+"/compra",asociacion).getStatusCode().value());
        var fila=llamar("contador",HttpMethod.GET,informe,null).getBody().get(0);
        assertEquals(1,fila.get("visitas").asInt());assertEquals(1,fila.get("inicios").asInt());
        assertEquals(1,fila.get("registradas").asInt());assertEquals(0,fila.get("aprobadas").asInt());
        assertEquals(200,llamar("admin",HttpMethod.POST,"/compras/"+id+"/resultado",Map.of("evento",UUID.randomUUID(),"resultado","APROBADA")).getStatusCode().value());
        assertEquals(1,llamar("contador",HttpMethod.GET,informe,null).getBody().get(0).get("aprobadas").asInt());
        assertEquals(204,llamar(null,HttpMethod.DELETE,ruta+"/visita/"+visita,null).getStatusCode().value());
        assertEquals(0,llamar("contador",HttpMethod.GET,informe,null).getBody().size());
    }

    @Test void beneficiosCombinanReglasYRespetanElegibilidadYLimite() {
        var vence=OffsetDateTime.now().plusDays(5).toString();
        var beca=Map.of("codigo","BECA10","porcentaje",10,"tipo","BECA","programa",1001,
                "cliente",3,"limiteCliente",1,"acumulable",true,"cupos",4,"vence",vence,"activo",true);
        var convenio=Map.of("codigo","CONVENIO5","importeFijo",5,"tipo","CONVENIO",
                "acumulable",true,"cupos",4,"vence",vence,"activo",true);
        assertEquals(200,llamar("admin",HttpMethod.POST,"/beneficios",beca).getStatusCode().value());
        assertEquals(200,llamar("admin",HttpMethod.POST,"/beneficios",convenio).getStatusCode().value());
        var datos=new HashMap<String,Object>(Map.of("clave",UUID.randomUUID(),"programas",List.of(1001),
                "codigo","CONVENIO5, BECA10","cuotas",1,"medio","BCP","acepta",true));
        var cotizada=llamar("cliente",HttpMethod.POST,"/cliente/cotizar",datos);
        assertEquals(200,cotizada.getStatusCode().value(),String.valueOf(cotizada.getBody()));
        assertEquals(75.10,cotizada.getBody().get("total").asDouble(),0.001);
        assertEquals(400,llamar("otro",HttpMethod.POST,"/cliente/cotizar",datos).getStatusCode().value());
        datos.put("programas",List.of(1002));
        assertEquals(400,llamar("cliente",HttpMethod.POST,"/cliente/cotizar",datos).getStatusCode().value());
        datos.put("programas",List.of(1001));
        datos.put("revision",cotizada.getBody().get("revision").asText());
        assertEquals(200,llamar("cliente",HttpMethod.POST,"/cliente/compras",datos).getStatusCode().value());
        assertEquals(200,llamar("cliente",HttpMethod.POST,"/cliente/compras",datos).getStatusCode().value());
        assertEquals(400,llamar("cliente",HttpMethod.POST,"/cliente/cotizar",datos).getStatusCode().value());
        assertEquals(2,jdbc.queryForObject("SELECT sum(usados) FROM beneficio_prueba",Integer.class));
    }

    private String crearCompraEnCuotas() {
        var datos = new HashMap<String,Object>(Map.of("clave",UUID.randomUUID(),"programas",List.of(1001),
                "codigo","","cuotas",2,"medio","IZIPAY","acepta",true));
        var cotizada=llamar("cliente",HttpMethod.POST,"/cliente/cotizar",datos);
        assertEquals(200,cotizada.getStatusCode().value());
        datos.put("revision",cotizada.getBody().get("revision").asText());
        var compra=llamar("cliente",HttpMethod.POST,"/cliente/compras",datos);
        assertEquals(200,compra.getStatusCode().value());
        return compra.getBody().get("id").asText();
    }

    @Test void cobroProgramadoReintentaYConciliaSinDuplicar() throws Exception {
        String id=crearCompraEnCuotas();
        String ruta="/demostracion/cliente/cobros/"+id;
        var acepta=Map.of("acepta",true,"escenario","RECUPERAR");
        assertEquals(409,llamar("otro",HttpMethod.POST,ruta,acepta).getStatusCode().value());
        assertEquals(400,llamar("cliente",HttpMethod.POST,ruta,Map.of("acepta",false,"escenario","RECUPERAR")).getStatusCode().value());
        assertEquals(200,llamar("cliente",HttpMethod.POST,ruta,acepta).getStatusCode().value());
        cobros.procesar();
        cobros.procesar();
        var intentos=llamar("cliente",HttpMethod.GET,ruta,null).getBody().get("intentos");
        assertEquals(1,intentos.size());
        assertEquals("FALLIDA",intentos.get(0).get("estado").asText());
        assertEquals(0,llamar("otro",HttpMethod.GET,ruta,null).getBody().get("intentos").size());
        jdbc.update("UPDATE transaccion_pasarela_prueba SET fecha=now()-interval '2 minutes' WHERE compra=?::uuid",id);
        var hilos=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var uno=hilos.submit(cobros::procesar);var dos=hilos.submit(cobros::procesar);
            uno.get(10,java.util.concurrent.TimeUnit.SECONDS);dos.get(10,java.util.concurrent.TimeUnit.SECONDS);
        } finally {hilos.shutdownNow();}
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM transaccion_pasarela_prueba",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM transaccion_pasarela_prueba WHERE estado='PAGADA'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM conciliacion_pasarela_prueba WHERE estado='CONCILIADA'",Integer.class));
        var externo=Map.of("referencia",UUID.randomUUID(),"importe",12,"moneda","USD","estado","PAGADA");
        assertEquals(200,llamar("contador",HttpMethod.POST,"/demostracion/pasarela",externo).getStatusCode().value());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM conciliacion_pasarela_prueba WHERE estado='SIN_COMPRA'",Integer.class));
        assertEquals(200,llamar("cliente",HttpMethod.DELETE,ruta,null).getStatusCode().value());
        jdbc.update("UPDATE cuota_compra_prueba SET vence=current_date WHERE id_compra=?::uuid",id);
        cobros.procesar();
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM transaccion_pasarela_prueba",Integer.class));
    }

    @Test void cobroRechazadoDetieneReintentosEnElLimite() {
        String id=crearCompraEnCuotas();
        String ruta="/demostracion/cliente/cobros/"+id;
        assertEquals(200,llamar("cliente",HttpMethod.POST,ruta,Map.of("acepta",true,"escenario","RECHAZAR")).getStatusCode().value());
        for(int i=0;i<5;i++) {
            cobros.procesar();
            jdbc.update("UPDATE transaccion_pasarela_prueba SET fecha=now()-interval '2 minutes' WHERE compra=?::uuid",id);
        }
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM transaccion_pasarela_prueba",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM transaccion_pasarela_prueba WHERE motivo='INTENTOS_AGOTADOS'",Integer.class));
    }

    @Test void empresaRestringeHistorialYConservaCreditoYComprobante() {
        String ruta="/demostracion/empresas";
        Map<String,Object> datos=new HashMap<>(Map.of("gestor",3,"empresa","Empresa ficticia","oc","DEMO-001","programa",1001,
                "cohorte","Piloto","cupos",2,"descuento",18,"condicion","CREDITO","vence",java.time.LocalDate.now().plusDays(30).toString(),
                "participantes",List.of(Map.of("alias","Persona uno","cargo","Analista"))));
        assertEquals(403,llamar("contador",HttpMethod.POST,ruta,datos).getStatusCode().value());
        var creada=llamar("admin",HttpMethod.POST,ruta,datos);
        assertEquals(200,creada.getStatusCode().value(),String.valueOf(creada.getBody()));
        String id=creada.getBody().get("id").asText();
        assertEquals(409,llamar("admin",HttpMethod.POST,ruta,datos).getStatusCode().value());
        assertEquals(409,llamar("admin",HttpMethod.POST,ruta+"/"+id+"/estado",Map.of("estado","ENTREGADA")).getStatusCode().value());
        for (String estado:List.of("APROBADA","A_CREDITO","EN_TRANSITO","ENTREGADA"))
            assertEquals(200,llamar("admin",HttpMethod.POST,ruta+"/"+id+"/estado",Map.of("estado",estado)).getStatusCode().value());
        var orden=llamar("contador",HttpMethod.GET,ruta,null).getBody().get(0);
        assertEquals("FACTURADO A CREDITO",orden.get("estado_pago").asText());
        assertEquals(160,orden.get("total").asDouble(),0.001);
        assertTrue(orden.get("comprobante").asText().startsWith("DEMO-"));
        assertEquals(1,llamar("cliente",HttpMethod.GET,"/demostracion/cliente/empresas",null).getBody().size());
        assertEquals(0,llamar("otro",HttpMethod.GET,"/demostracion/cliente/empresas",null).getBody().size());
        assertEquals(0,llamar("otro",HttpMethod.GET,"/demostracion/cliente/empresas/"+id+"/participantes",null).getBody().size());
        assertEquals("MATRICULADA DE PRUEBA",llamar("cliente",HttpMethod.GET,"/demostracion/cliente/empresas/"+id+"/participantes",null)
                .getBody().get(0).get("matricula").asText());
        assertEquals(200,llamar("admin",HttpMethod.POST,ruta+"/"+id+"/estado",Map.of("estado","PAGADA")).getStatusCode().value());
        var pagada=llamar("contador",HttpMethod.GET,ruta,null).getBody().get(0);
        assertEquals("ENTREGADA",pagada.get("estado").asText());
        assertEquals("PAGADO",pagada.get("estado_pago").asText());
        assertTrue(leerHoja("cliente","/cliente/empresas/excel").contains("DEMO-001"));
        assertFalse(leerHoja("otro","/cliente/empresas/excel").contains("DEMO-001"));
        assertTrue(leerHoja("contador","/empresas/excel").contains("<v>160.00</v>"));
    }

    private String leerHoja(String rol,String ruta) {
        var cabeceras=new HttpHeaders();
        cabeceras.setBearerAuth(jwt.generateToken(usuarios.loadUserByUsername(rol+"@prueba.local")));
        var respuesta=http.exchange("/api/v1/pruebas/demostracion"+ruta,HttpMethod.GET,new HttpEntity<>(cabeceras),byte[].class);
        assertEquals(200,respuesta.getStatusCode().value());
        assertTrue(respuesta.getHeaders().getFirst("Content-Type").contains("spreadsheetml"));
        try (var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(respuesta.getBody()))) {
            java.util.zip.ZipEntry entrada;
            while((entrada=zip.getNextEntry())!=null) {
                if(entrada.getName().equals("xl/worksheets/sheet1.xml")) return new String(zip.readAllBytes(),StandardCharsets.UTF_8);
            }
            throw new AssertionError("El Excel no contiene la hoja de órdenes.");
        } catch (java.io.IOException e) {throw new AssertionError(e);}
    }

    @Test void mideConsultasDelPanelConCuatroClientesConcurrentes() throws Exception {
        jdbc.update("""
                INSERT INTO orden_empresa_prueba(id,gestor,operador,empresa,oc,programa,titulo,cohorte,cupos,moneda,bruto,
                    descuento,base,impuesto,condicion,estado)
                SELECT gen_random_uuid(),3,1,'Empresa ficticia','CARGA-'||n,1001,'Programa de muestra','Piloto',
                    1,'USD',89,0,75.42,13.58,'CONTADO','PENDIENTE' FROM generate_series(1,60) n
                """);
        jdbc.update("""
                INSERT INTO reporte_pasarela_prueba(referencia,importe,moneda,estado)
                SELECT gen_random_uuid(),89,'USD','PAGADA' FROM generate_series(1,200) n
                """);
        cobros.conciliar();
        String[] rutas={"/programas","/beneficios","/demostracion/pasarela","/demostracion/empresas"};
        for (String ruta:rutas) assertEquals(200,llamar("contador",HttpMethod.GET,ruta,null).getStatusCode().value());
        var hilos=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<Long>> medidas=new ArrayList<>();
            for(int i=0;i<40;i++) {
                String ruta=rutas[i%rutas.length];
                medidas.add(hilos.submit(()->{
                    long inicio=System.nanoTime();
                    assertEquals(200,llamar("contador",HttpMethod.GET,ruta,null).getStatusCode().value());
                    return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-inicio);
                }));
            }
            List<Long> tiempos=new ArrayList<>();
            for(var medida:medidas) tiempos.add(medida.get(10,java.util.concurrent.TimeUnit.SECONDS));
            tiempos.sort(Long::compare);
            System.out.printf("CONSULTAS DEMO: n=%d concurrencia=4 p95=%dms max=%dms%n",tiempos.size(),tiempos.get(37),tiempos.get(39));
            assertTrue(tiempos.get(39)<2000,"Las consultas seleccionadas deben terminar dentro de dos segundos.");
        } finally {hilos.shutdownNow();}
    }

    @Test void recorridoHttpDeCuotasMantieneLaCompraPendienteHastaElUltimoPago() {
        Map<String,Object> datos=new HashMap<>(Map.of("clave",UUID.randomUUID().toString(),"programas",List.of(1001),"codigo","","cuotas",2,"medio","BCP","acepta",true));
        var cotizada=llamar("admin",HttpMethod.POST,"/cotizar",datos);
        datos.put("revision",cotizada.getBody().get("revision").asText());
        var creada=llamar("admin",HttpMethod.POST,"/compras",datos);
        String id=creada.getBody().get("id").asText();
        String ruta="/compras/"+id+"/cuotas";
        assertEquals(2,llamar("contador",HttpMethod.GET,ruta,null).getBody().size());
        var pago=Map.of("evento",UUID.randomUUID().toString(),"resultado","PAGADA");
        assertEquals(403,llamar("contador",HttpMethod.POST,ruta+"/1/resultado",pago).getStatusCode().value());
        var parcial=llamar("admin",HttpMethod.POST,ruta+"/1/resultado",pago);
        assertEquals(200,parcial.getStatusCode().value());assertEquals("EN_CUOTAS",parcial.getBody().get("estado").asText());
        assertEquals(parcial.getBody(),llamar("admin",HttpMethod.POST,ruta+"/1/resultado",pago).getBody());
        var completa=llamar("admin",HttpMethod.POST,ruta+"/2/resultado",Map.of("evento",UUID.randomUUID().toString(),"resultado","PAGADA"));
        assertEquals("APROBADA",completa.getBody().get("estado").asText());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM evento_cuota_prueba",Integer.class));
    }
}

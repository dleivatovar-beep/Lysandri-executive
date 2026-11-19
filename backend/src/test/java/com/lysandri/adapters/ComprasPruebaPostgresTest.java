package com.lysandri.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.application.service.ComprasPruebaService;
import com.lysandri.application.service.ComprasPruebaService.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ComprasPruebaPostgresTest {
    static PostgreSQLContainer<?> contenedor;
    static JdbcTemplate jdbc;
    static ComprasPruebaService servicio;
    static DataSourceTransactionManager manager;
    @BeforeAll static void iniciar() throws Exception {
        String url=System.getProperty("comercial.test.url");
        String usuario=System.getProperty("comercial.test.user","recuperacion_test");
        String password=System.getProperty("comercial.test.password","");
        if(url==null) {
            contenedor=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("recuperacion_test").withUsername(usuario).withPassword(UUID.randomUUID().toString());
            contenedor.start();url=contenedor.getJdbcUrl();password=contenedor.getPassword();
        }
        if(!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/recuperacion_test(?:\\?.*)?") || url.contains(":5432/"))
            throw new IllegalArgumentException("Solo se admite la base desechable recuperacion_test fuera del puerto 5432.");
        var ds=new DriverManagerDataSource(url,usuario,password);
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        jdbc.execute("CREATE TABLE usuario(id_user BIGINT PRIMARY KEY)");
        jdbc.execute("INSERT INTO usuario VALUES (1),(2)");
        try(var archivo=new ClassPathResource("db/migration/V51__compras-prueba.sql").getInputStream()) {
            jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));
        }
        try(var archivo=new ClassPathResource("db/migration/V52__cuotas-prueba.sql").getInputStream()) {jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));}
        try(var archivo=new ClassPathResource("db/migration/V58__cierre-demostracion.sql").getInputStream()) {jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));}
        manager=new DataSourceTransactionManager(ds);
        servicio=new ComprasPruebaService(jdbc,manager,new ObjectMapper().findAndRegisterModules());
    }
    @AfterAll static void cerrar() {if(contenedor!=null) contenedor.stop();}
    @BeforeEach void limpiar() {
        jdbc.execute("TRUNCATE transaccion_pasarela_prueba,plan_cobro_prueba,evento_cuota_prueba,cuota_compra_prueba,evento_compra_prueba,compra_prueba,beneficio_prueba");
        servicio.guardarBeneficio(new Beneficio("PRUEBA10",new BigDecimal("10"),5,OffsetDateTime.now().plusDays(30),true));
    }
    Solicitud solicitud(String codigo,int cuotas) {return new Solicitud(UUID.randomUUID(),List.of(1001L),codigo,cuotas,"BCP",true,null);}
    Solicitud aceptada(Solicitud solicitud) {
        var precio=servicio.cotizar(solicitud);
        return new Solicitud(solicitud.clave(),solicitud.programas(),solicitud.codigo(),solicitud.cuotas(),solicitud.medio(),true,precio.revision());
    }
    @Test void reparteCentavosSinCambiarElTotalYConservaLaCotizacion() {
        var datos=aceptada(solicitud("PRUEBA10",3));
        var compra=servicio.registrar(1,datos);
        assertEquals(new BigDecimal("80.10"),compra.detalle().total());
        assertEquals(List.of(new BigDecimal("26.70"),new BigDecimal("26.70"),new BigDecimal("26.70")),compra.detalle().cuotas().stream().map(Cuota::importe).toList());
        assertEquals("PENDIENTE",compra.estado());
        var sinDescuento=servicio.cotizar(solicitud("",3));
        assertEquals(List.of(new BigDecimal("29.67"),new BigDecimal("29.67"),new BigDecimal("29.66")),sinDescuento.cuotas().stream().map(Cuota::importe).toList());
        servicio.guardarBeneficio(new Beneficio("PRUEBA10",new BigDecimal("50"),5,OffsetDateTime.now().plusDays(30),true));
        assertEquals(compra,servicio.registrar(1,datos));
        assertEquals(1,jdbc.queryForObject("SELECT usados FROM beneficio_prueba",Integer.class));
    }
    @Test void cambioComercialEntreRevisionYCompraRechazaYNoConsumeCupo() {
        var datos=aceptada(solicitud("PRUEBA10",2));
        servicio.guardarBeneficio(new Beneficio("PRUEBA10",new BigDecimal("20"),5,OffsetDateTime.now().plusDays(30),true));
        assertThrows(IllegalStateException.class,()->servicio.registrar(1,datos));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compra_prueba",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT usados FROM beneficio_prueba",Integer.class));
    }
    @Test void rechazaCodigosInvalidosVencidosDesactivadosYAgotados() {
        assertThrows(IllegalArgumentException.class,()->servicio.cotizar(solicitud("NO-EXISTE",1)));
        for(String cambio:List.of("vence=clock_timestamp()-interval '1 second'","activo=false","usados=cupos")) {
            jdbc.update("UPDATE beneficio_prueba SET "+cambio);
            assertThrows(IllegalArgumentException.class,()->servicio.cotizar(solicitud("PRUEBA10",1)));
            jdbc.update("UPDATE beneficio_prueba SET vence=clock_timestamp()+interval '1 day',activo=true,usados=0");
        }
    }
    @Test void exigeAceptacionYNoAdmitePreciosNiProgramasDelCliente() {
        var datos=aceptada(solicitud("",1));
        assertThrows(IllegalArgumentException.class,()->servicio.registrar(1,new Solicitud(datos.clave(),datos.programas(),"",1,"BCP",false,datos.revision())));
        assertThrows(IllegalArgumentException.class,()->servicio.cotizar(new Solicitud(UUID.randomUUID(),List.of(9999L),"",1,"BCP",true,null)));
        assertThrows(IllegalArgumentException.class,()->servicio.cotizar(solicitud("",4)));
    }
    @Test void idempotenciaConcurrenteSoloGuardaUnaCompraYUnUso() throws Exception {
        var datos=aceptada(solicitud("PRUEBA10",1));
        var hilos=Executors.newFixedThreadPool(2);
        try {
            var a=hilos.submit(()->servicio.registrar(1,datos));var b=hilos.submit(()->servicio.registrar(1,datos));
            assertEquals(a.get(10,TimeUnit.SECONDS).id(),b.get(10,TimeUnit.SECONDS).id());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compra_prueba",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT usados FROM beneficio_prueba",Integer.class));
        } finally {hilos.shutdownNow();}
    }
    @Test void ultimoCupoConcurrenteSoloPuedeConsumirseUnaVez() throws Exception {
        jdbc.update("UPDATE beneficio_prueba SET cupos=1");
        var a=aceptada(solicitud("PRUEBA10",1));var b=aceptada(solicitud("PRUEBA10",1));
        var hilos=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> resultados=new ArrayList<>();
            for(var datos:List.of(a,b)) resultados.add(hilos.submit(()->{try{servicio.registrar(1,datos);return true;}catch(IllegalArgumentException e){return false;}}));
            int exitos=0;for(var r:resultados) if(r.get(10,TimeUnit.SECONDS)) exitos++;
            assertEquals(1,exitos);
            assertEquals(1,jdbc.queryForObject("SELECT usados FROM beneficio_prueba",Integer.class));
        } finally {hilos.shutdownNow();}
    }
    @Test void mismaClaveNoAceptaOtraSolicitudYSeSeparaPorOperador() {
        var datos=aceptada(solicitud("",1));
        var original=servicio.registrar(1,datos);
        assertNotEquals(original.id(),servicio.registrar(2,datos).id());
        assertThrows(IllegalStateException.class,()->servicio.registrar(1,new Solicitud(datos.clave(),datos.programas(),"",2,"BCP",true,datos.revision())));
    }
    @Test void eventosDuplicadosNoReaplicanNiCambianElResultado() {
        var compra=servicio.registrar(1,aceptada(solicitud("",1)));UUID evento=UUID.randomUUID();
        var aprobada=servicio.resultado(1,compra.id(),evento,"APROBADA");
        assertEquals(aprobada,servicio.resultado(1,compra.id(),evento,"APROBADA"));
        assertThrows(IllegalStateException.class,()->servicio.resultado(1,compra.id(),evento,"RECHAZADA"));
        assertThrows(IllegalStateException.class,()->servicio.resultado(1,compra.id(),UUID.randomUUID(),"RECHAZADA"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM evento_compra_prueba",Integer.class));
        assertEquals("APROBADA",servicio.listar().get(0).estado());
        assertTrue(servicio.cuotas(compra.id()).stream().allMatch(c->c.estado().equals("PAGADA")));
    }

    @Test void cuotasFallidasSePuedenReintentarYLaCompraSoloSeCompletaAlPagarTodas() {
        var compra=servicio.registrar(1,aceptada(solicitud("",3)));UUID fallo=UUID.randomUUID();
        assertEquals("PENDIENTE",servicio.registrarCuota(1,compra.id(),1,fallo,"FALLIDA").estado());
        assertEquals("FALLIDA",servicio.cuotas(compra.id()).get(0).estado());
        UUID pago=UUID.randomUUID();
        assertEquals("EN_CUOTAS",servicio.registrarCuota(1,compra.id(),1,pago,"PAGADA").estado());
        assertEquals("EN_CUOTAS",servicio.registrarCuota(1,compra.id(),1,pago,"PAGADA").estado());
        assertThrows(IllegalStateException.class,()->servicio.registrarCuota(1,compra.id(),1,pago,"FALLIDA"));
        assertThrows(IllegalStateException.class,()->servicio.resultado(1,compra.id(),UUID.randomUUID(),"RECHAZADA"));
        servicio.registrarCuota(1,compra.id(),2,UUID.randomUUID(),"PAGADA");
        assertEquals("APROBADA",servicio.registrarCuota(1,compra.id(),3,UUID.randomUUID(),"PAGADA").estado());
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM evento_cuota_prueba",Integer.class));
    }

    @Test void pagosConcurrentesDeLaMismaCuotaNoSeDuplican() throws Exception {
        var compra=servicio.registrar(1,aceptada(solicitud("",3)));var hilos=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> resultados=new ArrayList<>();
            for(int i=0;i<2;i++) resultados.add(hilos.submit(()->{try{servicio.registrarCuota(1,compra.id(),1,UUID.randomUUID(),"PAGADA");return true;}catch(IllegalStateException e){return false;}}));
            int exitos=0;for(var r:resultados) if(r.get(10,TimeUnit.SECONDS)) exitos++;
            assertEquals(1,exitos);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM evento_cuota_prueba",Integer.class));
        } finally {hilos.shutdownNow();}
    }

    @Test void migracionConservaImportesYEstadosDeComprasAnteriores() {
        new org.springframework.transaction.support.TransactionTemplate(manager).execute(tx->{
            jdbc.execute("CREATE SCHEMA cuotas_migracion_test; SET LOCAL search_path=cuotas_migracion_test");
            jdbc.execute("CREATE TABLE usuario(id_user BIGINT PRIMARY KEY); INSERT INTO usuario VALUES (1)");
            try {
                try(var archivo=new ClassPathResource("db/migration/V51__compras-prueba.sql").getInputStream()) {jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));}
                for(String estado:List.of("PENDIENTE","APROBADA")) jdbc.update("INSERT INTO compra_prueba(id,id_usuario,clave,solicitud_hash,detalle,estado) VALUES (?,1,?,'anterior',?::jsonb,?)",UUID.randomUUID(),UUID.randomUUID(),"{\"cuotas\":[{\"numero\":1,\"vence\":\"2026-11-10\",\"importe\":29.67},{\"numero\":2,\"vence\":\"2026-12-10\",\"importe\":29.67},{\"numero\":3,\"vence\":\"2027-01-10\",\"importe\":29.66}]}",estado);
                try(var archivo=new ClassPathResource("db/migration/V52__cuotas-prueba.sql").getInputStream()) {jdbc.execute(new String(archivo.readAllBytes(),StandardCharsets.UTF_8));}
                assertEquals(6,jdbc.queryForObject("SELECT count(*) FROM cuota_compra_prueba",Integer.class));
                assertEquals(new BigDecimal("178.00"),jdbc.queryForObject("SELECT sum(importe) FROM cuota_compra_prueba",BigDecimal.class));
                assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM cuota_compra_prueba WHERE estado='PAGADA'",Integer.class));
            } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
            tx.setRollbackOnly();return null;
        });
    }
}

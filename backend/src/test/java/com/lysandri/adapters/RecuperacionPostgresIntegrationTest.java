package com.lysandri.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.adapters.out.database.adapter.RecuperacionAdapter;
import com.lysandri.adapters.out.database.adapter.CuentaAdapter;
import com.lysandri.adapters.out.notification.EmailNotificationAdapter;
import com.lysandri.application.service.RecuperacionService;
import com.lysandri.application.service.CuentaService;
import com.lysandri.security.ActivationCryptoService;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class RecuperacionPostgresIntegrationTest {
    static PostgreSQLContainer<?> contenedor;
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    RecuperacionAdapter repositorio;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    ActivationCryptoService cifrado = new ActivationCryptoService("clave-local-exclusiva-de-las-pruebas-de-recuperacion");

    @BeforeAll static void iniciar() throws Exception {
        String url = System.getProperty("recuperacion.test.url");
        String usuario = System.getProperty("recuperacion.test.user", "recuperacion_test");
        String password = System.getProperty("recuperacion.test.password", "");
        if (url == null) {
            contenedor = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("recuperacion_test").withUsername(usuario).withPassword(UUID.randomUUID().toString());
            contenedor.start(); url = contenedor.getJdbcUrl(); password = contenedor.getPassword();
        }
        if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/recuperacion_test(?:\\?.*)?") || url.contains(":5432/")) {
            throw new IllegalArgumentException("Estas pruebas solo admiten su base desechable recuperacion_test, fuera de 5432.");
        }
        dataSource = new DriverManagerDataSource(url, usuario, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        jdbc.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='lysandri_app') THEN CREATE ROLE lysandri_app; END IF; END $$");
        jdbc.execute("CREATE TABLE usuario(id_user BIGSERIAL PRIMARY KEY, nombres TEXT, email TEXT UNIQUE, passw TEXT, rol TEXT DEFAULT 'ADMIN', activo BOOLEAN DEFAULT true, bloqueado_admin BOOLEAN DEFAULT false, pendiente_activacion BOOLEAN DEFAULT false, fecha_actualizacion TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE usuario_activacion_token(id_token BIGSERIAL PRIMARY KEY,id_user BIGINT,usado BOOLEAN DEFAULT false,revocado BOOLEAN DEFAULT false,fecha_revocacion TIMESTAMPTZ)");
        try (var archivo = new ClassPathResource("db/migration/V48__add_password_recovery.sql").getInputStream()) {
            jdbc.execute(new String(archivo.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (var archivo = new ClassPathResource("db/migration/V50__avisos-cuenta.sql").getInputStream()) {
            jdbc.execute(new String(archivo.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    @AfterAll static void cerrar() { if (contenedor != null) contenedor.stop(); }
    @BeforeEach void preparar() {
        jdbc.execute("TRUNCATE recuperacion_cuenta,usuario_activacion_token,usuario RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO usuario(nombres,email,passw) VALUES (?,?,?)", "Ana", "ana@prueba.local", encoder.encode("AnteriorPrueba2026"));
        repositorio = new RecuperacionAdapter(jdbc, new DataSourceTransactionManager(dataSource));
    }

    String solicitud(String token) {
        assertTrue(repositorio.guardarSolicitud("ana@prueba.local", RecuperacionService.hash(token), cifrado.encrypt("http://127.0.0.1:3000/intranet#recuperar="+token)));
        return RecuperacionService.hash(token);
    }

    @Test void otorgaPermisosMinimosAlUsuarioDeLaAplicacion() {
        assertTrue(jdbc.queryForObject("SELECT has_table_privilege('lysandri_app','recuperacion_cuenta','SELECT') AND has_table_privilege('lysandri_app','recuperacion_cuenta','INSERT') AND has_table_privilege('lysandri_app','recuperacion_cuenta','UPDATE')",Boolean.class));
        assertFalse(jdbc.queryForObject("SELECT has_table_privilege('lysandri_app','recuperacion_cuenta','DELETE')",Boolean.class));
    }

    @Test void cambiaPasswordUnaVezYConservaElRol() {
        String hash = solicitud("a".repeat(43));
        jdbc.update("INSERT INTO usuario_activacion_token(id_user) VALUES (1)");
        assertTrue(repositorio.confirmar(hash, encoder.encode("NuevaPrueba2026")));
        assertFalse(repositorio.confirmar(hash, encoder.encode("OtraPrueba2026")));
        assertTrue(encoder.matches("NuevaPrueba2026", jdbc.queryForObject("SELECT passw FROM usuario WHERE id_user=1",String.class)));
        assertEquals("ADMIN",jdbc.queryForObject("SELECT rol FROM usuario WHERE id_user=1",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT version_credenciales FROM usuario WHERE id_user=1",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT revocado FROM usuario_activacion_token",Boolean.class));
        assertEquals("CUENTA_RECUPERADA",jdbc.queryForObject("SELECT tipo FROM aviso_cuenta",String.class));
    }

    @Test void cambioAutenticadoRevocaEnlacesYGuardaAvisoEnLaMismaTransaccion() {
        solicitud("a".repeat(43));
        jdbc.update("INSERT INTO usuario_activacion_token(id_user) VALUES (1)");
        var cuentas = new CuentaAdapter(jdbc,new DataSourceTransactionManager(dataSource));
        String anterior=jdbc.queryForObject("SELECT passw FROM usuario WHERE id_user=1",String.class);
        assertTrue(cuentas.cambiarPassword(1,anterior,encoder.encode("NuevaPrueba2026")));
        assertFalse(cuentas.cambiarPassword(1,anterior,encoder.encode("OtraPrueba2026")));
        assertEquals(1,jdbc.queryForObject("SELECT version_credenciales FROM usuario",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT fecha_revocacion IS NOT NULL AND enlace_cifrado IS NULL FROM recuperacion_cuenta",Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT revocado FROM usuario_activacion_token",Boolean.class));
        assertEquals("PASSWORD_CAMBIADA",jdbc.queryForObject("SELECT tipo FROM aviso_cuenta",String.class));
    }

    @Test void falloAlGuardarAvisoRevierteElCambioDePassword() {
        var cuentas = new CuentaAdapter(jdbc,new DataSourceTransactionManager(dataSource));
        String anterior=jdbc.queryForObject("SELECT passw FROM usuario WHERE id_user=1",String.class);
        jdbc.execute("ALTER TABLE aviso_cuenta ADD CONSTRAINT rechazo_prueba CHECK(tipo <> 'PASSWORD_CAMBIADA')");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,()->cuentas.cambiarPassword(1,anterior,encoder.encode("NuevaPrueba2026")));
            assertEquals(anterior,jdbc.queryForObject("SELECT passw FROM usuario WHERE id_user=1",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT version_credenciales FROM usuario",Integer.class));
        } finally {jdbc.execute("ALTER TABLE aviso_cuenta DROP CONSTRAINT rechazo_prueba");}
    }

    @Test void trabajadoresReiniciadosNoRepitenAvisosReservados() throws Exception {
        jdbc.update("INSERT INTO aviso_cuenta(id_user,tipo) VALUES (1,'PASSWORD_CAMBIADA')");
        var cuentas = new CuentaAdapter(jdbc,new DataSourceTransactionManager(dataSource));
        ExecutorService hilos=Executors.newFixedThreadPool(2);
        try {
            var a=hilos.submit(()->cuentas.tomarAvisos().size());
            var b=hilos.submit(()->cuentas.tomarAvisos().size());
            assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
            var reiniciado=new CuentaAdapter(jdbc,new DataSourceTransactionManager(dataSource));
            assertTrue(reiniciado.tomarAvisos().isEmpty());
            reiniciado.confirmarAviso(1,false);
            assertEquals("REVISAR",jdbc.queryForObject("SELECT estado FROM aviso_cuenta",String.class));
            assertTrue(reiniciado.tomarAvisos().isEmpty());
        } finally {hilos.shutdownNow();}
    }

    @Test void rechazaVencidosYHashesPresentadosComoToken() {
        String token = "b".repeat(43), hash = solicitud(token);
        assertFalse(repositorio.confirmar(RecuperacionService.hash(hash), encoder.encode("NuevaPrueba2026")));
        jdbc.update("UPDATE recuperacion_cuenta SET fecha_expiracion=clock_timestamp()-INTERVAL '1 second'");
        assertFalse(repositorio.confirmar(hash, encoder.encode("NuevaPrueba2026")));
        assertEquals(0,jdbc.queryForObject("SELECT version_credenciales FROM usuario WHERE id_user=1",Integer.class));
    }

    @Test void solicitudesNuevasAnulanElEnlaceAnterior() {
        String viejo=solicitud("a".repeat(43));
        jdbc.update("UPDATE recuperacion_cuenta SET fecha_solicitud=clock_timestamp()-INTERVAL '61 seconds'");
        String nuevo=solicitud("b".repeat(43));
        assertFalse(repositorio.confirmar(viejo, encoder.encode("NuevaPrueba2026")));
        assertTrue(repositorio.confirmar(nuevo, encoder.encode("NuevaPrueba2026")));
    }

    @Test void cooldownNoAnulaElPrimerEnlace() {
        String primero=solicitud("a".repeat(43));
        assertFalse(repositorio.guardarSolicitud("ana@prueba.local",RecuperacionService.hash("b".repeat(43)),"cifrado"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recuperacion_cuenta",Integer.class));
        assertTrue(repositorio.confirmar(primero,encoder.encode("NuevaPrueba2026")));
    }

    @Test void cuentaBloqueadaNoSeReactivaConLaRecuperacion() {
        String hash=solicitud("a".repeat(43));
        jdbc.update("UPDATE usuario SET bloqueado_admin=true WHERE id_user=1");
        assertFalse(repositorio.confirmar(hash,encoder.encode("NuevaPrueba2026")));
        assertTrue(jdbc.queryForObject("SELECT bloqueado_admin FROM usuario WHERE id_user=1",Boolean.class));
        assertFalse(repositorio.guardarSolicitud("ana@prueba.local",RecuperacionService.hash("b".repeat(43)),"cifrado"));
    }

    @Test void dosConsumosConcurrentesSoloCambianUnaVez() throws Exception {
        String hash=solicitud("a".repeat(43));
        ExecutorService hilos=Executors.newFixedThreadPool(2);
        CountDownLatch inicio=new CountDownLatch(1);
        try {
            List<Future<Boolean>> cambios=new ArrayList<>();
            for(int i=0;i<2;i++) cambios.add(hilos.submit(() -> { inicio.await(); return repositorio.confirmar(hash,encoder.encode("NuevaPrueba2026")); }));
            inicio.countDown();
            int exitos=0;for(var cambio:cambios) if(cambio.get(10,TimeUnit.SECONDS)) exitos++;
            assertEquals(1,exitos);
            assertEquals(1,jdbc.queryForObject("SELECT version_credenciales FROM usuario WHERE id_user=1",Integer.class));
        } finally { hilos.shutdownNow(); }
    }

    @Test void dosTrabajadoresNoReservanElMismoCorreo() throws Exception {
        solicitud("a".repeat(43));
        ExecutorService hilos=Executors.newFixedThreadPool(2);
        try {
            var a=hilos.submit(() -> repositorio.tomarPendientes().size());
            var b=hilos.submit(() -> repositorio.tomarPendientes().size());
            assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
            assertTrue(repositorio.tomarPendientes().isEmpty());
            repositorio.registrarEnvio(1,false);
            assertEquals("REVISAR",jdbc.queryForObject("SELECT estado_correo FROM recuperacion_cuenta",String.class));
            assertTrue(repositorio.tomarPendientes().isEmpty());
        } finally { hilos.shutdownNow(); }
    }

    @Test void fallaDeActualizacionRevierteElConsumoDelToken() {
        String hash=solicitud("a".repeat(43));
        jdbc.update("UPDATE usuario SET version_credenciales=2147483647 WHERE id_user=1");
        assertThrows(org.springframework.dao.DataAccessException.class,() -> repositorio.confirmar(hash,encoder.encode("NuevaPrueba2026")));
        assertTrue(jdbc.queryForObject("SELECT fecha_uso IS NULL FROM recuperacion_cuenta",Boolean.class));
        assertTrue(encoder.matches("AnteriorPrueba2026",jdbc.queryForObject("SELECT passw FROM usuario WHERE id_user=1",String.class)));
    }

    @Test void enviaPorSmtpElEnlaceQueLuegoCambiaLaPassword() throws Exception {
        try(Buzon buzon=new Buzon()) {
            var mail=new JavaMailSenderImpl();mail.setHost("127.0.0.1");mail.setPort(buzon.servidor.getLocalPort());
            mail.getJavaMailProperties().put("mail.smtp.timeout","3000");
            mail.getJavaMailProperties().put("mail.smtp.connectiontimeout","3000");
            var correo=new EmailNotificationAdapter(mail,null);
            var servicio=new RecuperacionService(repositorio,encoder,cifrado,correo,new ObjectMapper());
            servicio.solicitar("ana@prueba.local","http://127.0.0.1:3000","prueba");
            servicio.enviarPendientes();
            byte[] recibido=buzon.mensajes.poll(5,TimeUnit.SECONDS);
            assertNotNull(recibido);
            var mensaje=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(recibido));
            assertEquals("Recupera tu acceso a Lysandri Executive",mensaje.getSubject());
            var encontrado=Pattern.compile("#recuperar=([A-Za-z0-9_-]{43})").matcher(texto(mensaje));
            assertTrue(encontrado.find());
            String token=encontrado.group(1);
            assertEquals(RecuperacionService.hash(token),jdbc.queryForObject("SELECT token_hash FROM recuperacion_cuenta",String.class));
            assertEquals("ENVIADO",jdbc.queryForObject("SELECT estado_correo FROM recuperacion_cuenta",String.class));
            assertTrue(jdbc.queryForObject("SELECT enlace_cifrado IS NULL FROM recuperacion_cuenta",Boolean.class));
            servicio.confirmar(token,"NuevaPrueba2026","prueba");
            assertTrue(encoder.matches("NuevaPrueba2026",jdbc.queryForObject("SELECT passw FROM usuario WHERE id_user=1",String.class)));
        }
    }

    @Test void avisoDeCambioLlegaAlBuzonSinIncluirLaPassword() throws Exception {
        try(Buzon buzon=new Buzon()) {
            var mail=new JavaMailSenderImpl();mail.setHost("127.0.0.1");mail.setPort(buzon.servidor.getLocalPort());
            mail.getJavaMailProperties().put("mail.smtp.timeout","3000");
            mail.getJavaMailProperties().put("mail.smtp.connectiontimeout","3000");
            var correo=new EmailNotificationAdapter(mail,null);
            var cuentas=new CuentaAdapter(jdbc,new DataSourceTransactionManager(dataSource));
            String anterior=jdbc.queryForObject("SELECT passw FROM usuario WHERE id_user=1",String.class);
            assertTrue(cuentas.cambiarPassword(1,anterior,encoder.encode("NuevaPrueba2026")));
            new CuentaService(null,cuentas,encoder,correo).enviarAvisos();
            byte[] recibido=buzon.mensajes.poll(5,TimeUnit.SECONDS);
            assertNotNull(recibido);
            var mensaje=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(recibido));
            assertEquals("Cambio de seguridad en tu cuenta de Lysandri Executive",mensaje.getSubject());
            assertFalse(texto(mensaje).contains("NuevaPrueba2026"));
            assertTrue(texto(mensaje).contains("Las sesiones anteriores"));
            assertEquals("ENVIADO",jdbc.queryForObject("SELECT estado FROM aviso_cuenta",String.class));
        }
    }

    static String texto(Part parte) throws Exception {
        if(parte.isMimeType("text/plain")) return (String)parte.getContent();
        if(parte.getContent() instanceof Multipart partes) {
            StringBuilder texto=new StringBuilder();for(int i=0;i<partes.getCount();i++) texto.append(texto(partes.getBodyPart(i)));return texto.toString();
        }
        return "";
    }

    static class Buzon implements AutoCloseable {
        final ServerSocket servidor=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
        final BlockingQueue<byte[]> mensajes=new LinkedBlockingQueue<>();
        final ExecutorService hilo=Executors.newSingleThreadExecutor();
        Buzon() throws IOException { hilo.submit(() -> {
            try(var socket=servidor.accept();var entrada=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));var salida=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8)) {
                socket.setSoTimeout(5000);salida.print("220 localhost SMTP\r\n");salida.flush();String linea;
                while((linea=entrada.readLine())!=null) {
                    if(linea.startsWith("DATA")) {
                        salida.print("354 End with a dot\r\n");salida.flush();StringBuilder contenido=new StringBuilder();
                        while((linea=entrada.readLine())!=null && !linea.equals(".")) contenido.append(linea).append("\r\n");
                        mensajes.add(contenido.toString().getBytes(StandardCharsets.UTF_8));salida.print("250 OK\r\n");
                    } else if(linea.startsWith("QUIT")) { salida.print("221 Bye\r\n");salida.flush();break; }
                    else salida.print("250 OK\r\n");salida.flush();
                }
            } catch(IOException ignored) { }
        }); }
        public void close() throws IOException { servidor.close();hilo.shutdownNow(); }
    }
}

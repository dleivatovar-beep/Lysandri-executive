package com.lysandri.adapters;

import com.lysandri.adapters.in.web.AuthController;
import com.lysandri.adapters.out.database.adapter.ConsentimientoAdapter;
import com.lysandri.adapters.out.database.adapter.UserActivationRepositoryAdapter;
import com.lysandri.adapters.out.database.adapter.UserRepositoryAdapter;
import com.lysandri.adapters.out.database.repository.SpringDataUserRepository;
import com.lysandri.adapters.out.notification.EmailNotificationAdapter;
import com.lysandri.application.service.ActivationOutboxService;
import com.lysandri.application.service.AuthService;
import com.lysandri.exception.GlobalExceptionHandler;
import com.lysandri.security.ActivationCryptoService;
import com.lysandri.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(RegistroPublicoPostgresTest.Config.class)
@ActiveProfiles({"test", "sandbox"})
@TestPropertySource(properties = {
        "app.public-registration.require-email-verification=true",
        "spring.mail.host=127.0.0.1",
        "app.frontend.url=http://127.0.0.1:3000",
        "app.security.activation-encryption-key=clave-ficticia-exclusiva-de-prueba-registro",
        "jwt.secret=Y2xhdmUtZmljdGljaWEtZXhjbHVzaXZhLWRlLXBydWViYS1qd3Q=",
        "jwt.expiration=60000"
})
@EnabledIfSystemProperty(named = "registro.test.url", matches = ".+")
class RegistroPublicoPostgresTest {
    private static final String EMAIL = "registro@lysandri.invalid";
    private static final String INICIAL = "Registro2026!";
    private static final String CONFIRMADA = "Activada2026!";

    @Autowired AuthController controller;
    @Autowired com.lysandri.domain.ports.in.AuthUseCase authService;
    @Autowired JdbcTemplate jdbc;
    @Autowired ActivationOutboxService outbox;
    @Autowired GuestActivationHttpMockSmtpIntegrationTest.SimpleMockSmtpServer smtp;
    private final ObjectMapper json = new ObjectMapper();

    @Test void registraConsentimientoEnviaCorreoYActivaUnaVez() throws Exception {
        try (var migration = new ClassPathResource("db/migration/V53__consentimiento-registro.sql").getInputStream()) {
            jdbc.execute(new String(migration.readAllBytes(), StandardCharsets.UTF_8));
        }
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String version = json.readTree(mvc.perform(get("/api/v1/auth/condiciones-registro"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("version").asText();
        String solicitud = """
                {"nombres":"<b>Prueba</b>","apellidos":"Registro","email":"Registro@lysandri.invalid",
                 "password":"%s","aceptaTerminos":true,"aceptaPrivacidad":true,"version":"%s"}
                """.formatted(INICIAL, version);
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content(solicitud.replace("\"aceptaPrivacidad\":true", "\"aceptaPrivacidad\":false")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content(solicitud.replace(version, "version-vencida")))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM usuario", Integer.class));

        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(solicitud))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.pendienteActivacion").value(true))
                .andExpect(jsonPath("$.token").isEmpty());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM consentimiento_usuario c JOIN usuario u USING (id_user)
                WHERE u.email = ? AND NOT u.activo AND u.pendiente_activacion
                AND c.terminos AND c.privacidad AND c.version = ? AND c.fecha IS NOT NULL
                """, Integer.class, EMAIL, version));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM outbox_notificacion_activacion
                WHERE estado = 'PENDIENTE' AND id_orden IS NULL AND enlace_cifrado LIKE 'v1:%'
                """, Integer.class));

        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(solicitud))
                .andExpect(status().isBadRequest());
        ingresar(mvc, INICIAL).andExpect(status().isUnauthorized());

        outbox.procesarTareasPendientes();
        String correo = smtp.waitForActivationEmail(EMAIL, 5, TimeUnit.SECONDS);
        assertNotNull(correo);
        String cuerpo = correo.replace("=3D", "=").replace("=\r\n", "").replace("=\n", "");
        assertTrue(cuerpo.contains("&lt;b&gt;Prueba&lt;/b&gt;"));
        var enlace = Pattern.compile("http://127\\.0\\.0\\.1:3000/activar-cuenta\\?token=([A-Za-z0-9_-]+)")
                .matcher(cuerpo);
        assertTrue(enlace.find());
        String token = enlace.group(1);
        assertEquals(UserActivationRepositoryAdapter.hashToken(token),
                jdbc.queryForObject("SELECT token FROM usuario_activacion_token", String.class));

        activar(mvc, token, "Corta202").andExpect(status().isBadRequest());
        activar(mvc, token, CONFIRMADA).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        activar(mvc, token, CONFIRMADA).andExpect(status().isConflict());
        ingresar(mvc, INICIAL).andExpect(status().isUnauthorized());
        ingresar(mvc, CONFIRMADA).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM usuario u JOIN usuario_activacion_token t USING (id_user)
                WHERE u.activo AND NOT u.pendiente_activacion AND t.usado
                """, Integer.class));

        // Cuentas creadas antes del cambio: solo se habilita el registro propio con contraseña válida.
        String previo = solicitud.replace("Registro@lysandri.invalid", "previo@lysandri.invalid");
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(previo))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.pendienteActivacion").value(true));
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "requireEmailVerification", false);
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", "previo@lysandri.invalid", "password", "incorrecta"))))
                .andExpect(status().isUnauthorized());
        assertTrue(jdbc.queryForObject("SELECT pendiente_activacion FROM usuario WHERE email = 'previo@lysandri.invalid'", Boolean.class));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", "previo@lysandri.invalid", "password", INICIAL))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        assertFalse(jdbc.queryForObject("SELECT pendiente_activacion FROM usuario WHERE email = 'previo@lysandri.invalid'", Boolean.class));

        String directo = solicitud.replace("Registro@lysandri.invalid", "directo@lysandri.invalid");
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(directo))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.pendienteActivacion").value(false)).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(directo))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", "directo@lysandri.invalid", "password", INICIAL))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM consentimiento_usuario", Integer.class));

        org.springframework.test.util.ReflectionTestUtils.setField(authService, "requireEmailVerification", true);
        String invitado = solicitud.replace("Registro@lysandri.invalid", "sin-consentimiento@lysandri.invalid");
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(invitado))
                .andExpect(status().isCreated());
        jdbc.update("DELETE FROM consentimiento_usuario WHERE id_user = (SELECT id_user FROM usuario WHERE email = 'sin-consentimiento@lysandri.invalid')");
        String bloqueado = solicitud.replace("Registro@lysandri.invalid", "bloqueado@lysandri.invalid");
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(bloqueado))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE usuario SET bloqueado_admin = true WHERE email = 'bloqueado@lysandri.invalid'");
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "requireEmailVerification", false);
        for (String email : new String[]{"sin-consentimiento@lysandri.invalid", "bloqueado@lysandri.invalid"}) {
            mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                            .content(json.writeValueAsString(Map.of("email", email, "password", INICIAL))))
                    .andExpect(status().isUnauthorized());
            assertFalse(jdbc.queryForObject("SELECT activo FROM usuario WHERE email = ?", Boolean.class, email));
        }
    }

    private org.springframework.test.web.servlet.ResultActions ingresar(MockMvc mvc, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", EMAIL, "password", password))));
    }

    private org.springframework.test.web.servlet.ResultActions activar(MockMvc mvc, String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/activar-cuenta").contentType("application/json")
                .content(json.writeValueAsString(Map.of("token", token, "password", password))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = SpringDataUserRepository.class)
    @Import({AuthController.class, AuthService.class, UserRepositoryAdapter.class, ConsentimientoAdapter.class,
            UserActivationRepositoryAdapter.class, JwtService.class, ActivationCryptoService.class,
            ActivationOutboxService.class, EmailNotificationAdapter.class})
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getProperty("registro.test.url");
            if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[1-9][0-9]*/recuperacion_test")
                    || url.contains(":5432/")) {
                throw new IllegalArgumentException("Solo se admite PostgreSQL desechable fuera del puerto 5432.");
            }
            var source = new DriverManagerDataSource(url, "recuperacion_test", "");
            String schema = "registro_" + UUID.randomUUID().toString().replace("-", "");
            new JdbcTemplate(source).execute("CREATE SCHEMA " + schema);
            source.setSchema(schema);
            return source;
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.lysandri.adapters.out.database.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean BCryptPasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean UserDetailsService userDetailsService(SpringDataUserRepository repository) {
            return email -> repository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        }

        @Bean AuthenticationManager authenticationManager(UserDetailsService users, BCryptPasswordEncoder encoder) {
            var provider = new DaoAuthenticationProvider();
            provider.setUserDetailsService(users);
            provider.setPasswordEncoder(encoder);
            return new ProviderManager(provider);
        }

        @Bean(destroyMethod = "close")
        GuestActivationHttpMockSmtpIntegrationTest.SimpleMockSmtpServer smtp() throws Exception {
            return new GuestActivationHttpMockSmtpIntegrationTest.SimpleMockSmtpServer(0);
        }

        @Bean JavaMailSenderImpl mailSender(GuestActivationHttpMockSmtpIntegrationTest.SimpleMockSmtpServer smtp) {
            var sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");
            sender.setPort(smtp.getPort());
            return sender;
        }
    }
}


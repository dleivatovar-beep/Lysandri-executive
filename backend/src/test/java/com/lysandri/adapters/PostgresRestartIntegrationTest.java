package com.lysandri.adapters;

import com.lysandri.LysandriApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/** Stops and starts the actual Spring backend while retaining only its disposable database. */
class PostgresRestartIntegrationTest extends PostgresTestDatabase {
    @Test
    void userAndAuditSurviveBackendShutdownAndRestart() {
        String email = "restart-" + java.util.UUID.randomUUID() + "@integration.invalid";
        Long userId;
        Long auditId;
        ConfigurableApplicationContext first = startBackend();
        try {
            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            userId = jdbc.queryForObject("""
                    INSERT INTO usuario (nombres, apellidos, email, passw, rol, activo)
                    VALUES ('Restart', 'Verification', ?, 'not-a-login-password', 'TRABAJADOR', false)
                    RETURNING id_user
                    """, Long.class, email);
            auditId = jdbc.queryForObject("""
                    INSERT INTO usuario_cambio_audit
                        (id_usuario, email, accion, rol_anterior, rol_nuevo, motivo, ejecutado_por)
                    VALUES (?, ?, 'DESACTIVADO', 'TRABAJADOR', 'TRABAJADOR', 'restart-test', 'integration-admin')
                    RETURNING id_auditoria
                    """, Long.class, userId, email);
        } finally {
            first.close();
        }
        assertFalse(first.isActive());
        try (ConfigurableApplicationContext restarted = startBackend()) {
            assertNotSame(first, restarted);
            JdbcTemplate jdbc = restarted.getBean(JdbcTemplate.class);
            assertEquals(email, jdbc.queryForObject("SELECT email FROM usuario WHERE id_user=?", String.class, userId));
            assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT activo FROM usuario WHERE id_user=?", Boolean.class, userId));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM usuario_cambio_audit WHERE id_auditoria=? AND id_usuario=?",
                    Integer.class, auditId, userId));
        }
    }

    private ConfigurableApplicationContext startBackend() {
        return new SpringApplicationBuilder(LysandriApplication.class).profiles("test").run(
                "--server.port=0",
                "--spring.datasource.url=" + database().getJdbcUrl(),
                "--spring.datasource.username=" + database().getUsername(),
                "--spring.datasource.password=" + database().getPassword(),
                "--spring.flyway.url=" + database().getJdbcUrl(),
                "--spring.flyway.user=" + database().getUsername(),
                "--spring.flyway.password=" + database().getPassword(),
                "--spring.flyway.validate-on-migrate=true",
                "--spring.jpa.hibernate.ddl-auto=validate");
    }
}

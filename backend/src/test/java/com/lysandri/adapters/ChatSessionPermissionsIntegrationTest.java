package com.lysandri.adapters;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ChatSessionPermissionsIntegrationTest extends PostgresTestDatabase {
    @Autowired DataSource dataSource;

    @Test void migracionPermiteCrearYActualizarSinDarPermisoParaBorrar() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                sql.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lysandri_app') THEN CREATE ROLE lysandri_app NOLOGIN; END IF; END $$");
                sql.execute("GRANT USAGE ON SCHEMA public TO lysandri_app");
                sql.execute("GRANT SELECT ON public.chat_session TO lysandri_app");
                sql.execute("REVOKE INSERT, UPDATE, DELETE ON public.chat_session FROM lysandri_app");
                String id = UUID.randomUUID().toString();
                String insert = "INSERT INTO public.chat_session (id_sesion, session_token_hash) VALUES ('" + id + "', 'hash-de-prueba')";
                var antes = connection.setSavepoint();
                sql.execute("SET LOCAL ROLE lysandri_app");
                SQLException error = assertThrows(SQLException.class, () -> sql.execute(insert));
                assertEquals("42501", error.getSQLState());
                connection.rollback(antes);
                sql.execute(new ClassPathResource("db/migration/V47__grant_chat_session_access.sql")
                        .getContentAsString(StandardCharsets.UTF_8));
                sql.execute("SET LOCAL ROLE lysandri_app");
                assertEquals(1, sql.executeUpdate(insert));
                assertEquals(1, sql.executeUpdate("UPDATE public.chat_session SET ultima_actividad = CURRENT_TIMESTAMP WHERE id_sesion = '" + id + "'"));
                try (var resultado = sql.executeQuery("SELECT has_table_privilege(current_user, 'public.chat_session', 'DELETE')")) {
                    assertTrue(resultado.next());
                    assertFalse(resultado.getBoolean(1));
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }
}

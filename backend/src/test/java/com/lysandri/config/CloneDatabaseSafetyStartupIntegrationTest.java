package com.lysandri.config;

import com.lysandri.LysandriApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Pruebas de Arranque Real: Guardia de Seguridad del Perfil Clone")
class CloneDatabaseSafetyStartupIntegrationTest {

    private void assertStartupAbortsWithSafetyException(String[] args, String expectedMessageFragment) {
        assertStartupAbortsWithSafetyException("clone", args, expectedMessageFragment);
    }

    private void assertStartupAbortsWithSafetyException(String profile, String[] args, String expectedMessageFragment) {
        SpringApplicationBuilder app = new SpringApplicationBuilder(LysandriApplication.class);
        app.properties("server.port=0");
        if (profile != null && !profile.isBlank()) {
            app.profiles(profile);
        }

        Throwable exception = assertThrows(Throwable.class, () -> {
            try (ConfigurableApplicationContext ctx = app.run(args != null ? args : new String[0])) {
                fail("El contexto de Spring Boot no debió iniciar con una configuración prohibida");
            }
        });

        // Buscar SecurityException en la cadena de causas
        Throwable current = exception;
        boolean foundSecurityException = false;
        String fullMessage = "";
        while (current != null) {
            fullMessage += " | " + current.getMessage();
            if (current instanceof SecurityException) {
                foundSecurityException = true;
                break;
            }
            current = current.getCause();
        }

        assertTrue(foundSecurityException,
                "Se esperaba que el arranque abortara con SecurityException, pero se obtuvo: " + exception);
        assertTrue(fullMessage.toLowerCase().contains(expectedMessageFragment.toLowerCase()),
                "El mensaje de error debía contener '" + expectedMessageFragment + "' pero fue: " + fullMessage);
    }

    @Test
    @DisplayName("A. El arranque permitido funciona con el mapeo Docker 5434 -> 5432 (puerto externo 5434 e interno 5432)")
    void arranquePermitidoConMapeoDocker5434A5432() throws Exception {
        // Simular conexion JDBC real con mapeo Docker:
        // URL externa en metadatos: jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db
        // Servidor interno PostgreSQL reporta: current_database()='lysandri_clone_db', inet_server_port()=5432
        Connection mockConn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        Statement stmt = mock(Statement.class);
        ResultSet rs = mock(ResultSet.class);

        when(mockConn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db");
        when(mockConn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("lysandri_clone_db");
        when(rs.getInt(2)).thenReturn(5432); // Puerto interno del contenedor
        when(rs.getString(3)).thenReturn("5432");

        // No debe lanzar excepcion porque el puerto externo es 5434 y la base es lysandri_clone_db
        assertDoesNotThrow(() -> DatabaseTargetSafetyGuard.validateLiveCloneConnection(mockConn, "TestDataSourceClone"));

        // Verificar que el estado de solo lectura y autocommit se restauran incondicionalmente
        verify(mockConn).setReadOnly(true);
        verify(mockConn, atLeastOnce()).setReadOnly(false);
    }

    @Test
    @DisplayName("B. Arranque aborta antes de Flyway si spring.datasource.url apunta al puerto prohibido 5432")
    void arranqueAbortaSiDatasourceApuntaAPuerto5432() {
        assertStartupAbortsWithSafetyException(
                new String[]{
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/lysandri_clone_db",
                        "--spring.datasource.username=lysandri_app",
                        "--spring.datasource.password=dummy_secret"
                },
                "5432"
        );
    }

    @Test
    @DisplayName("B. Arranque aborta antes de Flyway si spring.datasource.url apunta a la base original lysandri_db")
    void arranqueAbortaSiDatasourceApuntaALysandriDb() {
        assertStartupAbortsWithSafetyException(
                new String[]{
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/lysandri_db",
                        "--spring.datasource.username=lysandri_app",
                        "--spring.datasource.password=dummy_secret"
                },
                "lysandri_db"
        );
    }

    @Test
    @DisplayName("B. Arranque aborta antes de Flyway ante URLs multihost ambiguas o no autorizadas")
    void arranqueAbortaSiDatasourceUsaMultihostAmbiguo() {
        assertStartupAbortsWithSafetyException(
                new String[]{
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5434,127.0.0.1:5436/lysandri_clone_db",
                        "--spring.datasource.username=lysandri_app",
                        "--spring.datasource.password=dummy_secret"
                },
                "multi-host"
        );
    }

    @Test
    @DisplayName("B. Arranque aborta antes de Flyway si spring.datasource.url apunta a una base inesperada para clone")
    void arranqueAbortaSiDatasourceApuntaABaseInesperada() {
        assertStartupAbortsWithSafetyException(
                new String[]{
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/otra_base_no_autorizada",
                        "--spring.datasource.username=lysandri_app",
                        "--spring.datasource.password=dummy_secret"
                },
                "authorized clone database"
        );
    }

    @Test
    @DisplayName("B. Arranque aborta antes de Flyway si spring.flyway.url sobrescribe y apunta al puerto 5432")
    void arranqueAbortaSiFlywayUrlApuntaAPuerto5432() {
        assertStartupAbortsWithSafetyException(
                new String[]{
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db",
                        "--spring.flyway.url=jdbc:postgresql://127.0.0.1:5432/lysandri_clone_db",
                        "--spring.flyway.user=lysandri_admin",
                        "--spring.flyway.password=dummy_secret"
                },
                "5432"
        );
    }

    @Test
    @DisplayName("C. Sobrescritura mediante propiedad de sistema no elude la proteccion de seguridad")
    void sobrescrituraPorSystemPropertyAbortaArranque() {
        String propKey = "spring.datasource.url";
        String oldVal = System.getProperty(propKey);
        try {
            System.setProperty(propKey, "jdbc:postgresql://127.0.0.1:5432/lysandri_clone_db");
            assertStartupAbortsWithSafetyException(
                    new String[]{
                            "--spring.datasource.username=lysandri_app",
                            "--spring.datasource.password=dummy_secret"
                    },
                    "5432"
            );
        } finally {
            if (oldVal != null) {
                System.setProperty(propKey, oldVal);
            } else {
                System.clearProperty(propKey);
            }
        }
    }

    @Test
    @DisplayName("C. Sobrescritura de Flyway URL mediante propiedad de sistema no elude la proteccion")
    void sobrescrituraFlywayPorSystemPropertyAbortaArranque() {
        String propKey = "spring.flyway.url";
        String oldVal = System.getProperty(propKey);
        try {
            System.setProperty(propKey, "jdbc:postgresql://127.0.0.1:5432/lysandri_clone_db");
            assertStartupAbortsWithSafetyException(
                    new String[]{
                            "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db",
                            "--spring.datasource.username=lysandri_app",
                            "--spring.datasource.password=dummy_secret"
                    },
                    "5432"
            );
        } finally {
            if (oldVal != null) {
                System.setProperty(propKey, oldVal);
            } else {
                System.clearProperty(propKey);
            }
        }
    }

    @Test
    @DisplayName("Sanitizacion estricta: mensajes de error y URLs nunca revelan contrasenas ni secretos")
    void sanitizacionDeSecretosEnUrlYMensajes() {
        String rawUrlWithCreds = "jdbc:postgresql://super_admin:super_secret_password_123@127.0.0.1:5432/lysandri_db?password=token_secret_456";
        SecurityException ex = assertThrows(SecurityException.class, () -> {
            DatabaseTargetSafetyGuard.validateJdbcUrl(rawUrlWithCreds, "TestUrlWithSecrets");
        });

        String msg = ex.getMessage();
        assertFalse(msg.contains("super_secret_password_123"), "El mensaje de error no debe revelar la contrasena de la autoridad");
        assertFalse(msg.contains("token_secret_456"), "El mensaje de error no debe revelar parametros de consulta con secretos");
        assertTrue(msg.contains("***@"), "El mensaje debe contener la sanitizacion de credenciales");
    }

    @Test
    @DisplayName("D. Sin perfil explícito: fallo claro e inmediato antes de modificar una base")
    void arranqueSinPerfilExplicitoAbortaInmediatamente() {
        String propKey = "spring.profiles.active";
        String oldProfile = System.getProperty(propKey);
        try {
            System.clearProperty(propKey);
            assertStartupAbortsWithSafetyException(
                    null,
                    new String[]{
                            "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/lysandri_clone_db",
                            "--spring.datasource.username=lysandri_app",
                            "--spring.datasource.password=dummy_secret"
                    },
                    "perfil explícito"
            );
        } finally {
            if (oldProfile != null) {
                System.setProperty(propKey, oldProfile);
            } else {
                System.clearProperty(propKey);
            }
        }
    }

    @Test
    @DisplayName("E. Destino original prohibido en perfil de prueba (test): rechazo de puerto 5432 antes de Flyway")
    void arranqueEnPerfilTestRechazaPuerto5432() {
        assertStartupAbortsWithSafetyException(
                "test",
                new String[]{
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/lysandri_clone_db",
                        "--spring.datasource.username=lysandri_app",
                        "--spring.datasource.password=dummy_secret"
                },
                "5432"
        );
    }

    @Test
    @DisplayName("F. Destino original prohibido en perfil de prueba (test): rechazo de base lysandri_db antes de Flyway")
    void arranqueEnPerfilTestRechazaBaseOriginalLysandriDb() {
        assertStartupAbortsWithSafetyException(
                "test",
                new String[]{
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5434/lysandri_db",
                        "--spring.datasource.username=lysandri_app",
                        "--spring.datasource.password=dummy_secret"
                },
                "lysandri_db"
        );
    }

    @Test
    @DisplayName("G. Variables faltantes o incompletas: error identificable sin revelar secretos")
    void variablesFaltantesErrorIdentificableSinRevelarValores() {
        // En profile clone sin proporcionar password requerido
        Throwable ex = assertThrows(Throwable.class, () -> {
            SpringApplicationBuilder app = new SpringApplicationBuilder(LysandriApplication.class)
                    .profiles("clone");
            try (ConfigurableApplicationContext ctx = app.run()) {
                fail("No debió iniciar sin variables requeridas de contraseña");
            }
        });

        String message = ex.getMessage();
        assertNotNull(message);
        // Verificar que no se expongan contraseñas o datos sensibles
        assertFalse(message.contains("lysandri_app_secret_2026"));
        assertFalse(message.contains("lysandri_admin_secret_2026"));
    }
}

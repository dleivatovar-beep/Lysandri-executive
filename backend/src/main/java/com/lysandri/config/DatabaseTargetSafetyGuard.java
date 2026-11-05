package com.lysandri.config;

import org.flywaydb.core.Flyway;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;

/**
 * Guardian de seguridad activo en el backend ejecutable para el perfil clone.
 * Valida los destinos efectivos de DataSource y Flyway antes de cualquier
 * conexion, migracion o modificacion, garantizando aislamiento estricto
 * frente a la base de datos de produccion original (lysandri_db / puerto 5432).
 *
 * Distingue adecuadamente entre el puerto de conexion externo (host) y el puerto
 * interno del contenedor (5432 en mapeos Docker 5434 -> 5432).
 */
public final class DatabaseTargetSafetyGuard {

    public static final int FORBIDDEN_PRODUCTION_PORT = 5432;
    public static final String FORBIDDEN_PRODUCTION_DB = "lysandri_db";
    public static final String FORBIDDEN_PRODUCTION_CONTAINER_ID = "d3e0bb55ec61";
    public static final String FORBIDDEN_PRODUCTION_CONTAINER_NAME = "lysandri-postgres";
    public static final String FORBIDDEN_PRODUCTION_VOLUME = "lysandri-executive_pgdata";

    public static final String AUTHORIZED_CLONE_DB = "lysandri_clone_db";
    public static final int AUTHORIZED_CLONE_PORT = 5434;
    private static final Set<String> AUTHORIZED_CLONE_HOSTS = Set.of("127.0.0.1", "localhost");

    private DatabaseTargetSafetyGuard() {}

    public static class ParsedJdbcTarget {
        public final String host;
        public final int port;
        public final String database;
        public final boolean isMultiHost;

        public ParsedJdbcTarget(String host, int port, String database, boolean isMultiHost) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.isMultiHost = isMultiHost;
        }
    }

    /**
     * Parsea una URL JDBC de PostgreSQL extrayendo host, puerto y base de datos.
     */
    public static ParsedJdbcTarget parsePostgresUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        String clean = jdbcUrl.trim();
        if (!clean.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql://")) {
            return null;
        }

        String pathPart = clean.substring("jdbc:postgresql://".length());
        int slashIdx = pathPart.indexOf('/');
        if (slashIdx < 0) {
            return null;
        }

        String hostPart = pathPart.substring(0, slashIdx);
        String dbPart = pathPart.substring(slashIdx + 1);

        int qIdx = dbPart.indexOf('?');
        if (qIdx >= 0) {
            dbPart = dbPart.substring(0, qIdx);
        }

        boolean multiHost = hostPart.contains(",");
        String primaryHost = hostPart;
        int port = 5432; // Default Postgres port if omitted

        if (multiHost) {
            String[] hosts = hostPart.split(",");
            primaryHost = hosts[0];
        }

        if (primaryHost.contains(":")) {
            String[] parts = primaryHost.split(":");
            primaryHost = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {}
        }

        return new ParsedJdbcTarget(primaryHost, port, dbPart, multiHost);
    }

    /**
     * Valida una URL JDBC para verificar que no apunte a produccion,
     * no contenga puertos ni bases prohibidas, y rechace multihost ambiguos.
     */
    public static void validateJdbcUrl(String jdbcUrl, String label) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new SecurityException("SAFETY VIOLATION: " + label + " cannot be null or empty");
        }

        String raw = jdbcUrl.trim();
        String cleanUrl = raw.toLowerCase(Locale.ROOT);

        // 1. Prohibicion absoluta del puerto externo 5432 en cualquier posicion
        if (cleanUrl.matches(".*[:@]5432([/?&#,].*)?$")
                || cleanUrl.contains(":5432/")
                || cleanUrl.contains(":5432?")
                || cleanUrl.contains(":5432,")
                || cleanUrl.endsWith(":5432")) {
            throw new SecurityException(sanitizeMessage("SAFETY VIOLATION: External port 5432 in " + label
                    + " belongs to the production/original database and is strictly forbidden! Found in: " + sanitizeUrl(raw)));
        }

        // 2. Prohibicion absoluta del nombre de base lysandri_db
        if (cleanUrl.matches(".*/" + FORBIDDEN_PRODUCTION_DB + "([/?&#].*)?$")
                || cleanUrl.contains("/" + FORBIDDEN_PRODUCTION_DB)) {
            throw new SecurityException(sanitizeMessage("SAFETY VIOLATION: Database '" + FORBIDDEN_PRODUCTION_DB
                    + "' in " + label + " is the production/original database and is strictly forbidden! Found in: " + sanitizeUrl(raw)));
        }

        // 3. Rechazar URLs multihost no autorizadas o ambiguas
        ParsedJdbcTarget parsed = parsePostgresUrl(raw);
        if (parsed != null && parsed.isMultiHost) {
            throw new SecurityException("SAFETY VIOLATION: Ambiguous or unauthorized multi-host JDBC URL detected in " + label);
        }
    }

    public static boolean isAuthorizedCloneDatabase(String database) {
        if (database == null) return false;
        String clean = database.trim().toLowerCase(Locale.ROOT);
        return clean.equals(AUTHORIZED_CLONE_DB) || clean.startsWith("lysandri_clone_");
    }

    /**
     * Valida de manera estricta que la URL pertenezca exclusivamente
     * a los destinos autorizados del perfil clone (puerto externo 5434 y base lysandri_clone_db o desechables autorizadas).
     */
    public static void validateCloneJdbcUrl(String jdbcUrl, String label) {
        validateJdbcUrl(jdbcUrl, label);

        ParsedJdbcTarget parsed = parsePostgresUrl(jdbcUrl);
        if (parsed == null) {
            throw new SecurityException("SAFETY VIOLATION: " + label + " must be a valid PostgreSQL JDBC URL with host and database");
        }

        if (parsed.isMultiHost) {
            throw new SecurityException("SAFETY VIOLATION: Multi-host URLs are not permitted for clone target in " + label);
        }

        // Validar base autorizada
        if (!isAuthorizedCloneDatabase(parsed.database)) {
            throw new SecurityException("SAFETY VIOLATION: Database '" + parsed.database + "' in " + label
                    + " is not the authorized clone database. Expected '" + AUTHORIZED_CLONE_DB + "'");
        }

        // Validar host
        if (!AUTHORIZED_CLONE_HOSTS.contains(parsed.host.toLowerCase(Locale.ROOT))) {
            throw new SecurityException("SAFETY VIOLATION: Host '" + parsed.host + "' in " + label
                    + " is not an authorized clone host!");
        }

        // Validar puerto externo explicito
        if (parsed.port != AUTHORIZED_CLONE_PORT) {
            throw new SecurityException("SAFETY VIOLATION: Port " + parsed.port + " in " + label
                    + " is not the authorized clone port (" + AUTHORIZED_CLONE_PORT + ")!");
        }
    }

    /**
     * Valida la identidad real de la base de datos de manera estricta para el perfil clone
     * usando una conexion de solo lectura. Distingue el puerto externo 5434 del puerto
     * interno 5432 del contenedor Docker y restaura incondicionalmente el estado de conexion.
     */
    public static void validateLiveCloneConnection(Connection conn, String label) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SecurityException("SAFETY VIOLATION: Cannot validate a null or closed connection for " + label);
        }

        // 1. Validar la URL obtenida desde los metadatos de la conexion real
        String metaUrl = conn.getMetaData().getURL();
        if (metaUrl != null && !metaUrl.isBlank()) {
            validateCloneJdbcUrl(metaUrl, label + " Metadata URL");
        }

        // 2. Ejecutar validaciones en conexion de solo lectura
        boolean originalReadOnly = conn.isReadOnly();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("SET TRANSACTION READ ONLY");
                } catch (SQLException ignored) {}

                try (ResultSet rs = stmt.executeQuery(
                        "SELECT current_database(), inet_server_port(), coalesce(current_setting('port', true), '0')")) {
                    if (rs.next()) {
                        String activeDb = rs.getString(1);

                        // Comprobar nombre de base
                        if (FORBIDDEN_PRODUCTION_DB.equalsIgnoreCase(activeDb)) {
                            throw new SecurityException(sanitizeMessage("FATAL SAFETY TRIPWIRE: Connected database in " + label + " is '"
                                    + activeDb + "' which is the immutable production database! Aborting immediately."));
                        }

                        if (!isAuthorizedCloneDatabase(activeDb)) {
                            throw new SecurityException(sanitizeMessage("SAFETY VIOLATION: Connected database in " + label + " is '"
                                    + activeDb + "', expected authorized clone database '" + AUTHORIZED_CLONE_DB + "'"));
                        }

                        // Nota de seguridad:
                        // En contenedores Docker con mapeo de puertos (127.0.0.1:5434 -> 5432/tcp),
                        // inet_server_port() e internal configPort reportan el puerto de escucha interno
                        // del contenedor (5432). Esto es legitimo y esperado siempre que el puerto de conexion
                        // externo (validado en metaUrl y DataSource/Flyway URLs) sea estrictamente 5434.
                    }
                }
            }
        } finally {
            try {
                conn.setReadOnly(originalReadOnly);
            } catch (SQLException ignored) {}
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Valida de manera generica cualquier conexion activa contra un destino autorizado,
     * garantizando que no apunte a la base de produccion original ni al puerto 5432 externo.
     */
    public static void validateLiveConnection(Connection conn) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new SecurityException("SAFETY VIOLATION: Cannot validate a null or closed connection");
        }

        String metaUrl = conn.getMetaData().getURL();
        if (metaUrl != null && !metaUrl.isBlank()) {
            validateJdbcUrl(metaUrl, "Live Connection Metadata URL");
        }

        boolean originalReadOnly = conn.isReadOnly();
        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                try {
                    stmt.execute("SET TRANSACTION READ ONLY");
                } catch (SQLException ignored) {}

                try (ResultSet rs = stmt.executeQuery(
                        "SELECT current_database(), inet_server_port(), coalesce(current_setting('port', true), '0')")) {
                    if (rs.next()) {
                        String activeDb = rs.getString(1);

                        if (FORBIDDEN_PRODUCTION_DB.equalsIgnoreCase(activeDb)) {
                            throw new SecurityException(sanitizeMessage("FATAL SAFETY TRIPWIRE: Connected database is '"
                                    + activeDb + "' which is the immutable production database! Aborting immediately."));
                        }

                        if (metaUrl != null) {
                            ParsedJdbcTarget parsed = parsePostgresUrl(metaUrl);
                            if (parsed != null && parsed.port == FORBIDDEN_PRODUCTION_PORT) {
                                throw new SecurityException(sanitizeMessage("FATAL SAFETY TRIPWIRE: Connected PostgreSQL server external target is on port 5432 "
                                        + "which belongs to production! Aborting immediately."));
                            }
                        }
                    }
                }
            }
        } finally {
            try {
                conn.setReadOnly(originalReadOnly);
            } catch (SQLException ignored) {}
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {}
        }
    }

    /**
     * Extrae la URL configurada en el DataSource mediante reflection si esta disponible.
     */
    public static String extractJdbcUrl(DataSource dataSource) {
        if (dataSource == null) return null;
        try {
            Method m = dataSource.getClass().getMethod("getJdbcUrl");
            return (String) m.invoke(dataSource);
        } catch (Exception ignored) {}
        try {
            Method m = dataSource.getClass().getMethod("getUrl");
            return (String) m.invoke(dataSource);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Valida por separado el destino efectivo del DataSource para el perfil clone.
     */
    public static void validateCloneDataSource(Environment env, DataSource dataSource) {
        String dsUrl = env.getProperty("spring.datasource.url");
        if (dsUrl != null && !dsUrl.isBlank()) {
            validateCloneJdbcUrl(dsUrl, "spring.datasource.url");
        }

        String flywayUrl = env.getProperty("spring.flyway.url");
        if (flywayUrl != null && !flywayUrl.isBlank()) {
            validateCloneJdbcUrl(flywayUrl, "spring.flyway.url");
        }

        if (dataSource != null) {
            String beanUrl = extractJdbcUrl(dataSource);
            if (beanUrl != null && !beanUrl.isBlank()) {
                validateCloneJdbcUrl(beanUrl, "DataSource Effective JDBC URL");
            }

            try (Connection conn = dataSource.getConnection()) {
                validateLiveCloneConnection(conn, "DataSource");
            } catch (SQLException e) {
                throw new SecurityException(sanitizeMessage("SAFETY VIOLATION: Failed read-only identity verification against clone database for DataSource: "
                        + e.getMessage()), e);
            }
        }
    }

    /**
     * Valida por separado el destino efectivo de Flyway antes de ejecutar migrate().
     */
    public static void validateCloneFlyway(Environment env, Flyway flyway) {
        String flywayUrl = env.getProperty("spring.flyway.url");
        if (flywayUrl == null || flywayUrl.isBlank()) {
            flywayUrl = env.getProperty("spring.datasource.url");
        }
        if (flywayUrl != null && !flywayUrl.isBlank()) {
            validateCloneJdbcUrl(flywayUrl, "Flyway Target URL");
        }

        DataSource flywayDs = flyway.getConfiguration().getDataSource();
        if (flywayDs != null) {
            String beanUrl = extractJdbcUrl(flywayDs);
            if (beanUrl != null && !beanUrl.isBlank()) {
                validateCloneJdbcUrl(beanUrl, "Flyway DataSource JDBC URL");
            }

            try (Connection conn = flywayDs.getConnection()) {
                validateLiveCloneConnection(conn, "Flyway");
            } catch (SQLException e) {
                throw new SecurityException(sanitizeMessage("SAFETY VIOLATION: Flyway DataSource failed read-only safety verification: "
                        + e.getMessage()), e);
            }
        }
    }

    public static void validateTarget(String jdbcUrl, String containerId) {
        validateJdbcUrl(jdbcUrl, "Target JDBC URL");
        if (containerId != null && !containerId.isBlank()) {
            validateContainerId(containerId);
        }
    }

    public static void validateUrls(String datasourceUrl, String flywayUrl) {
        validateJdbcUrl(datasourceUrl, "Datasource URL");
        validateJdbcUrl(flywayUrl, "Flyway URL");
    }

    public static void validateContainerId(String containerId) {
        String cleanId = containerId.toLowerCase(Locale.ROOT).trim();
        if (cleanId.startsWith(FORBIDDEN_PRODUCTION_CONTAINER_ID)
                || cleanId.contains(FORBIDDEN_PRODUCTION_CONTAINER_NAME)) {
            throw new SecurityException("SAFETY VIOLATION: Target container matches original production container "
                    + FORBIDDEN_PRODUCTION_CONTAINER_NAME + " (" + FORBIDDEN_PRODUCTION_CONTAINER_ID + ")!");
        }
    }

    public static String sanitizeUrl(String url) {
        if (url == null) return "null";
        String sanitized = url.replaceAll("(?i)(//[^:/@]+:)[^@]+@", "$1***@");
        sanitized = sanitized.replaceAll("(?i)([?&](?:password|secret|token|passw|apikey|key)=)[^&]*", "$1***");
        return sanitized;
    }

    public static String sanitizeMessage(String message) {
        if (message == null) return null;
        String sanitized = message.replaceAll("(?i)(//[^:/@]+:)[^@]+@", "$1***@");
        sanitized = sanitized.replaceAll("(?i)([?&](?:password|secret|token|passw|apikey|key)=)[^&]*", "$1***");
        sanitized = sanitized.replaceAll("(?i)((?:password|secret|token|passw)\\s*[:=]\\s*)[^\\s,;'\"]+", "$1***");
        return sanitized;
    }
}

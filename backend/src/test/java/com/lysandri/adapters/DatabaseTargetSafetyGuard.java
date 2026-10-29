package com.lysandri.adapters;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Delegado de compatibilidad para código de prueba existente.
 * La implementación oficial y canónica reside en {@link com.lysandri.config.DatabaseTargetSafetyGuard}.
 */
public final class DatabaseTargetSafetyGuard {

    public static final int FORBIDDEN_PRODUCTION_PORT = com.lysandri.config.DatabaseTargetSafetyGuard.FORBIDDEN_PRODUCTION_PORT;
    public static final String FORBIDDEN_PRODUCTION_DB = com.lysandri.config.DatabaseTargetSafetyGuard.FORBIDDEN_PRODUCTION_DB;
    public static final String FORBIDDEN_PRODUCTION_CONTAINER_ID = com.lysandri.config.DatabaseTargetSafetyGuard.FORBIDDEN_PRODUCTION_CONTAINER_ID;
    public static final String FORBIDDEN_PRODUCTION_CONTAINER_NAME = com.lysandri.config.DatabaseTargetSafetyGuard.FORBIDDEN_PRODUCTION_CONTAINER_NAME;
    public static final String FORBIDDEN_PRODUCTION_VOLUME = com.lysandri.config.DatabaseTargetSafetyGuard.FORBIDDEN_PRODUCTION_VOLUME;

    private DatabaseTargetSafetyGuard() {}

    public static void validateTarget(String jdbcUrl, String containerId) {
        com.lysandri.config.DatabaseTargetSafetyGuard.validateTarget(jdbcUrl, containerId);
    }

    public static void validateUrls(String datasourceUrl, String flywayUrl) {
        com.lysandri.config.DatabaseTargetSafetyGuard.validateUrls(datasourceUrl, flywayUrl);
    }

    public static void validateJdbcUrl(String jdbcUrl, String label) {
        com.lysandri.config.DatabaseTargetSafetyGuard.validateJdbcUrl(jdbcUrl, label);
    }

    public static void validateContainerId(String containerId) {
        com.lysandri.config.DatabaseTargetSafetyGuard.validateContainerId(containerId);
    }

    public static boolean isAuthorizedCloneDatabase(String database) {
        return com.lysandri.config.DatabaseTargetSafetyGuard.isAuthorizedCloneDatabase(database);
    }

    public static void validateCloneJdbcUrl(String jdbcUrl, String label) {
        com.lysandri.config.DatabaseTargetSafetyGuard.validateCloneJdbcUrl(jdbcUrl, label);
    }

    public static void validateLiveCloneConnection(Connection conn, String label) throws SQLException {
        com.lysandri.config.DatabaseTargetSafetyGuard.validateLiveCloneConnection(conn, label);
    }

    public static void validateLiveConnection(Connection conn) throws SQLException {
        com.lysandri.config.DatabaseTargetSafetyGuard.validateLiveConnection(conn);
    }
}

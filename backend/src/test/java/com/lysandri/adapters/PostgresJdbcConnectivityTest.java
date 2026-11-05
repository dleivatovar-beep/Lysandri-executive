package com.lysandri.adapters;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresJdbcConnectivityTest extends PostgresTestDatabase {

    @Test
    void connectsWithTheSameJdbcConfigurationAsTheTestProfile() throws Exception {
        String url = database().getJdbcUrl();
        String username = database().getUsername();
        String password = database().getPassword();

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1")) {
            result.next();
            assertEquals(1, result.getInt(1));
        }
    }
}

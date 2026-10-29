package com.lysandri.adapters;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Disposable PostgreSQL only: deliberately ignores shared datasource environment variables. */
public abstract class PostgresTestDatabase {
    private static PostgreSQLContainer<?> DATABASE;
    private static boolean containerStarted = false;

    static PostgreSQLContainer<?> newContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("lysandri_test_db")
                .withUsername("lysandri_test_runner")
                .withPassword(java.util.UUID.randomUUID().toString());
    }

    static synchronized PostgreSQLContainer<?> database() {
        if (!containerStarted) {
            DATABASE = newContainer();
            DATABASE.start();
            containerStarted = true;
        }
        return DATABASE;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> container = database();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.flyway.url", container::getJdbcUrl);
        registry.add("spring.flyway.user", container::getUsername);
        registry.add("spring.flyway.password", container::getPassword);
        registry.add("spring.flyway.validate-on-migrate", () -> true);
    }
}

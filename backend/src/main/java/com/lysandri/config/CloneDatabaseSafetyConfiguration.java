package com.lysandri.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * Configuración de seguridad universal activa en el backend ejecutable para todos los perfiles.
 * Intercepta la inicialización del DataSource y la ejecución de Flyway para
 * validar que los destinos efectivos no apunten a producción original (puerto 5432 o lysandri_db),
 * exige un perfil explícito, y en el perfil 'clone' impone verificación estricta contra el clon autorizado.
 */
@Configuration
public class CloneDatabaseSafetyConfiguration {

    private final Environment environment;

    public CloneDatabaseSafetyConfiguration(Environment environment) {
        this.environment = environment;
    }

    public static void validateActiveProfile(Environment env) {
        String[] activeProfiles = env.getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0
                || (activeProfiles.length == 1 && "default".equalsIgnoreCase(activeProfiles[0]))) {
            throw new SecurityException("SAFETY VIOLATION: Se requiere un perfil explícito de Spring (ej: 'clone', 'test', 'prod') para iniciar la aplicación. No se permite arranque con perfil por defecto para proteger la base de datos original.");
        }
    }

    /**
     * BeanPostProcessor con la más alta prioridad para validar el DataSource
     * y su conexión viva en modo solo lectura antes de que JPA Hibernate o Flyway inicien.
     */
    @Bean
    public static BeanPostProcessor cloneDataSourceSafetyBeanPostProcessor(Environment env) {
        return new CloneDataSourceSafetyBeanPostProcessor(env);
    }

    public static class CloneDataSourceSafetyBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {
        private final Environment env;

        public CloneDataSourceSafetyBeanPostProcessor(Environment env) {
            this.env = env;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            validateActiveProfile(env);
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            validateActiveProfile(env);

            if (bean instanceof DataSource dataSource) {
                boolean isClone = Arrays.asList(env.getActiveProfiles()).contains("clone");
                boolean isProd = Arrays.stream(env.getActiveProfiles())
                        .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));

                if (isClone) {
                    DatabaseTargetSafetyGuard.validateCloneDataSource(env, dataSource);
                } else if (!isProd) {
                    String dsUrl = env.getProperty("spring.datasource.url");
                    if (dsUrl != null && !dsUrl.isBlank()) {
                        DatabaseTargetSafetyGuard.validateJdbcUrl(dsUrl, "spring.datasource.url");
                    }
                    String flywayUrl = env.getProperty("spring.flyway.url");
                    if (flywayUrl != null && !flywayUrl.isBlank()) {
                        DatabaseTargetSafetyGuard.validateJdbcUrl(flywayUrl, "spring.flyway.url");
                    }
                    String beanUrl = DatabaseTargetSafetyGuard.extractJdbcUrl(dataSource);
                    if (beanUrl != null && !beanUrl.isBlank()) {
                        DatabaseTargetSafetyGuard.validateJdbcUrl(beanUrl, "DataSource Effective JDBC URL");
                    }
                }
            }
            return bean;
        }
    }

    /**
     * Estrategia de migración de Flyway que revalida las propiedades finales y la conexión
     * antes de permitir que flyway.migrate() ejecute cualquier script SQL.
     */
    @Bean
    public FlywayMigrationStrategy cloneFlywayMigrationStrategy() {
        return flyway -> {
            validateActiveProfile(this.environment);
            boolean isClone = Arrays.asList(this.environment.getActiveProfiles()).contains("clone");
            boolean isProd = Arrays.stream(this.environment.getActiveProfiles())
                    .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));

            if (isClone) {
                DatabaseTargetSafetyGuard.validateCloneFlyway(this.environment, flyway);
            } else if (!isProd) {
                String flywayUrl = this.environment.getProperty("spring.flyway.url");
                if (flywayUrl == null || flywayUrl.isBlank()) {
                    flywayUrl = this.environment.getProperty("spring.datasource.url");
                }
                if (flywayUrl != null && !flywayUrl.isBlank()) {
                    DatabaseTargetSafetyGuard.validateJdbcUrl(flywayUrl, "Flyway Target URL");
                }
                DataSource flywayDs = flyway.getConfiguration().getDataSource();
                if (flywayDs != null) {
                    String beanUrl = DatabaseTargetSafetyGuard.extractJdbcUrl(flywayDs);
                    if (beanUrl != null && !beanUrl.isBlank()) {
                        DatabaseTargetSafetyGuard.validateJdbcUrl(beanUrl, "Flyway DataSource JDBC URL");
                    }
                }
            }
            flyway.migrate();
        };
    }
}

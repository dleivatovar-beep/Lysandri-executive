package com.lysandri.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita explícitamente la ejecución planificada de tareas en el backend (@Scheduled)
 * permitiendo configurar su activación o desactivación por entorno o perfil de pruebas.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}

package com.lysandri.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME =
            "bearerAuth";

    @Bean
    public OpenAPI lysandriOpenApi() {
        SecurityScheme securityScheme =
                new SecurityScheme();

        securityScheme.setName(
                SECURITY_SCHEME_NAME
        );
        securityScheme.setType(
                SecurityScheme.Type.HTTP
        );
        securityScheme.setScheme(
                "bearer"
        );
        securityScheme.setBearerFormat(
                "JWT"
        );

        Components components =
                new Components();

        components.addSecuritySchemes(
                SECURITY_SCHEME_NAME,
                securityScheme
        );

        SecurityRequirement requirement =
                new SecurityRequirement();

        requirement.addList(
                SECURITY_SCHEME_NAME
        );

        OpenAPI openAPI =
                new OpenAPI();

        openAPI.setComponents(
                components
        );
        openAPI.addSecurityItem(
                requirement
        );

        return openAPI;
    }
}
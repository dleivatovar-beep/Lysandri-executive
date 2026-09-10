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
                new SecurityScheme()
                    .name(
                        SECURITY_SCHEME_NAME
                    )
                    .type(
                        SecurityScheme.Type.HTTP
                    )
                    .scheme("bearer")
                    .bearerFormat("JWT");

        SecurityRequirement requirement =
                new SecurityRequirement()
                    .addList(
                        SECURITY_SCHEME_NAME
                    );

        return new OpenAPI()
            .components(
                new Components()
                    .addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        securityScheme
                    )
            )
            .addSecurityItem(requirement);
    }
}
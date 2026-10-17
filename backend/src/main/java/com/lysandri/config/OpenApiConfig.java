package com.lysandri.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Lysandri Executive API - Tienda de Conocimiento y Motor RAG")
                        .description("API REST para la plataforma ejecutiva de Lysandri. "
                                + "Integra pasarela de pagos izipay Online (tarjetas globales y wallets), aprovisionamiento automático en Moodle LMS, liquidación Interbank/BCP y consultas semánticas con pgvector.")
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("Lysandri")
                                .email("contacto@lysandri.com"))
                        .license(new License()
                                .name("Propietario - Lysandri")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Ingresa el token JWT en el formato: Bearer {token}")));
    }
}

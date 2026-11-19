package com.lysandri.adapters.in.web;

import com.lysandri.application.service.DocumentoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(DocumentoControllerSecurityTest.Config.class)
class DocumentoControllerSecurityTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean DocumentoService servicio() { return mock(DocumentoService.class); }
        @Bean DocumentoController controlador(DocumentoService servicio) { return new DocumentoController(servicio); }
    }
    @Autowired DocumentoController controlador;
    private final DocumentoController.Solicitud solicitud = new DocumentoController.Solicitud("Sílabo", "2", "PROPUESTA", null, "Contenido");

    @Test
    void anonimoNoCargaDocumentos() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controlador.cargar(solicitud));
    }

    @Test
    @WithMockUser(roles = "CONTABILIDAD")
    void contabilidadNoPuedePublicarConocimiento() {
        assertThrows(AccessDeniedException.class, () -> controlador.cargar(solicitud));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPuedeInvocarLaCarga() {
        assertDoesNotThrow(() -> controlador.cargar(solicitud));
    }
}

package com.lysandri.security;

import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import com.lysandri.domain.model.RolUsuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolUsuarioSecurityTest {

    @Test
    void fromString_shouldSupportWorkerAndFinanceRoles() {
        assertEquals(RolUsuario.TRABAJADOR, RolUsuario.fromString("trabajador"));
        assertEquals(RolUsuario.CONTABILIDAD, RolUsuario.fromString("contabilidad"));
        assertEquals(RolUsuario.FINANZAS, RolUsuario.fromString("finanzas"));
        assertEquals(RolUsuario.VENTAS, RolUsuario.fromString("ventas"));
        assertEquals(RolUsuario.COORDINADOR_ACADEMICO, RolUsuario.fromString("coordinador_academico"));
        assertEquals(RolUsuario.AUDITOR, RolUsuario.fromString("auditor"));
    }

    @Test
    void usuarioEntity_shouldExposeRoleAsSpringAuthority() {
        UsuarioEntity usuario = UsuarioEntity.builder()
                .email("trabajador@lysandri.com")
                .nombres("Trabajador")
                .apellidos("Lysandri")
                .rol(RolUsuario.TRABAJADOR)
                .build();

        assertTrue(usuario.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_TRABAJADOR")));
    }
}

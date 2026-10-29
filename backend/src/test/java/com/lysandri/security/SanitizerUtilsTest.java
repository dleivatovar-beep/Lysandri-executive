package com.lysandri.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias: SanitizerUtils (Retiro de PII y Secretos)")
class SanitizerUtilsTest {

    @Test
    @DisplayName("Enmascaramiento de correos electrónicos")
    void maskEmail_enmascaraCorrectamente() {
        assertEquals("c***o@empresa.com", SanitizerUtils.maskEmail("contacto@empresa.com"));
        assertEquals("a***b@c.com", SanitizerUtils.maskEmail("ab@c.com"));
        assertEquals("a***@c.com", SanitizerUtils.maskEmail("a@c.com"));
        assertNull(SanitizerUtils.maskEmail(null));
        assertEquals("", SanitizerUtils.maskEmail(""));
    }

    @Test
    @DisplayName("Sanitización de mensajes con tokens y enlaces")
    void sanitizeMessage_eliminaTokensYEnlacesEnTexto() {
        String input = "Error con enlace http://localhost:5173/activar-cuenta?token=secret123456789abcdef al enviar";
        String sanitized = SanitizerUtils.sanitizeMessage(input);

        assertFalse(sanitized.contains("secret123456789abcdef"), "El token no debe aparecer");
        assertTrue(sanitized.contains("token=***"));
    }

    @Test
    @DisplayName("Sanitización de contraseñas y credenciales")
    void sanitizeMessage_eliminaPasswordYCredenciales() {
        String input = "Fallo de conexión en jdbc:postgresql://user:MiPasswordSuperSecreto123@127.0.0.1:5434/db";
        String sanitized = SanitizerUtils.sanitizeMessage(input);

        assertFalse(sanitized.contains("MiPasswordSuperSecreto123"), "La contraseña de conexión no debe aparecer");
        assertTrue(sanitized.contains("***@"));
    }

    @Test
    @DisplayName("Sanitización de hashes bcrypt")
    void sanitizeMessage_eliminaHashesBcrypt() {
        String input = "Usuario con hash $2a$10$wK1k6Gk7N3p5J8q4L9v2uOu8r0y1z3a5b7c9d1e3f5g7h9i1j3k5l no pudo autenticarse";
        String sanitized = SanitizerUtils.sanitizeMessage(input);

        assertFalse(sanitized.contains("$2a$10$wK1k6Gk7N3p5J8q4L9v2uOu8r0y1z3a5b7c9d1e3f5g7h9i1j3k5l"));
        assertTrue(sanitized.contains("***"));
    }

    @Test
    @DisplayName("Sanitización de encabezados Authorization Bearer")
    void sanitizeMessage_eliminaTokensBearer() {
        String input = "Header Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.abc.def";
        String sanitized = SanitizerUtils.sanitizeMessage(input);

        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.abc.def"));
        assertTrue(sanitized.contains("Bearer ***"));
    }
}

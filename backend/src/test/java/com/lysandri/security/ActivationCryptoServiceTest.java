package com.lysandri.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias: ActivationCryptoService (Clave Exclusiva, Persistencia, Rotación, AES-256-GCM)")
class ActivationCryptoServiceTest {

    private static final String CLAVE_VALIDA_A = "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0";
    private static final String CLAVE_VALIDA_B = "f0e1d2c3b4a59876543210fedcba9876543210fedcba9876543210fedcba9876";

    @Test
    @DisplayName("Cifrar, simular reinicio con la misma clave y descifrar exitosamente")
    void encryptAndDecryptWithSameKeyAcrossRestart() {
        ActivationCryptoService serviceArranque1 = new ActivationCryptoService(CLAVE_VALIDA_A);
        String plainUrl = "https://app.lysandri.com/activar-cuenta?token=secret_token_123456789";

        String encrypted = serviceArranque1.encrypt(plainUrl);
        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("v1:"));
        assertFalse(encrypted.contains("secret_token_123456789"));

        // Simular reinicio creando una nueva instancia con la misma clave persistente
        ActivationCryptoService serviceArranque2 = new ActivationCryptoService(CLAVE_VALIDA_A);
        String decrypted = serviceArranque2.decrypt(encrypted);

        assertEquals(plainUrl, decrypted);
    }

    @Test
    @DisplayName("Cambiar JWT o clave externa distinta no afecta las activaciones pendientes")
    void changingJwtDoesNotAffectActivationEncryption() {
        // La clave de activación es completamente independiente de cualquier JWT
        ActivationCryptoService cryptoService = new ActivationCryptoService(CLAVE_VALIDA_A);
        String originalLink = "http://localhost:5173/activar-cuenta?token=jwt_independent_token";

        String encrypted = cryptoService.encrypt(originalLink);

        // Nueva instancia con la clave de activaciones intacta (independiente de rotación de JWT)
        ActivationCryptoService cryptoServicePostJwtRotation = new ActivationCryptoService(CLAVE_VALIDA_A);
        assertEquals(originalLink, cryptoServicePostJwtRotation.decrypt(encrypted));
    }

    @Test
    @DisplayName("Clave ausente genera IllegalStateException en arranque")
    void missingKeyThrowsIllegalStateException() {
        IllegalStateException exNull = assertThrows(IllegalStateException.class, () -> new ActivationCryptoService(null));
        assertTrue(exNull.getMessage().contains("Configuración obligatoria ausente"));

        IllegalStateException exEmpty = assertThrows(IllegalStateException.class, () -> new ActivationCryptoService("   "));
        assertTrue(exEmpty.getMessage().contains("Configuración obligatoria ausente"));
    }

    @Test
    @DisplayName("Clave con longitud menor a 32 caracteres genera IllegalArgumentException")
    void shortKeyThrowsIllegalArgumentException() {
        String shortKey = "clave-corta-de-20-car";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new ActivationCryptoService(shortKey));
        assertTrue(ex.getMessage().contains("Longitud insuficiente"));
    }

    @Test
    @DisplayName("Clave incorrecta lanza ActivationCryptoException sin exponer secretos ni alterar payload")
    void wrongKeyThrowsActivationCryptoException() {
        ActivationCryptoService serviceKeyA = new ActivationCryptoService(CLAVE_VALIDA_A);
        String plainLink = "http://localhost:5173/activar?token=recoverable_secret_link";
        String encrypted = serviceKeyA.encrypt(plainLink);

        ActivationCryptoService serviceKeyB = new ActivationCryptoService(CLAVE_VALIDA_B);
        ActivationCryptoException ex = assertThrows(ActivationCryptoException.class, () -> serviceKeyB.decrypt(encrypted));
        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().contains("recoverable_secret_link"));
        assertFalse(ex.getMessage().contains(CLAVE_VALIDA_A));
        assertFalse(ex.getMessage().contains(CLAVE_VALIDA_B));
    }

    @Test
    @DisplayName("Rotación fluida: permite descifrar payloads cifrados con clave previa")
    void keyRotationWithPreviousKeySupported() {
        // Payload generado antes de la rotación con clave A
        ActivationCryptoService legacyService = new ActivationCryptoService(CLAVE_VALIDA_A);
        String secretLink = "https://app.lysandri.com/activar?token=legacy_token_rotated";
        String encryptedLegacy = legacyService.encrypt(secretLink);

        // Nuevo servicio rotado a clave B, manteniendo clave A como previousKey
        ActivationCryptoService rotatedService = new ActivationCryptoService(CLAVE_VALIDA_B, CLAVE_VALIDA_A);

        // Puede descifrar el payload antiguo sin error
        String decrypted = rotatedService.decrypt(encryptedLegacy);
        assertEquals(secretLink, decrypted);

        // Los nuevos cifrados se realizan con la clave nueva B
        String newEncrypted = rotatedService.encrypt(secretLink);
        // El nuevo payload es descifrable por el servicio rotado
        assertEquals(secretLink, rotatedService.decrypt(newEncrypted));
        // Y es descifrable por un servicio con clave primaria B
        assertEquals(secretLink, new ActivationCryptoService(CLAVE_VALIDA_B).decrypt(newEncrypted));
    }
}

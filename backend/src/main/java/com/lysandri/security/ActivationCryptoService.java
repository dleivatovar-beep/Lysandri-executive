package com.lysandri.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Servicio criptográfico dedicado y persistente para cifrado fuerte AES-256-GCM
 * de enlaces y cargas sensibles de activación de cuentas.
 *
 * REQUISITOS ESTRICTOS:
 * - Requiere exclusivamente una clave persistente configurada externamente (app.security.activation-encryption-key).
 * - No posee fallback a JWT ni claves fijas embebidas en código.
 * - Falla de forma estricta en el arranque si la configuración es ausente o menor a 32 caracteres (256 bits).
 * - Cada cifrado utiliza un nonce/IV criptográfico aleatorio único de 12 bytes.
 * - Soporta versión de payload ("v1:") y rotación controlada mediante clave previa (app.security.activation-encryption-key-previous).
 * - Ante fallo de descifrado, preserva la carga para evitar destrucción silenciosa y lanza ActivationCryptoException.
 */
@Slf4j
@Service
public class ActivationCryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    public static final String VERSION_PREFIX = "v1:";

    private final SecretKey primarySecretKey;
    private final SecretKey previousSecretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ActivationCryptoService(
            @Value("${app.security.activation-encryption-key:#{null}}") String primaryKey
    ) {
        this(primaryKey, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ActivationCryptoService(
            @Value("${app.security.activation-encryption-key:#{null}}") String primaryKey,
            @Value("${app.security.activation-encryption-key-previous:#{null}}") String previousKey
    ) {
        if (primaryKey == null || primaryKey.trim().isBlank()) {
            throw new IllegalStateException(
                    "Configuración obligatoria ausente: 'app.security.activation-encryption-key' (o variable ACTIVATION_ENCRYPTION_KEY). " +
                    "Se requiere una clave persistente exclusiva de al menos 32 caracteres (256 bits). " +
                    "No se permite fallback a JWT ni claves fijas por motivos de seguridad y resiliencia."
            );
        }

        String trimmedPrimary = primaryKey.trim();
        if (trimmedPrimary.length() < 32) {
            throw new IllegalArgumentException(
                    "Longitud insuficiente para la clave de cifrado de activaciones (longitud=" + trimmedPrimary.length() +
                    "). Se requieren al menos 32 caracteres (256 bits)."
            );
        }

        this.primarySecretKey = deriveKey(trimmedPrimary);

        if (previousKey != null && !previousKey.trim().isBlank()) {
            String trimmedPrevious = previousKey.trim();
            if (trimmedPrevious.length() < 32) {
                throw new IllegalArgumentException(
                        "Longitud insuficiente para la clave previa de cifrado de activaciones (longitud=" + trimmedPrevious.length() +
                        "). Se requieren al menos 32 caracteres."
                );
            }
            this.previousSecretKey = deriveKey(trimmedPrevious);
            log.info("Clave previa de cifrado de activaciones inicializada para soporte de rotación fluida.");
        } else {
            this.previousSecretKey = null;
        }

        log.info("Servicio criptográfico de activaciones inicializado correctamente con AES-256-GCM y clave persistente exclusiva.");
    }

    private SecretKey deriveKey(String rawKey) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Fallo al inicializar algoritmo SHA-256 para derivación de clave", e);
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, primarySecretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
            return VERSION_PREFIX + encoded;
        } catch (Exception e) {
            log.error("Fallo criptográfico al cifrar enlace de activación");
            throw new ActivationCryptoException("Error al cifrar enlace de activación", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }
        String cleanText = encryptedText.trim();
        if (cleanText.startsWith(VERSION_PREFIX)) {
            cleanText = cleanText.substring(VERSION_PREFIX.length());
        }

        // Intento 1: Clave primaria activa
        try {
            return decryptWithKey(cleanText, primarySecretKey);
        } catch (Exception exPrimary) {
            // Intento 2: Clave previa configurada para soporte de rotación
            if (previousSecretKey != null) {
                try {
                    return decryptWithKey(cleanText, previousSecretKey);
                } catch (Exception exSecondary) {
                    // Ignorar para reportar error general sanitizado
                }
            }
            log.error("Fallo criptográfico al descifrar payload de activación: clave incorrecta o datos corruptos");
            throw new ActivationCryptoException("Error al descifrar enlace de activación con la clave configurada", exPrimary);
        }
    }

    private String decryptWithKey(String base64Payload, SecretKey key) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(base64Payload);
        if (decoded.length < GCM_IV_LENGTH_BYTES) {
            throw new IllegalArgumentException("Payload cifrado inválido: longitud insuficiente para IV");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        byteBuffer.get(iv);

        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        byte[] plainTextBytes = cipher.doFinal(cipherText);
        return new String(plainTextBytes, StandardCharsets.UTF_8);
    }
}

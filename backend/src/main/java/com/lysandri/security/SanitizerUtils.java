package com.lysandri.security;

import java.util.regex.Pattern;

public final class SanitizerUtils {

    private SanitizerUtils() {}

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    private static final Pattern TOKEN_URL_PATTERN = Pattern.compile(
            "(?i)(token=)[^&\\s]+"
    );

    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+)[^&,\\s]+"
    );

    private static final Pattern PASSWORD_PARAM_PATTERN = Pattern.compile(
            "(?i)(password|passw|secret|key|apikey|jwt)[=:\\s]+[^&,\\s]+"
    );

    private static final Pattern JDBC_CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(//[^:/@]+:)[^@]+@"
    );

    private static final Pattern BCRYPT_PATTERN = Pattern.compile(
            "\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}"
    );

    /**
     * Enmascara un correo electrónico para diagnóstico técnico seguro sin revelar PII.
     * Ejemplo: "contacto@empresa.com" -> "c***o@empresa.com"
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        if (email.isBlank()) {
            return "";
        }
        String clean = email.trim().toLowerCase();
        int atIdx = clean.indexOf('@');
        if (atIdx < 0) {
            return clean;
        }
        if (atIdx == 0) {
            return "***" + clean;
        }
        String user = clean.substring(0, atIdx);
        String domain = clean.substring(atIdx + 1);

        String maskedUser;
        if (user.length() == 1) {
            maskedUser = user + "***";
        } else if (user.length() == 2) {
            maskedUser = user.charAt(0) + "***" + user.charAt(1);
        } else {
            maskedUser = user.charAt(0) + "***" + user.charAt(user.length() - 1);
        }

        return maskedUser + "@" + domain;
    }

    /**
     * Sanitiza cualquier cadena o mensaje de error eliminando tokens, contraseñas,
     * hashes bcrypt, correos y credenciales JDBC.
     */
    public static String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return message == null ? null : "";
        }

        String sanitized = message;
        // 1. Redactar credenciales en URLs tipo //user:pass@
        sanitized = JDBC_CREDENTIAL_PATTERN.matcher(sanitized).replaceAll("$1***@");
        // 2. Redactar tokens Bearer
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("$1***");
        // 3. Redactar tokens en URLs
        sanitized = TOKEN_URL_PATTERN.matcher(sanitized).replaceAll("$1***");
        // 4. Redactar credenciales o claves en parámetros
        sanitized = PASSWORD_PARAM_PATTERN.matcher(sanitized).replaceAll("$1=***");
        // 5. Redactar hashes bcrypt
        sanitized = BCRYPT_PATTERN.matcher(sanitized).replaceAll("***");
        // 6. Enmascarar correos en mensajes
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(mr -> {
            String masked = maskEmail(mr.group());
            return masked != null ? masked : "***";
        });

        // Limitar longitud para evitar desbordes en columnas de BD o logs
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 497) + "...";
        }
        return sanitized;
    }
}

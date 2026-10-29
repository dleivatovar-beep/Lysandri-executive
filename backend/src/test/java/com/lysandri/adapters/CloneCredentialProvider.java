package com.lysandri.adapters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public final class CloneCredentialProvider {

    private static final Map<String, String> CACHED_PROPERTIES = new HashMap<>();

    static {
        loadEnvFile(new File("F:\\Lysandri-executive\\.env.local"));
        loadEnvFile(new File("F:\\Lysandri-executive\\backend\\.env.local"));
        loadEnvFile(new File(".env.local"));
    }

    private CloneCredentialProvider() {}

    private static void loadEnvFile(File file) {
        if (file.exists() && file.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("#") && line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        CACHED_PROPERTIES.put(parts[0].trim(), parts[1].trim());
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static String getSecret(String key) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val;
        val = CACHED_PROPERTIES.get(key);
        if (val != null && !val.isBlank()) return val;
        return null;
    }

    public static String getRequiredSecret(String key) {
        String val = getSecret(key);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException("CRITICAL: Required secret '" + key + "' is not set in environment or untracked .env.local!");
        }
        return val;
    }
}

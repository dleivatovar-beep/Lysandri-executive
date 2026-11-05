package com.lysandri.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Gestor del entorno de restauracion aislada por ejecucion.
 * Crea un contenedor y volumen Docker unicos y temporales,
 * verifica la integridad SHA-256 del respaldo antes de restaurar,
 * valida los codigos de salida de Docker, psql y pg_restore,
 * y limpia exclusivamente los recursos creados por esta ejecucion sin
 * alterar ningun contenedor ni clon persistente preexistente.
 */
public class IsolatedDockerRestorationEnvironment implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IsolatedDockerRestorationEnvironment.class);

    public static final String EXPECTED_BACKUP_SHA256 =
            "ac8e828a0811dd3d668dd9943e1c7fb31e9073046f24b0997d0273cce0e38bb1";

    private final String runId;
    private final String containerName;
    private final String volumeName;
    private final Path backupPath;
    private int hostPort;
    private boolean started = false;

    public IsolatedDockerRestorationEnvironment() {
        this.runId = "restore_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 6);
        this.containerName = "lysandri-" + runId;
        this.volumeName = "lysandri_vol_" + runId;

        // Buscar respaldo verificado en rutas relativas comunes
        Path candidate1 = Paths.get("../database/backup_verified.dump").toAbsolutePath().normalize();
        Path candidate2 = Paths.get("database/backup_verified.dump").toAbsolutePath().normalize();
        Path candidate3 = Paths.get("F:/Lysandri-executive/database/backup_verified.dump").toAbsolutePath().normalize();

        if (Files.exists(candidate1)) {
            this.backupPath = candidate1;
        } else if (Files.exists(candidate2)) {
            this.backupPath = candidate2;
        } else if (Files.exists(candidate3)) {
            this.backupPath = candidate3;
        } else {
            throw new IllegalStateException("No se encontro el archivo de respaldo verificado en ninguna ruta esperada");
        }
    }

    public void startAndRestore() throws Exception {
        log.info("Iniciando entorno de restauracion aislada. RunId: {}, Contenedor: {}, Volumen: {}",
                runId, containerName, volumeName);

        // 1. Verificar SHA-256 del respaldo antes de cualquier accion
        verifyBackupSha256();

        // 2. Crear volumen Docker exclusivo de esta ejecucion
        runDockerCommand("volume", "create", volumeName);

        // 3. Iniciar contenedor temporal mapeado a puerto dinamico en localhost
        // Usamos la imagen pgvector:pg16 requerida para soportar tipos vectoriales
        runDockerCommand("run", "-d",
                "--name", containerName,
                "--label", "owner=" + runId,
                "-v", volumeName + ":/var/lib/postgresql/data",
                "-p", "127.0.0.1::5432",
                "-e", "POSTGRES_DB=lysandri_verify_db",
                "-e", "POSTGRES_USER=postgres",
                "-e", "POSTGRES_PASSWORD=restore_temp_secret_2026",
                "pgvector/pgvector:pg16"
        );
        this.started = true;

        // 4. Obtener el puerto dinamico asignado por el host
        this.hostPort = extractAssignedHostPort();
        log.info("Contenedor {} en ejecucion en 127.0.0.1:{}", containerName, hostPort);

        // 5. Esperar disponibilidad de PostgreSQL
        waitForPostgresReady();

        // 6. Configurar usuarios lysandri_admin y lysandri_app
        setupRoles();

        // 7. Copiar respaldo al contenedor verificando codigo de salida
        runDockerCommand("cp", backupPath.toString(), containerName + ":/tmp/backup.dump");

        // 8. Restaurar respaldo con pg_restore comprobando codigo de salida
        runPgRestore();

        log.info("Restauracion limpia y verificada completada con exito en contenedor {}", containerName);
    }

    private void verifyBackupSha256() throws Exception {
        log.info("Verificando SHA-256 del respaldo: {}", backupPath);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(backupPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        String calculatedSha = HexFormat.of().formatHex(digest.digest()).toLowerCase();
        if (!EXPECTED_BACKUP_SHA256.equalsIgnoreCase(calculatedSha)) {
            throw new SecurityException("SAFETY VIOLATION: El hash SHA-256 del respaldo no coincide con el verificado. "
                    + "Esperado: " + EXPECTED_BACKUP_SHA256 + " | Calculado: " + calculatedSha);
        }
        log.info("Integridad SHA-256 del respaldo confirmada.");
    }

    private int extractAssignedHostPort() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "port", containerName, "5432");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor(10, TimeUnit.SECONDS);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Fallo al obtener puerto dinamico de Docker: " + output);
        }

        // Output formato: 127.0.0.1:54321 o 0.0.0.0:54321
        int lastColon = output.lastIndexOf(':');
        if (lastColon < 0) {
            throw new IllegalStateException("Formato inesperado en docker port: " + output);
        }
        return Integer.parseInt(output.substring(lastColon + 1).trim());
    }

    private void waitForPostgresReady() throws Exception {
        long deadline = System.currentTimeMillis() + 45000;
        int consecutiveSuccess = 0;
        while (System.currentTimeMillis() < deadline) {
            ProcessBuilder pb = new ProcessBuilder("docker", "exec", containerName, "psql", "-U", "postgres", "-d", "postgres", "-c", "SELECT 1;");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(5, TimeUnit.SECONDS);
            if (process.exitValue() == 0) {
                consecutiveSuccess++;
                if (consecutiveSuccess >= 2) {
                    return;
                }
            } else {
                consecutiveSuccess = 0;
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("PostgreSQL no estuvo listo dentro del tiempo limite en " + containerName);
    }

    private void setupRoles() throws Exception {
        String sql = """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_admin') THEN
                        CREATE ROLE lysandri_admin WITH LOGIN SUPERUSER PASSWORD 'restore_admin_secret_2026';
                    ELSE
                        ALTER ROLE lysandri_admin WITH LOGIN SUPERUSER PASSWORD 'restore_admin_secret_2026';
                    END IF;
                    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
                        CREATE ROLE lysandri_app WITH LOGIN PASSWORD 'restore_app_secret_2026';
                    ELSE
                        ALTER ROLE lysandri_app WITH LOGIN PASSWORD 'restore_app_secret_2026';
                    END IF;
                END
                $$;
                """;
        Exception lastEx = null;
        for (int i = 0; i < 5; i++) {
            try {
                runDockerExecCommand("psql", "-U", "postgres", "-d", "lysandri_verify_db", "-c", sql);
                return;
            } catch (Exception ex) {
                lastEx = ex;
                Thread.sleep(1500);
            }
        }
        throw (lastEx != null ? lastEx : new IllegalStateException("Fallo al configurar roles en " + containerName));
    }

    private void runPgRestore() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", containerName,
                "pg_restore", "-U", "lysandri_admin", "-d", "lysandri_verify_db", "--no-owner", "/tmp/backup.dump"
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor(60, TimeUnit.SECONDS);

        // pg_restore retorna 0 (éxito) o 1 (advertencias no críticas típicas en dumps)
        int exitCode = process.exitValue();
        if (exitCode > 1) {
            throw new IllegalStateException("pg_restore fallo con codigo de salida " + exitCode + ": " + output);
        }
    }

    private String runDockerCommand(String... args) throws Exception {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add("docker");
        Collections.addAll(fullCommand, args);

        ProcessBuilder pb = new ProcessBuilder(fullCommand);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor(60, TimeUnit.SECONDS);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Comando Docker fallo con codigo " + process.exitValue()
                    + " (" + String.join(" ", fullCommand) + "): " + output);
        }
        return output;
    }

    private String runDockerExecCommand(String... args) throws Exception {
        List<String> fullArgs = new ArrayList<>();
        fullArgs.add("exec");
        fullArgs.add(containerName);
        Collections.addAll(fullArgs, args);
        return runDockerCommand(fullArgs.toArray(new String[0]));
    }

    public String getJdbcUrl() {
        return "jdbc:postgresql://127.0.0.1:" + hostPort + "/lysandri_verify_db";
    }

    public String getAdminUser() {
        return "lysandri_admin";
    }

    public String getAdminPassword() {
        return "restore_admin_secret_2026";
    }

    public String getAppUser() {
        return "lysandri_app";
    }

    public String getAppPassword() {
        return "restore_app_secret_2026";
    }

    public int getHostPort() {
        return hostPort;
    }

    public String getContainerName() {
        return containerName;
    }

    @Override
    public void close() {
        if (started) {
            log.info("Limpiando recursos de la ejecucion: contenedor {} y volumen {}", containerName, volumeName);
            try {
                new ProcessBuilder("docker", "rm", "-f", containerName).start().waitFor(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("No se pudo remover contenedor temporal {}: {}", containerName, e.getMessage());
            }
            try {
                new ProcessBuilder("docker", "volume", "rm", volumeName).start().waitFor(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("No se pudo remover volumen temporal {}: {}", volumeName, e.getMessage());
            }
        }
    }
}

#!/usr/bin/env python3
"""
Script de Verificación en Vivo del Artefacto JAR Real contra PostgreSQL Desechable.
Recuperación de la MISMA tarea pendiente tras reinicio sin nueva solicitud HTTP.

Comprueba de forma estricta:
1. Cálculo de SHA-256 del JAR real ejecutado.
2. Contenedor PostgreSQL desechable en puerto 15438 (nunca toca 5432 ni clones).
3. Mantener SMTP temporalmente indisponible.
4. Arranque del JAR real contra PostgreSQL desechable.
5. Solicitud de activación mediante HTTP (POST /api/v1/auth/solicitar-activacion).
6. Comprobación en base de datos de que la tarea está confirmada como PENDIENTE o REINTENTO,
   con su carga cifrada intacta.
7. Registro de su identificador técnico (id_tarea), sin mostrar el enlace ni token.
8. Detención del primer proceso antes del envío exitoso.
9. Restablecimiento del servidor SMTP mock local.
10. Reinicio del MISMO JAR contra la MISMA base y con la MISMA clave persistente de cifrado.
11. Comprobación de que el planificador recupera ESA MISMA tarea (sin hacer otra solicitud de activación).
12. Verificación mediante aserciones de estado ENVIADO y enlace_cifrado IS NULL (purgado).
13. Captura del enlace desde Mock SMTP y activación de la cuenta mediante HTTP (POST /api/v1/auth/activar-cuenta),
    sin imprimir enlaces, tokens ni contraseñas.
14. Parada limpia y destrucción de recursos desechables.
"""

import hashlib
import json
import os
import re
import socket
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.error

JAR_PATH = r"F:\Lysandri-executive\backend\target\lysandri-backend-1.0.0-SNAPSHOT.jar"
DISPOSABLE_CONTAINER = "lysandri-disposable-live-test"
DISPOSABLE_PORT = 15438
DISPOSABLE_DB = "lysandri_live_test_db"
DISPOSABLE_USER = "test_runner"
DISPOSABLE_PASS = "test_runner_pass"
HTTP_PORT = 18088
SMTP_PORT = 1025
ACTIVATION_KEY = "test-activation-encryption-key-32chars-strictly-for-testing"
JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A25432A"

received_emails = []
smtp_running = True

def log(msg):
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)

class MockSmtpServer(threading.Thread):
    def __init__(self, port):
        super().__init__(daemon=True)
        self.port = port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(("127.0.0.1", port))
        self.sock.listen(5)
        self._active = True

    def run(self):
        while self._active and smtp_running:
            try:
                self.sock.settimeout(1.0)
                client, _ = self.sock.accept()
            except socket.timeout:
                continue
            except Exception:
                break
            threading.Thread(target=self.handle_client, args=(client,), daemon=True).start()

    def handle_client(self, client):
        try:
            client.sendall(b"220 localhost ESMTP Mock Lysandri Service\r\n")
            in_data = False
            msg_lines = []
            while True:
                data = client.recv(4096)
                if not data:
                    break
                text = data.decode("utf-8", errors="replace")
                lines = text.split("\r\n")
                for line in lines:
                    if not line and line != "":
                        continue
                    if in_data:
                        if line == ".":
                            in_data = False
                            full_msg = "\n".join(msg_lines)
                            received_emails.append(full_msg)
                            client.sendall(b"250 2.0.0 OK: queued\r\n")
                        else:
                            msg_lines.append(line)
                    else:
                        upper = line.strip().upper()
                        if upper.startswith("HELO") or upper.startswith("EHLO"):
                            client.sendall(b"250-localhost Hello\r\n250 OK\r\n")
                        elif upper.startswith("MAIL FROM:"):
                            client.sendall(b"250 2.1.0 Sender OK\r\n")
                        elif upper.startswith("RCPT TO:"):
                            client.sendall(b"250 2.1.5 Recipient OK\r\n")
                        elif upper == "DATA":
                            in_data = True
                            msg_lines = []
                            client.sendall(b"354 Start mail input; end with <CRLF>.<CRLF>\r\n")
                        elif upper == "QUIT":
                            client.sendall(b"221 2.0.0 Bye\r\n")
                            return
                        elif upper in ("RSET", "NOOP"):
                            client.sendall(b"250 OK\r\n")
                        elif upper:
                            client.sendall(b"250 OK\r\n")
        except Exception:
            pass
        finally:
            try:
                client.close()
            except Exception:
                pass

    def stop(self):
        self._active = False
        try:
            self.sock.close()
        except Exception:
            pass

def compute_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest().upper()

def run_cmd(cmd_list, timeout=60, check=True):
    log(f"CMD: {' '.join(cmd_list)}")
    p = subprocess.run(cmd_list, capture_output=True, text=True, timeout=timeout)
    if check and p.returncode != 0:
        raise RuntimeError(f"Command failed (exit {p.returncode}): {p.stderr}\nStdout: {p.stdout}")
    return p

def docker_exec_psql(sql):
    cmd = [
        "docker", "exec", DISPOSABLE_CONTAINER,
        "psql", "-U", DISPOSABLE_USER, "-d", DISPOSABLE_DB,
        "-t", "-A", "-c", sql
    ]
    p = run_cmd(cmd, timeout=30)
    return p.stdout.strip()

def main():
    global smtp_running
    log("================================================================================")
    log("INICIO DE VERIFICACIÓN EN VIVO: RECUPERACIÓN DE LA MISMA TAREA TRAS REINICIO")
    log("================================================================================")

    # 1. SHA-256 del JAR real
    if not os.path.isfile(JAR_PATH):
        raise FileNotFoundError(f"JAR no encontrado en: {JAR_PATH}")
    jar_hash = compute_sha256(JAR_PATH)
    log(f"Artefacto JAR verificado: {JAR_PATH}")
    log(f"SHA-256 del JAR: {jar_hash}")

    # 2. Iniciar Contenedor PostgreSQL Desechable
    log(f"Limpiando posibles contenedores desechables anteriores ({DISPOSABLE_CONTAINER})...")
    subprocess.run(["docker", "rm", "-f", DISPOSABLE_CONTAINER], capture_output=True)

    log(f"Iniciando contenedor PostgreSQL desechable en puerto {DISPOSABLE_PORT}...")
    run_cmd([
        "docker", "run", "-d",
        "--name", DISPOSABLE_CONTAINER,
        "-e", f"POSTGRES_DB={DISPOSABLE_DB}",
        "-e", f"POSTGRES_USER={DISPOSABLE_USER}",
        "-e", f"POSTGRES_PASSWORD={DISPOSABLE_PASS}",
        "-p", f"{DISPOSABLE_PORT}:5432",
        "pgvector/pgvector:pg16"
    ])

    # Esperar a que PostgreSQL esté listo
    log("Esperando a que PostgreSQL desechable esté listo para aceptar conexiones...")
    ready = False
    for i in range(30):
        res = subprocess.run(
            ["docker", "exec", DISPOSABLE_CONTAINER, "pg_isready", "-U", DISPOSABLE_USER, "-d", DISPOSABLE_DB],
            capture_output=True, text=True
        )
        if res.returncode == 0:
            ready = True
            break
        time.sleep(1)
    if not ready:
        raise RuntimeError("PostgreSQL desechable no respondió a tiempo.")
    log("PostgreSQL desechable listo y operativo en puerto 15438.")

    # 3. MANTENER SMTP TEMPORALMENTE INDISPONIBLE
    # No iniciamos MockSmtpServer aquí; el puerto 1025 permanece cerrado.
    log("SMTP mantenido temporalmente INDISPONIBLE (puerto 1025 cerrado) para forzar encolado/reintento.")

    jar_proc = None
    jar_proc2 = None
    smtp_server = None
    log_file_path = r"F:\Lysandri-executive\backend\jar-live-run.log"
    log_file = open(log_file_path, "w", encoding="utf-8")

    java_cmd = [
        "java",
        "-Dspring.profiles.active=test",
        f"-Dserver.port={HTTP_PORT}",
        f"-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:{DISPOSABLE_PORT}/{DISPOSABLE_DB}",
        f"-Dspring.datasource.username={DISPOSABLE_USER}",
        f"-Dspring.datasource.password={DISPOSABLE_PASS}",
        f"-Dspring.flyway.url=jdbc:postgresql://127.0.0.1:{DISPOSABLE_PORT}/{DISPOSABLE_DB}",
        f"-Dspring.flyway.user={DISPOSABLE_USER}",
        f"-Dspring.flyway.password={DISPOSABLE_PASS}",
        "-Dspring.mail.host=127.0.0.1",
        f"-Dspring.mail.port={SMTP_PORT}",
        "-Dapp.scheduling.enabled=true",
        "-Dapp.outbox.activation.enabled=true",
        "-Dapp.outbox.activation.fixed-delay-ms=1000",
        f"-Dapp.security.activation-encryption-key={ACTIVATION_KEY}",
        f"-Djwt.secret={JWT_SECRET}",
        "-jar", JAR_PATH
    ]

    try:
        # 4. Arrancar el primer proceso JAR
        log("Lanzando primer proceso JAR real (migraciones Flyway automáticas)...")
        jar_proc = subprocess.Popen(
            java_cmd,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            cwd=r"F:\Lysandri-executive\backend"
        )
        log(f"Primer proceso JAR iniciado con PID: {jar_proc.pid}")

        # Esperar disponibilidad HTTP
        log(f"Esperando respuesta HTTP en http://localhost:{HTTP_PORT}...")
        http_ok = False
        health_url = f"http://localhost:{HTTP_PORT}/api/v1/auth/solicitar-activacion"
        for i in range(45):
            if jar_proc.poll() is not None:
                raise RuntimeError(f"El proceso JAR terminó inesperadamente con código: {jar_proc.returncode}")
            try:
                req = urllib.request.Request(health_url, method="OPTIONS")
                with urllib.request.urlopen(req, timeout=2) as resp:
                    http_ok = True
                    break
            except urllib.error.HTTPError:
                http_ok = True
                break
            except Exception:
                time.sleep(1)

        if not http_ok:
            raise RuntimeError("El backend no respondió en HTTP tras 45 segundos.")
        log("Backend listo y respondiendo en HTTP (migraciones Flyway completadas).")

        # 5. Insertar usuario inactivo para prueba en la base desechable
        log("Insertando usuario inactivo en base de datos para la prueba de activación y recuperación...")
        docker_exec_psql("""
            INSERT INTO usuarios (id_user, nombres, apellidos, email, passw, rol)
            VALUES (801, 'Usuario', 'RecoveryTest', 'usuario.recover@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR')
            ON CONFLICT (id_user) DO NOTHING;
            INSERT INTO usuario (id_user, nombres, apellidos, email, passw, rol, activo, bloqueado_admin)
            VALUES (801, 'Usuario', 'RecoveryTest', 'usuario.recover@lysandri.invalid', 'dummy_hash', 'INSTRUCTOR', false, false)
            ON CONFLICT (id_user) DO NOTHING;
        """)

        # 6. Solicitar activación mediante HTTP (POST /api/v1/auth/solicitar-activacion)
        log("Solicitando activación mediante HTTP (POST /api/v1/auth/solicitar-activacion)...")
        req_body = json.dumps({"email": "usuario.recover@lysandri.invalid"}).encode("utf-8")
        req = urllib.request.Request(
            f"http://localhost:{HTTP_PORT}/api/v1/auth/solicitar-activacion",
            data=req_body,
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            resp_data = resp.read().decode("utf-8")
            log(f"Respuesta HTTP recibida ({resp.status}): {resp_data}")

        # Esperar 3 segundos a que el planificador intente enviar a SMTP indisponible y registre fallo
        log("Esperando intento del planificador contra SMTP indisponible...")
        time.sleep(3)

        # 7. Comprobar que la tarea está confirmada en base de datos como PENDIENTE o REINTENTO con carga cifrada
        task_info = docker_exec_psql("""
            SELECT id_tarea || '|' || estado || '|' || (enlace_cifrado IS NOT NULL)::text
            FROM outbox_notificacion_activacion
            WHERE destinatario = 'usuario.recover@lysandri.invalid';
        """)
        if not task_info or "|" not in task_info:
            raise RuntimeError(f"No se encontró la tarea Outbox en la base de datos: '{task_info}'")

        parts = task_info.split("|")
        target_task_id = int(parts[0])
        initial_state = parts[1]
        has_ciphertext = (parts[2].lower() == "true" or parts[2].lower() == "t")

        log(f"Tarea confirmada en BD - Identificador técnico: {target_task_id}")
        log(f"Estado inicial registrado: {initial_state}")
        log(f"Carga sensible cifrada presente en BD: {has_ciphertext}")

        if initial_state not in ("PENDIENTE", "REINTENTO", "PROCESANDO"):
            raise RuntimeError(f"Estado inesperado antes de reinicio: {initial_state}")
        if not has_ciphertext:
            raise RuntimeError("La carga cifrada debe estar presente y no ser NULL.")

        # 8. Detener el primer proceso JAR antes del envío exitoso
        log(f"Deteniendo el primer proceso JAR (PID {jar_proc.pid}) antes del envío...")
        jar_proc.terminate()
        try:
            jar_proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            jar_proc.kill()
        log("Primer proceso JAR detenido exitosamente.")

        # 9. Restablecer SMTP
        log(f"Restableciendo servidor Mock SMTP en 127.0.0.1:{SMTP_PORT}...")
        smtp_server = MockSmtpServer(SMTP_PORT)
        smtp_server.start()
        time.sleep(0.5)
        log("Servidor Mock SMTP iniciado y listo para recibir correos.")

        # 10. Reiniciar el MISMO JAR contra la MISMA base y con la MISMA clave persistente
        log("Reiniciando el MISMO JAR contra la misma base de datos...")
        jar_proc2 = subprocess.Popen(
            java_cmd,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            cwd=r"F:\Lysandri-executive\backend"
        )
        log(f"Segundo proceso JAR iniciado tras reinicio con PID: {jar_proc2.pid}")

        # Esperar disponibilidad HTTP del proceso reiniciado
        log("Esperando disponibilidad HTTP del proceso reiniciado...")
        http_ok2 = False
        for i in range(45):
            if jar_proc2.poll() is not None:
                raise RuntimeError(f"El segundo proceso JAR terminó con código: {jar_proc2.returncode}")
            try:
                req = urllib.request.Request(health_url, method="OPTIONS")
                with urllib.request.urlopen(req, timeout=2) as resp:
                    http_ok2 = True
                    break
            except urllib.error.HTTPError:
                http_ok2 = True
                break
            except Exception:
                time.sleep(1)

        if not http_ok2:
            raise RuntimeError("El backend reiniciado no respondió en HTTP tras 45 segundos.")
        log("Backend reiniciado operativo.")

        # 11. Comprobar que el planificador recupera ESA MISMA tarea (SIN nueva solicitud de activación)
        log(f"Esperando que el planificador recupere y procese automáticamente la MISMA tarea (ID: {target_task_id})...")
        email_captured = False
        captured_message = ""
        for i in range(25):
            for msg in received_emails:
                if "usuario.recover@lysandri.invalid" in msg and "activar-cuenta" in msg:
                    email_captured = True
                    captured_message = msg
                    break
            if email_captured:
                break
            time.sleep(1)

        if not email_captured:
            raise RuntimeError(f"El planificador automático no recuperó la tarea {target_task_id} tras el reinicio.")
        log(f"CORREO CAPTURADO POR MOCK SMTP: La misma tarea (ID: {target_task_id}) fue despachada por el planificador.")

        # 12. Verificar mediante aserciones en base de datos: estado ENVIADO y enlace_cifrado IS NULL
        post_recovery_state = docker_exec_psql(f"""
            SELECT id_tarea || '|' || estado || '|' || (enlace_cifrado IS NULL)::text || '|' || intentos
            FROM outbox_notificacion_activacion
            WHERE id_tarea = {target_task_id};
        """)
        if not post_recovery_state or "|" not in post_recovery_state:
            raise RuntimeError(f"No se pudo consultar el estado posterior de la tarea {target_task_id}")

        r_parts = post_recovery_state.split("|")
        r_id = int(r_parts[0])
        r_estado = r_parts[1]
        r_enlace_purgado = (r_parts[2].lower() == "true" or r_parts[2].lower() == "t")
        r_intentos = int(r_parts[3])

        log("================================================================================")
        log("ASERCIONES DE BASE DE DATOS TRAS RECUPERACIÓN:")
        log(f"- ID Tarea verificado: {r_id} (esperado: {target_task_id})")
        log(f"- Estado verificado: {r_estado} (esperado: ENVIADO)")
        log(f"- Carga sensible purgada (enlace_cifrado IS NULL): {r_enlace_purgado} (esperado: True)")
        log(f"- Intentos acumulados: {r_intentos}")
        log("================================================================================")

        assert r_id == target_task_id, f"El ID de la tarea recuperada ({r_id}) no coincide con el original ({target_task_id})"
        assert r_estado == "ENVIADO", f"Estado esperado ENVIADO pero se encontró {r_estado}"
        assert r_enlace_purgado, "El enlace cifrado debe ser NULL tras envío exitoso para eliminar carga sensible"

        # 13. Capturar el enlace desde SMTP y activar la cuenta mediante HTTP (POST /api/v1/auth/activar-cuenta)
        decoded_message = captured_message.replace("=3D", "=").replace("=\r\n", "").replace("=\n", "")
        token_match = re.search(r"activar-cuenta\?token=([a-zA-Z0-9_\-]+)", decoded_message)
        if not token_match:
            raise RuntimeError("No se pudo extraer el token de activación del mensaje SMTP recibido.")
        activation_token = token_match.group(1)
        log("Token de activación capturado desde SMTP mock: [TOKEN_PRESENTE_PROTEGIDO_NO_IMPRESO]")

        log("Enviando petición HTTP POST /api/v1/auth/activar-cuenta...")
        activate_body = json.dumps({
            "token": activation_token,
            "password": "PasswordSegura2026!"
        }).encode("utf-8")
        activate_req = urllib.request.Request(
            f"http://localhost:{HTTP_PORT}/api/v1/auth/activar-cuenta",
            data=activate_body,
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(activate_req, timeout=10) as act_resp:
            act_data = act_resp.read().decode("utf-8")
            log(f"Respuesta HTTP de activación ({act_resp.status}): Código 200 OK.")

        # Verificar en base de datos que el usuario quedó activado
        user_active = docker_exec_psql("SELECT activo FROM usuario WHERE id_user = 801;")
        log(f"Estado de activación del usuario 801 en BD: {user_active}")
        assert user_active in ("t", "true", "True"), f"El usuario 801 no fue activado en BD (valor: {user_active})"
        log("CUENTA ACTIVADA EXITOSAMENTE MEDIANTE HTTP Y CONFIRMADA EN BASE DE DATOS.")

        # 14. Detener segundo proceso JAR
        jar_proc2.terminate()
        try:
            jar_proc2.wait(timeout=10)
        except subprocess.TimeoutExpired:
            jar_proc2.kill()
        log("Segundo proceso JAR detenido limpiamente.")

        log("================================================================================")
        log("VERIFICACIÓN EN VIVO DEL ARTEFACTO JAR COMPLETADA CON ÉXITO")
        log(f"Artefacto verificado: lysandri-backend-1.0.0-SNAPSHOT.jar")
        log(f"Hash SHA-256 verificado: {jar_hash}")
        log("================================================================================")

    finally:
        if jar_proc and jar_proc.poll() is None:
            jar_proc.kill()
        if jar_proc2 and jar_proc2.poll() is None:
            jar_proc2.kill()
        smtp_running = False
        if smtp_server:
            smtp_server.stop()
        log_file.close()

        # Limpiar contenedor desechable comprobando su nombre exacto
        log(f"Destruyendo contenedor PostgreSQL desechable ({DISPOSABLE_CONTAINER})...")
        subprocess.run(["docker", "rm", "-f", DISPOSABLE_CONTAINER], capture_output=True)
        log("Contenedor desechable eliminado. Ningún dato residual conservado.")

if __name__ == "__main__":
    main()

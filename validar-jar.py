#!/usr/bin/env python3
"""
validar-jar.py
Script de validación reproducible e integral del artefacto ejecutable JAR real.
Comprueba autenticación, activación, JWT, IDOR, Outbox, WhatsApp Operador e Idempotencia
contra un PostgreSQL desechable aislado y servidores locales simulados (SMTP y WhatsApp).

Restricciones y salvaguardas de seguridad:
- NO se conecta a localhost:5432/lysandri_db ni a clones persistentes (puerto 5434).
- Aislamiento total: cada ejecución utiliza un contenedor PostgreSQL único, etiquetado y efímero.
- Asigna puertos disponibles comprobando conflictos previamente.
- Sanitiza logs y evidencias (sin tokens, contraseñas ni secretos).
- Finaliza con código de salida no-cero ante cualquier fallo o discrepancia.
- Registra el destino efectivo de PostgreSQL sin exponer credenciales.
- Limpieza garantizada mediante bloques try...finally, actuando exclusivamente sobre los recursos creados.
"""

import base64
import datetime
import hashlib
import http.server
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

# ==============================================================================
# IDENTIFICACIÓN ÚNICA DE EJECUCIÓN Y ASIGNACIÓN DE RECURSOS
# ==============================================================================
BASE_DIR = os.path.abspath(os.path.dirname(__file__))
JAR_PATH = os.path.join(BASE_DIR, "backend", "target", "lysandri-backend-1.0.0-SNAPSHOT.jar")

RUN_STAMP = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
RUN_SUFFIX = uuid.uuid4().hex[:6]
RUN_ID = f"run-{RUN_STAMP}-{RUN_SUFFIX}"

# Carpeta de evidencias única para esta ejecución (sin guiones bajos)
RUN_EVIDENCIAS_DIR = os.path.join(
    BASE_DIR, "backend", "evidencias-auth", f"ejecucion-20261029-cierre-checkout-estable-fase5-{RUN_SUFFIX}"
)
LATEST_EVIDENCIAS_DIR = os.path.join(
    BASE_DIR, "backend", "evidencias-auth", "ejecucion-20261029-cierre-checkout-estable-fase5"
)
DELIVERY_EVIDENCIAS_DIR = os.path.join(
    BASE_DIR, "backend", "evidencias-auth", "ejecucion-20261029-cierre-checkout-estable-fase5"
)

os.makedirs(RUN_EVIDENCIAS_DIR, exist_ok=True)
os.makedirs(LATEST_EVIDENCIAS_DIR, exist_ok=True)
os.makedirs(DELIVERY_EVIDENCIAS_DIR, exist_ok=True)

LOG_FILE_PATH = os.path.join(RUN_EVIDENCIAS_DIR, "registro-ejecucion-validar-jar.log")
JSON_RESULTS_PATH = os.path.join(RUN_EVIDENCIAS_DIR, "recorrido-http-jar-real.json")
JAR_CONSOLE_LOG_PATH = os.path.join(RUN_EVIDENCIAS_DIR, "jar-ejecucion-consola.log")

log_file_handle = open(LOG_FILE_PATH, "w", encoding="utf-8")

def log(msg: str):
    ts = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    sanitized = re.sub(r'eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+', '[REDACTED_JWT]', msg)
    sanitized = re.sub(r'(token=)[a-zA-Z0-9_-]+', r'\1[REDACTED_TOKEN]', sanitized)
    sanitized = re.sub(r'(password[:=]\s*)[^\s,"]+', r'\1[REDACTED_PASS]', sanitized, flags=re.IGNORECASE)
    sanitized = re.sub(r'(passw[:=]\s*)[^\s,"]+', r'\1[REDACTED_PASS]', sanitized, flags=re.IGNORECASE)
    line = f"[{ts}] {sanitized}"
    print(line, flush=True)
    log_file_handle.write(line + "\n")
    log_file_handle.flush()

def find_available_port(preferred_port: int) -> int:
    """Verifica si el puerto preferido está libre; si está ocupado, encuentra uno libre efímero."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("127.0.0.1", preferred_port))
            return preferred_port
        except OSError:
            pass
    # Buscar puerto efímero disponible
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]

# Puertos asignados dinámicamente evitando conflictos
DISPOSABLE_PORT = find_available_port(15445)
APP_HTTP_PORT = find_available_port(18099)
SMTP_MOCK_PORT = find_available_port(1025)
WHATSAPP_MOCK_PORT = find_available_port(19099)

DISPOSABLE_CONTAINER = f"lysandri-pg-{RUN_SUFFIX}"
DISPOSABLE_DB = "lysandri_disposable_jar_db"
DISPOSABLE_USER = "lysandri_test_runner"
DISPOSABLE_PASS = "test_runner_pass"

ACTIVATION_ENCRYPTION_KEY = "test-activation-encryption-key-32chars-strictly-for-testing"
JWT_SECRET_VALID = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A25432A"
JWT_SECRET_TAMPERED = "999E635266556A586E3272357538782F413F4428472B4B6250645367566B5970337336763979244226452948404D6351655468576D5A7134743777217A254399"

def compute_sha256(filepath: str) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest().upper()

def run_cmd(cmd_list, timeout=60, check=True):
    p = subprocess.run(cmd_list, capture_output=True, text=True, timeout=timeout)
    if check and p.returncode != 0:
        raise RuntimeError(f"Comando falló (exit {p.returncode}): {p.stderr}\nStdout: {p.stdout}")
    return p

created_container_id = None

def docker_exec_psql(sql: str) -> str:
    target = created_container_id if created_container_id else DISPOSABLE_CONTAINER
    cmd = [
        "docker", "exec", target,
        "psql", "-U", DISPOSABLE_USER, "-d", DISPOSABLE_DB,
        "-t", "-A", "-c", sql
    ]
    p = run_cmd(cmd, timeout=30)
    return p.stdout.strip()

# ==============================================================================
# SERVIDOR SMTP SIMULADO (PUERTO AISLADO)
# ==============================================================================
received_emails = []
smtp_active = True

class MockSmtpServer(threading.Thread):
    def __init__(self, port):
        super().__init__(daemon=True)
        self.port = port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(("127.0.0.1", port))
        self.sock.listen(5)
        self._running = True

    def run(self):
        while self._running:
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
            client.sendall(b"220 localhost SimpleMockSmtp ready\r\n")
            rfile = client.makefile("r", encoding="utf-8", errors="replace")
            wfile = client.makefile("w", encoding="utf-8", errors="replace")

            in_data = False
            msg_lines = []
            while True:
                line = rfile.readline()
                if not line:
                    break
                line = line.rstrip("\r\n")
                if in_data:
                    if line == ".":
                        in_data = False
                        full_msg = "\n".join(msg_lines)
                        received_emails.append(full_msg)
                        wfile.write("250 2.0.0 OK: message queued\r\n")
                        wfile.flush()
                    else:
                        msg_lines.append(line)
                else:
                    upper = line.strip().upper()
                    if upper.startswith("HELO") or upper.startswith("EHLO"):
                        wfile.write("250-localhost Hello\r\n250 OK\r\n")
                        wfile.flush()
                    elif upper.startswith("MAIL FROM:"):
                        wfile.write("250 2.1.0 OK\r\n")
                        wfile.flush()
                    elif upper.startswith("RCPT TO:"):
                        wfile.write("250 2.1.5 OK\r\n")
                        wfile.flush()
                    elif upper == "DATA":
                        in_data = True
                        msg_lines = []
                        wfile.write("354 Start mail input; end with <CRLF>.<CRLF>\r\n")
                        wfile.flush()
                    elif upper == "QUIT":
                        wfile.write("221 2.0.0 Bye\r\n")
                        wfile.flush()
                        break
                    elif upper.startswith("RSET") or upper.startswith("NOOP"):
                        wfile.write("250 OK\r\n")
                        wfile.flush()
                    else:
                        wfile.write("250 OK\r\n")
                        wfile.flush()
        except Exception:
            pass
        finally:
            try:
                client.close()
            except Exception:
                pass

    def stop(self):
        self._running = False
        try:
            self.sock.close()
        except Exception:
            pass

# ==============================================================================
# SERVIDOR SIMULADO DE WHATSAPP CLOUD API (PUERTO AISLADO)
# ==============================================================================
whatsapp_calls = []
restart_hold_event = threading.Event()
restart_received_event = threading.Event()

class MockWhatsAppServer(threading.Thread):
    def __init__(self, port):
        super().__init__(daemon=True)
        self.port = port
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(("127.0.0.1", port))
        self.sock.listen(10)
        self._running = True

    def run(self):
        while self._running:
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
            client.settimeout(10.0)
            data = b""
            while b"\r\n\r\n" not in data:
                chunk = client.recv(1024)
                if not chunk:
                    break
                data += chunk

            header_part, _, body_part = data.partition(b"\r\n\r\n")
            header_str = header_part.decode("utf-8", errors="replace")
            content_len = 0
            for line in header_str.split("\r\n"):
                if line.lower().startswith("content-length:"):
                    content_len = int(line.split(":", 1)[1].strip())
                    break

            while len(body_part) < content_len:
                chunk = client.recv(1024)
                if not chunk:
                    break
                body_part += chunk

            body_str = body_part.decode("utf-8", errors="replace")
            path_part = header_str.split(" ")[1] if " " in header_str else "/messages"
            whatsapp_calls.append({"path": path_part, "body": body_str})

            if "req-restart-" in body_str:
                restart_received_event.set()
                # Retener la respuesta de red para permitir interrumpir el proceso Java con la intención guardada en EN_PROCESO_ENVIO
                restart_hold_event.wait(timeout=6)

            res_body = json.dumps({
                "messaging_product": "whatsapp",
                "messages": [{"id": f"wamid.test_live_jar_{len(whatsapp_calls)}"}]
            }).encode("utf-8")

            res = (
                f"HTTP/1.1 200 OK\r\n"
                f"Content-Type: application/json; charset=utf-8\r\n"
                f"Content-Length: {len(res_body)}\r\n"
                f"Connection: close\r\n\r\n"
            ).encode("utf-8") + res_body

            try:
                client.sendall(res)
                client.shutdown(socket.SHUT_WR)
                client.settimeout(0.5)
                while client.recv(1024):
                    pass
            except Exception:
                pass
        finally:
            try:
                client.close()
            except Exception:
                pass

    def stop(self):
        self._running = False
        try:
            self.sock.close()
        except Exception:
            pass

# ==============================================================================
# CLIENTE HTTP PARA PRUEBAS
# ==============================================================================
def make_http_request(url: str, method="GET", payload=None, token=None, extra_headers=None) -> tuple[int, dict]:
    headers = {
        "Accept": "application/json"
    }
    if extra_headers:
        headers.update(extra_headers)
    data_bytes = None
    if payload is not None:
        data_bytes = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"

    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, data=data_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            status = resp.status
            body_str = resp.read().decode("utf-8", errors="replace")
            try:
                body_json = json.loads(body_str) if body_str else {}
            except Exception:
                body_json = {"raw": body_str}
            return status, body_json
    except urllib.error.HTTPError as ex:
        status = ex.code
        body_str = ex.read().decode("utf-8", errors="replace")
        try:
            body_json = json.loads(body_str) if body_str else {}
        except Exception:
            body_json = {"raw": body_str}
        return status, body_json

# ==============================================================================
# MATRIZ PREDEFINIDA DE ESCENARIOS A VERIFICAR (28 PASOS)
# ==============================================================================
PLANNED_STEPS = [
    "Inicio de sesión exitoso de Administrador",
    "Rechazo de contraseña incorrecta con mensaje uniforme",
    "Rechazo de cuenta inexistente con mensaje idéntico (anti-enumeración)",
    "Creación de orden checkout para invitado con token previo",
    "Confirmación de pago de orden y disparo de outbox de activación",
    "Recepción de correo de activación en Mock SMTP",
    "Rechazo de login para cuenta pendiente de activación con mensaje uniforme",
    "Activación de cuenta con token válido y establecimiento de contraseña",
    "Rechazo de token de activación ya consumido (anti-reuso)",
    "Rechazo de token manipulado / alterado",
    "Rechazo del hash de BD presentado directamente como token",
    "Inicio de sesión exitoso de Usuario 1 activado",
    "Registro de Usuario 2",
    "Administrador desactiva cuenta de Usuario 2",
    "Rechazo de login para cuenta desactivada con mensaje uniforme",
    "Rechazo de petición no autenticada a ruta protegida",
    "Rechazo de JWT manipulado / firma inválida",
    "Rechazo de JWT firmado con clave distinta",
    "Usuario común bloqueado de endpoints administrativos (HTTP 403)",
    "Administrador autorizado accede a endpoints administrativos (HTTP 200)",
    "Control IDOR: Usuario 1 consulta su propio perfil legítimamente (HTTP 200)",
    "Control IDOR: Usuario 1 intenta leer datos de Usuario 2 cambiando ID (HTTP 403)",
    "Control IDOR: Usuario 1 intenta modificar perfil de Usuario 2 (HTTP 403)",
    "Integridad en BD: Verificación de que la modificación IDOR rechazada no se aplicó",
    "Verificación en BD: Tarea de Outbox en estado ENVIADO y enlace cifrado purgado",
    "WhatsApp Operador: Usuario común bloqueado de endpoints de operador (HTTP 403)",
    "WhatsApp Operador: Administrador envía mensaje oficial con idSolicitud",
    "WhatsApp Idempotencia: Petición repetida no duplica envío al proveedor",
    "WhatsApp Integridad: Petición repetida con diferente contenido es rechazada (HTTP 400)",
    "WhatsApp Reinicio Real: Recuperación a RESULTADO_INCIERTO tras muerte del proceso JVM",
    "WhatsApp Reinicio Real: Solicitud en RESULTADO_INCIERTO rechaza nuevo despacho a la red",
    "WhatsApp Reinicio Real: Webhook autenticado concilia solicitud recuperada",
    "WhatsApp Reinicio Real: Estado final ENTREGADO y llamadas totales al proveedor igual a 1",
    "Cuentas Bancarias: Consulta pública retorna cuentas oficiales autorizadas (HTTP 200)",
    "Transferencia Manual: Creación de orden para transferencia bancaria en USD",
    "Idempotencia Órdenes: Reintento transparente recupera la misma orden sin duplicar en BD",
    "Idempotencia Órdenes: Intento de reuso de clave con datos divergentes es rechazado (HTTP 409)",
    "Idempotencia Órdenes: Aislamiento por comprador y prevención IDOR de clave (HTTP 403)",
    "Validación Método de Pago: Creación con método no permitido es rechazada (HTTP 400)",
    "Aprobación Manual BCP: Aprobación oficial con cuenta configurada y ceros a la izquierda en operación",
    "Detección Duplicados: Bloqueo de segundo abono con misma operación normalizada (anti-reuso)",
    "Validación Cuenta Destino: Rechazo de abono hacia cuenta bancaria no configurada o ajena (HTTP 400)",
    "Billeteras Digitales: Creación y aprobación con Yape en moneda PEN",
    "Seguridad de Pasarela: Rechazo de aprobación manual sobre orden de pasarela en línea (HTTP 400)",
    "Anti-reuso Variantes: Bloqueo de abono con variante de nombre de banco o CCI (HTTP 409)",
    "Compatibilidad Método/Destino: Rechazo de método y destino incompatibles o banco no aprobado (HTTP 400)",
    "Contexto Invitado: Validación de token criptográfico y rechazo 403 ante ausencia o manipulación",
    "Contexto Invitado Reinicio: Recuperación de orden de invitado tras reinicio real de JVM (DB count=1)"
]

def launch_jar_backend(port: int, log_file_path: str):
    log(f"[JAR] Iniciando backend JAR en puerto {port}...")
    log_f = open(log_file_path, "w", encoding="utf-8")
    java_cmd = [
        "java",
        "-Dspring.profiles.active=test",
        f"-Dserver.port={port}",
        f"-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:{DISPOSABLE_PORT}/{DISPOSABLE_DB}",
        f"-Dspring.datasource.username={DISPOSABLE_USER}",
        f"-Dspring.datasource.password={DISPOSABLE_PASS}",
        f"-Dspring.flyway.url=jdbc:postgresql://127.0.0.1:{DISPOSABLE_PORT}/{DISPOSABLE_DB}",
        f"-Dspring.flyway.user={DISPOSABLE_USER}",
        f"-Dspring.flyway.password={DISPOSABLE_PASS}",
        "-Dspring.mail.host=127.0.0.1",
        f"-Dspring.mail.port={SMTP_MOCK_PORT}",
        "-Dapp.scheduling.enabled=true",
        "-Dapp.outbox.activation.enabled=true",
        "-Dapp.outbox.activation.fixed-delay-ms=500",
        f"-Dapp.security.activation-encryption-key={ACTIVATION_ENCRYPTION_KEY}",
        f"-Djwt.secret={JWT_SECRET_VALID}",
        "-Dwhatsapp.enabled=true",
        f"-Dwhatsapp.api-url=http://127.0.0.1:{WHATSAPP_MOCK_PORT}",
        "-Dwhatsapp.phone-number-id=109876543210",
        "-Dwhatsapp.access-token=mock_access_token_valido",
        "-Dwhatsapp.verify-token=mock_verify_token_seguro",
        "-Dwhatsapp.app-secret=mock_app_secret_para_firma",
        "-Dapp.bancos.bcp.banco=BCP",
        "-Dapp.bancos.bcp.titular=LYSANDRI S.A.C.",
        "-Dapp.bancos.bcp.moneda=USD",
        "-Dapp.bancos.bcp.tipo-cuenta=CORRIENTE",
        "-Dapp.bancos.bcp.numero-cuenta=193-98218491-0-28",
        "-Dapp.bancos.bcp.cci=002-193-009821849102-18",
        "-Dapp.bancos.bcp.activo=true",
        "-Dapp.bancos.interbank.banco=INTERBANK",
        "-Dapp.bancos.interbank.titular=LYSANDRI S.A.C.",
        "-Dapp.bancos.interbank.moneda=USD",
        "-Dapp.bancos.interbank.tipo-cuenta=CORRIENTE",
        "-Dapp.bancos.interbank.numero-cuenta=200-3001849128",
        "-Dapp.bancos.interbank.cci=003-200-003001849128-33",
        "-Dapp.bancos.interbank.activo=true",
        "-Dapp.bancos.yape.nombre=YAPE",
        "-Dapp.bancos.yape.titular=LYSANDRI S.A.C.",
        "-Dapp.bancos.yape.moneda=PEN",
        "-Dapp.bancos.yape.telefono=987654321",
        "-Dapp.bancos.yape.activo=true",
        "-Dapp.bancos.plin.nombre=PLIN",
        "-Dapp.bancos.plin.titular=LYSANDRI S.A.C.",
        "-Dapp.bancos.plin.moneda=PEN",
        "-Dapp.bancos.plin.telefono=987654322",
        "-Dapp.bancos.plin.activo=true",
        "-jar", JAR_PATH
    ]
    proc = subprocess.Popen(
        java_cmd,
        stdout=log_f,
        stderr=subprocess.STDOUT,
        cwd=os.path.join(BASE_DIR, "backend")
    )
    log(f"[JAR] Proceso hijo iniciado con PID: {proc.pid}")
    log(f"[JAR] Esperando disponibilidad HTTP en http://127.0.0.1:{port}...")
    ready = False
    health_url = f"http://127.0.0.1:{port}/api/v1/auth/solicitar-activacion"
    for _ in range(45):
        if proc.poll() is not None:
            raise RuntimeError(f"El proceso JAR terminó prematuramente con código: {proc.returncode}. Verifique {log_file_path}")
        try:
            with urllib.request.urlopen(health_url, timeout=1) as r:
                ready = True
                break
        except urllib.error.HTTPError as ex:
            if ex.code in (400, 401, 403, 404, 405):
                ready = True
                break
        except Exception:
            time.sleep(1)
    if not ready:
        raise RuntimeError(f"El backend JAR no respondió peticiones HTTP en el puerto {port}.")
    log(f"[JAR] Backend JAR completamente inicializado en puerto {port}.")
    return proc, log_f

# ==============================================================================
# EJECUCIÓN PRINCIPAL DE VALIDACIÓN
# ==============================================================================
def main():
    global created_container_id
    log("================================================================================")
    log(f"INICIO DE VALIDACIÓN REPRODUCIBLE DEL ARTEFACTO JAR (validar-jar.py)")
    log(f"Identificador único de ejecución: {RUN_ID}")
    log(f"Carpeta de evidencias propia: {RUN_EVIDENCIAS_DIR}")
    log("================================================================================")

    # 1. Verificar existencia del JAR y calcular SHA-256
    if not os.path.isfile(JAR_PATH):
        raise FileNotFoundError(f"Artefacto JAR no encontrado en: {JAR_PATH}. Ejecute 'mvn package -DskipTests' primero.")
    jar_sha256 = compute_sha256(JAR_PATH)
    log(f"[ARTEFACTO] JAR localizado: {JAR_PATH}")
    log(f"[ARTEFACTO] SHA-256 del JAR probado: {jar_sha256}")

    smtp_server = None
    whatsapp_server = None
    jar_proc = None
    jar_log_file = None
    jar_proc_2 = None
    jar_log_file_2 = None

    test_results = []
    executed_step_names = set()

    def record_step(nombre, tipo, esperado, obtenido, exito, detalles=""):
        status_str = "APROBADO" if exito else "FALLIDO"
        log(f"[{tipo}] {nombre} -> {status_str} (HTTP {obtenido} / esperado {esperado}) {detalles}")
        test_results.append({
            "paso": len(test_results) + 1,
            "nombre": nombre,
            "tipo": tipo,
            "esperado": str(esperado),
            "obtenido": str(obtenido),
            "resultado": status_str,
            "detalles": detalles
        })
        executed_step_names.add(nombre)
        if not exito:
            raise AssertionError(f"Fallo en paso '{nombre}': esperado={esperado}, obtenido={obtenido}. {detalles}")

    try:
        # 2. Levantar servidor Mock SMTP en puerto aislado
        log(f"[INFRAESTRUCTURA] Iniciando servidor Mock SMTP local en puerto {SMTP_MOCK_PORT}...")
        smtp_server = MockSmtpServer(SMTP_MOCK_PORT)
        smtp_server.start()

        # 3. Levantar servidor Mock WhatsApp en puerto aislado
        log(f"[INFRAESTRUCTURA] Iniciando servidor Mock WhatsApp Cloud API en puerto {WHATSAPP_MOCK_PORT}...")
        whatsapp_server = MockWhatsAppServer(WHATSAPP_MOCK_PORT)
        whatsapp_server.start()

        # 4. Iniciar PostgreSQL desechable aislado y etiquetado
        log(f"[INFRAESTRUCTURA] Lanzando contenedor PostgreSQL desechable ({DISPOSABLE_CONTAINER}) en puerto {DISPOSABLE_PORT}...")
        docker_res = run_cmd([
            "docker", "run", "-d",
            "--name", DISPOSABLE_CONTAINER,
            "--label", f"lysandri-run-id={RUN_ID}",
            "-e", f"POSTGRES_DB={DISPOSABLE_DB}",
            "-e", f"POSTGRES_USER={DISPOSABLE_USER}",
            "-e", f"POSTGRES_PASSWORD={DISPOSABLE_PASS}",
            "-p", f"{DISPOSABLE_PORT}:5432",
            "pgvector/pgvector:pg16"
        ])
        created_container_id = docker_res.stdout.strip()
        log(f"[INFRAESTRUCTURA] Contenedor creado con ID: {created_container_id[:12]}")

        log("[INFRAESTRUCTURA] Esperando a que PostgreSQL desechable esté listo...")
        ready = False
        for _ in range(35):
            res = subprocess.run(
                ["docker", "exec", created_container_id, "pg_isready", "-U", DISPOSABLE_USER, "-d", DISPOSABLE_DB],
                capture_output=True, text=True
            )
            if res.returncode == 0:
                ready = True
                break
            time.sleep(1)
        if not ready:
            raise RuntimeError("PostgreSQL desechable no respondió dentro del tiempo límite.")
        log(f"[DB] Destino efectivo PostgreSQL: 127.0.0.1:{DISPOSABLE_PORT}/{DISPOSABLE_DB} (usuario: {DISPOSABLE_USER}, contrasena: [PROTEGIDA])")

        # Pre-crear roles requeridos por V25 si no existen
        log("[DB] Inicializando roles administrativos mínimos para migración V25...")
        docker_exec_psql("DO $$ BEGIN "
                         "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lysandri_admin') THEN CREATE ROLE lysandri_admin; END IF; "
                         "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lysandri_app') THEN CREATE ROLE lysandri_app; END IF; "
                         "END $$;")

        # 5. Iniciar Proceso JAR Real
        jar_proc, jar_log_file = launch_jar_backend(APP_HTTP_PORT, JAR_CONSOLE_LOG_PATH)
        log("[JAR] Backend JAR completamente inicializado y aceptando peticiones HTTP.")

        # Inicialización de catálogo académico mínimo para compras
        log("[DB] Configurando datos base en base desechable...")
        docker_exec_psql(
            "INSERT INTO programa (id_programa, moodle_course_id, titulo, slug, precio, moneda, activo, precio_aprobado) "
            "VALUES (101, 101, 'Curso Ejecutivo de Alta Dirección', 'curso-ejecutivo-alta-direccion', 150.00, 'PEN', true, true) "
            "ON CONFLICT (id_programa) DO UPDATE SET precio_aprobado = true, activo = true;"
        )
        docker_exec_psql(
            "INSERT INTO programa (id_programa, moodle_course_id, titulo, slug, precio, moneda, activo, precio_aprobado) "
            "VALUES (102, 102, 'Programa Internacional Ejecutivo', 'programa-internacional-ejecutivo', 200.00, 'USD', true, true) "
            "ON CONFLICT (id_programa) DO UPDATE SET precio_aprobado = true, activo = true;"
        )

        base_url = f"http://127.0.0.1:{APP_HTTP_PORT}"

        # ----------------------------------------------------------------------
        # PROVISIÓN SEGURA: Administrador
        # ----------------------------------------------------------------------
        log("[PROVISIÓN] Registrando cuenta administrativa con hash BCrypt nativo de Spring Security...")
        admin_email = "danny@lysandri.invalid"
        admin_pass = "AdminLysandriSecure2026!"
        st_reg, res_reg = make_http_request(
            f"{base_url}/api/v1/auth/register",
            method="POST",
            payload={
                "nombres": "Danny",
                "apellidos": "Administrador",
                "email": admin_email,
                "password": admin_pass,
                "telefono": "+51999888001"
            }
        )
        docker_exec_psql(
            f"UPDATE usuario SET rol = 'ADMIN', activo = true, bloqueado_admin = false, pendiente_activacion = false "
            f"WHERE email = '{admin_email}';"
        )
        log("[PROVISIÓN] Cuenta elevada a ADMIN y habilitada correctamente en BD.")

        # ----------------------------------------------------------------------
        # PASO 1: Login exitoso de Administrador
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/login",
            method="POST",
            payload={"email": admin_email, "password": admin_pass}
        )
        admin_token = res.get("token")
        record_step(
            "Inicio de sesión exitoso de Administrador",
            "HTTP",
            200, st,
            st == 200 and admin_token is not None,
            "Retorna HTTP 200 y token JWT emitido por el servidor"
        )

        # ----------------------------------------------------------------------
        # PASO 2: Contraseña incorrecta -> Mensaje uniforme 401
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/login",
            method="POST",
            payload={"email": admin_email, "password": "WrongPassword999!"}
        )
        msg_err_pass = res.get("message") or res.get("mensaje", "")
        record_step(
            "Rechazo de contraseña incorrecta con mensaje uniforme",
            "HTTP",
            401, st,
            st == 401 and "No se pudo iniciar sesión" in msg_err_pass,
            f"Mensaje recibido: '{msg_err_pass}'"
        )

        # ----------------------------------------------------------------------
        # PASO 3: Cuenta inexistente -> Mensaje uniforme e idéntico 401
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/login",
            method="POST",
            payload={"email": "noexiste.cuenta.fantasma@lysandri.invalid", "password": "CualquierPass123!"}
        )
        msg_err_inex = res.get("message") or res.get("mensaje", "")
        record_step(
            "Rechazo de cuenta inexistente con mensaje idéntico (anti-enumeración)",
            "HTTP",
            401, st,
            st == 401 and msg_err_inex == msg_err_pass,
            f"Mensaje uniforme verificado: '{msg_err_inex}'"
        )

        # ----------------------------------------------------------------------
        # PASO 4: Checkout Invitado -> Creación de Orden con Autorización Previa
        # ----------------------------------------------------------------------
        guest_email = "estudiante.invitado@lysandri.invalid"
        # 4a. Intento sin token previo es rechazado (HTTP 400)
        st_no_tok, res_no_tok = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload={
                "nombres": "Estudiante",
                "apellidos": "Invitado",
                "email": guest_email,
                "telefono": "+51999888777",
                "tipoComprobante": "BOLETA",
                "programaIds": [101]
            }
        )

        # 4b. Obtención de token criptográfico de sesión previa de compra
        st_gs, res_gs = make_http_request(
            f"{base_url}/api/v1/orders/guest-session",
            method="POST",
            payload={"email": guest_email}
        )
        guest_init_token = res_gs.get("guestSessionToken") if st_gs == 200 else ""

        # 4c. Creación de la orden presentando la credencial previa
        st, res = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload={
                "nombres": "Estudiante",
                "apellidos": "Invitado",
                "email": guest_email,
                "telefono": "+51999888777",
                "tipoComprobante": "BOLETA",
                "programaIds": [101],
                "guestSessionToken": guest_init_token
            },
            extra_headers={"X-Guest-Session-Token": guest_init_token}
        )
        session_id = res.get("sessionId") or res.get("stripeSessionId") or res.get("codigoOrden")
        order_code = res.get("codigoOrden")
        record_step(
            "Creación de orden checkout para invitado con token previo",
            "HTTP",
            200, st,
            st == 200 and session_id is not None and st_no_tok == 400 and st_gs == 200,
            f"Orden checkout creada con Session ID: {session_id} (Rechazo sin token comprobado: HTTP {st_no_tok})"
        )

        # ----------------------------------------------------------------------
        # PASO 5: Confirmación de pago y encolado en Outbox
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/orders/confirm/{session_id}",
            method="POST"
        )
        record_step(
            "Confirmación de pago de orden y disparo de outbox de activación",
            "HTTP",
            200, st,
            st == 200,
            "Pago confirmado. Evento de activación encolado en outbox."
        )

        # ----------------------------------------------------------------------
        # PASO 6: Mock SMTP recibe correo de activación
        # ----------------------------------------------------------------------
        log("[MAIL] Esperando entrega de correo de activación en servidor Mock SMTP local...")
        activation_email = None
        for _ in range(15):
            for m in reversed(received_emails):
                if "activar-cuenta" in m or "Activa tu cuenta" in m:
                    activation_email = m
                    break
            if activation_email:
                break
            time.sleep(1)

        record_step(
            "Recepción de correo de activación en Mock SMTP",
            "MAIL",
            "EMAIL_RECIBIDO",
            "EMAIL_RECIBIDO" if activation_email else "TIMEOUT_SIN_EMAIL",
            activation_email is not None,
            "Correo capturado por el outbox worker sin tocar servicios externos"
        )

        # Extraer token de activación de la URL decodificando quoted-printable
        import quopri
        decoded_email = quopri.decodestring(activation_email.encode("utf-8")).decode("utf-8", errors="replace")
        decoded_email = decoded_email.replace("=\r\n", "").replace("=\n", "").replace("token=3D", "token=")
        token_match = re.search(r'token=([a-zA-Z0-9_\-]+)', decoded_email)
        if not token_match:
            token_match = re.search(r'token=([a-zA-Z0-9_\-]+)', activation_email.replace("token=3D", "token="))
        if not token_match:
            raise ValueError("No se encontró el parámetro token en el cuerpo del correo de activación recibido.")
        valid_raw_token = token_match.group(1)
        log(f"[MAIL] Token de activación válido capturado (longitud {len(valid_raw_token)} chars).")

        # ----------------------------------------------------------------------
        # PASO 7: Cuenta pendiente rechazada con mensaje uniforme 401
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/login",
            method="POST",
            payload={"email": guest_email, "password": "CualquierPass123!"}
        )
        msg_err_pend = res.get("message") or res.get("mensaje", "")
        record_step(
            "Rechazo de login para cuenta pendiente de activación con mensaje uniforme",
            "HTTP",
            401, st,
            st == 401 and msg_err_pend == msg_err_pass,
            f"Mensaje uniforme verificado: '{msg_err_pend}'"
        )

        # ----------------------------------------------------------------------
        # PASO 8: Activación de cuenta con token válido y contraseña
        # ----------------------------------------------------------------------
        user1_new_pass = "EstudianteSeguro2026!"
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/activar-cuenta",
            method="POST",
            payload={"token": valid_raw_token, "password": user1_new_pass}
        )
        user1_token = res.get("token")
        record_step(
            "Activación de cuenta con token válido y establecimiento de contraseña",
            "HTTP",
            200, st,
            st == 200 and user1_token is not None,
            "Cuenta activada y token de sesión retornado"
        )

        # ----------------------------------------------------------------------
        # PASO 9: Anti-reuso - Token consumido es rechazado
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/activar-cuenta",
            method="POST",
            payload={"token": valid_raw_token, "password": "OtraPassword2026!"}
        )
        record_step(
            "Rechazo de token de activación ya consumido (anti-reuso)",
            "HTTP",
            409, st,
            st == 409,
            "Retorna HTTP 409 Conflict ante intento de reusar token consumido"
        )

        # ----------------------------------------------------------------------
        # PASO 10: Token manipulado / alterado rechazado
        # ----------------------------------------------------------------------
        tampered_token = valid_raw_token[:-4] + "XXXX"
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/activar-cuenta",
            method="POST",
            payload={"token": tampered_token, "password": "OtraPassword2026!"}
        )
        record_step(
            "Rechazo de token manipulado / alterado",
            "HTTP",
            400, st,
            st == 400,
            "Retorna HTTP 400 Bad Request ante token con firma/caracteres corruptos"
        )

        # ----------------------------------------------------------------------
        # PASO 11: Hash de base de datos no sustituye al token en claro
        # ----------------------------------------------------------------------
        db_hash = docker_exec_psql("SELECT token FROM usuario_activacion_token ORDER BY id_token DESC LIMIT 1;")
        log(f"[DB] Hash SHA-256 recuperado de la base de datos (longitud {len(db_hash)}).")
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/activar-cuenta",
            method="POST",
            payload={"token": db_hash, "password": "OtraPassword2026!"}
        )
        record_step(
            "Rechazo del hash de BD presentado directamente como token",
            "HTTP",
            400, st,
            st == 400,
            "El hash almacenado no puede utilizarse como sustituto del token en claro"
        )

        # ----------------------------------------------------------------------
        # PASO 12: Login con nueva contraseña de cuenta activada
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/login",
            method="POST",
            payload={"email": guest_email, "password": user1_new_pass}
        )
        user1_token = res.get("token")
        record_step(
            "Inicio de sesión exitoso de Usuario 1 activado",
            "HTTP",
            200, st,
            st == 200 and user1_token is not None,
            "Sesión iniciada correctamente con la nueva contraseña"
        )

        # ----------------------------------------------------------------------
        # PASO 13: Registro de Usuario 2
        # ----------------------------------------------------------------------
        user2_email = "usuario2.segundo@lysandri.invalid"
        user2_pass = "Usuario2Password2026!"
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/register",
            method="POST",
            payload={
                "nombres": "Segundo",
                "apellidos": "Usuario",
                "email": user2_email,
                "password": user2_pass,
                "telefono": "+51999888002"
            }
        )
        record_step(
            "Registro de Usuario 2",
            "HTTP",
            201, st,
            st == 201,
            "Usuario 2 registrado exitosamente"
        )

        user1_id = int(docker_exec_psql(f"SELECT id_user FROM usuario WHERE email = '{guest_email}';"))
        user2_id = int(docker_exec_psql(f"SELECT id_user FROM usuario WHERE email = '{user2_email}';"))
        log(f"[DB] IDs asignados en BD: Usuario 1 = {user1_id}, Usuario 2 = {user2_id}")

        # ----------------------------------------------------------------------
        # PASO 14: Administrador desactiva cuenta de Usuario 2
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios/{user2_id}/desactivar",
            method="PATCH",
            token=admin_token
        )
        record_step(
            "Administrador desactiva cuenta de Usuario 2",
            "HTTP",
            200, st,
            st == 200,
            "Usuario 2 marcado como inactivo/desactivado por la administración"
        )

        # ----------------------------------------------------------------------
        # PASO 15: Cuenta desactivada rechazada con mensaje uniforme 401
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/auth/login",
            method="POST",
            payload={"email": user2_email, "password": user2_pass}
        )
        msg_err_desact = res.get("message") or res.get("mensaje", "")
        record_step(
            "Rechazo de login para cuenta desactivada con mensaje uniforme",
            "HTTP",
            401, st,
            st == 401 and msg_err_desact == msg_err_pass,
            f"Mensaje uniforme verificado: '{msg_err_desact}'"
        )

        # ----------------------------------------------------------------------
        # PASO 16: Petición sin autenticación rechazada 401
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios/{user1_id}",
            method="GET"
        )
        record_step(
            "Rechazo de petición no autenticada a ruta protegida",
            "HTTP",
            401, st,
            st == 401,
            "Ruta protegida rechaza peticiones sin cabecera Authorization"
        )

        # ----------------------------------------------------------------------
        # PASO 17: JWT manipulado / firma inválida rechazado 401
        # ----------------------------------------------------------------------
        parts = user1_token.split(".")
        tampered_jwt = parts[0] + "." + parts[1] + ".FIRMA_FALSIFICADA_TOTALMENTE_INVALIDA"
        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios/{user1_id}",
            method="GET",
            token=tampered_jwt
        )
        record_step(
            "Rechazo de JWT manipulado / firma inválida",
            "HTTP",
            401, st,
            st == 401,
            "Filtro de seguridad rechaza JWT con firma alterada"
        )

        # ----------------------------------------------------------------------
        # PASO 18: JWT firmado con clave distinta rechazado 401
        # ----------------------------------------------------------------------
        import hmac
        header_b64 = parts[0]
        payload_b64 = parts[1]
        unsigned_token = f"{header_b64}.{payload_b64}"
        key_bytes = bytes.fromhex(JWT_SECRET_TAMPERED)
        sig = hmac.new(key_bytes, unsigned_token.encode("utf-8"), hashlib.sha256).digest()
        sig_b64 = base64.urlsafe_b64encode(sig).decode("utf-8").rstrip("=")
        wrong_key_jwt = f"{unsigned_token}.{sig_b64}"

        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios/{user1_id}",
            method="GET",
            token=wrong_key_jwt
        )
        record_step(
            "Rechazo de JWT firmado con clave distinta",
            "HTTP",
            401, st,
            st == 401,
            "El servidor rechaza tokens firmados con llaves no autorizadas"
        )

        # ----------------------------------------------------------------------
        # PASO 19: Usuario común bloqueado de endpoints de administrador 403
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios",
            method="GET",
            token=user1_token
        )
        record_step(
            "Usuario común bloqueado de endpoints administrativos (HTTP 403)",
            "HTTP",
            403, st,
            st == 403,
            "Usuario CLIENTE no tiene privilegios para acceder a /api/v1/usuarios"
        )

        # ----------------------------------------------------------------------
        # PASO 20: Administrador accede a endpoints de administrador 200
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios",
            method="GET",
            token=admin_token
        )
        record_step(
            "Administrador autorizado accede a endpoints administrativos (HTTP 200)",
            "HTTP",
            200, st,
            st == 200 and isinstance(res, list),
            f"Se listaron {len(res) if isinstance(res, list) else 0} usuarios correctamente"
        )

        # ----------------------------------------------------------------------
        # PASO 21: IDOR - Usuario 1 consulta su propio perfil legítimamente 200
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios/{user1_id}",
            method="GET",
            token=user1_token
        )
        record_step(
            "Control IDOR: Usuario 1 consulta su propio perfil legítimamente (HTTP 200)",
            "HTTP",
            200, st,
            st == 200 and res.get("email") == guest_email,
            "Acceso concedido al propietario de la cuenta"
        )

        # ----------------------------------------------------------------------
        # PASO 22: IDOR - Usuario 1 intenta leer datos de Usuario 2 -> 403
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios/{user2_id}",
            method="GET",
            token=user1_token
        )
        record_step(
            "Control IDOR: Usuario 1 intenta leer datos de Usuario 2 cambiando ID (HTTP 403)",
            "HTTP",
            403, st,
            st == 403,
            "Lectura cruzada entre usuarios bloqueada por control de autorización"
        )

        # ----------------------------------------------------------------------
        # PASO 23: IDOR - Usuario 1 intenta modificar perfil de Usuario 2 -> 403
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/usuarios/{user2_id}",
            method="PUT",
            payload={"nombres": "NombreHackeado", "apellidos": "ApellidosHackeados", "telefono": "+51999999999"},
            token=user1_token
        )
        record_step(
            "Control IDOR: Usuario 1 intenta modificar perfil de Usuario 2 (HTTP 403)",
            "HTTP",
            403, st,
            st == 403,
            "Modificación no autorizada rechazada con AccessDeniedException"
        )

        # ----------------------------------------------------------------------
        # PASO 24: Verificación en BD: Nombre de Usuario 2 permanece intacto
        # ----------------------------------------------------------------------
        name_in_db = docker_exec_psql(f"SELECT nombres FROM usuario WHERE id_user = {user2_id};")
        record_step(
            "Integridad en BD: Verificación de que la modificación IDOR rechazada no se aplicó",
            "DB",
            "Segundo",
            name_in_db,
            name_in_db == "Segundo",
            f"El nombre en BD permanece intacto: '{name_in_db}'"
        )

        # ----------------------------------------------------------------------
        # PASO 25: Verificación en BD: Outbox purga secreto efímero tras envío
        # ----------------------------------------------------------------------
        outbox_row = docker_exec_psql(
            f"SELECT estado, (enlace_cifrado IS NULL) FROM outbox_notificacion_activacion "
            f"WHERE destinatario = '{guest_email}' LIMIT 1;"
        )
        outbox_parts = outbox_row.split("|") if "|" in outbox_row else ["", ""]
        record_step(
            "Verificación en BD: Tarea de Outbox en estado ENVIADO y enlace cifrado purgado",
            "DB",
            "ENVIADO|t",
            outbox_row,
            outbox_parts[0] == "ENVIADO" and outbox_parts[1] == "t",
            "Estado es ENVIADO y enlace_cifrado IS NULL (secreto efímero destruido tras envío)"
        )

        # ----------------------------------------------------------------------
        # PASO 26: WhatsApp Operador - Usuario común bloqueado de endpoints 403
        # ----------------------------------------------------------------------
        st, res = make_http_request(
            f"{base_url}/api/v1/whatsapp/mensajes",
            method="POST",
            payload={"destinatario": "51999888777", "mensaje": "Mensaje de prueba"},
            token=user1_token
        )
        record_step(
            "WhatsApp Operador: Usuario común bloqueado de endpoints de operador (HTTP 403)",
            "HTTP",
            403, st,
            st == 403,
            "Acceso restringido exclusivamente a roles ADMIN o TRABAJADOR"
        )

        # ----------------------------------------------------------------------
        # PASO 27: WhatsApp Operador - Administrador envía mensaje con idSolicitud
        # ----------------------------------------------------------------------
        wa_solicitud_id = f"req-test-live-{int(time.time())}"
        st, res = make_http_request(
            f"{base_url}/api/v1/whatsapp/mensajes",
            method="POST",
            payload={
                "idSolicitud": wa_solicitud_id,
                "destinatario": "51999888777",
                "mensaje": "Mensaje oficial del administrador"
            },
            token=admin_token
        )
        wa_msg_id = res.get("id")
        wa_estado = res.get("estado")
        calls_before = len(whatsapp_calls)
        record_step(
            "WhatsApp Operador: Administrador envía mensaje oficial con idSolicitud",
            "HTTP",
            201, st,
            st == 201 and wa_estado == "ENVIADO" and calls_before > 0,
            f"Mensaje creado con ID {wa_msg_id}, estado {wa_estado}, contactó al proveedor simulado"
        )

        # ----------------------------------------------------------------------
        # PASO 28: WhatsApp Operador - Repetición con misma idSolicitud (Idempotencia)
        # ----------------------------------------------------------------------
        st, res_dup = make_http_request(
            f"{base_url}/api/v1/whatsapp/mensajes",
            method="POST",
            payload={
                "idSolicitud": wa_solicitud_id,
                "destinatario": "51999888777",
                "mensaje": "Mensaje oficial del administrador"
            },
            token=admin_token
        )
        calls_after = len(whatsapp_calls)
        record_step(
            "WhatsApp Idempotencia: Petición repetida no duplica envío al proveedor",
            "HTTP",
            201, st,
            st == 201 and calls_after == calls_before and res_dup.get("id") == wa_msg_id,
            f"Proveedor llamado {calls_before} veces (0 llamadas nuevas). Mensaje ID consistente: {res_dup.get('id')}"
        )

        # ----------------------------------------------------------------------
        # PASO 29: WhatsApp Integridad - Reutilización de idSolicitud con diferente contenido -> 400
        # ----------------------------------------------------------------------
        st, res_tampered = make_http_request(
            f"{base_url}/api/v1/whatsapp/mensajes",
            method="POST",
            payload={
                "idSolicitud": wa_solicitud_id,
                "destinatario": "51999888777",
                "mensaje": "Contenido fraudulento alterado"
            },
            token=admin_token
        )
        record_step(
            "WhatsApp Integridad: Petición repetida con diferente contenido es rechazada (HTTP 400)",
            "HTTP",
            400, st,
            st == 400,
            "Rechazado con IllegalArgumentException al intentar reusar idSolicitud con datos diferentes"
        )

        # ======================================================================
        # ESCENARIO 3B: REINICIO REAL DEL PROCESO JAVA CONSERVANDO POSTGRESQL
        # ======================================================================
        log("================================================================================")
        log("[ESCENARIO 3B] INICIO DE PRUEBA DE REINICIO REAL DE PROCESO JAVA CONSERVANDO POSTGRESQL")
        log("================================================================================")
        restart_solicitud_id = f"req-restart-{int(time.time())}"

        # 1. Iniciar un envío en un hilo separado mientras MockWhatsAppHandler retiene la llamada de red
        def trigger_send_async():
            try:
                make_http_request(
                    f"{base_url}/api/v1/whatsapp/mensajes",
                    method="POST",
                    payload={
                        "idSolicitud": restart_solicitud_id,
                        "destinatario": "51999888999",
                        "mensaje": "Mensaje interrumpido por reinicio forzado del proceso"
                    },
                    token=admin_token
                )
            except Exception:
                pass

        t_send = threading.Thread(target=trigger_send_async, daemon=True)
        t_send.start()

        # 2. Esperar a que el mock server reciba la llamada HTTP
        log(f"[REINICIO REAL] Esperando que la petición para {restart_solicitud_id} contacte al servidor mock de WhatsApp...")
        if not restart_received_event.wait(timeout=10):
            raise RuntimeError("El servidor simulado de WhatsApp no recibió la solicitud antes del tiempo límite.")

        # 3. Comprobar en PostgreSQL que la intención está en EN_PROCESO_ENVIO y el token de intento está asignado
        row_en_proceso = docker_exec_psql(
            f"SELECT estado, (token_intento IS NOT NULL) FROM whatsapp_mensaje WHERE id_solicitud = '{restart_solicitud_id}';"
        )
        log(f"[REINICIO REAL] Estado en BD en pleno vuelo de despacho: '{row_en_proceso}'")

        # 4. Detener el proceso del sistema operativo Java #1 (terminación real por SO)
        log(f"[REINICIO REAL] Deteniendo forzosamente el proceso del SO Java #1 (PID {jar_proc.pid}) antes de persistir resultado...")
        jar_proc.terminate()
        try:
            jar_proc.wait(timeout=5)
        except Exception:
            jar_proc.kill()
        restart_hold_event.set()  # Liberar el socket del mock
        log("[REINICIO REAL] Proceso Java #1 finalizado. La base de datos PostgreSQL permanece intacta.")

        # 5. Arrancar un SEGUNDO proceso Java con el MISMO JAR y la MISMA base PostgreSQL desechable
        jar_log_path_2 = os.path.join(RUN_EVIDENCIAS_DIR, "jar-ejecucion-consola-proceso2.log")
        jar_proc_2, jar_log_file_2 = launch_jar_backend(APP_HTTP_PORT, jar_log_path_2)

        # ----------------------------------------------------------------------
        # PASO 30: Recuperación tras reinicio a RESULTADO_INCIERTO
        # ----------------------------------------------------------------------
        estado_post_reinicio = docker_exec_psql(
            f"SELECT estado FROM whatsapp_mensaje WHERE id_solicitud = '{restart_solicitud_id}';"
        )
        record_step(
            "WhatsApp Reinicio Real: Recuperación a RESULTADO_INCIERTO tras muerte del proceso JVM",
            "DB",
            "RESULTADO_INCIERTO",
            estado_post_reinicio,
            estado_post_reinicio == "RESULTADO_INCIERTO",
            "La solicitud fue recuperada conservadoramente como RESULTADO_INCIERTO por el nuevo proceso JVM"
        )

        # ----------------------------------------------------------------------
        # PASO 31: Reintento de solicitud recuperada rechaza nuevo despacho de red
        # ----------------------------------------------------------------------
        st, res_reintento = make_http_request(
            f"{base_url}/api/v1/whatsapp/mensajes",
            method="POST",
            payload={
                "idSolicitud": restart_solicitud_id,
                "destinatario": "51999888999",
                "mensaje": "Mensaje interrumpido por reinicio forzado del proceso"
            },
            token=admin_token
        )
        calls_during_reintento = len([c for c in whatsapp_calls if restart_solicitud_id in c["body"]])
        record_step(
            "WhatsApp Reinicio Real: Solicitud en RESULTADO_INCIERTO rechaza nuevo despacho a la red",
            "HTTP",
            201, st,
            st == 201 and calls_during_reintento == 1 and res_reintento.get("estado") == "RESULTADO_INCIERTO",
            f"No se reenvió a la red. Llamadas al proveedor para esta solicitud se mantuvieron en 1"
        )

        # ----------------------------------------------------------------------
        # PASO 32: Webhook autenticado concilia solicitud recuperada
        # ----------------------------------------------------------------------
        wamid_recuperado = f"wamid.restart.delivered.{int(time.time())}"
        webhook_payload = json.dumps({
            "object": "whatsapp_business_account",
            "entry": [{
                "changes": [{
                    "value": {
                        "statuses": [{
                            "id": wamid_recuperado,
                            "status": "delivered",
                            "timestamp": str(int(time.time())),
                            "biz_opaque_callback_data": restart_solicitud_id
                        }]
                    }
                }]
            }]
        })
        import hmac
        sig_wh = hmac.new(b"mock_app_secret_para_firma", webhook_payload.encode("utf-8"), hashlib.sha256).hexdigest()
        req_wh = urllib.request.Request(
            f"{base_url}/api/v1/whatsapp/webhook",
            data=webhook_payload.encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "X-Hub-Signature-256": f"sha256={sig_wh}"
            },
            method="POST"
        )
        try:
            with urllib.request.urlopen(req_wh, timeout=5) as r:
                st_wh = r.status
        except urllib.error.HTTPError as ex:
            st_wh = ex.code

        record_step(
            "WhatsApp Reinicio Real: Webhook autenticado concilia solicitud recuperada",
            "HTTP",
            200, st_wh,
            st_wh == 200,
            "Webhook procesado exitosamente por el segundo proceso JVM"
        )

        # ----------------------------------------------------------------------
        # PASO 33: Estado final ENTREGADO y llamadas totales == 1
        # ----------------------------------------------------------------------
        row_final = docker_exec_psql(
            f"SELECT estado, id_proveedor FROM whatsapp_mensaje WHERE id_solicitud = '{restart_solicitud_id}';"
        )
        parts_final = row_final.split("|") if "|" in row_final else ["", ""]
        total_provider_calls = len([c for c in whatsapp_calls if restart_solicitud_id in c["body"]])

        record_step(
            "WhatsApp Reinicio Real: Estado final ENTREGADO y llamadas totales al proveedor igual a 1",
            "DB",
            f"ENTREGADO|{wamid_recuperado}|1",
            f"{parts_final[0]}|{parts_final[1]}|{total_provider_calls}",
            parts_final[0] == "ENTREGADO" and parts_final[1] == wamid_recuperado and total_provider_calls == 1,
            f"Estado final ENTREGADO conciliado, id_proveedor guardado, y exactamente 1 llamada al proveedor"
        )

        # ======================================================================
        # FASE 4: TRANSFERENCIAS BANCARIAS, CUENTAS OFICIALES E IDEMPOTENCIA
        # ======================================================================
        log("================================================================================")
        log("[FASE 4] INICIO DE PRUEBAS DE TRANSFERENCIAS BANCARIAS, CUENTAS E IDEMPOTENCIA")
        log("================================================================================")

        # ----------------------------------------------------------------------
        # PASO 34: Consulta pública de cuentas bancarias oficiales
        # ----------------------------------------------------------------------
        st, res_cuentas = make_http_request(f"{base_url}/api/v1/orders/cuentas-bancarias", method="GET")
        cuentas_validas = (
            st == 200 and isinstance(res_cuentas, list) and len(res_cuentas) >= 4 and
            any(c.get("banco") == "BCP" for c in res_cuentas) and
            any(c.get("banco") == "INTERBANK" for c in res_cuentas) and
            any(c.get("banco") == "YAPE" for c in res_cuentas) and
            any(c.get("banco") == "PLIN" for c in res_cuentas) and
            all(c.get("activo") is True for c in res_cuentas)
        )
        record_step(
            "Cuentas Bancarias: Consulta pública retorna cuentas oficiales autorizadas (HTTP 200)",
            "HTTP",
            200, st,
            cuentas_validas,
            f"Se listaron {len(res_cuentas) if isinstance(res_cuentas, list) else 0} cuentas y billeteras activas oficiales"
        )

        # ----------------------------------------------------------------------
        # PASO 35: Creación de orden para transferencia bancaria en USD
        # ----------------------------------------------------------------------
        order_usd_key = f"idemp-usd-transfer-{RUN_SUFFIX}"
        order_usd_payload = {
            "nombres": "Estudiante",
            "apellidos": "Invitado",
            "email": guest_email,
            "telefono": "+51999888777",
            "tipoComprobante": "BOLETA",
            "programaIds": [102],
            "metodoPago": "TRANSFERENCIA",
            "claveIdempotencia": order_usd_key
        }
        st, res_order1 = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=order_usd_payload,
            token=user1_token,
            extra_headers={"X-Idempotency-Key": order_usd_key}
        )
        order1_code = res_order1.get("codigoOrden")
        order1_monto = res_order1.get("montoTotal")
        order1_id = int(docker_exec_psql(f"SELECT id_orden FROM ordenes WHERE codigo_orden = '{order1_code}';")) if order1_code else 0
        record_step(
            "Transferencia Manual: Creación de orden para transferencia bancaria en USD",
            "HTTP",
            200, st,
            st == 200 and order1_code is not None and order1_id > 0,
            f"Orden creada con código {order1_code}, ID {order1_id}, monto {order1_monto} USD"
        )

        # ----------------------------------------------------------------------
        # PASO 36: Reintento idempotente transparente recupera la misma orden
        # ----------------------------------------------------------------------
        st, res_order1_retry = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=order_usd_payload,
            token=user1_token,
            extra_headers={"X-Idempotency-Key": order_usd_key}
        )
        orders_in_db_for_key = int(docker_exec_psql(f"SELECT count(*) FROM ordenes WHERE clave_idempotencia = '{order_usd_key}';"))
        record_step(
            "Idempotencia Órdenes: Reintento transparente recupera la misma orden sin duplicar en BD",
            "HTTP",
            200, st,
            st == 200 and res_order1_retry.get("codigoOrden") == order1_code and orders_in_db_for_key == 1,
            f"Recuperada orden existente {res_order1_retry.get('codigoOrden')} y conteo en BD se mantuvo en 1 fila"
        )

        # ----------------------------------------------------------------------
        # PASO 37: Conflicto de idempotencia ante datos divergentes (HTTP 409)
        # ----------------------------------------------------------------------
        conflict_payload = dict(order_usd_payload)
        conflict_payload["programaIds"] = [101] # Cambia programa y monto
        st, res_conflict = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=conflict_payload,
            token=user1_token,
            extra_headers={"X-Idempotency-Key": order_usd_key}
        )
        record_step(
            "Idempotencia Órdenes: Intento de reuso de clave con datos divergentes es rechazado (HTTP 409)",
            "HTTP",
            409, st,
            st == 409,
            "Rechazado con HTTP 409 Conflict ante colisión de clave de idempotencia con contenido diferente"
        )

        # ----------------------------------------------------------------------
        # PASO 38: Aislamiento por comprador y prevención IDOR de clave (HTTP 403)
        # ----------------------------------------------------------------------
        st, res_idor = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=order_usd_payload,
            token=admin_token,
            extra_headers={"X-Idempotency-Key": order_usd_key}
        )
        record_step(
            "Idempotencia Órdenes: Aislamiento por comprador y prevención IDOR de clave (HTTP 403)",
            "HTTP",
            403, st,
            st == 403,
            "Bloqueado con HTTP 403 al intentar acceder o reaprovechar clave de idempotencia de otro comprador"
        )

        # ----------------------------------------------------------------------
        # PASO 39: Método de pago inválido o no reconocido es rechazado (HTTP 400)
        # ----------------------------------------------------------------------
        bad_method_payload = dict(order_usd_payload)
        bad_method_payload["metodoPago"] = "BITCOIN"
        bad_method_payload["claveIdempotencia"] = f"idemp-bad-method-{RUN_SUFFIX}"
        st, res_bad_method = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=bad_method_payload,
            token=user1_token
        )
        record_step(
            "Validación Método de Pago: Creación con método no permitido es rechazada (HTTP 400)",
            "HTTP",
            400, st,
            st == 400,
            "Método de pago no admitido ('BITCOIN') rechazado con IllegalArgumentException"
        )

        # ----------------------------------------------------------------------
        # PASO 40: Aprobación oficial BCP con normalización y ceros a la izquierda
        # ----------------------------------------------------------------------
        approve_bcp_payload = {
            "numeroOperacion": "OP0012345",
            "bancoDestino": "BCP",
            "cuentaDestino": "193-98218491-0-28",
            "montoVerificado": 200.00,
            "monedaVerificada": "USD",
            "fechaAbono": "2026-10-29T10:00:00Z",
            "metodoPago": "TRANSFERENCIA",
            "notasAdmin": "Abono verificado en ventanilla BCP",
            "observacionVerificacion": "Comprobante verificado y legítimo"
        }
        st, res_bcp = make_http_request(
            f"{base_url}/api/v1/orders/{order1_id}/confirmar-pago-manual",
            method="POST",
            payload=approve_bcp_payload,
            token=admin_token
        )
        bcp_db_row = docker_exec_psql(
            f"SELECT estado_orden, numero_operacion, banco_destino, cuenta_destino FROM ordenes WHERE id_orden = {order1_id};"
        )
        bcp_parts = bcp_db_row.split("|") if "|" in bcp_db_row else ["", "", "", ""]
        record_step(
            "Aprobación Manual BCP: Aprobación oficial con cuenta configurada y ceros a la izquierda en operación",
            "HTTP",
            200, st,
            st == 200 and bcp_parts[0] == "PAGADO" and bcp_parts[1] == "OP0012345" and bcp_parts[3] == "19398218491028",
            f"Estado {bcp_parts[0]}, operación normalizada con ceros '{bcp_parts[1]}', cuenta normalizada '{bcp_parts[3]}'"
        )

        # ----------------------------------------------------------------------
        # PASO 41: Detección y bloqueo de operación duplicada (anti-reuso)
        # ----------------------------------------------------------------------
        order2_key = f"idemp-usd-transfer-2-{RUN_SUFFIX}"
        order2_payload = dict(order_usd_payload)
        order2_payload["claveIdempotencia"] = order2_key
        st_o2, res_o2 = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=order2_payload,
            token=user1_token,
            extra_headers={"X-Idempotency-Key": order2_key}
        )
        order2_code = res_o2.get("codigoOrden")
        order2_id = int(docker_exec_psql(f"SELECT id_orden FROM ordenes WHERE codigo_orden = '{order2_code}';")) if order2_code else 0

        dup_operation_payload = {
            "numeroOperacion": "OP-0012345 ",
            "bancoDestino": "BCP",
            "cuentaDestino": "193-98218491-0-28",
            "montoVerificado": 200.00,
            "monedaVerificada": "USD",
            "fechaAbono": "2026-10-29T10:00:00Z",
            "metodoPago": "TRANSFERENCIA"
        }
        st, res_dup_op = make_http_request(
            f"{base_url}/api/v1/orders/{order2_id}/confirmar-pago-manual",
            method="POST",
            payload=dup_operation_payload,
            token=admin_token
        )
        record_step(
            "Detección Duplicados: Bloqueo de segundo abono con misma operación normalizada (anti-reuso)",
            "HTTP",
            409, st,
            st == 409,
            "Rechazado con HTTP 409 Conflict al detectar número de operación previamente registrado tras normalización"
        )

        # ----------------------------------------------------------------------
        # PASO 42: Rechazo de abono hacia cuenta bancaria desconocida o ajena
        # ----------------------------------------------------------------------
        unknown_acc_payload = {
            "numeroOperacion": "OP9998877",
            "bancoDestino": "BCP",
            "cuentaDestino": "999-9999999-9-99",
            "montoVerificado": 200.00,
            "monedaVerificada": "USD",
            "fechaAbono": "2026-10-29T10:00:00Z",
            "metodoPago": "TRANSFERENCIA"
        }
        st, res_bad_acc = make_http_request(
            f"{base_url}/api/v1/orders/{order2_id}/confirmar-pago-manual",
            method="POST",
            payload=unknown_acc_payload,
            token=admin_token
        )
        record_step(
            "Validación Cuenta Destino: Rechazo de abono hacia cuenta bancaria no configurada o ajena (HTTP 400)",
            "HTTP",
            400, st,
            st == 400,
            "Cuenta destino no autorizada rechazada con IllegalArgumentException sin alterar la orden"
        )

        # ----------------------------------------------------------------------
        # PASO 43: Billeteras Digitales - Creación y aprobación con Yape en PEN
        # ----------------------------------------------------------------------
        order3_key = f"idemp-yape-{RUN_SUFFIX}"
        order3_payload = {
            "nombres": "Estudiante",
            "apellidos": "Invitado",
            "email": guest_email,
            "telefono": "+51999888777",
            "tipoComprobante": "BOLETA",
            "programaIds": [101],
            "metodoPago": "YAPE",
            "claveIdempotencia": order3_key
        }
        st_o3, res_o3 = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=order3_payload,
            token=user1_token,
            extra_headers={"X-Idempotency-Key": order3_key}
        )
        order3_code = res_o3.get("codigoOrden")
        order3_id = int(docker_exec_psql(f"SELECT id_orden FROM ordenes WHERE codigo_orden = '{order3_code}';")) if order3_code else 0

        yape_approve_payload = {
            "numeroOperacion": "00887766",
            "bancoDestino": "YAPE",
            "cuentaDestino": "987654321",
            "montoVerificado": 150.00,
            "monedaVerificada": "PEN",
            "fechaAbono": "2026-10-29T10:00:00Z",
            "metodoPago": "YAPE",
            "notasAdmin": "Abono verificado vía app Yape"
        }
        st, res_yape = make_http_request(
            f"{base_url}/api/v1/orders/{order3_id}/confirmar-pago-manual",
            method="POST",
            payload=yape_approve_payload,
            token=admin_token
        )
        yape_db_row = docker_exec_psql(
            f"SELECT estado_orden, numero_operacion, banco_destino, cuenta_destino FROM ordenes WHERE id_orden = {order3_id};"
        )
        yape_parts = yape_db_row.split("|") if "|" in yape_db_row else ["", "", "", ""]
        record_step(
            "Billeteras Digitales: Creación y aprobación con Yape en moneda PEN",
            "HTTP",
            200, st,
            st == 200 and yape_parts[0] == "PAGADO" and yape_parts[1] == "00887766" and yape_parts[3] == "987654321",
            f"Orden Yape aprobada a PAGADO, operación '{yape_parts[1]}' (ceros conservados) y destino '{yape_parts[3]}'"
        )

        # ----------------------------------------------------------------------
        # PASO 44: Rechazo de aprobación manual sobre orden de pasarela en línea
        # ----------------------------------------------------------------------
        order4_key = f"idemp-izipay-{RUN_SUFFIX}"
        order4_payload = {
            "nombres": "Estudiante",
            "apellidos": "Invitado",
            "email": guest_email,
            "telefono": "+51999888777",
            "tipoComprobante": "BOLETA",
            "programaIds": [101],
            "metodoPago": "IZIPAY",
            "claveIdempotencia": order4_key
        }
        st_o4, res_o4 = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=order4_payload,
            token=user1_token,
            extra_headers={"X-Idempotency-Key": order4_key}
        )
        order4_code = res_o4.get("codigoOrden")
        order4_id = int(docker_exec_psql(f"SELECT id_orden FROM ordenes WHERE codigo_orden = '{order4_code}';")) if order4_code else 0

        online_manual_payload = {
            "numeroOperacion": "00112233",
            "bancoDestino": "YAPE",
            "cuentaDestino": "987654321",
            "montoVerificado": 150.00,
            "monedaVerificada": "PEN",
            "fechaAbono": "2026-10-29T10:00:00Z",
            "metodoPago": "YAPE"
        }
        st, res_online_bad = make_http_request(
            f"{base_url}/api/v1/orders/{order4_id}/confirmar-pago-manual",
            method="POST",
            payload=online_manual_payload,
            token=admin_token
        )
        record_step(
            "Seguridad de Pasarela: Rechazo de aprobación manual sobre orden de pasarela en línea (HTTP 400)",
            "HTTP",
            400, st,
            st == 400,
            "Bloqueado con IllegalArgumentException: órdenes de pasarelas en línea (IZIPAY) no admiten aprobación manual"
        )

                # ----------------------------------------------------------------------
        # PASO 49: Anti-reuso Variantes: Bloqueo de abono con variante de nombre de banco o CCI (HTTP 409)
        # ----------------------------------------------------------------------
        past_deposit_time = "2026-10-29T10:00:00Z"
        log("[TRANSFERENCIAS] Verificando detección de abono duplicado mediante variante de entidad bancaria y CCI...")
        # order1_id ya fue pagado con BCP, cuenta 193-98218491-0-28 y operación OP0012345
        # Intentamos pagar order2_id (que sigue PENDIENTE_PAGO) usando:
        # bancoDestino = "BANCO DE CREDITO DEL PERU" (variante de BCP)
        # cuentaDestino = "002-193-009821849102-28" (CCI oficial de BCP que mapea a 19398218491028)
        # numeroOperacion = "OP0012345" (misma operación)
        variant_dup_payload = {
            "numeroOperacion": "OP0012345",
            "bancoDestino": "BANCO DE CREDITO DEL PERU",
            "cuentaDestino": "002-193-009821849102-18",
            "montoVerificado": 200.00,
            "monedaVerificada": "USD",
            "fechaAbono": past_deposit_time,
            "metodoPago": "TRANSFERENCIA",
            "notasAdmin": "Intento de pagar segunda orden con variante de nombre de banco y CCI"
        }
        st_var_dup, res_var_dup = make_http_request(
            f"{base_url}/api/v1/orders/{order2_id}/confirmar-pago-manual",
            method="POST",
            payload=variant_dup_payload,
            token=admin_token
        )
        record_step(
            "Anti-reuso Variantes: Bloqueo de abono con variante de nombre de banco o CCI (HTTP 409)",
            "HTTP",
            409, st_var_dup,
            st_var_dup == 409,
            "Rechazado con HTTP 409 Conflict: la resolución canónica detecta que 'BANCO DE CREDITO DEL PERU' y CCI corresponden al abono BCP ya aplicado a otra orden"
        )

        # ----------------------------------------------------------------------
        # PASO 50: Compatibilidad Método/Destino: Rechazo de método y destino incompatibles o banco no aprobado (HTTP 400)
        # ----------------------------------------------------------------------
        log("[TRANSFERENCIAS] Verificando rechazo de combinaciones incompatibles entre método y destino...")
        # Intento 1: Método TRANSFERENCIA con destino YAPE (billetera móvil no autorizada para transferencia directa)
        incompatible_dest_payload = {
            "numeroOperacion": "OP992211",
            "bancoDestino": "YAPE",
            "cuentaDestino": "987654321",
            "montoVerificado": 200.00,
            "monedaVerificada": "USD",
            "fechaAbono": past_deposit_time,
            "metodoPago": "TRANSFERENCIA",
            "notasAdmin": "Incompatibilidad intencional de método y destino"
        }
        st_incomp, res_incomp = make_http_request(
            f"{base_url}/api/v1/orders/{order2_id}/confirmar-pago-manual",
            method="POST",
            payload=incompatible_dest_payload,
            token=admin_token
        )
        # Intento 2: Banco no aprobado / no autorizado en propiedades (SCOTIABANK)
        unapproved_bank_payload = {
            "numeroOperacion": "OP992212",
            "bancoDestino": "SCOTIABANK",
            "cuentaDestino": "1234567890",
            "montoVerificado": 200.00,
            "monedaVerificada": "USD",
            "fechaAbono": past_deposit_time,
            "metodoPago": "TRANSFERENCIA",
            "notasAdmin": "Banco no configurado en lysandri.cuentas-bancarias"
        }
        st_unapp, res_unapp = make_http_request(
            f"{base_url}/api/v1/orders/{order2_id}/confirmar-pago-manual",
            method="POST",
            payload=unapproved_bank_payload,
            token=admin_token
        )
        record_step(
            "Compatibilidad Método/Destino: Rechazo de método y destino incompatibles o banco no aprobado (HTTP 400)",
            "HTTP",
            400, st_incomp if st_incomp != 400 else st_unapp,
            st_incomp == 400 and st_unapp == 400,
            "Rechazados con HTTP 400 Bad Request: validación estricta de destinos y compatibilidad con método de pago"
        )

        # ----------------------------------------------------------------------
        # PASO 51: Contexto Invitado: Validación de token criptográfico y rechazo 403 ante ausencia o manipulación
        # ----------------------------------------------------------------------
        log("[INVITADOS] Probando autorización de recuperación con token criptográfico seguro...")
        guest_email_rec = f"invitado.recuperacion.{RUN_SUFFIX}@empresa.pe"
        guest_idemp_key = f"idemp-guest-session-{RUN_SUFFIX}"

        # 1. Obtener token de sesión de invitado desde endpoint oficial
        st_gs, res_gs = make_http_request(
            f"{base_url}/api/v1/orders/guest-session",
            method="POST",
            payload={"email": guest_email_rec, "buyerScope": f"scope-{RUN_SUFFIX}", "programId": 101}
        )
        guest_token = res_gs.get("guestSessionToken")
        log(f"[INVITADOS] Token de sesión de invitado obtenido (longitud={len(guest_token) if guest_token else 0})")

        # 2. Crear orden de invitado asociada a ese token
        guest_order_payload = {
            "nombres": "Invitado",
            "apellidos": "Recuperable",
            "email": guest_email_rec,
            "telefono": "+51999888777",
            "tipoComprobante": "BOLETA",
            "numeroDocumento": "70809010",
            "programaIds": [101],
            "metodoPago": "TRANSFERENCIA",
            "claveIdempotencia": guest_idemp_key,
            "guestSessionToken": guest_token
        }
        st_go, res_go = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=guest_order_payload,
            extra_headers={"X-Idempotency-Key": guest_idemp_key, "X-Guest-Session-Token": guest_token}
        )
        guest_order_code = res_go.get("codigoOrden")

        # 3. Intentar recuperar la orden enviando SOLO email y clave de idempotencia (SIN token de sesión) -> Debe retornar HTTP 403
        guest_replay_no_token = dict(guest_order_payload)
        del guest_replay_no_token["guestSessionToken"]
        st_steal_no_token, res_steal_no_token = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=guest_replay_no_token,
            extra_headers={"X-Idempotency-Key": guest_idemp_key}
        )

        # 4. Intentar recuperar la orden con token inválido/manipulado -> Debe retornar HTTP 403
        st_steal_bad_token, res_steal_bad_token = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=guest_replay_no_token,
            extra_headers={"X-Idempotency-Key": guest_idemp_key, "X-Guest-Session-Token": "token-falsificado-o-alterado"}
        )

        # 5. Recuperación legítima presentando el token de sesión válido -> Debe retornar HTTP 200 y el mismo código de orden
        st_legit_rec, res_legit_rec = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=guest_order_payload,
            extra_headers={"X-Idempotency-Key": guest_idemp_key, "X-Guest-Session-Token": guest_token}
        )
        legit_rec_code = res_legit_rec.get("codigoOrden")

        record_step(
            "Contexto Invitado: Validación de token criptográfico y rechazo 403 ante ausencia o manipulación",
            "HTTP",
            200, st_legit_rec,
            st_go == 200 and st_steal_no_token == 403 and st_steal_bad_token == 403 and st_legit_rec == 200 and legit_rec_code == guest_order_code,
            f"Orden '{guest_order_code}' protegida contra IDOR: rechaza recuperación sin token (HTTP {st_steal_no_token}) y con token alterado (HTTP {st_steal_bad_token}); autoriza con token válido (HTTP {st_legit_rec})"
        )

        # ----------------------------------------------------------------------
        # PASO 52: Contexto Invitado Reinicio: Recuperación de orden de invitado tras reinicio real de JVM (DB count=1)
        # ----------------------------------------------------------------------
        log("[REINICIO REAL JVM 3] Verificando persistencia y recuperación de contexto de invitado a través de reinicio del backend...")
        guest_restart_key = f"idemp-guest-restart-{RUN_SUFFIX}"
        guest_restart_email = f"invitado.restart.{RUN_SUFFIX}@empresa.pe"

        # Emitir token de invitado
        st_grs, res_grs = make_http_request(
            f"{base_url}/api/v1/orders/guest-session",
            method="POST",
            payload={"email": guest_restart_email}
        )
        guest_restart_token = res_grs.get("guestSessionToken")

        # Crear orden en JVM 2
        guest_restart_payload = {
            "nombres": "Invitado",
            "apellidos": "Reinicio",
            "email": guest_restart_email,
            "telefono": "+51999888777",
            "tipoComprobante": "BOLETA",
            "numeroDocumento": "70809010",
            "programaIds": [101],
            "metodoPago": "TRANSFERENCIA",
            "claveIdempotencia": guest_restart_key,
            "guestSessionToken": guest_restart_token
        }
        st_pre_restart, res_pre_restart = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=guest_restart_payload,
            extra_headers={"X-Idempotency-Key": guest_restart_key, "X-Guest-Session-Token": guest_restart_token}
        )
        pre_restart_code = res_pre_restart.get("codigoOrden")
        pid_before_restart = jar_proc_2.pid
        log(f"[REINICIO REAL JVM 3] Orden creada en JVM 2 (PID {pid_before_restart}) con código '{pre_restart_code}'")

        # Detener JVM 2
        log(f"[REINICIO REAL JVM 3] Terminando proceso Java #2 (PID {pid_before_restart})...")
        jar_proc_2.terminate()
        try:
            jar_proc_2.wait(timeout=5)
        except Exception:
            jar_proc_2.kill()
        log("[REINICIO REAL JVM 3] Proceso Java #2 terminado.")

        # Iniciar JVM 3 con el mismo JAR y misma BD
        jar_log_path_3 = os.path.join(RUN_EVIDENCIAS_DIR, "jar-ejecucion-consola-proceso3.log")
        jar_proc_3, jar_log_file_3 = launch_jar_backend(APP_HTTP_PORT, jar_log_path_3)
        pid_after_restart = jar_proc_3.pid
        log(f"[REINICIO REAL JVM 3] Proceso Java #3 iniciado con éxito (PID {pid_after_restart})")

        # Reintento sin token en JVM 3 -> HTTP 403
        guest_restart_no_token = dict(guest_restart_payload)
        del guest_restart_no_token["guestSessionToken"]
        st_no_tok_jvm3, res_no_tok_jvm3 = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=guest_restart_no_token,
            extra_headers={"X-Idempotency-Key": guest_restart_key}
        )

        # Recuperación con token en JVM 3 -> HTTP 200
        st_rec_jvm3, res_rec_jvm3 = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=guest_restart_payload,
            extra_headers={"X-Idempotency-Key": guest_restart_key, "X-Guest-Session-Token": guest_restart_token}
        )
        post_restart_code = res_rec_jvm3.get("codigoOrden")
        count_guest_orders = docker_exec_psql(
            f"SELECT count(*) FROM ordenes WHERE clave_idempotencia = '{guest_restart_key}';"
        )

        record_step(
            "Contexto Invitado Reinicio: Recuperación de orden de invitado tras reinicio real de JVM (DB count=1)",
            "HTTP",
            200, st_rec_jvm3,
            st_pre_restart == 200 and st_no_tok_jvm3 == 403 and st_rec_jvm3 == 200 and post_restart_code == pre_restart_code and count_guest_orders == "1",
            f"Mismo código '{post_restart_code}' recuperado en JVM 3 (PID {pid_after_restart}, previo PID {pid_before_restart}), sin token HTTP 403, conteo en BD={count_guest_orders}"
        )

        log("================================================================================")
        log(f"VALIDACIÓN COMPLETADA EXITOSAMENTE: {len(test_results)} PASOS APROBADOS DE {len(test_results)}")
        log("================================================================================")

    finally:
        # Registrar pasos no ejecutados si hubo interrupción
        for expected_name in PLANNED_STEPS:
            if expected_name not in executed_step_names:
                test_results.append({
                    "paso": len(test_results) + 1,
                    "nombre": expected_name,
                    "tipo": "HTTP",
                    "esperado": "N/A",
                    "obtenido": "NO_EJECUTADO",
                    "resultado": "NO_EJECUTADO",
                    "detalles": "Paso no alcanzado debido a interrupción o fallo previo en la secuencia"
                })

        # Guardar reporte JSON estructurado propio de la ejecución
        summary = {
            "identificadorEjecucion": RUN_ID,
            "fechaEjecucion": datetime.datetime.now().isoformat(),
            "artefactoProbado": JAR_PATH,
            "sha256": jar_sha256 if 'jar_sha256' in locals() else "N/A",
            "entorno": {
                "disposableContainer": DISPOSABLE_CONTAINER,
                "disposableDatabase": f"127.0.0.1:{DISPOSABLE_PORT}/{DISPOSABLE_DB}",
                "disposableDatabaseUser": DISPOSABLE_USER,
                "mockSmtpPort": SMTP_MOCK_PORT,
                "mockWhatsAppPort": WHATSAPP_MOCK_PORT,
                "backendHttpPort": APP_HTTP_PORT
            },
            "totalPasos": len(test_results),
            "pasosAprobados": sum(1 for p in test_results if p["resultado"] == "APROBADO"),
            "pasosFallidos": sum(1 for p in test_results if p["resultado"] == "FALLIDO"),
            "pasosNoEjecutados": sum(1 for p in test_results if p["resultado"] == "NO_EJECUTADO"),
            "resultadosDetallados": test_results
        }
        with open(JSON_RESULTS_PATH, "w", encoding="utf-8") as jf:
            json.dump(summary, jf, indent=2, ensure_ascii=False)
        log(f"[EVIDENCIA] Reporte estructurado guardado en: {JSON_RESULTS_PATH}")

        # Copia de compatibilidad en latest directory y en la carpeta fija de entrega
        latest_json = os.path.join(LATEST_EVIDENCIAS_DIR, "recorrido-http-jar-real.json")
        latest_log = os.path.join(LATEST_EVIDENCIAS_DIR, "registro-ejecucion-validar-jar.log")
        delivery_json = os.path.join(DELIVERY_EVIDENCIAS_DIR, "recorrido-http-jar-real.json")
        delivery_log = os.path.join(DELIVERY_EVIDENCIAS_DIR, "registro-ejecucion-validar-jar.log")
        try:
            with open(latest_json, "w", encoding="utf-8") as jf:
                json.dump(summary, jf, indent=2, ensure_ascii=False)
            shutil.copy2(LOG_FILE_PATH, latest_log)
            with open(delivery_json, "w", encoding="utf-8") as jf:
                json.dump(summary, jf, indent=2, ensure_ascii=False)
            shutil.copy2(LOG_FILE_PATH, delivery_log)
            if os.path.isfile(JAR_CONSOLE_LOG_PATH):
                shutil.copy2(JAR_CONSOLE_LOG_PATH, os.path.join(DELIVERY_EVIDENCIAS_DIR, "jar-ejecucion-consola.log"))
            if 'jar_log_path_2' in locals() and os.path.isfile(jar_log_path_2):
                shutil.copy2(jar_log_path_2, os.path.join(DELIVERY_EVIDENCIAS_DIR, "jar-ejecucion-consola-proceso2.log"))
            if 'jar_log_path_3' in locals() and os.path.isfile(jar_log_path_3):
                shutil.copy2(jar_log_path_3, os.path.join(DELIVERY_EVIDENCIAS_DIR, "jar-ejecucion-consola-proceso3.log"))
            
            # Guardar reporte sha256-jar.txt en DELIVERY_EVIDENCIAS_DIR
            sha_file = os.path.join(DELIVERY_EVIDENCIAS_DIR, "sha256-jar.txt")
            with open(sha_file, "w", encoding="utf-8") as f:
                f.write(f"{jar_sha256}  lysandri-backend-1.0.0-SNAPSHOT.jar\n")
            log(f"[EVIDENCIA] Checksum SHA-256 guardado en: {sha_file}")

            # Copiar reportes de Surefire XML en DELIVERY_EVIDENCIAS_DIR/surefire-reports/
            surefire_src = os.path.join(BASE_DIR, "backend", "target", "surefire-reports")
            surefire_dst = os.path.join(DELIVERY_EVIDENCIAS_DIR, "surefire-reports")
            os.makedirs(surefire_dst, exist_ok=True)
            if os.path.isdir(surefire_src):
                copied_xmls = 0
                for f_name in os.listdir(surefire_src):
                    if f_name.startswith("TEST-") and f_name.endswith(".xml"):
                        shutil.copy2(os.path.join(surefire_src, f_name), os.path.join(surefire_dst, f_name))
                        copied_xmls += 1
                log(f"[EVIDENCIA] Copiados {copied_xmls} reportes XML de Surefire a: {surefire_dst}")
        except Exception as e:
            log(f"[WARN] Error guardando evidencias complementarias: {e}")

        # Limpieza rigurosa y segura de recursos creados exclusivamente en esta ejecución
        log("[LIMPIEZA] Deteniendo procesos JAR...")
        for proc in (jar_proc if 'jar_proc' in locals() else None, 
                     jar_proc_2 if 'jar_proc_2' in locals() else None,
                     jar_proc_3 if 'jar_proc_3' in locals() else None):
            if proc and proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=5)
                except Exception:
                    proc.kill()
        for log_f in (jar_log_file if 'jar_log_file' in locals() else None,
                      jar_log_file_2 if 'jar_log_file_2' in locals() else None,
                      jar_log_file_3 if 'jar_log_file_3' in locals() else None):
            if log_f:
                try:
                    log_f.close()
                except Exception:
                    pass

        log("[LIMPIEZA] Deteniendo servidores mock (SMTP y WhatsApp)...")
        if smtp_server:
            smtp_server.stop()
        if whatsapp_server:
            whatsapp_server.stop()

        target_container = created_container_id if created_container_id else DISPOSABLE_CONTAINER
        log(f"[LIMPIEZA] Destruyendo contenedor PostgreSQL desechable creado en esta corrida ({target_container})...")
        subprocess.run(["docker", "rm", "-f", target_container], capture_output=True)

        log_file_handle.close()

if __name__ == "__main__":
    try:
        main()
        sys.exit(0)
    except Exception as e:
        print(f"\n[ERROR FATAL DE VALIDACIÓN] {e}", file=sys.stderr)
        sys.exit(1)

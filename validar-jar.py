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
    BASE_DIR, "backend", "evidencias-auth", f"ejecucion-{RUN_STAMP}-cierre-moodle-fase6-{RUN_SUFFIX}"
)
LATEST_EVIDENCIAS_DIR = os.path.join(
    BASE_DIR, "backend", "evidencias-auth", "ejecucion-cierre-moodle-fase6-latest"
)
DELIVERY_EVIDENCIAS_DIR = os.path.join(
    BASE_DIR, "backend", "evidencias-auth", "ejecucion-cierre-moodle-fase6-latest"
)
CIERRE_EVIDENCIAS_DIR = os.path.join(
    BASE_DIR, "backend", "evidencias-auth", "ejecucion-20261103-cierre-revision-tres-problemas"
)

os.makedirs(RUN_EVIDENCIAS_DIR, exist_ok=True)
os.makedirs(LATEST_EVIDENCIAS_DIR, exist_ok=True)
os.makedirs(DELIVERY_EVIDENCIAS_DIR, exist_ok=True)
os.makedirs(CIERRE_EVIDENCIAS_DIR, exist_ok=True)

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
MOODLE_MOCK_PORT = find_available_port(18081)

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
    for attempt in range(12):
        try:
            p = run_cmd(cmd, timeout=30)
            return p.stdout.strip()
        except RuntimeError as e:
            err_str = str(e).lower()
            if ("shutting down" in err_str or "starting up" in err_str or "recovery" in err_str or "connection" in err_str) and attempt < 11:
                time.sleep(1)
                continue
            raise

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
# SERVIDOR SIMULADO DE MOODLE LMS REST API (PUERTO AISLADO)
# ==============================================================================
moodle_users_store = {}
moodle_user_counter = [9900]
moodle_lock = threading.Lock()

class MockMoodleHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8", errors="replace")
        params = urllib.parse.parse_qs(body)
        wsfunction = params.get("wsfunction", [""])[0]

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()

        if wsfunction == "core_course_get_courses_by_field":
            val = params.get("value", [""])[0]
            if val in ["101", "102"]:
                res = {"courses": [{"id": int(val), "fullname": f"Curso Moodle {val}", "shortname": f"CM-{val}", "visible": 1}]}
            else:
                res = {"courses": []}
            self.wfile.write(json.dumps(res).encode("utf-8"))
        elif wsfunction == "core_user_get_users_by_field":
            val = params.get("values[0]", [""])[0]
            with moodle_lock:
                if val and val in moodle_users_store:
                    res = [{"id": moodle_users_store[val], "email": val}]
                else:
                    res = []
            self.wfile.write(json.dumps(res).encode("utf-8"))
        elif wsfunction == "core_user_create_users":
            email = params.get("users[0][email]", [""])[0]
            username = params.get("users[0][username]", ["usuario_creado"])[0]
            with moodle_lock:
                if email and email in moodle_users_store:
                    uid = moodle_users_store[email]
                else:
                    moodle_user_counter[0] += 1
                    uid = moodle_user_counter[0]
                    if email:
                        moodle_users_store[email] = uid
            res = [{"id": uid, "username": username}]
            self.wfile.write(json.dumps(res).encode("utf-8"))
        elif wsfunction == "enrol_manual_enrol_users":
            self.wfile.write(b"null")
        elif wsfunction == "core_enrol_get_users_courses":
            res = [{"id": 101, "shortname": "CM-101"}]
            self.wfile.write(json.dumps(res).encode("utf-8"))
        else:
            self.wfile.write(b"{}")

    def log_message(self, format, *args):
        pass

class MockMoodleServer(threading.Thread):
    def __init__(self, port):
        super().__init__(daemon=True)
        self.port = port
        self.httpd = http.server.HTTPServer(("127.0.0.1", port), MockMoodleHandler)

    def run(self):
        self.httpd.serve_forever()

    def stop(self):
        try:
            self.httpd.shutdown()
            self.httpd.server_close()
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
            resp_headers = dict(resp.headers)
            try:
                body_json = json.loads(body_str) if body_str else {}
            except Exception:
                body_json = {"raw": body_str}
            if isinstance(body_json, dict):
                body_json["_headers"] = resp_headers
            return status, body_json
    except urllib.error.HTTPError as ex:
        status = ex.code
        body_str = ex.read().decode("utf-8", errors="replace")
        resp_headers = dict(ex.headers)
        try:
            body_json = json.loads(body_str) if body_str else {}
        except Exception:
            body_json = {"raw": body_str}
        if isinstance(body_json, dict):
            body_json["_headers"] = resp_headers
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
    "Abono Histórico Incierto: Bloqueo de abono con destino histórico ambiguo para revisión (HTTP 409)",
    "Contexto Invitado: Validación de token criptográfico y rechazo 403 ante ausencia o manipulación",
    "Contexto Invitado Reinicio: Recuperación de orden de invitado tras reinicio real de JVM (DB count=1)",
    "Moodle Catálogo Público: Sólo programas activos y con vinculación verificada vigente son visibles (HTTP 200)",
    "Moodle Regla Checkout: Rechazo de compra para programa sin vinculación verificada o con verificación expirada (HTTP 400)",
    "Moodle Congelamiento Checkout: moodleCourseId y origen congelados inmutablemente en detalle_ordenes (HTTP 200)",
    "Moodle Resolución Administrativa: Auditoría documental completa y habilitación de reintento de aprovisionamiento (HTTP 200)",
    "Moodle Desvinculación Programa: Limpieza de datos LMS, desactivación comercial y retiro automático del catálogo público (HTTP 200)",
    "Contabilidad Autorización: Rechazo de acceso a contabilidad sin PIN o con PIN inválido (HTTP 401/403)",
    "Contabilidad Reporte: Administrador consulta reporte general consolidado con PIN oficial y período TODOS (HTTP 200)",
    "Contabilidad SIRE Validación Período: Rechazo de exportación sin período o con período TODOS (HTTP 400)",
    "Contabilidad SIRE Validación Integridad: Rechazo con HTTP 422 si existen comprobantes incompletos identificando idComprobante e idOrden",
    "Contabilidad SIRE Validación Divisa: Rechazo con HTTP 422 ante transacciones en USD sin tipo de cambio oficial verificado",
    "Contabilidad Integridad Transaccional: Comprobación de que las solicitudes 400 y 422 no mutaron registros ni causaron inconsistencias",
    "Contabilidad Emisión y Anulación: Anulación formal mediante idComprobante persistente y motivo auditado (HTTP 200)",
    "Contabilidad Persistencia Auditoría: Verificación en BD del estado ANULADO y registro inmutable en auditoria_contable",
    "Contabilidad SIRE Exportación Exitosa: Exportación con período YYYY-MM, Content-Disposition y CUO determinista CP%08d (HTTP 200)",
    "Administración Usuarios: Separación estricta entre actualización de perfil (PUT) y modificación de rol auditada (PATCH) (HTTP 200)",
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
        f"-Dmoodle.url=http://127.0.0.1:{MOODLE_MOCK_PORT}",
        "-Dmoodle.token=mock_moodle_token_valido",
        "-Daccounting.audit-pin=test-accounting-audit-pin",
        "-DACCOUNTING_AUDIT_PIN=test-accounting-audit-pin",
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

        # 3b. Levantar servidor Mock Moodle LMS en puerto aislado
        log(f"[INFRAESTRUCTURA] Iniciando servidor Mock Moodle LMS en puerto {MOODLE_MOCK_PORT}...")
        moodle_server = MockMoodleServer(MOODLE_MOCK_PORT)
        moodle_server.start()

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
        consecutive_ok = 0
        for _ in range(50):
            res = subprocess.run(
                ["docker", "exec", created_container_id, "psql", "-U", DISPOSABLE_USER, "-d", DISPOSABLE_DB, "-t", "-c", "SELECT 1;"],
                capture_output=True, text=True
            )
            if res.returncode == 0 and "1" in res.stdout:
                consecutive_ok += 1
                if consecutive_ok >= 3:
                    ready = True
                    break
            else:
                consecutive_ok = 0
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
            "INSERT INTO programa (id_programa, moodle_course_id, titulo, slug, precio, moneda, activo, precio_aprobado, moodle_verificado, moodle_verificado_en, moodle_estado_verificacion, moodle_course_nombre) "
            "VALUES (101, 101, 'Curso Ejecutivo de Alta Dirección', 'curso-ejecutivo-alta-direccion', 150.00, 'PEN', true, true, true, NOW(), 'VERIFICADO', 'Curso Moodle 101') "
            "ON CONFLICT (id_programa) DO UPDATE SET precio_aprobado = true, activo = true, moodle_verificado = true, moodle_verificado_en = NOW(), moodle_estado_verificacion = 'VERIFICADO', moodle_course_nombre = 'Curso Moodle 101';"
        )
        docker_exec_psql(
            "INSERT INTO programa (id_programa, moodle_course_id, titulo, slug, precio, moneda, activo, precio_aprobado, moodle_verificado, moodle_verificado_en, moodle_estado_verificacion, moodle_course_nombre) "
            "VALUES (102, 102, 'Programa Internacional Ejecutivo', 'programa-internacional-ejecutivo', 200.00, 'USD', true, true, true, NOW(), 'VERIFICADO', 'Curso Moodle 102') "
            "ON CONFLICT (id_programa) DO UPDATE SET precio_aprobado = true, activo = true, moodle_verificado = true, moodle_verificado_en = NOW(), moodle_estado_verificacion = 'VERIFICADO', moodle_course_nombre = 'Curso Moodle 102';"
        )
        docker_exec_psql(
            "INSERT INTO programa (id_programa, moodle_course_id, titulo, slug, precio, moneda, activo, precio_aprobado, moodle_verificado, moodle_verificado_en, moodle_estado_verificacion) "
            "VALUES (103, null, 'Programa Borrador Sin Moodle', 'programa-borrador-sin-moodle', 180.00, 'USD', true, true, false, null, 'SIN_VINCULACION') "
            "ON CONFLICT (id_programa) DO UPDATE SET moodle_course_id = null, activo = true, moodle_verificado = false, moodle_verificado_en = null, moodle_estado_verificacion = 'SIN_VINCULACION';"
        )
        docker_exec_psql(
            "INSERT INTO programa (id_programa, moodle_course_id, titulo, slug, precio, moneda, activo, precio_aprobado, moodle_verificado, moodle_verificado_en, moodle_estado_verificacion) "
            "VALUES (104, 104, 'Programa Con Moodle Expirado', 'programa-moodle-expirado', 190.00, 'USD', true, true, true, NOW() - INTERVAL '30 days', 'VERIFICADO') "
            "ON CONFLICT (id_programa) DO UPDATE SET moodle_course_id = 104, activo = true, moodle_verificado = true, moodle_verificado_en = NOW() - INTERVAL '30 days', moodle_estado_verificacion = 'VERIFICADO';"
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
        # PASO 47: Abono Histórico Incierto: Bloqueo de abono con destino histórico ambiguo para revisión (HTTP 409)
        # ----------------------------------------------------------------------
        log("[TRANSFERENCIAS] Verificando rechazo de abono histórico con destino ambiguo/incierto...")
        op_ambigua = f"OP-AMBIGUO-{RUN_SUFFIX}"
        docker_exec_psql(
            f"INSERT INTO ordenes (id_orden, id_user, codigo_orden, estado_orden, estado_aprovisionamiento, total, moneda, metodo_pago, banco_destino, cuenta_destino, numero_operacion, moodle_matricula_sincronizada, fecha_orden) "
            f"VALUES (nextval('ordenes_id_orden_seq'), (SELECT id_user FROM usuario WHERE email = '{admin_email}'), 'ORD-HIST-AMB-{RUN_SUFFIX}', 'PAGADO', 'PENDIENTE', 200.00, 'USD', 'TRANSFERENCIA', 'BANCO SIN IDENTIFICAR', '12345', '{op_ambigua}', false, NOW());"
        )
        ambiguous_deposit_payload = {
            "numeroOperacion": op_ambigua,
            "bancoDestino": "BCP",
            "cuentaDestino": "193-98218491-0-28",
            "montoVerificado": 200.00,
            "monedaVerificada": "USD",
            "fechaAbono": past_deposit_time,
            "metodoPago": "TRANSFERENCIA",
            "notasAdmin": "Intento de aprobar abono que colisiona con registro histórico incierto"
        }
        st_amb, res_amb = make_http_request(
            f"{base_url}/api/v1/orders/{order2_id}/confirmar-pago-manual",
            method="POST",
            payload=ambiguous_deposit_payload,
            token=admin_token
        )
        order2_status_after_amb = docker_exec_psql(f"SELECT estado_orden FROM ordenes WHERE id_orden = {order2_id};")
        comprobantes_after_amb = int(docker_exec_psql(f"SELECT count(*) FROM comprobante_pago WHERE id_orden = {order2_id};"))
        record_step(
            "Abono Histórico Incierto: Bloqueo de abono con destino histórico ambiguo para revisión (HTTP 409)",
            "HTTP",
            409, st_amb,
            st_amb == 409 and "requiere revisión administrativa" in str(res_amb.get("message", "")) and "PENDIENTE" in order2_status_after_amb and comprobantes_after_amb == 0,
            f"Rechazado con HTTP 409: '{res_amb.get('message')}', orden permanece {order2_status_after_amb}, comprobantes emitidos={comprobantes_after_amb}"
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

        # ----------------------------------------------------------------------
        # PASO 50: Moodle Catálogo Público: Sólo programas activos y con vinculación verificada vigente son visibles (HTTP 200)
        # ----------------------------------------------------------------------
        log("[MOODLE 1] Verificando catálogo público filtrado exclusivamente por programas verificados y vigentes...")
        st_cat, res_cat = make_http_request(f"{base_url}/api/v1/programas", method="GET")
        cat_programas = res_cat if isinstance(res_cat, list) else []
        cat_ids = [p.get("idPrograma") for p in cat_programas]
        cat_ok = (101 in cat_ids) and (102 in cat_ids) and (103 not in cat_ids) and (104 not in cat_ids)
        record_step(
            "Moodle Catálogo Público: Sólo programas activos y con vinculación verificada vigente son visibles (HTTP 200)",
            "HTTP",
            200, st_cat,
            st_cat == 200 and cat_ok,
            f"Catálogo público retornó {len(cat_programas)} programas (IDs visibles: {cat_ids}). Programas 103 (sin vincular) y 104 (expirado) excluidos correctamente."
        )

        # ----------------------------------------------------------------------
        # PASO 51: Moodle Regla Checkout: Rechazo de compra para programa sin vinculación verificada o con verificación expirada (HTTP 400)
        # ----------------------------------------------------------------------
        log("[MOODLE 2] Verificando bloqueo en checkout de programas con vinculación inválida o expirada...")
        chk_sin_vinc = {
            "nombres": "Cliente",
            "apellidos": "Moodle Test",
            "nombreCompleto": "Cliente Moodle Test",
            "email": f"moodle.test.{RUN_SUFFIX}@empresa.pe",
            "telefono": "+51999888999",
            "tipoComprobante": "BOLETA",
            "numeroDocumento": "71819101",
            "programaIds": [103],
            "metodoPago": "TRANSFERENCIA"
        }
        st_chk_103, res_chk_103 = make_http_request(f"{base_url}/api/v1/orders/checkout", method="POST", payload=chk_sin_vinc)
        
        chk_exp = dict(chk_sin_vinc)
        chk_exp["programaIds"] = [104]
        st_chk_104, res_chk_104 = make_http_request(f"{base_url}/api/v1/orders/checkout", method="POST", payload=chk_exp)

        msg_103 = res_chk_103.get("message", "")
        msg_104 = res_chk_104.get("message", "")
        chk_rejected = (st_chk_103 == 400 and ("vinculaci" in msg_103 or "Moodle" in msg_103)) and \
                       (st_chk_104 == 400 and ("expirado" in msg_104 or "revalidaci" in msg_104 or "Moodle" in msg_104))
        record_step(
            "Moodle Regla Checkout: Rechazo de compra para programa sin vinculación verificada o con verificación expirada (HTTP 400)",
            "HTTP",
            400, st_chk_103,
            chk_rejected,
            f"Rechazo HTTP 400 para programa sin vinculación (ID 103: '{msg_103}') y para programa con verificación expirada (ID 104: '{msg_104}')"
        )

        # ----------------------------------------------------------------------
        # PASO 52: Moodle Congelamiento Checkout: moodleCourseId y origen congelados inmutablemente en detalle_ordenes (HTTP 200)
        # ----------------------------------------------------------------------
        log("[MOODLE 3] Verificando congelamiento inmutable de moodleCourseId en detalle_ordenes...")
        freeze_email = f"moodle.freeze.{RUN_SUFFIX}@empresa.pe"
        freeze_idemp_key = f"idemp-moodle-freeze-{RUN_SUFFIX}"

        # Obtener token seguro de invitado requerido para checkout de invitado
        st_gs_freeze, res_gs_freeze = make_http_request(
            f"{base_url}/api/v1/orders/guest-session",
            method="POST",
            payload={"email": freeze_email}
        )
        freeze_guest_token = res_gs_freeze.get("guestSessionToken") if st_gs_freeze == 200 else ""

        chk_ok_payload = {
            "nombres": "Cliente",
            "apellidos": "Moodle Congelado",
            "nombreCompleto": "Cliente Moodle Congelado",
            "email": freeze_email,
            "telefono": "+51999888999",
            "tipoComprobante": "BOLETA",
            "numeroDocumento": "71819101",
            "programaIds": [101],
            "metodoPago": "TRANSFERENCIA",
            "claveIdempotencia": freeze_idemp_key,
            "guestSessionToken": freeze_guest_token
        }
        st_chk_ok, res_chk_ok = make_http_request(
            f"{base_url}/api/v1/orders/checkout",
            method="POST",
            payload=chk_ok_payload,
            extra_headers={
                "X-Idempotency-Key": freeze_idemp_key,
                "X-Guest-Session-Token": freeze_guest_token
            }
        )
        freeze_order_code = res_chk_ok.get("codigoOrden")

        freeze_db = docker_exec_psql(
            f"SELECT det.moodle_course_id, det.origen_moodle_course_id "
            f"FROM detalle_ordenes det JOIN ordenes o ON det.id_orden = o.id_orden "
            f"WHERE o.codigo_orden = '{freeze_order_code}';"
        )
        course_id_frozen, origen_frozen = (freeze_db.split("|") + [""])[:2]
        is_frozen_ok = (st_chk_ok == 200 and course_id_frozen == "101" and origen_frozen == "CHECKOUT_CONGELADO")
        record_step(
            "Moodle Congelamiento Checkout: moodleCourseId y origen congelados inmutablemente en detalle_ordenes (HTTP 200)",
            "HTTP",
            200, st_chk_ok,
            is_frozen_ok,
            f"Orden '{freeze_order_code}' congeló moodle_course_id={course_id_frozen} con origen='{origen_frozen}' en base de datos"
        )

        # ----------------------------------------------------------------------
        # PASO 53: Moodle Resolución Administrativa: Auditoría documental completa y habilitación de reintento de aprovisionamiento (HTTP 200)
        # ----------------------------------------------------------------------
        log("[MOODLE 4] Verificando resolución administrativa de orden histórica y reintento de matrícula...")
        hist_order_code = f"ORD-HIST-RES-{RUN_SUFFIX}"
        docker_exec_psql(
            f"INSERT INTO ordenes (id_orden, codigo_orden, id_user, estado_orden, estado_aprovisionamiento, moodle_matricula_sincronizada, fecha_orden, total, moneda, metodo_pago, clave_idempotencia) "
            f"VALUES (9999, '{hist_order_code}', (SELECT id_user FROM usuario WHERE email = '{admin_email}'), 'PAGADO', 'PENDIENTE', false, NOW() - INTERVAL '10 days', 150.00, 'PEN', 'STRIPE', 'idemp-hist-res-{RUN_SUFFIX}') "
            f"ON CONFLICT (id_orden) DO NOTHING;"
        )
        docker_exec_psql(
            "INSERT INTO detalle_ordenes (id_detalle, id_orden, id_programa, precio_unitario, cantidad, subtotal, moodle_matriculado, moodle_course_id, origen_moodle_course_id) "
            "VALUES (9999, 9999, 101, 150.00, 1, 150.00, false, null, 'PENDIENTE_RESOLUCION_ADMINISTRATIVA') "
            "ON CONFLICT (id_detalle) DO NOTHING;"
        )

        resolucion_payload = {
            "moodleCourseId": 101,
            "motivo": "Revisión documental de matrícula histórica conforme a comprobante físico auditado",
            "referenciaEvidencia": f"EXP-RESOLUCION-2026-{RUN_SUFFIX}"
        }
        st_res_admin, res_res_admin = make_http_request(
            f"{base_url}/api/v1/orders/detalles/9999/resolver-moodle",
            method="POST",
            payload=resolucion_payload,
            token=admin_token
        )

        audit_db = docker_exec_psql(
            "SELECT moodle_course_id, origen_moodle_course_id, moodle_resolucion_autorizada_por, moodle_resolucion_motivo, moodle_resolucion_evidencia "
            "FROM detalle_ordenes WHERE id_detalle = 9999;"
        )
        audit_parts = audit_db.split("|")
        res_course = audit_parts[0] if len(audit_parts) > 0 else ""
        res_origen = audit_parts[1] if len(audit_parts) > 1 else ""
        res_autor = audit_parts[2] if len(audit_parts) > 2 else ""

        st_retry, res_retry = make_http_request(
            f"{base_url}/api/v1/orders/9999/reintentar-matricula",
            method="POST",
            token=admin_token
        )

        post_retry_db = docker_exec_psql(
            "SELECT estado_aprovisionamiento, moodle_matricula_sincronizada FROM ordenes WHERE id_orden = 9999;"
        )
        aprov_estado, sync_mat = (post_retry_db.split("|") + [""])[:2]
        is_res_ok = (st_res_admin == 200 and res_course == "101" and res_origen == "RESOLUCION_ADMINISTRATIVA" and
                     res_autor == admin_email and st_retry == 200 and aprov_estado == "COMPLETADO" and sync_mat == "t")

        record_step(
            "Moodle Resolución Administrativa: Auditoría documental completa y habilitación de reintento de aprovisionamiento (HTTP 200)",
            "HTTP",
            200, st_res_admin,
            is_res_ok,
            f"Resolución HTTP {st_res_admin} (moodleCourseId={res_course}, origen='{res_origen}', autor='{res_autor}'); Reintento HTTP {st_retry} completó aprovisionamiento (estado={aprov_estado}, sync={sync_mat})"
        )

        # ----------------------------------------------------------------------
        # PASO 54: Moodle Desvinculación Programa: Limpieza de datos LMS, desactivación comercial y retiro automático del catálogo público (HTTP 200)
        # ----------------------------------------------------------------------
        log("[MOODLE 5] Verificando desvinculación administrativa de programa, desactivación y exclusión del catálogo...")
        st_desv, res_desv = make_http_request(
            f"{base_url}/api/v1/programas/admin/102/desvincular-moodle",
            method="POST",
            token=admin_token
        )

        prog_db_after = docker_exec_psql(
            "SELECT moodle_course_id, activo, moodle_verificado, moodle_estado_verificacion FROM programa WHERE id_programa = 102;"
        )
        p_course, p_activo, p_verif, p_status = (prog_db_after.split("|") + ["", "", ""])[:4]

        st_cat2, res_cat2 = make_http_request(f"{base_url}/api/v1/programas", method="GET")
        cat2_ids = [p.get("idPrograma") for p in (res_cat2 if isinstance(res_cat2, list) else [])]
        is_desv_ok = (st_desv == 200 and p_course == "" and p_activo == "f" and p_verif == "f" and
                      p_status == "SIN_VINCULACION" and 102 not in cat2_ids)

        record_step(
            "Moodle Desvinculación Programa: Limpieza de datos LMS, desactivación comercial y retiro automático del catálogo público (HTTP 200)",
            "HTTP",
            200, st_desv,
            is_desv_ok,
            f"Desvinculación HTTP {st_desv}: programa 102 desactivado (activo={p_activo}, verif={p_verif}, estado={p_status}, courseId={p_course or 'null'}). Retirado del catálogo público: 102 not in {cat2_ids}"
        )

        # ----------------------------------------------------------------------
        # PASO 55: Contabilidad Autorización: Rechazo de acceso a contabilidad sin PIN o con PIN inválido (HTTP 401/403)
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 1] Verificando rechazo de acceso sin PIN o con PIN de auditoría inválido...")
        st_no_pin, res_no_pin = make_http_request(
            f"{base_url}/api/v1/internal/accounting/ventas",
            method="GET",
            token=admin_token
        )
        st_bad_pin, res_bad_pin = make_http_request(
            f"{base_url}/api/v1/internal/accounting/ventas",
            method="GET",
            token=admin_token,
            extra_headers={"X-Audit-PIN": "PIN_INCORRECTO_9999"}
        )
        is_pin_sec_ok = (st_no_pin == 401 and res_no_pin.get("codigo") == "AUDIT_PIN_INVALID" and
                         st_bad_pin == 401 and res_bad_pin.get("codigo") == "AUDIT_PIN_INVALID")
        record_step(
            "Contabilidad Autorización: Rechazo de acceso a contabilidad sin PIN o con PIN inválido (HTTP 401/403)",
            "HTTP",
            401, st_no_pin,
            is_pin_sec_ok,
            f"Sin PIN: HTTP {st_no_pin} (código={res_no_pin.get('codigo')}); Con PIN incorrecto: HTTP {st_bad_pin} (código={res_bad_pin.get('codigo')})"
        )

        # ----------------------------------------------------------------------
        # PASO 56: Contabilidad Reporte: Administrador consulta reporte general consolidado con PIN oficial y período TODOS (HTTP 200)
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 2] Consultando reporte consolidado de ventas con PIN válido y período TODOS...")
        st_rep, res_rep = make_http_request(
            f"{base_url}/api/v1/internal/accounting/ventas",
            method="GET",
            token=admin_token,
            extra_headers={"X-Audit-PIN": "test-accounting-audit-pin"}
        )
        is_rep_ok = (st_rep == 200 and res_rep.get("periodoSeleccionado") == "TODOS" and
                     "totalVentasPen" in res_rep and "totalVentasUsd" in res_rep and
                     isinstance(res_rep.get("ventasConsolidadas"), list))
        record_step(
            "Contabilidad Reporte: Administrador consulta reporte general consolidado con PIN oficial y período TODOS (HTTP 200)",
            "HTTP",
            200, st_rep,
            is_rep_ok,
            f"Reporte HTTP {st_rep}: período={res_rep.get('periodoSeleccionado')}, ventasPen={res_rep.get('totalVentasPen')}, ventasUsd={res_rep.get('totalVentasUsd')}, comprobantes={res_rep.get('cantidadComprobantes')}"
        )

        # ----------------------------------------------------------------------
        # PASO 57: Contabilidad SIRE Validación Período: Rechazo de exportación sin período o con período TODOS (HTTP 400)
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 3] Verificando rechazo de exportación SIRE sin período y con período 'TODOS'...")
        st_sire_no_p, res_sire_no_p = make_http_request(
            f"{base_url}/api/v1/internal/accounting/ventas/export-sire",
            method="GET",
            token=admin_token,
            extra_headers={"X-Audit-PIN": "test-accounting-audit-pin", "Accept": "*/*"}
        )
        st_sire_todos, res_sire_todos = make_http_request(
            f"{base_url}/api/v1/internal/accounting/ventas/export-sire?periodo=TODOS",
            method="GET",
            token=admin_token,
            extra_headers={"X-Audit-PIN": "test-accounting-audit-pin", "Accept": "*/*"}
        )
        is_sire_p_ok = (st_sire_no_p == 400 and res_sire_no_p.get("codigo") == "PERIODO_REQUERIDO_SIRE" and
                        st_sire_todos == 400 and res_sire_todos.get("codigo") == "PERIODO_REQUERIDO_SIRE")
        record_step(
            "Contabilidad SIRE Validación Período: Rechazo de exportación sin período o con período TODOS (HTTP 400)",
            "HTTP",
            400, st_sire_no_p,
            is_sire_p_ok,
            f"Sin período: HTTP {st_sire_no_p} (código={res_sire_no_p.get('codigo')}); Con período TODOS: HTTP {st_sire_todos} (código={res_sire_todos.get('codigo')})"
        )

        # ----------------------------------------------------------------------
        # PASO 58: Contabilidad SIRE Validación Integridad: Rechazo con HTTP 422 si existen comprobantes incompletos identificando idComprobante e idOrden
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 4] Registrando comprobante incompleto para 2026-09 y comprobando rechazo HTTP 422...")
        docker_exec_psql(
            f"INSERT INTO ordenes (id_orden, id_user, codigo_orden, total, moneda, estado_orden, metodo_pago, fecha_orden) "
            f"VALUES (9101, {user1_id}, 'ORD-TEST-9101', 118.00, 'PEN', 'PAGADO', 'IZIPAY', '2026-09-15 10:00:00') "
            f"ON CONFLICT (id_orden) DO NOTHING;"
        )
        docker_exec_psql(
            "INSERT INTO comprobante_pago (id_comprobante, id_orden, tipo, serie, correlativo, fecha_emision, tipo_documento_identidad, numero_documento_identidad, razon_social_o_nombre, monto_subtotal, monto_igv, monto_total, moneda, estado) "
            "VALUES (9501, 9101, 'BOLETA', '', 1, '2026-09-15 10:00:00', 'DNI', '', '', 100.00, 18.00, 118.00, 'PEN', 'EMITIDO') "
            "ON CONFLICT (id_comprobante) DO NOTHING;"
        )
        st_incomp, res_incomp = make_http_request(
            f"{base_url}/api/v1/internal/accounting/ventas/export-sire?periodo=2026-09",
            method="GET",
            token=admin_token,
            extra_headers={"X-Audit-PIN": "test-accounting-audit-pin", "Accept": "*/*"}
        )
        incomp_det = res_incomp.get("registrosIncompletos", []) if isinstance(res_incomp, dict) else []
        has_expected_ids = any(r.get("idComprobante") == 9501 and r.get("idOrden") == 9101 for r in incomp_det)
        is_incomp_ok = (st_incomp == 422 and res_incomp.get("codigo") == "COMPROBANTES_INCOMPLETOS" and has_expected_ids)
        record_step(
            "Contabilidad SIRE Validación Integridad: Rechazo con HTTP 422 si existen comprobantes incompletos identificando idComprobante e idOrden",
            "HTTP",
            422, st_incomp,
            is_incomp_ok,
            f"Comprobante incompleto rechazado HTTP {st_incomp}: código={res_incomp.get('codigo')}, detalles={incomp_det}"
        )

        # ----------------------------------------------------------------------
        # PASO 59: Contabilidad SIRE Validación Divisa: Rechazo con HTTP 422 ante transacciones en USD sin tipo de cambio oficial verificado
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 5] Registrando transacción USD sin paridad oficial verificada y comprobando rechazo HTTP 422...")
        docker_exec_psql(
            f"INSERT INTO ordenes (id_orden, id_user, codigo_orden, total, moneda, estado_orden, metodo_pago, fecha_orden) "
            f"VALUES (9102, {user1_id}, 'ORD-TEST-9102', 118.00, 'USD', 'PAGADO', 'STRIPE', '2026-08-10 10:00:00') "
            f"ON CONFLICT (id_orden) DO NOTHING;"
        )
        docker_exec_psql(
            "INSERT INTO comprobante_pago (id_comprobante, id_orden, tipo, serie, correlativo, fecha_emision, tipo_documento_identidad, numero_documento_identidad, razon_social_o_nombre, monto_subtotal, monto_igv, monto_total, moneda, estado) "
            "VALUES (9502, 9102, 'FACTURA', 'F001', 1, '2026-08-10 10:00:00', 'RUC', '20123456789', 'EMPRESA USD S.A.C.', 100.00, 18.00, 118.00, 'USD', 'EMITIDO') "
            "ON CONFLICT (id_comprobante) DO NOTHING;"
        )
        st_usd_err, res_usd_err = make_http_request(
            f"{base_url}/api/v1/internal/accounting/ventas/export-sire?periodo=2026-08",
            method="GET",
            token=admin_token,
            extra_headers={"X-Audit-PIN": "test-accounting-audit-pin", "Accept": "*/*"}
        )
        usd_det = res_usd_err.get("registrosSinTipoCambio", []) if isinstance(res_usd_err, dict) else []
        has_usd_id = any(r.get("idComprobante") == 9502 for r in usd_det)
        is_usd_ok = (st_usd_err == 422 and res_usd_err.get("codigo") == "TIPO_CAMBIO_USD_NO_VERIFICADO" and has_usd_id)
        record_step(
            "Contabilidad SIRE Validación Divisa: Rechazo con HTTP 422 ante transacciones en USD sin tipo de cambio oficial verificado",
            "HTTP",
            422, st_usd_err,
            is_usd_ok,
            f"Transacción USD sin tipo de cambio oficial rechazada HTTP {st_usd_err}: código={res_usd_err.get('codigo')}, detalles={usd_det}"
        )

        # ----------------------------------------------------------------------
        # PASO 60: Contabilidad Integridad Transaccional: Comprobación de que las solicitudes 400 y 422 no mutaron registros ni causaron inconsistencias
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 6] Comprobando que los rechazos HTTP 400 y 422 preservaron la integridad de la base sin mutaciones no autorizadas...")
        db_incomp = docker_exec_psql("SELECT serie, correlativo, estado FROM comprobante_pago WHERE id_comprobante = 9501;")
        db_usd = docker_exec_psql("SELECT serie, correlativo, estado, moneda FROM comprobante_pago WHERE id_comprobante = 9502;")
        is_integ_ok = (db_incomp == "|1|EMITIDO" and db_usd == "F001|1|EMITIDO|USD")
        record_step(
            "Contabilidad Integridad Transaccional: Comprobación de que las solicitudes 400 y 422 no mutaron registros ni causaron inconsistencias",
            "DB",
            "NO_MUTADO", "NO_MUTADO" if is_integ_ok else "MUTADO",
            is_integ_ok,
            f"Registros en BD intactos tras intentos fallidos: 9501='{db_incomp}', 9502='{db_usd}'"
        )

        # ----------------------------------------------------------------------
        # PASO 61: Contabilidad Emisión y Anulación: Anulación formal mediante idComprobante persistente y motivo auditado (HTTP 200)
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 7] Emitiendo comprobante 9503 (orden 9103) y ejecutando anulación contable oficial...")
        docker_exec_psql(
            f"INSERT INTO ordenes (id_orden, id_user, codigo_orden, total, moneda, estado_orden, metodo_pago, fecha_orden) "
            f"VALUES (9103, {user1_id}, 'ORD-TEST-9103', 118.00, 'PEN', 'PAGADO', 'IZIPAY', '2026-10-15 12:00:00') "
            f"ON CONFLICT (id_orden) DO NOTHING;"
        )
        docker_exec_psql(
            "INSERT INTO comprobante_pago (id_comprobante, id_orden, tipo, serie, correlativo, fecha_emision, tipo_documento_identidad, numero_documento_identidad, razon_social_o_nombre, monto_subtotal, monto_igv, monto_total, moneda, estado) "
            "VALUES (9503, 9103, 'BOLETA', 'B001', 9503, '2026-10-15 12:00:00', 'DNI', '44556677', 'JUAN PEREZ', 100.00, 18.00, 118.00, 'PEN', 'EMITIDO') "
            "ON CONFLICT (id_comprobante) DO NOTHING;"
        )
        st_anul, res_anul = make_http_request(
            f"{base_url}/api/v1/internal/accounting/comprobantes/9503/anular",
            method="POST",
            payload={"motivo": "Anulación fiscal por comprobante emitido por error"},
            token=admin_token,
            extra_headers={"X-Audit-PIN": "test-accounting-audit-pin"}
        )
        is_anul_ok = (st_anul == 200 and res_anul.get("estado") == "ANULADO" and res_anul.get("idComprobante") == 9503)
        record_step(
            "Contabilidad Emisión y Anulación: Anulación formal mediante idComprobante persistente y motivo auditado (HTTP 200)",
            "HTTP",
            200, st_anul,
            is_anul_ok,
            f"Anulación HTTP {st_anul}: idComprobante={res_anul.get('idComprobante')}, estado={res_anul.get('estado')}, motivo='{res_anul.get('motivoAnulacion')}'"
        )

        # ----------------------------------------------------------------------
        # PASO 62: Contabilidad Persistencia Auditoría: Verificación en BD del estado ANULADO y registro inmutable en auditoria_contable
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 8] Verificando persistencia del comprobante anulado y pista inmutable en auditoria_contable...")
        db_anul = docker_exec_psql("SELECT estado, motivo_anulacion FROM comprobante_pago WHERE id_comprobante = 9503;")
        db_audit = docker_exec_psql("SELECT accion, entidad, entidad_id, motivo FROM auditoria_contable WHERE entidad_id = 9503 ORDER BY id_auditoria DESC LIMIT 1;")
        is_audit_ok = ("ANULADO" in db_anul and "COMPROBANTE_ANULADO" in db_audit and "9503" in db_audit)
        record_step(
            "Contabilidad Persistencia Auditoría: Verificación en BD del estado ANULADO y registro inmutable en auditoria_contable",
            "DB",
            "ANULADO_AUDITADO", "ANULADO_AUDITADO" if is_audit_ok else "NO_AUDITADO",
            is_audit_ok,
            f"BD comprobante 9503: '{db_anul}'; Auditoría contable: '{db_audit}'"
        )

        # ----------------------------------------------------------------------
        # PASO 63: Contabilidad SIRE Exportación Exitosa: Exportación con período YYYY-MM, Content-Disposition y CUO determinista CP%08d (HTTP 200)
        # ----------------------------------------------------------------------
        log("[CONTABILIDAD 9] Insertando comprobante 9504 activo para 2026-10 y descargando archivo SIRE oficial...")
        docker_exec_psql(
            f"INSERT INTO ordenes (id_orden, id_user, codigo_orden, total, moneda, estado_orden, metodo_pago, fecha_orden) "
            f"VALUES (9104, {user1_id}, 'ORD-TEST-9104', 236.00, 'PEN', 'PAGADO', 'IZIPAY', '2026-10-16 14:00:00') "
            f"ON CONFLICT (id_orden) DO NOTHING;"
        )
        docker_exec_psql(
            "INSERT INTO comprobante_pago (id_comprobante, id_orden, tipo, serie, correlativo, fecha_emision, tipo_documento_identidad, numero_documento_identidad, razon_social_o_nombre, monto_subtotal, monto_igv, monto_total, moneda, estado) "
            "VALUES (9504, 9104, 'FACTURA', 'F001', 9504, '2026-10-16 14:00:00', 'RUC', '20601234567', 'CLIENTE AUDITADO S.A.C.', 200.00, 36.00, 236.00, 'PEN', 'EMITIDO') "
            "ON CONFLICT (id_comprobante) DO NOTHING;"
        )
        st_sire_ok, res_sire_ok = make_http_request(
            f"{base_url}/api/v1/internal/accounting/ventas/export-sire?periodo=2026-10",
            method="GET",
            token=admin_token,
            extra_headers={"X-Audit-PIN": "test-accounting-audit-pin", "Accept": "*/*"}
        )
        sire_raw = res_sire_ok.get("raw", "") if isinstance(res_sire_ok, dict) else ""
        sire_headers = res_sire_ok.get("_headers", {}) if isinstance(res_sire_ok, dict) else {}
        disposition = sire_headers.get("content-disposition", "") or sire_headers.get("Content-Disposition", "")
        has_file_name = "LE2060981245120261000140100001111.txt" in disposition
        has_cuo_9503 = "CP00009503" in sire_raw
        has_cuo_9504 = "CP00009504" in sire_raw
        is_sire_export_ok = (st_sire_ok == 200 and has_file_name and has_cuo_9503 and has_cuo_9504)
        record_step(
            "Contabilidad SIRE Exportación Exitosa: Exportación con período YYYY-MM, Content-Disposition y CUO determinista CP%08d (HTTP 200)",
            "HTTP",
            200, st_sire_ok,
            is_sire_export_ok,
            f"SIRE HTTP {st_sire_ok}: Content-Disposition='{disposition}', CUO 9503 presente={has_cuo_9503}, CUO 9504 presente={has_cuo_9504}, tamaño={len(sire_raw)} bytes"
        )

        # ----------------------------------------------------------------------
        # PASO 64: Administración Usuarios: Separación estricta entre actualización de perfil (PUT) y modificación de rol auditada (PATCH) (HTTP 200)
        # ----------------------------------------------------------------------
        log("[ADMIN 1] Verificando que PUT /usuarios/{id} rechaza cambios no auditados de rol/activo (HTTP 403), actualiza perfil limpio (HTTP 200) y PATCH /usuarios/{id}/rol audita rol...")
        # 1. Intento no auditado de alterar rol mediante PUT -> rechazado con HTTP 403
        st_put_err, res_put_err = make_http_request(
            f"{base_url}/api/v1/usuarios/{user2_id}",
            method="PUT",
            payload={
                "nombres": "Usuario Dos",
                "apellidos": "Segundo",
                "email": "usuario2.segundo@lysandri.invalid",
                "telefono": "+51999888999",
                "rol": "ADMIN",
                "activo": True
            },
            token=admin_token
        )
        # 2. Actualización legítima de perfil sin campos restringidos
        st_put_ok, res_put_ok = make_http_request(
            f"{base_url}/api/v1/usuarios/{user2_id}",
            method="PUT",
            payload={
                "nombres": "Usuario Dos Actualizado",
                "apellidos": "Segundo",
                "email": "usuario2.segundo@lysandri.invalid",
                "telefono": "+51999888999"
            },
            token=admin_token
        )
        db_user2_after_put = docker_exec_psql(f"SELECT rol, activo, nombres FROM usuario WHERE id_user = {user2_id};")
        u2_rol_put, u2_act_put, u2_nom_put = db_user2_after_put.split("|")
        put_safe = (st_put_err == 403 and st_put_ok == 200 and u2_nom_put == "Usuario Dos Actualizado" and u2_rol_put != "ADMIN" and u2_act_put == "f")

        # 3. Modificación oficial de rol con auditoría mediante PATCH
        st_patch_r, res_patch_r = make_http_request(
            f"{base_url}/api/v1/usuarios/{user2_id}/rol?rol=CONTABILIDAD&motivo=Asignacion_oficial_auditoria",
            method="PATCH",
            token=admin_token
        )
        db_user2_after_patch = docker_exec_psql(f"SELECT rol FROM usuario WHERE id_user = {user2_id};")
        is_admin_u_ok = (put_safe and st_patch_r == 200 and db_user2_after_patch == "CONTABILIDAD")
        record_step(
            "Administración Usuarios: Separación estricta entre actualización de perfil (PUT) y modificación de rol auditada (PATCH) (HTTP 200)",
            "HTTP",
            200, st_patch_r,
            is_admin_u_ok,
            f"PUT con rol rechazado HTTP {st_put_err}; PUT perfil HTTP {st_put_ok} (nombre='{u2_nom_put}', rol='{u2_rol_put}'); PATCH rol a CONTABILIDAD (HTTP {st_patch_r}): nuevo rol='{db_user2_after_patch}'"
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
            
            # Guardar reporte sha256-jar.txt en DELIVERY_EVIDENCIAS_DIR y CIERRE_EVIDENCIAS_DIR
            sha_file = os.path.join(DELIVERY_EVIDENCIAS_DIR, "sha256-jar.txt")
            with open(sha_file, "w", encoding="utf-8") as f:
                f.write(f"{jar_sha256}  lysandri-backend-1.0.0-SNAPSHOT.jar\n")
            log(f"[EVIDENCIA] Checksum SHA-256 guardado en: {sha_file}")

            sha_cierre = os.path.join(CIERRE_EVIDENCIAS_DIR, "sha256-jar.txt")
            with open(sha_cierre, "w", encoding="utf-8") as f:
                f.write(f"{jar_sha256}  lysandri-backend-1.0.0-SNAPSHOT.jar\n")

            # Guardar reporte estructurado y logs en CIERRE_EVIDENCIAS_DIR
            cierre_json = os.path.join(CIERRE_EVIDENCIAS_DIR, "recorrido-http-jar-real.json")
            cierre_log = os.path.join(CIERRE_EVIDENCIAS_DIR, "registro-ejecucion-validar-jar.log")
            with open(cierre_json, "w", encoding="utf-8") as jf:
                json.dump(summary, jf, indent=2, ensure_ascii=False)
            shutil.copy2(LOG_FILE_PATH, cierre_log)
            if os.path.isfile(JAR_CONSOLE_LOG_PATH):
                shutil.copy2(JAR_CONSOLE_LOG_PATH, os.path.join(CIERRE_EVIDENCIAS_DIR, "jar-ejecucion-consola.log"))
            if 'jar_log_path_2' in locals() and os.path.isfile(jar_log_path_2):
                shutil.copy2(jar_log_path_2, os.path.join(CIERRE_EVIDENCIAS_DIR, "jar-ejecucion-consola-proceso2.log"))
            if 'jar_log_path_3' in locals() and os.path.isfile(jar_log_path_3):
                shutil.copy2(jar_log_path_3, os.path.join(CIERRE_EVIDENCIAS_DIR, "jar-ejecucion-consola-proceso3.log"))

            # Copiar reportes de Surefire XML en DELIVERY_EVIDENCIAS_DIR/surefire-reports/ y CIERRE_EVIDENCIAS_DIR/surefire-reports/
            surefire_src = os.path.join(BASE_DIR, "backend", "target", "surefire-reports")
            surefire_dst = os.path.join(DELIVERY_EVIDENCIAS_DIR, "surefire-reports")
            surefire_cierre_dst = os.path.join(CIERRE_EVIDENCIAS_DIR, "surefire-reports")
            os.makedirs(surefire_dst, exist_ok=True)
            os.makedirs(surefire_cierre_dst, exist_ok=True)
            if os.path.isdir(surefire_src):
                copied_xmls = 0
                for f_name in os.listdir(surefire_src):
                    if f_name.startswith("TEST-") and f_name.endswith(".xml"):
                        shutil.copy2(os.path.join(surefire_src, f_name), os.path.join(surefire_dst, f_name))
                        shutil.copy2(os.path.join(surefire_src, f_name), os.path.join(surefire_cierre_dst, f_name))
                        copied_xmls += 1
                log(f"[EVIDENCIA] Copiados {copied_xmls} reportes XML de Surefire a: {surefire_dst} y {surefire_cierre_dst}")
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
        if 'moodle_server' in locals() and moodle_server:
            moodle_server.stop()

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

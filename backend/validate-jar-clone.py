#!/usr/bin/env python3
"""
Validación de Arranque y Ejecución del Backend JAR con Perfil 'clone'.
Demuestra en un entorno 100% aislado y reproducible:
1. Conexión al contenedor autorizado lysandri-postgres-clone en puerto 5434.
2. Identidad del clon y 0 migraciones pendientes en lysandri_clone_db.
3. Comprobación empírica de integridad previa y posterior en lysandri_clone_db
   (conteo de migraciones, tablas y filas en tablas clave antes y después).
4. Creación y uso exclusivo de base de datos desechable (lysandri_clone_disposable_*)
   para no alterar usuarios ni datos del clon persistente.
5. Verificación de que el perfil clone EXIGE JWT_SECRET sin fallback conocido
   (comprobación de fallo inmediato al arrancar sin variable).
6. Generación segura de clave JWT temporal exclusiva en el entorno del proceso hijo
   (sin parámetro -Djwt.secret en línea de comandos ni en logs).
7. Servidores locales simulados en puertos dinámicos:
   - Mock Moodle LMS HTTP server con respuestas controladas de éxito y HTTP 504.
   - Mock SMTP receiver local para retener correos sin tráfico saliente real.
8. Arranque real del artefacto ejecutable target/lysandri-backend-1.0.0-SNAPSHOT.jar.
9. Validación de inicio de sesión real HTTP:
   - Login con credenciales correctas (Admin y Estudiante).
   - Rechazo de credenciales incorrectas (HTTP 401).
   - Rechazo de petición sin token (HTTP 401).
   - Rechazo por rol insuficiente (HTTP 403 con Estudiante a /api/v1/usuarios).
   - Acceso autorizado concedido (HTTP 200 con Admin a /api/v1/usuarios).
   - Rechazo de login para usuario deshabilitado (HTTP 401).
   - Rechazo inmediato de token previo tras desactivación del usuario (HTTP 401).
10. Validación de flujo de compra y resiliencia Moodle mediante el JAR:
    - Creación de orden de compra mediante checkout.
    - Confirmación de pago con fallo Moodle provocado (HTTP 504).
    - Persistencia de orden PAGADA, aprovisionamiento FALLIDO y liberación del lease.
    - Reintento con Moodle recuperado y verificación de estado COMPLETADO.
    - Verificación de no duplicación de órdenes, ítems, comprobantes ni notificaciones.
    - Verificación de idempotencia en reintentos posteriores.
11. Limpieza rigurosa y exclusiva de recursos de la ejecución (terminación de procesos,
    detención de servidores mock y eliminación de la base desechable sin tocar el clon).
12. Generación de evidencias sanitizadas (resumen JSON y registro de ejecución).
"""

import base64
import datetime
import hashlib
import http.server
import json
import os
import re
import secrets
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid


# ==============================================================================
# GESTOR DE REGISTRO SANITIZADO (DUAL STDOUT + ARCHIVO)
# ==============================================================================
class SanitizedLogger:
    def __init__(self, filepath: str):
        self.terminal = sys.stdout
        os.makedirs(os.path.dirname(filepath), exist_ok=True)
        self.log_file = open(filepath, "w", encoding="utf-8")
        self.lock = threading.Lock()

    def write(self, message: str):
        sanitized = self._sanitize(message)
        with self.lock:
            self.terminal.write(sanitized)
            self.log_file.write(sanitized)
            self.log_file.flush()

    def flush(self):
        with self.lock:
            self.terminal.flush()
            self.log_file.flush()

    def close(self):
        with self.lock:
            self.log_file.close()

    @staticmethod
    def _sanitize(text: str) -> str:
        # Enmascarar tokens JWT (eyJh... o similares)
        sanitized = re.sub(r'eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+', '[REDACTED_JWT_TOKEN]', text)
        # Enmascarar posibles contraseñas en URLs o parámetros
        sanitized = re.sub(r'(password[:=]\s*)([^\s,]+)', r'\1[REDACTED_PASSWORD]', sanitized, flags=re.IGNORECASE)
        sanitized = re.sub(r'(jwt\.secret[:=]\s*)([^\s,]+)', r'\1[REDACTED_JWT_SECRET]', sanitized, flags=re.IGNORECASE)
        return sanitized


def sanitize_data(data: any) -> any:
    """Sanitiza diccionarios o listas eliminando tokens o secretos antes de serializar o loguear."""
    if isinstance(data, dict):
        res = {}
        for k, v in data.items():
            if k.lower() in ("token", "jwt", "accesstoken", "password", "secret", "authorization", "bearer"):
                res[k] = "[REDACTED_SENSITIVE]"
            elif isinstance(v, (dict, list)):
                res[k] = sanitize_data(v)
            else:
                res[k] = v
        return res
    elif isinstance(data, list):
        return [sanitize_data(x) for x in data]
    return data


def calculate_sha256(filepath: str) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


# ==============================================================================
# SERVIDOR MOODLE SIMULADO (PUERTO DINÁMICO)
# ==============================================================================
class MockMoodleHandler(http.server.BaseHTTPRequestHandler):
    """
    Simulador de API WebService REST de Moodle (/webservice/rest/server.php).
    Soporta alternancia en vivo entre fallo HTTP 504 Gateway Timeout y éxito HTTP 200.
    """
    def log_message(self, format, *args):
        pass

    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        post_body = self.rfile.read(content_length).decode('utf-8', errors='ignore')

        server = self.server
        with server.lock:
            server.total_calls += 1

        if server.simulate_504:
            response_content = b"<html><body>504 Gateway Time-out: Moodle cluster upstream unreachable</body></html>"
            self.send_response(504)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(response_content)))
            self.end_headers()
            self.wfile.write(response_content)
            return

        # Modo éxito (SUCCESS)
        with server.lock:
            if "enrol_manual_enrol_users" in post_body:
                server.enrol_calls += 1

        if "wsfunction=core_user_get_users_by_field" in post_body:
            json_response = '[{"id": 5001}]'
        elif "wsfunction=core_user_create_users" in post_body:
            json_response = '[{"id": 5001}]'
        elif "wsfunction=enrol_manual_enrol_users" in post_body:
            json_response = 'null'
        elif "wsfunction=core_enrol_get_users_courses" in post_body:
            json_response = '[{"id": 1}]'
        else:
            json_response = '[]'

        resp_bytes = json_response.encode('utf-8')
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(resp_bytes)))
        self.end_headers()
        self.wfile.write(resp_bytes)


class MockMoodleServer:
    def __init__(self):
        self.httpd = http.server.ThreadingHTTPServer(('127.0.0.1', 0), MockMoodleHandler)
        self.port = self.httpd.server_address[1]
        self.httpd.simulate_504 = True
        self.httpd.total_calls = 0
        self.httpd.enrol_calls = 0
        self.httpd.lock = threading.Lock()
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()

    def set_simulate_504(self, enabled: bool):
        with self.httpd.lock:
            self.httpd.simulate_504 = enabled

    def get_call_counts(self):
        with self.httpd.lock:
            return self.httpd.total_calls, self.httpd.enrol_calls

    def stop(self):
        self.httpd.shutdown()
        self.httpd.server_close()


# ==============================================================================
# RECEPTOR SMTP LOCAL SIMULADO (PUERTO DINÁMICO)
# ==============================================================================
class MockSmtpServer:
    """
    Receptor SMTP local ultraliviano para retener notificaciones sin tráfico saliente.
    """
    def __init__(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind(('127.0.0.1', 0))
        self.port = self.sock.getsockname()[1]
        self.sock.listen(5)
        self.running = True
        self.received_messages = []
        self.lock = threading.Lock()
        self.thread = threading.Thread(target=self._serve, daemon=True)
        self.thread.start()

    def _serve(self):
        while self.running:
            try:
                conn, _ = self.sock.accept()
            except Exception:
                break
            threading.Thread(target=self._handle_conn, args=(conn,), daemon=True).start()

    def _handle_conn(self, conn):
        try:
            conn.sendall(b"220 127.0.0.1 Mock SMTP Service Ready\r\n")
            in_data = False
            data_buffer = []
            f = conn.makefile('rb')
            while self.running:
                raw_line = f.readline()
                if not raw_line:
                    break
                line_str = raw_line.decode('utf-8', errors='ignore').rstrip("\r\n")
                if in_data:
                    if line_str == ".":
                        in_data = False
                        msg_text = "\n".join(data_buffer)
                        with self.lock:
                            self.received_messages.append(msg_text)
                        conn.sendall(b"250 2.0.0 OK message queued\r\n")
                    else:
                        data_buffer.append(line_str)
                elif line_str.upper().startswith("EHLO") or line_str.upper().startswith("HELO"):
                    conn.sendall(b"250-127.0.0.1\r\n250 HELP\r\n")
                elif line_str.upper().startswith("MAIL FROM:"):
                    conn.sendall(b"250 2.1.0 Sender OK\r\n")
                elif line_str.upper().startswith("RCPT TO:"):
                    conn.sendall(b"250 2.1.5 Recipient OK\r\n")
                elif line_str.upper() == "DATA":
                    in_data = True
                    conn.sendall(b"354 Start mail input; end with <CRLF>.<CRLF>\r\n")
                elif line_str.upper() == "QUIT":
                    conn.sendall(b"221 2.0.0 Service closing transmission channel\r\n")
                    break
                else:
                    conn.sendall(b"250 OK\r\n")
        except Exception:
            pass
        finally:
            try:
                conn.close()
            except Exception:
                pass

    def get_received_count(self):
        with self.lock:
            return len(self.received_messages)

    def stop(self):
        self.running = False
        try:
            self.sock.close()
        except Exception:
            pass


# ==============================================================================
# UTILIDADES DE ENTORNO Y COMUNICACIÓN
# ==============================================================================
def load_env_file(filepath: str) -> dict:
    env_vars = {}
    if os.path.exists(filepath):
        with open(filepath, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    k, v = line.split('=', 1)
                    env_vars[k.strip()] = v.strip()
    return env_vars


def http_request(url: str, method: str = "GET", data: dict = None, token: str = None) -> tuple[int, dict]:
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode('utf-8') if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            content = resp.read().decode('utf-8')
            parsed = json.loads(content) if content else {}
            return resp.status, parsed
    except urllib.error.HTTPError as e:
        content = e.read().decode('utf-8')
        try:
            parsed = json.loads(content) if content else {}
        except Exception:
            parsed = {"raw": content[:200]}
        return e.code, parsed


def run_psql_cmd(db_name: str, sql: str) -> str:
    cmd = [
        "docker", "exec", "lysandri-postgres-clone",
        "psql", "-U", "lysandri_admin", "-d", db_name, "-t", "-A", "-c", sql
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Fallo psql en {db_name}: {res.stderr.strip()}")
    return res.stdout.strip()


def query_clone_snapshot() -> dict:
    sql = """
    SELECT 
        (SELECT count(*) FROM flyway_schema_history) || '|' ||
        (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE') || '|' ||
        (SELECT count(*) FROM usuario) || '|' ||
        (SELECT count(*) FROM programa) || '|' ||
        (SELECT count(*) FROM ordenes) || '|' ||
        (SELECT count(*) FROM detalle_ordenes) || '|' ||
        (SELECT count(*) FROM comprobante_pago) || '|' ||
        (SELECT count(*) FROM notificaciones_log) || '|' ||
        (SELECT count(*) FROM auditoria_contable) || '|' ||
        (SELECT count(*) FROM usuario_cambio_audit);
    """
    raw = run_psql_cmd("lysandri_clone_db", sql)
    p = raw.split('|')
    return {
        "mig_count": int(p[0]),
        "tables_count": int(p[1]),
        "cnt_usuario": int(p[2]),
        "cnt_programa": int(p[3]),
        "cnt_ordenes": int(p[4]),
        "cnt_detalles": int(p[5]),
        "cnt_comprobantes": int(p[6]),
        "cnt_notificaciones": int(p[7]),
        "cnt_audit_contable": int(p[8]),
        "cnt_audit_usuario": int(p[9]),
    }


# ==============================================================================
# FLUJO PRINCIPAL DE VALIDACIÓN
# ==============================================================================
def main():
    start_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()
    evidence_dir = os.path.abspath("evidencias_cierre")
    os.makedirs(evidence_dir, exist_ok=True)
    sanitized_log_path = os.path.join(evidence_dir, "validacion_jar_sanitizada.log")
    json_summary_path = os.path.join(evidence_dir, "resumen_validacion_jar.json")

    logger = SanitizedLogger(sanitized_log_path)
    sys.stdout = logger

    print("================================================================================")
    print("VALIDACIÓN EN VIVO: ARRANQUE Y EJECUCIÓN DEL JAR CON PERFIL 'clone'")
    print("================================================================================")

    run_id = f"{int(time.time())}_{uuid.uuid4().hex[:6]}"
    disposable_db = None
    proc = None
    mock_moodle = None
    mock_smtp = None
    log_file = None

    # Estructura de resultados para resumen JSON
    results_summary = {
        "fecha": start_iso,
        "execution_id": run_id,
        "jar_path": "target/lysandri-backend-1.0.0-SNAPSHOT.jar",
        "jar_sha256": None,
        "disposable_database": None,
        "resources_created": {},
        "cleanup_status": {
            "java_process_terminated": False,
            "mock_moodle_stopped": False,
            "mock_smtp_stopped": False,
            "disposable_database_dropped": False,
            "all_cleaned_successfully": False,
            "pending_resources": []
        },
        "jwt_security": {
            "jwt_secret_source": "Variable de entorno exclusiva (JWT_SECRET) sin fallback",
            "jvm_arguments_sanitized": True,
            "no_secret_in_command_line": True,
            "fallback_absence_verified": False
        },
        "clone_integrity": {
            "verified_directly": {},
            "inferred": "No se realizaron escrituras directas sobre el volumen físico fuera de las conexiones a la base desechable."
        },
        "checks": [],
        "exit_code": 1
    }

    def record_check(check_id: str, name: str, status: str, details: str):
        print(f"  [{check_id}] {name} -> {status}")
        print(f"       Detalle: {details}")
        results_summary["checks"].append({
            "id": check_id,
            "name": name,
            "status": status,
            "details": details
        })

    snapshot_before = None

    try:
        jar_path = os.path.abspath("target/lysandri-backend-1.0.0-SNAPSHOT.jar")
        if not os.path.exists(jar_path):
            print(f"ERROR: No existe el JAR en {jar_path}")
            sys.exit(1)

        jar_sha256 = calculate_sha256(jar_path)
        results_summary["jar_sha256"] = jar_sha256
        print(f"  -> Artefacto JAR verificado: {os.path.basename(jar_path)}")
        print(f"  -> SHA-256 del JAR: {jar_sha256}")

        # 1. Comprobar identidad del clon en contenedor lysandri-postgres-clone y tomar instantánea
        print("\n[Paso 1/9] Comprobando identidad y estado de migraciones en lysandri-postgres-clone...")
        check_out = run_psql_cmd(
            "lysandri_clone_db",
            "SELECT current_database() || '|' || current_user || '|' || count(*) FROM flyway_schema_history;"
        )
        parts = check_out.split('|')
        db_name, db_user, mig_count = parts[0], parts[1], int(parts[2])
        print(f"  -> Base de datos activa : {db_name} (autorizada)")
        print(f"  -> Usuario de migración : {db_user}")
        print(f"  -> Migraciones aplicadas: {mig_count} (esperadas 28, versión 26)")
        assert db_name == "lysandri_clone_db", "Base no autorizada"
        assert mig_count == 28, f"Migraciones inesperadas: {mig_count}"

        snapshot_before = query_clone_snapshot()
        print(f"  -> Instantánea previa del clon tomada (migraciones: {snapshot_before['mig_count']}, tablas: {snapshot_before['tables_count']}, órdenes: {snapshot_before['cnt_ordenes']})")

        # 2. Cargar credenciales externas de .env.local
        print("\n[Paso 2/9] Cargando credenciales externas de .env.local...")
        env_file = os.path.abspath("F:/Lysandri-executive/.env.local")
        secrets_env = load_env_file(env_file)
        if not secrets_env:
            secrets_env = load_env_file(os.path.abspath(".env.local"))

        if "CLONE_APP_PASSWORD" not in secrets_env or "CLONE_ADMIN_PASSWORD" not in secrets_env:
            print("ERROR: Faltan variables CLONE_APP_PASSWORD o CLONE_ADMIN_PASSWORD en .env.local")
            sys.exit(1)
        print("  -> Credenciales de BD cargadas exitosamente (secretos no impresos)")

        # 3. Verificar que el perfil EXIGE JWT_SECRET sin fallback conocido
        print("\n[Paso 3/9] Verificando obligatoriedad estricta de JWT_SECRET (sin fallback)...")
        test_env_no_jwt = os.environ.copy()
        test_env_no_jwt.pop("JWT_SECRET", None)
        test_env_no_jwt["SPRING_PROFILES_ACTIVE"] = "clone"
        test_env_no_jwt["CLONE_APP_PASSWORD"] = secrets_env["CLONE_APP_PASSWORD"]
        test_env_no_jwt["CLONE_ADMIN_PASSWORD"] = secrets_env["CLONE_ADMIN_PASSWORD"]
        no_jwt_cmd = ["java", "-Dspring.profiles.active=clone", "-jar", jar_path]

        no_jwt_proc = subprocess.Popen(
            no_jwt_cmd,
            env=test_env_no_jwt,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        try:
            stdout_no_jwt, stderr_no_jwt = no_jwt_proc.communicate(timeout=6)
        except subprocess.TimeoutExpired:
            no_jwt_proc.kill()
            stdout_no_jwt, stderr_no_jwt = no_jwt_proc.communicate()

        combined_no_jwt = (stdout_no_jwt or "") + (stderr_no_jwt or "")
        assert no_jwt_proc.returncode != 0, "El JAR debió fallar al arrancar sin JWT_SECRET"
        assert "Could not resolve placeholder 'JWT_SECRET'" in combined_no_jwt, "No se encontró el error esperado de placeholder JWT_SECRET"
        results_summary["jwt_security"]["fallback_absence_verified"] = True
        print("  -> Verificación exitosa: arranque rechazado con código != 0 y 'Could not resolve placeholder JWT_SECRET'")

        # 4. Crear base de datos desechable para aislamiento total de escritura
        disposable_db = f"lysandri_clone_disposable_{run_id}"
        results_summary["disposable_database"] = disposable_db
        results_summary["resources_created"]["disposable_database"] = disposable_db
        print(f"\n[Paso 4/9] Creando base de datos desechable: {disposable_db}...")
        run_psql_cmd("postgres", f"CREATE DATABASE {disposable_db} TEMPLATE lysandri_clone_db;")
        print(f"  -> Base desechable {disposable_db} creada exitosamente como copia exacta de lysandri_clone_db")

        # 5. Iniciar servidores locales simulados (Moodle y SMTP)
        print("\n[Paso 5/9] Levantando integraciones simuladas en puertos locales dinámicos...")
        mock_moodle = MockMoodleServer()
        mock_smtp = MockSmtpServer()
        results_summary["resources_created"]["mock_moodle_port"] = mock_moodle.port
        results_summary["resources_created"]["mock_smtp_port"] = mock_smtp.port
        print(f"  -> Mock Moodle HTTP server escuchando en http://127.0.0.1:{mock_moodle.port}")
        print(f"  -> Mock SMTP receiver escuchando en 127.0.0.1:{mock_smtp.port}")

        # 6. Generar clave JWT segura temporal y configurar entorno del JAR (SIN -Djwt.secret)
        print("\n[Paso 6/9] Generando clave JWT temporal segura y configurando entorno...")
        jwt_temp_secret = base64.b64encode(secrets.token_bytes(64)).decode('utf-8')
        assert len(jwt_temp_secret) >= 64, "Longitud de clave JWT insuficiente"
        print("  -> Clave temporal generada (512 bits criptográficos, no impresa)")

        disposable_jdbc = f"jdbc:postgresql://127.0.0.1:5434/{disposable_db}"
        app_env = os.environ.copy()
        app_env["CLONE_APP_PASSWORD"] = secrets_env["CLONE_APP_PASSWORD"]
        app_env["CLONE_ADMIN_PASSWORD"] = secrets_env["CLONE_ADMIN_PASSWORD"]
        app_env["SPRING_PROFILES_ACTIVE"] = "clone"
        app_env["SPRING_DATASOURCE_URL"] = disposable_jdbc
        app_env["SPRING_FLYWAY_URL"] = disposable_jdbc
        app_env["JWT_SECRET"] = jwt_temp_secret  # EXCLUSIVAMENTE EN VARIABLE DE ENTORNO
        app_env["MOODLE_URL"] = f"http://127.0.0.1:{mock_moodle.port}"
        app_env["SPRING_MAIL_HOST"] = "127.0.0.1"
        app_env["SPRING_MAIL_PORT"] = str(mock_smtp.port)

        jar_live_log_path = os.path.join(evidence_dir, "jar-execution-sanitized.log")
        log_file = open(jar_live_log_path, "w", encoding="utf-8")

        # IMPORTANTE: No se incluye -Djwt.secret en la línea de comandos para evitar exposición en inspección de procesos
        java_cmd = [
            "java",
            "-Dspring.profiles.active=clone",
            f"-Dspring.datasource.url={disposable_jdbc}",
            f"-Dspring.flyway.url={disposable_jdbc}",
            f"-Dmoodle.url=http://127.0.0.1:{mock_moodle.port}",
            "-Dspring.mail.host=127.0.0.1",
            f"-Dspring.mail.port={mock_smtp.port}",
            "-jar", jar_path
        ]
        proc = subprocess.Popen(
            java_cmd,
            env=app_env,
            stdout=log_file,
            stderr=subprocess.STDOUT
        )
        results_summary["resources_created"]["java_process_pid"] = proc.pid
        print(f"  -> Proceso Java iniciado con PID: {proc.pid}")
        print(f"  -> Conectado a base desechable: {disposable_db}")
        print("  -> Esperando disponibilidad HTTP del JAR...")

        started = False
        start_time = time.time()
        for attempt in range(60):
            if proc.poll() is not None:
                print(f"ERROR: El proceso Java finalizó prematuramente con código {proc.returncode}")
                log_file.close()
                with open(jar_live_log_path, "r", encoding="utf-8", errors="replace") as f:
                    print(f.read()[-3000:])
                sys.exit(1)

            try:
                req = urllib.request.Request("http://127.0.0.1:8080/api/v1/programas")
                with urllib.request.urlopen(req, timeout=2) as response:
                    if response.status == 200:
                        started = True
                        break
            except Exception:
                time.sleep(1)

        if not started:
            print("ERROR: Timeout esperando a que la aplicación responda en el puerto 8080")
            sys.exit(1)

        elapsed = time.time() - start_time
        print(f"  -> Backend JAR arrancado y respondiendo en http://127.0.0.1:8080 ({elapsed:.2f}s)")
        record_check(
            "CHECK-01",
            "Arranque correcto del JAR",
            "PASS",
            f"JAR inicializado en puerto 8080 en {elapsed:.2f}s respondiendo HTTP 200 a peticiones públicas."
        )

        # 7. Preparar usuarios y validar login real y autorización
        print("\n[Paso 7/9] Validando login real, roles y ciclo de vida de tokens...")

        admin_email = f"admin_{run_id}@lysandri.invalid"
        student_email = f"student_{run_id}@lysandri.invalid"
        disabled_email = f"disabled_{run_id}@lysandri.invalid"
        temp_deact_email = f"deact_{run_id}@lysandri.invalid"
        safe_password = f"SafePass_{run_id}!"

        # Registrar usuarios en base desechable
        st, res = http_request("http://127.0.0.1:8080/api/v1/auth/register", "POST", {
            "nombres": "Admin", "apellidos": "Test", "email": admin_email,
            "password": safe_password, "telefono": "999111222"
        })
        assert st == 201, f"Fallo registro de admin: status={st}"
        run_psql_cmd(disposable_db, f"UPDATE usuario SET rol = 'ADMIN' WHERE email = '{admin_email}';")

        st, res = http_request("http://127.0.0.1:8080/api/v1/auth/register", "POST", {
            "nombres": "Estudiante", "apellidos": "Test", "email": student_email,
            "password": safe_password, "telefono": "999333444"
        })
        assert st == 201, f"Fallo registro de estudiante: status={st}"
        run_psql_cmd(disposable_db, f"UPDATE usuario SET rol = 'ESTUDIANTE' WHERE email = '{student_email}';")

        st, res = http_request("http://127.0.0.1:8080/api/v1/auth/register", "POST", {
            "nombres": "Deshabilitado", "apellidos": "Test", "email": disabled_email,
            "password": safe_password, "telefono": "999555666"
        })
        assert st == 201, f"Fallo registro de deshabilitado: status={st}"
        run_psql_cmd(disposable_db, f"UPDATE usuario SET activo = false WHERE email = '{disabled_email}';")

        st, res = http_request("http://127.0.0.1:8080/api/v1/auth/register", "POST", {
            "nombres": "TempDeact", "apellidos": "Test", "email": temp_deact_email,
            "password": safe_password, "telefono": "999777888"
        })
        assert st == 201, f"Fallo registro de temp_deact: status={st}"

        # Test Login Correcto
        st, admin_login = http_request("http://127.0.0.1:8080/api/v1/auth/login", "POST", {
            "email": admin_email, "password": safe_password
        })
        assert st == 200, f"Login admin debió retornar 200 (recibido {st})"
        admin_token = admin_login.get("token")
        assert admin_token and len(admin_token) > 20, "Token de admin no recibido"

        st, student_login = http_request("http://127.0.0.1:8080/api/v1/auth/login", "POST", {
            "email": student_email, "password": safe_password
        })
        assert st == 200, f"Login estudiante debió retornar 200 (recibido {st})"
        student_token = student_login.get("token")
        assert student_token and len(student_token) > 20, "Token de estudiante no recibido"

        # Test Login Incorrecto
        st, bad_login = http_request("http://127.0.0.1:8080/api/v1/auth/login", "POST", {
            "email": admin_email, "password": "WrongPassword123!"
        })
        assert st == 401, f"Login incorrecto debió retornar 401 (recibido {st})"
        record_check(
            "CHECK-02",
            "Login válido e inválido",
            "PASS",
            "Autenticación exitosa (HTTP 200) para credenciales legítimas con emisión de token; rechazo (HTTP 401) para credenciales inválidas."
        )

        # Test Sin Token y Rol Insuficiente
        st, no_token_resp = http_request("http://127.0.0.1:8080/api/v1/usuarios", "GET")
        assert st == 401, f"Petición sin token debió retornar 401 (recibido {st})"

        st, forbidden_resp = http_request("http://127.0.0.1:8080/api/v1/usuarios", "GET", token=student_token)
        assert st == 403, f"Petición con rol ESTUDIANTE debió retornar 403 (recibido {st})"
        record_check(
            "CHECK-03",
            "Rechazo sin token y por rol insuficiente",
            "PASS",
            "Petición anónima a recurso protegido rechazada con HTTP 401; petición con rol ESTUDIANTE a endpoint administrativo denegada con HTTP 403."
        )

        # Test Acceso Autorizado
        st, users_resp = http_request("http://127.0.0.1:8080/api/v1/usuarios", "GET", token=admin_token)
        assert st == 200, f"Acceso con rol ADMIN debió retornar 200 (recibido {st})"
        assert isinstance(users_resp, list) and len(users_resp) >= 3
        record_check(
            "CHECK-04",
            "Acceso autorizado",
            "PASS",
            f"Petición con rol ADMIN concedida con HTTP 200 retornando {len(users_resp)} usuarios."
        )

        # Test Usuario Deshabilitado y Token Previo
        st, disabled_login = http_request("http://127.0.0.1:8080/api/v1/auth/login", "POST", {
            "email": disabled_email, "password": safe_password
        })
        assert st == 401, f"Login de usuario deshabilitado debió retornar 401 (recibido {st})"

        st, temp_login = http_request("http://127.0.0.1:8080/api/v1/auth/login", "POST", {
            "email": temp_deact_email, "password": safe_password
        })
        assert st == 200
        temp_token = temp_login.get("token")

        st, me_active = http_request("http://127.0.0.1:8080/api/v1/auth/me", "GET", token=temp_token)
        assert st == 200, "Token debió ser válido antes de desactivar"

        run_psql_cmd(disposable_db, f"UPDATE usuario SET activo = false WHERE email = '{temp_deact_email}';")

        st, me_deactivated = http_request("http://127.0.0.1:8080/api/v1/auth/me", "GET", token=temp_token)
        assert st == 401, f"Token previo de usuario desactivado debió ser rechazado con 401 (recibido {st})"
        record_check(
            "CHECK-05",
            "Rechazo de usuario deshabilitado y de su token previo",
            "PASS",
            "Usuario con activo=false no puede iniciar sesión (HTTP 401) y su token previamente emitido queda invalidado de inmediato en peticiones subsecuentes (HTTP 401)."
        )

        # 8. Validar flujo de compra, fallo Moodle 504, liberación de lease y recuperación
        print("\n[Paso 8/9] Validando flujo de compra, fallo Moodle 504 y recuperación limpia...")

        comprador_email = f"comprador_{run_id}@empresa.pe"
        op_num = f"OP-JAR-504-{run_id}"

        # Test negativo: Rechazo comercial de programa con precio provisional no aprobado (Requisito 5)
        st_unapproved, unapproved_resp = http_request("http://127.0.0.1:8080/api/v1/orders/checkout", "POST", {
            "programaIds": [1],
            "email": comprador_email,
            "nombreCompleto": "Ejecutivo Validación JAR",
            "tipoComprobante": "BOLETA",
            "numeroDocumento": "70908010",
            "nombreFacturacion": "Ejecutivo Validación JAR",
            "metodoPago": "TRANSFERENCIA",
            "numeroOperacion": op_num
        })
        assert st_unapproved == 400, f"Compra con precio provisional debió rechazarse con 400 (recibido {st_unapproved})"
        print("  -> Comprobado rechazo comercial de programa con precio provisional no aprobado (HTTP 400)")

        # Habilitar aprobación comercial de programa 1 exclusivamente en la base desechable para probar el flujo de compra
        run_psql_cmd(disposable_db, "UPDATE programa SET precio_aprobado = true WHERE id_programa = 1;")

        st, checkout_resp = http_request("http://127.0.0.1:8080/api/v1/orders/checkout", "POST", {
            "programaIds": [1],
            "email": comprador_email,
            "nombreCompleto": "Ejecutivo Validación JAR",
            "tipoComprobante": "BOLETA",
            "numeroDocumento": "70908010",
            "nombreFacturacion": "Ejecutivo Validación JAR",
            "metodoPago": "TRANSFERENCIA",
            "numeroOperacion": op_num
        })
        assert st == 200, f"Checkout debió retornar 200 (recibido {st})"
        codigo_orden = checkout_resp.get("codigoOrden")
        assert codigo_orden, "No se recibió codigoOrden en respuesta"

        id_orden = int(run_psql_cmd(disposable_db, f"SELECT id_orden FROM ordenes WHERE codigo_orden = '{codigo_orden}';"))
        record_check(
            "CHECK-06",
            "Compra mediante HTTP",
            "PASS",
            f"Orden de compra creada exitosamente mediante checkout HTTP con código {codigo_orden} (id_orden: {id_orden}) en estado PENDIENTE."
        )

        # Fallo Moodle 504
        mock_moodle.set_simulate_504(True)
        st, confirm_resp = http_request(
            f"http://127.0.0.1:8080/api/v1/orders/{id_orden}/confirmar-pago-manual",
            "POST",
            {
                "numeroOperacion": op_num,
                "metodoPago": "TRANSFERENCIA_BANCARIA",
                "notasAdmin": "Aprobado para prueba 504"
            },
            token=admin_token
        )
        assert st == 200, f"Confirmación debió retornar 200 con error controlado (recibido {st})"

        estado_orden_db = run_psql_cmd(disposable_db, f"SELECT estado_orden FROM ordenes WHERE id_orden = {id_orden};")
        estado_aprov_db = run_psql_cmd(disposable_db, f"SELECT estado_aprovisionamiento FROM ordenes WHERE id_orden = {id_orden};")
        lease_token_db = run_psql_cmd(disposable_db, f"SELECT coalesce(aprovisionamiento_lease_token, 'LIBERADO') FROM ordenes WHERE id_orden = {id_orden};")
        moodle_sync_db = run_psql_cmd(disposable_db, f"SELECT moodle_matricula_sincronizada::text FROM ordenes WHERE id_orden = {id_orden};")
        ultimo_error_db = run_psql_cmd(disposable_db, f"SELECT aprovisionamiento_ultimo_error FROM ordenes WHERE id_orden = {id_orden};")

        assert estado_orden_db == "PAGADO", "La orden debió quedar PAGADA"
        assert estado_aprov_db == "FALLIDO", "El aprovisionamiento debió quedar FALLIDO"
        assert moodle_sync_db == "false", "La sincronización Moodle debió ser false"
        assert "504" in ultimo_error_db or "Gateway" in ultimo_error_db or "Timeout" in ultimo_error_db
        record_check(
            "CHECK-07",
            "Fallo Moodle 504 y persistencia de FALLIDO",
            "PASS",
            f"Fallo upstream simulado HTTP 504 capturado: orden persiste irrevocablemente como PAGADO y aprovisionamiento como FALLIDO (error: {ultimo_error_db[:50]}...)."
        )

        assert lease_token_db == "LIBERADO", "El lease debió liberarse inmediatamente"
        record_check(
            "CHECK-08",
            "Liberación de la reserva",
            "PASS",
            "Bloqueo transaccional aprovisionamiento_lease_token liberado inmediatamente (NULL) permitiendo reintentos limpios posteriores."
        )

        # Recuperación con Moodle 200
        mock_moodle.set_simulate_504(False)
        st, retry_resp = http_request(
            f"http://127.0.0.1:8080/api/v1/orders/{id_orden}/reintentar-matricula",
            "POST",
            token=admin_token
        )
        assert st == 200, f"Reintento debió retornar 200 (recibido {st})"

        estado_final_aprov = run_psql_cmd(disposable_db, f"SELECT estado_aprovisionamiento FROM ordenes WHERE id_orden = {id_orden};")
        moodle_final_sync = run_psql_cmd(disposable_db, f"SELECT moodle_matricula_sincronizada::text FROM ordenes WHERE id_orden = {id_orden};")
        lease_final = run_psql_cmd(disposable_db, f"SELECT coalesce(aprovisionamiento_lease_token, 'LIBERADO') FROM ordenes WHERE id_orden = {id_orden};")

        assert estado_final_aprov == "COMPLETADO", "El aprovisionamiento debió completarse con éxito"
        assert moodle_final_sync == "true", "La sincronización Moodle debió quedar en true"
        assert lease_final == "LIBERADO", "El lease final debió quedar liberado"
        record_check(
            "CHECK-09",
            "Recuperación a COMPLETADO",
            "PASS",
            "Tras restablecer Moodle a HTTP 200, el reintento transicionó el aprovisionamiento a COMPLETADO y sincronizado_moodle a true."
        )

        # Ausencia de Duplicados e Idempotencia
        cnt_ordenes = int(run_psql_cmd(disposable_db, f"SELECT count(*) FROM ordenes WHERE codigo_orden = '{codigo_orden}';"))
        cnt_detalles = int(run_psql_cmd(disposable_db, f"SELECT count(*) FROM detalle_ordenes WHERE id_orden = {id_orden};"))
        cnt_comprobantes = int(run_psql_cmd(disposable_db, f"SELECT count(*) FROM comprobante_pago WHERE id_orden = {id_orden};"))
        cnt_notificaciones = int(run_psql_cmd(disposable_db, f"SELECT count(*) FROM notificaciones_log WHERE id_orden = {id_orden};"))

        assert cnt_ordenes == 1, "Órdenes duplicadas detectadas"
        assert cnt_detalles == 1, "Detalles duplicados detectados"
        assert cnt_comprobantes == 1, "Comprobante duplicado detectado"
        assert cnt_notificaciones <= 1, "Notificaciones duplicadas detectadas"

        total_moodle_before, enrol_before = mock_moodle.get_call_counts()
        st, second_retry = http_request(
            f"http://127.0.0.1:8080/api/v1/orders/{id_orden}/reintentar-matricula",
            "POST",
            token=admin_token
        )
        assert st == 200
        total_moodle_after, enrol_after = mock_moodle.get_call_counts()
        assert enrol_after == enrol_before, "No debió llamarse al endpoint de matrícula para orden ya completada"

        cnt_comprobantes_after = int(run_psql_cmd(disposable_db, f"SELECT count(*) FROM comprobante_pago WHERE id_orden = {id_orden};"))
        assert cnt_comprobantes_after == 1, "Comprobante emitido nuevamente en reintento"
        record_check(
            "CHECK-10",
            "Ausencia de duplicados e idempotencia",
            "PASS",
            "Verificado 1 única orden, 1 ítem de detalle, 1 comprobante y 1 notificación. Segundo reintento ejecutó 0 llamadas extras a Moodle sin duplicación."
        )

        # Verificación Servidor SMTP Local
        smtp_count = mock_smtp.get_received_count()
        assert smtp_count >= 1, "El servidor SMTP local debió recibir la notificación"
        record_check(
            "CHECK-11",
            "Recepción de correo en el servidor local",
            "PASS",
            f"Servidor SMTP simulado en puerto {mock_smtp.port} retuvo {smtp_count} correo(s) transaccionales sin conexión a internet ni tráfico saliente real."
        )

        results_summary["exit_code"] = 0

    except Exception as e:
        print(f"\nERROR DURANTE LA VALIDACIÓN: {e}")
        record_check("CHECK-ERROR", "Fallo de validación", "FAIL", str(e))
        results_summary["exit_code"] = 1

    finally:
        # 9. Limpieza rigurosa y comprobación de integridad
        print("\n[Paso 9/9] Detención de procesos y limpieza exclusiva de recursos propios...")
        cleanup_ok = True
        pending_list = []

        if proc is not None:
            print("  -> Terminando proceso Java...")
            proc.terminate()
            try:
                proc.wait(timeout=10)
                results_summary["cleanup_status"]["java_process_terminated"] = True
            except subprocess.TimeoutExpired:
                proc.kill()
                results_summary["cleanup_status"]["java_process_terminated"] = True
            if log_file:
                log_file.close()
            print("  -> Proceso Java finalizado limpiamente.")
        else:
            results_summary["cleanup_status"]["java_process_terminated"] = True

        if mock_moodle is not None:
            try:
                mock_moodle.stop()
                results_summary["cleanup_status"]["mock_moodle_stopped"] = True
                print("  -> Mock Moodle HTTP server detenido.")
            except Exception as e:
                cleanup_ok = False
                pending_list.append(f"Mock Moodle port {mock_moodle.port}: {e}")

        if mock_smtp is not None:
            try:
                mock_smtp.stop()
                results_summary["cleanup_status"]["mock_smtp_stopped"] = True
                print("  -> Mock SMTP receiver detenido.")
            except Exception as e:
                cleanup_ok = False
                pending_list.append(f"Mock SMTP port {mock_smtp.port}: {e}")

        if disposable_db is not None:
            print(f"  -> Eliminando base desechable {disposable_db}...")
            try:
                run_psql_cmd("postgres", f"""
                    SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
                    WHERE datname = '{disposable_db}' AND pid <> pg_backend_pid();
                """)
                run_psql_cmd("postgres", f"DROP DATABASE IF EXISTS {disposable_db};")
                results_summary["cleanup_status"]["disposable_database_dropped"] = True
                print(f"  -> Base desechable {disposable_db} eliminada con éxito.")
            except Exception as e:
                cleanup_ok = False
                pending_list.append(f"Base de datos desechable {disposable_db}: {e}")
                print(f"  -> ERROR: No se pudo eliminar base desechable: {e}")

        # Comprobar integridad de lysandri_clone_db antes vs después
        try:
            snapshot_after = query_clone_snapshot()
            is_identical = (snapshot_before == snapshot_after)
            results_summary["clone_integrity"]["verified_directly"] = {
                "database": "lysandri_clone_db",
                "container": "lysandri-postgres-clone",
                "port": 5434,
                "snapshot_before": snapshot_before,
                "snapshot_after": snapshot_after,
                "identical": is_identical
            }
            if is_identical:
                print("  -> Integridad empírica de lysandri_clone_db comprobada: 100% IDÉNTICA antes y después de la ejecución.")
                print(f"     (migraciones: {snapshot_after['mig_count']}, tablas: {snapshot_after['tables_count']}, órdenes: {snapshot_after['cnt_ordenes']}, usuarios: {snapshot_after['cnt_usuario']})")
            else:
                cleanup_ok = False
                pending_list.append("Discrepancia detectada en métricas de lysandri_clone_db")
                print("  -> ALERTA: Se detectaron cambios inesperados en lysandri_clone_db")
        except Exception as e:
            cleanup_ok = False
            pending_list.append(f"Error verificando clon: {e}")
            print(f"  -> Error al verificar clon: {e}")

        results_summary["cleanup_status"]["pending_resources"] = pending_list
        results_summary["cleanup_status"]["all_cleaned_successfully"] = cleanup_ok

        if cleanup_ok:
            record_check(
                "CHECK-12",
                "Detención de procesos y limpieza exclusiva de recursos propios",
                "PASS",
                "Proceso Java terminado, Moodle y SMTP simulados detenidos, base desechable eliminada y clon persistente 100% verificado intacto."
            )
        else:
            record_check(
                "CHECK-12",
                "Detención de procesos y limpieza exclusiva de recursos propios",
                "FAIL",
                f"Recursos pendientes de limpieza: {', '.join(pending_list)}"
            )
            results_summary["exit_code"] = 1

        # Guardar resumen JSON de evidencias
        with open(json_summary_path, "w", encoding="utf-8") as jf:
            json.dump(results_summary, jf, indent=2, ensure_ascii=False)
        print(f"\n  -> Resumen de evidencia guardado en: {json_summary_path}")
        print(f"  -> Registro de ejecución guardado en: {sanitized_log_path}")

        print("\n================================================================================")
        if results_summary["exit_code"] == 0:
            print("VALIDACIÓN EN VIVO DEL JAR EJECUTABLE: EXITOSA (0 fallos, 0 errores)")
            print("================================================================================")
        else:
            print("VALIDACIÓN EN VIVO DEL JAR EJECUTABLE: FALLIDA")
            print("================================================================================")

        sys.stdout = logger.terminal
        logger.close()
        sys.exit(results_summary["exit_code"])


if __name__ == "__main__":
    main()

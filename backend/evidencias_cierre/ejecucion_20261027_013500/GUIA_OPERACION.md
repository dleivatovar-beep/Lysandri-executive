# Guía de Operación y Verificación del Backend

Esta guía documenta los procedimientos de verificación, configuración, arranque seguro y mantenimiento operacional del backend de **Lysandri Executive Education**.

---

## 1. Restricciones de Seguridad Críticas

1. **Base de Datos Original Protegida (Puerto 5432)**:
   - La base de datos original `lysandri_db` en el puerto `5432` está terminantemente protegida.
   - **Ningún script ni comando de prueba o desarrollo puede conectarse o migrar el puerto 5432**.
   - Los scripts de arranque abortan inmediatamente (Código 1) si detectan el puerto 5432, el nombre `lysandri_db` o el host `lysandri-postgres` configurado, antes de emitir cualquier paquete TCP.

2. **Entornos Autorizados**:
   - Para pruebas unitarias y de arquitectura: se ejecutan en memoria local con mocks e inyección de dependencias (`BUILD SUCCESS` sin dependencias externas).
   - Para pruebas de integración con persistencia: se ejecutan exclusivamente en contenedores **PostgreSQL desechables** generados en memoria y volúmenes temporales eliminados al concluir cada suite.
   - Para verificación y ejecución local: se utiliza exclusivamente el clon autorizado `lysandri_clone_db` en el puerto `5434`.

3. **Protección de Secretos y Privacidad (PII)**:
   - Queda estrictamente prohibido registrar o imprimir contraseñas, secretos, tokens JWT, hashes o enlaces de activación en la consola o bitácoras de logs.
   - Los datos personales del comprador (nombre completo, correo, número de documento de identidad) nunca se registran en los logs de órdenes ni de pasarelas de pago (`SanitizerUtils`).
   - La variable `JWT_SECRET` se valida con un mínimo estricto de 256 bits (32 caracteres) o se genera de forma temporal segura (512 bits) en memoria si no se provee.

---

## 2. Variables de Entorno Requeridas

Las credenciales para el entorno clon se leen desde las variables de entorno del sistema o desde `backend/.env.local`:

| Variable | Descripción | Valor por Defecto / Requisito |
| :--- | :--- | :--- |
| `CLONE_APP_PASSWORD` | Contraseña del usuario operativo `lysandri_app` | **Obligatoria** |
| `CLONE_ADMIN_PASSWORD` | Contraseña del usuario administrador `lysandri_admin` | **Obligatoria** |
| `MOODLE_URL` | URL del LMS Moodle | `http://localhost:8081` (puerto distinto del backend) |
| `MOODLE_TOKEN` | Token del Web Service Moodle | Token configurado en el aula virtual |
| `FRONTEND_URL` | URL pública autorizada del Frontend | `http://localhost:5173` |
| `JWT_SECRET` | Clave secreta HMAC-SHA de 256+ bits | Se autogenera temporalmente si no se provee |
| `DISABLE_VERIFICATION_TOOL` | Opcional para pruebas negativas de script | `0` por defecto |

---

## 3. Uso del Script de Arranque Seguro (`iniciar-backend.ps1`)

El script se encuentra tanto en la raíz `iniciar-backend.ps1` como en `backend/iniciar-backend.ps1` con comportamiento idéntico y delegación transparente.

### Sintaxis
```powershell
.\iniciar-backend.ps1 [-SoloVerificar] [-Perfil <clone|dev|test>] [-CloneHost <host>] [-ClonePort <puerto>] [-CloneDatabase <base>] [-HttpPort <puerto>] [-ForzarHerramientaAusente]
```

### Parámetros
- `-SoloVerificar`: Ejecuta de manera estricta todas las comprobaciones previas (Java 17+, artefacto JAR, variables y secretos, MOODLE_URL, conectividad TCP e identidad autenticada SQL de solo lectura para `lysandri_app` y `lysandri_admin`) y finaliza con código 0 **sin arrancar Java**.
- `-Perfil`: Perfil de Spring Boot (`clone` por defecto).
- `-CloneHost`: Host de la base clon (`127.0.0.1` por defecto).
- `-ClonePort`: Puerto del PostgreSQL destino (`5434` por defecto). El puerto 5432 está prohibido.
- `-CloneDatabase`: Base de datos autorizada (`lysandri_clone_db` o prefijo `lysandri_clone_`).
- `-HttpPort`: Puerto HTTP del backend (`8080` por defecto).
- `-ForzarHerramientaAusente`: Simula la ausencia del cliente `psql` para validación negativa (Código 4).

### Códigos de Salida del Script
- `0`: Verificación exitosa o proceso terminado normalmente.
- `1`: Error de configuración, violación de seguridad (puerto 5432, base original `lysandri_db` o host `lysandri-postgres`), MOODLE_URL inválida o conflicto de puerto.
- `2`: Fallo de conectividad TCP al puerto del PostgreSQL destino.
- `3`: Conflicto de puerto HTTP local (puerto en uso por otro proceso).
- `4`: Fallo de autenticación/identidad en base de datos o herramienta de verificación no disponible.

---

## 4. Procesamiento y Recuperación de Notificaciones (Patrón Outbox)

La emisión y envío de correos de activación está desacoplada de dependencias externas (Moodle y facturación):

1. **Persistencia Duradera en la Misma Transacción Financiera**:
   - En `OrderFinancialTransactionService.confirmarPagoFinanciero`, cuando la orden pasa a `PAGADO`, se genera el token de activación y se registra inmediatamente una tarea en la tabla `outbox_notificacion_activacion`.
   - Si la transacción financiera falla o hace rollback, la tarea de activación nunca se persiste.
   - Si la transacción se confirma, la tarea queda persistida duraderamente con estado `PENDIENTE`.

2. **Independencia de Moodle y Facturación**:
   - Si Moodle responde con timeout HTTP 504 o la pasarela de facturación electrónica sufre una degradación, la activación **no se cancela**. La tarea queda en cola y se procesa de forma autónoma.

3. **Cifrado en Reposo del Enlace**:
   - El enlace de activación (que contiene el token en texto plano necesario para el clic del usuario) se almacena cifrado con **AES-256-GCM** mediante `ActivationCryptoService`.
   - Cuando SMTP entrega el correo exitosamente o cuando se alcanza el límite de reintentos, el enlace cifrado se sobreescribe con `null` en la base de datos, garantizando que no queden secretos residuales.

4. **Reintentos y Backoff Exponencial**:
   - Intervalos de reintento: Intento 1: +5s, Intento 2: +15s, Intento 3: +45s, Intento 4: +135s, Intento 5: +405s.
   - Si tras 5 intentos el servidor SMTP sigue inaccesible, el estado pasa a `FALLIDO` y el error se sanitiza para no exponer información sensible.

5. **Recuperación tras Reinicio del Backend**:
   - Al reiniciar el backend o invocar `ActivationOutboxService.procesarTareasPendientes()`, las tareas en estado `PENDIENTE` cuya fecha `proximo_intento` sea menor o igual al instante actual son adquiridas mediante bloqueo pesimista o lease token (`PROCESANDO`).

6. **Protección contra Reemplazo de Enlaces (Obsolescencia)**:
   - Al despachar una tarea pendiente, el servicio valida si el token asociado continúa activo. Si el usuario solicitó un reenvío posterior, el token anterior fue revocado; la tarea antigua pasa inmediatamente a `CANCELADO_OBSOLETO`, previniendo que un enlace antiguo sobrescriba un enlace más reciente.

---

## 5. Transición Segura de Tokens Antiguos (Migración V28)

1. **Vulnerabilidad Histórica Corregida**:
   - Anteriormente, el backend calculaba el hash de la entrada pero permitía un fallback de búsqueda directa en claro si el hash no coincidía.
   - Esto permitía que un actor que tuviera acceso de solo lectura al hash en la base pudiera enviarlo directamente como credencial de activación.

2. **Modelo Estricto Actual**:
   - El cliente envía únicamente el token en texto plano recibido por correo.
   - El backend calcula `tokenHash = SHA256(tokenRecibido)` y busca **exclusivamente** en la columna `token_hash`.
   - Se eliminaron por completo las consultas en texto plano (`findByToken`, `findByTokenForUpdate`).

3. **Migración `V28`**:
   - Se aplica la migración `V28__invalidate_legacy_plaintext_tokens_and_outbox.sql`.
   - Marca automáticamente como `usado = true` y `revocado = true` cualquier registro histórico donde el token no cumpla con el formato de hash hexadecimal de 64 caracteres (`length(token) != 64 OR token !~ '^[0-9a-f]{64}$'`).
   - Los usuarios con tokens heredados deben utilizar la funcionalidad pública `POST /api/v1/auth/solicitar-activacion` para recibir un enlace seguro reemitido.

---

## 6. Comandos de Reproducción Seguros

### A. Ejecución de Pruebas Unitarias y de Servicios
```powershell
cd backend
.\mvnw.cmd test -Dtest="!*IntegrationTest,!*TestDatabase,!*PostgresJdbcConnectivityTest"
```
*Ejecuta 181 pruebas unitarias cubriendo adaptadores de activación, outbox, sanitización, controladores, pagos y lógica de negocio con 0 fallos.*

### B. Ejecución de Batería de Scripts de Arranque (19 Casos Aislados)
```powershell
cd backend
powershell.exe -ExecutionPolicy Bypass -File .\test-scripts-behavior.ps1
```
*Ejecuta los 19 casos de prueba del script de arranque (códigos 0, 1, 2, 3 y 4), verificando bloqueo de puerto 5432, identidad autenticada, MOODLE_URL, puerto HTTP y paso seguro de argumentos.*

### C. Verificación de Arranque del JAR sin Modificar Bases
```powershell
cd backend
powershell.exe -ExecutionPolicy Bypass -File .\iniciar-backend.ps1 -SoloVerificar -ClonePort 15434 -HttpPort 18088
```

### D. Empaquetado del JAR
```powershell
cd backend
.\mvnw.cmd package -DskipTests
```
*Genera `target/lysandri-backend-1.0.0-SNAPSHOT.jar`.*

---

## 7. Limitaciones y Consideraciones Operativas

1. **Dependencia de Docker para Pruebas de Integración**:
   - Las pruebas de integración que heredan de `PostgresTestDatabase` (`ConcurrentTokenConsumptionIntegrationTest`, `GuestActivationHttpMockSmtpIntegrationTest`, `PostgresPersistenceIntegrationTest`) requieren que el daemon de Docker Desktop esté en ejecución para inicializar los contenedores PostgreSQL desechables. En entornos sin Docker activo, estas pruebas arrojan error de conexión al socket de Docker (`//./pipe/dockerDesktopLinuxEngine`), manteniéndose en estado **IMPLEMENTADO** y cubiertas a nivel de componentes por las pruebas unitarias y de arquitectura (**VERIFICADO**).
2. **Entrega de Correo (SMTP)**:
   - El protocolo SMTP no proporciona garantías de entrega final a la bandeja de entrada del destinatario ("delivered to inbox"). El Outbox garantiza de forma duradera la **aceptación por el servidor de retransmisión SMTP** (código 250 OK) y maneja reintentos con backoff progresivo ante caídas transitorias.

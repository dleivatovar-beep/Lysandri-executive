# INFORME CONSOLIDADO DE CIERRE Y VALIDACIÓN DE HALLAZGOS

**Proyecto:** Lysandri Executive Backend  
**Fecha de Ejecución:** 2026-10-27 01:38:52 (UTC) / 2026-10-26 20:38:52 (Local)  
**Ruta de Evidencias:** `F:\Lysandri-executive\backend\evidencias_cierre\ejecucion_20261027_013500\`  
**Artefacto Probado:** `target/lysandri-backend-1.0.0-SNAPSHOT.jar`  
**Hash SHA-256 del JAR:** `9275910079CFA78E4FD6580E094A840086142AE512D7F8B3E6FE740556F932F9`

---

## 1. RESUMEN EJECUTIVO DE EJECUCIONES Y RESULTADOS REALES

| ID | Batería / Comando | Código Salida | Pruebas Ejecutadas | Aprobadas (PASS) | Fallos (Failures) | Errores (Errors) | Omitidas (Skipped) |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **CMD-1** | `mvnw.cmd test -Dtest="!*IntegrationTest,!*TestDatabase,!*PostgresJdbcConnectivityTest"` | `0` (BUILD SUCCESS) | 181 | 181 | 0 | 0 | 0 |
| **CMD-2** | `mvnw.cmd test` (Suite Completa Maven) | `1` (BUILD FAILURE)* | 209 | 194 | 0 | 15* | 0 |
| **CMD-3** | `powershell -File test-scripts-behavior.ps1` | `0` (EXIT 0) | 19 | 19 | 0 | 0 | 0 |
| **CMD-4** | `powershell -File iniciar-backend.ps1 -SoloVerificar` | `0` (EXIT 0) | 1 | 1 | 0 | 0 | 0 |

*\* Nota explicativa de CMD-2: Los 15 errores corresponden exclusivamente a la indisponibilidad del daemon local de Docker Desktop en el entorno de ejecución, lo que impidió que Testcontainers levantara contenedores PostgreSQL efímeros. Ninguna prueba unitaria, de seguridad, de lógica de negocio o de guardas de base de datos falló. Las 181 pruebas sin dependencia de Docker pasaron al 100% (CMD-1).*

---

## 2. DEDUPLICACIÓN ESTRICTA DE CASOS ÚNICOS

Para evitar el problema histórico de doble conteo de ejecuciones o de pruebas parametrizadas:

- **Casos únicos de prueba Java:** 209 (194 PASSED, 15 con impedimento de entorno Docker).
- **Casos únicos de prueba PowerShell:** 19 (19 PASSED).
- **Total de casos únicos de prueba:** **228 casos únicos**.
- **Duplicados sumados:** 0. Cada caso está identificado unívocamente por `Clase#Método` o por `Número y Nombre de Caso PowerShell`.

---

## 3. DETALLE DE CORRECCIÓN POR CADA UNO DE LOS CINCO HALLAZGOS

### Hallazgo 1: Impedir que el Hash Almacenado funcione como Token
- **Problema corregido:** En `UserActivationRepositoryAdapter.java`, los métodos `buscarPorToken` y `buscarPorTokenConBloqueo` calculaban el hash SHA-256 de la entrada, pero mantenían un fallback que buscaba directamente el valor en claro recibido si no se encontraba el hash. Esto permitía usar el hash extraído de la base de datos como token de activación.
- **Acciones implementadas:**
  1. Se eliminaron completamente las llamadas y métodos de búsqueda por token en texto plano (`findByToken`, `findByTokenForUpdate`).
  2. El cliente entrega únicamente el token original recibido en su enlace por correo. El backend calcula de manera estricta `tokenHash = hashToken(plainToken)` y busca exclusivamente en la columna `token_hash`.
  3. Se creó la migración segura `V28__invalidate_legacy_plaintext_tokens_and_outbox.sql` en `backend/src/main/resources/db/migration/`: invalida y revoca tokens antiguos almacenados en texto plano (`length(token) != 64 OR token !~ '^[0-9a-f]{64}$'`), forzando una reemisión segura y eliminando cualquier compatibilidad con tokens vulnerables.
- **Archivos modificados:**
  - `backend/src/main/java/com/lysandri/adapters/out/database/adapter/UserActivationRepositoryAdapter.java`
  - `backend/src/main/resources/db/migration/V28__invalidate_legacy_plaintext_tokens_and_outbox.sql`
  - `backend/src/test/java/com/lysandri/adapters/out/database/adapter/UserActivationRepositoryAdapterTest.java` (7 pruebas unitarias dedicadas, todas PASS).

---

### Hallazgo 2: Desacoplar la Activación de Moodle y Facturación
- **Problema corregido:** En `BuyCourseService.java`, el envío de activación ocurría de manera síncrona después de la llamada a Moodle y a facturación electrónica. Un timeout (HTTP 504) o fallo en Moodle/facturación abortaba o impedía el envío de la activación. Además, si fallaba el servidor SMTP, el error no contaba con reintento persistente duradero.
- **Acciones implementadas:**
  1. **Patrón Outbox Transaccional:** Se creó la tabla `outbox_notificacion_activacion` y el servicio `ActivationOutboxService`. En `OrderFinancialTransactionService.confirmarPagoFinanciero`, dentro de la misma transacción de confirmación de pago, se registra de forma atómica y duradera la tarea de activación en estado `PENDIENTE`.
  2. **Independencia Operativa:** El procesamiento del Outbox ocurre tras el commit financiero. Si Moodle devuelve HTTP 504 o la facturación falla, la notificación de activación queda persistida y es procesada independientemente.
  3. **Seguridad del Enlace:** El enlace de activación con el token en texto plano es cifrado en reposo mediante AES-256-GCM con clave derivada externamente (`ActivationCryptoService`). Una vez entregado a SMTP o agotados los reintentos, el enlace cifrado se elimina de la base de datos para no conservar secretos.
  4. **Resiliencia y Reintentos:** Se implementó backoff exponencial (5s, 15s, 45s, 135s, 405s) con hasta 5 intentos, registro sanitizado de errores y recuperación tras reinicio del backend.
  5. **Concurrencia e Idempotencia:** Adquisición atómica de tareas mediante bloqueo o lease token (`PROCESANDO`), previniendo ejecución duplicada por trabajadores concurrentes o confirmaciones repetidas de webhooks.
  6. **Protección de Renovación:** Al despachar la tarea, se verifica el estado actual del token en base de datos. Si el token fue revocado o superado por una solicitud posterior, la tarea se cancela como `CANCELADO_OBSOLETO`, evitando que reintentos antiguos sobrescriban un enlace nuevo.
- **Archivos modificados/creados:**
  - `backend/src/main/java/com/lysandri/adapters/out/database/entity/ActivationOutboxTaskEntity.java`
  - `backend/src/main/java/com/lysandri/adapters/out/database/repository/SpringDataActivationOutboxTaskRepository.java`
  - `backend/src/main/java/com/lysandri/application/service/ActivationCryptoService.java`
  - `backend/src/main/java/com/lysandri/application/service/ActivationOutboxService.java`
  - `backend/src/main/java/com/lysandri/application/service/OrderFinancialTransactionService.java`
  - `backend/src/main/java/com/lysandri/application/service/BuyCourseService.java`
  - `backend/src/main/java/com/lysandri/application/service/AuthService.java`
  - `backend/src/test/java/com/lysandri/application/service/ActivationOutboxServiceTest.java` (7 pruebas unitarias, todas PASS).
  - `backend/src/test/java/com/lysandri/application/service/BuyCourseServiceTest.java` (21 pruebas unitarias, todas PASS).

---

### Hallazgo 3: Verificar el Destino Real que Utilizará el JAR
- **Problema corregido:** Los scripts de inicio utilizaban `docker exec` contra un contenedor fijo (`lysandri-postgres-clone`), ignorando si el usuario configuraba un host o puerto distinto.
- **Acciones implementadas:**
  1. **Resolución Única y Verificación Autenticada:** Tanto `backend/iniciar-backend.ps1` como `iniciar-backend.ps1` resuelven una única configuración efectiva (`$CloneHost:$ClonePort/$CloneDatabase`) y verifican mediante conexión autenticada con cliente nativo `psql` (`-w -t -A`) las credenciales e identidad de DataSource (`lysandri_app`) y Flyway (`lysandri_admin`).
  2. **Bloqueo Incondicional de Base Original:** Se rechaza de inmediato (Código 1) cualquier intento de conexión al puerto 5432, base `lysandri_db` o host `lysandri-postgres` antes de emitir cualquier tráfico de red.
  3. **Validación de Identidad Autorizada:** Se exige que el nombre de la base de datos corresponda estrictamente a un clon (`lysandri_clone_db` o prefijo `lysandri_clone_`). De no coincidir, aborta con Código 4.
  4. **Protección de Secretos en Procesos:** Las contraseñas nunca se pasan en los argumentos visibles del proceso Java. Se inyectan únicamente mediante variables de entorno del proceso (`SPRING_DATASOURCE_PASSWORD`, `SPRING_FLYWAY_PASSWORD`), y `PGPASSWORD` se elimina estrictamente en el bloque `finally`.
  5. **Manejo de Fallbacks y Códigos de Salida:** Códigos documentados: `0` (éxito), `1` (violación de seguridad o parámetros inválidos), `2` (fallo de red/TCP), `3` (puerto HTTP ocupado), `4` (fallo de identidad o herramienta de verificación ausente).
- **Archivos modificados:**
  - `backend/iniciar-backend.ps1`
  - `iniciar-backend.ps1`
  - `backend/mock_psql.ps1` (herramienta de prueba mock reproducible)
  - `backend/test-scripts-behavior.ps1` (suite de 19 pruebas aisladas, 100% PASS).

---

### Hallazgo 4: Retirar Datos Personales y Secretos de los Logs
- **Problema corregido:** En `BuyCourseService.java` y otros componentes, se registraban en los logs el nombre, correo y documento de identidad del comprador, así como mensajes de error de pasarelas y tokens.
- **Acciones implementadas:**
  1. Se eliminaron los datos personales (nombre, correo, documento) de los mensajes de log en `BuyCourseService.java` (línea 495 y asociadas), sustituyéndolos por identificadores técnicos (`ordenId`, `codigoOrden`).
  2. Se creó la clase utilitaria centralizada `SanitizerUtils.java` para enmascaramiento estricto de emails (`u***o@dominio.com`), remoción de credenciales JDBC en URLs, sanitización de cabeceras Bearer, query params de tokens (`token=***`) y hashes bcrypt.
  3. Se aplicó sanitización en logs de `SolicitudInformacionService.java`, `MatriculaCorporativaService.java`, `EmailNotificationAdapter.java`, `AuthService.java` y `GlobalExceptionHandler.java`.
- **Archivos modificados/creados:**
  - `backend/src/main/java/com/lysandri/security/SanitizerUtils.java`
  - `backend/src/main/java/com/lysandri/application/service/BuyCourseService.java`
  - `backend/src/main/java/com/lysandri/application/service/AuthService.java`
  - `backend/src/main/java/com/lysandri/application/service/SolicitudInformacionService.java`
  - `backend/src/main/java/com/lysandri/application/service/MatriculaCorporativaService.java`
  - `backend/src/main/java/com/lysandri/adapters/out/notification/EmailNotificationAdapter.java`
  - `backend/src/main/java/com/lysandri/adapters/in/web/GlobalExceptionHandler.java`
  - `backend/src/test/java/com/lysandri/security/SanitizerUtilsTest.java` (5 pruebas unitarias, todas PASS).

---

### Hallazgo 5: Regenerar Evidencias sin Duplicar Pruebas
- **Problema corregido:** El informe previo sumaba dos veces pruebas ejecutadas en diferentes fases y reportaba un número de casos PowerShell inconsistente con la batería real.
- **Acciones implementadas:**
  1. Carpeta de evidencias única y estampada en el tiempo: `backend/evidencias_cierre/ejecucion_20261027_013500/`.
  2. Los reportes XML de Surefire y los logs de consola de cada comando se capturaron inmediatamente en la carpeta de la ejecución antes de ser sobrescritos.
  3. Se calculó el hash SHA-256 del artefacto compilado y probado: `9275910079CFA78E4FD6580E094A840086142AE512D7F8B3E6FE740556F932F9`.
  4. Generación programática de `resumen_ejecucion.json` a partir de los XMLs reales, deduplicando por identidad de caso (`Clase#Método`) y enumerando exactamente los 19 casos PowerShell.

---

## 4. BATERÍA DE PRUEBAS POWERSHELL (19/19 APROBADAS)

| # | Caso de Prueba | Resultado | Salida Esperada | Salida Real |
|:---:|---|:---:|:---:|:---:|
| 1 | MOODLE_URL ausente (usa default `http://localhost:8081`) | **PASS** | 0 | 0 |
| 2 | MOODLE_URL vacía (usa default `http://localhost:8081`) | **PASS** | 0 | 0 |
| 3 | MOODLE_URL personalizada válida (`http://localhost:8085`) | **PASS** | 0 | 0 |
| 4 | MOODLE_URL inválida (`ftp://bad-url`) | **PASS** | 1 | 1 |
| 5 | MOODLE_URL conflicto de puerto con HttpPort (`18088`) | **PASS** | 1 | 1 |
| 6 | Destino original rechazado: Puerto prohibido `5432` | **PASS** | 1 | 1 |
| 7 | Destino original rechazado: Base original `lysandri_db` | **PASS** | 1 | 1 |
| 8 | Destino original rechazado: Host `lysandri-postgres` | **PASS** | 1 | 1 |
| 9 | Fallo de conexión TCP a host:puerto no activo (`15499`) | **PASS** | 2 | 2 |
| 10 | Puerto HTTP ocupado detecta conflicto (`18090`) | **PASS** | 3 | 3 |
| 11 | Herramienta de verificación ausente (`-ForzarHerramientaAusente`) | **PASS** | 4 | 4 |
| 12 | Base incorrecta: Nombre no autorizado antes de conectar (`base_ajena_test`) | **PASS** | 4 | 4 |
| 13 | Base incorrecta: Servidor responde con base distinta | **PASS** | 4 | 4 |
| 14 | Host o puerto diferente: Servidor responde con otra identidad | **PASS** | 4 | 4 |
| 15 | Credenciales incorrectas para usuario `lysandri_app` | **PASS** | 4 | 4 |
| 16 | Credenciales incorrectas para usuario `lysandri_admin` | **PASS** | 4 | 4 |
| 17 | Destino correcto y verificado (`-SoloVerificar`) | **PASS** | 0 | 0 |
| 18 | Verificación de argumentos Java y protección de secretos | **PASS** | 0 | 0 |
| 19 | Ejecución delegada desde script raíz (`iniciar-backend.ps1`) | **PASS** | 0 | 0 |

---

## 5. DISTINCIÓN: IMPLEMENTADO vs. VERIFICADO

| Componente / Prueba | Estado | Justificación / Evidencia |
|---|:---:|---|
| **Lógica de Token Hash Estricto (Hallazgo 1)** | **VERIFICADO** | 7 pruebas unitarias en `UserActivationRepositoryAdapterTest` (100% PASS). |
| **Outbox Transaccional y Cifrado AES-256 (Hallazgo 2)** | **VERIFICADO** | 7 pruebas unitarias en `ActivationOutboxServiceTest`, 21 en `BuyCourseServiceTest` y 14 en `OrderFinancialTransactionServiceTest` (100% PASS). |
| **Scripts de Verificación de Destino (Hallazgo 3)** | **VERIFICADO** | 19 pruebas automatizadas en `test-scripts-behavior.ps1` con mock psql y listeners TCP (100% PASS). |
| **Sanitización de Logs y Eliminación de PII (Hallazgo 4)** | **VERIFICADO** | 5 pruebas unitarias en `SanitizerUtilsTest` (100% PASS). Logs de servicios verificados sin datos personales ni secretos. |
| **Pruebas de Integración con Contenedores Docker Reales** | **IMPLEMENTADO** | Código completo implementado en `ConcurrentTokenConsumptionIntegrationTest`, `GuestActivationHttpMockSmtpIntegrationTest`, `PostgresPersistenceIntegrationTest`. No verificado en esta máquina debido a que el servicio local de Docker Desktop se encuentra detenido (`//./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`). |

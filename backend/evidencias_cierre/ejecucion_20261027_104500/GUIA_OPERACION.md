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
   - Para pruebas de integración con persistencia: se ejecutan exclusivamente en contenedores **PostgreSQL desechables** generados en memoria y volúmenes temporales eliminados al concluir cada suite (usando `pgvector/pgvector:pg16`).
   - Para verificación y ejecución local: se utiliza exclusivamente el clon autorizado `lysandri_clone_db` en el puerto `5434`.

3. **Protección de Secretos y Privacidad (PII)**:
   - Queda estrictamente prohibido registrar o imprimir contraseñas, secretos, tokens JWT, hashes o enlaces de activación en la consola o bitácoras de logs.
   - Los datos personales del comprador (nombre completo, correo, número de documento de identidad) nunca se registran en los logs de órdenes ni de pasarelas de pago (`SanitizerUtils`).
   - La variable `JWT_SECRET` se valida con un mínimo estricto de 256 bits (32 caracteres) o se genera de forma temporal segura (512 bits) en memoria si no se provee.
   - La clave `ACTIVATION_ENCRYPTION_KEY` es independiente de `JWT_SECRET`, obligatoria, persistente y externa (mínimo 32 caracteres / 256 bits). Nunca se pasa en la línea de comandos `-D` de Java; se transfiere únicamente por variable de entorno de proceso.

---

## 2. Variables de Entorno Requeridas

Las credenciales para el entorno clon se leen desde las variables de entorno del sistema o desde `backend/.env.local`:

| Variable | Descripción | Valor por Defecto / Requisito |
| :--- | :--- | :--- |
| `ACTIVATION_ENCRYPTION_KEY` | Clave persistente exclusiva para AES-256-GCM de activaciones | **Obligatoria** (mínimo 32 caracteres) |
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

## 4. Procesamiento Automático y Concurrencia de Activación (Patrón Outbox)

### A. Garantías de Concurrencia y Reserva Atómica
1. **Reserva Atómica Condicional con Respeto Estricto de `proximoIntento`**:
   - Se abandonó el patrón inseguro `findById` seguido de `save`.
   - Se implementó una actualización condicional nativa en `SpringDataActivationOutboxTaskRepository.java`:
     ```sql
     UPDATE outbox_notificacion_activacion
     SET estado = 'PROCESANDO', bloqueado_por = :workerId, bloqueado_hasta = :leaseHasta, ...
     WHERE id_tarea = :idTarea
       AND (
         (estado IN ('PENDIENTE', 'REINTENTO') AND bloqueado_hasta < :ahora)
         OR (estado = 'PROCESANDO' AND bloqueado_hasta < :ahora)
       )
       AND proximo_intento <= :ahora
     ```
   - **Coherencia entre selección y reserva**: Si un trabajador obtiene una lista previa de candidatos y otro trabajador procesa una tarea, falla y programa su próximo intento en el futuro (`proximo_intento > NOW()`), cuando el primer trabajador intenta reservar usando la lista desactualizada, la condición temporal SQL falla directamente en el motor de base de datos, retornando `0` filas afectadas y evitando un envío prematuro no autorizado.
   - Cuando transcurre el plazo programado, la tarea vuelve a ser elegible y cualquier trabajador puede reservarla exitosamente.

2. **Acotamiento de Red SMTP y Coordinación con la Reserva**:
   - **Timeouts SMTP explícitos**: Se configuran explícitamente en `application.yml`:
     - `mail.smtp.connectiontimeout: 5000` (5 segundos para establecer conexión TCP)
     - `mail.smtp.timeout: 10000` (10 segundos de espera de lectura de socket)
     - `mail.smtp.writetimeout: 5000` (5 segundos de escritura de socket)
     - Verificados en pruebas unitarias mediante inspección directa de `JavaMailSenderImpl.getJavaMailProperties()`.
   - **Presupuesto Total de Envío vs Duración de Reserva**:
     - Duración exclusiva de la reserva: `leaseDurationSeconds = 120` segundos.
     - Presupuesto total de red para el envío: `sendTimeoutSeconds = 20` segundos.
     - Los timeouts de socket individuales no garantizan por sí solos el fin de la operación de red en todas las fases del protocolo SMTP. Por ello, el envío se ejecuta a través de un `ExecutorService` dedicado con cancelación efectiva (`Future.cancel(true)`) y una espera acotada de drenaje de red (`esperarCierreOperacionRed`).
     - Si un intento experimenta demoras pero continúa dentro de los parámetros permitidos, se invoca `renovarReserva(idTarea, workerId, nuevoLease)` para asegurar que ningún otro trabajador tome la tarea mientras el envío anterior siga activo y autorizado.
     - Si el servidor SMTP acepta la conexión y deja de responder (socket colgado), la tarea agota su presupuesto de forma controlada, captura la excepción, conserva y programa su reintento con backoff exponencial, y el planificador continúa procesando normalmente sin bloqueos.

3. **Arquitectura de 3 Fases y Ausencia de Bloqueos en Base de Datos**:
   - **FASE 1 (TX 1 - REQUIRES_NEW)**: Se adquiere el lease de 120 segundos mediante actualización atómica en `TransactionTemplate` y se confirma (commit) inmediatamente antes de iniciar cualquier interacción de red.
   - **FASE 2 (SIN TRANSACCIÓN NI BLOQUEO DB)**: Se descifra la carga con `ActivationCryptoService` y se contacta al servidor SMTP externo. La llamada de red no retiene ninguna conexión de base de datos ni transacción abierta.
     - **Demostración de ausencia de bloqueos**: Verificado experimentalmente en `AtomicActivationOutboxPostgresIntegrationTest`: mientras el envío SMTP está deliberadamente pausado con latches, una conexión independiente y transacción distinta ejecuta `SELECT ... FOR UPDATE` sobre la misma fila con `SET lock_timeout = '2000ms'`. La consulta adquiere el bloqueo de fila de inmediato (< 1500 ms) sin entrar en conflicto ni esperar a que SMTP termine. Asimismo, se comprueba mediante `TransactionSynchronizationManager.isActualTransactionActive() == false` que el hilo de notificación no posee ninguna transacción Spring activa.
   - **FASE 3 (TX 2 - REQUIRES_NEW)**: Se registra el resultado condicionado a conservar la propiedad:
     `WHERE id_tarea = :idTarea AND bloqueado_por = :workerId`.
     Si el lease expiró y otro worker tomó la tarea, el resultado tardío no sobrescribe la base de datos. Al completarse con éxito, se establece `estado = 'ENVIADO'` y `enlace_cifrado = NULL` (purgando la carga sensible).

4. **Semántica de Entrega "Al Menos Una Vez" (At-Least-Once Delivery)**:
   - *Documentación operativa de posible duplicación*: Si el servidor SMTP acepta el mensaje (respuesta `250 OK`) y el proceso o la máquina colapsa antes de que la Fase 3 confirme el estado `ENVIADO` en PostgreSQL, la tarea permanecerá en base de datos. Al expirar su lease de 120 segundos, el planificador (en el mismo nodo tras reiniciar o en otro nodo) la recuperará y volverá a enviarla.
   - Por fundamentos de sistemas distribuidos, no es físicamente posible garantizar una entrega "exactamente una vez" (exactly-once) entre una base de datos relacional y un servidor SMTP sin soporte de un protocolo de commit de dos fases (2PC) a nivel de transporte de correo. Se asume y documenta la posibilidad de entrega duplicada en caso de caída catastrófica en dicha ventana, prefiriendo la eventual recepción duplicada a la pérdida silenciosa del enlace de activación.

5. **Recuperación Automática de Tareas Huérfanas tras Reinicio**:
   - Las tareas en estado `PROCESANDO` con lease vencido (`bloqueado_hasta < NOW()`) o en estado `PENDIENTE`/`REINTENTO` son recuperadas automáticamente por el planificador tras reiniciar el JAR, utilizando la misma clave persistente de cifrado AES-256-GCM y sin requerir una nueva solicitud HTTP.

---

## 5. Cifrado Fuerte de Cargas Sensibles (`ActivationCryptoService`)

1. **Clave Exclusiva Persistente**:
   - Requiere `app.security.activation-encryption-key` (o `ACTIVATION_ENCRYPTION_KEY`).
   - Se eliminó el fallback a JWT y cualquier clave fija en código.
   - Falla estrictamente si la clave tiene menos de 32 caracteres (256 bits).
   - Derivación determinista mediante digest SHA-256.

2. **Cifrado AES-256-GCM con Nonce Aleatorio**:
   - Cada enlace cifrado genera un IV criptográfico aleatorio único de 12 bytes.
   - Se almacena con prefijo de versión (`v1:`).
   - Tag de autenticación GCM de 128 bits.

3. **Soporte de Rotación de Claves**:
   - Se puede configurar `app.security.activation-encryption-key-previous` para rotación fluida. Si falla el descifrado con la clave primaria, intenta con la clave previa antes de lanzar error.

4. **Protección contra Destrucción Silenciosa**:
   - Ante error de descifrado, la carga cifrada se conserva intacta en la base de datos y la tarea pasa al estado `FALLIDO_CRIPTOGRAFICO`, permitiendo intervención operativa sin pérdida irrecuperable de datos.

---

## 6. Diagnósticos del Editor PowerShell (Aclaración Operativa)

Durante la edición interactiva en VS Code o Antigravity IDE, el servicio de lenguaje `PowerShell Editor Services` (PSES) analiza el archivo en memoria mientras el desarrollador escribe. Errores transitorios como:
- "Llave de cierre faltante"
- "Expresión de asignación inválida"
- "Paréntesis inesperado"

son artefactos normales de estado intermedio de escritura en el búfer de edición. **Los archivos guardados en disco nunca tuvieron errores de sintaxis**, como lo certifica el parser AST nativo:
```powershell
[System.Management.Automation.Language.Parser]::ParseFile("iniciar-backend.ps1", [ref]$null, [ref]$tokens, [ref]$errors)
# $errors.Count == 0
```
Para limpiar advertencias desactualizadas en el editor, ejecute:
`Ctrl+Shift+P` -> `PowerShell: Restart Current Session`.

---

## 7. Comandos de Reproducción y Verificación

### A. Ejecución de la Suite Completa de Pruebas Automatizadas (224 Pruebas)
```powershell
cd backend
.\mvnw.cmd test
```
*Ejecuta 224 pruebas automáticas (181 unitarias + 43 de integración con contenedores desechables). Resultado: 0 fallos, 0 errores, 0 omitidos (BUILD SUCCESS).*

### B. Ejecución de Pruebas de Comportamiento de Scripts (19 Casos)
```powershell
cd backend
powershell.exe -ExecutionPolicy Bypass -File .\test-scripts-behavior.ps1
```
*Valida las 19 reglas estrictas de seguridad de los scripts PowerShell con 100% de éxito.*

### C. Verificación en Vivo del Artefacto JAR Real contra PostgreSQL Desechable
```powershell
cd backend
python .\run-live-jar-test.py
```
*Ejecuta el JAR real empaquetado contra un contenedor PostgreSQL desechable (`pgvector/pgvector:pg16` en puerto 15438) y servidor SMTP mock local (puerto 1025). Comprueba disponibilidad HTTP, solicitud de activación, procesamiento automático por `@Scheduled`, recuperación automática de la MISMA tarea técnica tras caída y reinicio del proceso (sin nueva solicitud HTTP), verificación de `enlace_cifrado IS NULL`, y activación exitosa de la cuenta mediante HTTP.*

### D. Empaquetado del Artefacto Final
```powershell
cd backend
.\mvnw.cmd package -DskipTests
```
*Genera el ejecutable `target/lysandri-backend-1.0.0-SNAPSHOT.jar`.*

---

## 8. Verificación del Artefacto Entregado

- **Artefacto**: `backend/target/lysandri-backend-1.0.0-SNAPSHOT.jar`
- **Algoritmo**: SHA-256
- **Hash Verificado**: `F8023B7E0C5A062A8A52FFE1F66A8EEB56ADEA23CC3C2631FC66D8939CA040C0`
- **Carpeta de Evidencias**: `backend/evidencias_cierre/ejecucion_20261027_104500/`

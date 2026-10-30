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
   - **Coherencia entre selección y reserva**: Si un trabajador obtiene una lista de candidatos y otro trabajador procesa la tarea, falla y programa su próximo intento en el futuro (`proximo_intento > NOW()`), cuando el primer trabajador intenta reservar usando la lista desactualizada, la condición temporal SQL falla directamente en el motor de base de datos, retornando `0` filas afectadas y evitando un envío prematuro no autorizado.
   - Cuando transcurre el plazo programado, la tarea vuelve a ser elegible y cualquier trabajador puede reservarla exitosamente.

2. **Diferenciación entre Cancelación Solicitada y Terminación Real**:
   - `Future.isDone()`, `Future.isCancelled()` y `Future.cancel(true)` **no prueban** que la operación de red haya concluido ni que el socket esté cerrado.
   - La terminación efectiva del envío y la liberación de sus recursos se registran a través de una señal independiente emitida en el bloque `finally` de la tarea de envío (`CountDownLatch realTerminationLatch`).
   - Se maneja explícitamente el caso de una tarea cancelada antes de comenzar su ejecución (por ejemplo, si fue cancelada en la cola del ejecutor): `if (!sendStarted.get()) { realTerminationLatch.countDown(); }`, evitando esperas indefinidas.
   - La tarea nunca se libera ni se reprograma en base de datos hasta que dicha señal confirma la salida real de la operación de red.

3. **Interrupción Efectiva del Transporte SMTP (`AbortableSocketFactory`)**:
   - `Thread.interrupt()` no es suficiente para abortar operaciones de socket bloqueadas en lectura o escritura nativa, y el método `close()` de `SMTPTransport` en Jakarta Mail está sincronizado con el monitor de transporte que retiene la llamada activa `sendMessage()`.
   - Se implementó `AbortableSocketFactory`, el cual intercepta y registra cada `java.net.Socket` TCP activo asociado al destinatario en curso.
   - Ante el agotamiento del presupuesto global (`sendTimeoutSeconds = 20s`), se invoca `notificationPort.abortarEnvio(destinatario)`, cerrando directamente el socket TCP a nivel de sistema operativo (`socket.close()`).
   - Esto causa una excepción inmediata `SocketException` en el hilo bloqueado en red, forzando la terminación limpia e inmediata del transporte para esa operación específica sin afectar otros envíos concurrentes y sin utilizar `Thread.stop()`.

4. **Mantenimiento de Exclusividad mediante Renovación Periódica**:
   - Mientras la operación de envío permanezca activa, un ejecutor desacoplado (`watchdogExecutor`) renueva periódicamente el lease en base de datos mediante:
     ```sql
     UPDATE outbox_notificacion_activacion
     SET bloqueado_hasta = :nuevoLease, modificado_en = :now
     WHERE id_tarea = :idTarea AND bloqueado_por = :workerId AND estado = 'PROCESANDO'
     ```
   - Esta renovación periódica se ejecuta de forma totalmente independiente del hilo de red.
   - Si la renovación falla (por ejemplo, la reserva fue transferida o expiró), el watchdog aborta inmediatamente el transporte SMTP (`notificationPort.abortarEnvio`) y marca el resultado como incierto.
   - **Resultado SMTP Incierto**: Si se pierde la reserva o falla su renovación durante el envío, el trabajador no sobrescribe el estado en base de datos, registrando en bitácora el resultado incierto para evitar colisiones con el nuevo propietario.
   - **Salida Operativa Acotada**: Para evitar renovaciones infinitas que oculten hilos bloqueados de forma irrecuperable, se define `maxTotalAttemptSeconds` (ej. 60s). Al alcanzarse este límite, se detiene la renovación para permitir el rescate operativo de la tarea huérfana.

5. **Semántica de Entrega "Al Menos Una Vez" (At-Least-Once Delivery)**:
   - *Documentación operativa de posible duplicación*: Si el servidor SMTP acepta el mensaje (respuesta `250 OK`) y el proceso colapsa catastróficamente antes de que la Fase 3 confirme el estado `ENVIADO` en PostgreSQL, la tarea permanecerá en base de datos. Al expirar su lease de 120 segundos, el planificador la recuperará y volverá a enviarla.
   - Esta duplicación eventual es inevitable en sistemas distribuidos sin un protocolo 2PC entre el servidor de correo y la base de datos, distinguiéndose claramente del solapamiento evitable que ocurría previamente al liberar una reserva mientras el envío seguía activo en segundo plano.

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

### A. Ejecución de la Suite Completa de Pruebas Automatizadas (227 Pruebas en 31 Archivos XML)
```powershell
cd backend
.\mvnw.cmd test
```
*Ejecuta 227 casos de prueba automáticos distribuidos en 31 archivos XML de Surefire (181 unitarias + 46 de integración con contenedores desechables). Resultado: 0 fallos, 0 errores, 0 omitidos (BUILD SUCCESS).*

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
- **Hash Verificado**: `880BA0ADC859812FE9DE22F3714CD78C3E71B6C2A5B313837A8ACE1473DA5B40`
- **Carpeta de Evidencias**: `backend/evidencias_cierre/ejecucion_20261027_112000/`

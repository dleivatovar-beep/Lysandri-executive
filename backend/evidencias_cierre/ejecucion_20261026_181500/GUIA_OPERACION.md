# Guía de Operación y Verificación del Backend

Esta guía documenta los procedimientos de verificación, configuración y arranque seguro del backend de **Lysandri Executive Education**.

---

## 1. Restricciones de Seguridad Críticas

1. **Base de Datos Original Protegida**:
   - La base de datos original `lysandri_db` en el puerto `5432` está terminantemente protegida.
   - **Ningún script ni comando de prueba o desarrollo puede conectarse o migrar el puerto 5432**.
   - Los scripts de arranque abortan inmediatamente si detectan el puerto 5432 configurado.

2. **Entornos Autorizados**:
   - Para pruebas de integración con persistencia y escrituras: se utilizan contenedores **PostgreSQL desechables** generados en memoria y volúmenes temporales eliminados al concluir cada suite.
   - Para verificación y ejecución local: se utiliza exclusivamente el clon autorizado `lysandri_clone_db` en el puerto `5434`.

3. **Protección de Secretos y Privacidad**:
   - Nunca imprimir contraseñas, secretos, tokens JWT o de activación en consola o bitácoras.
   - La variable `JWT_SECRET` se genera o valida de forma temporal y efímera en memoria local si no está provista, sin imprimir su contenido.

---

## 2. Variables de Entorno Requeridas

Las credenciales para el entorno clon se leen desde variables de entorno o desde el archivo `backend/.env.local`:

| Variable | Descripción | Valor por Defecto / Requisito |
| :--- | :--- | :--- |
| `CLONE_APP_PASSWORD` | Contraseña del usuario operativo `lysandri_app` | **Obligatoria** |
| `CLONE_ADMIN_PASSWORD` | Contraseña del usuario administrador `lysandri_admin` | **Obligatoria** |
| `MOODLE_URL` | URL del LMS Moodle | `http://localhost:8081` (puerto distinto del backend) |
| `MOODLE_TOKEN` | Token del Web Service Moodle | Token configurado en el aula virtual |
| `FRONTEND_URL` | URL pública autorizada del Frontend | `http://localhost:5173` |
| `JWT_SECRET` | Clave secreta HMAC-SHA de 512 bits | Se genera temporalmente si no se provee |

---

## 3. Uso del Script de Arranque (`iniciar-backend.ps1`)

El script se encuentra tanto en la raíz `iniciar-backend.ps1` como en `backend/iniciar-backend.ps1` con comportamiento consistente.

### Sintaxis
```powershell
.\iniciar-backend.ps1 [-SoloVerificar] [-Perfil <clone|dev|test>] [-PostgresPort <puerto>] [-HttpPort <puerto>]
```

### Parámetros
- `-SoloVerificar`: Ejecuta de manera estricta todas las comprobaciones previas (Java, JAR, variables, Moodle URL, conectividad TCP e identidad autenticada SQL de solo lectura) y finaliza con código de salida correspondiente **sin arrancar Java**.
- `-Perfil`: Perfil de Spring Boot (`clone` por defecto).
- `-PostgresPort`: Puerto del PostgreSQL destino (`5434` por defecto). El puerto 5432 está prohibido.
- `-HttpPort`: Puerto HTTP del backend (`8080` por defecto).

### Códigos de Salida del Script
- `0`: Verificación exitosa o proceso terminado normalmente.
- `1`: Error de configuración, parámetros inválidos, puerto 5432 prohibido o longitud de secreto insuficiente.
- `2`: Fallo de conectividad TCP al puerto del PostgreSQL destino.
- `3`: Conflicto de puerto HTTP local (puerto ocupado).
- `4`: Fallo de autenticación o identidad efectiva de la base de datos (credenciales incorrectas o nombre de base de datos no coincidente).

---

## 4. Ejecución de Pruebas Automatizadas

### A. Suite Habitual de Regresión
```powershell
cd backend
.\mvnw test
```
*Ejecuta 189 pruebas cubriendo órdenes, pagos Stripe/Izipay, seguridad por roles, catálogo de precios, matriculación en Moodle y auditoría.*

### B. Restauración Aislada y Migraciones (V7.1 a V27)
```powershell
cd backend
.\mvnw test -Preal-clone
```
*Restaura el snapshot oficial de V7 en un contenedor y volumen Docker temporal, aplica las migraciones V7.1 a V27, comprueba al 100% la conservación de cada columna y dato histórico, y verifica permisos de mínimo privilegio.*

### C. Activación de Invitados con Mock SMTP Local
```powershell
cd backend
.\mvnw test "-Dtest=GuestActivationHttpMockSmtpIntegrationTest"
```
*Prueba end-to-end de checkout anónimo, confirmación de pago, entrega de correo al servidor SMTP mock local (puerto 1025), extracción del enlace, consumo del token, establecimiento de contraseña y login.*

### D. Consumo Concurrente Atómico y Bloqueo Administrativo
```powershell
cd backend
.\mvnw test "-Dtest=ConcurrentTokenConsumptionIntegrationTest"
```
*Prueba concurrente en PostgreSQL desechable con bloqueo pesimista `SELECT ... FOR UPDATE`, confirmación de un único ganador, bloqueo administrativo irreversible por token, y recuperación segura de cuentas provisionales.*

### E. Comprobación de Scripts PowerShell
```powershell
cd backend
powershell.exe -ExecutionPolicy Bypass -File .\test-scripts-behavior.ps1
```
*Prueba reproducible de los 10 casos de borde del script de arranque (códigos 0, 1, 2, 3 y 4).*

---

## 5. Política de Activación de Cuentas y Tokens

1. **Tokens de Activación**:
   - Generados tras confirmar la transacción de compra de un invitado.
   - Almacenados en la base de datos mediante su **hash criptográfico SHA-256** (el token en texto claro nunca se almacena).
   - Caducidad por defecto: 48 horas.
   - De un solo uso; consumo atómico garantizado bajo transacción y bloqueo pesimista.
2. **Renovación / Reenvío**:
   - Endpoint: `POST /api/v1/auth/solicitar-activacion`.
   - Respuesta pública uniforme para prevenir enumeración de usuarios.
   - Política de invalidación: al emitir un nuevo enlace, se revocan automáticamente los tokens anteriores no consumidos del usuario.
   - Límite de tasa: máximo 3 solicitudes por ventana de 15 minutos (retorna `429 Too Many Requests` al excederse).
3. **Cuentas Bloqueadas**:
   - El bloqueo administrativo (`bloqueado_admin = true`) es independiente del estado de activación.
   - Un token de activación **nunca** levanta un bloqueo administrativo.
   - Al deshabilitar una cuenta administrativamente, se revocan todos los tokens de activación activos asociados.

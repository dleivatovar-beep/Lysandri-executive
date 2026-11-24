# Prueba de Lysandri Executive

Rama de entrega: `feature/danny-desarrollo`.

## Preparación en Windows

Se necesitan Node.js y npm, JDK 17 o superior y Docker Desktop con el motor Linux activo. Para probar el asistente se necesitan también Ollama y los modelos configurados en el proyecto. No se distribuyen contraseñas ni claves de servicios.

```powershell
git fetch origin
git switch feature/danny-desarrollo
git pull --ff-only origin feature/danny-desarrollo
npm install --prefix frontend
```

Si el tester ya tiene una rama con trabajo propio, debe integrar esta rama sin descartar sus cambios, en lugar de cambiarla a la fuerza.

### Base de datos del tester

Para el clon local previamente preparado en el puerto 5434:

```powershell
npm run prueba:clone
```

Definir en `backend/.env.local` las variables `CLONE_APP_PASSWORD`, `CLONE_ADMIN_PASSWORD` y `ACTIVATION_ENCRYPTION_KEY`. Las contraseñas deben coincidir con las del contenedor existente; no basta con inventar otras. La clave de cifrado debe ser propia del entorno y persistente. Consultar `GUIA_OPERACION.md` para la configuración del clon. No borrar volúmenes ni reutilizar datos de otra instalación para resolver un error de autenticación.

En el equipo del desarrollador, con la base existente `lysandri_db` del puerto 5432 y sus credenciales locales ya configuradas, el comando autorizado es `npm run prueba`. Ese comando ejecuta las migraciones en esa base; para un tester se prefiere el clon.

El arranque no crea por sí solo una base vacía ni copia el conocimiento del asistente de otro equipo. Si no existe el contenedor esperado, preparar primero la base según la guía. Detener un backend anterior antes de cambiar al modo PMV; el coordinador comprueba los perfiles de la instancia que intenta reutilizar.

### Correo local

En otra terminal, desde la raíz del proyecto:

```powershell
npm run mail
```

En modo PMV el envío se dirige a Mailpit, en `127.0.0.1:1025`. Abrir `http://127.0.0.1:8025`. Los mensajes no se envían a Gmail. La entrega del enlace puede tardar unos segundos.

## Ingreso

- Plataforma: http://127.0.0.1:3000
- Ingreso: http://127.0.0.1:3000/intranet
- Panel de prueba: http://127.0.0.1:3000/pmv

El primer arranque con PMV crea cuatro cuentas ficticias: Cliente, Administrador, Contador y Gerente. Cada instalación genera sus propias contraseñas en `.pmv/accesos.txt`, bajo el directorio de trabajo desde el que se inició el proyecto. Si se inició desde la raíz, el archivo estará allí. Ese directorio está excluido de Git.

No se reinician las contraseñas ni se duplican los ejemplos al volver a arrancar. Conservar el archivo de accesos de esa instalación. El panel incluye las cuentas bancarias reales facilitadas para la revisión, junto con el contacto real de la empresa. Permanecen deshabilitadas: los titulares y las monedas pendientes no se presuponen. La migración V60 sustituye el juego original de marcadores en instalaciones anteriores, sin sobrescribir configuraciones editadas. Las contraseñas y claves de servicios no se distribuyen por Git.

## Recorrido sugerido

1. Ingresar con cada perfil y abrir el panel PMV.
2. Administración: editar el contacto y comprobar que el cambio persiste. Revisar usuarios y los ejemplos de compras.
3. Gerencia: consultar el resumen; comprobar que no puede editar la configuración, administrar usuarios ni crear promociones.
4. Contabilidad: consultar compras y revisar los módulos de cuotas y conciliación de demostración.
5. Cliente: desde su cuenta, cotizar con `PMV10`, registrar una compra y consultar su resultado. Repetir la misma confirmación no debe crear otra compra.
6. En Administración, abrir `/pruebas`, revisar una aprobación y un rechazo simulado, y las cuotas pendientes. Los códigos `PMV10`, `PMVBECA` y `PMVCONVENIO` tienen condiciones ficticias editables.
7. Solicitar recuperación de contraseña con una cuenta activa de prueba. Revisar el mensaje en Mailpit y probar el enlace desde la plataforma.
8. Intentar acceder a operaciones ajenas al perfil. El backend debe rechazar esas operaciones aunque se conozca la dirección.
9. Revisar el catálogo y el chat. Las fuentes locales requieren cargar documentos de prueba en Administración > IA. Probar precios, temarios y preguntas informales. No cargar contraseñas, datos de compradores ni documentos internos en la IA.

## Registro público e inicio de sesión

Desde `/intranet`, abrir **Personal**, mantener el área **Cliente**, completar los datos y aceptar
las condiciones. El registro crea una cuenta CLIENTE activa y devuelve una sesión firmada; la interfaz
abre su panel automáticamente. Cerrar sesión, volver a entrar con la misma contraseña y recargar
la página deben conservar el acceso correcto. El correo repetido debe informar que ya existe.

La configuración `PUBLIC_REGISTRATION_REQUIRE_EMAIL_VERIFICATION` vale `false` por defecto.
No requiere SMTP para crear la cuenta. Esto no acredita que el usuario controle el correo introducido.
Si la empresa establece esa verificación, configurar la variable en `true` junto con el correo saliente;
la cuenta quedará pendiente hasta completar el enlace. Ambos flujos están cubiertos por las pruebas.

Para cuentas creadas previamente como registro público pendiente, el inicio de sesión puede habilitarlas
al comprobar su contraseña y su consentimiento. No habilita cuentas bloqueadas por administración,
cuentas inactivas sin activación pendiente ni invitaciones sin consentimiento de registro.

El registro público asigna exclusivamente CLIENTE. Para probar los otros perfiles en la instalación local,
abrir **Personal** y elegir Administración, Contabilidad, Gerencia, Docencia o Ejecutivo / Estudiante.
El perfil `sandbox`, usado también en el entorno local del tester, habilita estas altas desde la red privada.
Cada alta inicia sesión con su rol y permite salir y volver a entrar con la contraseña elegida.
Para recorrer también el panel de Gerencia, iniciar con `npm run prueba` en el equipo del desarrollador
o `npm run prueba:clone` en el entorno preparado del tester; ese panel requiere el perfil PMV.
Fuera del entorno local de pruebas, el registro interno permanece deshabilitado por defecto.
Probar con cuentas ficticias propias. No copiar credenciales del desarrollador a otro equipo.

## Búsqueda web del asistente

Es opcional y requiere una clave de Tavily propia del entorno, configurada con `npm run configurar:web`. La clave se guarda cifrada para el usuario de Windows. Reiniciar el backend después de configurarla. No se incluye la clave del desarrollador ni se ejecuta la prueba real de Tavily por defecto.

## Límites de esta entrega

Las compras son simuladas: no efectúan cargos ni transferencias ni matriculan en Moodle real. Las cuentas bancarias y billeteras permanecen deshabilitadas en PMV. El correo real, Moodle, la pasarela definitiva y el servidor de producción requieren configuración y accesos independientes. El nombre del dominio ejecutivo es un dato de demostración, no una configuración de DNS. Esta entrega no certifica disponibilidad mensual del 99,9 % ni capacidad bajo carga de producción.

Para reportar un fallo: indicar perfil, pasos, resultado esperado y observado, y adjuntar captura sin contraseñas, tokens ni datos personales. Conservar los errores de consola relevantes.

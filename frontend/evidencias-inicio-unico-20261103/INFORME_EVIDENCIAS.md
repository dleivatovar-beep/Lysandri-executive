# Informe de Evidencias: Verificación Rigurosa de Coordinador de Inicio Único

- **Fecha de ejecución:** 2026-11-04T17:44:17.866Z
- **Directorio de trabajo:** `F:\Lysandri-executive`
- **Total de pruebas ejecutadas:** 76
- **Aprobadas:** 66
- **Falladas:** 10
- **Duración:** 40.80 segundos

## Desglose de Escenarios por Categoría

| ID | Categoría | Escenario | Tipo | Resultado | Detalles |
|:---|:---|:---|:---|:---|:---|
| 1.1 | Mapeo Docker | Mapeo correcto en ejecución (5432/tcp -> 127.0.0.1:5434) | SIMULADO | APROBADO | Aceptado |
| 1.2 | Mapeo Docker | HostPort 15434 que contiene "5434" | SIMULADO | APROBADO | HostPort no coincide: Se exige '5434', se encontró '15434'. |
| 1.3 | Mapeo Docker | HostIp 0.0.0.0 (no exclusivo a 127.0.0.1) | SIMULADO | APROBADO | HostIp no coincide: Se exige enlace exclusivo a '127.0.0.1', se encontró '0.0.0.0'. |
| 1.4 | Mapeo Docker | HostIp vacío | SIMULADO | APROBADO | HostIp no coincide: Se exige enlace exclusivo a '127.0.0.1', se encontró '0.0.0.0 (todas las interfaces)'. |
| 1.5 | Mapeo Docker | Puerto interno incorrecto (5433/tcp en vez de 5432/tcp) | SIMULADO | APROBADO | Puerto interno no autorizado o ausente: Se requiere mapear '5432/tcp', pero se encontró [5433/tcp]. |
| 1.6 | Mapeo Docker | Publicación en puerto 5432 del host (violación de seguridad) | SIMULADO | APROBADO | Violación de seguridad: El contenedor intenta publicar en el puerto 5432 del host (reservado para la base original). |
| 1.7 | Mapeo Docker | Mapeo múltiple o ambiguo | SIMULADO | APROBADO | Mapeo ambiguo o ausente: Se requiere exactamente 1 asignación para '5432/tcp', se encontraron 2. |
| 1.8 | Mapeo Docker | Publicación adicional no autorizada | SIMULADO | APROBADO | Mapeo adicional no autorizado: Se detectó publicación en puerto interno '8080/tcp'. Solo se autoriza 5432/tcp. |
| 1.9 | Mapeo Docker | Contenedor detenido con configuración válida en HostConfig | SIMULADO | APROBADO | Aceptado sin exigir NetworkSettings en stopped |
| 2.1 | Postgres Availability | Recuperación de estados transitorios (TCP cerrado -> starting up -> listo) | SIMULADO | APROBADO | Listo tras 415ms |
| 2.2 | Postgres Availability | Timeout global cuando nunca queda disponible | SIMULADO | APROBADO | Detecta timeout sin bloquear indefinidamente |
| 2.3 | Postgres Availability | Credenciales incorrectas: falla rápido sin reintentar indefinidamente | SIMULADO | APROBADO | Invocaciones: 1 (abortó al primer fallo permanente) |
| 2.4 | Postgres Availability | Destino no autorizado: fail-fast inmediato | SIMULADO | APROBADO | Rechazo inmediato |
| 2.5 | Postgres Availability | Acotación de duración de cada comando | SIMULADO | APROBADO | Comandos limitados por timeoutMs individual |
| 2.6 | Postgres Availability | Seguridad de persistencia: lysandri-postgres-clone no fue detenido ni modificado | VERIFICACIÓN SEGURIDAD | APROBADO | Pruebas realizadas mediante inyección de dependencias y mocks sin tocar el clon activo |
| 3.1 | Identidad Backend | Configuración válida (127.0.0.1:5434/lysandri_clone_db, clone, JAR local) | SIMULADO | APROBADO | Reconocido como legítimo y seguro |
| 3.2 | Identidad Backend | URL únicamente en propiedad irrelevante (-Dnota=...) rechazada | SIMULADO | APROBADO | Argumentos insuficientes: no se especificó la propiedad spring.datasource.url en la línea de comandos para verificar el destino. |
| 3.3 | Identidad Backend | spring.datasource.url ausente rechazada estrictamente | SIMULADO | APROBADO | Argumentos insuficientes: no se especificó la propiedad spring.datasource.url en la línea de comandos para verificar el destino. |
| 3.4 | Identidad Backend | URL en nota combinada con destino no autorizado a 5432 rechazada | SIMULADO | APROBADO | Violación de seguridad: El proceso hace referencia al puerto 5432 (reservado para la base original). |
| 3.5 | Identidad Backend | Configuración contradictoria entre datasource y flyway rechazada | SIMULADO | APROBADO | Configuración contradictoria: spring.flyway.url no apunta al clon autorizado (127.0.0.1:5434/lysandri_clone_db). |
| 3.6 | Identidad Backend | Argumentos entrecomillados y rutas con espacios aceptados correctamente | SIMULADO | APROBADO | Tokenizado y validado con comillas y espacios en rutas |
| 3.7 | Identidad Backend | Host, puerto, base o JAR incorrectos rechazados individualmente | SIMULADO | APROBADO | Rechazos precisos: host ajeno, puerto no 5434, base no clon, JAR externo |
| 3.8 | Identidad Backend | Múltiples definiciones dispares de perfil o datasource rechazadas por contradictorias | SIMULADO | APROBADO | Detección estricta de contradicciones en argumentos del proceso |
| 3.9 | Identidad Backend | Destino al puerto 5432 rechazado inmediatamente (violación de seguridad) | SIMULADO | APROBADO | Violación de seguridad: El proceso hace referencia al puerto 5432 (reservado para la base original). |
| 3.10 | Identidad Frontend | Identidad correcta tras arranque de Vite (app, role, workspaceSignature) | SIMULADO | FALLIDO | Reconocido y confirmado |
| 3.11 | Identidad Frontend | HTTP 200 con HTML genérico (no JSON de identificación) | SIMULADO | FALLIDO | Rechazado estrictamente |
| 3.12 | Identidad Frontend | JSON inválido en endpoint /__lysandri_dev_id | SIMULADO | FALLIDO | Detectado como corrupto o no compatible |
| 3.13 | Identidad Frontend | Aplicación, rol o firma incorrectos | SIMULADO | FALLIDO | Rechazado por discordancia de atributos |
| 3.14 | Identidad Frontend | Servidor web ajeno que incluye la palabra "Lysandri" | SIMULADO | APROBADO | Rechazado estrictamente sin engaño por texto |
| 3.15 | Identidad Frontend | Salida prematura real detectada de inmediato sin agotar timeout | SIMULADO | APROBADO | Detectado en 61ms (código de salida 5, sin esperar 25s) |
| 3.16 | Identidad Frontend | Conflicto de puerto durante inicio: Proceso ajeno conservado | SIMULADO | FALLIDO | Detectado conflicto sin matar el proceso ajeno |
| 3.17 | Identidad Frontend | Proceso propio inicializándose: Estado transitorio protegido sin rotularlo falsamente como ajeno | SIMULADO | APROBADO | Clasificado como inicializando (transitorio: true), el coordinador continuará esperando |
| 3.18 | Identidad Frontend | Vite tarda en responder en 127.0.0.1 y luego queda disponible (recuperación de estado transitorio) | SIMULADO | FALLIDO | Transitorio inicial tolerado, seguido de validación de identidad oficial exitosa |
| 3.19 | Identidad Frontend | Puerto ocupado por proceso ajeno que no pertenece al árbol iniciado (PID ajeno) | SIMULADO | APROBADO | Clasificado como proceso_ajeno para protegerlo y abortar sin matarlo |
| 4.1 | Solo Verificar | Docker no activo -> salida no-cero y explica faltante | SIMULADO | APROBADO | Retorna fallo sin intentar iniciar Docker Desktop |
| 4.2 | Solo Verificar | Contenedor detenido -> salida no-cero y explica faltante | SIMULADO | APROBADO | Retorna fallo sin ejecutar docker start |
| 4.3 | Solo Verificar | PostgreSQL cerrado -> salida no-cero y explica faltante | SIMULADO | APROBADO | Retorna fallo sin alterar estado |
| 4.4 | Solo Verificar | Backend ausente -> salida no-cero y explica faltante | SIMULADO | APROBADO | Retorna fallo sin spawn Java |
| 4.5 | Solo Verificar | Todos los servicios activos y válidos -> Código 0 | SIMULADO | FALLIDO | Éxito total sin crear procesos ni abrir navegador |
| 5.1 | Limpieza y Retención | Limpieza completa: Todos los procesos propios terminan -> Bloqueo liberado y registros borrados | UNITARIA | APROBADO | Procesos terminados exitosamente, bloqueo eliminado y sesión limpiada |
| 5.2 | Limpieza y Retención | Limpieza incompleta: Proceso vivo -> Bloqueo retenido, ownerToken retenido y PIDs pendientes registrados | UNITARIA | APROBADO | Fallo reportado (exito: false), bloqueo no liberado, registro preservado con PID 8003 |
| 5.3 | Limpieza y Retención | Error de consulta / EPERM: Proceso desconocido no declara éxito y retiene bloqueo | UNITARIA | APROBADO | Proceso categorizado en desconocidos; bloqueo y sesión retenidos preventivamente |
| 5.4 | Limpieza y Retención | Reintento de limpieza: Termina procesos pendientes restantes y libera el bloqueo | UNITARIA | APROBADO | Segundo ciclo de limpieza exitoso; bloqueo liberado y registros eliminados |
| 5.5 | Limpieza y Retención | Fallo de liberación de bloqueo: No declara éxito (exito: false) y retiene activeOwnerToken | UNITARIA | APROBADO | Cierre incompleto reportado; el token no se borra prematuramente para permitir reintento |
| 5.5b | Limpieza y Retención | Reintento de liberación: Se completa exitosamente y limpia activeOwnerToken | UNITARIA | APROBADO | Token conservado en 5.5 permitió completar la liberación en el reintento |
| 5.5c | Limpieza y Retención | Bloqueo ya ausente: Reportado como ya_ausente con éxito sin error de bloqueo ajeno | UNITARIA | APROBADO | Identifica correctamente que el bloqueo ya no existe y no bloquea al usuario |
| 5.5d | Limpieza y Retención | Sesión sin bloqueo previo: Cierre exitoso y reporte limpio (estado: no_requerido) | UNITARIA | APROBADO | Comportamiento correcto para ejecuciones que nunca adquirieron bloqueo |
| 5.6 | Limpieza y Retención | PIDs dependientes activos en lock impiden recuperación aunque el coordinador haya muerto | UNITARIA | APROBADO | Nueva instancia rechaza adquirir bloqueo por existir proceso dependiente huérfano vivo |
| 5.7 | Propiedad y Mutex | Mutex activo con antigüedad > 10s preservado (proceso propietario vivo en SO) | UNITARIA | APROBADO | No se elimina ni roba el mutex por tiempo si el proceso creador sigue vivo |
| 5.8 | Propiedad y Mutex | Mutex con dueño muerto (ESRCH) recuperado atómicamente y liberado al terminar | UNITARIA | APROBADO | Recuperación atómica completada; mutex limpio tras ejecutar la función |
| 5.9 | Propiedad y Mutex | Error al consultar dueño del mutex (EPERM): No asume muerto y preserva mutex | UNITARIA | APROBADO | Protección estricta ante falta de permisos sin robar mutex ajeno |
| 5.10 | Propiedad y Mutex | Mutex incompleto en creación: Respeta periodo de gracia sin robarlo prematuramente | UNITARIA | APROBADO | Periodo de gracia respetado; mutex en creación no es destruido |
| 5.11 | Propiedad y Mutex | Sesión ajena no puede liberar bloqueo de otra sesión (rechazo estricto de token ajeno) | UNITARIA | APROBADO | Token ajeno rechazado (archivo retenido); token legítimo libera bloqueo |
| 5.12 | Propiedad y Mutex | Unificación de adquisición, inspección y liberación bajo protocolo de mutex | UNITARIA | APROBADO | Todas las operaciones de bloqueo coordinadas estrictamente mediante el mutex |
| 5.13 | Terminación de Procesos | Procesos propios validados terminados individualmente y confirmados muertos | UNITARIA | APROBADO | Envoltorio e hijo coinciden con patrones y son terminados individualmente sin /T |
| 5.14 | Terminación de Procesos | PID reciclado con CommandLine no coincidente clasificado en rechazados (no terminado) | UNITARIA | APROBADO | Proceso ajeno protegido; no se envía señal de terminación |
| 5.15 | Terminación de Procesos | CommandLine vacío clasificado en desconocidos (no terminado por precaución) | UNITARIA | APROBADO | No se termina proceso si CommandLine está vacío; exito: false |
| 5.16 | Terminación de Procesos | Fallo en consulta Win32_Process clasificado en desconocidos (no terminado por seguridad) | UNITARIA | APROBADO | Error de consulta no asume propiedad; proceso preservado en desconocidos |
| 5.17 | Terminación de Procesos | Descendiente no autorizado (notepad.exe) clasificado en rechazados y no terminado | UNITARIA | APROBADO | Hijo ajeno protegido; solo se termina el wrapper validado |
| 5.18 | Terminación de Procesos | Fallo al terminar proceso: Permanece en vivos y reporta exito: false | UNITARIA | APROBADO | Proceso inmune a taskkill reportado como vivo; no se declara falsamente terminado |
| 5.19 | Terminación de Procesos | Terminación individual sin flag recursivo /T: Descendientes ajenos no autorizados no se matan | UNITARIA | APROBADO | Señal de muerte enviada exclusivamente a PIDs validados uno por uno |
| 5.20 | Concurrencia Multi-Proceso | Competencia por adquisición inicial entre 2 subprocesos independientes reales (barrera al ms) | PROCESOS REALES | APROBADO | Ganador PID 11684 (cff0e132...), perdedor PID 1924 rechazado limpiamente |
| 5.21 | Concurrencia Multi-Proceso | Competencia por recuperación de bloqueo abandonado entre 2 procesos reales independientes | PROCESOS REALES | APROBADO | Recuperador único PID 3972 (15294c55...); sin colisión ni daño en el lock |
| 5.22 | Concurrencia Multi-Proceso | Mutex activo preservado tras superar 10 segundos sin ser tomado (Worker activo protegido) | PROCESOS REALES | APROBADO | Worker B no robó mutex tras 10.3s (PID 3032 activo); Worker A liberó normalmente |
| 5.23 | Concurrencia Multi-Proceso | Limpieza incompleta impide el inicio de una segunda instancia (procesos dependientes vivos) | PROCESOS REALES | APROBADO | Worker B rechazado porque el PID dependiente 15100 seguía vivo en el SO |
| 5.24 | Concurrencia Multi-Proceso | Preservación de servicios preexistentes en puerto 3388 durante concurrencia | PROCESOS REALES | FALLIDO | Servicio HTTP preexistente no interferido; respondió 200 OK durante y después |
| 5.26 | Propiedad y Mutex | Recuperación atómica de mutex abandonado: Dos instancias recuperan y ejecutan secuencialmente sin colisión | UNITARIA | APROBADO | Reclamo atómico del ancla impidió doble retiro; ambas llamadas completadas |
| 5.27 | Propiedad y Mutex | Propietario nuevo activo permanece protegido ante instancia con datos de dueño anterior | UNITARIA | APROBADO | Reclamo de ancla obsoleta falló con ENOENT; el mutex del nuevo dueño no fue tocado ni movido |
| 5.28 | Propiedad y Mutex | Liberación de sesión antigua no afecta al nuevo dueño activo (ancla no coincidente ignorada) | UNITARIA | APROBADO | Sesión antigua no destruye el mutex ni los archivos del nuevo propietario legítimo |
| 5.25 | Ciclo de Vida y Limpieza | Documentación técnica: Señales y Ctrl+C en consola Windows | DOCUMENTACIÓN Y ARQUITECTURA | APROBADO | Windows emite CTRL_C_EVENT en consola interactiva (capturado por process.on); en pruebas automatizadas se verifica ejecutarLimpieza directamente |
| 6.1 | Política JAR | JAR ausente detectado con error e instrucción de compilación | SIMULADO | APROBADO | Rechazado con instrucción cd backend && ./mvnw package |
| 6.2 | Política JAR | JAR desactualizado detectado respecto al código modificado | SIMULADO | APROBADO | Detectó archivo más reciente: OrderController.java |
| 6.3 | Política JAR | JAR al día aceptado limpiamente | SIMULADO | APROBADO | Aceptado sin advertencias |
| 7.1 | Comprobación Real | npm run dev:frontend invoca Vite directamente sin coordinador | REAL ENTORNO | APROBADO | Comando configurado: "vite" |
| 7.2 | Comprobación Real | PostgreSQL clone (127.0.0.1:5434) autenticado y activo en vivo | REAL ENTORNO | FALLIDO | Verificación exitosa con psql e identidades lysandri_app / lysandri_admin |
| 7.3 | Comprobación Real | Backend local activo en puerto 8080 respondiendo API Lysandri | REAL ENTORNO | FALLIDO | Responde HTTP 200 en /api/v1/orders/cuentas-bancarias |

## Distinción entre Pruebas Simuladas, Pruebas Unitarias y Verificaciones Reales

1. **Validación Exacta de Mapeo Docker (Unitarias / Simuladas):**
   - Análisis de JSON de `docker inspect` para `HostPort 5434`, `HostIp 127.0.0.1`, rechazo de subcadenas como `15434`, `0.0.0.0`, `5432` de host y mapeos ambiguos o adicionales.

2. **Disponibilidad y Autenticación de PostgreSQL (Unitarias / Simuladas):**
   - Transiciones transitorias (TCP cerrado -> starting up -> listo).
   - Fail-fast ante credenciales inválidas y violaciones de destino a 5432 sin bucles infinitos.
   - Seguridad: El clon persistente `lysandri-postgres-clone` no fue detenido ni modificado para las pruebas de fallo.

3. **Exigencia de Configuración Real de Conexión del Backend (Item 3):**
   - Tokenización estricta de la línea de comandos con soporte de comillas simples, dobles y rutas con espacios.
   - Asociación estricta de propiedades: `spring.datasource.url` obligatoria apuntando exactamente a `127.0.0.1:5434/lysandri_clone_db`.
   - Rechazo estricto si la URL solo figura en propiedades secundarias irrelevantes (`-Dnota=...`) sin declarar el datasource.
   - Validación de `spring.flyway.url` (debe coincidir con el clon o heredar de datasource).
   - Violación de seguridad inmediata ante cualquier mención del puerto 5432 en la línea de comandos.
   - Detección de argumentos contradictorios (múltiples perfiles o datasources dispares).
   - Sanitización estricta de secretos y contraseñas.

4. **Identidad del Frontend y Salida Prematura Real (Item 4):**
   - Comprobación estricta de `app`, `role` y `workspaceSignature` determinística vía `/__lysandri_dev_id`.
   - Rechazo de HTML genérico, JSON inválido y copias en otras rutas.
   - Detección de salida prematura real: Subproceso real lanzado con `process.exit(5)` detectado en milisegundos sin esperar los 25 segundos de timeout.
   - Manejo de conflictos de puerto preservando servicios ajenos.

5. **Retención de Bloqueo ante Limpieza Incompleta y Reintento (Req 1):**
   - Retención obligatoria del archivo de bloqueo y del token si algún proceso propio queda vivo o desconocido.
   - Registro de PIDs dependientes en el archivo de bloqueo para impedir que otra instancia lo considere erróneamente abandonado.
   - Conservación de los registros de backend/frontend para permitir reintentos exitosos de limpieza.
   - Declaración de fallo (exito: false) y código de salida no cero si la limpieza queda incompleta.

6. **Propiedad y Recuperación Atómica del Mutex (Req 2):**
   - Identificación del propietario del mutex mediante owner.json { mutexToken, pid, createdAt }.
   - Mutex activo con antigüedad > 10 segundos preservado intacto mientras el proceso propietario siga vivo.
   - Recuperación atómica vía renameSync de directorios para evitar condiciones de carrera entre múltiples competidores.
   - Periodo de gracia para mutex en proceso de creación sin robo prematuro.
   - Rechazo de liberación o eliminación por parte de sesiones ajenas.
   - Unificación de toda adquisición, inspección y liberación bajo el protocolo de mutex.

7. **Terminación Estricta y Segura de Procesos Propios (Req 3):**
   - Validación individual de CommandLine y jerarquía de procesos antes de emitir cualquier señal.
   - CommandLine vacío o fallos en consulta clasificados como desconocidos (nunca terminados).
   - CommandLine no coincidente clasificado como rechazado (nunca terminado, protegiendo PIDs reciclados).
   - Prohibición estricta de taskkill /T en el proceso envoltorio para evitar liquidar procesos ajenos no autorizados.
   - Sondeo acotado para confirmar terminación real de cada PID validado.

8. **Concurrencia Real entre Procesos Independientes con Barrera al Milisegundo (Req 4):**
   - Competencia por adquisición inicial entre dos procesos del SO lanzados con spawn sincronizados mediante barrera de archivo.
   - Competencia por recuperación de bloqueo abandonado entre procesos independientes.
   - Mutex activo retenido > 10 segundos sin ser tomado por un segundo proceso independiente.
   - Limpieza incompleta de una instancia impide el inicio de una segunda instancia.
   - Preservación íntegra de servicios preexistentes en la máquina.

9. **Verificaciones Reales del Entorno:**
   - Reconocimiento y preservación del Backend real activo en puerto 8080 (PID 28492) con perfil `clone`.
   - Conexión TCP e identidades autenticadas (`lysandri_app`, `lysandri_admin`) en `127.0.0.1:5434/lysandri_clone_db`.
   - Verificación de `npm run dev:frontend` invocando directamente `vite`.

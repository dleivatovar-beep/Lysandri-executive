# Adenda de Revisión de Precisión Técnica y Operativa al Informe de Cierre
**Fecha**: 2026-10-31
**Referencia**: Ejecución `run-20261030-231653-9ed81c`
**Documento Afectado**: `GUIA_REVISION_ADMINISTRATIVA_ABONOS.md` (actualizado a Versión 1.1) e Informe de Cierre

---

### Resumen de las Tres Correcciones

#### 1. Registro de Revisiones Rechazadas y `notasAdmin`
- **Contraste con el Código**: En `OrderFinancialTransactionService.java`, el rechazo ante `DESTINO_INCIERTO` o colisión se produce en los pasos de validación (líneas 296–314), lanzando de forma inmediata una excepción `IllegalStateException`. Dicha excepción interrumpe la transacción y ejecuta un rollback antes de alcanzar la asignación de `ordenBloqueada.setNotasAdmin(...)` (líneas 352–354).
- **Corrección**:
  - Se aclara que cualquier justificación enviada en el campo `notasAdmin` durante una solicitud rechazada **no queda almacenada** en la base de datos.
  - Escribir en `notasAdmin` no elimina la incertidumbre técnica ni permite superar el bloqueo HTTP 409.
  - Se declara expresamente que **no existe en la plataforma ningún mecanismo implementado** (endpoint, tabla o servicio) para registrar notas o auditorías en órdenes que permanecen en estado `PENDIENTE`.
  - Como procedimiento operativo externo (no como función de software existente), se establece el uso de una bitácora o ticket administrativo controlado que relacione: código de orden, identificadores de órdenes históricas en conflicto, responsable, fecha/hora, evidencia examinada, conclusión y motivo de escalamiento.
  - Si la revisión documental confirma un destino diferente pero el sistema continúa bloqueando la orden por ambigüedad técnica, se estipula **escalamiento técnico** hacia ingeniería de software; queda estrictamente prohibida la manipulación SQL directa del estado financiero (`estado_orden = 'PAGADO'`).

#### 2. Roles Implementados vs. Política Operativa
- **Contraste con el Código**: En `OrderController.java` (línea 195), el endpoint está anotado con:
  ```java
  @PostMapping("/{idOrden}/confirmar-pago-manual")
  @PreAuthorize("hasAnyRole('ADMIN', 'TRABAJADOR', 'VENTAS', 'FINANZAS', 'CONTABILIDAD')")
  ```
- **Corrección**:
  - Se documenta que el backend autoriza técnicamente a usuarios con roles `ADMIN`, `TRABAJADOR`, `VENTAS`, `FINANZAS` o `CONTABILIDAD`.
  - Se distingue con claridad entre el permiso técnico implementado en código y una recomendación de política operativa interna que sugiera delegar los casos de incertidumbre exclusivamente a `ADMIN` o personal de Tesorería.
  - Se aclara que dicha política operativa **no está restringida técnicamente por el backend** y que no se cuenta con evidencia de aprobación formal institucional por la organización, manteniéndose los roles sin alteraciones en el código.

#### 3. Mecanismo de Bloqueo de Operaciones Concurrentes
- **Contraste con el Código**: En `OrderFinancialTransactionService.java` (línea 151 y 276) y `SpringDataOrderRepository.java` (líneas 45–65):
  - El bloqueo de fila se realiza sobre la orden: `findByIdWithLock(idOrden)` (`SELECT ... FOR UPDATE`).
  - El advisory lock transaccional se adquiere mediante:
    ```sql
    SELECT pg_advisory_xact_lock(hashtextextended(:operation, 0))
    ```
    donde `:operation` es **únicamente el número de operación normalizado** (`nroOp`).
- **Corrección**:
  - Se corrige la afirmación errónea de que el advisory lock utilizaba `banco + cuenta + numOperacion`. El candado utiliza **exclusivamente el número de operación normalizado**.
  - Banco y cuenta se evalúan **posteriormente** en los pasos de clasificación histórica en memoria y base de datos.
  - Se explica que dos solicitudes concurrentes con el mismo número de operación normalizado se serializan forzosamente en PostgreSQL incluso si pertenecen a bancos diferentes. Esta serialización no significa que sean duplicadas: garantiza atomicidad de evaluación y permite aprobar la segunda si demuestra `DESTINO_DIFERENTE` una vez liberado el candado transaccional.
  - Se eliminan afirmaciones de garantías absolutas que excedan lo evidenciado en las pruebas.

---

### Estado de Conservación de Evidencias
- El documento histórico original `GUIA_REVISION_ADMINISTRATIVA_ABONOS.md` generado en la ejecución previa se conserva intacto en esta carpeta para mantener la trazabilidad histórica de la corrida.
- La versión corregida y revisada se encuentra resguardada como `GUIA_REVISION_ADMINISTRATIVA_ABONOS_REV1.1.md` y en la raíz del repositorio.
- No se han alterado logs, archivos XML de Surefire, hashes del JAR probado (`38490B1716360AC4C826D23C2987DCFFE09EC48BF068CB61A24EA2C815B7BCC2`) ni los resultados estructurados del recorrido HTTP (`recorrido-http-jar-real.json`).


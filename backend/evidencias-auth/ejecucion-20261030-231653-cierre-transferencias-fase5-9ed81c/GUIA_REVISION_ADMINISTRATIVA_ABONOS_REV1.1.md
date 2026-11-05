# Guía de Revisión Administrativa de Abonos por Transferencia
**Versión**: 1.1 (Revisión de Precisión Técnica y Operativa)
**Fecha**: 2026-10-31
**Estado**: Documento Normativo Operativo y de Auditoría

Esta guía documenta los criterios operativos y de auditoría para la conciliación manual de transferencias bancarias en Lysandri, con énfasis en los casos donde el sistema detecta abonos históricos con destino incierto, colisiones o posibles reutilizaciones de comprobantes.

---

## 1. Clasificación de Destinos Bancarios

Cuando se procesa una confirmación manual de pago (`OrderFinancialTransactionService.confirmarPagoManualFinanciero`), el sistema evalúa la operación contra los registros históricos existentes bajo tres estados estrictos:

| Estado | Definición | Comportamiento Técnico del Backend |
| :--- | :--- | :--- |
| `MISMO_DESTINO` | Se verifica que el banco y la cuenta (o su forma canónica/CCI normalizada) corresponden inequívocamente al mismo destino institucional oficial. | **Bloqueo por reutilización**: Si la operación ya fue aplicada a una orden previa pagada, se lanza `IllegalStateException` (*"El abono ya fue aplicado a otra orden pagada"*). |
| `DESTINO_DIFERENTE` | Se demuestra fehacientemente que la transacción histórica ocurrió en una entidad o cuenta distinta (ejemplo: abono previo en `INTERBANK` vs. nueva orden en `BCP`). | **Permitido**: La coincidencia del número de operación no colisiona si las cuentas y entidades son genuinamente distintas y se cumplen todas las reglas financieras. |
| `DESTINO_INCIERTO` | La información histórica contiene ambigüedades: entidad no identificada (`BANCO SIN IDENTIFICAR`, `BANCO_EXTRANO`), cuenta nula (`null`), incompleta (`1234`), genérica (`CUENTA-NO-IDENTIFICADA`) o con formato numérico insuficiente para resolver la equivalencia con las cuentas oficiales. | **Bloqueo preventivo**: El sistema lanza de forma inmediata `IllegalStateException` (HTTP 409) con mensaje de conflicto que requiere revisión administrativa previa para prevenir doble uso. |

---

## 2. Principios Técnicos Fundamentales

### Por qué la longitud de una cuenta no prueba que sea diferente
Una cuenta corriente institucional y su Código de Cuenta Interbancario (CCI) poseen diferente cantidad de dígitos (por ejemplo, cuenta BCP tradicional de 13–14 dígitos versus CCI de 20 dígitos `002-193-...`). Una cuenta no puede declararse "diferente" basándose únicamente en que tiene distinta longitud o formato de guiones, pues ambas representaciones pueden remitir exactamente a la misma cuenta receptora corporativa. El sistema normaliza quitando guiones y espacios, extrayendo sufijos y prefijos bancarios.

### Por qué una cuenta histórica del mismo banco puede requerir revisión
En migraciones o registros legacy, una cuenta de un mismo banco pudo registrarse de forma parcial (ejemplo: solo el número de cuenta sin código de sucursal, o con prefijo omitido) o pertenecer a una cuenta institucional anterior que ya fue retirada de `lysandri.cuentas-bancarias`. Dado que ambas operaciones pertenecen al mismo banco, existe un riesgo real de que el mismo depósito bancario físico esté intentando acreditar dos compras distintas. Ante la duda razonable, el sistema clasifica el caso como `DESTINO_INCIERTO` para proteger la integridad contable.

---

## 3. Matriz de Contraste Obligatoria

El revisor debe cruzar la información documental física o digital contra el sistema:

1. **Comprobante de Pago Bancario (Voucher / Constancia PDF)**:
   - Número de operación / voucher oficial del banco emisor.
   - Entidad bancaria receptora y titular de la cuenta (debe ser la razón social oficial de Lysandri).
   - Cuenta de abono o CCI receptor.
   - Importe exacto y divisa (USD / PEN).
   - Fecha y hora exacta de la transferencia física.
2. **Registro Histórico en Base de Datos**:
   - Localizar la orden previa asociada a dicho número de operación.
   - Identificar al comprador anterior, fecha de la orden previa e importe acreditado.
3. **Nueva Orden Solicitante**:
   - Verificar si el estudiante que solicita la activación corresponde al depositante o si se trata de un intento fraudulento de reutilización de voucher ajeno.

---

## 4. Roles Implementados y Política Operativa

### Permisos Técnicos Implementados en el Backend
En el código fuente actual (`OrderController.java`, línea 195), el endpoint de confirmación manual está configurado formalmente con:
```java
@PostMapping("/{idOrden}/confirmar-pago-manual")
@PreAuthorize("hasAnyRole('ADMIN', 'TRABAJADOR', 'VENTAS', 'FINANZAS', 'CONTABILIDAD')")
```
Por tanto, a nivel de control de acceso técnico (Spring Security), cualquier usuario autenticado que posea alguno de estos cinco roles (`ADMIN`, `TRABAJADOR`, `VENTAS`, `FINANZAS`, `CONTABILIDAD`) tiene autorización técnica para invocar el endpoint.

### Política Operativa Recomendada (Gobernanza)
Para salvaguardar el control financiero, se recomienda como **política operativa interna** que la resolución de abonos bloqueados por `DESTINO_INCIERTO` o posibles colisiones sea gestionada exclusivamente por personal con rol `ADMIN` o personal especializado de Tesorería. 

> [!IMPORTANT]
> Esta política operativa es un lineamiento de gestión y **NO representa una restricción enforced por el backend**. El sistema no deniega el endpoint a roles como `VENTAS` o `TRABAJADOR`. Asimismo, no debe considerarse como una política formalmente aprobada por toda la organización hasta que se emita la directiva institucional correspondiente. En este trabajo no se han modificado ni alterado los roles ni los permisos del backend.

---

## 5. Registro de Revisiones y Tratamiento de Órdenes Bloqueadas

### Comportamiento del Código ante Rechazos
Es fundamental comprender el flujo de ejecución en `OrderFinancialTransactionService.java`:
1. El rechazo por destino incierto o colisión se produce en las líneas 296–314, lanzando una excepción `IllegalStateException`.
2. Esta excepción se produce **antes** de alcanzar la asignación del campo `notasAdmin` (líneas 352–354) y provoca el rollback automático de la transacción.
3. **Consecuencia directa**: Cualquier texto enviado en el campo `notasAdmin` durante una solicitud rechazada **no se almacena en la base de datos**.
4. **Escribir notasAdmin no desbloquea la orden**: Enviar una justificación en `notasAdmin` no elimina la incertidumbre técnica del algoritmo ni permite saltarse el bloqueo HTTP 409.

### Cómo Documentar la Revisión mientras la Orden Sigue PENDIENTE
- **Mecanismo en el Sistema**: **Actualmente NO existe en el backend ningún endpoint, tabla ni funcionalidad implementada** para registrar notas de auditoría ni bitácoras sobre órdenes que permanecen en estado `PENDIENTE`.
- **Procedimiento Operativo Externo Controlado**: Hasta que se implemente un módulo de auditoría de órdenes pendientes, la revisión administrativa debe documentarse en un **registro administrativo externo controlado** (por ejemplo, el sistema de tickets de soporte interno o la bitácora de conciliación de Tesorería).
- **Campos Obligatorios del Registro Externo**:
  1. **Código de la Orden Afectada** (`codigoOrden`, ej. `ORD-XXXX-XXXX`).
  2. **Identificadores Históricos en Conflicto** (`idOrden` de la orden candidata histórica, `numeroOperacion`, banco y cuenta registrada).
  3. **Responsable de la Revisión** (nombre y rol de quien efectuó el análisis).
  4. **Fecha y Hora de la Revisión**.
  5. **Evidencia Documental Examinada** (identificador de la constancia bancaria, consulta al extracto oficial).
  6. **Conclusión Técnica / Operativa**.
  7. **Motivo del Escalamiento** (si la duda persiste o si requiere intervención técnica).

### Ausencia de Mecanismos de Bypassing
- No existen pantallas, botones ni endpoints en la aplicación para forzar la aprobación de un abono incierto.
- **Prohibición Estricta de Modificación Directa**: Si la revisión administrativa confirma que el destino es físicamente legítimo y diferente, pero el sistema continúa bloqueándolo por ambigüedad histórica, **el caso debe escalarse al equipo de desarrollo / ingeniería de software**. Queda estrictamente prohibido alterar mediante sentencias SQL directas el estado de la orden (`estado_orden = 'PAGADO'`) o sus campos financieros para eludir los controles del sistema.

---

## 6. Procedimiento ante Equivalencia Indeterminada (Escalamiento)

Si tras la revisión no es posible demostrar de forma inequívoca que la operación histórica y la nueva operación son depósitos físicamente distintos:
1. **Mantener el Bloqueo**: La orden nueva **DEBE PERMANECER EN ESTADO PENDIENTE**. Bajo ninguna circunstancia se debe forzar su aprobación.
2. **Escalamiento a Tesorería**: Solicitar al área de Tesorería la consulta del extracto bancario en línea (Banca por Internet Empresas) del día y hora del movimiento para validar el saldo y la unicidad del abono.
3. **Comunicación al Comprador**: Notificar al comprador a través del canal oficial de soporte para que proporcione la constancia bancaria oficial en formato PDF emitido por la entidad financiera con fecha, hora y número de operación completo.

---

## 7. Bloqueo de Operaciones Concurrentes

El mecanismo de sincronización de transferencias se fundamenta en dos niveles de bloqueo en base de datos:

1. **Bloqueo Pesimista de Fila sobre la Orden**:
   Al inicio de la transacción, se ejecuta `orderRepository.buscarPorIdConBloqueo(idOrden)` (`SELECT o FROM OrdenEntity o WHERE o.idOrden = :id` con `@Lock(LockModeType.PESSIMISTIC_WRITE)`). Esto bloquea la fila específica de la orden a nivel de PostgreSQL (`FOR UPDATE`), previniendo modificaciones concurrentes sobre la misma orden.

2. **Advisory Lock a Nivel de Transacción (`pg_advisory_xact_lock`)**:
   En la línea 276 de `OrderFinancialTransactionService`, se ejecuta:
   ```java
   orderRepository.bloquearAbonoBancario(nroOp);
   ```
   Dicho método invoca en PostgreSQL:
   ```sql
   SELECT pg_advisory_xact_lock(hashtextextended(:operation, 0))
   ```
   donde `:operation` es **exclusivamente el número de operación normalizado** (`nroOp`). 
   > [!IMPORTANT]
   > El advisory lock **NO se calcula sobre banco + cuenta + operación**, sino **únicamente sobre el número de operación normalizado**.

3. **Serialización y Evaluación Posterior de Banco y Cuenta**:
   - Debido a que la clave del candado es el número de operación normalizado, **dos peticiones concurrentes que presenten el mismo número de operación se serializan obligatoriamente en PostgreSQL, incluso si corresponden a entidades bancarias diferentes** (ej. una en BCP y otra en Interbank con el mismo número de voucher).
   - Esta serialización **no significa que ambas operaciones sean duplicadas o vayan a ser rechazadas**: el bloqueo transaccional simplemente asegura que las transacciones se ejecuten una después de la otra de forma atómica.
   - La evaluación de la entidad bancaria y de la cuenta corriente se realiza **posteriormente** en memoria y contra base de datos (pasos 7.1 a 7.3). Una vez que la primera transacción concluye y libera el advisory lock al hacer commit o rollback, la segunda transacción evalúa el estado actualizado. Si se demuestra que la segunda corresponde a un `DESTINO_DIFERENTE`, podrá aprobarse exitosamente; si por el contrario converge al mismo destino de un abono ya pagado o genera incertidumbre, será rechazada de manera atómica.

4. **Límites de las Garantías**:
   La serialización depende de la sesión transaccional de PostgreSQL y de la función hash de 64 bits `hashtextextended`. Evita colisiones de ejecución en entornos concurrentes según lo validado en las pruebas de estrés con hilos paralelos; sin embargo, no reemplaza la necesidad de verificar la exactitud de los datos bancarios ingresados ni cubre contingencias que excedan el alcance de la transacción en curso.

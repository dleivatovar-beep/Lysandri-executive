# Guía de Revisión Administrativa de Abonos por Transferencia

Esta guía documenta los criterios operativos y de auditoría para la conciliación manual de transferencias bancarias en Lysandri, con énfasis en los casos donde el sistema detecta abonos históricos con destino incierto, colisiones o posibles reutilizaciones de comprobantes.

---

## 1. Clasificación de Destinos Bancarios

Cuando un administrador registra un abono para confirmar una orden de compra, el motor de conciliación financiera (`OrderFinancialTransactionService`) evalúa la operación contra los registros existentes bajo tres estados estrictos:

| Estado | Definición | Acción del Sistema |
| :--- | :--- | :--- |
| `MISMO_DESTINO` | Se verifica que el banco y la cuenta (o su forma canónica/CCI normalizada) corresponden inequívocamente al mismo destino institucional. | **Bloqueo por reutilización**: Si la operación ya fue aplicada a una orden previa pagada, se rechaza la aprobación con mensaje de reutilización. |
| `DESTINO_DIFERENTE` | Se demuestra fehacientemente que la transacción histórica ocurrió en una entidad o cuenta distinta (ejemplo: abono previo en `INTERBANK` vs nueva orden en `BCP`). | **Permitido**: El número de operación idéntico entre bancos distintos o cuentas institucionales distintas no colisiona si las reglas financieras se satisfacen. |
| `DESTINO_INCIERTO` | La información histórica contiene ambigüedades: banco desconocido (`BANCO SIN IDENTIFICAR`, `BANCO_EXTRANO`), cuenta ausente (`null`), truncada/incompleta (`1234`), genérica (`CUENTA-NO-IDENTIFICADA`) o con formato de dígitos insuficiente para certificar equivalencia con las cuentas oficiales. | **Bloqueo preventivo**: El sistema rechaza la operación con código HTTP 409 (`IllegalStateException`) y mensaje: *"Abono con destino histórico incierto (...) requiere revisión administrativa previa para prevenir doble uso"*. |

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

## 4. Roles Autorizados y Registro de la Conclusión

- **Rol Autorizado**: La revisión y resolución solo puede ser ejecutada por personal con rol `ADMIN` (Administrador de Finanzas o Tesorería) debidamente autenticado en la plataforma.
- **Canal Oficial**: No existen pantallas ni botones mágicos automáticos en la interfaz de usuario para sobreescribir destinos inciertos. Toda resolución debe efectuarse a través de los procedimientos administrativos y endpoints autorizados con auditoría.
- **Registro de Notas de Auditoría (`notasAdmin`)**:
  - Al ingresar la confirmación, el administrador debe registrar en el campo `notasAdmin` la justificación detallada de la verificación realizada (ej. *"Voucher físico verificado en banca empresa BCP extracto 2026-10-30; corresponde a nuevo depósito legítimo sin relación con orden histórica #104"*).
  - Queda prohibido manipular directamente mediante scripts SQL los campos financieros (`estado_orden = 'PAGADO'`) para saltarse las validaciones de negocio.

---

## 5. Trazabilidad y Conservación de Evidencias

- **Principio de Mínima Exposición**: No ingresar en notas administrativas ni registrar en logs números de tarjeta, claves o datos sensibles de cuentas bancarias personales de los compradores. Registrar únicamente los últimos 4 dígitos o el número de operación oficial emitido por la entidad financiera.
- **Archivo de Comprobantes**: Los comprobantes físicos o adjuntos digitales deben resguardarse en el repositorio documental corporativo de tesorería vinculado por el código de orden (`codigoOrden`).

---

## 6. Procedimiento ante Equivalencia Indeterminada (Escalamiento)

Si tras la revisión no es posible demostrar de forma inequívoca que la operación histórica y la nueva operación son depósitos físicamente distintos:
1. **Mantener el Bloqueo**: La orden nueva **DEBE PERMANECER EN ESTADO PENDIENTE**. Bajo ninguna circunstancia se debe forzar su aprobación.
2. **Escalamiento a Tesorería**: Solicitar al área de Tesorería la consulta del extracto bancario en línea (Banca por Internet Empresas) del día y hora del movimiento para validar el saldo y la unicidad del abono.
3. **Comunicación al Comprador**: Notificar al comprador a través del canal oficial de soporte para que proporcione la constancia bancaria oficial en formato PDF emitido por la entidad financiera con fecha, hora y número de operación completo.

---

## 7. Prevención de Doble Aprobación y Concurrencia

El motor backend garantiza que:
1. La consulta y validación de abonos se ejecuta bajo bloqueo pesimista en base de datos (`SELECT ... FOR UPDATE` sobre la orden y advisory lock PostgreSQL sobre `banco + cuenta + numOperacion`).
2. Si dos operadores o peticiones concurrentes intentan aprobar la misma operación simultáneamente, solo una transacción adquirirá el bloqueo y completará la aprobación; la segunda colisionará y será rechazada de manera atómica sin generar dobles comprobantes, dobles matrículas ni dobles correos.

-- ==============================================================================
-- SCRIPT DE AUDITORÍA Y VERIFICACIÓN DE ABONOS BANCARIOS HISTÓRICOS (PRODUCCIÓN)
-- ==============================================================================
-- PROPÓSITO:
-- Procedimiento de diagnóstico de solo lectura (READ-ONLY) para que el equipo
-- de operaciones / administración revise el estado de las órdenes históricas en
-- base de datos, identifique potenciales colisiones, variantes de nombre o cuenta,
-- y compruebe la integridad de los abonos sin alterar ninguna fila ni índice.
--
-- REGLAS DE SEGURIDAD:
-- 1. Este script contiene ÚNICAMENTE consultas SELECT.
-- 2. No realiza UPDATE, DELETE, ALTER, DROP ni ningún cambio destructivo.
-- 3. No ejecutar directamente contra la base principal sin revisión previa.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. CONSULTA DE INTEGRIDAD: Variantes en Nombres de Bancos (BCP e Interbank)
-- Detecta órdenes pagadas guardadas con alias históricos (ej. "BANCO DE CRÉDITO")
-- ------------------------------------------------------------------------------
SELECT
    id_orden,
    codigo_orden,
    banco_destino AS banco_registrado,
    translate(UPPER(banco_destino), ' -./_', '') AS banco_normalizado,
    cuenta_destino AS cuenta_registrada,
    numero_operacion AS operacion_registrada,
    monto_verificado,
    moneda_verificada,
    fecha_abono,
    fecha_aprobacion,
    aprobado_por
FROM ordenes
WHERE estado_orden = 'PAGADO'
  AND banco_destino IS NOT NULL
  AND (
      UPPER(banco_destino) LIKE '%CREDITO%'
      OR UPPER(banco_destino) LIKE '%CRÉDITO%'
      OR UPPER(banco_destino) LIKE '%INTERNACIONAL%'
      OR UPPER(banco_destino) NOT IN ('BCP', 'INTERBANK', 'YAPE', 'PLIN')
  )
ORDER BY fecha_aprobacion DESC NULLS LAST;

-- ------------------------------------------------------------------------------
-- 2. CONSULTA DE INTEGRIDAD: Formatos de Cuenta Destino (CCIs vs Cuentas Simples)
-- Identifica abonos registrados con CCI (20 dígitos) o con guiones/espacios
-- ------------------------------------------------------------------------------
SELECT
    id_orden,
    codigo_orden,
    banco_destino,
    cuenta_destino AS cuenta_original,
    translate(UPPER(cuenta_destino), ' -./_', '') AS cuenta_normalizada,
    CASE
        WHEN LENGTH(translate(UPPER(cuenta_destino), ' -./_', '')) = 20 THEN 'CCI_INTERBANCARIO'
        WHEN cuenta_destino LIKE '%-%' THEN 'CUENTA_CON_GUIONES'
        WHEN cuenta_destino LIKE '% %' THEN 'CUENTA_CON_ESPACIOS'
        ELSE 'CUENTA_SIMPLE_DIRECTA'
    END AS tipo_formato_cuenta,
    numero_operacion,
    fecha_aprobacion
FROM ordenes
WHERE estado_orden = 'PAGADO'
  AND cuenta_destino IS NOT NULL
  AND (
      cuenta_destino LIKE '%-%'
      OR cuenta_destino LIKE '% %'
      OR LENGTH(translate(UPPER(cuenta_destino), ' -./_', '')) >= 20
  )
ORDER BY fecha_aprobacion DESC NULLS LAST;

-- ------------------------------------------------------------------------------
-- 3. CONSULTA DE INTEGRIDAD: Variantes en Números de Operación
-- Detecta operaciones con espacios, guiones, barras o letras agregadas
-- ------------------------------------------------------------------------------
SELECT
    id_orden,
    codigo_orden,
    banco_destino,
    numero_operacion AS operacion_original,
    translate(UPPER(numero_operacion), ' -./_', '') AS operacion_normalizada,
    total,
    moneda,
    fecha_abono
FROM ordenes
WHERE estado_orden = 'PAGADO'
  AND numero_operacion IS NOT NULL
  AND (
      numero_operacion LIKE '% %'
      OR numero_operacion LIKE '%-%'
      OR numero_operacion LIKE '%/%'
      OR numero_operacion LIKE '%.%'
  )
ORDER BY fecha_aprobacion DESC NULLS LAST;

-- ------------------------------------------------------------------------------
-- 4. CONSULTA CRÍTICA DE COLISIONES: Detección de Operaciones Duplicadas Convergentes
-- Identifica si en el historial existen 2 o más órdenes PAGADAS que convergen al
-- mismo número de operación normalizado dentro de la misma entidad financiera.
-- Si esta consulta devuelve registros, REQUIERE INTERVENCIÓN Y AUDITORÍA MANUAL.
-- ------------------------------------------------------------------------------
WITH abonos_normalizados AS (
    SELECT
        id_orden,
        codigo_orden,
        banco_destino,
        CASE
            WHEN translate(UPPER(banco_destino), ' -./_', '') IN ('BCP', 'BANCODECREDITO', 'BANCODECREDITODELPERU', 'BANCOCREDITO', 'CREDITO') THEN 'BCP'
            WHEN translate(UPPER(banco_destino), ' -./_', '') IN ('INTERBANK', 'BANCOINTERNACIONALDELPERU', 'BANCOINTERNACIONAL', 'INTERBANKPERU') THEN 'INTERBANK'
            WHEN translate(UPPER(banco_destino), ' -./_', '') IN ('YAPE', 'BILLETERAYAPE', 'BILLETERADIGITALYAPE') THEN 'YAPE'
            WHEN translate(UPPER(banco_destino), ' -./_', '') IN ('PLIN', 'BILLETERAPLIN', 'BILLETERADIGITALPLIN') THEN 'PLIN'
            ELSE translate(UPPER(banco_destino), ' -./_', '')
        END AS entidad_canonica,
        translate(UPPER(cuenta_destino), ' -./_', '') AS cuenta_norm,
        translate(UPPER(numero_operacion), ' -./_', '') AS operacion_norm,
        monto_verificado,
        moneda_verificada,
        fecha_abono,
        fecha_aprobacion,
        aprobado_por
    FROM ordenes
    WHERE estado_orden = 'PAGADO'
      AND numero_operacion IS NOT NULL
      AND banco_destino IS NOT NULL
)
SELECT
    entidad_canonica,
    operacion_norm,
    COUNT(DISTINCT id_orden) AS cantidad_ordenes_convergentes,
    string_agg(codigo_orden, ', ' ORDER BY id_orden) AS codigos_ordenes,
    string_agg(id_orden::text, ', ' ORDER BY id_orden) AS ids_ordenes,
    string_agg(banco_destino, ' | ' ORDER BY id_orden) AS variantes_banco,
    MIN(fecha_aprobacion) AS primera_aprobacion,
    MAX(fecha_aprobacion) AS ultima_aprobacion
FROM abonos_normalizados
GROUP BY entidad_canonica, operacion_norm
HAVING COUNT(DISTINCT id_orden) > 1
ORDER BY cantidad_ordenes_convergentes DESC;

-- ------------------------------------------------------------------------------
-- 5. RESUMEN ESTADÍSTICO DE ABONOS BANCARIOS REGISTRADOS
-- Visualiza el volumen total de órdenes pagadas por entidad y método de pago
-- ------------------------------------------------------------------------------
SELECT
    metodo_pago,
    COALESCE(banco_destino, 'NO_ESPECIFICADO') AS banco_destino,
    COUNT(*) AS total_ordenes_pagadas,
    SUM(total) AS importe_acumulado,
    moneda
FROM ordenes
WHERE estado_orden = 'PAGADO'
GROUP BY metodo_pago, banco_destino, moneda
ORDER BY total_ordenes_pagadas DESC;


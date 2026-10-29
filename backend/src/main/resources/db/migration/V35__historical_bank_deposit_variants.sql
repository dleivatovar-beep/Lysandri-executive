-- Migración V35: Soporte e índices para detección de abonos bancarios con variantes históricas
--
-- 1. Índice funcional sobre número de operación normalizado para órdenes en estado PAGADO.
--    Elimina espacios, guiones, puntos y barras y convierte a mayúsculas conservando ceros a la izquierda significativos.
CREATE INDEX IF NOT EXISTS idx_ordenes_op_norm
ON ordenes (translate(UPPER(numero_operacion), ' -./_', ''))
WHERE estado_orden = 'PAGADO' AND numero_operacion IS NOT NULL;

-- 2. Índice compuesto sobre banco, cuenta y número de operación normalizados para órdenes PAGADAS.
--    Permite verificación rápida y concurrente de duplicados sin alterar las columnas originales de auditoría.
CREATE INDEX IF NOT EXISTS idx_ordenes_abono_historico_norm
ON ordenes (
    translate(UPPER(banco_destino), ' -./_', ''),
    translate(UPPER(cuenta_destino), ' -./_', ''),
    translate(UPPER(numero_operacion), ' -./_', '')
)
WHERE estado_orden = 'PAGADO'
  AND banco_destino IS NOT NULL
  AND cuenta_destino IS NOT NULL
  AND numero_operacion IS NOT NULL;


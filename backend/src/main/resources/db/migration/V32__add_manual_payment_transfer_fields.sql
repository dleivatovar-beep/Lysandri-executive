-- V32: Campos de verificación de transferencias bancarias y pagos manuales (BCP e Interbank)
ALTER TABLE ordenes
    ADD COLUMN IF NOT EXISTS banco_destino VARCHAR(50),
    ADD COLUMN IF NOT EXISTS cuenta_destino VARCHAR(100),
    ADD COLUMN IF NOT EXISTS monto_verificado NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS moneda_verificada VARCHAR(3),
    ADD COLUMN IF NOT EXISTS fecha_abono TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS aprobado_por VARCHAR(150),
    ADD COLUMN IF NOT EXISTS fecha_aprobacion TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS observacion_verificacion VARCHAR(500);

-- Índice único parcial: impide que el mismo abono bancario (banco, cuenta, número de operación)
-- sea utilizado para confirmar dos órdenes diferentes en estado PAGADO.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ordenes_abono_bancario_verificado
ON ordenes (banco_destino, cuenta_destino, numero_operacion)
WHERE estado_orden = 'PAGADO'
  AND banco_destino IS NOT NULL
  AND cuenta_destino IS NOT NULL
  AND numero_operacion IS NOT NULL;


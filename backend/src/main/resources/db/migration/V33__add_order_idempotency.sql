-- Migración V33: Idempotencia persistente en creación de órdenes de compra
-- Permite registrar una clave de idempotencia por intento de compra y resolver
-- solicitudes duplicadas o reintentos concurrentes de forma atómica.

ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS clave_idempotencia VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ordenes_clave_idempotencia 
ON ordenes (clave_idempotencia) 
WHERE clave_idempotencia IS NOT NULL;


-- =========================================================================
-- MIGRACIÓN V18: Permitir estado 'CANCELADO' en la restricción check de ORDENES
-- Asegura consistencia de base de datos para expiraciones y cancelaciones
-- =========================================================================

ALTER TABLE ORDENES DROP CONSTRAINT IF EXISTS ordenes_estado_orden_check;

ALTER TABLE ORDENES ADD CONSTRAINT ordenes_estado_orden_check 
    CHECK (estado_orden IN ('PENDIENTE', 'PAGADO', 'FALLIDO', 'REEMBOLSADO', 'CANCELADO'));

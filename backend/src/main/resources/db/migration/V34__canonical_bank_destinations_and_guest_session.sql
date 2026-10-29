-- Migración V34: Canonización de destinos bancarios y hash de sesión de recuperación para invitados
--
-- 1. Columna para almacenar el hash SHA-256 de la sesión autorizada de checkout para invitados.
--    Permite validar criptográficamente los reintentos de recuperación sin basarse únicamente en email o buyerScope.
ALTER TABLE ordenes ADD COLUMN IF NOT EXISTS guest_session_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_ordenes_guest_session_hash
ON ordenes (guest_session_hash)
WHERE guest_session_hash IS NOT NULL;

-- 2. Verificación y resolución de variantes históricas:
--    De acuerdo con la directriz de seguridad e integridad contable, no se alteran ni borran
--    filas de órdenes históricas de forma automática para evitar pérdida de datos o auditoría.
--    Las nuevas aprobaciones y consultas resuelven estrictamente a los destinos canónicos oficiales
--    ('BCP', 'INTERBANK', 'YAPE', 'PLIN') y sus cuentas oficiales normalizadas, garantizando que el
--    índice único 'uq_ordenes_abono_bancario_verificado' impida la reutilización de un mismo abono
--    bajo nombres alternativos o variantes de cuenta/CCI.


-- Migración V14: Soporte nativo para pasarela de pagos izipay Online (Tarjetas globales y wallets)
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS izipay_transaction_id VARCHAR(255);
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS izipay_order_id VARCHAR(255);
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS izipay_uuid VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_ordenes_izipay_tx ON ORDENES(izipay_transaction_id);
CREATE INDEX IF NOT EXISTS idx_ordenes_izipay_order ON ORDENES(izipay_order_id);

-- Actualizar valor predeterminado de metodo_pago a IZIPAY
ALTER TABLE ORDENES ALTER COLUMN metodo_pago SET DEFAULT 'IZIPAY';

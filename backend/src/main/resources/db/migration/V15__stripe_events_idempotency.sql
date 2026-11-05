-- Migración V15: Tabla de auditoría e idempotencia para eventos de pasarela Stripe
CREATE TABLE IF NOT EXISTS STRIPE_PROCESSED_EVENT (
    id_evento VARCHAR(255) PRIMARY KEY,
    tipo_evento VARCHAR(100) NOT NULL,
    stripe_session_id VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255),
    id_orden BIGINT REFERENCES ORDENES(id_orden) ON DELETE SET NULL,
    codigo_orden VARCHAR(36),
    estado VARCHAR(30) NOT NULL DEFAULT 'RECIBIDO',
    mensaje_error TEXT,
    fecha_registro TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_procesamiento TIMESTAMPTZ,
    CONSTRAINT chk_stripe_event_estado CHECK (estado IN ('RECIBIDO', 'PROCESANDO', 'COMPLETADO', 'FALLIDO'))
);

CREATE INDEX IF NOT EXISTS idx_stripe_event_session ON STRIPE_PROCESSED_EVENT(stripe_session_id);
CREATE INDEX IF NOT EXISTS idx_stripe_event_pi ON STRIPE_PROCESSED_EVENT(stripe_payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_stripe_event_orden ON STRIPE_PROCESSED_EVENT(id_orden);
CREATE INDEX IF NOT EXISTS idx_stripe_event_codigo ON STRIPE_PROCESSED_EVENT(codigo_orden);

-- Asegurar índice para búsquedas rápidas por stripe_payment_intent_id en ORDENES
CREATE INDEX IF NOT EXISTS idx_ordenes_stripe_pi ON ORDENES(stripe_payment_intent_id);

-- Migración V17: Restricciones de unicidad para identificadores financieros de Stripe en ORDENES
CREATE UNIQUE INDEX IF NOT EXISTS uq_ordenes_stripe_pi ON ORDENES(stripe_payment_intent_id) WHERE stripe_payment_intent_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_ordenes_stripe_session ON ORDENES(stripe_session_id) WHERE stripe_session_id IS NOT NULL;

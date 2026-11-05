-- Migración V16: Campos de arrendamiento (lease), expiración y concurrencia para eventos de Stripe
ALTER TABLE STRIPE_PROCESSED_EVENT
    ADD COLUMN IF NOT EXISTS lease_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS owner_token VARCHAR(64),
    ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_stripe_event_lease ON STRIPE_PROCESSED_EVENT(estado, lease_expires_at);

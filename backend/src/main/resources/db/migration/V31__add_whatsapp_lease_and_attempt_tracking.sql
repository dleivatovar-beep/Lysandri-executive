-- V31__add_whatsapp_lease_and_attempt_tracking.sql
-- Control de reservas atómicas, tokens de intento y correlación de webhooks para WhatsApp

ALTER TABLE whatsapp_mensaje
    ADD COLUMN IF NOT EXISTS token_intento VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bloqueado_hasta TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS intento_count INT DEFAULT 0 NOT NULL;

CREATE INDEX IF NOT EXISTS idx_whatsapp_token_intento ON whatsapp_mensaje(token_intento);
CREATE INDEX IF NOT EXISTS idx_whatsapp_bloqueado_hasta ON whatsapp_mensaje(bloqueado_hasta);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE whatsapp_mensaje TO lysandri_app;
    END IF;
END $$;


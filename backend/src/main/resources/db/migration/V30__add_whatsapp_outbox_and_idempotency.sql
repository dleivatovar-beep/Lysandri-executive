-- V30__add_whatsapp_outbox_and_idempotency.sql
-- Control de idempotencia y desacoplamiento de transacciones para WhatsApp Cloud API

ALTER TABLE whatsapp_mensaje
    ADD COLUMN IF NOT EXISTS id_solicitud VARCHAR(100) UNIQUE;

CREATE INDEX IF NOT EXISTS idx_whatsapp_id_solicitud ON whatsapp_mensaje(id_solicitud);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE whatsapp_mensaje TO lysandri_app;
        IF to_regclass('public.whatsapp_mensaje_id_seq') IS NOT NULL THEN
            GRANT USAGE, SELECT ON SEQUENCE whatsapp_mensaje_id_seq TO lysandri_app;
        END IF;
    END IF;
END $$;


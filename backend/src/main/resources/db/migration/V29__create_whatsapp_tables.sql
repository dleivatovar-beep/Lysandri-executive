-- V29__create_whatsapp_tables.sql
-- Módulo de Atención al Cliente por WhatsApp (API Oficial Meta WhatsApp Business Cloud API)

CREATE TABLE IF NOT EXISTS whatsapp_mensaje (
    id BIGSERIAL PRIMARY KEY,
    id_proveedor VARCHAR(120) UNIQUE,
    telefono_remitente VARCHAR(50) NOT NULL,
    telefono_destinatario VARCHAR(50) NOT NULL,
    direccion VARCHAR(20) NOT NULL, -- ENTRANTE, SALIENTE
    contenido TEXT,
    tipo VARCHAR(30) NOT NULL,      -- TEXTO, PLANTILLA, REACCION, OTRO
    estado VARCHAR(30) NOT NULL,    -- RECIBIDO, ENVIADO, ENTREGADO, LEIDO, FALLIDO
    timestamp_mensaje TIMESTAMP WITH TIME ZONE NOT NULL,
    operador VARCHAR(100),          -- Usuario operador que gestionó o respondió
    error_detalle TEXT,
    fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Índices para búsqueda rápida y ordenamiento
CREATE INDEX IF NOT EXISTS idx_whatsapp_id_proveedor ON whatsapp_mensaje(id_proveedor);
CREATE INDEX IF NOT EXISTS idx_whatsapp_telefono_remitente ON whatsapp_mensaje(telefono_remitente);
CREATE INDEX IF NOT EXISTS idx_whatsapp_telefono_destinatario ON whatsapp_mensaje(telefono_destinatario);
CREATE INDEX IF NOT EXISTS idx_whatsapp_timestamp ON whatsapp_mensaje(timestamp_mensaje);


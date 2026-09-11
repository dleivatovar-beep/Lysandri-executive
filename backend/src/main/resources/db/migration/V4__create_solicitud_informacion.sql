-- Migración V4: Creación de tabla para solicitudes de información (leads / prospectos)
CREATE TABLE IF NOT EXISTS SOLICITUD_INFORMACION (
    id_solicitud SERIAL PRIMARY KEY,
    nombre_completo VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    telefono VARCHAR(20),
    id_programa INT REFERENCES PROGRAMA(id_programa) ON DELETE SET NULL,
    mensaje TEXT,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    fecha_atencion TIMESTAMPTZ,
    notas_admin TEXT
);

CREATE INDEX IF NOT EXISTS idx_solicitud_email ON SOLICITUD_INFORMACION(email);
CREATE INDEX IF NOT EXISTS idx_solicitud_estado ON SOLICITUD_INFORMACION(estado);
CREATE INDEX IF NOT EXISTS idx_solicitud_id_programa ON SOLICITUD_INFORMACION(id_programa);

CREATE TABLE incidencia_integracion (
    id UUID PRIMARY KEY,
    servicio VARCHAR(30) NOT NULL,
    referencia VARCHAR(120) NOT NULL,
    criticidad VARCHAR(10) NOT NULL CHECK (criticidad IN ('MEDIA', 'ALTA')),
    descripcion VARCHAR(500) NOT NULL,
    estado VARCHAR(15) NOT NULL CHECK (estado IN ('ABIERTA', 'SEGUIMIENTO', 'RESUELTA')),
    creada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resuelta_en TIMESTAMPTZ,
    UNIQUE (servicio, referencia)
);
CREATE TABLE evento_incidencia (
    id UUID PRIMARY KEY,
    incidencia UUID NOT NULL REFERENCES incidencia_integracion(id),
    estado VARCHAR(15) NOT NULL CHECK (estado IN ('ABIERTA', 'SEGUIMIENTO', 'RESUELTA')),
    comentario VARCHAR(1000) NOT NULL,
    operador VARCHAR(150) NOT NULL,
    fecha TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX incidencia_integracion_estado ON incidencia_integracion(estado, creada_en);
CREATE INDEX evento_incidencia_historial ON evento_incidencia(incidencia, fecha);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON incidencia_integracion TO lysandri_app;
        GRANT SELECT, INSERT ON evento_incidencia TO lysandri_app;
    END IF;
END $$;

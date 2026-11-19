CREATE TABLE visita_conversion_prueba (
    id UUID PRIMARY KEY,
    dispositivo VARCHAR(10) NOT NULL CHECK (dispositivo IN ('MOVIL','TABLETA','ESCRITORIO')),
    canal VARCHAR(10) NOT NULL CHECK (canal IN ('DIRECTO','CAMPANA')),
    consentimiento VARCHAR(20) NOT NULL,
    visita TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    inicio TIMESTAMPTZ,
    programa BIGINT CHECK (programa BETWEEN 1001 AND 1006),
    compra UUID UNIQUE REFERENCES compra_prueba(id) ON DELETE SET NULL
);
CREATE INDEX idx_visita_conversion_prueba_fecha ON visita_conversion_prueba(visita);
DO $$ BEGIN
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON visita_conversion_prueba TO lysandri_app;
    END IF;
END $$;

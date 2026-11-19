CREATE TABLE abono_liquidacion (
    id UUID PRIMARY KEY,
    liquidacion UUID NOT NULL REFERENCES liquidacion_pasarela(id),
    movimiento UUID NOT NULL UNIQUE REFERENCES movimiento_bancario(id),
    justificacion VARCHAR(1000) NOT NULL,
    creado_por VARCHAR(150) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (liquidacion, movimiento)
);
CREATE INDEX abono_liquidacion_busqueda ON abono_liquidacion(liquidacion);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT ON abono_liquidacion TO lysandri_app;
    END IF;
END $$;

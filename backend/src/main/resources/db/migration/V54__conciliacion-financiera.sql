CREATE TABLE liquidacion_pasarela (
    id UUID PRIMARY KEY,
    pasarela VARCHAR(30) NOT NULL,
    referencia VARCHAR(120) NOT NULL,
    moneda VARCHAR(3) NOT NULL CHECK (moneda IN ('PEN', 'USD')),
    bruto NUMERIC(14,2) NOT NULL CHECK (bruto >= 0),
    comision NUMERIC(14,2) NOT NULL CHECK (comision >= 0),
    retencion NUMERIC(14,2) NOT NULL CHECK (retencion >= 0),
    otros_cargos NUMERIC(14,2) NOT NULL CHECK (otros_cargos >= 0),
    neto NUMERIC(14,2) GENERATED ALWAYS AS (bruto - comision - retencion - otros_cargos) STORED,
    fecha_estimada DATE NOT NULL,
    fecha_efectiva DATE,
    cuenta VARCHAR(120) NOT NULL,
    entorno VARCHAR(10) NOT NULL CHECK (entorno IN ('PRUEBA', 'REAL')),
    creado_por VARCHAR(150) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (bruto >= comision + retencion + otros_cargos),
    UNIQUE (pasarela, referencia, entorno)
);
CREATE TABLE movimiento_bancario (
    id UUID PRIMARY KEY,
    cuenta VARCHAR(120) NOT NULL,
    referencia VARCHAR(120) NOT NULL,
    moneda VARCHAR(3) NOT NULL CHECK (moneda IN ('PEN', 'USD')),
    importe NUMERIC(14,2) NOT NULL CHECK (importe > 0),
    fecha DATE NOT NULL,
    pasarela VARCHAR(30) NOT NULL,
    entorno VARCHAR(10) NOT NULL CHECK (entorno IN ('PRUEBA', 'REAL')),
    creado_por VARCHAR(150) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (cuenta, referencia, entorno)
);
CREATE TABLE conciliacion_bancaria (
    id UUID PRIMARY KEY,
    liquidacion UUID NOT NULL REFERENCES liquidacion_pasarela(id),
    movimiento UUID NOT NULL REFERENCES movimiento_bancario(id),
    estado VARCHAR(15) NOT NULL CHECK (estado IN ('CONCILIADA', 'OBSERVADA')),
    diferencia NUMERIC(14,2) NOT NULL,
    justificacion VARCHAR(1000) NOT NULL,
    creado_por VARCHAR(150) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (liquidacion),
    UNIQUE (movimiento)
);
CREATE INDEX liquidacion_pasarela_fecha ON liquidacion_pasarela(fecha_estimada);
CREATE INDEX movimiento_bancario_fecha ON movimiento_bancario(fecha);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT ON liquidacion_pasarela, movimiento_bancario, conciliacion_bancaria TO lysandri_app;
    END IF;
END $$;

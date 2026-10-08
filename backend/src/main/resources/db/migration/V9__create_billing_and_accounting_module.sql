-- Migración V9: Módulo de facturación y comprobantes de pago
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_comprobante') THEN
        CREATE TYPE tipo_comprobante AS ENUM ('BOLETA', 'FACTURA');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_comprobante') THEN
        CREATE TYPE estado_comprobante AS ENUM ('EMITIDO', 'ANULADO');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS comprobante_pago (
    id_comprobante SERIAL PRIMARY KEY,
    id_orden BIGINT NOT NULL,
    tipo tipo_comprobante NOT NULL,
    serie VARCHAR(5) NOT NULL,
    correlativo INT NOT NULL,
    tipo_documento_identidad VARCHAR(10) NOT NULL,
    numero_documento_identidad VARCHAR(15) NOT NULL,
    razon_social_o_nombre VARCHAR(200) NOT NULL,
    monto_subtotal NUMERIC(10, 2) NOT NULL,
    monto_igv NUMERIC(10, 2) NOT NULL,
    monto_total NUMERIC(10, 2) NOT NULL,
    moneda VARCHAR(3) DEFAULT 'PEN',
    fecha_emision TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    estado estado_comprobante DEFAULT 'EMITIDO',
    pdf_url VARCHAR(255),
    codigo_hash VARCHAR(64),
    monto_detraccion NUMERIC(10, 2) DEFAULT 0.00,
    porcentaje_detraccion NUMERIC(5, 2) DEFAULT 0.00,
    medio_pago VARCHAR(50) DEFAULT 'STRIPE_CHECKOUT',
    motivo_anulacion VARCHAR(255),
    fecha_anulacion TIMESTAMP,
    CONSTRAINT uq_comprobante_pago_id_orden UNIQUE (id_orden),
    CONSTRAINT chk_tipo_documento_identidad CHECK (tipo_documento_identidad IN ('DNI', 'RUC')),
    CONSTRAINT chk_correlativo_positivo CHECK (correlativo > 0),
    CONSTRAINT chk_monto_subtotal_positivo CHECK (monto_subtotal >= 0),
    CONSTRAINT chk_monto_igv_positivo CHECK (monto_igv >= 0),
    CONSTRAINT chk_monto_total_positivo CHECK (monto_total >= 0)
);

ALTER TABLE comprobante_pago ADD COLUMN IF NOT EXISTS codigo_hash VARCHAR(64);
ALTER TABLE comprobante_pago ADD COLUMN IF NOT EXISTS monto_detraccion NUMERIC(10, 2) DEFAULT 0.00;
ALTER TABLE comprobante_pago ADD COLUMN IF NOT EXISTS porcentaje_detraccion NUMERIC(5, 2) DEFAULT 0.00;
ALTER TABLE comprobante_pago ADD COLUMN IF NOT EXISTS medio_pago VARCHAR(50) DEFAULT 'STRIPE_CHECKOUT';
ALTER TABLE comprobante_pago ADD COLUMN IF NOT EXISTS motivo_anulacion VARCHAR(255);
ALTER TABLE comprobante_pago ADD COLUMN IF NOT EXISTS fecha_anulacion TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uq_comprobante_serie_correlativo 
    ON comprobante_pago (serie, correlativo);

CREATE INDEX IF NOT EXISTS idx_comprobante_fecha_emision 
    ON comprobante_pago (fecha_emision);
CREATE INDEX IF NOT EXISTS idx_comprobante_doc_identidad 
    ON comprobante_pago (numero_documento_identidad);
CREATE INDEX IF NOT EXISTS idx_comprobante_tipo_estado 
    ON comprobante_pago (tipo, estado);

DO $$
DECLARE
    v_target_table text;
    v_col_type text;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ordenes') THEN
        v_target_table := 'ordenes';
    ELSIF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'orden') THEN
        v_target_table := 'orden';
    END IF;

    IF v_target_table IS NOT NULL THEN
        SELECT data_type INTO v_col_type
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = v_target_table AND column_name = 'id_orden';

        IF v_col_type = 'integer' THEN
            ALTER TABLE comprobante_pago ALTER COLUMN id_orden TYPE INT;
        ELSE
            ALTER TABLE comprobante_pago ALTER COLUMN id_orden TYPE BIGINT;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_comprobante_pago_orden') THEN
            EXECUTE format(
                'ALTER TABLE comprobante_pago 
                 ADD CONSTRAINT fk_comprobante_pago_orden 
                 FOREIGN KEY (id_orden) 
                 REFERENCES %I(id_orden) 
                 ON DELETE RESTRICT',
                v_target_table
            );
        END IF;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'ordenes') THEN
        ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS tipo_comprobante_solicitado VARCHAR(10);
        ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS numero_documento_cliente VARCHAR(15);
        ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS nombre_facturacion VARCHAR(200);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'orden') THEN
        ALTER TABLE ORDEN ADD COLUMN IF NOT EXISTS tipo_comprobante_solicitado VARCHAR(10);
        ALTER TABLE ORDEN ADD COLUMN IF NOT EXISTS numero_documento_cliente VARCHAR(15);
        ALTER TABLE ORDEN ADD COLUMN IF NOT EXISTS nombre_facturacion VARCHAR(200);
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE comprobante_pago TO lysandri_app;
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO lysandri_app;
    END IF;
END $$;

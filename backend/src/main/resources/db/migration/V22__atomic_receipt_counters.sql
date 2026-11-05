CREATE TABLE IF NOT EXISTS comprobante_correlativo (
    serie VARCHAR(5) PRIMARY KEY,
    ultimo_correlativo BIGINT NOT NULL CHECK (ultimo_correlativo >= 0)
);

INSERT INTO comprobante_correlativo (serie, ultimo_correlativo)
SELECT serie, COALESCE(MAX(correlativo), 0)
FROM comprobante_pago
GROUP BY serie
ON CONFLICT (serie) DO UPDATE
SET ultimo_correlativo = GREATEST(
    comprobante_correlativo.ultimo_correlativo,
    EXCLUDED.ultimo_correlativo
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE comprobante_correlativo TO lysandri_app;
    END IF;
END $$;

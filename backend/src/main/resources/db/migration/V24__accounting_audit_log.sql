CREATE TABLE IF NOT EXISTS auditoria_contable (
    id_auditoria BIGSERIAL PRIMARY KEY,
    accion VARCHAR(50) NOT NULL,
    entidad VARCHAR(50) NOT NULL,
    entidad_id BIGINT NOT NULL,
    actor VARCHAR(150) NOT NULL,
    motivo VARCHAR(255),
    fecha_hora TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auditoria_contable_fecha
    ON auditoria_contable (fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_contable_entidad
    ON auditoria_contable (entidad, entidad_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT ON TABLE auditoria_contable TO lysandri_app;
        GRANT USAGE, SELECT ON SEQUENCE auditoria_contable_id_auditoria_seq TO lysandri_app;
    END IF;
END $$;

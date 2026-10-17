-- Migración V13: Soporte para pagos manuales y auditoría de matrícula corporativa
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS numero_operacion VARCHAR(100);
ALTER TABLE ORDENES ADD COLUMN IF NOT EXISTS notas_admin VARCHAR(500);

-- Asegurar índices para búsqueda rápida de órdenes por estado
CREATE INDEX IF NOT EXISTS idx_ordenes_estado ON ORDENES(estado_orden);
CREATE INDEX IF NOT EXISTS idx_ordenes_fecha ON ORDENES(fecha_orden DESC);

-- Tabla de auditoría para registro de lotes de matrículas corporativas
CREATE TABLE IF NOT EXISTS LOTE_MATRICULA_CORPORATIVA (
    id_lote BIGSERIAL PRIMARY KEY,
    id_programa BIGINT NOT NULL REFERENCES PROGRAMA(id_programa) ON DELETE RESTRICT,
    id_solicitud INT REFERENCES SOLICITUD_INFORMACION(id_solicitud) ON DELETE SET NULL,
    empresa_nombre VARCHAR(200) NOT NULL,
    total_colaboradores INT NOT NULL DEFAULT 0,
    total_exitosos INT NOT NULL DEFAULT 0,
    total_fallidos INT NOT NULL DEFAULT 0,
    fecha_registro TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notas_admin TEXT
);

CREATE INDEX IF NOT EXISTS idx_lote_corp_programa ON LOTE_MATRICULA_CORPORATIVA(id_programa);

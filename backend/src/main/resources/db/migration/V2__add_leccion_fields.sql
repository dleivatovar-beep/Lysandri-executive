-- Migración para ampliar la tabla LECCION con campos de descripción y orden
ALTER TABLE LECCION ADD COLUMN IF NOT EXISTS descripcion TEXT;
ALTER TABLE LECCION ADD COLUMN IF NOT EXISTS orden INT DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_leccion_orden ON LECCION(id_programa, orden);

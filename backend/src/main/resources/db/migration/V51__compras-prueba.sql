CREATE TABLE beneficio_prueba (
 codigo VARCHAR(30) PRIMARY KEY,
 porcentaje NUMERIC(5,2) NOT NULL CHECK(porcentaje > 0 AND porcentaje <= 100),
 cupos INTEGER NOT NULL CHECK(cupos > 0),
 usados INTEGER NOT NULL DEFAULT 0 CHECK(usados >= 0 AND usados <= cupos),
 vence TIMESTAMPTZ NOT NULL,
 activo BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE compra_prueba (
 id UUID PRIMARY KEY,
 id_usuario BIGINT NOT NULL REFERENCES usuario(id_user),
 clave UUID NOT NULL,
 solicitud_hash VARCHAR(64) NOT NULL,
 detalle JSONB NOT NULL,
 estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','APROBADA','RECHAZADA')),
 fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(id_usuario,clave)
);
CREATE TABLE evento_compra_prueba (
 evento UUID PRIMARY KEY,
 id_compra UUID NOT NULL REFERENCES compra_prueba(id),
 id_operador BIGINT NOT NULL REFERENCES usuario(id_user),
 resultado VARCHAR(15) NOT NULL CHECK(resultado IN ('APROBADA','RECHAZADA')),
 fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='lysandri_app') THEN
  GRANT SELECT, INSERT, UPDATE ON beneficio_prueba, compra_prueba TO lysandri_app;
  GRANT SELECT, INSERT ON evento_compra_prueba TO lysandri_app;
 END IF;
END $$;

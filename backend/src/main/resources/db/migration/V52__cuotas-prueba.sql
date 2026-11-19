ALTER TABLE compra_prueba DROP CONSTRAINT compra_prueba_estado_check;
ALTER TABLE compra_prueba ADD CONSTRAINT compra_prueba_estado_check CHECK(estado IN ('PENDIENTE','EN_CUOTAS','APROBADA','RECHAZADA'));
CREATE TABLE cuota_compra_prueba (
 id_compra UUID NOT NULL REFERENCES compra_prueba(id),
 numero INTEGER NOT NULL CHECK(numero BETWEEN 1 AND 3),
 vence DATE NOT NULL,
 importe NUMERIC(10,2) NOT NULL CHECK(importe >= 0),
 estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','PAGADA','FALLIDA')),
 PRIMARY KEY(id_compra,numero)
);
INSERT INTO cuota_compra_prueba(id_compra,numero,vence,importe,estado)
 SELECT c.id,(q->>'numero')::integer,(q->>'vence')::date,(q->>'importe')::numeric,
 CASE c.estado WHEN 'APROBADA' THEN 'PAGADA' WHEN 'RECHAZADA' THEN 'FALLIDA' ELSE 'PENDIENTE' END
 FROM compra_prueba c CROSS JOIN LATERAL jsonb_array_elements(c.detalle->'cuotas') q;
CREATE TABLE evento_cuota_prueba (
 evento UUID PRIMARY KEY,
 id_compra UUID NOT NULL,
 numero INTEGER NOT NULL,
 id_operador BIGINT NOT NULL REFERENCES usuario(id_user),
 resultado VARCHAR(15) NOT NULL CHECK(resultado IN ('PAGADA','FALLIDA')),
 fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(id_compra,numero) REFERENCES cuota_compra_prueba(id_compra,numero)
);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='lysandri_app') THEN
  GRANT SELECT, INSERT, UPDATE ON cuota_compra_prueba TO lysandri_app;
  GRANT SELECT, INSERT ON evento_cuota_prueba TO lysandri_app;
 END IF;
END $$;

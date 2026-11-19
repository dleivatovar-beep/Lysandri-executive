ALTER TABLE beneficio_prueba ADD COLUMN tipo VARCHAR(15) NOT NULL DEFAULT 'DESCUENTO'
    CHECK (tipo IN ('DESCUENTO','BECA','CONVENIO'));
ALTER TABLE beneficio_prueba ADD COLUMN importe_fijo NUMERIC(10,2) CHECK (importe_fijo > 0);
ALTER TABLE beneficio_prueba ADD COLUMN programa BIGINT CHECK (programa BETWEEN 1001 AND 1006);
ALTER TABLE beneficio_prueba ADD COLUMN cliente BIGINT REFERENCES usuario(id_user) ON DELETE CASCADE;
ALTER TABLE beneficio_prueba ADD COLUMN limite_cliente INTEGER CHECK (limite_cliente > 0);
ALTER TABLE beneficio_prueba ADD COLUMN acumulable BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE beneficio_prueba ADD COLUMN inicio TIMESTAMPTZ NOT NULL DEFAULT '-infinity';

CREATE TABLE plan_cobro_prueba (
    compra UUID PRIMARY KEY REFERENCES compra_prueba(id) ON DELETE CASCADE,
    estado VARCHAR(12) NOT NULL CHECK (estado IN ('ACTIVO','CANCELADO','TERMINADO')),
    escenario VARCHAR(15) NOT NULL CHECK (escenario IN ('APROBAR','RECUPERAR','RECHAZAR')),
    autorizado TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE transaccion_pasarela_prueba (
    referencia UUID PRIMARY KEY,
    compra UUID NOT NULL,
    cuota INTEGER NOT NULL,
    intento INTEGER NOT NULL CHECK (intento BETWEEN 1 AND 3),
    importe NUMERIC(10,2) NOT NULL CHECK (importe >= 0),
    moneda VARCHAR(3) NOT NULL CHECK (moneda IN ('USD','PEN')),
    estado VARCHAR(10) NOT NULL CHECK (estado IN ('PAGADA','FALLIDA')),
    motivo VARCHAR(60),
    fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (compra, cuota, intento),
    FOREIGN KEY(compra,cuota) REFERENCES cuota_compra_prueba(id_compra,numero) ON DELETE CASCADE
);
CREATE TABLE reporte_pasarela_prueba (
    referencia UUID PRIMARY KEY,
    importe NUMERIC(10,2) NOT NULL CHECK (importe >= 0),
    moneda VARCHAR(3) NOT NULL CHECK (moneda IN ('USD','PEN')),
    estado VARCHAR(10) NOT NULL CHECK (estado IN ('PAGADA','FALLIDA')),
    recibido TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE conciliacion_pasarela_prueba (
    referencia UUID PRIMARY KEY,
    estado VARCHAR(15) NOT NULL CHECK (estado IN ('CONCILIADA','PENDIENTE','OBSERVADA','SIN_COMPRA')),
    revision TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orden_empresa_prueba (
    id UUID PRIMARY KEY,
    gestor BIGINT NOT NULL REFERENCES usuario(id_user),
    operador BIGINT NOT NULL REFERENCES usuario(id_user),
    empresa VARCHAR(120) NOT NULL,
    oc VARCHAR(40) NOT NULL,
    programa BIGINT NOT NULL CHECK (programa BETWEEN 1001 AND 1006),
    titulo VARCHAR(200) NOT NULL,
    cohorte VARCHAR(80) NOT NULL,
    cupos INTEGER NOT NULL CHECK (cupos BETWEEN 1 AND 50),
    moneda VARCHAR(3) NOT NULL CHECK (moneda IN ('USD','PEN')),
    bruto NUMERIC(12,2) NOT NULL CHECK (bruto > 0),
    descuento NUMERIC(12,2) NOT NULL CHECK (descuento >= 0 AND descuento < bruto),
    base NUMERIC(12,2) NOT NULL CHECK (base >= 0),
    impuesto NUMERIC(12,2) NOT NULL CHECK (impuesto >= 0),
    condicion VARCHAR(10) NOT NULL CHECK (condicion IN ('CONTADO','CREDITO')),
    vence DATE,
    estado VARCHAR(12) NOT NULL CHECK (estado IN ('PENDIENTE','APROBADA','EN_TRANSITO','ENTREGADA','PAGADA','A_CREDITO','ANULADA')),
    fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (gestor,oc),
    CHECK (base + impuesto = bruto - descuento),
    CHECK ((condicion = 'CREDITO') = (vence IS NOT NULL))
);
CREATE TABLE participante_empresa_prueba (
    orden UUID NOT NULL REFERENCES orden_empresa_prueba(id) ON DELETE CASCADE,
    alias VARCHAR(40) NOT NULL,
    cargo VARCHAR(80) NOT NULL,
    PRIMARY KEY (orden,alias)
);
CREATE TABLE evento_empresa_prueba (
    orden UUID NOT NULL REFERENCES orden_empresa_prueba(id) ON DELETE CASCADE,
    estado VARCHAR(12) NOT NULL,
    operador BIGINT NOT NULL REFERENCES usuario(id_user),
    fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(orden,estado)
);
CREATE INDEX idx_orden_empresa_prueba_gestor ON orden_empresa_prueba(gestor,fecha DESC);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON plan_cobro_prueba, conciliacion_pasarela_prueba, orden_empresa_prueba TO lysandri_app;
        GRANT SELECT, INSERT ON transaccion_pasarela_prueba, reporte_pasarela_prueba,
            participante_empresa_prueba, evento_empresa_prueba TO lysandri_app;
    END IF;
END $$;

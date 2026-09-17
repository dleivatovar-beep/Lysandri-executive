CREATE TABLE BLOQUE (
    id_bloque SERIAL PRIMARY KEY,
    id_programa INT NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    orden INT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_bloque_programa
        FOREIGN KEY (id_programa)
        REFERENCES PROGRAMA(id_programa)
        ON DELETE CASCADE,

    CONSTRAINT uk_bloque_programa_orden
        UNIQUE (id_programa, orden),

    CONSTRAINT chk_bloque_orden_positivo
        CHECK (orden > 0)
);

CREATE INDEX idx_bloque_programa
    ON BLOQUE(id_programa);


CREATE TABLE MODULO (
    id_modulo SERIAL PRIMARY KEY,
    id_bloque INT NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    orden INT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_modulo_bloque
        FOREIGN KEY (id_bloque)
        REFERENCES BLOQUE(id_bloque)
        ON DELETE CASCADE,

    CONSTRAINT uk_modulo_bloque_orden
        UNIQUE (id_bloque, orden),

    CONSTRAINT chk_modulo_orden_positivo
        CHECK (orden > 0)
);

CREATE INDEX idx_modulo_bloque
    ON MODULO(id_bloque);


CREATE TABLE ACTIVIDAD_BLOQUE (
    id_actividad SERIAL PRIMARY KEY,
    id_bloque INT NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    descripcion TEXT,
    instrucciones TEXT,
    tipo_actividad VARCHAR(30) NOT NULL,
    contenido_url VARCHAR(500),
    orden INT NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_actividad_bloque
        FOREIGN KEY (id_bloque)
        REFERENCES BLOQUE(id_bloque)
        ON DELETE CASCADE,

    CONSTRAINT uk_actividad_bloque_orden
        UNIQUE (id_bloque, orden),

    CONSTRAINT chk_actividad_orden_positivo
        CHECK (orden > 0),

    CONSTRAINT chk_actividad_tipo
        CHECK (
            tipo_actividad IN (
                'RETO',
                'SIMULACION',
                'CASO',
                'DESAFIO_FINAL'
            )
        )
);

CREATE INDEX idx_actividad_bloque
    ON ACTIVIDAD_BLOQUE(id_bloque);


ALTER TABLE LECCION
    ADD COLUMN id_modulo INT;

ALTER TABLE LECCION
    ADD CONSTRAINT fk_leccion_modulo
        FOREIGN KEY (id_modulo)
        REFERENCES MODULO(id_modulo)
        ON DELETE CASCADE;

CREATE INDEX idx_leccion_modulo_orden
    ON LECCION(id_modulo, orden);
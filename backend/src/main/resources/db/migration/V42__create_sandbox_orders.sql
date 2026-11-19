CREATE TABLE sandbox_orders (
    id BIGSERIAL PRIMARY KEY,
    codigo_orden VARCHAR(50) NOT NULL UNIQUE,
    clave_idempotencia VARCHAR(150) NOT NULL UNIQUE,
    total NUMERIC(10, 2) NOT NULL CHECK (total > 0),
    moneda VARCHAR(3) NOT NULL CHECK (moneda = 'USD'),
    estado VARCHAR(20) NOT NULL CHECK (estado = 'SIMULADA'),
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sandbox_order_items (
    id BIGSERIAL PRIMARY KEY,
    sandbox_order_id BIGINT NOT NULL REFERENCES sandbox_orders(id) ON DELETE CASCADE,
    id_programa BIGINT NOT NULL CHECK (id_programa > 0),
    titulo VARCHAR(200) NOT NULL,
    precio_unitario NUMERIC(10, 2) NOT NULL CHECK (precio_unitario = 1.00),
    cantidad INTEGER NOT NULL DEFAULT 1 CHECK (cantidad = 1),
    subtotal NUMERIC(10, 2) NOT NULL CHECK (subtotal = precio_unitario * cantidad),
    UNIQUE (sandbox_order_id, id_programa)
);

CREATE INDEX idx_sandbox_order_items_order ON sandbox_order_items(sandbox_order_id);

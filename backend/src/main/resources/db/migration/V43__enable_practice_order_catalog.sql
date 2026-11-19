ALTER TABLE sandbox_orders
    DROP CONSTRAINT sandbox_orders_estado_check;

UPDATE sandbox_orders SET estado = 'PRACTICA' WHERE estado = 'SIMULADA';

ALTER TABLE sandbox_orders
    ADD CONSTRAINT sandbox_orders_estado_check CHECK (estado = 'PRACTICA');

ALTER TABLE sandbox_orders
    ADD COLUMN comprador_nombre VARCHAR(160),
    ADD COLUMN comprador_email VARCHAR(200),
    ADD COLUMN comprador_telefono VARCHAR(40);

ALTER TABLE sandbox_order_items
    DROP CONSTRAINT sandbox_order_items_precio_unitario_check,
    ADD CONSTRAINT sandbox_order_items_precio_unitario_check CHECK (precio_unitario > 0);

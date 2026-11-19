DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT ON TABLE sandbox_orders, sandbox_order_items TO lysandri_app;
        GRANT USAGE, SELECT ON SEQUENCE sandbox_orders_id_seq, sandbox_order_items_id_seq TO lysandri_app;
    END IF;
END $$;

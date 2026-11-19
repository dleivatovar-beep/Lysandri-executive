DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'lysandri_app') THEN
        GRANT SELECT, INSERT, UPDATE ON TABLE public.chat_session TO lysandri_app;
    END IF;
END
$$;

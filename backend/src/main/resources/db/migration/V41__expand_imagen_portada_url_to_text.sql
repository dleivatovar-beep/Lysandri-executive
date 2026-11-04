-- V41: Expandir imagen_portada_url a TEXT para soportar URLs extensas y data URIs
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
          AND table_name = 'programa' 
          AND column_name = 'imagen_portada_url'
          AND data_type <> 'text'
    ) THEN
        ALTER TABLE programa ALTER COLUMN imagen_portada_url TYPE TEXT;
    END IF;
END $$;


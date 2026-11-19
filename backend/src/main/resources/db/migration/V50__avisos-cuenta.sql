CREATE TABLE aviso_cuenta (
 id_aviso BIGSERIAL PRIMARY KEY,
 id_user BIGINT NOT NULL REFERENCES usuario(id_user),
 tipo VARCHAR(30) NOT NULL CHECK(tipo IN ('PASSWORD_CAMBIADA','CUENTA_RECUPERADA')),
 fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 estado VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','ENVIANDO','ENVIADO','REVISAR'))
);
CREATE INDEX idx_aviso_cuenta_pendiente ON aviso_cuenta(estado,id_aviso);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='lysandri_app') THEN
  GRANT SELECT, INSERT, UPDATE ON aviso_cuenta TO lysandri_app;
  GRANT USAGE, SELECT ON SEQUENCE aviso_cuenta_id_aviso_seq TO lysandri_app;
 END IF;
END $$;

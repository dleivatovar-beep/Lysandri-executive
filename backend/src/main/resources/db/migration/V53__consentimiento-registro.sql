CREATE TABLE consentimiento_usuario (
 id_user BIGINT NOT NULL REFERENCES usuario(id_user),
 version VARCHAR(40) NOT NULL,
 terminos BOOLEAN NOT NULL CHECK(terminos),
 privacidad BOOLEAN NOT NULL CHECK(privacidad),
 fecha TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id_user,version)
);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='lysandri_app') THEN
  GRANT SELECT, INSERT ON consentimiento_usuario TO lysandri_app;
 END IF;
END $$;

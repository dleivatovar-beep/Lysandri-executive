-- Migración V12: Garantizar cuentas directivas fundadoras en tabla USUARIO
SELECT setval(pg_get_serial_sequence('usuario', 'id_user'), coalesce(max(id_user), 1)) FROM usuario;

INSERT INTO USUARIO (nombres, apellidos, email, passw, telefono, rol, activo)
VALUES 
('Danny Ronaldo', 'Leiva Tovar', 'danny@lysandri.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6', '+51 941 238 905', 'ADMIN', true),
('Antony Brayan', 'Ruiz Susanibar', 'antonybrayanruizsusanibar@gmail.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6', '+51 987 242 796', 'ADMIN', true)
ON CONFLICT (email) DO NOTHING;

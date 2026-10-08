-- Migración V12: Garantizar cuentas directivas fundadoras en tabla USUARIO
INSERT INTO USUARIO (nombres, apellidos, email, passw, telefono, rol, activo)
VALUES 
('Danny Ronaldo', 'Leiva Tovar', 'danny@lysandri.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6', '+51 941 238 905', 'ADMIN', true),
('Antony Brayan', 'Ruiz Susanibar', 'antonybrayanruizsusanibar@gmail.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6', '+51 987 242 796', 'ADMIN', true)
ON CONFLICT (email) DO UPDATE SET
    nombres = EXCLUDED.nombres,
    apellidos = EXCLUDED.apellidos,
    telefono = EXCLUDED.telefono,
    rol = 'ADMIN',
    activo = true;

-- Datos iniciales para pruebas locales
-- admin@lysandri.com / admin123
-- instructor@lysandri.com / instructor123
-- estudiante@lysandri.com / student123

INSERT INTO USUARIOS (nombres, apellidos, email, passw, telefono, rol) VALUES
('Admin', 'Lysandri', 'admin@lysandri.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6', '999888777', 'ADMIN'),
('Profesor', 'Valenzuela', 'instructor@lysandri.com', '$2a$10$PkPhBdiDGJITwZoojqimyuLhq1wLwDECWoZqK6PkXOdK0Z0XDL7Bm', '988777666', 'INSTRUCTOR'),
('Carlos', 'Mendoza', 'estudiante@lysandri.com', '$2a$10$WEX4ZF7I7KTjt1H7I25O5esf/EFiuZhing/Gd9uTZSFocFphUhRQq', '977666555', 'ESTUDIANTE')
ON CONFLICT (email) DO NOTHING;

INSERT INTO INSTRUCTOR (id_instructor_dni, id_user, especialidad, direccion_instructor) VALUES
('DNI10203040', 2, 'IA y Machine Learning', 'Av. Javier Prado 450, Lima')
ON CONFLICT (id_instructor_dni) DO NOTHING;

INSERT INTO PROGRAMA (id_programa, id_instructor_dni, titulo_programa, duracion_programa, tipo_programa, level, fecha_inicio_global, fecha_final_global, requisitos, metodologia) VALUES
(1, 'DNI10203040', 'IA para Ejecutivos', '8 semanas', 'Especializacion', 'Avanzado', CURRENT_DATE, CURRENT_DATE + 60, 'Experiencia directiva', 'Casos practicos y talleres')
ON CONFLICT (id_programa) DO NOTHING;

INSERT INTO LECCION (id_leccion, id_programa, titulo_leccion, descripcion, tipo_contenido, media_url, duracion_leccion, orden) VALUES
(1, 1, 'Fundamentos de IA y RAG', 'Conceptos clave de arquitectura RAG', 'VIDEO', 'https://video.lysandri.com/l1.mp4', '45m', 1),
(2, 1, 'Gobernanza y Seguridad', 'Privacidad y seguridad en cloud', 'VIDEO', 'https://video.lysandri.com/l2.mp4', '50m', 2)
ON CONFLICT (id_leccion) DO NOTHING;

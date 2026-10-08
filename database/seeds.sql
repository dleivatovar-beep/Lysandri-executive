INSERT INTO USUARIO (id_user, nombres, apellidos, email, passw, telefono, rol, moodle_user_id) VALUES
(1, 'Danny Ronaldo', 'Leiva Tovar', 'danny@lysandri.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6', '+51 941 238 905', 'ADMIN', 1),
(2, 'Antony Brayan', 'Ruiz Susanibar', 'antonybrayanruizsusanibar@gmail.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6', '+51 987 242 796', 'ADMIN', 2),
(3, 'Administrador General', 'Lysandri', 'admin@lysandri.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6', '+51 999 000 111', 'ADMIN', 3),
(4, 'Carlos', 'Mendoza Torres', 'carlos.mendoza@corporativo.com', '$2a$10$WEX4ZF7I7KTjt1H7I25O5esf/EFiuZhing/Gd9uTZSFocFphUhRQq', '+51 987 654 321', 'CLIENTE', 4)
ON CONFLICT (email) DO UPDATE SET
  nombres = EXCLUDED.nombres,
  apellidos = EXCLUDED.apellidos,
  rol = EXCLUDED.rol;

INSERT INTO PROGRAMA (
    id_programa, moodle_course_id, titulo, slug, subtitulo, descripcion_corta, descripcion_detallada,
    precio, moneda, imagen_portada_url, syllabus_url, instructor_nombre, instructor_bio, nivel, duracion_horas
) VALUES
(1, 101, 'FinOps Empresarial & Gobernanza Cloud', 'finops-empresarial', 
 'Maximiza la eficiencia y rentabilidad de tu infraestructura multi-cloud.', 
 'Domina la asignación de costos cloud, gestión de instancias reservadas y cultura de costos en AWS, Azure y GCP.',
 'Programa ejecutivo diseñado para Directores de Tecnología (CTO) y Gerentes de Operaciones que buscan reducir hasta un 40% en facturación cloud sin sacrificar rendimiento.',
 799.00, 'USD', 'https://images.unsplash.com/photo-1551288049-bebda4e38f71', 'https://lysandri.com/syllabi/finops.pdf',
 'Ing. Roberto Valenzuela', 'Principal Cloud Economist con 15+ años en arquitectura AWS/Azure.', 'EJECUTIVO', 40),

(2, 102, 'Ciberseguridad Corporativa & Estrategia Zero-Trust', 'ciberseguridad-corporativa', 
 'Liderazgo y resiliencia en ciberseguridad para comités directivos.',
 'Protección de infraestructura crítica, respuesta ante ransomware y cumplimiento NIS2 / ISO 27001.',
 'Especialización directiva para CISOs y líderes de seguridad para estructurar modelos de defensa en profundidad y gobierno de riesgos.',
 899.00, 'USD', 'https://images.unsplash.com/photo-1563986768609-322da13575f3', 'https://lysandri.com/syllabi/cybersecurity.pdf',
 'Dra. Elena Alarcón', 'Ex-CISO regional, asesora de seguridad en entidades financieras.', 'EJECUTIVO', 45),

(3, 103, 'Inteligencia Artificial Generativa para Ejecutivos C-Suite', 'ia-para-ejecutivos',
 'Adopción estratégica de Modelos Fundacionales y Gobernanza de IA.',
 'Diseño de casos de uso de IA generativa con retorno de inversión comprobado y mitigación de riesgos legales y éticos.',
 'Taller inmersivo enfocado en CEOs, CIOs y Directores de Innovación que buscan integrar asistentes inteligentes y automatización cognitiva.',
 950.00, 'USD', 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe', 'https://lysandri.com/syllabi/ia-csuite.pdf',
 'Dr. Marcos Sotomayor', 'Investigador de IA y consultor para compañías Fortune 500.', 'C-SUITE', 30),

(4, 104, 'Arquitectura Orientada a Eventos y Microservicios Resilientes', 'arquitectura-eventos-kafka',
 'Construcción de plataformas en tiempo real a escala masiva.',
 'Patrones EDA, Apache Kafka, Event Sourcing y transacciones distribuidas con el patrón Saga.',
 'Programa técnico-ejecutivo para Arquitectos de Software y Líderes Técnicos encargados de sistemas core de alto tráfico.',
 750.00, 'USD', 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5', 'https://lysandri.com/syllabi/eda-kafka.pdf',
 'Ing. David Poma', 'Lead Architect especializado en streaming de datos y microservicios.', 'AVANZADO', 40)
ON CONFLICT (id_programa) DO UPDATE SET
  moodle_course_id = EXCLUDED.moodle_course_id,
  titulo = EXCLUDED.titulo,
  slug = EXCLUDED.slug,
  precio = EXCLUDED.precio,
  syllabus_url = EXCLUDED.syllabus_url;

-- Sincronización de secuencias
SELECT setval('usuario_id_user_seq', COALESCE((SELECT MAX(id_user) FROM USUARIO), 1));
SELECT setval('programa_id_programa_seq', COALESCE((SELECT MAX(id_programa) FROM PROGRAMA), 1));

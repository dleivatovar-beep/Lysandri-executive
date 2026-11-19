-- The legacy catalog had no commercial prices; V8/V23 assigned 499 as a placeholder.
-- Keep these values for local practice only and leave every price unapproved for sale.
UPDATE public.programa AS p
SET precio = v.precio,
    precio_aprobado = FALSE
FROM (VALUES
    (1, 'FinOps Empresarial', 89.00),
    (2, 'Ciberseguridad Corporativa', 109.00),
    (3, 'Liderazgo Estrategico', 79.00),
    (4, 'Metodologias Agiles', 59.00),
    (5, 'Inteligencia Artificial para Ejecutivos', 99.00),
    (6, 'Arquitectura Orientada a Eventos con Kafka', 109.00),
    (7, 'Gobierno de Datos y Cumplimiento Regulatorio', 89.00),
    (8, 'Estrategia de Plataformas Cloud & EKS', 119.00),
    (9, 'DevOps Ejecutivo y Confiabilidad de Sistemas (SRE)', 109.00),
    (10, 'Gestion de Riesgos Tecnologicos y Resiliencia', 89.00),
    (11, 'Estrategia de Producto Digital e Innovacion C-Suite', 99.00)
) AS v(id_programa, titulo, precio)
WHERE p.id_programa = v.id_programa
  AND p.titulo = v.titulo
  AND p.precio = 499.00
  AND p.precio_aprobado = FALSE;

-- V12 used a shared password hash for these seeded accounts. Deactivate only rows
-- that still carry that exact migration hash; preserve any account with a changed password.
UPDATE public.usuario
SET activo = FALSE
WHERE (email, passw) IN (
    ('danny@lysandri.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6'),
    ('antonybrayanruizsusanibar@gmail.com', '$2a$10$NeFAj3nOfE3/FliITxEYT.FjAoeRL1pa3IE7eesqhPKfErgO91.C6')
);

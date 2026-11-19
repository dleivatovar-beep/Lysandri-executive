-- Corrige solo los textos heredados que conservan el valor dañado exacto.
-- No cambia precios, permisos ni disponibilidad comercial.

UPDATE public.programa SET descripcion_corta = 'Casos prácticos de optimización cloud y FinOps'
WHERE id_programa = 1 AND descripcion_corta = 'Casos pr?cticos de optimizaci?n cloud y FinOps';

UPDATE public.programa SET descripcion_detallada = 'Gestión de presupuestos o liderazgo en TI'
WHERE id_programa = 1 AND descripcion_detallada = 'Gesti?n de presupuestos o liderazgo en TI';

UPDATE public.programa SET descripcion_corta = 'Frameworks Zero-Trust, simulación de amenazas y respuesta a incidentes'
WHERE id_programa = 2 AND descripcion_corta = 'Frameworks Zero-Trust, simulaci?n de amenazas y respuesta a incidentes';

UPDATE public.programa SET descripcion_corta = 'Dinámicas de toma de decisiones de alto impacto y gestión del cambio'
WHERE id_programa = 3 AND descripcion_corta = 'Din?micas de toma de decisiones de alto impacto y gesti?n del cambio';

UPDATE public.programa SET descripcion_detallada = 'Experiencia liderando equipos técnicos'
WHERE id_programa = 3 AND descripcion_detallada = 'Experiencia liderando equipos t?cnicos';

UPDATE public.programa SET descripcion_corta = 'Implementación de Scrum, Kanban y principios Lean a escala'
WHERE id_programa = 4 AND descripcion_corta = 'Implementaci?n de Scrum, Kanban y principios Lean a escala';

UPDATE public.programa SET descripcion_detallada = 'Gestión de proyectos básica'
WHERE id_programa = 4 AND descripcion_detallada = 'Gesti?n de proyectos b?sica';

UPDATE public.programa SET descripcion_corta = 'Estrategia de adopción de LLMs, gobernanza y ROI de IA'
WHERE id_programa = 5 AND descripcion_corta = 'Estrategia de adopci?n de LLMs, gobernanza y ROI de IA';

UPDATE public.programa SET descripcion_detallada = 'Visión directiva y fundamentos tecnológicos'
WHERE id_programa = 5 AND descripcion_detallada = 'Visi?n directiva y fundamentos tecnol?gicos';

UPDATE public.programa SET descripcion_corta = 'Políticas de privacidad, GDPR, catálogo de datos y soberanía cloud'
WHERE id_programa = 7 AND descripcion_corta = 'Pol?ticas de privacidad, GDPR, cat?logo de datos y soberan?a cloud';

UPDATE public.programa SET descripcion_detallada = 'Gestión de información corporativa'
WHERE id_programa = 7 AND descripcion_detallada = 'Gesti?n de informaci?n corporativa';

UPDATE public.programa SET descripcion_corta = 'Modernización de aplicaciones, Kubernetes empresarial y multi-cloud'
WHERE id_programa = 8 AND descripcion_corta = 'Modernizaci?n de aplicaciones, Kubernetes empresarial y multi-cloud';

UPDATE public.programa SET descripcion_corta = 'Cultura SRE, SLIs/SLOs, automatización CI/CD y observabilidad 360'
WHERE id_programa = 9 AND descripcion_corta = 'Cultura SRE, SLIs/SLOs, automatizaci?n CI/CD y observabilidad 360';

UPDATE public.programa SET descripcion_detallada = 'Roles directivos o de auditoría'
WHERE id_programa = 10 AND descripcion_detallada = 'Roles directivos o de auditor?a';

UPDATE public.programa SET descripcion_corta = 'Product-Led Growth, experimentación continua y monetización digital'
WHERE id_programa = 11 AND descripcion_corta = 'Product-Led Growth, experimentaci?n continua y monetizaci?n digital';

UPDATE public.programa SET descripcion_detallada = 'Dirección de producto o tecnología'
WHERE id_programa = 11 AND descripcion_detallada = 'Direcci?n de producto o tecnolog?a';

import {
  Category,
  Playbook,
  ProgramaResponse,
  TierLevel,
} from '../types';

interface CoursePresentation {
  coverUrl: string;
  description: string;
  category: string;
  tags: string[];
  tier: TierLevel;
}

const normalize = (value: string) =>
  value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase();

const slugify = (value: string) =>
  normalize(value)
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');

const PRESENTATION_BY_TITLE: Record<
  string,
  CoursePresentation
> = {
  'inteligencia artificial para ejecutivos': {
    coverUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80',
    description:
      'Aprende a integrar asistentes inteligentes, automatizar la atención y ventas, y tomar decisiones estratégicas basadas en datos sin necesidad de saber programar.',
    category: 'Inteligencia Artificial',
    tags: ['Inteligencia Artificial', 'Estrategia', 'Decisiones', 'Productividad'],
    tier: 'ENTERPRISE',
  },

  'ia para ejecutivos': {
    coverUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80',
    description:
      'Aprende a integrar asistentes inteligentes, automatizar la atención y ventas, y tomar decisiones estratégicas basadas en datos sin necesidad de saber programar.',
    category: 'Inteligencia Artificial',
    tags: ['Inteligencia Artificial', 'Estrategia', 'Decisiones', 'Productividad'],
    tier: 'ENTERPRISE',
  },

  'inteligencia artificial para directivos & c-suite': {
    coverUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80',
    description:
      'Aprende a integrar asistentes inteligentes, automatizar la atención y ventas, y tomar decisiones estratégicas basadas en datos sin necesidad de saber programar.',
    category: 'Inteligencia Artificial',
    tags: ['Inteligencia Artificial', 'Estrategia', 'Decisiones', 'Productividad'],
    tier: 'ENTERPRISE',
  },

  'inteligencia artificial generativa para ejecutivos c-suite': {
    coverUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80',
    description:
      'Aprende a integrar asistentes inteligentes, automatizar la atención y ventas, y tomar decisiones estratégicas basadas en datos sin necesidad de saber programar.',
    category: 'Inteligencia Artificial',
    tags: ['Inteligencia Artificial', 'Estrategia', 'Decisiones', 'Productividad'],
    tier: 'ENTERPRISE',
  },

  'ciberseguridad corporativa': {
    coverUrl: '/courses/curso2.png',
    description:
      'Protege a tu organización contra estafas digitales, ransomware, robo de información confidencial y fraudes bancarios. Diseñado para directivos y líderes de negocio.',
    category: 'Seguridad y Riesgos',
    tags: ['Ciberseguridad', 'Protección de Datos', 'Antifraude', 'Gestión de Riesgos'],
    tier: 'ADVANCED',
  },

  'ciberseguridad & proteccion antifraude para empresas': {
    coverUrl: 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=800&q=80',
    description:
      'Protege a tu organización contra estafas digitales, ransomware, robo de información confidencial y fraudes bancarios. Diseñado para directivos y líderes de negocio.',
    category: 'Seguridad y Riesgos',
    tags: ['Ciberseguridad', 'Protección de Datos', 'Antifraude', 'Gestión de Riesgos'],
    tier: 'ADVANCED',
  },

  'ciberseguridad corporativa & estrategia zero-trust': {
    coverUrl: 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=800&q=80',
    description:
      'Protege a tu organización contra estafas digitales, ransomware, robo de información confidencial y fraudes bancarios. Diseñado para directivos y líderes de negocio.',
    category: 'Seguridad y Riesgos',
    tags: ['Ciberseguridad', 'Protección de Datos', 'Antifraude', 'Gestión de Riesgos'],
    tier: 'ADVANCED',
  },

  'finops empresarial': {
    coverUrl: '/courses/curso1.png',
    description:
      'Aprende a auditar y reducir hasta un 40% en gastos de tecnología, servicios en la nube y licencias operativas, maximizando el margen de rentabilidad y retorno de inversión (ROI).',
    category: 'Finanzas y Gestión',
    tags: ['Finanzas', 'Reducción de Costos', 'ROI', 'Presupuestos'],
    tier: 'ENTERPRISE',
  },

  'gestion financiera estrategica & optimizacion de costos': {
    coverUrl: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=800&q=80',
    description:
      'Aprende a auditar y reducir hasta un 40% en gastos de tecnología, servicios en la nube y licencias operativas, maximizando el margen de rentabilidad y retorno de inversión (ROI).',
    category: 'Finanzas y Gestión',
    tags: ['Finanzas', 'Reducción de Costos', 'ROI', 'Presupuestos'],
    tier: 'ENTERPRISE',
  },

  'finops empresarial & gobernanza cloud': {
    coverUrl: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=800&q=80',
    description:
      'Aprende a auditar y reducir hasta un 40% en gastos de tecnología, servicios en la nube y licencias operativas, maximizando el margen de rentabilidad y retorno de inversión (ROI).',
    category: 'Finanzas y Gestión',
    tags: ['Finanzas', 'Reducción de Costos', 'ROI', 'Presupuestos'],
    tier: 'ENTERPRISE',
  },

  'automatizacion de procesos con herramientas no-code': {
    coverUrl: 'https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop&w=800&q=80',
    description:
      'Automatiza flujos de ventas, cobranzas, reportes y atención al cliente conectando sistemas empresariales fácilmente sin contratar programadores ni escribir código.',
    category: 'Automatización y Procesos',
    tags: ['Automatización', 'No-Code', 'Productividad', 'Ventas'],
    tier: 'ESSENTIAL',
  },

  'arquitectura orientada a eventos con kafka': {
    coverUrl: 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=800&q=80',
    description:
      'Construcción de plataformas en tiempo real a escala masiva. Patrones EDA, Apache Kafka, Event Sourcing y transacciones distribuidas con el patrón Saga.',
    category: 'Arquitectura y Cloud',
    tags: ['Kafka', 'EDA', 'Microservicios', 'Streaming'],
    tier: 'ENTERPRISE',
  },

  'arquitectura orientada a eventos y microservicios resilientes': {
    coverUrl: 'https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop&w=800&q=80',
    description:
      'Automatiza flujos de ventas, cobranzas, reportes y atención al cliente conectando sistemas empresariales fácilmente sin contratar programadores ni escribir código.',
    category: 'Automatización y Procesos',
    tags: ['Automatización', 'No-Code', 'Productividad', 'Operaciones'],
    tier: 'ESSENTIAL',
  },

  'transformacion digital & liderazgo de negocios': {
    coverUrl: 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=800&q=80',
    description:
      'Cómo liderar la modernización de empresas tradicionales hacia modelos digitales rentables, gestionando el cambio organizacional y alineando equipos sin fricción.',
    category: 'Liderazgo y Estrategia',
    tags: ['Liderazgo', 'Transformación Digital', 'Gestión del Cambio', 'Estrategia'],
    tier: 'ADVANCED',
  },

  'metodologias agiles': {
    coverUrl: '/courses/curso4.png',
    description:
      'Implementa Scrum, Kanban y principios Lean para organizar equipos, optimizar procesos y entregar valor de manera continua en proyectos empresariales.',
    category: 'Gestión de Proyectos',
    tags: ['Scrum', 'Kanban', 'Lean', 'Agilidad', 'Proyectos'],
    tier: 'ESSENTIAL',
  },

  'liderazgo estrategico': {
    coverUrl: '/courses/curso3.png',
    description:
      'Desarrolla habilidades directivas para liderar equipos, negociar con eficacia, tomar decisiones de alto impacto y alinear la organización con los objetivos del negocio.',
    category: 'Liderazgo y Estrategia',
    tags: ['Liderazgo', 'Estrategia', 'Negociación', 'Decisiones'],
    tier: 'ADVANCED',
  },

  'direccion estrategica & negociacion para alta gerencia': {
    coverUrl: 'https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=800&q=80',
    description:
      'Desarrolla habilidades de comunicación con comités de directorio, negociación de contratos corporativos de alto valor y toma de decisiones ágiles en momentos clave.',
    category: 'Liderazgo y Estrategia',
    tags: ['Negociación', 'Alta Dirección', 'Comités', 'Decisiones'],
    tier: 'ESSENTIAL',
  },
};

const resolveTier = (
  level?: string,
): TierLevel => {
  const normalizedLevel = normalize(level || '');

  if (
    normalizedLevel.includes('enterprise') ||
    normalizedLevel.includes('empresarial')
  ) {
    return 'ENTERPRISE';
  }

  if (
    normalizedLevel.includes('advanced') ||
    normalizedLevel.includes('avanzado')
  ) {
    return 'ADVANCED';
  }

  return 'ESSENTIAL';
};

export const toCatalogCourse = (
  program: ProgramaResponse,
): Playbook => {
  const title = program.titulo || program.tituloPrograma || 'Programa sin título';
  const presentation =
    PRESENTATION_BY_TITLE[
      normalize(title)
    ];

  const category =
    presentation?.category ||
    program.tipoPrograma ||
    'Programa académico';

  const tags =
    presentation?.tags ||
    [
      program.tipoPrograma,
      program.level,
    ].filter(
      (value): value is string =>
        Boolean(value),
    );

  return {
    id: String(program.idPrograma),
    programId: program.idPrograma,
    title,
    price: program.precio,
    currency: program.moneda,
    coverUrl: program.imagenPortadaUrl || presentation?.coverUrl,
    slug: program.slug || slugify(title),

    description:
      program.descripcion ||
      presentation?.description ||
      program.descripcionCorta ||
      program.descripcionDetallada ||
      program.metodologia ||
      program.requisitos ||
      'Consulta el contenido y los requisitos de este programa académico.',

    category,

    tier:
      presentation?.tier ||
      resolveTier(program.nivel || program.level),

    tags,
    duration: program.duracionHoras
      ? `${program.duracionHoras} horas`
      : program.duracionPrograma,
    instructor: program.instructorNombre || program.nombreInstructor,
  };
};

export const buildCategories = (
  courses: Playbook[],
): Category[] => {
  const counts = courses.reduce<
    Record<string, number>
  >((accumulator, course) => {
    accumulator[course.category] =
      (accumulator[course.category] || 0) + 1;

    return accumulator;
  }, {});

  return [
    {
      id: 'cat-all',
      name: 'Todos los cursos',
      count: courses.length,
      iconName: 'Layers',
    },

    ...Object.entries(counts).map(
      ([name, count]) => ({
        id: `cat-${slugify(name)}`,
        name,
        count,
        iconName: 'BookOpen',
      }),
    ),
  ];
};

export const getCourseCover = (
  title: string,
): string | undefined =>
  PRESENTATION_BY_TITLE[
    normalize(title)
  ]?.coverUrl;

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
  'finops empresarial': {
    coverUrl: '/courses/curso1.png',

    description:
      'Domina la gestión financiera de infraestructuras cloud, optimizando costos, presupuestos y recursos mediante estrategias FinOps orientadas a la eficiencia y toma de decisiones empresariales.',

    category: 'Cloud y FinOps',
    tags: ['FinOps', 'Cloud', 'AWS', 'Azure'],
    tier: 'ENTERPRISE',
  },

  'ciberseguridad corporativa': {
    coverUrl: '/courses/curso2.png',

    description:
      'Aprende a proteger los activos digitales de una organización mediante gestión de riesgos, controles de seguridad, Zero Trust y estrategias de defensa frente a amenazas modernas.',

    category: 'Seguridad y Cumplimiento',

    tags: [
      'Zero Trust',
      'Riesgos',
      'Defensa',
      'Seguridad',
    ],

    tier: 'ADVANCED',
  },

  'liderazgo estrategico': {
    coverUrl: '/courses/curso3.png',

    description:
      'Desarrolla habilidades para liderar equipos, tomar decisiones de alto impacto, gestionar el cambio y alinear personas y recursos con los objetivos estratégicos de la organización.',

    category: 'Liderazgo y Gestión',

    tags: [
      'Liderazgo',
      'Estrategia',
      'Equipos',
      'Decisiones',
    ],

    tier: 'ADVANCED',
  },

  'metodologias agiles': {
    coverUrl: '/courses/curso4.png',

    description:
      'Implementa Scrum, Kanban y principios Lean para organizar equipos, optimizar procesos y entregar valor de manera continua en proyectos empresariales.',

    category: 'Gestión de Proyectos',

    tags: [
      'Scrum',
      'Kanban',
      'Lean',
      'Agilidad',
    ],

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
  const presentation =
    PRESENTATION_BY_TITLE[
      normalize(program.tituloPrograma)
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
    title: program.tituloPrograma,
    coverUrl: presentation?.coverUrl,
    slug: slugify(program.tituloPrograma),

    description:
      presentation?.description ||
      program.metodologia ||
      program.requisitos ||
      'Consulta el contenido y los requisitos de este programa académico.',

    category,

    tier:
      presentation?.tier ||
      resolveTier(program.level),

    tags,
    duration: program.duracionPrograma,
    instructor: program.nombreInstructor,
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
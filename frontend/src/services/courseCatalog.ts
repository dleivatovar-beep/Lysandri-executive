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

export const FALLBACK_PLAYBOOKS: Playbook[] = [
  {
    id: '1',
    programId: 1,
    title: 'FinOps Empresarial & Gobernanza Multi-Cloud',
    coverUrl: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=800&q=80',
    slug: 'finops-empresarial',
    description: 'Domina la asignación de costos multi-cloud, optimización de instancias reservadas y gobernanza financiera para reducir hasta 40% de facturación en AWS y Azure.',
    category: 'Cloud y FinOps',
    tier: 'ENTERPRISE',
    tags: ['FinOps', 'AWS', 'Azure', 'Kubernetes'],
    duration: '8 Semanas',
    instructor: 'Ing. Roberto Valenzuela (Principal Cloud Economist)',
  },
  {
    id: '2',
    programId: 2,
    title: 'Ciberseguridad Corporativa & Estrategia Zero-Trust',
    coverUrl: 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=800&q=80',
    slug: 'ciberseguridad-corporativa',
    description: 'Protección de infraestructura crítica, respuesta ante incidentes de ransomware, gobernanza NIS2 e implementación práctica de arquitectura Zero-Trust.',
    category: 'Seguridad y Cumplimiento',
    tier: 'ADVANCED',
    tags: ['Zero-Trust', 'Ransomware', 'ISO 27001', 'SOC'],
    duration: '10 Semanas',
    instructor: 'Dra. Elena Alarcón (Ex-CISO Regional)',
  },
  {
    id: '3',
    programId: 3,
    title: 'Inteligencia Artificial Generativa para C-Suite',
    coverUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80',
    slug: 'ia-para-ejecutivos',
    description: 'Estrategia y adopción directiva de Modelos Fundacionales, gobernanza ética, mitigación de riesgos legales y ROI tangible de asistentes cognitivos.',
    category: 'Inteligencia Artificial',
    tier: 'ENTERPRISE',
    tags: ['LLMs', 'RAG', 'Gobernanza IA', 'ROI'],
    duration: '6 Semanas',
    instructor: 'Dr. Marcos Sotomayor (Consultor Fortune 500)',
  },
  {
    id: '4',
    programId: 4,
    title: 'Arquitectura Orientada a Eventos con Apache Kafka',
    coverUrl: 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=800&q=80',
    slug: 'arquitectura-eventos-kafka',
    description: 'Diseño de sistemas distribuidos de ultra alto rendimiento, Event Sourcing, transacciones con patrón Saga y resiliencia empresarial con Apache Kafka.',
    category: 'Arquitectura de Software',
    tier: 'ADVANCED',
    tags: ['Kafka', 'Event-Driven', 'Microservicios', 'Saga'],
    duration: '8 Semanas',
    instructor: 'Ing. David Poma (Lead Solutions Architect)',
  },
  {
    id: '5',
    programId: 5,
    title: 'Estrategia de Plataformas Cloud & EKS Enterprise',
    coverUrl: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=800&q=80',
    slug: 'estrategia-cloud-eks',
    description: 'Modernización de aplicaciones monolíticas a clústeres elásticos en Amazon EKS, service mesh con Istio y observabilidad OpenTelemetry.',
    category: 'Cloud y FinOps',
    tier: 'ENTERPRISE',
    tags: ['Kubernetes', 'EKS', 'DevOps', 'Istio'],
    duration: '10 Semanas',
    instructor: 'Ing. Roberto Valenzuela',
  },
  {
    id: '6',
    programId: 6,
    title: 'Liderazgo Estratégico para Directores de TI',
    coverUrl: 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=800&q=80',
    slug: 'liderazgo-estrategico',
    description: 'Gestión del cambio tecnológico, comunicación con juntas directivas, atracción de talento de alta ingeniería y alineación de TI con el negocio.',
    category: 'Liderazgo y Gestión',
    tier: 'ESSENTIAL',
    tags: ['C-Level', 'Negociación', 'Gestión de Talento'],
    duration: '6 Semanas',
    instructor: 'MSc. Patricia Arana (Executive Coach)',
  }
];
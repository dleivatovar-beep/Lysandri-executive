import {
  Category,
  ChatMessage,
  Playbook,
  UserProfile,
  UserRole,
} from '../types';

export const MOCK_USERS: Record<UserRole, UserProfile> = {
  ESTUDIANTE: {
    id: 'user-executive-001',
    name: 'Alexander Vance',
    role: 'ESTUDIANTE',
    jobTitle: 'Ejecutivo en formación',
    company: 'Lysandri Global Tech',
    avatarUrl:
      'https://images.unsplash.com/photo-1560250097-0b93528c311a?auto=format&fit=crop&w=160&q=80',
  },

  INSTRUCTOR: {
    id: 'user-instructor-001',
    name: 'Valeria Torres',
    role: 'INSTRUCTOR',
    jobTitle: 'Profesora corporativa',
    company: 'Lysandri Global Tech',
    avatarUrl:
      'https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=160&q=80',
  },

  ADMIN: {
    id: 'user-admin-001',
    name: 'Daniela Ruiz',
    role: 'ADMIN',
    jobTitle: 'Administradora de plataforma',
    company: 'Lysandri Global Tech',
    avatarUrl:
      'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=160&q=80',
  },
};

export const INITIAL_USER: UserProfile = MOCK_USERS.ESTUDIANTE;

export const MOCK_PLAYBOOKS: Playbook[] = [
  {
    id: 'course-001',
    title: 'FinOps Empresarial',
    coverUrl: '/courses/curso1.png',
    slug: 'finops-empresarial',
    description:
      'Domina la gestión financiera de infraestructuras cloud, optimizando costos, presupuestos y recursos mediante estrategias FinOps orientadas a la eficiencia y toma de decisiones empresariales.',
    category: 'Cloud y FinOps',
    price: 249,
    tier: 'ENTERPRISE',
    rating: 4.95,
    downloadsCount: 1420,
    tags: [
      'FinOps',
      'Cloud',
      'AWS',
      'Azure',
      'Optimización de Costos',
    ],
  },
  {
    id: 'course-002',
    title: 'Ciberseguridad Corporativa',
    coverUrl: '/courses/curso2.png',
    slug: 'ciberseguridad-corporativa',
    description:
      'Aprende a proteger los activos digitales de una organización mediante gestión de riesgos, controles de seguridad, Zero Trust y estrategias de defensa frente a amenazas modernas.',
    category: 'Seguridad y Cumplimiento',
    price: 199,
    tier: 'ADVANCED',
    rating: 4.91,
    downloadsCount: 1850,
    tags: [
      'Zero Trust',
      'Gestión de Riesgos',
      'Defensa',
      'Seguridad',
      'Compliance',
    ],
  },
  {
    id: 'course-003',
    title: 'Liderazgo Estratégico',
    coverUrl: '/courses/curso3.png',
    slug: 'liderazgo-estrategico',
    description:
      'Desarrolla habilidades para liderar equipos, tomar decisiones de alto impacto, gestionar el cambio y alinear personas y recursos con los objetivos estratégicos de la organización.',
    category: 'Liderazgo y Gestión',
    price: 149,
    tier: 'ADVANCED',
    rating: 4.89,
    downloadsCount: 1260,
    tags: [
      'Liderazgo',
      'Estrategia',
      'Gestión del Cambio',
      'Equipos',
      'Decisiones',
    ],
  },
  {
    id: 'course-004',
    title: 'Metodologías Ágiles',
    coverUrl: '/courses/curso4.png',
    slug: 'metodologias-agiles',
    description:
      'Implementa Scrum, Kanban y principios Lean para organizar equipos, optimizar procesos y entregar valor de manera continua en proyectos empresariales.',
    category: 'Gestión de Proyectos',
    price: 119,
    tier: 'ESSENTIAL',
    rating: 4.84,
    downloadsCount: 2410,
    tags: ['Scrum', 'Kanban', 'Lean', 'Agilidad', 'Proyectos'],
  },
];

export const INITIAL_PLAYBOOKS: Playbook[] = MOCK_PLAYBOOKS;

export const MOCK_CATEGORIES: Category[] = [
  {
    id: 'cat-all',
    name: 'Todos los cursos',
    count: MOCK_PLAYBOOKS.length,
    iconName: 'Layers',
  },
  {
    id: 'cat-cloud',
    name: 'Cloud y FinOps',
    count: 1,
    iconName: 'TrendingUp',
  },
  {
    id: 'cat-security',
    name: 'Seguridad y Cumplimiento',
    count: 1,
    iconName: 'ShieldCheck',
  },
  {
    id: 'cat-leadership',
    name: 'Liderazgo y Gestión',
    count: 1,
    iconName: 'Cpu',
  },
  {
    id: 'cat-projects',
    name: 'Gestión de Proyectos',
    count: 1,
    iconName: 'Bot',
  },
];

export const INITIAL_CATEGORIES: Category[] = MOCK_CATEGORIES;

export const MOCK_CHAT_MESSAGES: ChatMessage[] = [
  {
    id: 'message-001',
    sessionId: 'session-executive-01',
    sender: 'ASSISTANT',
    content:
      'Hola. Soy el asistente académico de Lysandri. Puedo ayudarte a encontrar cursos, resolver preguntas sobre contenidos y organizar tu aprendizaje.',
    timestamp: '11:30',
    sources: [],
  },
  {
    id: 'message-002',
    sessionId: 'session-executive-01',
    sender: 'USER',
    content:
      '¿Qué curso me recomiendas para mejorar la gestión de proyectos?',
    timestamp: '11:31',
  },
  {
    id: 'message-003',
    sessionId: 'session-executive-01',
    sender: 'ASSISTANT',
    content:
      'Te recomiendo **Metodologías Ágiles**. Incluye Scrum, Kanban y principios Lean aplicados a proyectos empresariales.',
    timestamp: '11:31',
    sources: [
      'Programa_Metodologias_Agiles.pdf',
      'Guia_Scrum_Kanban_Lean.pdf',
    ],
  },
];

export const INITIAL_CHAT_MESSAGES: ChatMessage[] =
  MOCK_CHAT_MESSAGES;
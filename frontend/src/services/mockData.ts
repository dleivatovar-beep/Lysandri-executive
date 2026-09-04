// src/services/mockData.ts
import { Playbook, Category, ChatMessage, UserProfile } from '../types';

export const INITIAL_USER: UserProfile = {
  name: 'Alexander Vance',
  role: 'Director de Tecnología (CTO)',
  company: 'Lysandri Global Tech',
  avatarUrl:
    'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=200',
};

export const MOCK_CATEGORIES: Category[] = [
  {
    id: 'cat-all',
    name: 'Todos los cursos',
    iconName: 'Layers',
    count: 4,
  },
];

export const MOCK_PLAYBOOKS: Playbook[] = [
  {
    id: 'pb-001',
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
    tags: ['FinOps', 'Cloud', 'AWS', 'Azure', 'Optimización de Costos'],
  },
  {
    id: 'pb-002',
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
    id: 'pb-003',
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
    id: 'pb-004',
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

export const MOCK_CHAT_MESSAGES: ChatMessage[] = [
  {
    id: 'msg-001',
    sessionId: 'session-executive-01',
    sender: 'USER',
    content:
      '¿Cuál es el retorno estimado de inversión (ROI) y los riesgos de migrar nuestro monolito legacy a una arquitectura orientada a eventos con Kafka?',
    timestamp: '10:14 AM',
  },
  {
    id: 'msg-002',
    sessionId: 'session-executive-01',
    sender: 'ASSISTANT',
    content: `Basado en el análisis de nuestros activos arquitectónicos y reportes de FinOps 2026, los hallazgos ejecutivos son los siguientes:

### 1. Estimación de ROI (Horizonte a 24 meses)
* **Reducción de Latencia Operativa:** Hasta un **45%** en el procesamiento de transacciones masivas.
* **Eficiencia de Costos de Cómputo:** Disminución del **28%** en uso de infraestructura serverless al eliminar polling innecesario.
* **Tiempo de Time-to-Market:** Despliegues independientes de microservicios aceleran el release cycle en un **3.5x**.

### 2. Matriz de Riesgos y Mitigación
* **Consistencia Eventual:** Riesgo alto en transacciones bancarias/financieras. *Mitigación:* Implementar el patrón **Saga Orchestrator** detallado en la *Suite de Microservicios Orientados a Eventos*.
* **Monitoreo & Trazabilidad:** Dificultad para rastrear transacciones distribuidas. *Mitigación:* OpenTelemetry y Distributed Tracing obligatorio desde el día 1.`,
    timestamp: '10:15 AM',
    sources: [
      'Reporte_ROI_Arquitectura_Eventos_2026.pdf',
      'Benchmark_FinOps_Q2_Infraestructura_Nube.xlsx',
      'Radar_Tecnologico_Lysandri_Microservicios.pdf',
    ],
  },
  {
    id: 'msg-003',
    sessionId: 'session-executive-01',
    sender: 'USER',
    content:
      'Excelente. ¿Tenemos un Playbook en la plataforma que cubra el patrón Saga y la infraestructura de resiliencia lista para desplegar?',
    timestamp: '10:17 AM',
  },
  {
    id: 'msg-004',
    sessionId: 'session-executive-01',
    sender: 'ASSISTANT',
    content:
      "Sí, CTO Vance. El playbook **'Suite de Microservicios Orientados a Eventos y Framework de Resiliencia Patrón Saga'** (Nivel ADVANCED) incluye el código IaC en Terraform, manifiestos de Kubernetes, interceptores de Kafka y la implementación completa del patrón Saga en TypeScript/Go.",
    timestamp: '10:17 AM',
    sources: [
      'Catalogo_Maestro_Playbooks_2026.json',
      'Especificaciones_Implementacion_Patron_Saga.md',
    ],
  },
];
// src/services/mockData.ts
import { Playbook, Category, ChatMessage, UserProfile } from '../types';

export const INITIAL_USER: UserProfile = {
  name: "Alexander Vance",
  role: "Chief Technology Officer",
  company: "Lysandri Global Tech",
  avatarUrl: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=200"
};

export const MOCK_CATEGORIES: Category[] = [
  {
    id: "cat-all",
    name: "Todos los Activos",
    iconName: "Layers",
    count: 4
  },
  {
    id: "cat-finops",
    name: "Cloud & FinOps",
    iconName: "TrendingUp",
    count: 1
  },
  {
    id: "cat-arch",
    name: "Software Architecture",
    iconName: "Cpu",
    count: 1
  },
  {
    id: "cat-ai",
    name: "AI & LLM Infra",
    iconName: "Bot",
    count: 1
  },
  {
    id: "cat-sec",
    name: "Security & Compliance",
    iconName: "ShieldCheck",
    count: 1
  }
];

export const MOCK_PLAYBOOKS: Playbook[] = [
  {
    id: "pb-001",
    title: "FinOps Cloud Cost Optimization & Executive Governance Blueprint",
    slug: "finops-cloud-cost-optimization-blueprint",
    description: "Marco integral para reducción de costos en la nube (AWS/Azure/GCP) hasta un 38%. Incluye modelos de etiquetado financiero, presupuestos con anomalías por IA y plantillas Terraform para auto-scaling inteligente.",
    category: "Cloud & FinOps",
    price: 1499,
    tier: "ENTERPRISE",
    rating: 4.95,
    downloadsCount: 1420,
    tags: ["FinOps", "AWS", "Terraform", "Cloud Governance", "Cost Allocation"]
  },
  {
    id: "pb-002",
    title: "Event-Driven Microservices Suite & Saga Pattern Resilience Framework",
    slug: "event-driven-microservices-suite",
    description: "Arquitectura de referencia para sistemas distribuídos de alta disponibilidad. Implementa Apache Kafka, CQRS, patrones Saga para transacciones distribuidas y Circuit Breaker automático.",
    category: "Software Architecture",
    price: 899,
    tier: "ADVANCED",
    rating: 4.88,
    downloadsCount: 2150,
    tags: ["Microservices", "Kafka", "CQRS", "Saga Pattern", "Kubernetes"]
  },
  {
    id: "pb-003",
    title: "Enterprise AI Gateway & Orchestration Mesh Playbook",
    slug: "enterprise-ai-gateway-mesh",
    description: "Infraestructura lista para producción de pasarela RAG y LLMs en la empresa. Control de cuotas multi-modelo, caching semántico vectorial (Redis/Qdrant) y guardrails de seguridad de datos.",
    category: "AI & LLM Infra",
    price: 1999,
    tier: "ENTERPRISE",
    rating: 4.98,
    downloadsCount: 980,
    tags: ["RAG", "LLM Gateway", "Semantic Cache", "Vector DB", "Security"]
  },
  {
    id: "pb-004",
    title: "Zero-Trust Cloud Native Security & OPA Policy Automation Kit",
    slug: "zero-trust-cloud-native-security",
    description: "Guía ejecutable para implementar políticas de seguridad Zero-Trust en clústeres EKS/GKE con Open Policy Agent (OPA), Istio Service Mesh e inspección dinámica de vulnerabilidades en CI/CD.",
    category: "Security & Compliance",
    price: 499,
    tier: "ESSENTIAL",
    rating: 4.79,
    downloadsCount: 3120,
    tags: ["Zero-Trust", "Kubernetes", "OPA", "Istio", "DevSecOps"]
  }
];

export const MOCK_CHAT_MESSAGES: ChatMessage[] = [
  {
    id: "msg-001",
    sessionId: "session-executive-01",
    sender: "USER",
    content: "¿Cuál es el retorno estimado de inversión (ROI) y los riesgos de migrar nuestro monolito legacy a una arquitectura orientada a eventos con Kafka?",
    timestamp: "10:14 AM"
  },
  {
    id: "msg-002",
    sessionId: "session-executive-01",
    sender: "ASSISTANT",
    content: `Basado en el análisis de nuestros activos arquitectónicos y reportes de FinOps 2026, los hallazgos ejecutivos son los siguientes:

### 1. Estimación de ROI (Horizonte a 24 meses)
* **Reducción de Latencia Operativa:** Hasta un **45%** en el procesamiento de transacciones masivas.
* **Eficiencia de Costos de Cómputo:** Disminución del **28%** en uso de infraestructura serverless al eliminar polling innecesario.
* **Tiempo de Time-to-Market:** Despliegues independientes de microservicios aceleran el release cycle en un **3.5x**.

### 2. Matriz de Riesgos y Mitigación
* **Consistencia Eventual:** Riesgo alto en transacciones bancarias/financieras. *Mitigación:* Implementar el patrón **Saga Orchestrator** detallado en el *Event-Driven Microservices Suite*.
* **Monitoreo & Trazabilidad:** Dificultad para rastrear transacciones distribuidas. *Mitigación:* OpenTelemetry y Distributed Tracing obligatorio desde el día 1.`,
    timestamp: "10:15 AM",
    sources: [
      "Event-Driven_Architecture_ROI_Report_2026.pdf",
      "FinOps_Benchmark_Q2_Cloud_Infrastructure.xlsx",
      "Lysandri_Tech_Radar_Microservices.pdf"
    ]
  },
  {
    id: "msg-003",
    sessionId: "session-executive-01",
    sender: "USER",
    content: "Excelente. ¿Tenemos un Playbook en la plataforma que cubra el patrón Saga y la infraestructura de resiliencia lista para desplegar?",
    timestamp: "10:17 AM"
  },
  {
    id: "msg-004",
    sessionId: "session-executive-01",
    sender: "ASSISTANT",
    content: "Sí, CTO Vance. El playbook **'Event-Driven Microservices Suite & Saga Pattern Resilience Framework'** (Nivel ADVANCED) incluye el código IaC en Terraform, manifiestos de Kubernetes, interceptores de Kafka y la implementación completa del patrón Saga en TypeScript/Go.",
    timestamp: "10:17 AM",
    sources: [
      "Playbook_Catalog_Master_2026.json",
      "Saga_Pattern_Implementation_Specs.md"
    ]
  }
];

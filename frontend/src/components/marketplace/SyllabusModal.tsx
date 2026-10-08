import React from 'react';
import { X, BookOpen, Bot, CheckCircle2, Clock, Award, FileText, ArrowRight } from 'lucide-react';
import { Playbook } from '../../types';

interface SyllabusModalProps {
  isOpen: boolean;
  onClose: () => void;
  playbook: Playbook | null;
  onAddToCart?: (playbook: Playbook) => void;
  onSelect?: (playbook: Playbook) => void;
  onAskAi: (playbook: Playbook) => void;
}

export const SyllabusModal: React.FC<SyllabusModalProps> = ({
  isOpen,
  onClose,
  playbook,
  onAddToCart,
  onSelect,
  onAskAi,
}) => {
  if (!isOpen || !playbook) return null;

  const normalizedTitle = playbook.title.toLowerCase();

  let modules = playbook.syllabus && playbook.syllabus.length > 0 ? playbook.syllabus : [];

  if (modules.length === 0) {
    modules = [
      {
        titulo: 'Módulo 1: Diagnóstico Estratégico y Alineamiento Empresarial',
        duracion: '2 Semanas',
        temas: [
          'Definición de objetivos clave de negocio y metas estratégicas (OKRs / KPIs)',
          'Evaluación de oportunidades de modernización operativa en la organización',
          'Liderazgo directivo y gestión del cambio en equipos multidisciplinarios',
        ],
      },
      {
        titulo: 'Módulo 2: Herramientas Prácticas y Metodologías de Gestión Directiva',
        duracion: '2 Semanas',
        temas: [
          'Herramientas ejecutivas para optimizar la productividad y toma de decisiones',
          'Negociación efectiva de contratos comerciales y relación con aliados clave',
          'Medición continua del desempeño y reducción de ineficiencias operativas',
        ],
      },
      {
        titulo: 'Módulo 3: Plan Integral de Negocio y Sustentación Directiva',
        duracion: '2 Semanas',
        temas: [
          'Elaboración del plan de modernización de tu área o empresa a 90 y 180 días',
          'Análisis de viabilidad financiera, presupuestos y retorno de inversión (ROI)',
          'Presentación ejecutiva y defensa del plan ante la gerencia general o directorio',
        ],
      },
    ];

    if (normalizedTitle.includes('finops') || normalizedTitle.includes('financiera') || normalizedTitle.includes('costos')) {
    modules = [
      {
        titulo: 'Módulo 1: Auditoría Financiera de Gastos en Tecnología y Servicios',
        duracion: '2 Semanas',
        temas: [
          'Identificación de suscripciones duplicadas, licencias no utilizadas y cobros ocultos',
          'Análisis transparente de gastos en la nube y plataformas digitales para gerentes',
          'Mapeo de costos directos por cliente, producto y canal de venta',
        ],
      },
      {
        titulo: 'Módulo 2: Estrategias de Negociación y Reducción Inmediata de Costos',
        duracion: '3 Semanas',
        temas: [
          'Renegociación de acuerdos con proveedores de tecnología y telecomunicaciones',
          'Estrategias para reducir hasta un 40% en facturación operativa sin perder calidad',
          'Alertas automáticas de control de presupuesto para evitar sobrecostos a fin de mes',
        ],
      },
      {
        titulo: 'Módulo 3: Cuadros de Mando Financieros y Presupuestos Dinámicos',
        duracion: '3 Semanas',
        temas: [
          'Tableros ejecutivos visuales para monitorear costos operativos en tiempo real',
          'Presupuestos dinámicos basados en la rentabilidad real de la empresa',
          'Sustentación del plan de optimización de costos ante el directorio ejecutivo',
        ],
      },
    ];
  } else if (normalizedTitle.includes('ciberseguridad') || normalizedTitle.includes('seguridad') || normalizedTitle.includes('antifraude')) {
    modules = [
      {
        titulo: 'Módulo 1: Diagnóstico de Riesgos y Blindaje Empresarial',
        duracion: '2 Semanas',
        temas: [
          'Mapa de vulnerabilidades críticas: correos, accesos bancarios y equipos corporativos',
          'Políticas de contraseñas seguras, verificación en dos pasos y control de accesos',
          'Cumplimiento con la Ley de Protección de Datos Personales y resguardo de clientes',
        ],
      },
      {
        titulo: 'Módulo 2: Prevención de Estafas Digitales, Phishing y Ransomware',
        duracion: '3 Semanas',
        temas: [
          'Detección y neutralización de estafas por correo y suplantación de identidad',
          'Protocolos de protección ante ataques de secuestro de información (ransomware)',
          'Copias de seguridad automáticas y plan de contingencia para no frenar la empresa',
        ],
      },
      {
        titulo: 'Módulo 3: Gestión de Crisis, Aspectos Legales y Continuidad del Negocio',
        duracion: '3 Semanas',
        temas: [
          'Guía de respuesta rápida durante las primeras 24 horas ante una incidencia',
          'Responsabilidad legal de la gerencia y comunicación estratégica con clientes',
          'Auditoría preventiva y presentación del reporte de seguridad ante el directorio',
        ],
      },
    ];
  } else if (normalizedTitle.includes('ia') || normalizedTitle.includes('inteligencia artificial')) {
    modules = [
      {
        titulo: 'Módulo 1: Oportunidades y Fundamentos de la IA para Negocios (Sin Código)',
        duracion: '2 Semanas',
        temas: [
          'Qué es la IA generativa y cómo aplicarla en ventas, operaciones y gestión sin programar',
          'Dominio de herramientas líderes: ChatGPT, Claude, Copilot y Gemini para líderes',
          'Políticas de privacidad y protección de secretos comerciales al utilizar IA',
        ],
      },
      {
        titulo: 'Módulo 2: Asistentes Inteligentes y Automatización de Clientes',
        duracion: '2 Semanas',
        temas: [
          'Creación de asistentes virtuales para atención de ventas y soporte 24/7 sin programar',
          'Conexión de IA con documentos de tu empresa (catálogos, manuales y contratos)',
          'Casos reales de empresas que aumentaron sus ventas y productividad con IA',
        ],
      },
      {
        titulo: 'Módulo 3: Estrategia, Métricas de Ahorro y Retorno de Inversión (ROI)',
        duracion: '2 Semanas',
        temas: [
          'Cálculo exacto de horas de trabajo ahorradas y retorno de inversión de la IA',
          'Gestión del cambio: cómo capacitar a tu equipo y vencer la resistencia a la tecnología',
          'Presentación del plan de implementación de IA ante la junta de accionistas o socios',
        ],
      },
    ];
  } else if (normalizedTitle.includes('automatizacion') || normalizedTitle.includes('no-code') || normalizedTitle.includes('procesos') || normalizedTitle.includes('eventos') || normalizedTitle.includes('kafka')) {
    modules = [
      {
        titulo: 'Módulo 1: Mapeo de Procesos y Detección de Cuellos de Botella',
        duracion: '2 Semanas',
        temas: [
          'Identificación de tareas repetitivas que consumen tiempo valioso en tu empresa',
          'Diagramación sencilla de flujos de ventas, facturación y atención a clientes',
          'Selección de herramientas No-Code visuales (Make, Zapier, Notion, Airtable)',
        ],
      },
      {
        titulo: 'Módulo 2: Automatización de Ventas, Facturación y Cobranzas sin Código',
        duracion: '2 Semanas',
        temas: [
          'Automatización de correos de bienvenida, presupuestos y seguimiento comercial',
          'Conexión automática entre formularios web, WhatsApp y hojas de cálculo (Excel / Sheets)',
          'Automatización de recordatorios de cobro a clientes para mejorar el flujo de caja',
        ],
      },
      {
        titulo: 'Módulo 3: Integración de Sistemas y Escalabilidad del Negocio',
        duracion: '2 Semanas',
        temas: [
          'Conexión entre sistemas de cobranza, comprobantes de pago y entrega de servicios',
          'Medición del ahorro mensual en horas/hombre y reducción del margen de error',
          'Cómo escalar tus operaciones para atender el triple de clientes con el mismo equipo',
        ],
      },
    ];
  }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-950/80 backdrop-blur-md transition-opacity"
        onClick={onClose}
      />

      <div className="relative z-10 w-full max-w-3xl overflow-hidden rounded-2xl border border-cyan-500/30 bg-slate-900 text-slate-100 shadow-2xl">
        {/* Header */}
        <div className="border-b border-slate-800 bg-slate-950/80 p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 mb-2">
                <span className="rounded bg-cyan-950/80 border border-cyan-800/60 px-2.5 py-0.5 font-mono text-[10px] font-bold text-cyan-400">
                  {playbook.tier}
                </span>
                <span className="text-xs text-slate-400 font-medium">
                  {playbook.category}
                </span>
              </div>
              <h2 className="text-xl font-bold text-white leading-tight">
                {playbook.title}
              </h2>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg p-2 text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="max-h-[60vh] overflow-y-auto p-6 space-y-6">
          {/* Metadata Cards */}
          <div className="grid grid-cols-3 gap-3">
            <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-3 text-center">
              <Clock className="h-4 w-4 text-cyan-400 mx-auto mb-1" />
              <div className="text-[10px] text-slate-400">Dedicación Estimada</div>
              <div className="text-xs font-bold text-slate-200">{playbook.duration || '8 Semanas'}</div>
            </div>
            <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-3 text-center">
              <Award className="h-4 w-4 text-indigo-400 mx-auto mb-1" />
              <div className="text-[10px] text-slate-400">Certificación</div>
              <div className="text-xs font-bold text-slate-200">Ejecutiva Yunix</div>
            </div>
            <div className="rounded-xl border border-slate-800 bg-slate-950/50 p-3 text-center">
              <BookOpen className="h-4 w-4 text-emerald-400 mx-auto mb-1" />
              <div className="text-[10px] text-slate-400">Entorno de Aprendizaje</div>
              <div className="text-xs font-bold text-slate-200">Campus Moodle LMS</div>
            </div>
          </div>

          {/* Description */}
          <div>
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-2">
              Resumen del Programa
            </h3>
            <p className="text-xs text-slate-300 leading-relaxed">
              {playbook.description}
            </p>
          </div>

          {/* Syllabus Topics */}
          <div>
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-3 flex items-center gap-2">
              <FileText className="h-4 w-4 text-cyan-400" />
              Estructura Curricular y Módulos
            </h3>
            <div className="space-y-3">
              {modules.map((modulo, idx) => (
                <div
                  key={idx}
                  className="rounded-xl border border-slate-800 bg-slate-950/60 p-4 space-y-2"
                >
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-semibold text-white">
                      {modulo.titulo}
                    </h4>
                    <span className="text-[10px] font-mono text-cyan-400 bg-cyan-950/60 border border-cyan-800/40 px-2 py-0.5 rounded">
                      {modulo.duracion}
                    </span>
                  </div>
                  <ul className="space-y-1.5 pt-1">
                    {modulo.temas.map((tema, tIdx) => (
                      <li key={tIdx} className="flex items-start gap-2 text-xs text-slate-300">
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400 shrink-0 mt-0.5" />
                        <span>{tema}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-between border-t border-slate-800 bg-slate-950/90 p-5">
          <button
            type="button"
            onClick={() => {
              onAskAi(playbook);
              onClose();
            }}
            className="group/iaModal relative overflow-hidden flex items-center gap-2 rounded-xl border border-cyan-500/40 bg-cyan-950/50 px-4 py-2.5 text-xs font-semibold text-cyan-300 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:bg-cyan-900/60 hover:border-cyan-400 hover:text-cyan-200 hover:shadow-md hover:shadow-cyan-400/20 active:scale-95"
          >
            <div className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-cyan-300/15 to-transparent transition-transform duration-700 ease-out group-hover/iaModal:translate-x-full" />
            <div className="relative">
              <Bot className="h-4 w-4 transition-transform duration-300 group-hover/iaModal:scale-125 group-hover/iaModal:rotate-12" />
              <span className="absolute -top-0.5 -right-0.5 flex h-1.5 w-1.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-cyan-400" />
              </span>
            </div>
            <span className="relative z-10">Consultar al Asistente IA sobre esta Malla</span>
          </button>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 text-xs text-slate-400 hover:text-white transition-colors active:scale-95"
            >
              Cerrar
            </button>
            <button
              type="button"
              onClick={() => {
                if (onSelect) {
                  onSelect(playbook);
                } else if (onAddToCart) {
                  onAddToCart(playbook);
                }
                onClose();
              }}
              className="group/insModal relative overflow-hidden flex items-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-cyan-500 bg-[length:200%_auto] px-5 py-2.5 text-xs font-bold text-white shadow-md shadow-cyan-500/20 transition-all duration-500 hover:bg-right hover:-translate-y-0.5 hover:shadow-lg hover:shadow-cyan-500/35 active:scale-95"
            >
              <div className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/25 to-transparent transition-transform duration-1000 ease-in-out group-hover/insModal:translate-x-full" />
              <span className="relative z-10 tracking-wide font-extrabold">Inscribir</span>
              <ArrowRight className="relative z-10 h-3.5 w-3.5 transition-transform duration-300 ease-out group-hover/insModal:translate-x-1" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

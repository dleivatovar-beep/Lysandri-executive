import React from 'react';
import { X, BookOpen, Bot, ShoppingCart, CheckCircle2, Clock, Award, FileText } from 'lucide-react';
import { Playbook } from '../../types';

interface SyllabusModalProps {
  isOpen: boolean;
  onClose: () => void;
  playbook: Playbook | null;
  onAddToCart: (playbook: Playbook) => void;
  onAskAi: (playbook: Playbook) => void;
}

export const SyllabusModal: React.FC<SyllabusModalProps> = ({
  isOpen,
  onClose,
  playbook,
  onAddToCart,
  onAskAi,
}) => {
  if (!isOpen || !playbook) return null;

  const sampleModules = [
    {
      titulo: 'Módulo 1: Fundamentos y Alineamiento Estratégico',
      duracion: '2 Semanas',
      temas: [
        'Diagnóstico del estado actual y gobierno corporativo',
        'Modelos de madurez y benchmarks internacionales',
        'Estrategias de adopción y mitigación de resistencia organizacional',
      ],
    },
    {
      titulo: 'Módulo 2: Arquitectura y Prácticas Técnicas Avanzadas',
      duracion: '3 Semanas',
      temas: [
        'Frameworks de diseño y patrones de escalabilidad',
        'Simulaciones de escenarios de crisis y recuperación ante desastres',
        'Métricas de observabilidad y KPIs de alto impacto',
      ],
    },
    {
      titulo: 'Módulo 3: Proyecto Aplicativo In-Company & Desafío Final',
      duracion: '3 Semanas',
      temas: [
        'Defensa del plan de optimización ante comité directivo ficticio',
        'Evaluación de ROI y plan de implementación a 90 días',
        'Validación de estándares en el Campus Virtual Moodle',
      ],
    },
  ];

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
              {sampleModules.map((modulo, idx) => (
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
            className="flex items-center gap-2 rounded-xl border border-cyan-500/40 bg-cyan-950/50 px-4 py-2.5 text-xs font-semibold text-cyan-300 hover:bg-cyan-900/60 transition-all"
          >
            <Bot className="h-4 w-4" />
            <span>Consultar al Asistente IA sobre esta Malla</span>
          </button>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 text-xs text-slate-400 hover:text-white transition-colors"
            >
              Cerrar
            </button>
            <button
              type="button"
              onClick={() => {
                onAddToCart(playbook);
                onClose();
              }}
              className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-5 py-2.5 text-xs font-bold text-white shadow-md shadow-cyan-500/20 hover:opacity-95 transition-all"
            >
              <ShoppingCart className="h-4 w-4" />
              <span>Añadir al Carrito</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

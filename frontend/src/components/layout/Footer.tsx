// src/components/layout/Footer.tsx
import React from 'react';
import {
  ShieldCheck,
  Building2,
  GraduationCap,
  Cpu,
  Lock,
  Mail,
  ChevronRight,
  Globe2,
} from 'lucide-react';
import companyLogo from '../../assets/lysandri-logo.png';

export const Footer: React.FC = () => {
  return (
    <footer className="mt-20 border-t border-cyan-500/15 bg-gradient-to-b from-slate-900/95 via-[#080d1a] to-[#04060a] text-slate-400">
      {/* Top Value Strip */}
      <div className="border-b border-cyan-500/10 bg-cyan-950/20 py-4 px-4 sm:px-6 lg:px-8">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 text-xs">
          <div className="flex items-center gap-3">
            <span className="flex h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
            <span className="font-mono text-[11px] font-semibold text-slate-300">
              CAMPUS VIRTUAL ACTIVO • MATRÍCULAS ABIERTAS PARA CONVOCATORIA 2026-II
            </span>
          </div>

          <div className="flex items-center gap-4 text-[11px] text-slate-400">
            <span className="flex items-center gap-1.5">
              <Lock className="h-3.5 w-3.5 text-cyan-400" />
              Transacciones Encriptadas SSL 256-Bit vía Stripe
            </span>
            <span className="hidden sm:inline text-slate-700">•</span>
            <span className="hidden sm:flex items-center gap-1.5">
              <Globe2 className="h-3.5 w-3.5 text-indigo-400" />
              Sede Principal: Lima, Perú / Yunix Ingenieros E.I.R.L.
            </span>
          </div>
        </div>
      </div>

      {/* Main Footer Grid */}
      <div className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-10 sm:grid-cols-2 lg:grid-cols-4">
          {/* Columna 1: Identidad Corporativa */}
          <div className="space-y-4">
            <div className="flex items-center gap-3">
              <img
                src={companyLogo}
                alt="Logo Lysandri"
                className="h-9 w-9 object-contain drop-shadow-[0_0_8px_rgba(34,211,238,0.35)]"
              />
              <div>
                <span className="text-base font-extrabold tracking-wider text-white">
                  LYSANDRI
                </span>
                <span className="ml-2 rounded border border-cyan-500/30 bg-cyan-500/10 px-1.5 py-0.5 font-mono text-[9px] font-bold text-cyan-400">
                  EXECUTIVE
                </span>
              </div>
            </div>

            <p className="text-xs leading-relaxed text-slate-400">
              División de Educación Ejecutiva de <strong className="text-slate-200">Yunix Ingenieros E.I.R.L.</strong> Formación de alto impacto en Inteligencia Artificial, Ciberseguridad Práctica, Optimización de Costos y Liderazgo para directores, gerentes y dueños de negocios.
            </p>

            <div className="space-y-1.5 pt-1 text-[11px] text-slate-400">
              <div className="flex items-center gap-2">
                <Building2 className="h-3.5 w-3.5 text-indigo-400 shrink-0" />
                <span>Razón Social: Yunix Ingenieros E.I.R.L.</span>
              </div>
              <div className="flex items-center gap-2">
                <Mail className="h-3.5 w-3.5 text-cyan-400 shrink-0" />
                <a href="mailto:contacto@yunix.pe" className="hover:text-cyan-300 transition-colors">
                  contacto@yunix.pe
                </a>
              </div>
            </div>
          </div>

          {/* Columna 2: Programas Directivos */}
          <div className="space-y-3">
            <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-200 flex items-center gap-2">
              <GraduationCap className="h-4 w-4 text-cyan-400" />
              <span>Programas Directivos</span>
            </h4>
            <ul className="space-y-2 text-xs">
              <li>
                <a href="#catalogo-section" className="hover:text-cyan-400 transition-colors flex items-center gap-1.5">
                  <ChevronRight className="h-3 w-3 text-cyan-600" />
                  <span>IA para Directivos & C-Suite</span>
                </a>
              </li>
              <li>
                <a href="#catalogo-section" className="hover:text-cyan-400 transition-colors flex items-center gap-1.5">
                  <ChevronRight className="h-3 w-3 text-cyan-600" />
                  <span>Ciberseguridad & Protección Antifraude</span>
                </a>
              </li>
              <li>
                <a href="#catalogo-section" className="hover:text-cyan-400 transition-colors flex items-center gap-1.5">
                  <ChevronRight className="h-3 w-3 text-cyan-600" />
                  <span>Gestión Financiera & Reducción de Costos</span>
                </a>
              </li>
              <li>
                <a href="#catalogo-section" className="hover:text-cyan-400 transition-colors flex items-center gap-1.5">
                  <ChevronRight className="h-3 w-3 text-cyan-600" />
                  <span>Automatización de Procesos No-Code</span>
                </a>
              </li>
              <li>
                <a href="#catalogo-section" className="hover:text-cyan-400 transition-colors flex items-center gap-1.5">
                  <ChevronRight className="h-3 w-3 text-cyan-600" />
                  <span>Transformación Digital & Liderazgo</span>
                </a>
              </li>
            </ul>
          </div>

          {/* Columna 3: Metodología y Acreditación */}
          <div className="space-y-3">
            <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-200 flex items-center gap-2">
              <Cpu className="h-4 w-4 text-indigo-400" />
              <span>Garantía & Metodología</span>
            </h4>
            <ul className="space-y-2 text-xs">
              <li className="flex items-center justify-between text-slate-400">
                <span>Campus Virtual 24/7</span>
                <span className="font-mono text-[10px] text-indigo-400 font-semibold">Acceso Multidispositivo</span>
              </li>
              <li className="flex items-center justify-between text-slate-400">
                <span>Docentes C-Level</span>
                <span className="font-mono text-[10px] text-cyan-400 font-semibold">Mentoría Directa</span>
              </li>
              <li className="flex items-center justify-between text-slate-400">
                <span>Casos Prácticos</span>
                <span className="font-mono text-[10px] text-emerald-400 font-semibold">Escenarios Reales</span>
              </li>
              <li className="flex items-center justify-between text-slate-400">
                <span>Facturación Electrónica</span>
                <span className="font-mono text-[10px] text-amber-400 font-semibold">Boleta o Factura</span>
              </li>
              <li className="flex items-center justify-between text-slate-400">
                <span>Certificación Oficial</span>
                <span className="font-mono text-[10px] text-violet-400 font-semibold">Validación Digital</span>
              </li>
            </ul>
          </div>

          {/* Columna 4: Corporativo & Confianza */}
          <div className="space-y-3">
            <h4 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-200 flex items-center gap-2">
              <ShieldCheck className="h-4 w-4 text-emerald-400" />
              <span>Capacitación Corporativa</span>
            </h4>
            <p className="text-xs text-slate-400 leading-relaxed">
              Diseñamos programas in-company adaptados a las necesidades estratégicas, procesos y metas de su empresa.
            </p>
            <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-3 space-y-2">
              <div className="flex items-center gap-2 text-xs text-cyan-300 font-medium">
                <Building2 className="h-3.5 w-3.5 text-cyan-400" />
                <span>¿Cotización empresarial?</span>
              </div>
              <p className="text-[11px] text-slate-400">
                Factura electrónica, cohortes privadas y analítica de desempeño docente.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Legal & Copyright Bar */}
      <div className="border-t border-slate-800/80 bg-[#030508] py-6 px-4 sm:px-6 lg:px-8">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-4 text-center sm:flex-row sm:text-left">
          <p className="text-xs text-slate-500">
            © {new Date().getFullYear()} <strong className="text-slate-400">Yunix Ingenieros E.I.R.L.</strong> Todos los derechos reservados. Lysandri Executive es una marca registrada.
          </p>

          <div className="flex flex-wrap items-center justify-center gap-4 text-xs text-slate-500">
            <a href="#catalogo-section" className="hover:text-slate-300 transition-colors">Términos y Condiciones</a>
            <span>•</span>
            <a href="#catalogo-section" className="hover:text-slate-300 transition-colors">Política de Privacidad</a>
            <span>•</span>
            <a href="#catalogo-section" className="hover:text-slate-300 transition-colors">Garantía Académica</a>
            <span>•</span>
            <a href="#catalogo-section" className="hover:text-slate-300 transition-colors">Catálogo de Programas</a>
          </div>
        </div>
      </div>
    </footer>
  );
};
import React, { useMemo, useState } from 'react';
import {
  Layers,
  LoaderCircle,
  RefreshCw,
  ShieldAlert,
  Bot,
  Building2,
  ArrowRight,
  GraduationCap,
  Sparkles,
  ShieldCheck,
  CheckCircle2,
  Clock3,
  Award,
  BookOpen,
  Filter,
  Crown,
  Zap,
  Calculator,
} from 'lucide-react';
import { CategoryFilter } from '../components/marketplace/CategoryFilter';
import { PlaybookCard } from '../components/marketplace/PlaybookCard';
import { SearchBar } from '../components/marketplace/SearchBar';
import { CartDrawer } from '../components/marketplace/CartDrawer';
import { SyllabusModal } from '../components/marketplace/SyllabusModal';
import { ExecutiveChatDrawer } from '../components/chat/ExecutiveChatDrawer';
import { CorporateInCompanyModal } from '../components/marketplace/CorporateInCompanyModal';
import { CheckoutModal } from '../components/marketplace/CheckoutModal';
import { Category, Playbook } from '../types';
import { useCart } from '../context/CartContext';

interface MarketplaceViewProps {
  playbooks: Playbook[];
  categories: Category[];
  isLoading?: boolean;
  errorMessage?: string;
  selectingId?: string | null;
  onRetry?: () => void;
  onSelectPlaybook?: (playbook: Playbook) => void | Promise<void>;
}

export const MarketplaceView: React.FC<MarketplaceViewProps> = ({
  playbooks,
  categories,
  isLoading = false,
  errorMessage = '',
  onRetry,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [selectedTier, setSelectedTier] = useState<string>('ALL');

  // Modales y Drawers
  const { cartItems, addToCart, removeFromCart, clearCart, isCartOpen, setIsCartOpen } = useCart();
  const [isAiOpen, setIsAiOpen] = useState(false);
  const [isCorporateOpen, setIsCorporateOpen] = useState(false);
  const [selectedForSyllabus, setSelectedForSyllabus] = useState<Playbook | null>(null);
  const [selectedForAi, setSelectedForAi] = useState<Playbook | null>(null);
  const [selectedForCheckout, setSelectedForCheckout] = useState<Playbook | null>(null);

  // Escuchar evento de apertura de Chatbot RAG desde la Navbar pública
  React.useEffect(() => {
    const handleChatEvent = () => setIsAiOpen(true);
    window.addEventListener('open-executive-ai-chat', handleChatEvent);
    return () => window.removeEventListener('open-executive-ai-chat', handleChatEvent);
  }, []);

  // Spotlight Playbook (first ENTERPRISE playbook or first available)
  const spotlightPlaybook = useMemo(() => {
    return playbooks.find((p) => p.tier === 'ENTERPRISE') || playbooks[0] || null;
  }, [playbooks]);

  const filteredPlaybooks = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();

    return playbooks.filter((playbook) => {
      const matchesCategory =
        selectedCategory === 'ALL' || playbook.category === selectedCategory;

      const matchesTier =
        selectedTier === 'ALL' || playbook.tier === selectedTier;

      const matchesSearch =
        !normalizedQuery ||
        playbook.title.toLowerCase().includes(normalizedQuery) ||
        playbook.description.toLowerCase().includes(normalizedQuery) ||
        playbook.tags.some((tag) => tag.toLowerCase().includes(normalizedQuery)) ||
        (playbook.instructor && playbook.instructor.toLowerCase().includes(normalizedQuery));

      return matchesCategory && matchesTier && matchesSearch;
    });
  }, [playbooks, searchQuery, selectedCategory, selectedTier]);

  const handleAskAi = (playbook: Playbook) => {
    setSelectedForAi(playbook);
    setIsAiOpen(true);
  };

  const scrollToSection = (id: string) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <div className="space-y-16 pb-28 relative">
      {/* Hero */}
      <section className="relative overflow-hidden rounded-3xl border border-cyan-500/20 bg-gradient-to-b from-slate-900 via-[#0a1122] to-[#060810] p-6 text-white shadow-2xl md:p-12">
        <div className="pointer-events-none absolute -right-24 -top-24 h-[500px] w-[500px] rounded-full bg-cyan-500/15 blur-[120px]" />
        <div className="pointer-events-none absolute -left-24 -bottom-24 h-[500px] w-[500px] rounded-full bg-indigo-500/15 blur-[120px]" />
        <div className="pointer-events-none absolute inset-0 lysandri-login-grid opacity-25" />

        <div className="relative z-10 grid grid-cols-1 items-center gap-12 lg:grid-cols-12">
          <div className="space-y-7 lg:col-span-7">
            <div className="flex flex-wrap items-center gap-2.5">
              <span className="inline-flex items-center gap-2 rounded-full border border-cyan-500/40 bg-cyan-950/70 px-3.5 py-1.5 font-mono text-[10px] font-bold tracking-wider text-cyan-300 backdrop-blur-md shadow-md">
                <span className="h-2 w-2 rounded-full bg-cyan-400 animate-pulse" />
                Yunix Ingenieros • Formación Ejecutiva Empresarial
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-indigo-500/30 bg-indigo-950/60 px-3 py-1 font-mono text-[10px] font-medium text-indigo-300 backdrop-blur-md">
                <GraduationCap className="h-3.5 w-3.5 text-indigo-400" />
                100% Práctico • Sin Código
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/30 bg-emerald-950/60 px-3 py-1 font-mono text-[10px] font-medium text-emerald-300 backdrop-blur-md">
                <Bot className="h-3.5 w-3.5 text-emerald-400" />
                Tutoría con IA 24/7
              </span>
            </div>

            <div className="space-y-4">
              <h1 className="text-3xl font-black tracking-tight text-white sm:text-4xl md:text-5xl lg:leading-[1.15]">
                Educación Ejecutiva en IA & Estrategia{' '}
                <span className="text-gradient-cyan">
                  para Líderes que Deciden
                </span>
              </h1>
              <p className="text-sm leading-relaxed text-slate-300 md:text-base md:leading-relaxed">
                Programas de alto impacto diseñados para directores, gerentes, dueños de empresas y profesionales que buscan multiplicar sus resultados, automatizar procesos y blindar su negocio sin necesidad de saber programar.
              </p>
            </div>

            {/* Quick Metrics Bar */}
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div className="rounded-2xl border border-slate-800 bg-slate-950/70 p-3.5 backdrop-blur-md">
                <div className="text-2xl font-black text-cyan-400">100%</div>
                <div className="text-[11px] text-slate-400 font-medium">Práctico y Sin Código</div>
              </div>
              <div className="rounded-2xl border border-slate-800 bg-slate-950/70 p-3.5 backdrop-blur-md">
                <div className="text-2xl font-black text-indigo-400">+40%</div>
                <div className="text-[11px] text-slate-400 font-medium">Ahorro en Costos</div>
              </div>
              <div className="rounded-2xl border border-slate-800 bg-slate-950/70 p-3.5 backdrop-blur-md">
                <div className="text-2xl font-black text-emerald-400">1,250+</div>
                <div className="text-[11px] text-slate-400 font-medium">Líderes Formados</div>
              </div>
              <a
                href="/contabilidad"
                className="group rounded-2xl border border-amber-500/30 bg-amber-950/20 p-3.5 backdrop-blur-md hover:border-amber-400 hover:bg-amber-900/30 transition-all block cursor-pointer"
                title="Ir al Portal del Área Contable y Facturación SUNAT"
              >
                <div className="flex items-center justify-between">
                  <div className="text-2xl font-black text-amber-400">SUNAT</div>
                  <Calculator className="h-4 w-4 text-amber-400/80 group-hover:scale-110 transition-transform" />
                </div>
                <div className="text-[11px] text-amber-300 font-medium flex items-center gap-1 mt-0.5">
                  <span>Área Contable</span>
                  <ArrowRight className="h-3 w-3 inline text-amber-400 group-hover:translate-x-0.5 transition-transform" />
                </div>
              </a>
            </div>

            {/* Action CTAs */}
            <div className="flex flex-wrap items-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => scrollToSection('catalogo-section')}
                className="flex items-center gap-2.5 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-cyan-500 bg-size-200 px-6 py-3.5 text-xs font-bold text-white shadow-xl shadow-cyan-500/25 hover:shadow-cyan-500/40 hover:opacity-95 active:scale-95 transition-all"
              >
                <span>Explorar Programas Directivos</span>
                <ArrowRight className="h-4 w-4" />
              </button>

              <button
                type="button"
                onClick={() => setIsAiOpen(true)}
                className="flex items-center gap-2 rounded-xl border border-cyan-500/30 bg-cyan-950/60 px-5 py-3.5 text-xs font-bold text-cyan-300 hover:bg-cyan-900/60 hover:border-cyan-400/60 active:scale-95 transition-all shadow-md"
              >
                <Bot className="h-4 w-4 text-cyan-400" />
                <span>Consultar Asesor con IA</span>
              </button>

              <button
                type="button"
                onClick={() => setIsCorporateOpen(true)}
                className="flex items-center gap-2 rounded-xl border border-slate-700 bg-slate-900/80 px-5 py-3.5 text-xs font-semibold text-slate-300 hover:text-white hover:border-slate-500 active:scale-95 transition-all"
              >
                <Building2 className="h-4 w-4 text-indigo-400" />
                <span>Capacitación para tu Empresa</span>
              </button>
            </div>
          </div>

          {/* Columna Derecha: Tarjeta Ejecutiva Destacada con Glassmorphism (5 columnas) */}
          <div className="relative lg:col-span-5">
            {/* Floating Top Pill */}
            <div className="absolute -top-4 -right-2 z-20 hidden sm:flex items-center gap-2 rounded-full border border-cyan-400/40 bg-slate-950/90 px-3.5 py-1.5 text-[10px] font-mono font-bold text-cyan-300 shadow-xl backdrop-blur-md">
              <span className="flex h-2 w-2 rounded-full bg-cyan-400 animate-pulse" />
              <span>⚡ Acceso Inmediato al Campus Virtual</span>
            </div>

            {/* Floating Bottom Pill */}
            <div className="absolute -bottom-4 -left-2 z-20 hidden sm:flex items-center gap-2 rounded-full border border-emerald-400/40 bg-slate-950/90 px-3.5 py-1.5 text-[10px] font-mono font-bold text-emerald-300 shadow-xl backdrop-blur-md">
              <Award className="h-3.5 w-3.5 text-emerald-400" />
              <span>Certificación Directiva Oficial Yunix</span>
            </div>

            {/* Spotlight Container Card */}
            {spotlightPlaybook && (
              <div className="relative overflow-hidden rounded-3xl border border-cyan-500/30 bg-gradient-to-b from-slate-900/95 via-[#0d1424]/95 to-[#080d19]/95 p-6 shadow-2xl backdrop-blur-xl">
                {/* Header Badge */}
                <div className="flex items-center justify-between pb-4 border-b border-slate-800">
                  <span className="inline-flex items-center gap-1.5 font-mono text-[10px] font-bold text-amber-300 bg-amber-950/60 border border-amber-500/40 rounded-md px-2.5 py-1">
                    <Sparkles className="h-3 w-3 text-amber-400" />
                    CERTIFICACIÓN DIRECTIVA DESTACADA
                  </span>

                  <span className="text-[10px] font-mono text-emerald-400 font-bold flex items-center gap-1">
                    <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
                    MATRÍCULA ABIERTA
                  </span>
                </div>

                {/* Cover Image Preview */}
                <div className="relative my-4 aspect-video w-full overflow-hidden rounded-2xl border border-slate-800">
                  <img
                    src={spotlightPlaybook.coverUrl || 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80'}
                    alt={spotlightPlaybook.title}
                    className="h-full w-full object-cover"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-slate-950 via-transparent to-transparent opacity-80" />
                  <div className="absolute bottom-3 left-3 right-3 flex items-center justify-between">
                    <span className="font-mono text-[9px] rounded-md bg-slate-950/80 px-2.5 py-1 text-cyan-300 border border-cyan-500/30 backdrop-blur-md">
                      {spotlightPlaybook.category}
                    </span>
                    <span className="font-mono text-sm font-black text-cyan-300 rounded-md bg-slate-950/90 px-3 py-1 border border-cyan-500/40">
                      $950 USD
                    </span>
                  </div>
                </div>

                {/* Details */}
                <div className="space-y-3">
                  <h3 className="text-base font-bold text-white line-clamp-2">
                    {spotlightPlaybook.title}
                  </h3>
                  <p className="text-xs text-slate-400 line-clamp-2">
                    {spotlightPlaybook.description}
                  </p>

                  <div className="flex items-center justify-between text-[11px] text-slate-400 border-t border-slate-800/80 pt-3">
                    <span className="flex items-center gap-1.5">
                      <Clock3 className="h-3.5 w-3.5 text-cyan-400" />
                      {spotlightPlaybook.duration || '6 Semanas'}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <GraduationCap className="h-3.5 w-3.5 text-indigo-400" />
                      Modalidad Online Ejecutiva
                    </span>
                  </div>

                  {/* Actions */}
                  <div className="grid grid-cols-2 gap-2 pt-2">
                    <button
                      type="button"
                      onClick={() => setSelectedForSyllabus(spotlightPlaybook)}
                      className="group/spotMalla relative overflow-hidden flex items-center justify-center gap-1.5 rounded-xl border border-slate-700 bg-slate-800/80 py-2.5 text-xs font-semibold text-slate-200 transition-all duration-300 hover:-translate-y-0.5 hover:border-cyan-400 hover:text-cyan-300 hover:shadow-md hover:shadow-cyan-500/15 active:scale-95"
                    >
                      <div className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-cyan-400/10 to-transparent transition-transform duration-700 ease-out group-hover/spotMalla:translate-x-full" />
                      <BookOpen className="h-3.5 w-3.5 text-cyan-400 transition-transform duration-300 group-hover/spotMalla:scale-110 group-hover/spotMalla:-rotate-6" />
                      <span className="relative z-10">Malla Curricular</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => setSelectedForCheckout(spotlightPlaybook)}
                      className="group/spotIns relative overflow-hidden flex items-center justify-center gap-1.5 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-cyan-500 bg-[length:200%_auto] py-2.5 text-xs font-bold text-white shadow-md shadow-cyan-500/20 transition-all duration-500 hover:bg-right hover:-translate-y-0.5 hover:shadow-lg hover:shadow-cyan-500/35 active:scale-95"
                    >
                      <div className="pointer-events-none absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/25 to-transparent transition-transform duration-1000 ease-in-out group-hover/spotIns:translate-x-full" />
                      <span className="relative z-10">Inscribirme Ahora</span>
                      <ArrowRight className="relative z-10 h-3.5 w-3.5 transition-transform duration-300 ease-out group-hover/spotIns:translate-x-1" />
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </section>

      {/* Sectores y especialidades */}
      <section className="space-y-4">
        <p className="text-center font-mono text-[10px] uppercase tracking-widest text-slate-500 dark:text-slate-400 font-bold">
          LÍDERES DE LAS PRINCIPALES ORGANIZACIONES CONFÍAN EN NUESTRA FORMACIÓN EJECUTIVA
        </p>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <div className="flex flex-col items-center justify-center rounded-2xl border border-slate-200/80 bg-white p-4 text-center dark:border-slate-800 dark:bg-[#0c111a] shadow-sm">
            <span className="text-base mb-1">🏦</span>
            <span className="text-xs font-bold text-slate-800 dark:text-slate-200">Banca & Finanzas</span>
            <span className="text-[10px] text-slate-500">Prevención de fraude & control</span>
          </div>

          <div className="flex flex-col items-center justify-center rounded-2xl border border-slate-200/80 bg-white p-4 text-center dark:border-slate-800 dark:bg-[#0c111a] shadow-sm">
            <span className="text-base mb-1">🛍️</span>
            <span className="text-xs font-bold text-slate-800 dark:text-slate-200">Comercio & Retail</span>
            <span className="text-[10px] text-slate-500">Ventas & fidelización con IA</span>
          </div>

          <div className="flex flex-col items-center justify-center rounded-2xl border border-slate-200/80 bg-white p-4 text-center dark:border-slate-800 dark:bg-[#0c111a] shadow-sm">
            <span className="text-base mb-1">💼</span>
            <span className="text-xs font-bold text-slate-800 dark:text-slate-200">Servicios Profesionales</span>
            <span className="text-[10px] text-slate-500">Ahorro de horas operativas</span>
          </div>

          <div className="flex flex-col items-center justify-center rounded-2xl border border-slate-200/80 bg-white p-4 text-center dark:border-slate-800 dark:bg-[#0c111a] shadow-sm">
            <span className="text-base mb-1">🏢</span>
            <span className="text-xs font-bold text-slate-800 dark:text-slate-200">Dirección & C-Suite</span>
            <span className="text-[10px] text-slate-500">Decisiones estratégicas con datos</span>
          </div>

          <div className="flex flex-col items-center justify-center rounded-2xl border border-slate-200/80 bg-white p-4 text-center dark:border-slate-800 dark:bg-[#0c111a] shadow-sm">
            <span className="text-base mb-1">🏭</span>
            <span className="text-xs font-bold text-slate-800 dark:text-slate-200">PYMES & Empresas</span>
            <span className="text-[10px] text-slate-500">Automatización sin programar</span>
          </div>
        </div>
      </section>

      {/* Metodología */}
      <section id="metodologia-section" className="space-y-6 scroll-mt-20">
        <div className="text-center max-w-2xl mx-auto space-y-2">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-cyan-500/30 bg-cyan-950/40 px-3 py-1 font-mono text-[10px] font-bold text-cyan-400">
            <Sparkles className="h-3 w-3" />
            METODOLOGÍA DISEÑADA PARA DIRECTIVOS
          </span>
          <h2 className="text-2xl font-black text-slate-900 dark:text-white md:text-3xl">
            Aprende a tu Ritmo, con Enfoque 100% en Resultados de Negocio
          </h2>
          <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400">
            Diseñada para directores y gerentes que necesitan soluciones reales: casos prácticos empresariales, retorno de inversión y herramientas listas para aplicar.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {/* Pilar 1: Campus Virtual */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-7 transition-all duration-300 hover:border-indigo-400/50 hover:shadow-xl dark:border-slate-800 dark:bg-[#0d121c] dark:hover:border-indigo-500/40">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-500/10 text-indigo-500 mb-5">
              <GraduationCap className="h-6 w-6" />
            </div>
            <h3 className="text-base font-bold text-slate-900 dark:text-white mb-2">
              Campus Virtual Flexible 24/7
            </h3>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed mb-4">
              Acceso inmediato a clases en video de alta calidad, material ejecutivo y foros de debate. Estudia a tu propio ritmo, desde cualquier dispositivo y sin horarios rígidos.
            </p>
            <div className="flex items-center gap-2 font-mono text-[10px] text-indigo-500 font-semibold">
              <CheckCircle2 className="h-3.5 w-3.5" />
              <span>Acceso 24/7 sin límite de horarios</span>
            </div>
          </div>

          {/* Pilar 2: Asistente Inteligente */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-7 transition-all duration-300 hover:border-cyan-400/50 hover:shadow-xl dark:border-slate-800 dark:bg-[#0d121c] dark:hover:border-cyan-500/40">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-cyan-500/10 text-cyan-500 mb-5">
              <Bot className="h-6 w-6" />
            </div>
            <h3 className="text-base font-bold text-slate-900 dark:text-white mb-2">
              Asesor Inteligente con IA para Negocios
            </h3>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed mb-4">
              Un tutor virtual inteligente entrenado con los materiales y mejores prácticas de gestión para resolver dudas de tu empresa en segundos, orientarte y guiar tu aprendizaje.
            </p>
            <div className="flex items-center gap-2 font-mono text-[10px] text-cyan-500 font-semibold">
              <CheckCircle2 className="h-3.5 w-3.5" />
              <span>Orientación personalizada inmediata</span>
            </div>
          </div>

          {/* Pilar 3: Certificación Verificable */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-7 transition-all duration-300 hover:border-emerald-400/50 hover:shadow-xl dark:border-slate-800 dark:bg-[#0d121c] dark:hover:border-emerald-500/40">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-500 mb-5">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <h3 className="text-base font-bold text-slate-900 dark:text-white mb-2">
              Certificación Oficial para tu Perfil Directivo
            </h3>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed mb-4">
              Diploma ejecutivo respaldado por Yunix Ingenieros E.I.R.L. con código de verificación QR oficial para respaldar tu currículum, perfil de LinkedIn y comités directivos.
            </p>
            <div className="flex items-center gap-2 font-mono text-[10px] text-emerald-500 font-semibold">
              <CheckCircle2 className="h-3.5 w-3.5" />
              <span>Reconocimiento profesional verificable</span>
            </div>
          </div>
        </div>
      </section>

      {/* Catálogo y filtros - Academic EdTech Showcase */}
      <section id="catalogo-section" className="space-y-5 scroll-mt-20">
        <div className="edtech-catalog-panel relative overflow-hidden rounded-3xl p-6 md:p-8">
          {/* Ambient decorative glow */}
          <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-cyan-500/15 blur-[90px]" />
          <div className="pointer-events-none absolute -left-24 -bottom-24 h-72 w-72 rounded-full bg-indigo-500/15 blur-[90px]" />
          <div className="pointer-events-none absolute inset-0 lysandri-login-grid opacity-10" />

          {/* Academic Top Status Badges */}
          <div className="relative z-10 flex flex-wrap items-center justify-between gap-3 pb-6 border-b border-slate-800/80">
            <div className="flex flex-wrap items-center gap-2">
              <span className="inline-flex items-center gap-1.5 rounded-full border border-cyan-400/40 bg-cyan-950/70 px-3.5 py-1 font-mono text-[10px] font-bold text-cyan-300 shadow-sm backdrop-blur-md">
                <GraduationCap className="h-3.5 w-3.5 text-cyan-400" />
                Portal Académico 2026-II
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-indigo-400/30 bg-indigo-950/60 px-3 py-1 font-mono text-[10px] font-medium text-indigo-300 backdrop-blur-md">
                <BookOpen className="h-3.5 w-3.5 text-indigo-400" />
                Campus Virtual Activo 24/7
              </span>
              <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-400/30 bg-emerald-950/60 px-3 py-1 font-mono text-[10px] font-medium text-emerald-300 backdrop-blur-md">
                <Award className="h-3.5 w-3.5 text-emerald-400" />
                Certificación Oficial Yunix
              </span>
            </div>

            <div className="flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-950/40 px-3.5 py-1 font-mono text-[10px] text-emerald-300">
              <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="font-semibold">Convocatoria con Matrícula Abierta</span>
            </div>
          </div>

          {/* Catalog Title + SearchBar */}
          <div className="relative z-10 pt-6 pb-6 grid grid-cols-1 gap-6 lg:grid-cols-12 lg:items-center">
            <div className="lg:col-span-7 space-y-2">
              <h2 className="text-2xl sm:text-3xl font-black text-white tracking-tight flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-cyan-500/10 border border-cyan-500/30 text-cyan-400 shadow-glow-cyan-sm shrink-0">
                  <GraduationCap className="h-5 w-5" />
                </span>
                <span>Catálogo de Especialización Directiva</span>
              </h2>
              <p className="text-xs sm:text-sm text-slate-300 leading-relaxed max-w-xl">
                Programas académicos estructurados con metodología 100% práctica, casos reales de empresa y acompañamiento continuo de asesores con Inteligencia Artificial.
              </p>
            </div>

            <div className="lg:col-span-5">
              <SearchBar
                searchQuery={searchQuery}
                setSearchQuery={setSearchQuery}
                resultsCount={filteredPlaybooks.length}
              />
            </div>
          </div>

          {/* Categories & Academic Level Selector */}
          <div className="relative z-10 pt-5 border-t border-slate-800/80 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="overflow-hidden">
              <CategoryFilter
                categories={categories}
                selectedCategory={selectedCategory}
                onSelectCategory={setSelectedCategory}
              />
            </div>

            {/* Academic Level Selector */}
            <div className="flex items-center gap-1.5 shrink-0 overflow-x-auto pb-1">
              <span className="text-[11px] font-mono text-slate-400 mr-1 flex items-center gap-1">
                <Filter className="h-3 w-3 text-cyan-400" /> Nivel Académico:
              </span>
              {[
                { id: 'ALL', label: 'Todos los Niveles', icon: Layers },
                { id: 'ENTERPRISE', label: 'C-Suite / Directivo', icon: Crown },
                { id: 'ADVANCED', label: 'Avanzado', icon: ShieldCheck },
                { id: 'ESSENTIAL', label: 'Esencial', icon: Zap },
              ].map((tier) => {
                const IconComp = tier.icon;
                const isSelected = selectedTier === tier.id;
                return (
                  <button
                    key={tier.id}
                    type="button"
                    onClick={() => setSelectedTier(tier.id)}
                    className={`flex items-center gap-1.5 rounded-xl px-3 py-1.5 font-mono text-[10px] font-semibold transition-all duration-200 active:scale-95 ${
                      isSelected
                        ? 'border border-cyan-400 bg-gradient-to-r from-cyan-950/90 to-indigo-950/90 text-cyan-300 shadow-md shadow-cyan-500/20'
                        : 'border border-slate-800 bg-slate-900/60 text-slate-400 hover:text-white hover:border-slate-700'
                    }`}
                  >
                    <IconComp className={`h-3 w-3 ${isSelected ? 'text-cyan-400' : 'text-slate-500'}`} />
                    <span>{tier.label}</span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Student Value Quick Summary Strip */}
        <div className="flex flex-wrap items-center justify-between gap-3 px-2 text-[11px] text-slate-400">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-cyan-400 animate-pulse" />
            <span className="text-slate-300 font-medium">
              Mostrando <strong className="text-cyan-400 font-bold">{filteredPlaybooks.length}</strong> {filteredPlaybooks.length === 1 ? 'programa disponible' : 'programas disponibles'}
            </span>
          </div>
          <div className="hidden sm:flex items-center gap-4 text-slate-400">
            <span className="flex items-center gap-1.5">
              <Clock3 className="h-3.5 w-3.5 text-cyan-400" />
              Modalidad 100% Online Asincrónica
            </span>
            <span>•</span>
            <span className="flex items-center gap-1.5">
              <Award className="h-3.5 w-3.5 text-indigo-400" />
              Diploma Oficial con Código QR
            </span>
            <span>•</span>
            <span className="flex items-center gap-1.5">
              <Bot className="h-3.5 w-3.5 text-emerald-400" />
              Tutoría Continua con IA
            </span>
          </div>
        </div>
      </section>

      {isLoading ? (
        <div className="flex min-h-[350px] flex-col items-center justify-center gap-3 rounded-3xl border border-slate-200 bg-white p-12 text-slate-500 dark:border-slate-800 dark:bg-[#0d121a]">
          <LoaderCircle className="h-10 w-10 animate-spin text-cyan-500" />
          <p className="text-xs font-semibold">Cargando catálogo ejecutivo de Yunix Ingenieros...</p>
        </div>
      ) : errorMessage ? (
        <div className="flex min-h-[350px] flex-col items-center justify-center gap-4 rounded-3xl border border-rose-200 bg-rose-50/50 p-12 text-center dark:border-rose-900/40 dark:bg-rose-950/20">
          <ShieldAlert className="h-12 w-12 text-rose-500" />
          <div className="space-y-1">
            <p className="text-base font-bold text-rose-900 dark:text-rose-200">
              No se pudo sincronizar el catálogo
            </p>
            <p className="text-xs text-rose-700 dark:text-rose-400 max-w-md">{errorMessage}</p>
          </div>
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-5 py-2.5 text-xs font-bold text-white hover:bg-rose-700 transition-colors shadow-md"
            >
              <RefreshCw className="h-4 w-4" />
              Reintentar Sincronización
            </button>
          )}
        </div>
      ) : filteredPlaybooks.length === 0 ? (
        <div className="flex min-h-[350px] flex-col items-center justify-center gap-3 rounded-3xl border border-slate-200 bg-white p-12 text-center dark:border-slate-800 dark:bg-[#0d121a]">
          <div className="rounded-2xl bg-slate-100 dark:bg-slate-900 p-4 text-slate-400">
            <Layers className="h-8 w-8" />
          </div>
          <p className="text-sm font-bold text-slate-900 dark:text-slate-100">
            No se encontraron programas con los filtros seleccionados
          </p>
          <p className="text-xs text-slate-500 max-w-sm">
            Prueba buscando por términos como "Inteligencia Artificial", "Ciberseguridad", "Finanzas" o restablece los filtros.
          </p>
          <button
            type="button"
            onClick={() => {
              setSearchQuery('');
              setSelectedCategory('ALL');
              setSelectedTier('ALL');
            }}
            className="mt-2 text-xs font-bold text-cyan-500 hover:underline"
          >
            Restablecer Filtros
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {filteredPlaybooks.map((playbook) => (
            <PlaybookCard
              key={playbook.id}
              playbook={playbook}
              onSelect={(pb) => setSelectedForCheckout(pb)}
              onPreviewSyllabus={(pb) => setSelectedForSyllabus(pb)}
              onAddToCart={(pb) => addToCart(pb)}
              onAskAi={handleAskAi}
            />
          ))}
        </div>
      )}

      {/* Capacitación In-Company */}
      <section id="in-company-section" className="relative overflow-hidden rounded-3xl border border-indigo-500/25 bg-gradient-to-r from-indigo-950/70 via-slate-900 to-cyan-950/70 p-8 md:p-12 shadow-2xl scroll-mt-20">
        <div className="flex flex-col gap-8 lg:flex-row lg:items-center lg:justify-between relative z-10">
          <div className="max-w-2xl space-y-4">
            <span className="inline-flex items-center gap-2 rounded-full border border-indigo-400/40 bg-indigo-950/90 px-3.5 py-1 font-mono text-[10px] font-bold text-indigo-300">
              <Building2 className="h-3.5 w-3.5" />
              CAPACITACIÓN CORPORATIVA PARA EQUIPOS GERENCIALES
            </span>
            <h3 className="text-2xl font-black text-white sm:text-3xl leading-snug">
              ¿Deseas capacitar al equipo de tu empresa?
            </h3>
            <p className="text-xs leading-relaxed text-slate-300 sm:text-sm">
              Diseñamos programas a la medida de tu organización para modernizar la gestión empresarial, implementar Inteligencia Artificial en tus procesos y optimizar los costos operativos de tu empresa.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 text-xs text-slate-300">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Facturación con RUC deducible para empresas (SUNAT)</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Casos de estudio enfocados en el rubro real de tu negocio</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Seguimiento de progreso y reportes de impacto para gerencia</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Acompañamiento personalizado y sesiones en vivo</span>
              </div>
            </div>
          </div>

          <div className="shrink-0 flex flex-col gap-3">
            <button
              type="button"
              onClick={() => setIsCorporateOpen(true)}
              className="flex items-center justify-center gap-2.5 rounded-xl bg-white px-7 py-4 text-xs font-bold text-slate-950 shadow-xl shadow-white/10 hover:bg-slate-100 active:scale-95 transition-all"
            >
              <Building2 className="h-4 w-4 text-indigo-600" />
              <span>Solicitar Cotización Empresarial</span>
            </button>
            <p className="text-center font-mono text-[10px] text-slate-400">
              Respuesta en menos de 24 horas laborables
            </p>
          </div>
        </div>
      </section>


      {/* Modales y drawers */}
      <CheckoutModal
        isOpen={Boolean(selectedForCheckout)}
        onClose={() => setSelectedForCheckout(null)}
        playbook={selectedForCheckout}
      />

      <CartDrawer
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        cartItems={cartItems}
        onRemoveItem={removeFromCart}
        onClearCart={clearCart}
      />

      <SyllabusModal
        isOpen={Boolean(selectedForSyllabus)}
        onClose={() => setSelectedForSyllabus(null)}
        playbook={selectedForSyllabus}
        onSelect={(pb) => setSelectedForCheckout(pb)}
        onAddToCart={(pb) => addToCart(pb)}
        onAskAi={handleAskAi}
      />

      <ExecutiveChatDrawer
        isOpen={isAiOpen}
        onClose={() => setIsAiOpen(false)}
        activePlaybook={selectedForAi}
        onClearContext={() => setSelectedForAi(null)}
      />

      <CorporateInCompanyModal
        isOpen={isCorporateOpen}
        onClose={() => setIsCorporateOpen(false)}
      />
    </div>
  );
};
import { FC, useEffect, useMemo, useState } from 'react';
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
  Users,
  MessageCircle,
  Star,
  ChevronDown,
  HelpCircle,
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
}

export const MarketplaceView: FC<MarketplaceViewProps> = ({
  playbooks,
  categories,
  isLoading = false,
  errorMessage = '',
  selectingId = null,
  onRetry,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [selectedTier, setSelectedTier] = useState<string>('ALL');
  const [openFaqIndex, setOpenFaqIndex] = useState<number | null>(null);

  // Modales y Drawers
  const { cartItems, addToCart, removeFromCart, clearCart, isCartOpen, setIsCartOpen } = useCart();
  const [isAiOpen, setIsAiOpen] = useState(false);
  const [isCorporateOpen, setIsCorporateOpen] = useState(false);
  const [selectedForSyllabus, setSelectedForSyllabus] = useState<Playbook | null>(null);
  const [selectedForAi, setSelectedForAi] = useState<Playbook | null>(null);
  const [selectedForCheckout, setSelectedForCheckout] = useState<Playbook | null>(null);

  // Escuchar evento de apertura de Chatbot RAG desde la Navbar pública
  useEffect(() => {
    const handleChatEvent = () => setIsAiOpen(true);
    window.addEventListener('open-executive-ai-chat', handleChatEvent);
    return () => window.removeEventListener('open-executive-ai-chat', handleChatEvent);
  }, []);

  const handleSelectPlaybook = (playbook: Playbook) => {
    setSelectedForCheckout(playbook);
  };

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
                Lysandri • Formación Ejecutiva Empresarial
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
              <div className="rounded-2xl border border-slate-800 bg-slate-950/70 p-3.5 backdrop-blur-md">
                <div className="text-2xl font-black text-amber-400">4.9/5</div>
                <div className="text-[11px] text-slate-400 font-medium">Satisfacción Directiva</div>
              </div>
            </div>

            {/* Concierge Welcome Bar */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-2xl border border-indigo-500/30 bg-gradient-to-r from-indigo-950/50 via-purple-950/25 to-slate-900/80 p-3.5 backdrop-blur-md shadow-sm">
              <div className="flex items-center gap-3">
                <div className="relative shrink-0">
                  <div className="h-10 w-10 rounded-full bg-gradient-to-tr from-cyan-400 via-indigo-500 to-fuchsia-500 p-0.5 shadow-md">
                    <div className="h-full w-full rounded-full bg-slate-900 flex items-center justify-center text-cyan-300 font-bold text-xs">
                      SV
                    </div>
                  </div>
                  <span className="absolute bottom-0 right-0 h-2.5 w-2.5 rounded-full bg-emerald-400 border border-slate-900 animate-pulse" title="En línea para orientarte" />
                </div>
                <div className="text-xs text-slate-300">
                  <p className="font-bold text-white flex items-center gap-1.5">
                    <span>Lic. Sofía Valdivia</span>
                    <span className="text-[10px] font-normal text-indigo-300">· Coordinación de Admisiones</span>
                  </p>
                  <p className="text-[11px] text-slate-300 mt-0.5">
                    «¿Dudas sobre qué especialización directiva elegir? Te orientamos en minutos por WhatsApp.»
                  </p>
                </div>
              </div>
              <a
                href="https://wa.me/51941238905?text=Hola%20Sof%C3%ADa,%20deseo%20orientaci%C3%B3n%20sobre%20los%20programas%20ejecutivos%20de%20Lysandri"
                target="_blank"
                rel="noopener noreferrer"
                className="shrink-0 inline-flex items-center justify-center gap-1.5 rounded-xl border border-emerald-500/40 bg-emerald-950/60 px-3.5 py-2 text-xs font-bold text-emerald-300 hover:bg-emerald-900/60 transition-colors shadow-sm"
              >
                <MessageCircle className="h-3.5 w-3.5 text-emerald-400" />
                <span>Hablar con Sofía</span>
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
              <span>Acceso Inmediato al Campus Virtual</span>
            </div>

            {/* Floating Bottom Pill */}
            <div className="absolute -bottom-4 -left-2 z-20 hidden sm:flex items-center gap-2 rounded-full border border-emerald-400/40 bg-slate-950/90 px-3.5 py-1.5 text-[10px] font-mono font-bold text-emerald-300 shadow-xl backdrop-blur-md">
              <Award className="h-3.5 w-3.5 text-emerald-400" />
              <span>Certificación Directiva Oficial Lysandri</span>
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
                      onClick={() => handleSelectPlaybook(spotlightPlaybook)}
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
              Diploma ejecutivo respaldado por Lysandri con código de verificación QR oficial para respaldar tu currículum, perfil de LinkedIn y comités directivos.
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
                Certificación Oficial Lysandri
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
          <p className="text-xs font-semibold">Cargando catálogo ejecutivo de Lysandri...</p>
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
              onSelect={handleSelectPlaybook}
              onPreviewSyllabus={(pb) => setSelectedForSyllabus(pb)}
              onAddToCart={(pb) => addToCart(pb)}
              onAskAi={handleAskAi}
              isSubmitting={selectingId === String(playbook.id)}
            />
          ))}
        </div>
      )}

      {/* Capacitación In-Company */}
      <section id="in-company-section" className="relative overflow-hidden rounded-3xl border border-indigo-500/25 bg-gradient-to-r from-indigo-950/70 via-slate-900 to-cyan-950/70 p-8 md:p-12 shadow-2xl scroll-mt-20">
        <div className="flex flex-col gap-8 lg:flex-row lg:items-center lg:justify-between relative z-10">
          <div className="max-w-2xl space-y-4">
            <span className="inline-flex items-center gap-2 rounded-full border border-indigo-400/40 bg-indigo-950/90 px-3.5 py-1 font-mono text-[10px] font-bold text-indigo-300">
              <Users className="h-3.5 w-3.5" />
              FORMACIÓN CORPORATIVA PARA EQUIPOS
            </span>
            <h3 className="text-2xl font-black text-white sm:text-3xl leading-snug">
              ¿Buscas capacitar a tu equipo o área de trabajo?
            </h3>
            <p className="text-xs leading-relaxed text-slate-300 sm:text-sm">
              Diseñamos cohortes y talleres prácticos adaptados a las herramientas y objetivos reales de tu empresa. Con horarios flexibles, laboratorios aplicados y facturación con RUC deducible.
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
                <span>Seguimiento de progreso y reportes para gerencia</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Acompañamiento directo con instructores en activo</span>
              </div>
            </div>
          </div>

          <div className="shrink-0 flex flex-col gap-3">
            <button
              type="button"
              onClick={() => setIsCorporateOpen(true)}
              className="flex items-center justify-center gap-2.5 rounded-xl bg-white px-7 py-4 text-xs font-bold text-slate-950 shadow-xl shadow-white/10 hover:bg-slate-100 active:scale-95 transition-all"
            >
              <Users className="h-4 w-4 text-indigo-600" />
              <span>Coordinar Capacitación para mi Equipo</span>
            </button>
            <p className="text-center font-mono text-[10px] text-slate-400">
              Atención directa con nuestro equipo académico
            </p>
          </div>
        </div>
      </section>

      {/* Sección 1: Dirección Académica & Mentores en Activo */}
      <section className="space-y-6">
        <div className="text-center max-w-2xl mx-auto space-y-2">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-cyan-500/30 bg-cyan-950/40 px-3.5 py-1 font-mono text-[10px] font-bold text-cyan-400">
            <GraduationCap className="h-3.5 w-3.5" />
            DIRECCIÓN ACADÉMICA & MENTORÍA EN ACTIVO
          </span>
          <h2 className="text-2xl font-black text-slate-900 dark:text-white md:text-3xl">
            Aprende de Líderes Reales con Experiencia en las Empresas Más Influyentes
          </h2>
          <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400">
            Nuestros mentores son directores, consultores C-Suite y líderes de tecnología en activo. No enseñamos teoría abstracta, sino metodologías probadas con impacto directo en balance, ahorro operativo y crecimiento.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Mentor 1 */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-6 transition-all duration-300 hover:border-cyan-400/50 hover:shadow-xl dark:border-slate-800 dark:bg-[#0d121c] flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-center gap-3.5">
                <img
                  src="https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80"
                  alt="Dr. Alejandro Benavides"
                  className="h-14 w-14 rounded-2xl object-cover border-2 border-cyan-400/40 shadow-md"
                />
                <div>
                  <h3 className="text-sm font-bold text-slate-900 dark:text-white">
                    Dr. Alejandro Benavides
                  </h3>
                  <p className="text-[11px] text-cyan-500 font-medium">
                    Director Académico de IA & Estrategia
                  </p>
                  <p className="text-[10px] text-slate-500 font-mono">
                    PhD Stanford • Ex-VP Analítica en Banca
                  </p>
                </div>
              </div>
              <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed italic">
                «El valor de la IA para la alta dirección no está en saber codificar algoritmos, sino en formular las preguntas correctas y dirigir equipos técnicos con autoridad de negocio.»
              </p>
            </div>
            <div className="pt-4 border-t border-slate-100 dark:border-slate-800/80 mt-4 flex items-center justify-between text-[10px] text-slate-500">
              <span className="inline-flex items-center gap-1 text-emerald-500 font-semibold">
                <CheckCircle2 className="h-3 w-3" /> Mentor Activo 2026
              </span>
              <span>Casos Prácticos Reales</span>
            </div>
          </div>

          {/* Mentor 2 */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-6 transition-all duration-300 hover:border-indigo-400/50 hover:shadow-xl dark:border-slate-800 dark:bg-[#0d121c] flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-center gap-3.5">
                <img
                  src="https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80"
                  alt="Dra. Valeria Quintana"
                  className="h-14 w-14 rounded-2xl object-cover border-2 border-indigo-400/40 shadow-md"
                />
                <div>
                  <h3 className="text-sm font-bold text-slate-900 dark:text-white">
                    Dra. Valeria Quintana
                  </h3>
                  <p className="text-[11px] text-indigo-400 font-medium">
                    Mentora Principal en Ciberseguridad & Riesgos
                  </p>
                  <p className="text-[10px] text-slate-500 font-mono">
                    MBA INSEAD • Asesora de Directorios C-Suite
                  </p>
                </div>
              </div>
              <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed italic">
                «Blindar una corporación moderna contra fraudes y ciberataques es una decisión estratégica de gobernanza, no un asunto confinado al área de sistemas.»
              </p>
            </div>
            <div className="pt-4 border-t border-slate-100 dark:border-slate-800/80 mt-4 flex items-center justify-between text-[10px] text-slate-500">
              <span className="inline-flex items-center gap-1 text-emerald-500 font-semibold">
                <CheckCircle2 className="h-3 w-3" /> Asesoría Directa
              </span>
              <span>Gobernanza Corporativa</span>
            </div>
          </div>

          {/* Mentor 3 */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-6 transition-all duration-300 hover:border-fuchsia-400/50 hover:shadow-xl dark:border-slate-800 dark:bg-[#0d121c] flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex items-center gap-3.5">
                <img
                  src="https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80"
                  alt="Ing. Martín Solano"
                  className="h-14 w-14 rounded-2xl object-cover border-2 border-fuchsia-400/40 shadow-md"
                />
                <div>
                  <h3 className="text-sm font-bold text-slate-900 dark:text-white">
                    Ing. Martín Solano
                  </h3>
                  <p className="text-[11px] text-fuchsia-400 font-medium">
                    Mentor en Automatización No-Code & Eficiencia
                  </p>
                  <p className="text-[10px] text-slate-500 font-mono">
                    Ex-McKinsey & Co. • Líder de Transformación
                  </p>
                </div>
              </div>
              <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed italic">
                «Mi compromiso en cada módulo es que el alumno identifique procesos redundantes y automatice flujos que le ahorren al menos 10 horas semanales a su equipo.»
              </p>
            </div>
            <div className="pt-4 border-t border-slate-100 dark:border-slate-800/80 mt-4 flex items-center justify-between text-[10px] text-slate-500">
              <span className="inline-flex items-center gap-1 text-emerald-500 font-semibold">
                <CheckCircle2 className="h-3 w-3" /> Metodología No-Code
              </span>
              <span>Retorno Inmediato</span>
            </div>
          </div>
        </div>
      </section>

      {/* Sección 2: Historias Reales & Testimonios Directivos */}
      <section className="space-y-6">
        <div className="text-center max-w-2xl mx-auto space-y-2">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-500/30 bg-amber-950/40 px-3.5 py-1 font-mono text-[10px] font-bold text-amber-400">
            <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
            HISTORIAS DE ÉXITO & TESTIMONIOS DIRECTIVOS
          </span>
          <h2 className="text-2xl font-black text-slate-900 dark:text-white md:text-3xl">
            Lo que Opinan los Directores que ya se Graduaron
          </h2>
          <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400">
            Casos de éxito reales de líderes empresariales que transformaron sus áreas, optimizaron costos y certificaron a sus equipos directivos con Lysandri.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {/* Testimonio 1 */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-6 dark:border-slate-800 dark:bg-[#0c111a] shadow-sm flex flex-col justify-between">
            <div className="space-y-3">
              <div className="flex items-center gap-1 text-amber-400">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} className="h-4 w-4 fill-amber-400 text-amber-400" />
                ))}
              </div>
              <p className="text-xs text-slate-700 dark:text-slate-300 leading-relaxed italic">
                «Redujimos en un 38% el tiempo de generación de reportes para el comité de gerencia. Lo que más destaco es la calidez y el acompañamiento: cuando tuve dudas sobre cómo aplicar un flujo a mi ERP, la orientación fue inmediata.»
              </p>
            </div>
            <div className="pt-4 border-t border-slate-100 dark:border-slate-800/80 mt-4 flex items-center gap-3">
              <img
                src="https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=200&q=80"
                alt="Carlos Villacorta"
                className="h-10 w-10 rounded-full object-cover border border-cyan-400/40"
              />
              <div>
                <h4 className="text-xs font-bold text-slate-900 dark:text-white">
                  MBA Carlos Villacorta
                </h4>
                <p className="text-[10px] text-slate-500">
                  Gerente de Operaciones • Retail & Consumo Masivo
                </p>
                <span className="font-mono text-[9px] text-emerald-500 font-semibold flex items-center gap-1 mt-0.5">
                  <CheckCircle2 className="h-2.5 w-2.5" /> Factura Electrónica SUNAT
                </span>
              </div>
            </div>
          </div>

          {/* Testimonio 2 */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-6 dark:border-slate-800 dark:bg-[#0c111a] shadow-sm flex flex-col justify-between">
            <div className="space-y-3">
              <div className="flex items-center gap-1 text-amber-400">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} className="h-4 w-4 fill-amber-400 text-amber-400" />
                ))}
              </div>
              <p className="text-xs text-slate-700 dark:text-slate-300 leading-relaxed italic">
                «La experiencia de pago con izipay fue impecable; en 2 minutos ya tenía mis accesos a Moodle y mi factura con RUC en el correo. El enfoque sin código es perfecto para quienes no venimos del mundo técnico.»
              </p>
            </div>
            <div className="pt-4 border-t border-slate-100 dark:border-slate-800/80 mt-4 flex items-center gap-3">
              <img
                src="https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=200&q=80"
                alt="Dra. Patricia Arbulú"
                className="h-10 w-10 rounded-full object-cover border border-indigo-400/40"
              />
              <div>
                <h4 className="text-xs font-bold text-slate-900 dark:text-white">
                  Dra. Patricia Arbulú
                </h4>
                <p className="text-[10px] text-slate-500">
                  Directora de Finanzas (CFO) • Grupo Industrial
                </p>
                <span className="font-mono text-[9px] text-cyan-500 font-semibold flex items-center gap-1 mt-0.5">
                  <CheckCircle2 className="h-2.5 w-2.5" /> Pago izipay Online
                </span>
              </div>
            </div>
          </div>

          {/* Testimonio 3 */}
          <div className="rounded-3xl border border-slate-200/90 bg-white p-6 dark:border-slate-800 dark:bg-[#0c111a] shadow-sm flex flex-col justify-between">
            <div className="space-y-3">
              <div className="flex items-center gap-1 text-amber-400">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} className="h-4 w-4 fill-amber-400 text-amber-400" />
                ))}
              </div>
              <p className="text-xs text-slate-700 dark:text-slate-300 leading-relaxed italic">
                «Capacitamos a 6 gerentes de área en automatización con IA. La atención de Sofía Valdivia por WhatsApp fue de primer nivel, resolvió nuestras dudas en minutos y el aula virtual estuvo disponible 24/7 sin fricción.»
              </p>
            </div>
            <div className="pt-4 border-t border-slate-100 dark:border-slate-800/80 mt-4 flex items-center gap-3">
              <img
                src="https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=200&q=80"
                alt="Ing. Gonzalo De la Torre"
                className="h-10 w-10 rounded-full object-cover border border-fuchsia-400/40"
              />
              <div>
                <h4 className="text-xs font-bold text-slate-900 dark:text-white">
                  Ing. Gonzalo De la Torre
                </h4>
                <p className="text-[10px] text-slate-500">
                  CEO • Operador Logístico 3PL LatAm
                </p>
                <span className="font-mono text-[9px] text-indigo-400 font-semibold flex items-center gap-1 mt-0.5">
                  <CheckCircle2 className="h-2.5 w-2.5" /> Formación In-Company
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Sección 3: Garantía Humana de Satisfacción Lysandri (7 Días Cero Riesgo) */}
      <section className="relative overflow-hidden rounded-3xl border border-emerald-500/30 bg-gradient-to-r from-emerald-950/40 via-slate-900 to-teal-950/40 p-8 md:p-12 shadow-2xl">
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-emerald-500/10 blur-[80px]" />
        <div className="relative z-10 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-8">
          <div className="max-w-2xl space-y-4">
            <span className="inline-flex items-center gap-2 rounded-full border border-emerald-400/40 bg-emerald-950/90 px-3.5 py-1 font-mono text-[10px] font-bold text-emerald-300">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-400" />
              GARANTÍA DE SATISFACCIÓN TOTAL LYSANDRI • 7 DÍAS
            </span>
            <h3 className="text-2xl font-black text-white sm:text-3xl leading-snug">
              Prueba el Campus Virtual sin Ningún Riesgo para tu Empresa
            </h3>
            <p className="text-xs leading-relaxed text-slate-300 sm:text-sm">
              Sabemos que tu tiempo y tu inversión son valiosos. Explora las primeras lecciones, interactúa con el Asesor Inteligente y revisa el material. Si durante los primeros 7 días consideras que el programa no supera tus estándares de excelencia directiva, te reembolsamos el <strong>100% de tu dinero</strong> de inmediato mediante izipay Online. Sin preguntas incómodas ni trámites engorrosos.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2 text-xs text-slate-300">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Reembolso 100% inmediato</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Sin preguntas ni burocracia</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
                <span>Atención directa por WhatsApp</span>
              </div>
            </div>
          </div>

          <div className="shrink-0 flex flex-col items-center justify-center p-6 rounded-2xl border border-emerald-500/30 bg-slate-950/80 text-center backdrop-blur-md">
            <div className="h-16 w-16 rounded-full bg-emerald-500/20 border-2 border-emerald-400 flex items-center justify-center text-emerald-400 mb-3 shadow-lg shadow-emerald-500/20">
              <ShieldCheck className="h-8 w-8" />
            </div>
            <span className="font-mono text-2xl font-black text-white">7 DÍAS</span>
            <span className="text-[11px] font-bold text-emerald-300 uppercase tracking-wider">
              100% Garantizado
            </span>
            <span className="text-[10px] text-slate-400 mt-1 max-w-[180px]">
              Protegido por izipay Online & Lysandri Executive
            </span>
          </div>
        </div>
      </section>

      {/* Sección 4: Preguntas Frecuentes Respondidas de Persona a Persona (FAQ) */}
      <section className="space-y-6">
        <div className="text-center max-w-2xl mx-auto space-y-2">
          <span className="inline-flex items-center gap-1.5 rounded-full border border-cyan-500/30 bg-cyan-950/40 px-3.5 py-1 font-mono text-[10px] font-bold text-cyan-400">
            <HelpCircle className="h-3.5 w-3.5" />
            PREGUNTAS FRECUENTES RESUELTAS DE PERSONA A PERSONA
          </span>
          <h2 className="text-2xl font-black text-slate-900 dark:text-white md:text-3xl">
            Todo lo que Necesitas Saber Antes de Iniciar tu Formación
          </h2>
          <p className="text-xs leading-relaxed text-slate-600 dark:text-slate-400">
            Respuestas transparentes y directas a las dudas más comunes de ejecutivos y gerentes.
          </p>
        </div>

        <div className="max-w-3xl mx-auto space-y-3">
          {[
            {
              pregunta: '¿Qué sucede exactamente después de completar mi pago con izipay Online?',
              respuesta:
                'Tu matrícula se procesa de forma 100% automatizada. En menos de 60 segundos recibirás un correo de bienvenida con tus credenciales de acceso (usuario y contraseña) a nuestro Campus Virtual Moodle (campus.yunixingenieros.com). Podrás ingresar de inmediato a todas las videoclases, plantillas descargables y activar el Asesor Inteligente con IA.',
            },
            {
              pregunta: '¿Puedo solicitar Factura con RUC para deducir impuestos de mi empresa (SUNAT)?',
              respuesta:
                'Sí, por supuesto. Al momento de inscribirte en el Checkout, simplemente selecciona la opción "Empresa (Factura con RUC)" e ingresa el RUC y Razón Social de tu organización. Emitimos Factura Electrónica homologada por SUNAT, 100% válida para crédito fiscal y deducción de gastos de capacitación según la normativa tributaria peruana.',
            },
            {
              pregunta: '¿Qué pasa si mi agenda es muy apretada y no puedo ingresar todos los días?',
              respuesta:
                'Nuestros programas están diseñados específicamente para directores y gerentes con agendas demandantes. Toda la formación es 100% asincrónica y flexible: tienes acceso permanente y vitalicio a los contenidos y a las actualizaciones futuras de los módulos. Puedes avanzar a tu propio ritmo, en fines de semana o en sesiones de 30 minutos.',
            },
            {
              pregunta: '¿Cómo es el acompañamiento y quién resuelve mis dudas durante el curso?',
              respuesta:
                'Cuentas con acompañamiento dual: por un lado, tienes a Lic. Sofía Valdivia en WhatsApp para cualquier coordinación administrativa o emisión de certificados; y por otro lado, dispones de un Asesor Inteligente con IA entrenado con la bibliografía y casos prácticos de cada programa para resolver consultas de aplicación en tu negocio 24/7.',
            },
            {
              pregunta: '¿Qué garantía tengo si el programa no cumple mis expectativas directivas?',
              respuesta:
                'Tu inversión está 100% respaldada por nuestra Garantía de Satisfacción Total de 7 Días. Puedes explorar el campus, ver los primeros módulos y probar el material. Si por cualquier motivo consideras que el programa no aporta valor inmediato a tu perfil, te reembolsamos el 100% de tu dinero de inmediato mediante izipay Online. Sin preguntas incómodas ni trámites burocráticos.',
            },
          ].map((faq, index) => {
            const isOpen = openFaqIndex === index;
            return (
              <div
                key={index}
                className="rounded-2xl border border-slate-200/90 bg-white transition-all dark:border-slate-800 dark:bg-[#0d121c] overflow-hidden"
              >
                <button
                  type="button"
                  onClick={() => setOpenFaqIndex(isOpen ? null : index)}
                  className="w-full flex items-center justify-between p-5 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-900/50"
                >
                  <span className="text-xs sm:text-sm font-bold text-slate-900 dark:text-white pr-4">
                    {faq.pregunta}
                  </span>
                  <ChevronDown
                    className={`h-4 w-4 shrink-0 text-cyan-500 transition-transform duration-300 ${
                      isOpen ? 'rotate-180' : ''
                    }`}
                  />
                </button>
                {isOpen && (
                  <div className="px-5 pb-5 pt-1 text-xs text-slate-600 dark:text-slate-300 leading-relaxed border-t border-slate-100 dark:border-slate-800/80">
                    <p>{faq.respuesta}</p>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Footer de FAQ con contacto humano */}
        <div className="text-center pt-2">
          <p className="text-xs text-slate-400">
            ¿Tienes alguna consulta adicional antes de matricularte?{' '}
            <a
              href="https://wa.me/51941238905?text=Hola%20Sof%C3%ADa,%20tengo%20una%20consulta%20sobre%20mi%20matr%C3%ADcula%20en%20Lysandri"
              target="_blank"
              rel="noopener noreferrer"
              className="text-emerald-400 font-bold hover:underline inline-flex items-center gap-1"
            >
              <MessageCircle className="h-3.5 w-3.5" />
              Chatea con Sofía Valdivia en WhatsApp
            </a>
          </p>
        </div>
      </section>

      {/* Floating Concierge Widget (Bottom Right) */}
      <div className="fixed bottom-6 right-6 z-40 hidden sm:block">
        <a
          href="https://wa.me/51941238905?text=Hola%20Sof%C3%ADa,%20deseo%20orientaci%C3%B3n%20sobre%20los%20programas%20ejecutivos%20de%20Lysandri"
          target="_blank"
          rel="noopener noreferrer"
          className="group flex items-center gap-3 rounded-full border border-emerald-500/40 bg-slate-950/90 p-2 pr-4 shadow-2xl backdrop-blur-xl transition-all duration-300 hover:scale-105 hover:border-emerald-400 hover:shadow-emerald-500/25 active:scale-95"
        >
          <div className="relative">
            <div className="h-10 w-10 rounded-full bg-gradient-to-tr from-cyan-400 via-indigo-500 to-fuchsia-500 p-0.5">
              <div className="h-full w-full rounded-full bg-slate-900 flex items-center justify-center text-cyan-300 font-bold text-xs">
                SV
              </div>
            </div>
            <span className="absolute bottom-0 right-0 h-2.5 w-2.5 rounded-full bg-emerald-500 border-2 border-slate-950 animate-pulse" />
          </div>
          <div className="text-left text-xs">
            <div className="font-bold text-white group-hover:text-emerald-300 transition-colors flex items-center gap-1.5">
              <span>Lic. Sofía Valdivia</span>
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
            </div>
            <div className="text-[10px] text-slate-400 font-mono">
              Admisiones • En línea por WhatsApp
            </div>
          </div>
        </a>
      </div>


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
        onSelect={handleSelectPlaybook}
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
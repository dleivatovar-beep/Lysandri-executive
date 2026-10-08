import React, { useMemo, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  Bot,
  Building2,
  CheckCircle2,
  GraduationCap,
  LoaderCircle,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
} from 'lucide-react';
import architectureImage from '../assets/hero-arquitectura-editorial.png';
import { CategoryFilter } from '../components/marketplace/CategoryFilter';
import { ProgramRow } from '../components/marketplace/ProgramRow';
import { SearchBar } from '../components/marketplace/SearchBar';
import { CartDrawer } from '../components/marketplace/CartDrawer';
import { EditorialSyllabusModal } from '../components/marketplace/EditorialSyllabusModal';
import { ExecutiveChatDrawer } from '../components/chat/ExecutiveChatDrawer';
import { CorporateInCompanyModal } from '../components/marketplace/CorporateInCompanyModal';
import { Category, Playbook } from '../types';
import { useCart } from '../context/CartContext';
import './marketplace-editorial.css';

interface MarketplaceViewProps {
  playbooks: Playbook[];
  categories: Category[];
  isLoading?: boolean;
  errorMessage?: string;
  selectingId?: string | null;
  onRetry?: () => void;
  onSelectPlaybook?: (playbook: Playbook) => void | Promise<void>;
}

const TIERS = [
  { id: 'ALL', label: 'Todos los niveles' },
  { id: 'ENTERPRISE', label: 'Enterprise' },
  { id: 'ADVANCED', label: 'Advanced' },
  { id: 'ESSENTIAL', label: 'Essential' },
] as const;

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
  const [featuredIndex, setFeaturedIndex] = useState(0);
  const [featuredDirection, setFeaturedDirection] = useState<'next' | 'previous'>('next');
  const [isAiOpen, setIsAiOpen] = useState(false);
  const [isCorporateOpen, setIsCorporateOpen] = useState(false);
  const [selectedForSyllabus, setSelectedForSyllabus] = useState<Playbook | null>(null);
  const [selectedForAi, setSelectedForAi] = useState<Playbook | null>(null);

  const { cartItems, addToCart, removeFromCart, clearCart, isCartOpen, setIsCartOpen } = useCart();
  const featured = playbooks.length > 0 ? playbooks[featuredIndex % playbooks.length] : null;

  const filteredPlaybooks = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    return playbooks.filter((playbook) => {
      const matchesCategory = selectedCategory === 'ALL' || playbook.category === selectedCategory;
      const matchesTier = selectedTier === 'ALL' || playbook.tier === selectedTier;
      const matchesSearch = !query ||
        playbook.title.toLowerCase().includes(query) ||
        playbook.description.toLowerCase().includes(query) ||
        playbook.tags.some((tag) => tag.toLowerCase().includes(query)) ||
        Boolean(playbook.instructor?.toLowerCase().includes(query));
      return matchesCategory && matchesTier && matchesSearch;
    });
  }, [playbooks, searchQuery, selectedCategory, selectedTier]);

  const askAi = (playbook: Playbook) => {
    setSelectedForAi(playbook);
    setIsAiOpen(true);
  };

  const scrollToCatalog = () => document.getElementById('catalogo-section')?.scrollIntoView({ behavior: 'smooth' });
  const shiftFeatured = (step: number) => {
    if (playbooks.length < 2) return;
    setFeaturedDirection(step > 0 ? 'next' : 'previous');
    setFeaturedIndex((current) => (current + step + playbooks.length) % playbooks.length);
  };

  return (
    <div className="lx-marketplace">
      <section className="lx-hero" aria-labelledby="lx-hero-title">
        <div className="lx-hero-visual" aria-hidden="true">
          <img src={architectureImage} alt="" />
        </div>
        <div className="lx-hero-copy">
          <h1 id="lx-hero-title">Programas para <span>avanzar con propósito.</span></h1>
          <p>Explora la oferta académica, compara contenidos y encuentra el programa que mejor se adapta a tus objetivos profesionales.</p>
          <button type="button" className="lx-hero-cta" onClick={scrollToCatalog}>
            Explorar programas <ArrowRight aria-hidden="true" />
          </button>
          <div className="lx-hero-signature">Formación ejecutiva de Yunix Ingenieros E.I.R.L.</div>
        </div>

        <aside className="lx-feature" aria-label="Programa destacado">
          {featured ? (
            <>
              <div className="lx-feature-top">
                <span className="lx-feature-counter">{String((featuredIndex % playbooks.length) + 1).padStart(2, '0')} <span>/ {String(playbooks.length).padStart(2, '0')}</span></span>
                <div className="lx-feature-nav">
                  <button type="button" onClick={() => shiftFeatured(-1)} disabled={playbooks.length < 2} aria-label="Programa anterior"><ArrowLeft aria-hidden="true" /></button>
                  <button type="button" onClick={() => shiftFeatured(1)} disabled={playbooks.length < 2} aria-label="Programa siguiente"><ArrowRight aria-hidden="true" /></button>
                </div>
              </div>
              <div key={featured.id} className={`lx-feature-slide lx-feature-slide-${featuredDirection}`} aria-live="polite" aria-atomic="true">
                <div className="lx-feature-content">
                  <span className="lx-feature-category">{featured.category}{featured.isDemo && ' · Curso simulado'}</span>
                  <h2>{featured.title}</h2>
                  <p>{featured.description}</p>
                  {featured.duration && <span className="lx-feature-duration">{featured.duration}</span>}
                </div>
                <button type="button" className="lx-feature-cta" onClick={() => setSelectedForSyllabus(featured)}>
                  Ver sílabo <ArrowUpRight aria-hidden="true" />
                </button>
              </div>
            </>
          ) : (
            <div className="lx-feature-empty">Los programas aparecerán aquí cuando estén disponibles.</div>
          )}
        </aside>
      </section>

      <section id="catalogo-section" className="lx-catalog" aria-labelledby="lx-catalog-title">
        <div className="lx-catalog-heading">
          <div>
            <h2 id="lx-catalog-title">Explora los programas</h2>
            <p>Busca por tema, especialidad o nivel y revisa el sílabo antes de decidir.</p>
          </div>
          <SearchBar searchQuery={searchQuery} setSearchQuery={setSearchQuery} resultsCount={filteredPlaybooks.length} />
        </div>
        <div className="lx-catalog-filters">
          <CategoryFilter categories={categories} selectedCategory={selectedCategory} onSelectCategory={setSelectedCategory} />
          <div className="lx-tier-filter" aria-label="Filtrar por nivel">
            {TIERS.map((tier) => (
              <button key={tier.id} type="button" aria-pressed={selectedTier === tier.id} onClick={() => setSelectedTier(tier.id)}>
                {tier.label}
              </button>
            ))}
          </div>
        </div>

        {isLoading ? (
          <div className="lx-catalog-state" role="status"><LoaderCircle className="animate-spin" aria-hidden="true" /> Cargando programas...</div>
        ) : errorMessage ? (
          <div className="lx-catalog-state lx-catalog-error" role="alert">
            <ShieldAlert aria-hidden="true" />
            <div><strong>No se pudo cargar el catálogo</strong><p>{errorMessage}</p></div>
            {onRetry && <button type="button" onClick={onRetry}><RefreshCw aria-hidden="true" /> Reintentar</button>}
          </div>
        ) : filteredPlaybooks.length === 0 ? (
          <div className="lx-catalog-state">
            <BookOpen aria-hidden="true" />
            <div><strong>No hay programas con esos filtros</strong><p>Prueba otra búsqueda o vuelve a ver toda la oferta.</p></div>
            <button type="button" onClick={() => { setSearchQuery(''); setSelectedCategory('ALL'); setSelectedTier('ALL'); }}>Restablecer filtros</button>
          </div>
        ) : (
          <div className="lx-program-list">
            {filteredPlaybooks.map((playbook, index) => (
              <ProgramRow key={playbook.id} playbook={playbook} index={index} onPreviewSyllabus={setSelectedForSyllabus} onAddToCart={addToCart} onAskAi={askAi} />
            ))}
          </div>
        )}
      </section>

      <section id="metodologia-section" className="lx-method" aria-labelledby="lx-method-title">
        <div className="lx-method-heading">
          <h2 id="lx-method-title">Formación pensada para aplicar.</h2>
          <p>Una experiencia que conecta contenido, acompañamiento y verificación de lo aprendido.</p>
        </div>
        <div className="lx-method-grid">
          <article>
            <GraduationCap aria-hidden="true" />
            <h3>Campus Moodle 4.x + Bunny.net</h3>
            <p>Aulas virtuales sincronizadas mediante WebServices REST, con video, foros de debate y entrega de proyectos.</p>
            <span><CheckCircle2 aria-hidden="true" /> Acceso al campus tras el pago</span>
          </article>
          <article>
            <Bot aria-hidden="true" />
            <h3>Tutor RAG semántico</h3>
            <p>Recuperación semántica basada en los materiales de la plataforma para consultar dudas con referencias a las fuentes.</p>
            <span><CheckCircle2 aria-hidden="true" /> Consulta sobre el contenido</span>
          </article>
          <article>
            <ShieldCheck aria-hidden="true" />
            <h3>Certificación verificable</h3>
            <p>Diploma emitido por Yunix Ingenieros E.I.R.L. con un mecanismo público de verificación mediante código QR.</p>
            <span><CheckCircle2 aria-hidden="true" /> Verificación de autenticidad</span>
          </article>
        </div>
      </section>

      <section id="in-company-section" className="lx-corporate" aria-labelledby="lx-corporate-title">
        <div>
          <h2 id="lx-corporate-title">Capacitación para equipos de tecnología.</h2>
          <p>Diseñamos mallas a medida para organizaciones, con cohortes cerradas, seguimiento del progreso y consultoría aplicada.</p>
          <div className="lx-corporate-points">
            <span>Facturación para empresas</span><span>Cohortes cerradas</span><span>Seguimiento del aprendizaje</span>
          </div>
        </div>
        <button type="button" onClick={() => setIsCorporateOpen(true)}><Building2 aria-hidden="true" /> Solicitar información <ArrowUpRight aria-hidden="true" /></button>
      </section>

      <button type="button" className="lx-chat-launcher" onClick={() => setIsAiOpen(true)} aria-label="Abrir asistente de programas">
        <Bot aria-hidden="true" /><span>Consulta sobre un programa</span>
      </button>

      <CartDrawer isOpen={isCartOpen} onClose={() => setIsCartOpen(false)} cartItems={cartItems} onRemoveItem={removeFromCart} onClearCart={clearCart} />
      <EditorialSyllabusModal isOpen={Boolean(selectedForSyllabus)} onClose={() => setSelectedForSyllabus(null)} playbook={selectedForSyllabus} onAddToCart={addToCart} onAskAi={askAi} />
      <ExecutiveChatDrawer isOpen={isAiOpen} onClose={() => setIsAiOpen(false)} activePlaybook={selectedForAi} onClearContext={() => setSelectedForAi(null)} />
      <CorporateInCompanyModal isOpen={isCorporateOpen} onClose={() => setIsCorporateOpen(false)} />
    </div>
  );
};

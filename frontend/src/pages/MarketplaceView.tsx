import React, { useMemo, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  ArrowUpRight,
  Award,
  BookOpen,
  Bot,
  Building2,
  CheckCircle2,
  ChevronDown,
  GraduationCap,
  LoaderCircle,
  MessageCircle,
  Phone,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Star,
} from 'lucide-react';
import architectureImage from '../assets/hero-arquitectura-editorial.png';
import { CategoryFilter } from '../components/marketplace/CategoryFilter';
import { ProgramRow } from '../components/marketplace/ProgramRow';
import { SearchBar } from '../components/marketplace/SearchBar';
import { CartDrawer } from '../components/marketplace/CartDrawer';
import { EditorialSyllabusModal } from '../components/marketplace/EditorialSyllabusModal';
import { ExecutiveChatDrawer } from '../components/chat/ExecutiveChatDrawer';
import { CorporateInCompanyModal } from '../components/marketplace/CorporateInCompanyModal';
import { CheckoutModal } from '../components/marketplace/CheckoutModal';
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

const FACULTY_MENTORS = [
  {
    name: 'Dr. Carlos Mendoza',
    role: 'Ex-VP de Arquitectura en Credicorp',
    institution: 'PhD en Computación Distribuida (MIT)',
    avatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=240&q=80',
    bio: 'Más de 18 años liderando arquitecturas resilientes de alta concurrencia en la banca corporativa peruana.',
    tag: 'Arquitectura & Core Banking',
  },
  {
    name: 'Mg. Elena Rostova',
    role: 'Lead Data Scientist en Telefónica Tech',
    institution: 'MSc en Inteligencia Artificial (Oxford)',
    avatar: 'https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=240&q=80',
    bio: 'Pionera en despliegues de RAG semántico y LLMs gobernados para optimización operativa en telecomunicaciones.',
    tag: 'IA Aplicada & RAG',
  },
  {
    name: 'Ing. Roberto Takahashi',
    role: 'Principal Cloud Architect en AWS',
    institution: 'Ex-Head de DevOps en Globant',
    avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=240&q=80',
    bio: 'Especialista en observabilidad de misión crítica, Kubernetes multi-región y automatización CI/CD enterprise.',
    tag: 'Cloud & DevOps Enterprise',
  },
  {
    name: 'Dra. Mariana Benavides',
    role: 'Directora de Ciberseguridad en BCP',
    institution: 'CISSP, CISM, Stanford Executive',
    avatar: 'https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=240&q=80',
    bio: 'Responsable de la gobernanza de ciberseguridad, respuesta ante incidentes y cumplimiento regulatorio bancario.',
    tag: 'Ciberseguridad & Riesgos',
  },
];

const TESTIMONIALS = [
  {
    quote:
      'Lysandri Executive transformó radicalmente el estándar técnico de nuestros squads de pagos. La profundidad del sílabo y la aplicación directa a entornos de misión crítica redujeron nuestros incidentes un 42%.',
    author: 'Ing. Alejandro Morales',
    role: 'Vicepresidente Adjunto de Arquitectura',
    company: 'Banco de Crédito BCP',
    avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=240&q=80',
  },
  {
    quote:
      'Capacitamos a más de 35 ingenieros sénior a través del canal In-Company. La convalidación inmediata, el soporte en vivo y la facturación empresarial con RUC fueron impecables.',
    author: 'Mg. Patricia Seminario',
    role: 'Directora Corporativa de TI',
    company: 'Alicorp',
    avatar: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=240&q=80',
  },
  {
    quote:
      'La pasarela izipay y el proceso de matrícula con boleta electrónica fueron automáticos. En 10 minutos ya tenía mi acceso activo al Campus Moodle 4.x con Bunny.net y la certificación verificable.',
    author: 'Ing. Fernando Wong',
    role: 'Principal DevOps & Tech Lead',
    company: 'Interbank',
    avatar: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&w=240&q=80',
  },
];

const FAQ_ITEMS = [
  {
    question: '¿Qué métodos de pago aceptan y qué tan seguro es el proceso?',
    answer:
      'Aceptamos tarjetas de crédito y débito Visa, Mastercard, Diners y Amex mediante la pasarela segura izipay Online con certificación PCI-DSS Nivel 1 y 3D Secure. Además, puedes pagar al instante mediante Yape/Plin o transferencia bancaria en BCP e Interbank.',
  },
  {
    question: '¿Emiten Boleta o Factura electrónica con RUC para reembolso de empresa?',
    answer:
      'Sí, emitimos comprobantes electrónicos válidos ante SUNAT (Boleta o Factura con RUC). Durante el checkout puedes ingresar la Razón Social y RUC de tu empresa para remitirte el comprobante con valor tributario para deducibilidad o reembolso corporativo.',
  },
  {
    question: '¿Cuándo y cómo recibo el acceso al Campus Moodle 4.x y los videos?',
    answer:
      'Una vez confirmado el pago (inmediato con tarjeta, Yape o pasarela izipay), recibes tus credenciales de acceso automático por correo electrónico. Podrás ingresar al Campus Moodle 4.x con CDN global Bunny.net y consultar al Tutor RAG con IA las 24 horas.',
  },
  {
    question: '¿Cómo funciona la Garantía Institucional de 7 Días?',
    answer:
      'Si durante los primeros 7 días naturales tras tu matrícula consideras que el programa no cumple con tus expectativas de rigor técnico y aplicabilidad ejecutiva, te devolvemos el 100% de tu dinero sin preguntas ni trabas.',
  },
  {
    question: '¿Cómo puedo inscribir a mi equipo de tecnología o solicitar un programa In-Company?',
    answer:
      'Haz clic en el botón "Solicitar información" en la sección In-Company o conversa directamente con nuestra Asesora Senior de Admisiones Lic. Sofía Valdivia por WhatsApp. Estructuramos cohortes cerradas a medida de tu stack institucional.',
  },
];

export const MarketplaceView: React.FC<MarketplaceViewProps> = ({
  playbooks,
  categories,
  isLoading = false,
  errorMessage = '',
  onRetry,
  onSelectPlaybook,
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
  const [selectedForCheckout, setSelectedForCheckout] = useState<Playbook | null>(null);
  const [openFaqIndex, setOpenFaqIndex] = useState<number | null>(null);

  const { cartItems, addToCart, removeFromCart, clearCart, isCartOpen, setIsCartOpen } = useCart();
  const featured = playbooks.length > 0 ? playbooks[featuredIndex % playbooks.length] : null;

  const filteredPlaybooks = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    return playbooks.filter((playbook) => {
      const matchesCategory = selectedCategory === 'ALL' || playbook.category === selectedCategory;
      const matchesTier = selectedTier === 'ALL' || playbook.tier === selectedTier;
      const matchesSearch =
        !query ||
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

  const handleEnroll = (playbook: Playbook) => {
    if (onSelectPlaybook) {
      void onSelectPlaybook(playbook);
    }
    setSelectedForCheckout(playbook);
  };

  const scrollToCatalog = () =>
    document.getElementById('catalogo-section')?.scrollIntoView({ behavior: 'smooth' });

  const shiftFeatured = (step: number) => {
    if (playbooks.length < 2) return;
    setFeaturedDirection(step > 0 ? 'next' : 'previous');
    setFeaturedIndex((current) => (current + step + playbooks.length) % playbooks.length);
  };

  return (
    <div className="lx-marketplace">
      {/* 1. HERO EDITORIAL DE JARED */}
      <section className="lx-hero" aria-labelledby="lx-hero-title">
        <div className="lx-hero-visual" aria-hidden="true">
          <img src={architectureImage} alt="" />
        </div>
        <div className="lx-hero-copy">
          <h1 id="lx-hero-title">
            Programas para <span>avanzar con propósito.</span>
          </h1>
          <p>
            Explora la oferta académica, compara contenidos y encuentra el programa que mejor se adapta a tus
            objetivos profesionales con acreditación ejecutiva.
          </p>
          <button type="button" className="lx-hero-cta" onClick={scrollToCatalog}>
            Explorar programas <ArrowRight aria-hidden="true" />
          </button>
          <div className="lx-hero-signature">Tecnología aplicada. Liderazgo ejecutivo.</div>
        </div>

        <aside className="lx-feature" aria-label="Programa destacado">
          {featured ? (
            <>
              <div className="lx-feature-top">
                <span className="lx-feature-counter">Programa destacado</span>
                <div className="lx-feature-nav">
                  <button
                    type="button"
                    onClick={() => shiftFeatured(-1)}
                    disabled={playbooks.length < 2}
                    aria-label="Programa anterior"
                  >
                    <ArrowLeft aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    onClick={() => shiftFeatured(1)}
                    disabled={playbooks.length < 2}
                    aria-label="Programa siguiente"
                  >
                    <ArrowRight aria-hidden="true" />
                  </button>
                </div>
              </div>
              <div
                className="lx-feature-progress"
                role="progressbar"
                aria-label="Posición en programas destacados"
                aria-valuemin={1}
                aria-valuemax={playbooks.length}
                aria-valuenow={(featuredIndex % playbooks.length) + 1}
              >
                <span
                  style={{
                    transform: `scaleX(${((featuredIndex % playbooks.length) + 1) / playbooks.length})`,
                  }}
                />
              </div>
              <div
                key={featured.id}
                className={`lx-feature-slide lx-feature-slide-${featuredDirection}`}
                aria-live="polite"
                aria-atomic="true"
              >
                <div className="lx-feature-content">
                  <span className="lx-feature-category">
                    {featured.category}
                    {featured.isDemo && ' · Curso simulado'}
                  </span>
                  <h2>{featured.title}</h2>
                  <p>{featured.description}</p>
                  {featured.duration && <span className="lx-feature-duration">{featured.duration}</span>}
                </div>
                <button
                  type="button"
                  className="lx-feature-cta"
                  onClick={() => setSelectedForSyllabus(featured)}
                >
                  Ver sílabo <ArrowUpRight aria-hidden="true" />
                </button>
              </div>
            </>
          ) : (
            <div className="lx-feature-empty">Los programas aparecerán aquí cuando estén disponibles.</div>
          )}
        </aside>
      </section>

      {/* 2. CONCIERGE EJECUTIVO HUMANO (LIC. SOFÍA VALDIVIA) */}
      <section className="lx-concierge-strip" aria-label="Atención personalizada de admisiones">
        <div className="lx-concierge-info">
          <div className="lx-concierge-avatar">
            <img
              src="https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=240&q=80"
              alt="Lic. Sofía Valdivia"
            />
            <span className="lx-concierge-online-dot" title="En línea ahora" />
          </div>
          <div className="lx-concierge-text">
            <h4>
              Lic. Sofía Valdivia <span className="lx-demo-label">— Asesora Senior de Admisiones</span>
            </h4>
            <p>
              ¿Dudas sobre convalidación de mallas, pasarela de pago izipay o facturación corporativa? Te atiendo en
              directo.
            </p>
          </div>
        </div>
        <div className="lx-concierge-actions">
          <a
            href="https://wa.me/51987654321?text=Hola%20Sof%C3%ADa%2C%20quisiera%20asesor%C3%ADa%20personalizada%20sobre%20los%20programas%20ejecutivos%20de%20Lysandri."
            target="_blank"
            rel="noopener noreferrer"
            className="lx-concierge-btn-wa"
          >
            <MessageCircle aria-hidden="true" style={{ width: '1.05rem', height: '1.05rem' }} />
            <span>Chat WhatsApp</span>
          </a>
          <a href="tel:+51987654321" className="lx-concierge-btn-phone">
            <Phone aria-hidden="true" style={{ width: '0.95rem', height: '0.95rem' }} />
            <span>+51 987 654 321</span>
          </a>
        </div>
      </section>

      {/* 3. CATÁLOGO EDITORIAL DE JARED CON ENROLLMENT DIRECTO */}
      <section id="catalogo-section" className="lx-catalog" aria-labelledby="lx-catalog-title">
        <div className="lx-catalog-heading">
          <div>
            <h2 id="lx-catalog-title">Explora los programas</h2>
            <p>Busca por tema, especialidad o nivel y revisa el sílabo antes de decidir.</p>
          </div>
          <SearchBar
            searchQuery={searchQuery}
            setSearchQuery={setSearchQuery}
            resultsCount={filteredPlaybooks.length}
          />
        </div>
        <div className="lx-catalog-filters">
          <CategoryFilter
            categories={categories}
            selectedCategory={selectedCategory}
            onSelectCategory={setSelectedCategory}
          />
          <div className="lx-tier-filter" aria-label="Filtrar por nivel">
            {TIERS.map((tier) => (
              <button
                key={tier.id}
                type="button"
                aria-pressed={selectedTier === tier.id}
                onClick={() => setSelectedTier(tier.id)}
              >
                {tier.label}
              </button>
            ))}
          </div>
        </div>

        {isLoading ? (
          <div className="lx-catalog-state" role="status">
            <LoaderCircle className="animate-spin" aria-hidden="true" /> Cargando programas...
          </div>
        ) : errorMessage ? (
          <div className="lx-catalog-state lx-catalog-error" role="alert">
            <ShieldAlert aria-hidden="true" />
            <div>
              <strong>No se pudo cargar el catálogo</strong>
              <p>{errorMessage}</p>
            </div>
            {onRetry && (
              <button type="button" onClick={onRetry}>
                <RefreshCw aria-hidden="true" /> Reintentar
              </button>
            )}
          </div>
        ) : filteredPlaybooks.length === 0 ? (
          <div className="lx-catalog-state">
            <BookOpen aria-hidden="true" />
            <div>
              <strong>No hay programas con esos filtros</strong>
              <p>Prueba otra búsqueda o vuelve a ver toda la oferta.</p>
            </div>
            <button
              type="button"
              onClick={() => {
                setSearchQuery('');
                setSelectedCategory('ALL');
                setSelectedTier('ALL');
              }}
            >
              Restablecer filtros
            </button>
          </div>
        ) : (
          <div className="lx-program-list">
            {filteredPlaybooks.map((playbook) => (
              <ProgramRow
                key={playbook.id}
                playbook={playbook}
                onPreviewSyllabus={setSelectedForSyllabus}
                onAddToCart={addToCart}
                onAskAi={askAi}
                onEnroll={handleEnroll}
              />
            ))}
          </div>
        )}
      </section>

      {/* 4. METODOLOGÍA EDITORIAL DE JARED */}
      <section id="metodologia-section" className="lx-method" aria-labelledby="lx-method-title">
        <div className="lx-method-heading">
          <h2 id="lx-method-title">Formación pensada para aplicar.</h2>
          <p>Una experiencia que conecta contenido, acompañamiento y verificación de lo aprendido.</p>
        </div>
        <div className="lx-method-grid">
          <article>
            <GraduationCap aria-hidden="true" />
            <h3>Campus Moodle 4.x + Bunny.net</h3>
            <p>
              Aulas virtuales sincronizadas mediante WebServices REST, con video de alta definición, foros de debate y
              entrega de proyectos de código.
            </p>
            <span>
              <CheckCircle2 aria-hidden="true" /> Acceso al campus tras el pago
            </span>
          </article>
          <article>
            <Bot aria-hidden="true" />
            <h3>Tutor RAG semántico</h3>
            <p>
              Recuperación semántica basada en los materiales de la plataforma para consultar dudas con referencias
              exactas a las fuentes de código.
            </p>
            <span>
              <CheckCircle2 aria-hidden="true" /> Consulta sobre el contenido
            </span>
          </article>
          <article>
            <ShieldCheck aria-hidden="true" />
            <h3>Certificación verificable</h3>
            <p>
              Diploma digital con código QR criptográfico para que empleadores y auditores verifiquen su autenticidad.
            </p>
            <span>
              <CheckCircle2 aria-hidden="true" /> Verificación de autenticidad
            </span>
          </article>
        </div>
      </section>

      {/* 5. CLAUSTRO DOCENTE Y MENTORES EJECUTIVOS */}
      <section className="lx-mentors" aria-labelledby="lx-mentors-title">
        <div className="lx-mentors-heading">
          <h2 id="lx-mentors-title">Claustro Docente y Mentores Ejecutivos</h2>
          <p>Profesionales de primer nivel en activo en las principales organizaciones tecnológicas y financieras.</p>
        </div>
        <div className="lx-mentors-grid">
          {FACULTY_MENTORS.map((mentor) => (
            <article key={mentor.name} className="lx-mentor-card">
              <div className="lx-mentor-header">
                <img src={mentor.avatar} alt={mentor.name} className="lx-mentor-avatar" loading="lazy" />
                <div>
                  <h3 className="lx-mentor-name">{mentor.name}</h3>
                  <p className="lx-mentor-role">{mentor.role}</p>
                </div>
              </div>
              <p className="lx-mentor-bio">{mentor.bio}</p>
              <span className="lx-mentor-tag">
                <Award aria-hidden="true" style={{ width: '0.85rem', height: '0.85rem' }} />
                {mentor.tag}
              </span>
            </article>
          ))}
        </div>
      </section>

      {/* 6. TESTIMONIOS EJECUTIVOS */}
      <section className="lx-testimonials" aria-labelledby="lx-testimonials-title">
        <div className="lx-mentors-heading" style={{ marginBottom: '1rem' }}>
          <h2 id="lx-testimonials-title">Historias de Éxito Ejecutivo</h2>
          <p>Decisores y líderes tecnológicos que aceleraron su carrera con Lysandri Executive.</p>
        </div>
        <div className="lx-testimonials-grid">
          {TESTIMONIALS.map((testimonial, idx) => (
            <article key={idx} className="lx-testimonial-card">
              <div className="lx-testimonial-stars">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} style={{ width: '1rem', height: '1rem', fill: 'currentColor' }} />
                ))}
              </div>
              <blockquote className="lx-testimonial-quote">"{testimonial.quote}"</blockquote>
              <div className="lx-testimonial-author">
                <img
                  src={testimonial.avatar}
                  alt={testimonial.author}
                  className="lx-testimonial-avatar"
                  loading="lazy"
                />
                <div>
                  <h4 className="lx-testimonial-author-name">{testimonial.author}</h4>
                  <p className="lx-testimonial-author-company">
                    {testimonial.role} · {testimonial.company}
                  </p>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>

      {/* 7. GARANTÍA DE SATISFACCIÓN Y PREGUNTAS FRECUENTES */}
      <section className="lx-guarantee-section" aria-labelledby="lx-guarantee-title">
        <div className="lx-guarantee-banner">
          <div className="lx-guarantee-seal">
            100%<br />Garantía<br />7 Días
          </div>
          <div className="lx-guarantee-info">
            <h3 id="lx-guarantee-title">Garantía Institucional de Satisfacción de 7 Días</h3>
            <p>
              Inscríbete con total confianza. Si en tus primeros 7 días naturales consideras que el programa no cumple
              tus expectativas técnicas o directivas, te reembolsamos el 100% de tu matrícula sin preguntas.
            </p>
          </div>
        </div>

        <div className="lx-faq-container">
          <div style={{ marginBottom: '2rem', textAlign: 'center' }}>
            <h2
              style={{
                fontFamily: "'Barlow Condensed', Bahnschrift, 'Arial Narrow', sans-serif",
                fontSize: '2.5rem',
                fontWeight: 700,
              }}
            >
              Preguntas Frecuentes
            </h2>
            <p style={{ color: 'var(--lx-muted)', fontSize: '0.9rem', marginTop: '0.3rem' }}>
              Todo lo que necesitas saber sobre matrícula, pasarela izipay y accesos.
            </p>
          </div>

          <div className="lx-faq-list">
            {FAQ_ITEMS.map((item, index) => {
              const isOpen = openFaqIndex === index;
              return (
                <div key={index} className="lx-faq-item">
                  <button
                    type="button"
                    className="lx-faq-trigger"
                    aria-expanded={isOpen}
                    onClick={() => setOpenFaqIndex(isOpen ? null : index)}
                  >
                    <span>{item.question}</span>
                    <ChevronDown aria-hidden="true" />
                  </button>
                  {isOpen && <div className="lx-faq-content">{item.answer}</div>}
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* 8. IN-COMPANY CORPORATE DE JARED */}
      <section id="in-company-section" className="lx-corporate" aria-labelledby="lx-corporate-title">
        <div>
          <h2 id="lx-corporate-title">Capacitación para equipos de tecnología.</h2>
          <p>
            Diseñamos mallas a medida para organizaciones, con cohortes cerradas, seguimiento del progreso y
            consultoría aplicada.
          </p>
          <div className="lx-corporate-points">
            <span>Facturación para empresas con RUC</span>
            <span>Cohortes cerradas</span>
            <span>Seguimiento del aprendizaje</span>
          </div>
        </div>
        <button type="button" onClick={() => setIsCorporateOpen(true)}>
          <Building2 aria-hidden="true" /> Solicitar información <ArrowUpRight aria-hidden="true" />
        </button>
      </section>

      {/* BOTÓN FLOTANTE WHATSAPP CONCIERGE */}
      <a
        href="https://wa.me/51987654321?text=Hola%20Sof%C3%ADa%2C%20quisiera%20consultar%20sobre%20la%20matr%C3%ADcula%20y%20programas%20de%20Lysandri."
        target="_blank"
        rel="noopener noreferrer"
        className="lx-whatsapp-floating"
        aria-label="Atención por WhatsApp con Lic. Sofía Valdivia"
      >
        <MessageCircle aria-hidden="true" />
        <span>¿Dudas? Sofía te ayuda</span>
      </a>

      {/* BOTÓN FLOTANTE ASISTENTE IA DE JARED */}
      <button
        type="button"
        className="lx-chat-launcher"
        onClick={() => setIsAiOpen(true)}
        aria-label="Abrir asistente de programas"
      >
        <Bot aria-hidden="true" />
        <span>Consulta sobre un programa</span>
      </button>

      {/* DRAWERS Y MODALES */}
      <CartDrawer
        isOpen={isCartOpen}
        onClose={() => setIsCartOpen(false)}
        cartItems={cartItems}
        onRemoveItem={removeFromCart}
        onClearCart={clearCart}
      />

      <EditorialSyllabusModal
        isOpen={Boolean(selectedForSyllabus)}
        onClose={() => setSelectedForSyllabus(null)}
        playbook={selectedForSyllabus}
        onAddToCart={addToCart}
        onAskAi={askAi}
        onEnroll={handleEnroll}
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

      <CheckoutModal
        isOpen={Boolean(selectedForCheckout)}
        onClose={() => setSelectedForCheckout(null)}
        playbook={selectedForCheckout}
      />
    </div>
  );
};
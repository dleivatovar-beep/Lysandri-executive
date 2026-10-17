import React, { useEffect, useState } from 'react';
import { ArrowUpRight, LogIn, LogOut, Menu, Moon, ShoppingBag, Sun, X } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import companyLogo from '../../assets/mi-logo.png';
import { useCart } from '../../context/CartContext';
import { getRoleHomePath } from '../../routes/RoleProtectedRoute';
import { UserProfile } from '../../types';

export interface NavbarProps {
  user?: UserProfile | null;
  theme: 'light' | 'dark';
  onThemeToggle: () => void;
  onLoginClick?: () => void;
  onLogoutClick?: () => void;
  onOpenAiChat?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  user,
  theme,
  onThemeToggle,
  onLoginClick,
  onLogoutClick,
  onOpenAiChat,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { itemCount, openCart } = useCart();
  const [activeNav, setActiveNav] = useState('catalogo-section');
  const [isPublicMenuOpen, setIsPublicMenuOpen] = useState(false);
  const isAdmin = user?.role === 'ADMIN';
  const adminSection = location.pathname.includes('/admin/contabilidad') || location.pathname.includes('/admin/reportes')
    ? 'Área contable'
    : location.pathname.includes('/admin/inscripciones')
      ? 'Inscripciones'
      : location.pathname.includes('/admin/solicitudes-informacion')
        ? 'Solicitudes'
        : location.pathname.includes('/admin/usuarios')
          ? 'Usuarios'
          : 'Cursos';

  const scrollToSection = (id: string) => {
    setActiveNav(id);
    setIsPublicMenuOpen(false);
    if (window.location.pathname !== '/') {
      navigate(`/#${id}`);
      window.setTimeout(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' }), 80);
      return;
    }

    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (isAdmin || location.pathname !== '/' || typeof IntersectionObserver === 'undefined') return;
    const targets = ['catalogo-section', 'metodologia-section', 'in-company-section']
      .map((id) => document.getElementById(id))
      .filter((element): element is HTMLElement => Boolean(element));
    if (!targets.length) return;

    const observer = new IntersectionObserver((entries) => {
      const current = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (current?.target.id) setActiveNav(current.target.id);
    }, { rootMargin: '-18% 0px -58% 0px', threshold: [0, 0.15, 0.35] });
    targets.forEach((target) => observer.observe(target));
    return () => observer.disconnect();
  }, [isAdmin, location.pathname]);

  const openAssistant = () => {
    setIsPublicMenuOpen(false);
    if (onOpenAiChat) {
      onOpenAiChat();
    } else {
      window.dispatchEvent(new CustomEvent('open-executive-ai-chat'));
    }
  };

  return (
    <header className={`sticky top-0 z-40${isAdmin ? ' lx-admin-navbar' : ' lx-public-navbar'}`}>
      <div className="mx-auto flex h-[72px] w-full max-w-[1920px] items-center gap-2 px-3 sm:gap-4 sm:px-6 lg:px-8">
        <Link to="/" className="group flex min-w-0 shrink-0 items-center gap-2 rounded-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-cyan-500 sm:gap-3" aria-label="Lysandri Executive, ir al inicio">
          <img src={companyLogo} alt="" className="h-8 w-8 object-contain sm:h-10 sm:w-10" />
          <span className="flex min-w-0 flex-col leading-none">
            <span className="text-[13px] font-extrabold tracking-[0.05em] sm:text-[15px] sm:tracking-[0.08em]">LYSANDRI</span>
            <span className="mt-1 hidden text-[10px] font-semibold uppercase tracking-[0.17em] text-slate-500 dark:text-slate-400 sm:block">Executive</span>
          </span>
        </Link>

        {user?.role === 'ADMIN' && (
          <div className="lx-admin-top-context" aria-label={`Administración, ${adminSection}`}>
            <span>ESPACIO OPERATIVO</span>
            <strong>{adminSection}</strong>
          </div>
        )}

        {!isAdmin && <nav aria-label="Navegación principal" className="lx-public-nav ml-auto hidden items-center gap-1 lg:flex">
          <button type="button" onClick={() => scrollToSection('catalogo-section')} className={`lx-public-nav-link${activeNav === 'catalogo-section' ? ' is-active' : ''}`} aria-current={activeNav === 'catalogo-section' ? 'location' : undefined}>Programas</button>
          <button type="button" onClick={() => scrollToSection('metodologia-section')} className={`lx-public-nav-link${activeNav === 'metodologia-section' ? ' is-active' : ''}`} aria-current={activeNav === 'metodologia-section' ? 'location' : undefined}>Metodología</button>
          <button type="button" onClick={() => scrollToSection('in-company-section')} className={`lx-public-nav-link${activeNav === 'in-company-section' ? ' is-active' : ''}`} aria-current={activeNav === 'in-company-section' ? 'location' : undefined}>Para empresas</button>
          <button type="button" onClick={openAssistant} className="lx-public-nav-link">Asistente</button>
        </nav>}

        <div className={`ml-auto flex shrink-0 items-center gap-1 sm:gap-2${user?.role === 'ADMIN' ? ' lg:ml-auto' : ' lg:ml-4'}`}>
          {!isAdmin && (
            <button
              type="button"
              className="lx-public-menu-toggle lg:hidden"
              onClick={() => setIsPublicMenuOpen((open) => !open)}
              aria-label={isPublicMenuOpen ? 'Cerrar menú' : 'Abrir menú'}
              aria-expanded={isPublicMenuOpen}
              aria-controls="lx-public-mobile-nav"
            >
              {isPublicMenuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
            </button>
          )}
          {user?.role !== 'ADMIN' && <button type="button" onClick={openCart} aria-label={`Abrir carrito, ${itemCount} productos`} className="relative flex h-10 items-center gap-2 rounded-md px-2 text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white sm:px-3">
            <ShoppingBag className="h-[19px] w-[19px]" aria-hidden="true" />
            <span className="hidden text-sm font-medium sm:inline">Carrito</span>
            {itemCount > 0 && <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-cyan-600 px-1 text-[11px] font-bold text-white">{itemCount}</span>}
          </button>}
          <button type="button" onClick={onThemeToggle} aria-label={theme === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'} className="flex h-10 w-10 items-center justify-center rounded-md text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white">
            {theme === 'dark' ? <Sun className="h-[19px] w-[19px]" /> : <Moon className="h-[19px] w-[19px]" />}
          </button>
          {user ? (
            <>
              {user.role === 'ADMIN' ? (
                <div className="lx-admin-user-label">
                  <span>{user.name}</span>
                  <small>Administrador</small>
                </div>
              ) : (
                <button type="button" onClick={() => navigate(getRoleHomePath(user.role))} className="lysandri-nav-primary flex h-10 items-center gap-2 rounded-md bg-[#112b45] px-3 text-sm font-semibold text-white transition-colors hover:bg-[#174266] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-500 dark:bg-cyan-400 dark:text-[#071725] dark:hover:bg-cyan-300">
                  <span className="max-w-28 truncate sm:max-w-40">{user.name}</span>
                  <ArrowUpRight className="hidden h-4 w-4 sm:block" aria-hidden="true" />
                </button>
              )}
              <button type="button" onClick={onLogoutClick} aria-label="Cerrar sesión" title="Cerrar sesión" className={`flex h-10 items-center justify-center gap-2 rounded-md text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white${user.role === 'ADMIN' ? ' lx-admin-logout-button' : ' w-10'}`}>
                {user.role === 'ADMIN' ? (
                  <span>Salir</span>
                ) : (
                  <LogOut className="h-[19px] w-[19px]" aria-hidden="true" />
                )}
              </button>
            </>
          ) : (
            <button type="button" onClick={onLoginClick} className="lysandri-nav-primary flex h-10 items-center gap-2 rounded-md bg-[#112b45] px-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#174266] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-500 dark:bg-cyan-400 dark:text-[#071725] dark:hover:bg-cyan-300 sm:px-4 sm:text-sm">
              <LogIn className="hidden h-4 w-4 sm:block" aria-hidden="true" />
              <span>Iniciar sesión</span>
            </button>
          )}
        </div>
      </div>
      {!isAdmin && isPublicMenuOpen && (
        <nav id="lx-public-mobile-nav" className="lx-public-mobile-nav lg:hidden" aria-label="Navegación móvil">
          <button type="button" className={activeNav === 'catalogo-section' ? 'is-active' : ''} onClick={() => scrollToSection('catalogo-section')}>Programas</button>
          <button type="button" className={activeNav === 'metodologia-section' ? 'is-active' : ''} onClick={() => scrollToSection('metodologia-section')}>Metodología</button>
          <button type="button" className={activeNav === 'in-company-section' ? 'is-active' : ''} onClick={() => scrollToSection('in-company-section')}>Para empresas</button>
          <button type="button" onClick={openAssistant}>Asistente</button>
        </nav>
      )}
    </header>
  );
};

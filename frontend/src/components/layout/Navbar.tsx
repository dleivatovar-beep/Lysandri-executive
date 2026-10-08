import React from 'react';
import { ArrowUpRight, LogIn, LogOut, Moon, ShoppingBag, Sun } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';

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
  const { itemCount, openCart } = useCart();

  const scrollToSection = (id: string) => {
    if (window.location.pathname !== '/') {
      navigate(`/#${id}`);
      window.setTimeout(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' }), 80);
      return;
    }

    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
  };

  const openAssistant = () => {
    if (onOpenAiChat) {
      onOpenAiChat();
    } else {
      window.dispatchEvent(new CustomEvent('open-executive-ai-chat'));
    }
  };

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 text-slate-900 backdrop-blur-sm dark:border-[#203047] dark:bg-[#081321]/95 dark:text-slate-100">
      <div className="mx-auto flex h-[72px] w-full max-w-[1920px] items-center gap-2 px-3 sm:gap-4 sm:px-6 lg:px-8">
        <Link to="/" className="group flex min-w-0 shrink-0 items-center gap-2 rounded-md focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-cyan-500 sm:gap-3" aria-label="Lysandri Executive, ir al inicio">
          <img src={companyLogo} alt="" className="h-8 w-8 object-contain sm:h-10 sm:w-10" />
          <span className="flex min-w-0 flex-col leading-none">
            <span className="text-[13px] font-extrabold tracking-[0.05em] sm:text-[15px] sm:tracking-[0.08em]">LYSANDRI</span>
            <span className="mt-1 hidden text-[10px] font-semibold uppercase tracking-[0.17em] text-slate-500 dark:text-slate-400 sm:block">Executive</span>
          </span>
        </Link>

        <nav aria-label="Navegación principal" className="ml-auto hidden items-center gap-1 lg:flex">
          <button type="button" onClick={() => scrollToSection('catalogo-section')} className="rounded-md px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white">Programas</button>
          <button type="button" onClick={() => scrollToSection('metodologia-section')} className="rounded-md px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white">Metodología</button>
          <button type="button" onClick={() => scrollToSection('in-company-section')} className="rounded-md px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white">Para empresas</button>
          <button type="button" onClick={openAssistant} className="rounded-md px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white">Asistente</button>
        </nav>

        <div className="ml-auto flex shrink-0 items-center gap-1 sm:gap-2 lg:ml-4">
          <button type="button" onClick={openCart} aria-label={`Abrir carrito, ${itemCount} productos`} className="relative flex h-10 items-center gap-2 rounded-md px-2 text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white sm:px-3">
            <ShoppingBag className="h-[19px] w-[19px]" aria-hidden="true" />
            <span className="hidden text-sm font-medium sm:inline">Carrito</span>
            {itemCount > 0 && <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-cyan-600 px-1 text-[11px] font-bold text-white">{itemCount}</span>}
          </button>
          <button type="button" onClick={onThemeToggle} aria-label={theme === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'} className="flex h-10 w-10 items-center justify-center rounded-md text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white">
            {theme === 'dark' ? <Sun className="h-[19px] w-[19px]" /> : <Moon className="h-[19px] w-[19px]" />}
          </button>
          {user ? (
            <>
              <button type="button" onClick={() => navigate(getRoleHomePath(user.role))} className="lysandri-nav-primary flex h-10 items-center gap-2 rounded-md bg-[#112b45] px-3 text-sm font-semibold text-white transition-colors hover:bg-[#174266] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cyan-500 dark:bg-cyan-400 dark:text-[#071725] dark:hover:bg-cyan-300">
                <span className="max-w-28 truncate sm:max-w-40">{user.name}</span>
                <ArrowUpRight className="hidden h-4 w-4 sm:block" aria-hidden="true" />
              </button>
              <button type="button" onClick={onLogoutClick} aria-label="Cerrar sesión" title="Cerrar sesión" className="flex h-10 w-10 items-center justify-center rounded-md text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-950 focus-visible:outline focus-visible:outline-2 focus-visible:outline-cyan-500 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white">
                <LogOut className="h-[19px] w-[19px]" />
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
    </header>
  );
};

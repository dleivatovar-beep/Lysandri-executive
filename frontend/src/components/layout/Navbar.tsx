import React, {
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  ChevronDown,
  LogIn,
  LogOut,
  Moon,
  Sun,
  UserRound,
  ShoppingCart,
  Sparkles,
  Building2,
} from 'lucide-react';

import companyLogo from '../../assets/mi-logo.png';
import { UserProfile, UserRole } from '../../types';
import { useCart } from '../../context/CartContext';

interface NavbarProps {
  user: UserProfile | null;
  theme: 'light' | 'dark';
  onThemeToggle: () => void;
  onLoginClick: () => void;
  onLogoutClick: () => void;
  onOpenAiChat?: () => void;
}

const ROLE_LABELS: Record<UserRole, string> = {
  ESTUDIANTE: 'Ejecutivo',
  INSTRUCTOR: 'Profesor',
  ADMIN: 'Administrador',
};

export const Navbar: React.FC<NavbarProps> = ({
  user,
  theme,
  onThemeToggle,
  onLoginClick,
  onLogoutClick,
  onOpenAiChat,
}) => {
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  // Safe access to cart context
  let cartCount = 0;
  let openCartFn = () => {};
  try {
    const cart = useCart();
    cartCount = cart.itemCount;
    openCartFn = cart.openCart;
  } catch {
    // If rendered outside CartProvider, gracefully degrade
  }

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        userMenuRef.current &&
        !userMenuRef.current.contains(event.target as Node)
      ) {
        setIsUserMenuOpen(false);
      }
    };

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsUserMenuOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, []);

  useEffect(() => {
    if (!user) {
      setIsUserMenuOpen(false);
    }
  }, [user]);

  const handleLogout = () => {
    setIsUserMenuOpen(false);
    onLogoutClick();
  };

  const scrollToSection = (id: string) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <header className="sticky top-0 z-40 h-16 border-b border-cyan-500/10 bg-white/95 shadow-[0_1px_25px_rgba(6,182,212,0.06)] backdrop-blur-xl dark:border-slate-800/80 dark:bg-[#070a10]/95">
      <div className="flex h-full w-full items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Brand / Logo */}
        <div className="flex min-w-0 items-center gap-3">
          <a href="/" className="group flex items-center gap-3">
            <div className="relative flex h-10 w-10 shrink-0 items-center justify-center">
              <div className="absolute inset-0 rounded-xl bg-cyan-400/20 opacity-70 blur-lg group-hover:opacity-100 transition-opacity" />
              <img
                src={companyLogo}
                alt="Logo de Lysandri"
                className="relative h-10 w-10 object-contain drop-shadow-[0_0_8px_rgba(34,211,238,0.35)]"
              />
            </div>

            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="truncate text-base font-extrabold tracking-wide text-slate-900 dark:text-white">
                  LYSANDRI
                </span>
                <span className="rounded-md border border-cyan-500/30 bg-cyan-500/10 px-2 py-0.5 font-mono text-[9px] font-bold tracking-wider text-cyan-600 dark:text-cyan-400">
                  EXECUTIVE
                </span>
              </div>
              <span className="hidden font-mono text-[9px] tracking-tight text-slate-500 dark:text-slate-400 sm:block">
                by Yunix Ingenieros E.I.R.L.
              </span>
            </div>
          </a>
        </div>

        {/* Center Navigation Links (Desktop) */}
        <nav className="hidden items-center gap-1 md:flex lg:gap-2">
          <button
            type="button"
            onClick={() => scrollToSection('catalogo-section')}
            className="rounded-lg px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-cyan-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:text-cyan-400 dark:hover:bg-slate-800/50 transition-colors"
          >
            Programas & Certificaciones
          </button>

          <button
            type="button"
            onClick={() => scrollToSection('metodologia-section')}
            className="rounded-lg px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-cyan-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:text-cyan-400 dark:hover:bg-slate-800/50 transition-colors"
          >
            Metodología Moodle 4.x
          </button>

          <button
            type="button"
            onClick={() => scrollToSection('in-company-section')}
            className="rounded-lg px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-indigo-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:text-indigo-400 dark:hover:bg-slate-800/50 transition-colors flex items-center gap-1.5"
          >
            <Building2 className="h-3.5 w-3.5 text-indigo-400" />
            <span>Para Empresas (B2B)</span>
          </button>

          {onOpenAiChat && (
            <button
              type="button"
              onClick={onOpenAiChat}
              className="rounded-lg px-3 py-1.5 text-xs font-semibold text-cyan-600 hover:bg-cyan-50 dark:text-cyan-300 dark:hover:bg-cyan-950/40 transition-colors flex items-center gap-1.5"
            >
              <Sparkles className="h-3.5 w-3.5 text-cyan-400" />
              <span>Asistente RAG</span>
            </button>
          )}
        </nav>

        {/* Right Actions: Cart, Theme Toggle, Login/Profile */}
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Shopping Cart Button */}
          <button
            type="button"
            onClick={openCartFn}
            aria-label="Abrir Carrito de Compras"
            className="relative flex items-center gap-2 rounded-xl border border-cyan-500/20 bg-slate-100/80 px-3 py-2 text-xs font-semibold text-slate-700 transition-all duration-200 hover:border-cyan-400 hover:bg-cyan-500/10 dark:border-slate-800 dark:bg-slate-900/80 dark:text-slate-200 dark:hover:border-cyan-500/40"
          >
            <ShoppingCart className="h-4 w-4 text-cyan-600 dark:text-cyan-400" />
            <span className="hidden font-mono text-[11px] font-bold sm:inline">
              Carrito
            </span>
            {cartCount > 0 && (
              <span className="flex h-5 min-w-[20px] items-center justify-center rounded-full bg-gradient-to-r from-cyan-500 to-indigo-600 px-1 text-[10px] font-black text-white shadow-md shadow-cyan-500/30">
                {cartCount}
              </span>
            )}
          </button>

          {/* Theme Toggle */}
          <button
            type="button"
            onClick={onThemeToggle}
            aria-label={
              theme === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'
            }
            className="flex h-9 w-9 items-center justify-center rounded-xl border border-cyan-500/10 bg-slate-50 text-slate-500 transition-all duration-300 hover:border-cyan-500/30 hover:bg-cyan-500/5 hover:text-cyan-600 dark:border-slate-800 dark:bg-slate-900/70 dark:text-slate-400 dark:hover:border-cyan-500/30 dark:hover:text-cyan-400"
          >
            {theme === 'dark' ? (
              <Sun className="h-4 w-4" />
            ) : (
              <Moon className="h-4 w-4" />
            )}
          </button>

          {/* User Profile or Login */}
          {user ? (
            <div ref={userMenuRef} className="relative">
              <button
                type="button"
                onClick={() => setIsUserMenuOpen((current) => !current)}
                aria-haspopup="menu"
                aria-expanded={isUserMenuOpen}
                className={`group flex items-center gap-3 rounded-xl border px-2 py-1.5 text-left transition-all duration-300 sm:px-3 ${
                  isUserMenuOpen
                    ? 'border-cyan-500/20 bg-cyan-500/5'
                    : 'border-transparent hover:border-cyan-500/15 hover:bg-cyan-500/5'
                }`}
              >
                <div className="relative shrink-0">
                  <div className="absolute -inset-0.5 rounded-xl bg-gradient-to-br from-cyan-400 to-indigo-500 opacity-50 blur-[2px] transition-opacity group-hover:opacity-90" />
                  <img
                    src={user.avatarUrl}
                    alt={user.name}
                    className="relative h-8 w-8 rounded-xl object-cover ring-2 ring-white dark:ring-[#070a10]"
                  />
                </div>

                <div className="hidden max-w-[160px] md:block">
                  <p className="truncate text-xs font-bold text-slate-900 dark:text-slate-100">
                    {user.name}
                  </p>
                  <p className="truncate font-mono text-[9px] uppercase tracking-wider text-cyan-600 dark:text-cyan-400">
                    {ROLE_LABELS[user.role]}
                  </p>
                </div>

                <ChevronDown
                  className={`hidden h-4 w-4 text-slate-400 transition-transform duration-300 md:block ${
                    isUserMenuOpen ? 'rotate-180' : ''
                  }`}
                />
              </button>

              {isUserMenuOpen && (
                <div
                  role="menu"
                  className="absolute right-0 top-[calc(100%+0.65rem)] w-64 origin-top-right overflow-hidden rounded-2xl border border-cyan-500/15 bg-white/95 p-2 shadow-[0_20px_60px_rgba(15,23,42,0.18)] backdrop-blur-xl animate-in fade-in slide-in-from-top-2 duration-200 dark:border-slate-800 dark:bg-[#0c111a]/95"
                >
                  <div className="flex items-center gap-3 rounded-xl bg-slate-50 px-3 py-3 dark:bg-slate-900/70">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-cyan-500/20 bg-cyan-500/10 text-cyan-600 dark:text-cyan-400">
                      <UserRound className="h-4 w-4" />
                    </div>

                    <div className="min-w-0">
                      <p className="truncate text-xs font-bold text-slate-900 dark:text-white">
                        {user.name}
                      </p>
                      <p className="mt-0.5 truncate font-mono text-[9px] uppercase tracking-wider text-cyan-600 dark:text-cyan-400">
                        {ROLE_LABELS[user.role]}
                      </p>
                      {user.company && (
                        <p className="mt-1 truncate text-[10px] text-slate-500">
                          {user.company}
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="my-2 h-px bg-slate-200 dark:bg-slate-800" />

                  <button
                    type="button"
                    role="menuitem"
                    onClick={handleLogout}
                    className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-xs font-semibold text-rose-600 transition-colors hover:bg-rose-500/10 dark:text-rose-400 dark:hover:bg-rose-500/10"
                  >
                    <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-rose-500/10">
                      <LogOut className="h-4 w-4" />
                    </span>
                    <span>Cerrar sesión</span>
                  </button>
                </div>
              )}
            </div>
          ) : (
            <button
              type="button"
              onClick={onLoginClick}
              className="group flex items-center gap-2 rounded-xl border border-cyan-500/20 bg-gradient-to-r from-cyan-500/10 to-indigo-500/10 px-3 py-2 transition-all duration-300 hover:border-cyan-500/40 hover:shadow-[0_0_20px_rgba(6,182,212,0.10)] sm:px-4"
            >
              <LogIn className="h-4 w-4 text-cyan-600 transition-transform duration-300 group-hover:translate-x-0.5 dark:text-cyan-400" />
              <div className="text-left">
                <p className="text-[11px] font-bold leading-none text-slate-900 dark:text-white sm:text-xs">
                  Iniciar sesión
                </p>
                <p className="mt-1 hidden font-mono text-[8px] uppercase tracking-wider text-slate-500 sm:block">
                  o inscribirse
                </p>
              </div>
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
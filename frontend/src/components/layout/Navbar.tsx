import React, {
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  Bell,
  ChevronDown,
  LogIn,
  LogOut,
  Moon,
  Sun,
  UserRound,
} from 'lucide-react';

import companyLogo from '../../assets/mi-logo.png';
import { UserProfile, UserRole } from '../../types';

interface NavbarProps {
  user: UserProfile | null;
  theme: 'light' | 'dark';
  onThemeToggle: () => void;
  onLoginClick: () => void;
  onLogoutClick: () => void;
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
}) => {
  const [isUserMenuOpen, setIsUserMenuOpen] =
    useState(false);

  const userMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (
      event: MouseEvent,
    ) => {
      if (
        userMenuRef.current &&
        !userMenuRef.current.contains(
          event.target as Node,
        )
      ) {
        setIsUserMenuOpen(false);
      }
    };

    const handleEscape = (
      event: KeyboardEvent,
    ) => {
      if (event.key === 'Escape') {
        setIsUserMenuOpen(false);
      }
    };

    document.addEventListener(
      'mousedown',
      handleClickOutside,
    );

    document.addEventListener(
      'keydown',
      handleEscape,
    );

    return () => {
      document.removeEventListener(
        'mousedown',
        handleClickOutside,
      );

      document.removeEventListener(
        'keydown',
        handleEscape,
      );
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

  return (
    <header className="sticky top-0 z-40 h-16 border-b border-cyan-500/10 bg-white/90 shadow-[0_1px_20px_rgba(6,182,212,0.04)] backdrop-blur-xl dark:border-slate-800 dark:bg-[#070a10]/90">
      <div className="flex h-full w-full items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Logo */}
        <div className="flex min-w-0 items-center gap-3">
          <div className="relative flex h-10 w-10 shrink-0 items-center justify-center">
            <div className="absolute inset-0 rounded-xl bg-cyan-400/20 opacity-70 blur-lg" />

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

              <span className="hidden rounded-md border border-cyan-500/30 bg-cyan-500/10 px-2 py-0.5 font-mono text-[9px] font-bold tracking-wider text-cyan-600 dark:text-cyan-400 sm:inline-flex">
                EXECUTIVE
              </span>
            </div>
          </div>
        </div>

        {/* Acciones */}
        <div className="flex items-center gap-2 sm:gap-3">
          <button
            type="button"
            onClick={onThemeToggle}
            aria-label={
              theme === 'dark'
                ? 'Cambiar a modo claro'
                : 'Cambiar a modo oscuro'
            }
            className="flex h-9 w-9 items-center justify-center rounded-xl border border-cyan-500/10 bg-slate-50 text-slate-500 transition-all duration-300 hover:border-cyan-500/30 hover:bg-cyan-500/5 hover:text-cyan-600 dark:border-slate-800 dark:bg-slate-900/70 dark:text-slate-400 dark:hover:border-cyan-500/30 dark:hover:text-cyan-400"
          >
            {theme === 'dark' ? (
              <Sun className="h-4 w-4" />
            ) : (
              <Moon className="h-4 w-4" />
            )}
          </button>

          {user && (
            <button
              type="button"
              aria-label="Notificaciones"
              className="relative hidden h-9 w-9 items-center justify-center rounded-xl border border-cyan-500/10 bg-slate-50 text-slate-500 transition-all duration-300 hover:border-cyan-500/30 hover:bg-cyan-500/5 hover:text-cyan-600 dark:border-slate-800 dark:bg-slate-900/70 dark:text-slate-400 dark:hover:border-cyan-500/30 dark:hover:text-cyan-400 sm:flex"
            >
              <Bell className="h-4 w-4" />

              <span className="absolute right-2 top-2 h-2 w-2 rounded-full border-2 border-white bg-indigo-500 dark:border-slate-900" />
            </button>
          )}

          {user ? (
            <div
              ref={userMenuRef}
              className="relative"
            >
              {/* Botón que abre el menú */}
              <button
                type="button"
                onClick={() =>
                  setIsUserMenuOpen(
                    (current) => !current,
                  )
                }
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
                    isUserMenuOpen
                      ? 'rotate-180'
                      : ''
                  }`}
                />
              </button>

              {/* Menú desplegable */}
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
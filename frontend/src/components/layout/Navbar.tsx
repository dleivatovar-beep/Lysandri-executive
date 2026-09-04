// src/components/layout/Navbar.tsx
import React from 'react';
import { Bell, Sun, Moon, ChevronDown, LogIn } from 'lucide-react';
import { UserProfile } from '../../types';
import logoImg from '../../assets/mi-logo.png';

interface NavbarProps {
  user: UserProfile;
  theme: 'dark' | 'light';
  onToggleTheme: () => void;
  isLoggedIn?: boolean;
  onLoginClick?: () => void;
  onLogoutClick?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  user,
  theme,
  onToggleTheme,
  isLoggedIn = false,
  onLoginClick,
  onLogoutClick,
}) => {
  return (
    <header className="sticky top-0 z-40 flex h-16 w-full items-center justify-between border-b border-slate-200 bg-white/90 px-4 backdrop-blur-xl transition-colors duration-500 dark:border-slate-800/80 dark:bg-[#07090e]/90 md:px-6">
      <div className="flex items-center space-x-3.5">
        <img
          src={logoImg}
          alt="Logo Lysandri"
          className="h-10 w-10 object-contain drop-shadow-[0_0_8px_rgba(34,211,238,0.5)]"
        />

        <div className="flex items-center space-x-2">
          <span className="text-base font-extrabold uppercase tracking-wider text-slate-800 dark:text-slate-100">
            Lysandri
          </span>

          <span className="rounded border border-cyan-200 bg-cyan-100 px-2 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider text-cyan-700 dark:border-cyan-500/20 dark:bg-cyan-500/10 dark:text-cyan-500">
            Executive
          </span>
        </div>
      </div>

      <div className="flex items-center space-x-3">
        <button
          onClick={onToggleTheme}
          className="relative rounded-lg border border-slate-200 bg-slate-50 p-2 text-slate-500 transition-all duration-300 hover:scale-110 hover:bg-white hover:text-indigo-600 active:scale-90 dark:border-slate-800 dark:bg-slate-900/80 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-amber-300"
          title="Cambiar tema"
        >
          {theme === 'dark' ? (
            <Sun className="h-4 w-4" />
          ) : (
            <Moon className="h-4 w-4" />
          )}
        </button>

        <button
          className="relative rounded-lg border border-slate-200 bg-slate-50 p-2 text-slate-500 transition-all duration-300 hover:scale-110 hover:bg-white hover:text-cyan-600 active:scale-90 dark:border-slate-800 dark:bg-slate-900/80 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-cyan-300"
          title="Notificaciones"
        >
          <Bell className="h-4 w-4" />
          <span className="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-indigo-500" />
        </button>

        <div className="flex items-center border-l border-slate-200 pl-3 dark:border-slate-800/80">
          {isLoggedIn ? (
            <button
              onClick={onLogoutClick}
              className="group flex items-center space-x-3 text-left"
              title="Cerrar sesión"
            >
              <img
                src={user.avatarUrl}
                alt={user.name}
                className="h-8 w-8 rounded-lg border border-slate-300 object-cover shadow-sm dark:border-cyan-500/30"
              />

              <div className="hidden flex-col xl:flex">
                <span className="text-xs font-semibold leading-tight text-slate-800 transition-colors group-hover:text-indigo-600 dark:text-slate-200 dark:group-hover:text-cyan-300">
                  {user.name}
                </span>

                <span className="font-mono text-[9px] uppercase leading-tight text-cyan-600 dark:text-cyan-500">
                  {user.role}
                </span>
              </div>

              <ChevronDown className="hidden h-3.5 w-3.5 text-slate-400 transition-colors group-hover:text-cyan-500 xl:block" />
            </button>
          ) : (
            <button
              onClick={onLoginClick}
              className="group flex items-center gap-2.5 rounded-xl border border-cyan-200 bg-cyan-50/70 px-3 py-1.5 text-left transition-all duration-300 hover:-translate-y-0.5 hover:border-cyan-400 hover:bg-cyan-100 hover:shadow-md hover:shadow-cyan-500/10 active:translate-y-0 dark:border-cyan-500/20 dark:bg-cyan-500/10 dark:hover:border-cyan-400/60 dark:hover:bg-cyan-500/15"
            >
              <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-gradient-to-br from-cyan-500 to-indigo-600 text-white shadow-sm shadow-cyan-500/30">
                <LogIn className="h-3.5 w-3.5" />
              </div>

              <div className="hidden flex-col sm:flex">
                <span className="text-xs font-bold leading-tight text-slate-800 transition-colors group-hover:text-indigo-700 dark:text-slate-100 dark:group-hover:text-cyan-200">
                  Iniciar sesión
                </span>

                <span className="font-mono text-[9px] uppercase leading-tight text-slate-500 dark:text-slate-400">
                  o inscribirse
                </span>
              </div>
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
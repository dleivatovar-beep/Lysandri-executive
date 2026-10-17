import React from 'react';

import {
  BookOpen,
  Bot,
  ChartNoAxesColumnIncreasing,
  ClipboardCheck,
  FileText,
  GraduationCap,
  LayoutDashboard,
  Library,
  MessageSquareText,
  Store,
  Users,
  LogOut,
  Calculator,
  type LucideIcon,
} from 'lucide-react';

import {
  ActiveView,
  UserRole,
} from '../../types';

interface SidebarProps {
  activeView: ActiveView;
  role: UserRole;
  onViewChange: (
    view: ActiveView,
  ) => void;
  onLogout?: () => void;
}

interface NavigationItem {
  label: string;
  view: ActiveView;
  icon: LucideIcon;
}

const ROLE_LABELS: Record<
  UserRole,
  string
> = {
  ESTUDIANTE: 'Espacio ejecutivo',
  INSTRUCTOR: 'Espacio docente',
  ADMIN: 'Administración',
};

const ROLE_NAVIGATION: Record<
  UserRole,
  NavigationItem[]
> = {
  ESTUDIANTE: [
    {
      label: 'Explorar cursos',
      view: 'MARKETPLACE',
      icon: Store,
    },
    {
      label: 'Mis cursos',
      view: 'MY_COURSES',
      icon: BookOpen,
    },
    {
      label: 'Mi progreso',
      view: 'PROGRESS',
      icon: ChartNoAxesColumnIncreasing,
    },
    {
      label: 'Biblioteca',
      view: 'LIBROS',
      icon: Library,
    },
    {
      label: 'Asistente IA',
      view: 'CHAT',
      icon: Bot,
    },
  ],

  INSTRUCTOR: [
    {
      label: 'Panel docente',
      view: 'INSTRUCTOR_DASHBOARD',
      icon: LayoutDashboard,
    },
    {
      label: 'Mis cursos',
      view: 'MANAGE_COURSES',
      icon: GraduationCap,
    },
    {
      label: 'Contenido',
      view: 'CONTENT',
      icon: FileText,
    },
    {
      label: 'Estudiantes',
      view: 'STUDENTS',
      icon: Users,
    },
  ],

  ADMIN: [
    {
      label: 'Cursos',
      view: 'MANAGE_COURSES',
      icon: GraduationCap,
    },
    {
      label: 'Usuarios',
      view: 'USERS',
      icon: Users,
    },
    {
      label: 'Inscripciones',
      view: 'ENROLLMENTS',
      icon: ClipboardCheck,
    },
    {
      label: 'Solicitudes',
      view: 'INFORMATION_REQUESTS',
      icon: MessageSquareText,
    },
    {
      label: 'Área Contable',
      view: 'ACCOUNTING',
      icon: Calculator,
    },
  ],
};

export const Sidebar:
  React.FC<SidebarProps> = ({
    activeView,
    role,
    onViewChange,
    onLogout,
  }) => {
    const navigationItems =
      ROLE_NAVIGATION[role];

    return (
      <aside className={`fixed bottom-0 left-0 top-16 z-30 hidden w-64 border-r border-cyan-500/10 bg-white/95 px-4 py-6 backdrop-blur-xl dark:border-slate-800 dark:bg-[#080b12]/95 lg:block${role === 'ADMIN' ? ' lx-admin-sidebar' : ''}`}>
        <div className="flex h-full flex-col justify-between">
          <div>
            <div className="mb-6 px-3">
              <p className="font-mono text-[9px] uppercase tracking-[0.22em] text-slate-400 dark:text-slate-600">
                Navegación
              </p>

              <h2 className="mt-2 text-sm font-bold text-slate-900 dark:text-white">
                {ROLE_LABELS[role]}
              </h2>
            </div>

            <nav aria-label={role === 'ADMIN' ? 'Navegación de administración' : 'Navegación de usuario'} className="space-y-1.5">
              {navigationItems.map(
                (item) => {
                  const Icon =
                    item.icon;

                  const isActive =
                    activeView ===
                    item.view;

                  return (
                    <button
                      key={item.view}
                      type="button"
                      onClick={() =>
                        onViewChange(
                          item.view,
                        )
                      }
                      aria-current={isActive ? 'page' : undefined}
                      className={`group relative flex w-full items-center gap-3 overflow-hidden rounded-xl px-3 py-3 text-left text-sm font-semibold transition-all duration-300 ${
                        isActive
                          ? 'border border-cyan-500/20 bg-gradient-to-r from-cyan-500/10 to-indigo-500/10 text-cyan-700 shadow-[0_8px_24px_rgba(6,182,212,0.08)] dark:text-cyan-300'
                          : 'border border-transparent text-slate-500 hover:border-cyan-500/10 hover:bg-cyan-500/5 hover:text-slate-900 dark:text-slate-500 dark:hover:text-slate-200'
                      }`}
                    >
                      {isActive && (
                        <span aria-hidden="true" className="absolute bottom-2 left-0 top-2 w-0.5 rounded-r-full bg-gradient-to-b from-cyan-400 to-indigo-500" />
                      )}

                      {role === 'ADMIN' ? (
                        <span className="lx-admin-nav-icon" aria-hidden="true">
                          <LysandriAdminMark view={item.view} />
                        </span>
                      ) : (
                        <span
                          className={`lx-admin-nav-icon flex h-8 w-8 items-center justify-center transition-all duration-300 ${
                            isActive
                              ? 'bg-cyan-500/10 text-cyan-600 dark:text-cyan-400'
                              : 'bg-slate-100 text-slate-400 group-hover:text-cyan-600 dark:bg-slate-900 dark:text-slate-500 dark:group-hover:text-cyan-400'
                          }`}
                        >
                          <Icon className="h-4 w-4" aria-hidden="true" />
                        </span>
                      )}

                      <span className="lx-admin-nav-label">
                        {item.label}
                      </span>
                    </button>
                  );
                },
              )}
            </nav>
          </div>

          {onLogout && (
            <div className="pt-4 border-t border-slate-200 dark:border-slate-800/80">
              <button
                type="button"
                onClick={onLogout}
                className={`group flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-xs font-semibold text-rose-500 hover:bg-rose-500/10 dark:text-rose-400 dark:hover:bg-rose-500/15 transition-all${role === 'ADMIN' ? ' lx-admin-sidebar-logout' : ''}`}
              >
                {role !== 'ADMIN' && (
                  <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-rose-500/10 text-rose-500 dark:bg-rose-500/20 group-hover:scale-105 transition-transform">
                    <LogOut className="h-4 w-4" />
                  </span>
                )}
                <span>Cerrar sesión</span>
              </button>
            </div>
          )}
        </div>
      </aside>
    );
  };

/** Purpose-drawn admin symbols: records and finance share one Lysandri stroke language. */
const LysandriAdminMark: React.FC<{ view: ActiveView }> = ({ view }) => (
  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <g stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round">
      {view === 'MANAGE_COURSES' && <>
        <path d="M5 4.5h12.75A2.25 2.25 0 0 1 20 6.75V20H7a2 2 0 0 1-2-2z" />
        <path d="M7 20h13M8.5 8h8M8.5 11.5h7M8.5 15h4.5" />
        <path className="lx-admin-icon-accent" d="M5 7.5h1.5" />
      </>}
      {view === 'USERS' && <>
        <circle cx="9" cy="8" r="3" />
        <path d="M3.75 19c.45-3.15 2.35-4.75 5.25-4.75s4.8 1.6 5.25 4.75" />
        <path d="M15.25 5.75a2.55 2.55 0 0 1 0 4.8M16.5 14.75c2.25.45 3.5 1.85 3.75 4.25" />
        <path className="lx-admin-icon-accent" d="M6.5 8h.01" />
      </>}
      {view === 'ENROLLMENTS' && <>
        <path d="M6 4.5h12.25A1.75 1.75 0 0 1 20 6.25v12A1.75 1.75 0 0 1 18.25 20H6a2 2 0 0 1-2-2V6.5a2 2 0 0 1 2-2z" />
        <path d="M8 9h5M8 13h5M8 17h3" />
        <path className="lx-admin-icon-accent" d="m15.5 12.5 1.25 1.25 2.5-3" />
      </>}
      {view === 'INFORMATION_REQUESTS' && <>
        <path d="M5.25 5.5h11.5A2.25 2.25 0 0 1 19 7.75v6.5a2.25 2.25 0 0 1-2.25 2.25h-6.5L6 19v-2.5h-.75A2.25 2.25 0 0 1 3 14.25v-6.5A2.25 2.25 0 0 1 5.25 5.5z" />
        <path d="M7 9.5h8M7 12.5h5" />
        <path className="lx-admin-icon-accent" d="M18.5 4.5h2" />
      </>}
      {view === 'ACCOUNTING' && <>
        <path d="M5.25 3.75h11.5A2.25 2.25 0 0 1 19 6v14H7a2 2 0 0 1-2-2z" />
        <path d="M8 8h8M8 11.5h3M8 15h2M13.5 13.5v3M16 13.5v3" />
        <path className="lx-admin-icon-accent" d="M13.5 18.5h2.5" />
      </>}
    </g>
  </svg>
);

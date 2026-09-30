import React from 'react';

import {
  BarChart3,
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
      label: 'Panel general',
      view: 'ADMIN_DASHBOARD',
      icon: LayoutDashboard,
    },
    {
      label: 'Usuarios',
      view: 'USERS',
      icon: Users,
    },
    {
      label: 'Cursos',
      view: 'MANAGE_COURSES',
      icon: GraduationCap,
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
      label: 'Reportes',
      view: 'REPORTS',
      icon: BarChart3,
    },
  ],
};

export const Sidebar:
  React.FC<SidebarProps> = ({
    activeView,
    role,
    onViewChange,
  }) => {
    const navigationItems =
      ROLE_NAVIGATION[role];

    return (
      <aside className="fixed bottom-0 left-0 top-16 z-30 hidden w-64 border-r border-cyan-500/10 bg-white/95 px-4 py-6 backdrop-blur-xl dark:border-slate-800 dark:bg-[#080b12]/95 lg:block">
        <div className="h-full">
          <div className="mb-6 px-3">
            <p className="font-mono text-[9px] uppercase tracking-[0.22em] text-slate-400 dark:text-slate-600">
              Navegación
            </p>

            <h2 className="mt-2 text-sm font-bold text-slate-900 dark:text-white">
              {ROLE_LABELS[role]}
            </h2>
          </div>

          <nav className="space-y-1.5">
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
                    className={`group relative flex w-full items-center gap-3 overflow-hidden rounded-xl px-3 py-3 text-left text-sm font-semibold transition-all duration-300 ${
                      isActive
                        ? 'border border-cyan-500/20 bg-gradient-to-r from-cyan-500/10 to-indigo-500/10 text-cyan-700 shadow-[0_8px_24px_rgba(6,182,212,0.08)] dark:text-cyan-300'
                        : 'border border-transparent text-slate-500 hover:border-cyan-500/10 hover:bg-cyan-500/5 hover:text-slate-900 dark:text-slate-500 dark:hover:text-slate-200'
                    }`}
                  >
                    {isActive && (
                      <span className="absolute bottom-2 left-0 top-2 w-0.5 rounded-r-full bg-gradient-to-b from-cyan-400 to-indigo-500" />
                    )}

                    <span
                      className={`flex h-8 w-8 items-center justify-center rounded-lg transition-all duration-300 ${
                        isActive
                          ? 'bg-cyan-500/10 text-cyan-600 dark:text-cyan-400'
                          : 'bg-slate-100 text-slate-400 group-hover:text-cyan-600 dark:bg-slate-900 dark:text-slate-500 dark:group-hover:text-cyan-400'
                      }`}
                    >
                      <Icon className="h-4 w-4" />
                    </span>

                    <span>
                      {item.label}
                    </span>
                  </button>
                );
              },
            )}
          </nav>
        </div>
      </aside>
    );
  };
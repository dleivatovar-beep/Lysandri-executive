// src/components/layout/Sidebar.tsx
import React from 'react';
import { Store, BookOpen, ClipboardList, ChevronLeft, ChevronRight } from 'lucide-react';
import { ActiveView } from '../../types';

interface SidebarProps {
  activeView: ActiveView;
  setActiveView: (view: ActiveView | any) => void;
  isCollapsed: boolean;
  setIsCollapsed: (collapsed: boolean) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeView, setActiveView, isCollapsed, setIsCollapsed }) => {
  return (
    <aside className={`relative h-[calc(100vh-4rem)] bg-white dark:bg-[#07090e] border-r border-slate-200 dark:border-slate-800/80 transition-all duration-300 ease-in-out flex flex-col justify-between ${isCollapsed ? 'w-16' : 'w-60'}`}>
      <div className="p-3 space-y-5">
        <div className={`flex ${isCollapsed ? 'justify-center' : 'justify-end'} mb-1 transition-all duration-300`}>
          <button onClick={() => setIsCollapsed(!isCollapsed)} className="p-1 rounded-lg bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 transition-all duration-300 ease-out hover:text-indigo-600 dark:hover:text-cyan-400 hover:bg-white dark:hover:bg-slate-800 active:scale-90" title={isCollapsed ? "Expandir Menú" : "Colapsar Menú"}>
            {isCollapsed ? <ChevronRight className="w-3.5 h-3.5" /> : <ChevronLeft className="w-3.5 h-3.5" />}
          </button>
        </div>

        {!isCollapsed && (
          <div className="px-3 text-[9px] font-mono uppercase text-slate-500 dark:text-slate-500 tracking-widest font-semibold transition-opacity duration-300">Plataforma C-Suite</div>
        )}

        <nav className="space-y-1.5">
          <button onClick={() => setActiveView('MARKETPLACE')} className={`w-full flex items-center ${isCollapsed ? 'justify-center px-0' : 'justify-start space-x-3 px-3'} py-2.5 rounded-xl transition-all duration-300 ease-out active:scale-95 font-medium text-xs relative ${activeView === 'MARKETPLACE' ? 'bg-slate-100 dark:bg-slate-900 text-slate-900 dark:text-cyan-300 border border-slate-200 dark:border-cyan-500/30 shadow-sm dark:shadow-glow-cyan font-semibold' : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-900/60 border border-transparent hover:translate-x-1'}`}>
            {activeView === 'MARKETPLACE' && <span className="absolute left-0 top-2 bottom-2 w-1 bg-indigo-500 dark:bg-cyan-400 rounded-r" />}
            <Store className={`w-4 h-4 flex-shrink-0 transition-colors duration-300 ${activeView === 'MARKETPLACE' ? 'text-indigo-600 dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500'}`} />
            {!isCollapsed && <div className="flex items-center justify-between w-full"><span>Cursos</span><span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-400">4 Activos</span></div>}
          </button>

          <button onClick={() => setActiveView('LIBROS')} className={`w-full flex items-center ${isCollapsed ? 'justify-center px-0' : 'justify-start space-x-3 px-3'} py-2.5 rounded-xl transition-all duration-300 ease-out active:scale-95 font-medium text-xs relative ${activeView === 'LIBROS' ? 'bg-slate-100 dark:bg-slate-900 text-slate-900 dark:text-cyan-300 border border-slate-200 dark:border-cyan-500/30 shadow-sm dark:shadow-glow-cyan font-semibold' : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-900/60 border border-transparent hover:translate-x-1'}`}>
            {activeView === 'LIBROS' && <span className="absolute left-0 top-2 bottom-2 w-1 bg-indigo-500 dark:bg-cyan-400 rounded-r" />}
            <BookOpen className={`w-4 h-4 flex-shrink-0 transition-colors duration-300 ${activeView === 'LIBROS' ? 'text-indigo-600 dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500'}`} />
            {!isCollapsed && <div className="flex items-center justify-between w-full"><span>Libros</span></div>}
          </button>

          <button onClick={() => setActiveView('TAREAS')} className={`w-full flex items-center ${isCollapsed ? 'justify-center px-0' : 'justify-start space-x-3 px-3'} py-2.5 rounded-xl transition-all duration-300 ease-out active:scale-95 font-medium text-xs relative ${activeView === 'TAREAS' ? 'bg-slate-100 dark:bg-slate-900 text-slate-900 dark:text-cyan-300 border border-slate-200 dark:border-cyan-500/30 shadow-sm dark:shadow-glow-cyan font-semibold' : 'text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-900/60 border border-transparent hover:translate-x-1'}`}>
            {activeView === 'TAREAS' && <span className="absolute left-0 top-2 bottom-2 w-1 bg-indigo-500 dark:bg-cyan-400 rounded-r" />}
            <ClipboardList className={`w-4 h-4 flex-shrink-0 transition-colors duration-300 ${activeView === 'TAREAS' ? 'text-indigo-600 dark:text-cyan-400' : 'text-slate-400 dark:text-slate-500'}`} />
            {!isCollapsed && <div className="flex items-center justify-between w-full"><span>Tareas</span></div>}
          </button>
        </nav>
      </div>
    </aside>
  );
};
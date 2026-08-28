// src/components/layout/Sidebar.tsx
import React from 'react';
import { 
  Store, 
  Bot, 
  ChevronLeft, 
  ChevronRight, 
  TrendingUp, 
  FileText, 
  ExternalLink
} from 'lucide-react';
import { ActiveView } from '../../types';

interface SidebarProps {
  activeView: ActiveView;
  setActiveView: (view: ActiveView) => void;
  isCollapsed: boolean;
  setIsCollapsed: (collapsed: boolean) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  activeView,
  setActiveView,
  isCollapsed,
  setIsCollapsed,
}) => {
  return (
    <aside 
      className={`relative h-[calc(100vh-4rem)] bg-[#07090e]/95 backdrop-blur-2xl border-r border-slate-800/80 transition-all duration-300 ease-in-out flex flex-col justify-between ${
        isCollapsed ? 'w-16' : 'w-60'
      }`}
    >
      {/* Navigation Links */}
      <div className="p-3 space-y-5">
        {/* Collapse / Expand Toggle Button */}
        <div className="flex justify-end mb-1">
          <button
            onClick={() => setIsCollapsed(!isCollapsed)}
            className="p-1 rounded-lg bg-slate-900 border border-slate-800 text-slate-400 hover:text-cyan-400 hover:border-slate-700 transition-all"
            title={isCollapsed ? "Expandir Menú" : "Colapsar Menú"}
          >
            {isCollapsed ? <ChevronRight className="w-3.5 h-3.5" /> : <ChevronLeft className="w-3.5 h-3.5" />}
          </button>
        </div>

        {/* Section Header */}
        {!isCollapsed && (
          <div className="px-3 text-[9px] font-mono uppercase text-slate-500 tracking-widest font-semibold">
            Plataforma C-Suite
          </div>
        )}

        <nav className="space-y-1.5">
          {/* Marketplace Nav Item */}
          <button
            onClick={() => setActiveView('MARKETPLACE')}
            className={`w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl transition-all font-medium text-xs relative ${
              activeView === 'MARKETPLACE'
                ? 'bg-slate-900 text-cyan-300 border border-cyan-500/30 shadow-glow-cyan font-semibold'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/60 border border-transparent'
            }`}
          >
            {activeView === 'MARKETPLACE' && (
              <span className="absolute left-0 top-2 bottom-2 w-1 bg-cyan-400 rounded-r" />
            )}
            <Store className={`w-4 h-4 flex-shrink-0 ${activeView === 'MARKETPLACE' ? 'text-cyan-400' : 'text-slate-400'}`} />
            {!isCollapsed && (
              <div className="flex items-center justify-between w-full">
                <span>Marketplace</span>
                <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-400">
                  4 Activos
                </span>
              </div>
            )}
          </button>

          {/* RAG Executive AI Chat Nav Item */}
          <button
            onClick={() => setActiveView('CHAT')}
            className={`w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl transition-all font-medium text-xs relative ${
              activeView === 'CHAT'
                ? 'bg-slate-900 text-cyan-300 border border-cyan-500/30 shadow-glow-cyan font-semibold'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/60 border border-transparent'
            }`}
          >
            {activeView === 'CHAT' && (
              <span className="absolute left-0 top-2 bottom-2 w-1 bg-indigo-400 rounded-r" />
            )}
            <Bot className={`w-4 h-4 flex-shrink-0 ${activeView === 'CHAT' ? 'text-cyan-400' : 'text-slate-400'}`} />
            {!isCollapsed && (
              <div className="flex items-center justify-between w-full">
                <span>Asistente IA RAG</span>
                <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  Executive
                </span>
              </div>
            )}
          </button>
        </nav>
      </div>

      {/* System Metrics Footer */}
      {!isCollapsed ? (
        <div className="p-3.5 m-2.5 rounded-xl bg-slate-900/80 border border-slate-800 space-y-2.5 shadow-inner">
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-mono font-bold text-slate-300 uppercase tracking-wider flex items-center space-x-1.5">
              <TrendingUp className="w-3.5 h-3.5 text-cyan-400" />
              <span>C-Suite Telemetry</span>
            </span>
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
          </div>

          <div className="space-y-1 text-[11px] font-mono">
            <div className="flex justify-between text-slate-400">
              <span>Activos:</span>
              <span className="text-cyan-300 font-semibold">4 Playbooks</span>
            </div>
            <div className="flex justify-between text-slate-400">
              <span>RAG Knowledge:</span>
              <span className="text-indigo-300 font-semibold">1,240 Docs</span>
            </div>
            <div className="flex justify-between text-slate-400">
              <span>Acceso:</span>
              <span className="text-amber-400 font-semibold">Enterprise</span>
            </div>
          </div>

          <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[9px] text-slate-500 font-mono">
            <span>v2.4.0-Executive</span>
            <ExternalLink className="w-3 h-3 hover:text-cyan-400 cursor-pointer" />
          </div>
        </div>
      ) : (
        <div className="p-2 mb-2 flex flex-col items-center justify-center space-y-2">
          <div className="w-7 h-7 rounded-lg bg-slate-900 border border-slate-800 flex items-center justify-center text-cyan-400" title="RAG Documents: 1,240">
            <FileText className="w-3.5 h-3.5" />
          </div>
        </div>
      )}
    </aside>
  );
};

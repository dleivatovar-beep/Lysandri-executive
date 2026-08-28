// src/components/layout/Navbar.tsx
import React from 'react';
import { 
  Search, 
  Bell, 
  ChevronDown,
  Cpu,
  Lock
} from 'lucide-react';
import { UserProfile } from '../../types';

interface NavbarProps {
  user: UserProfile;
  onOpenSearch?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ user, onOpenSearch }) => {
  return (
    <header className="sticky top-0 z-40 w-full h-16 bg-[#07090e]/90 backdrop-blur-2xl border-b border-slate-800/80 px-4 md:px-6 flex items-center justify-between transition-all">
      {/* Brand & Logo */}
      <div className="flex items-center space-x-3.5">
        <div className="relative flex items-center justify-center w-9 h-9 rounded-lg bg-gradient-to-br from-cyan-500/20 via-slate-900 to-indigo-600/30 border border-cyan-500/40 text-cyan-400 shadow-glow-cyan">
          <Cpu className="w-5 h-5" />
          <span className="absolute -top-0.5 -right-0.5 w-2 h-2 bg-cyan-400 rounded-full" />
        </div>
        
        <div className="flex flex-col">
          <div className="flex items-center space-x-2">
            <span className="font-extrabold text-base tracking-wider text-slate-100 uppercase font-sans">
              Lysandri
            </span>
            <span className="text-[10px] font-mono font-bold px-2 py-0.5 rounded bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 tracking-wider uppercase">
              Executive
            </span>
          </div>
          <span className="text-[9px] text-slate-400 tracking-widest font-mono uppercase">
            ENTERPRISE ASSET PLATFORM & RAG AI
          </span>
        </div>
      </div>

      {/* Global AI Status & Search Trigger */}
      <div className="hidden md:flex items-center space-x-4">
        {/* Search trigger button */}
        <button 
          onClick={onOpenSearch}
          className="flex items-center space-x-3 px-3.5 py-1.5 rounded-lg bg-slate-900/90 border border-slate-800 text-slate-400 hover:text-slate-200 hover:border-slate-700 transition-all text-xs font-medium w-64 shadow-inner"
        >
          <Search className="w-3.5 h-3.5 text-cyan-400" />
          <span className="text-slate-400">Buscar activos o consultar RAG...</span>
          <kbd className="ml-auto text-[9px] bg-slate-800 text-slate-400 px-1.5 py-0.5 rounded font-mono border border-slate-700/50">
            ⌘K
          </kbd>
        </button>

        {/* AI System Status Badge */}
        <div className="flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-slate-900/90 border border-slate-800 text-slate-300 text-xs font-mono">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-cyan-400"></span>
          </span>
          <span className="text-cyan-300 font-semibold">RAG AI Core Online</span>
          <span className="text-[10px] text-slate-500 bg-slate-800 px-1.5 py-0.5 rounded">v4.2</span>
        </div>
      </div>

      {/* User Actions & Profile */}
      <div className="flex items-center space-x-3">
        {/* Notifications Button */}
        <button className="relative p-2 rounded-lg bg-slate-900/80 border border-slate-800 text-slate-400 hover:text-cyan-300 hover:border-slate-700 transition-all">
          <Bell className="w-4 h-4" />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-indigo-500 rounded-full" />
        </button>

        {/* Security Shield Badge */}
        <div className="hidden sm:flex items-center space-x-1.5 px-2.5 py-1.5 rounded-lg bg-slate-900/80 border border-slate-800 text-emerald-400 text-xs font-mono" title="Zero-Trust Encrypted Session">
          <Lock className="w-3.5 h-3.5" />
          <span className="text-[10px] text-slate-400">mTLS Enforced</span>
        </div>

        {/* User Profile Pill */}
        <div className="flex items-center space-x-3 pl-3 border-l border-slate-800/80">
          <img 
            src={user.avatarUrl} 
            alt={user.name} 
            className="w-8 h-8 rounded-lg object-cover border border-cyan-500/30 shadow-sm"
          />
          <div className="hidden xl:flex flex-col text-left">
            <span className="text-xs font-semibold text-slate-200 leading-tight">
              {user.name}
            </span>
            <span className="text-[9px] text-cyan-400 font-mono leading-tight uppercase">
              {user.role}
            </span>
          </div>
          <ChevronDown className="w-3.5 h-3.5 text-slate-400 hidden xl:block" />
        </div>
      </div>
    </header>
  );
};

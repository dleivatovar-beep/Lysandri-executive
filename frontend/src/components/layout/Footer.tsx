// src/components/layout/Footer.tsx
import React from 'react';
import { ShieldCheck } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer className="mt-12 border-t border-cyan-100 bg-white/60 px-4 py-6 backdrop-blur-sm dark:border-slate-800 dark:bg-[#07090e]/60 md:px-6">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 text-center sm:flex-row sm:items-center sm:justify-between sm:text-left">
        <div>
          <p className="text-sm font-bold tracking-wide text-slate-800 dark:text-slate-100">
            Lysandri Executive
          </p>

          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Plataforma de formación y crecimiento profesional.
          </p>
        </div>

        <div className="flex items-center justify-center gap-2 text-xs text-slate-500 dark:text-slate-400">
          <ShieldCheck className="h-4 w-4 text-cyan-600 dark:text-cyan-400" />

          <span>© {new Date().getFullYear()} Lysandri Global Tech</span>

          <span className="mx-1 text-slate-300 dark:text-slate-700">•</span>

          <span className="font-mono text-[10px] uppercase tracking-wider text-cyan-600 dark:text-cyan-400">
            Beta
          </span>
        </div>
      </div>
    </footer>
  );
};
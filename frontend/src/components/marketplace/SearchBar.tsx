// src/components/marketplace/SearchBar.tsx
import React from 'react';
import { Search, X, SlidersHorizontal } from 'lucide-react';

interface SearchBarProps {
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  resultsCount: number;
}

export const SearchBar: React.FC<SearchBarProps> = ({
  searchQuery,
  setSearchQuery,
  resultsCount,
}) => {
  return (
    <div className="w-full space-y-2">
      <div className="relative flex items-center w-full">
        <Search className="absolute left-4 w-5 h-5 text-cyan-400 pointer-events-none" />
        
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Buscar Playbooks por título, arquitectura, FinOps o etiquetas (ej. Kafka, RAG, OPA)..."
          className="w-full pl-12 pr-24 py-3.5 bg-slate-900/80 border border-slate-800 rounded-xl text-slate-100 placeholder-slate-500 text-sm focus:outline-none focus:border-cyan-500/60 focus:ring-2 focus:ring-cyan-500/20 transition-all font-medium"
        />

        <div className="absolute right-3 flex items-center space-x-2">
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="p-1 rounded-md text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition-all"
              title="Limpiar búsqueda"
            >
              <X className="w-4 h-4" />
            </button>
          )}

          <div className="hidden sm:flex items-center space-x-1.5 px-2.5 py-1 rounded-lg bg-slate-800/80 text-slate-400 text-xs font-mono border border-slate-700/50">
            <SlidersHorizontal className="w-3.5 h-3.5 text-cyan-400" />
            <span>{resultsCount} Activos</span>
          </div>
        </div>
      </div>
    </div>
  );
};

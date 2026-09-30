import React from 'react';
import { Search, X, SlidersHorizontal } from 'lucide-react';

interface SearchBarProps {
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  resultsCount: number;
}

export const SearchBar: React.FC<SearchBarProps> = ({ searchQuery, setSearchQuery, resultsCount }) => {
  return (
    <div className="w-full space-y-2">
      <div className="relative flex items-center w-full transition-all duration-300 ease-out focus-within:scale-[1.01] hover:shadow-md dark:hover:shadow-cyan-500/5 group">
        <Search className="absolute left-4 w-5 h-5 text-slate-400 dark:text-cyan-500 pointer-events-none transition-colors duration-300 group-focus-within:text-slate-600 dark:group-focus-within:text-cyan-300" />
        
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Buscar cursos por título, tecnología o etiquetas (ej. Kafka, OPA, AWS)..."
          className="w-full pl-12 pr-24 py-3.5 bg-white dark:bg-slate-900/80 border border-slate-200 dark:border-slate-800 rounded-xl text-slate-900 dark:text-slate-100 placeholder-slate-400 dark:placeholder-slate-500 text-sm focus:outline-none focus:border-slate-400 dark:focus:border-cyan-500/60 focus:ring-2 focus:ring-slate-200 dark:focus:ring-cyan-500/20 transition-all duration-300 hover:border-slate-300 dark:hover:border-slate-700 font-medium shadow-sm dark:shadow-none"
        />

        <div className="absolute right-3 flex items-center space-x-2">
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="p-1.5 rounded-md text-slate-400 hover:text-red-500 hover:bg-slate-100 dark:hover:bg-slate-800/80 transition-all duration-200 active:scale-90"
              title="Limpiar búsqueda"
            >
              <X className="w-4 h-4" />
            </button>
          )}

          <div className="hidden sm:flex items-center space-x-1.5 px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800/80 text-slate-600 dark:text-slate-400 text-xs font-mono border border-slate-200 dark:border-slate-700/50">
            <SlidersHorizontal className="w-3.5 h-3.5 text-slate-500 dark:text-cyan-400" />
            <span>{resultsCount} Activos</span>
          </div>
        </div>
      </div>
    </div>
  );
};
import React from 'react';
import { Search, X } from 'lucide-react';

interface SearchBarProps {
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  resultsCount: number;
}

export const SearchBar: React.FC<SearchBarProps> = ({ searchQuery, setSearchQuery, resultsCount }) => {
  return (
    <div className="w-full">
      <div className="relative flex items-center w-full transition-all duration-300 ease-out group">
        <div className="absolute left-4 flex items-center pointer-events-none text-slate-400 group-focus-within:text-cyan-400 transition-colors duration-300">
          <Search className="w-4 h-4" />
        </div>
        
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Buscar programas por nombre o área..."
          className="w-full pl-11 pr-32 py-3.5 bg-slate-950/85 border border-slate-700/80 rounded-2xl text-slate-100 placeholder-slate-400 text-xs md:text-sm focus:outline-none focus:border-cyan-400 focus:ring-2 focus:ring-cyan-500/25 transition-all duration-300 hover:border-slate-600 font-medium backdrop-blur-md shadow-inner"
        />

        <div className="absolute right-2.5 flex items-center space-x-1.5">
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="p-1 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors"
              title="Limpiar búsqueda"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}

          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-xl bg-cyan-950/80 text-cyan-300 text-[11px] font-mono border border-cyan-500/35 shadow-sm">
            <span className="h-1.5 w-1.5 rounded-full bg-cyan-400 animate-pulse" />
            <span className="font-semibold">{resultsCount} {resultsCount === 1 ? 'activo' : 'activos'}</span>
          </div>
        </div>
      </div>
    </div>
  );
};
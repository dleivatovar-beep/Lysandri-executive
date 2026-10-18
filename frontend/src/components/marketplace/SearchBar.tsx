import React from 'react';
import { Search, X } from 'lucide-react';

interface SearchBarProps {
  searchQuery: string;
  setSearchQuery: (query: string) => void;
  resultsCount: number;
}

export const SearchBar: React.FC<SearchBarProps> = ({ searchQuery, setSearchQuery, resultsCount }) => (
  <div className="lx-search">
    <div className="lx-search-field">
      <Search aria-hidden="true" />
      <input
        type="search"
        aria-label="Buscar programas"
        value={searchQuery}
        onChange={(event) => setSearchQuery(event.target.value)}
        placeholder="Buscar programas, tecnologías o habilidades..."
      />
      {searchQuery && (
        <button type="button" aria-label="Limpiar búsqueda" onClick={() => setSearchQuery('')}>
          <X aria-hidden="true" />
        </button>
      )}
    </div>
    <span className="lx-search-count" aria-live="polite">{resultsCount} {resultsCount === 1 ? 'programa' : 'programas'}</span>
  </div>
);

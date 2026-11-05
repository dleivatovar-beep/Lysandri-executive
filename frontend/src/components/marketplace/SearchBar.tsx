import React from 'react';
import { Search, X } from 'lucide-react';

interface SearchBarProps {
  searchQuery: string;
  setSearchQuery: (query: string) => void;
}

export const SearchBar: React.FC<SearchBarProps> = ({ searchQuery, setSearchQuery }) => {
  return (
    <div className="lx-search" role="search">
      <div className="lx-search-field">
        <Search aria-hidden="true" />
        <input
          type="search"
          aria-label="Buscar programas"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Buscar programas por nombre o área..."
        />
        {searchQuery && (
          <button type="button" aria-label="Limpiar búsqueda" onClick={() => setSearchQuery('')} title="Limpiar búsqueda">
            <X aria-hidden="true" />
          </button>
        )}
      </div>
    </div>
);
};

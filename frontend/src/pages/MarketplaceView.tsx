import React, { useMemo, useState } from 'react';
import { Layers } from 'lucide-react';
import { Playbook, Category } from '../types';
import { SearchBar } from '../components/marketplace/SearchBar';
import { CategoryFilter } from '../components/marketplace/CategoryFilter';
import { PlaybookCard } from '../components/marketplace/PlaybookCard';

interface MarketplaceViewProps {
  playbooks: Playbook[];
  categories: Category[];
  onSelectPlaybook?: (playbook: Playbook) => void;
}

export const MarketplaceView: React.FC<MarketplaceViewProps> = ({
  playbooks,
  categories,
  onSelectPlaybook,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');

  const filteredPlaybooks = useMemo(() => {
    return playbooks.filter((playbook) => {
      const matchesCategory =
        selectedCategory === 'ALL' || playbook.category === selectedCategory;

      const query = searchQuery.toLowerCase();

      const matchesSearch =
        searchQuery.trim() === '' ||
        playbook.title.toLowerCase().includes(query) ||
        playbook.description.toLowerCase().includes(query) ||
        playbook.tags.some((tag) => tag.toLowerCase().includes(query));

      return matchesCategory && matchesSearch;
    });
  }, [playbooks, selectedCategory, searchQuery]);

  return (
    <div className="space-y-6 pb-12">
      <section className="relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-colors duration-300 dark:border-slate-800 dark:bg-[#0d121a] dark:shadow-executive md:p-8">
        <div className="pointer-events-none absolute -right-12 -top-12 h-64 w-64 rounded-full bg-slate-100 blur-3xl dark:bg-cyan-500/10" />
        <div className="pointer-events-none absolute -bottom-12 -left-12 h-64 w-64 rounded-full bg-slate-100 blur-3xl dark:bg-indigo-500/10" />

        <div className="relative z-10 max-w-3xl">
          <h1 className="text-2xl font-extrabold leading-tight tracking-tight text-slate-900 md:text-3xl dark:text-slate-100">
            Lysandri Executive:{' '}
            <span className="text-indigo-600 dark:text-cyan-400">
              Centro de Capacitación y Recursos
            </span>
          </h1>

          <p className="mt-4 text-xs leading-relaxed text-slate-600 md:text-sm dark:text-slate-400">
            Explora nuestro catálogo de cursos especializados, biblioteca técnica
            y recursos diseñados para acelerar tu crecimiento profesional.
          </p>
        </div>
      </section>

      <div className="space-y-4">
        <SearchBar
          searchQuery={searchQuery}
          setSearchQuery={setSearchQuery}
          resultsCount={filteredPlaybooks.length}
        />

        <CategoryFilter
          categories={categories}
          selectedCategory={selectedCategory}
          onSelectCategory={setSelectedCategory}
        />
      </div>

      {filteredPlaybooks.length > 0 ? (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
          {filteredPlaybooks.map((playbook) => (
            <PlaybookCard
              key={playbook.id}
              playbook={playbook}
              onSelect={onSelectPlaybook}
            />
          ))}
        </div>
      ) : (
        <div className="space-y-3 rounded-2xl border border-slate-200 bg-white p-12 text-center dark:border-slate-800 dark:bg-slate-900/50">
          <Layers className="mx-auto h-10 w-10 text-slate-400 dark:text-slate-600" />
          <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300">
            No se encontraron resultados
          </h3>
          <p className="mx-auto max-w-sm text-xs text-slate-500">
            Intenta modificar los términos de búsqueda o cambiar la categoría.
          </p>
          <button
            onClick={() => {
              setSearchQuery('');
              setSelectedCategory('ALL');
            }}
            className="mt-2 rounded-xl bg-slate-100 px-4 py-2 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-200 dark:bg-slate-800 dark:text-cyan-400 dark:hover:bg-slate-700"
          >
            Restablecer filtros
          </button>
        </div>
      )}
    </div>
  );
};
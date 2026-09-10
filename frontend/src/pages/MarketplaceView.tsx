import React, {
  useMemo,
  useState,
} from 'react';

import {
  Layers,
  LoaderCircle,
  RefreshCw,
  ShieldAlert,
} from 'lucide-react';

import { CategoryFilter } from '../components/marketplace/CategoryFilter';
import { PlaybookCard } from '../components/marketplace/PlaybookCard';
import { SearchBar } from '../components/marketplace/SearchBar';

import {
  Category,
  Playbook,
} from '../types';

interface MarketplaceViewProps {
  playbooks: Playbook[];
  categories: Category[];
  isLoading?: boolean;
  errorMessage?: string;
  selectingId?: string | null;
  onRetry?: () => void;

  onSelectPlaybook?: (
    playbook: Playbook,
  ) => void | Promise<void>;
}

export const MarketplaceView:
  React.FC<MarketplaceViewProps> = ({
    playbooks,
    categories,
    isLoading = false,
    errorMessage = '',
    selectingId = null,
    onRetry,
    onSelectPlaybook,
  }) => {
    const [
      searchQuery,
      setSearchQuery,
    ] = useState('');

    const [
      selectedCategory,
      setSelectedCategory,
    ] = useState('ALL');

    const filteredPlaybooks =
      useMemo(() => {
        const normalizedQuery =
          searchQuery
            .trim()
            .toLowerCase();

        return playbooks.filter(
          (playbook) => {
            const matchesCategory =
              selectedCategory === 'ALL' ||
              playbook.category ===
                selectedCategory;

            const matchesSearch =
              !normalizedQuery ||
              playbook.title
                .toLowerCase()
                .includes(normalizedQuery) ||
              playbook.description
                .toLowerCase()
                .includes(normalizedQuery) ||
              playbook.tags.some((tag) =>
                tag
                  .toLowerCase()
                  .includes(
                    normalizedQuery,
                  ),
              );

            return (
              matchesCategory &&
              matchesSearch
            );
          },
        );
      }, [
        playbooks,
        searchQuery,
        selectedCategory,
      ]);

    return (
      <div className="space-y-6 pb-12">
        <section className="relative overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-[#0d121a] md:p-8">
          <div className="pointer-events-none absolute -right-12 -top-12 h-64 w-64 rounded-full bg-cyan-500/10 blur-3xl" />

          <div className="pointer-events-none absolute -bottom-12 -left-12 h-64 w-64 rounded-full bg-indigo-500/10 blur-3xl" />

          <div className="relative z-10 max-w-3xl">
            <h1 className="text-2xl font-extrabold leading-tight tracking-tight text-slate-900 md:text-3xl dark:text-slate-100">
              Lysandri Executive:{' '}

              <span className="text-indigo-600 dark:text-cyan-400">
                Centro de Capacitación y
                Recursos
              </span>
            </h1>

            <p className="mt-4 text-xs leading-relaxed text-slate-600 md:text-sm dark:text-slate-400">
              Explora los programas académicos
              publicados y elige el que se adapte
              a tus objetivos profesionales.
            </p>
          </div>
        </section>

        {!isLoading &&
          !errorMessage &&
          playbooks.length > 0 && (
            <div className="space-y-4">
              <SearchBar
                searchQuery={searchQuery}
                setSearchQuery={
                  setSearchQuery
                }
                resultsCount={
                  filteredPlaybooks.length
                }
              />

              <CategoryFilter
                categories={categories}
                selectedCategory={
                  selectedCategory
                }
                onSelectCategory={
                  setSelectedCategory
                }
              />
            </div>
          )}

        {isLoading ? (
          <div className="flex min-h-72 items-center justify-center rounded-2xl border border-cyan-500/10 bg-white dark:border-slate-800 dark:bg-[#0d121a]">
            <div className="flex items-center gap-3 text-sm text-slate-500">
              <LoaderCircle className="h-5 w-5 animate-spin text-cyan-500" />

              Cargando programas...
            </div>
          </div>
        ) : errorMessage ? (
          <div className="flex min-h-72 flex-col items-center justify-center rounded-2xl border border-rose-500/20 bg-white p-8 text-center dark:bg-[#0d121a]">
            <ShieldAlert className="h-10 w-10 text-rose-500" />

            <h2 className="mt-4 text-base font-bold text-slate-900 dark:text-white">
              No se pudo cargar el catálogo
            </h2>

            <p className="mt-2 max-w-md text-sm text-slate-500">
              {errorMessage}
            </p>

            {onRetry && (
              <button
                type="button"
                onClick={onRetry}
                className="mt-5 flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white dark:bg-gradient-to-r dark:from-cyan-500 dark:to-indigo-600"
              >
                <RefreshCw className="h-4 w-4" />
                Reintentar
              </button>
            )}
          </div>
        ) : filteredPlaybooks.length >
          0 ? (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
            {filteredPlaybooks.map(
              (playbook) => (
                <PlaybookCard
                  key={playbook.id}
                  playbook={playbook}
                  isSubmitting={
                    selectingId ===
                    playbook.id
                  }
                  onSelect={
                    onSelectPlaybook
                  }
                />
              ),
            )}
          </div>
        ) : (
          <div className="space-y-3 rounded-2xl border border-slate-200 bg-white p-12 text-center dark:border-slate-800 dark:bg-slate-900/50">
            <Layers className="mx-auto h-10 w-10 text-slate-400 dark:text-slate-600" />

            <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300">
              {playbooks.length === 0
                ? 'No hay programas publicados'
                : 'No se encontraron resultados'}
            </h3>

            <p className="mx-auto max-w-sm text-xs text-slate-500">
              {playbooks.length === 0
                ? 'Los programas aparecerán aquí cuando sean registrados en la plataforma.'
                : 'Modifica la búsqueda o selecciona otra categoría.'}
            </p>

            {playbooks.length > 0 && (
              <button
                type="button"
                onClick={() => {
                  setSearchQuery('');
                  setSelectedCategory(
                    'ALL',
                  );
                }}
                className="mt-2 rounded-xl bg-slate-100 px-4 py-2 text-xs font-medium text-slate-700 dark:bg-slate-800 dark:text-cyan-400"
              >
                Restablecer filtros
              </button>
            )}
          </div>
        )}
      </div>
    );
  };
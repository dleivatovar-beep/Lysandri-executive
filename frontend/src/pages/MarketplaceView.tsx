// src/pages/MarketplaceView.tsx
import React, { useState, useMemo } from 'react';
import { 
  Sparkles, 
  Layers, 
  ShieldCheck, 
  Cpu, 
  TrendingUp 
} from 'lucide-react';
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
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');

  // Reactive filtering
  const filteredPlaybooks = useMemo(() => {
    return playbooks.filter((pb) => {
      const matchesCategory =
        selectedCategory === 'ALL' || pb.category === selectedCategory;

      const matchesSearch =
        searchQuery.trim() === '' ||
        pb.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        pb.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
        pb.tags.some((tag) => tag.toLowerCase().includes(searchQuery.toLowerCase()));

      return matchesCategory && matchesSearch;
    });
  }, [playbooks, selectedCategory, searchQuery]);

  return (
    <div className="space-y-6 pb-12">
      {/* Executive Hero Banner */}
      <div className="relative overflow-hidden rounded-2xl glass-card p-6 md:p-8 border border-slate-800 shadow-executive">
        <div className="absolute -top-12 -right-12 w-64 h-64 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-12 -left-12 w-64 h-64 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 space-y-4 max-w-3xl">
          <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-cyan-500/10 text-cyan-300 border border-cyan-500/20 text-xs font-mono">
            <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
            <span>Catálogo Validado por CTOs & Arquitectos Principales</span>
          </div>

          <h1 className="text-2xl md:text-3xl font-extrabold text-slate-100 tracking-tight leading-tight">
            Marketplace Ejecutivo de Activos Tecnológicos & <span className="text-gradient-cyan">Playbooks de Arquitectura</span>
          </h1>

          <p className="text-xs md:text-sm text-slate-400 leading-relaxed">
            Obtenga aceleradores de software listos para producción, marcos de costos FinOps y plantillas de seguridad Zero-Trust diseñadas para acelerar la toma de decisiones C-Level.
          </p>

          {/* Quick Metrics Bar */}
          <div className="pt-2 grid grid-cols-2 sm:grid-cols-3 gap-4 border-t border-slate-800/80">
            <div className="flex items-center space-x-2">
              <div className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-cyan-400">
                <Cpu className="w-4 h-4" />
              </div>
              <div>
                <span className="text-xs font-bold text-slate-200 block font-mono">100% IaC & Code</span>
                <span className="text-[10px] text-slate-500">Despliegue directo</span>
              </div>
            </div>

            <div className="flex items-center space-x-2">
              <div className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-indigo-400">
                <TrendingUp className="w-4 h-4" />
              </div>
              <div>
                <span className="text-xs font-bold text-slate-200 block font-mono">FinOps Evaluado</span>
                <span className="text-[10px] text-slate-500">ROI probado</span>
              </div>
            </div>

            <div className="hidden sm:flex items-center space-x-2">
              <div className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-emerald-400">
                <ShieldCheck className="w-4 h-4" />
              </div>
              <div>
                <span className="text-xs font-bold text-slate-200 block font-mono">Zero-Trust Validated</span>
                <span className="text-[10px] text-slate-500">Compliance listo</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Filter & Search Toolbar */}
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

      {/* Playbooks Responsive Grid */}
      {filteredPlaybooks.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3 gap-6">
          {filteredPlaybooks.map((playbook) => (
            <PlaybookCard 
              key={playbook.id} 
              playbook={playbook} 
              onSelect={onSelectPlaybook}
            />
          ))}
        </div>
      ) : (
        /* Empty State */
        <div className="p-12 text-center glass-panel rounded-2xl border border-slate-800 space-y-3">
          <Layers className="w-10 h-10 text-slate-600 mx-auto" />
          <h3 className="text-sm font-bold text-slate-300">No se encontraron activos tecnológicos</h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            Intente modificar los términos de búsqueda o cambiar la categoría seleccionada.
          </p>
          <button
            onClick={() => {
              setSearchQuery('');
              setSelectedCategory('ALL');
            }}
            className="mt-2 px-4 py-2 rounded-xl bg-slate-800 text-cyan-400 text-xs font-medium hover:bg-slate-700 transition-colors"
          >
            Restablecer Filtros
          </button>
        </div>
      )}
    </div>
  );
};

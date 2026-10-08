import React from 'react';
import {
  BookOpen,
  LucideIcon,
  ShieldCheck,
  TrendingUp,
  GraduationCap,
  Sparkles,
  Zap,
  Award,
} from 'lucide-react';
import { Category } from '../../types';

interface CategoryFilterProps {
  categories: Category[];
  selectedCategory: string;
  onSelectCategory: (categoryId: string) => void;
}

const resolveCategoryIcon = (categoryName: string): LucideIcon => {
  const norm = categoryName.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  if (norm.includes('todo') || norm.includes('all')) return GraduationCap;
  if (norm.includes('inteligencia') || norm.includes('ia')) return Sparkles;
  if (norm.includes('seguridad') || norm.includes('riesgo')) return ShieldCheck;
  if (norm.includes('finanza') || norm.includes('costo') || norm.includes('cloud')) return TrendingUp;
  if (norm.includes('automatiz') || norm.includes('proceso') || norm.includes('no-code')) return Zap;
  if (norm.includes('liderazgo') || norm.includes('estrategia') || norm.includes('gestion')) return Award;
  return BookOpen;
};

export const CategoryFilter: React.FC<CategoryFilterProps> = ({
  categories,
  selectedCategory,
  onSelectCategory,
}) => {
  return (
    <div className="flex items-center space-x-2.5 overflow-x-auto py-1 scrollbar-none">
      {categories.map((category) => {
        const IconComponent = resolveCategoryIcon(category.name);

        const isSelected =
          selectedCategory === category.name ||
          (category.id === 'cat-all' && selectedCategory === 'ALL');

        return (
          <button
            key={category.id}
            type="button"
            onClick={() =>
              onSelectCategory(
                category.id === 'cat-all' ? 'ALL' : category.name,
              )
            }
            className={`group edtech-filter-pill relative flex items-center space-x-2.5 whitespace-nowrap rounded-2xl border px-4 py-2.5 text-xs font-semibold backdrop-blur-md active:scale-95 ${
              isSelected
                ? 'border-cyan-400 bg-gradient-to-r from-cyan-950/90 via-slate-900 to-indigo-950/90 text-cyan-200 shadow-lg shadow-cyan-500/20 ring-1 ring-cyan-400/40'
                : 'border-slate-800 bg-slate-950/70 text-slate-300 hover:border-slate-700 hover:bg-slate-900/80 hover:text-white'
            }`}
          >
            <span
              className={`flex h-6 w-6 items-center justify-center rounded-xl transition-transform duration-300 group-hover:scale-110 ${
                isSelected
                  ? 'bg-cyan-500/25 text-cyan-300 shadow-sm shadow-cyan-500/30'
                  : 'bg-slate-800/80 text-slate-400 group-hover:bg-slate-800 group-hover:text-cyan-400'
              }`}
            >
              <IconComponent className="h-3.5 w-3.5" />
            </span>

            <span className="tracking-tight font-medium">
              {category.name === 'Todos los cursos' ? 'Todos los Programas' : category.name}
            </span>

            <span
              className={`ml-1 rounded-full px-2 py-0.5 font-mono text-[10px] font-bold transition-colors ${
                isSelected
                  ? 'bg-cyan-400/20 text-cyan-200 border border-cyan-400/40 shadow-sm'
                  : 'bg-slate-800/90 text-slate-400 border border-slate-700/60 group-hover:text-slate-200 group-hover:border-slate-600'
              }`}
            >
              {category.count}
            </span>
          </button>
        );
      })}
    </div>
  );
};
// src/components/marketplace/CategoryFilter.tsx
import React from 'react';
import { Layers, TrendingUp, Cpu, Bot, ShieldCheck, LucideIcon } from 'lucide-react';
import { Category } from '../../types';

interface CategoryFilterProps {
  categories: Category[];
  selectedCategory: string;
  onSelectCategory: (categoryId: string) => void;
}

const ICON_MAP: Record<string, LucideIcon> = {
  Layers, TrendingUp, Cpu, Bot, ShieldCheck
};

export const CategoryFilter: React.FC<CategoryFilterProps> = ({ categories, selectedCategory, onSelectCategory }) => {
  return (
    <div className="flex items-center space-x-2 overflow-x-auto pb-4 pt-2 px-1 -mx-1 scrollbar-none">
      {categories.map((cat) => {
        const IconComponent = ICON_MAP[cat.iconName] || Layers;
        const isSelected = selectedCategory === cat.name || (cat.id === 'cat-all' && selectedCategory === 'ALL');

        return (
          <button
            key={cat.id}
            onClick={() => onSelectCategory(cat.id === 'cat-all' ? 'ALL' : cat.name)}
            className={`group flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-medium whitespace-nowrap transition-all duration-300 ease-out active:scale-95 hover:-translate-y-0.5 border ${
              isSelected
                ? 'bg-slate-800 text-white border-slate-700 shadow-sm dark:bg-slate-800 dark:text-cyan-300 dark:border-cyan-500/50 dark:shadow-glow-cyan'
                : 'bg-white dark:bg-slate-900/60 text-slate-600 dark:text-slate-400 border-slate-200 dark:border-slate-800 hover:text-slate-900 dark:hover:text-slate-200 hover:border-slate-300 dark:hover:border-slate-700 hover:bg-slate-50'
            }`}
          >
            <IconComponent 
              className={`w-4 h-4 transition-transform duration-300 group-hover:scale-110 ${
                isSelected ? 'text-white dark:text-cyan-400' : 'text-slate-400 group-hover:text-slate-600 dark:group-hover:text-cyan-400'
              }`} 
            />
            <span>{cat.name}</span>
            <span 
              className={`ml-1 text-[10px] px-1.5 py-0.5 rounded-full font-mono transition-colors duration-300 ${
                isSelected 
                  ? 'bg-slate-600 text-slate-100 dark:bg-cyan-500/30 dark:text-cyan-200' 
                  : 'bg-slate-100 dark:bg-slate-800 text-slate-500 group-hover:bg-slate-200 dark:group-hover:bg-slate-700'
              }`}
            >
              {cat.count}
            </span>
          </button>
        );
      })}
    </div>
  );
};
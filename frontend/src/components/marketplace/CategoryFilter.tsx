// src/components/marketplace/CategoryFilter.tsx
import React from 'react';
import { 
  Layers, 
  TrendingUp, 
  Cpu, 
  Bot, 
  ShieldCheck, 
  LucideIcon 
} from 'lucide-react';
import { Category } from '../../types';

interface CategoryFilterProps {
  categories: Category[];
  selectedCategory: string;
  onSelectCategory: (categoryId: string) => void;
}

const ICON_MAP: Record<string, LucideIcon> = {
  Layers,
  TrendingUp,
  Cpu,
  Bot,
  ShieldCheck
};

export const CategoryFilter: React.FC<CategoryFilterProps> = ({
  categories,
  selectedCategory,
  onSelectCategory,
}) => {
  return (
    <div className="flex items-center space-x-2 overflow-x-auto pb-2 scrollbar-none">
      {categories.map((cat) => {
        const IconComponent = ICON_MAP[cat.iconName] || Layers;
        const isSelected = selectedCategory === cat.name || (cat.id === 'cat-all' && selectedCategory === 'ALL');

        return (
          <button
            key={cat.id}
            onClick={() => onSelectCategory(cat.id === 'cat-all' ? 'ALL' : cat.name)}
            className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-medium whitespace-nowrap transition-all duration-200 border ${
              isSelected
                ? 'bg-gradient-to-r from-cyan-500/20 to-indigo-500/20 text-cyan-300 border-cyan-500/50 shadow-glow-cyan'
                : 'bg-slate-900/60 text-slate-400 border-slate-800 hover:text-slate-200 hover:border-slate-700'
            }`}
          >
            <IconComponent className={`w-4 h-4 ${isSelected ? 'text-cyan-400' : 'text-slate-500'}`} />
            <span>{cat.name}</span>
            <span className={`ml-1 text-[10px] px-1.5 py-0.5 rounded-full font-mono ${
              isSelected ? 'bg-cyan-500/30 text-cyan-200' : 'bg-slate-800 text-slate-500'
            }`}>
              {cat.count}
            </span>
          </button>
        );
      })}
    </div>
  );
};

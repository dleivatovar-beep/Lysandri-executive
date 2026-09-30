import React from 'react';

import {
  BookOpen,
  Bot,
  Cpu,
  Layers,
  LucideIcon,
  ShieldCheck,
  TrendingUp,
} from 'lucide-react';

import { Category } from '../../types';

interface CategoryFilterProps {
  categories: Category[];
  selectedCategory: string;

  onSelectCategory: (
    categoryId: string,
  ) => void;
}

const ICON_MAP: Record<
  string,
  LucideIcon
> = {
  Layers,
  TrendingUp,
  Cpu,
  Bot,
  ShieldCheck,
  BookOpen,
};

export const CategoryFilter:
  React.FC<CategoryFilterProps> = ({
    categories,
    selectedCategory,
    onSelectCategory,
  }) => {
    return (
      <div className="-mx-1 flex items-center space-x-2 overflow-x-auto px-1 pb-4 pt-2 scrollbar-none">
        {categories.map((category) => {
          const IconComponent =
            ICON_MAP[
              category.iconName
            ] || Layers;

          const isSelected =
            selectedCategory ===
              category.name ||
            (category.id === 'cat-all' &&
              selectedCategory ===
                'ALL');

          return (
            <button
              key={category.id}
              type="button"
              onClick={() =>
                onSelectCategory(
                  category.id ===
                    'cat-all'
                    ? 'ALL'
                    : category.name,
                )
              }
              className={`group flex items-center space-x-2 whitespace-nowrap rounded-xl border px-4 py-2 text-xs font-medium transition-all duration-300 ease-out hover:-translate-y-0.5 active:scale-95 ${
                isSelected
                  ? 'border-slate-700 bg-slate-800 text-white shadow-sm dark:border-cyan-500/50 dark:bg-slate-800 dark:text-cyan-300 dark:shadow-glow-cyan'
                  : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900 dark:border-slate-800 dark:bg-slate-900/60 dark:text-slate-400 dark:hover:border-slate-700 dark:hover:text-slate-200'
              }`}
            >
              <IconComponent
                className={`h-4 w-4 transition-transform duration-300 group-hover:scale-110 ${
                  isSelected
                    ? 'text-white dark:text-cyan-400'
                    : 'text-slate-400 group-hover:text-slate-600 dark:group-hover:text-cyan-400'
                }`}
              />

              <span>
                {category.name}
              </span>

              <span
                className={`ml-1 rounded-full px-1.5 py-0.5 font-mono text-[10px] transition-colors duration-300 ${
                  isSelected
                    ? 'bg-slate-600 text-slate-100 dark:bg-cyan-500/30 dark:text-cyan-200'
                    : 'bg-slate-100 text-slate-500 group-hover:bg-slate-200 dark:bg-slate-800 dark:group-hover:bg-slate-700'
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
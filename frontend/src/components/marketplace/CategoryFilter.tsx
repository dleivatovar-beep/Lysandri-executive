import React from 'react';
import { Category } from '../../types';

interface CategoryFilterProps {
  categories: Category[];
  selectedCategory: string;
  onSelectCategory: (categoryId: string) => void;
}

export const CategoryFilter: React.FC<CategoryFilterProps> = ({
  categories,
  selectedCategory,
  onSelectCategory,
}) => (
  <div className="lx-category-filter" role="group" aria-label="Filtrar por categoría">
    {categories.map((category) => {
      const value = category.id === 'cat-all' ? 'ALL' : category.name;
      const isSelected = selectedCategory === value;
      return (
        <button
          key={category.id}
          type="button"
          aria-pressed={isSelected}
          onClick={() => onSelectCategory(value)}
        >
          <span>{category.name}</span>
          <small>{category.count}</small>
        </button>
      );
    })}
  </div>
);

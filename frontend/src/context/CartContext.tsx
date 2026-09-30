import React, { createContext, useContext, useState, useEffect } from 'react';
import { Playbook } from '../types';

interface CartContextType {
  cartItems: Playbook[];
  addToCart: (playbook: Playbook) => void;
  removeFromCart: (programId: number) => void;
  clearCart: () => void;
  isCartOpen: boolean;
  setIsCartOpen: (open: boolean) => void;
  openCart: () => void;
  closeCart: () => void;
  totalAmount: number;
  itemCount: number;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

const CART_STORAGE_KEY = 'lysandri_cart_items';

const getPrice = (playbook: Playbook): number => {
  if (playbook.tier === 'ENTERPRISE') return 950;
  if (playbook.tier === 'ADVANCED') return 899;
  return 799;
};

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [cartItems, setCartItems] = useState<Playbook[]>(() => {
    try {
      const stored = localStorage.getItem(CART_STORAGE_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  });

  const [isCartOpen, setIsCartOpen] = useState(false);

  useEffect(() => {
    try {
      localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cartItems));
    } catch (e) {
      console.warn('Failed to save cart to localStorage', e);
    }
  }, [cartItems]);

  const addToCart = (playbook: Playbook) => {
    setCartItems((prev) => {
      if (prev.some((item) => item.programId === playbook.programId)) {
        return prev;
      }
      return [...prev, playbook];
    });
    setIsCartOpen(true);
  };

  const removeFromCart = (programId: number) => {
    setCartItems((prev) => prev.filter((item) => item.programId !== programId));
  };

  const clearCart = () => {
    setCartItems([]);
  };

  const openCart = () => setIsCartOpen(true);
  const closeCart = () => setIsCartOpen(false);

  const totalAmount = cartItems.reduce((acc, item) => acc + getPrice(item), 0);
  const itemCount = cartItems.length;

  return (
    <CartContext.Provider
      value={{
        cartItems,
        addToCart,
        removeFromCart,
        clearCart,
        isCartOpen,
        setIsCartOpen,
        openCart,
        closeCart,
        totalAmount,
        itemCount,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};

export const useCart = (): CartContextType => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};

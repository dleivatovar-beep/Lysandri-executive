import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import { Playbook } from '../types';
import { academicService } from '../services/academicService';
import { FALLBACK_PLAYBOOKS, toCatalogCourse } from '../services/courseCatalog';

export type CartVerificationStatus = 'IDLE' | 'PENDING' | 'VERIFIED' | 'ERROR';

export interface CartContextType {
  cartItems: Playbook[];
  addToCart: (playbook: Playbook) => boolean;
  removeFromCart: (programId: number) => void;
  clearCart: () => void;
  removeUnpurchasableItems: () => void;
  isCartOpen: boolean;
  setIsCartOpen: (open: boolean) => void;
  openCart: () => void;
  closeCart: () => void;
  totalAmount: number | null;
  itemCount: number;
  hasUnpurchasableItems: boolean;
  unpurchasableItems: Playbook[];
  currencies: string[];
  hasMixedCurrencies: boolean;
  totalsByCurrency: Record<string, number | null>;
  primaryCurrency: string;
  verificationStatus: CartVerificationStatus;
  verificationError: string;
  retryVerification: () => void;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

const CART_STORAGE_KEY = 'lysandri_cart_items';
const isSampleProgram = (item: Playbook): boolean => (
  item.origin === 'SAMPLE' ||
  item.isSample === true ||
  item.isDemo === true ||
  (typeof item.id === 'string' && item.id.startsWith('sample-'))
);

/**
 * Determina si un programa o playbook puede ser adquirido comercialmente.
 * Elimina reglas arbitrarias por ID (ej. ID >= 1000) y valida procedencia explícita
 * y disponibilidad comercial validada por el backend.
 */
export const isPlaybookPurchasable = (item: Playbook | null | undefined): boolean => {
  if (!item) return false;
  if (item.origin === 'SAMPLE' || item.isSample === true || item.isDemo === true) return false;
  if (typeof item.id === 'string' && item.id.startsWith('sample-')) return false;
  if (!Number.isInteger(item.programId) || item.programId <= 0) return false;
  if (item.disponibilidadVerificada !== true || item.disponibleParaCompra !== true) return false;
  if (item.activo !== true || item.precioAprobado !== true || item.moodleVerificado !== true) return false;
  if (item.noDisponible === true) return false;

  const price = getItemPrice(item);
  if (price === null || (price === 0 && item.esGratuito !== true) || (price > 0 && item.esGratuito === true)) return false;
  if (!item.currency || !['PEN', 'USD'].includes(item.currency.trim().toUpperCase())) return false;

  return true;
};

/**
 * Retorna el motivo descriptivo por el cual un programa no es comercialmente adquirible.
 */
export const getUnpurchasableReason = (item: Playbook | null | undefined): string => {
  if (!item) return 'Programa no válido';
  if (
    item.origin === 'SAMPLE' ||
    item.isSample === true ||
    item.isDemo === true ||
    (typeof item.id === 'string' && item.id.startsWith('sample-'))
  ) {
    return 'Programa de muestra académica (solo consulta)';
  }
  if (item.disponibilidadVerificada !== true) {
    return 'Disponibilidad comercial pendiente de confirmar con el servidor';
  }
  if (item.motivoNoDisponible) {
    return item.motivoNoDisponible;
  }
  if (item.activo !== true) {
    return 'Programa no activo para venta comercial';
  }
  if (item.precioAprobado !== true) {
    return 'Precio comercial pendiente de aprobación';
  }
  if (item.moodleVerificado !== true) {
    return 'Vinculación con Moodle pendiente de verificación';
  }
  const price = getItemPrice(item);
  if (price === null || (price === 0 && item.esGratuito !== true)) {
    return 'Precio comercial no asignado o inválido';
  }
  if (!item.currency || !['PEN', 'USD'].includes(item.currency.trim().toUpperCase())) {
    return 'Moneda comercial ausente o no admitida';
  }
  if (item.noDisponible || item.disponibleParaCompra !== true) {
    return 'No disponible actualmente en el catálogo comercial';
  }
  return 'No disponible para compra comercial';
};

/**
 * Obtiene el precio numérico real autorizado del programa.
 * Nunca utiliza tarifas hardcodeadas por tier.
 */
export const getItemPrice = (item: Playbook): number | null => {
  if (typeof item.price === 'number' && Number.isFinite(item.price) && item.price >= 0) {
    return item.price;
  }
  return null;
};

const sanitizeCartItem = (item: Playbook): Playbook => {
  if (!item) return item;
  if (item.origin === 'SAMPLE' || (typeof item.id === 'string' && item.id.startsWith('sample-'))) {
    return {
      ...item,
      origin: 'SAMPLE',
      isSample: true,
      isDemo: true,
    };
  }
  return item;
};

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [cartItems, setCartItems] = useState<Playbook[]>(() => {
    try {
      const stored = localStorage.getItem(CART_STORAGE_KEY);
      const items: Playbook[] = stored ? JSON.parse(stored) : [];
      if (!Array.isArray(items)) return [];
      const uniqueItems = new Map<number, Playbook>();
      items.filter((item) => item && Number.isInteger(item.programId) && item.programId > 0)
        .forEach((item) => {
          const sample = isSampleProgram(item)
            ? FALLBACK_PLAYBOOKS.find((program) => program.programId === item.programId)
            : undefined;
          uniqueItems.set(item.programId, {
            ...sample,
            ...sanitizeCartItem(item),
            price: sample?.price,
            currency: sample?.currency,
            disponibleParaCompra: false,
            disponibilidadVerificada: false,
            noDisponible: false,
            motivoNoDisponible: undefined,
          });
        });
      return [...uniqueItems.values()];
    } catch {
      return [];
    }
  });

  const [isCartOpen, setIsCartOpen] = useState(false);
  const [verificationStatus, setVerificationStatus] = useState<CartVerificationStatus>('IDLE');
  const [verificationError, setVerificationError] = useState('');
  const [verificationAttempt, setVerificationAttempt] = useState(0);
  const availabilityRequestRef = useRef(0);

  const selectedProgramIds = cartItems.map((item) => item.programId).sort((a, b) => a - b);
  const selectionKey = selectedProgramIds.join(',');

  useEffect(() => {
    const requestId = ++availabilityRequestRef.current;
    if (selectedProgramIds.length === 0) {
      setVerificationStatus('IDLE');
      setVerificationError('');
      return;
    }

    setVerificationStatus('PENDING');
    setVerificationError('');
    setCartItems((previous) => previous.map((item) => ({
      ...item,
      disponibleParaCompra: false,
      disponibilidadVerificada: false,
      noDisponible: false,
      motivoNoDisponible: undefined,
    })));

    void academicService.getPurchaseAvailability(selectedProgramIds)
      .then((programs) => {
        if (requestId !== availabilityRequestRef.current) return;
        const byId = new Map<number, (typeof programs)[number]>();
        for (const program of programs) {
          if (
            !Number.isInteger(program.idPrograma) ||
            !selectedProgramIds.includes(program.idPrograma) ||
            byId.has(program.idPrograma) ||
            typeof program.disponibleParaCompra !== 'boolean'
          ) {
            throw new Error('El servidor devolvió datos de disponibilidad incompletos o inconsistentes.');
          }
          byId.set(program.idPrograma, program);
        }

        setCartItems((previous) => previous.map((item) => {
          if (item.origin === 'SAMPLE' || item.isSample === true || item.isDemo === true) {
            const sample = FALLBACK_PLAYBOOKS.find((program) => program.programId === item.programId);
            return {
              ...item,
              price: sample?.price,
              currency: sample?.currency,
              disponibleParaCompra: false,
              disponibilidadVerificada: true,
              noDisponible: true,
              motivoNoDisponible: 'Programa de muestra académica (solo consulta)',
            };
          }
          const current = byId.get(item.programId);
          if (!current) {
            return {
              ...item,
              disponibleParaCompra: false,
              disponibilidadVerificada: true,
              noDisponible: true,
              motivoNoDisponible: 'El programa ya no existe o no está disponible en la consulta comercial exacta.',
            };
          }
          const fresh = toCatalogCourse(current);
          return {
            ...item,
            title: fresh.title,
            price: fresh.price,
            currency: fresh.currency,
            coverUrl: fresh.coverUrl || item.coverUrl,
            category: fresh.category || item.category,
            tier: fresh.tier || item.tier,
            origin: 'BACKEND',
            isSample: false,
            isDemo: false,
            activo: fresh.activo,
            precioAprobado: fresh.precioAprobado,
            moodleVerificado: fresh.moodleVerificado,
            moodleCourseId: fresh.moodleCourseId,
            esGratuito: fresh.esGratuito,
            disponibleParaCompra: fresh.disponibleParaCompra,
            disponibilidadVerificada: true,
            noDisponible: fresh.disponibleParaCompra !== true,
            motivoNoDisponible: fresh.motivoNoDisponible,
          };
        }));
        setVerificationStatus('VERIFIED');
      })
      .catch((error: unknown) => {
        if (requestId !== availabilityRequestRef.current) return;
        setCartItems((previous) => previous.map((item) => ({
          ...item,
          disponibleParaCompra: false,
          disponibilidadVerificada: false,
          noDisponible: false,
          motivoNoDisponible: undefined,
        })));
        setVerificationError(
          error instanceof Error
            ? error.message
            : 'No se pudo confirmar la disponibilidad comercial. Revisa tu conexión e inténtalo de nuevo.'
        );
        setVerificationStatus('ERROR');
      });
  }, [selectionKey, verificationAttempt]);

  useEffect(() => {
    try {
      localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cartItems));
    } catch (e) {
      console.warn('Failed to save cart to localStorage', e);
    }
  }, [cartItems]);

  const addToCart = (playbook: Playbook): boolean => {
    if (!playbook || !Number.isInteger(playbook.programId) || playbook.programId <= 0) return false;
    const cartItem = playbook;
    const esMuestra = playbook.origin === 'SAMPLE' || playbook.isSample === true || playbook.isDemo === true ||
      (typeof playbook.id === 'string' && playbook.id.startsWith('sample-'));
    if (!esMuestra && !isPlaybookPurchasable(cartItem)) {
      const motivo = getUnpurchasableReason(playbook);
      console.warn(`[Cart] El programa "${playbook.title}" no se puede añadir: ${motivo}`);
      return false;
    }
    setCartItems((prev) => {
      if (prev.some((item) => item.programId === playbook.programId)) {
        return prev;
      }
      return [...prev, cartItem];
    });
    setIsCartOpen(true);
    return true;
  };

  const removeFromCart = (programId: number) => {
    setCartItems((prev) => prev.filter((item) => item.programId !== programId));
  };

  const clearCart = () => {
    setCartItems([]);
  };

  const removeUnpurchasableItems = () => {
    setCartItems((prev) => prev.filter((item) => (
      !isSampleProgram(item) &&
      !(item.disponibilidadVerificada === true && !isPlaybookPurchasable(item))
    )));
  };

  const retryVerification = () => setVerificationAttempt((attempt) => attempt + 1);

  const openCart = () => setIsCartOpen(true);
  const closeCart = () => setIsCartOpen(false);

  const unpurchasableItems = cartItems.filter((item) => (
    isSampleProgram(item) ||
    (item.disponibilidadVerificada === true && !isPlaybookPurchasable(item))
  ));
  const hasUnpurchasableItems = unpurchasableItems.length > 0;

  // Detección y agrupación multi-moneda
  const currencies = Array.from(new Set(
    cartItems
      .map((item) => item.currency?.trim().toUpperCase())
      .filter((value): value is string => value === 'PEN' || value === 'USD')
  ));
  const hasMixedCurrencies = currencies.length > 1;
  const primaryCurrency = currencies[0] || '';

  const totalsByCurrency: Record<string, number | null> = {};
  currencies.forEach((cur) => {
    const itemsInCurrency = cartItems.filter((item) => item.currency?.trim().toUpperCase() === cur);
    const prices = itemsInCurrency.map(getItemPrice);
    totalsByCurrency[cur] = prices.some((price) => price === null)
      ? null
      : prices.reduce<number>((total, price) => total + (price ?? 0), 0);
  });

  const totalAmount = primaryCurrency ? totalsByCurrency[primaryCurrency] : null;
  const itemCount = cartItems.length;

  return (
    <CartContext.Provider
      value={{
        cartItems,
        addToCart,
        removeFromCart,
        clearCart,
        removeUnpurchasableItems,
        isCartOpen,
        setIsCartOpen,
        openCart,
        closeCart,
        totalAmount,
        itemCount,
        hasUnpurchasableItems,
        unpurchasableItems,
        currencies,
        hasMixedCurrencies,
        totalsByCurrency,
        primaryCurrency,
        verificationStatus,
        verificationError,
        retryVerification,
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

import React, { useState } from 'react';
import { X, Trash2, CreditCard, ShieldCheck, ArrowRight, LoaderCircle, ShoppingCart } from 'lucide-react';
import { Playbook } from '../../types';
import { storeService } from '../../services/storeService';

interface CartDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  cartItems: Playbook[];
  onRemoveItem: (programId: number) => void;
  onClearCart: () => void;
}

export const CartDrawer: React.FC<CartDrawerProps> = ({
  isOpen,
  onClose,
  cartItems,
  onRemoveItem,
  onClearCart,
}) => {
  const [isProcessing, setIsProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  if (!isOpen) return null;

  // Precios ejecutivos base estándar
  const getItemPrice = (item: Playbook): number => {
    if (item.tier === 'ENTERPRISE') return 950;
    if (item.tier === 'ADVANCED') return 899;
    return 799;
  };

  const total = cartItems.reduce((acc, item) => acc + getItemPrice(item), 0);

  const handleCheckout = async () => {
    if (cartItems.length === 0) return;
    setIsProcessing(true);
    setErrorMessage('');

    try {
      const ids = cartItems.map((item) => item.programId);
      const session = await storeService.checkout(ids);

      if (session.stripeCheckoutUrl) {
        window.location.href = session.stripeCheckoutUrl;
      } else {
        throw new Error('No se recibió la URL de pago de Stripe');
      }
    } catch (error: any) {
      setErrorMessage(
        error.response?.data?.message ||
          error.message ||
          'Fallo al iniciar el checkout con Stripe. Asegúrate de haber iniciado sesión.'
      );
      setIsProcessing(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-slate-950/70 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />

      <div className="fixed inset-y-0 right-0 flex max-w-full pl-10">
        <div className="w-screen max-w-md border-l border-cyan-500/20 bg-slate-900 text-slate-100 shadow-2xl">
          <div className="flex h-full flex-col">
            {/* Header */}
            <div className="flex items-center justify-between border-b border-slate-800 px-6 py-5">
              <div className="flex items-center gap-3">
                <div className="rounded-lg bg-cyan-500/10 p-2 text-cyan-400">
                  <ShoppingCart className="h-5 w-5" />
                </div>
                <div>
                  <h2 className="text-base font-bold text-white">Carrito Ejecutivo</h2>
                  <p className="text-xs text-slate-400">
                    {cartItems.length} programa{cartItems.length !== 1 ? 's' : ''} seleccionado{cartItems.length !== 1 ? 's' : ''}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={onClose}
                className="rounded-lg p-2 text-slate-400 hover:bg-slate-800 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Error banner */}
            {errorMessage && (
              <div className="mx-6 mt-4 rounded-xl border border-rose-500/30 bg-rose-950/50 p-3 text-xs text-rose-300">
                {errorMessage}
              </div>
            )}

            {/* Items List */}
            <div className="flex-1 overflow-y-auto px-6 py-4">
              {cartItems.length === 0 ? (
                <div className="flex h-full flex-col items-center justify-center text-center">
                  <ShoppingCart className="h-12 w-12 text-slate-600 mb-3" />
                  <p className="text-sm font-semibold text-slate-300">Tu carrito está vacío</p>
                  <p className="mt-1 text-xs text-slate-500 max-w-xs">
                    Explora nuestro catálogo directivo y añade los programas de tu interés profesional.
                  </p>
                </div>
              ) : (
                <div className="space-y-4">
                  {cartItems.map((item) => (
                    <div
                      key={item.id}
                      className="flex items-start justify-between gap-3 rounded-xl border border-slate-800 bg-slate-950/60 p-4 transition-all hover:border-slate-700"
                    >
                      <div className="flex-1 min-w-0">
                        <span className="inline-block rounded px-2 py-0.5 font-mono text-[9px] font-semibold text-cyan-400 bg-cyan-950/60 border border-cyan-800/50 mb-1">
                          {item.tier}
                        </span>
                        <h4 className="truncate text-sm font-semibold text-white">{item.title}</h4>
                        <p className="text-xs text-slate-400 mt-0.5">
                          Aprovisionamiento automático en Moodle LMS
                        </p>
                        <p className="mt-2 text-sm font-bold text-cyan-300">
                          ${getItemPrice(item)} USD
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => onRemoveItem(item.programId)}
                        className="rounded p-1.5 text-slate-500 hover:bg-slate-800 hover:text-rose-400 transition-colors"
                        title="Eliminar del carrito"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  ))}

                  <div className="pt-2 flex justify-end">
                    <button
                      type="button"
                      onClick={onClearCart}
                      className="text-xs text-slate-500 hover:text-slate-300 transition-colors"
                    >
                      Vaciar carrito
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Footer with Checkout */}
            {cartItems.length > 0 && (
              <div className="border-t border-slate-800 bg-slate-950/80 p-6">
                <div className="mb-4 space-y-2">
                  <div className="flex justify-between text-xs text-slate-400">
                    <span>Subtotal</span>
                    <span>${total.toFixed(2)} USD</span>
                  </div>
                  <div className="flex justify-between text-xs text-slate-400">
                    <span>Matrícula y Campus Moodle</span>
                    <span className="text-emerald-400 font-medium">Incluida</span>
                  </div>
                  <div className="flex justify-between border-t border-slate-800 pt-2 text-base font-bold text-white">
                    <span>Total de la Orden</span>
                    <span className="text-cyan-400">${total.toFixed(2)} USD</span>
                  </div>
                </div>

                <button
                  type="button"
                  disabled={isProcessing}
                  onClick={handleCheckout}
                  className="flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 py-3.5 text-sm font-bold text-white shadow-lg shadow-cyan-500/25 transition-all hover:opacity-95 active:scale-95 disabled:opacity-50"
                >
                  {isProcessing ? (
                    <>
                      <LoaderCircle className="h-4 w-4 animate-spin" />
                      <span>Conectando con Stripe...</span>
                    </>
                  ) : (
                    <>
                      <CreditCard className="h-4 w-4" />
                      <span>Pagar Seguro con Stripe</span>
                      <ArrowRight className="h-4 w-4 ml-1" />
                    </>
                  )}
                </button>

                <div className="mt-4 flex items-center justify-center gap-2 text-[10px] text-slate-500">
                  <ShieldCheck className="h-3.5 w-3.5 text-emerald-400" />
                  <span>Pagos 256-bit SSL encriptados vía Stripe Checkout</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

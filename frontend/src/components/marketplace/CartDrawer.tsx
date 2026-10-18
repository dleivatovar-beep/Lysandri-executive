import React, { useState } from 'react';
import { ArrowRight, LoaderCircle, ShoppingBag, Trash2, X } from 'lucide-react';
import { Playbook } from '../../types';
import { storeService } from '../../services/storeService';
import { useDialogAccessibility } from '../../hooks/useDialogAccessibility';
import './editorial-overlays.css';

interface CartDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  cartItems: Playbook[];
  onRemoveItem: (programId: number) => void;
  onClearCart: () => void;
}

const getItemPrice = (item: Playbook): number => {
  if (item.tier === 'ENTERPRISE') return 950;
  if (item.tier === 'ADVANCED') return 899;
  return 799;
};

export const CartDrawer: React.FC<CartDrawerProps> = ({ isOpen, onClose, cartItems, onRemoveItem, onClearCart }) => {
  const [isProcessing, setIsProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const dialogRef = useDialogAccessibility<HTMLDivElement>(isOpen, onClose);
  if (!isOpen) return null;

  const total = cartItems.reduce((acc, item) => acc + getItemPrice(item), 0);

  const handleCheckout = async () => {
    if (cartItems.length === 0) return;
    setIsProcessing(true);
    setErrorMessage('');
    try {
      const session = await storeService.checkout(cartItems.map((item) => item.programId));
      if (!session.stripeCheckoutUrl) throw new Error('No se recibió el enlace de pago. Inténtalo de nuevo.');
      window.location.href = session.stripeCheckoutUrl;
    } catch (error: any) {
      setErrorMessage(error.response?.data?.message || error.message || 'No se pudo iniciar el pago. Verifica tu sesión e inténtalo de nuevo.');
      setIsProcessing(false);
    }
  };

  return (
    <div className="lx-overlay lx-overlay-drawer">
      <div className="lx-overlay-backdrop" onClick={onClose} aria-hidden="true" />
      <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="cart-drawer-title" tabIndex={-1} className="lx-drawer lx-cart">
        <header className="lx-overlay-header">
          <div><h2 id="cart-drawer-title">Tu selección</h2><p>{cartItems.length} programa{cartItems.length !== 1 ? 's' : ''} en el carrito</p></div>
          <button type="button" onClick={onClose} data-dialog-close aria-label="Cerrar carrito" className="lx-overlay-close"><X aria-hidden="true" /></button>
        </header>

        <div className="lx-cart-content">
          {errorMessage && <p role="alert" className="lx-overlay-error">{errorMessage}</p>}
          {cartItems.length === 0 ? (
            <div className="lx-cart-empty"><ShoppingBag aria-hidden="true" /><h3>Tu carrito está vacío</h3><p>Explora los programas y guarda aquí los que quieras revisar antes de comprar.</p><button type="button" onClick={onClose} className="lx-overlay-primary">Explorar programas <ArrowRight aria-hidden="true" /></button></div>
          ) : (
            <>
              <ol className="lx-cart-items">
                {cartItems.map((item, index) => (
                  <li key={item.id}>
                    <span className="lx-cart-number">{String(index + 1).padStart(2, '0')}</span>
                    <div className="lx-cart-item-main"><span className="lx-cart-category">{item.category} · {item.tier}</span><h3>{item.title}</h3><strong>US$ {getItemPrice(item).toFixed(2)}</strong></div>
                    <button type="button" onClick={() => onRemoveItem(item.programId)} aria-label={`Quitar ${item.title} del carrito`} className="lx-cart-remove"><Trash2 aria-hidden="true" /></button>
                  </li>
                ))}
              </ol>
              <button type="button" onClick={onClearCart} className="lx-cart-clear">Vaciar carrito</button>
            </>
          )}
        </div>

        {cartItems.length > 0 && <footer className="lx-cart-summary">
          <div><span>Total estimado</span><strong>US$ {total.toFixed(2)}</strong></div>
          <button type="button" disabled={isProcessing} onClick={handleCheckout} className="lx-overlay-primary">
            {isProcessing ? <><LoaderCircle className="lx-spin" aria-hidden="true" /> Conectando con el pago…</> : <>Continuar al pago <ArrowRight aria-hidden="true" /></>}
          </button>
          <p>El pago se completará en una página externa.</p>
        </footer>}
      </div>
    </div>
  );
};

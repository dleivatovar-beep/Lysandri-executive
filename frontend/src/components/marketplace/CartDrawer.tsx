import React, { useEffect, useRef, useState } from 'react';
import { ArrowRight, LoaderCircle, ShoppingBag, Trash2, X, AlertCircle } from 'lucide-react';
import { Playbook } from '../../types';
import { useDialogAccessibility } from '../../hooks/useDialogAccessibility';
import { isPlaybookPurchasable, getItemPrice, getUnpurchasableReason } from '../../context/CartContext';
import type { CartVerificationStatus } from '../../context/CartContext';
import './editorial-overlays.css';

interface CartDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  cartItems: Playbook[];
  onRemoveItem: (programId: number) => void;
  onClearCart: () => void;
  verificationStatus: CartVerificationStatus;
  verificationError: string;
  onRetryVerification: () => void;
  onProceedToCheckout: (items: Playbook[]) => void;
}

export const CartDrawer: React.FC<CartDrawerProps> = ({
  isOpen,
  onClose,
  cartItems,
  onRemoveItem,
  onClearCart,
  verificationStatus,
  verificationError,
  onRetryVerification,
  onProceedToCheckout,
}) => {
  const [isProcessing, setIsProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const checkoutLockRef = useRef(false);
  const dialogRef = useDialogAccessibility<HTMLDivElement>(isOpen, onClose);

  useEffect(() => {
    if (!isOpen) {
      checkoutLockRef.current = false;
      setIsProcessing(false);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const unpurchasableItems = cartItems.filter((item) => (
    item.origin === 'SAMPLE' || item.isSample === true || item.isDemo === true ||
    (typeof item.id === 'string' && item.id.startsWith('sample-')) ||
    (item.disponibilidadVerificada === true && !isPlaybookPurchasable(item))
  ));
  const sampleItems = unpurchasableItems.filter((item) => (
    item.origin === 'SAMPLE' || item.isSample === true || item.isDemo === true ||
    (typeof item.id === 'string' && item.id.startsWith('sample-'))
  ));
  const unavailableItems = unpurchasableItems.filter((item) => !sampleItems.includes(item));
  const hasUnpurchasableItems = unpurchasableItems.length > 0;
  const practiceOnly = cartItems.length > 0 && sampleItems.length === cartItems.length && sampleItems.every((item) =>
    Number.isInteger(item.programId) && item.programId >= 1001 && item.programId <= 1006
  );

  // Detección de monedas
  const currencies = Array.from(new Set(
    cartItems
      .map((item) => item.currency?.trim().toUpperCase())
      .filter((value): value is string => value === 'PEN' || value === 'USD')
  ));
  const hasMixedCurrencies = currencies.length > 1;

  const totalsByCurrency: Record<string, number | null> = {};
  currencies.forEach((cur) => {
    const itemsInCurrency = cartItems.filter((item) => item.currency?.trim().toUpperCase() === cur);
    const prices = itemsInCurrency.map(getItemPrice);
    totalsByCurrency[cur] = prices.some((price) => price === null)
      ? null
      : prices.reduce<number>((total, price) => total + (price ?? 0), 0);
  });

  const primaryCurrency = currencies[0] || '';
  const primaryTotal = primaryCurrency ? totalsByCurrency[primaryCurrency] : null;
  const formattedTotal = primaryCurrency && primaryTotal !== null
    ? new Intl.NumberFormat('es-PE', { style: 'currency', currency: primaryCurrency }).format(primaryTotal)
    : 'Importe no disponible';

  const handleRemoveUnpurchasable = () => {
    unpurchasableItems.forEach((item) => {
      onRemoveItem(item.programId);
    });
    setErrorMessage('');
  };

  const handleRemoveSamples = () => {
    sampleItems.forEach((item) => onRemoveItem(item.programId));
    setErrorMessage('');
  };

  const handleCheckout = async () => {
    if (checkoutLockRef.current || isProcessing) return;
    if (cartItems.length === 0) return;

    // Validación preventiva de monedas heterogéneas
    if (hasMixedCurrencies) {
      setErrorMessage(
        `Tu carrito contiene programas en distintas monedas (${currencies.join(' y ')}). Las pasarelas de pago y la normativa tributaria exigen procesar las compras en una sola moneda. Por favor conserva solo programas con la misma moneda para continuar.`
      );
      return;
    }

    if (!practiceOnly && (verificationStatus === 'PENDING' || verificationStatus === 'IDLE')) {
      setErrorMessage('Espera a que el servidor confirme la disponibilidad y los precios de los programas.');
      return;
    }
    if (!practiceOnly && verificationStatus === 'ERROR') {
      setErrorMessage('No se pudo confirmar la disponibilidad. Reintenta la verificación antes de continuar.');
      return;
    }
    if (!practiceOnly && verificationStatus !== 'VERIFIED') {
      setErrorMessage('La selección todavía no está verificada por el servidor.');
      return;
    }
    if (!practiceOnly && (hasUnpurchasableItems || cartItems.some((i) => !isPlaybookPurchasable(i)))) {
      setErrorMessage(
        'Hay programas que todavía no están habilitados para compra. Retíralos para continuar al pago.'
      );
      return;
    }

    checkoutLockRef.current = true;
    setIsProcessing(true);
    setErrorMessage('');
    try {
      onProceedToCheckout(cartItems);
    } catch (error: unknown) {
      checkoutLockRef.current = false;
      setErrorMessage(error instanceof Error ? error.message : 'No se pudo abrir el formulario de compra.');
      setIsProcessing(false);
    }
  };

  return (
    <div className="lx-overlay lx-overlay-drawer">
      <div className="lx-overlay-backdrop" onClick={onClose} aria-hidden="true" />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cart-drawer-title"
        tabIndex={-1}
        className="lx-drawer lx-cart"
      >
        <header className="lx-overlay-header">
          <div>
            <h2 id="cart-drawer-title">Tu selección</h2>
            <p>
              {cartItems.length} programa{cartItems.length !== 1 ? 's' : ''} en el carrito
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            data-dialog-close
            aria-label="Cerrar carrito"
            className="lx-overlay-close"
          >
            <X aria-hidden="true" />
          </button>
        </header>

        <div className="lx-cart-content">
          {errorMessage && (
            <p role="alert" className="lx-overlay-error">
              {errorMessage}
            </p>
          )}
          {verificationStatus === 'PENDING' && (
            <p role="status">Confirmando con el servidor los precios y la disponibilidad de los programas…</p>
          )}
          {verificationStatus === 'ERROR' && (
            <div role="alert" className="lx-overlay-error">
              <p>{verificationError || 'No se pudo verificar el carrito. Los programas no se consideran habilitados hasta reintentar.'}</p>
              <button type="button" onClick={onRetryVerification}>Reintentar verificación</button>
            </div>
          )}
          {/* Advertencia de monedas heterogéneas */}
          {hasMixedCurrencies && (
            <div
              role="alert"
              className="lx-cart-mixed-currency-alert"
              style={{
                background: '#fef2f2',
                border: '1px solid #fecaca',
                borderRadius: '0.5rem',
                padding: '0.85rem 1rem',
                marginBottom: '1rem',
                fontSize: '0.85rem',
                color: '#991b1b',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontWeight: 700, marginBottom: '0.35rem' }}>
                <AlertCircle style={{ width: '1rem', height: '1rem', color: '#dc2626', flexShrink: 0 }} aria-hidden="true" />
                <span>Monedas mixtas en el carrito</span>
              </div>
              <p style={{ margin: 0, lineHeight: 1.4 }}>
                Has seleccionado programas en distintas monedas ({currencies.join(' y ')}). La plataforma procesa órdenes en una sola moneda. Por favor retira los artículos de una de las monedas para continuar al pago.
              </p>
            </div>
          )}

          {sampleItems.length > 0 && (
            <div role="note" className="lx-cart-sample-note">
              <strong>Inscripción de práctica</strong>
              <p>Estos programas son referenciales. Puedes registrar una orden de práctica en el backend; no se realizará un cobro ni se habilitará Moodle.</p>
              <button type="button" onClick={handleRemoveSamples}>Retirar del carrito</button>
            </div>
          )}

          {unavailableItems.length > 0 && (
            <div
              role="alert"
              className="lx-cart-unavailable-alert"
              style={{
                background: '#fffbeb',
                border: '1px solid #fde68a',
                borderRadius: '0.5rem',
                padding: '0.85rem 1rem',
                marginBottom: '1rem',
                fontSize: '0.85rem',
                color: '#92400e',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontWeight: 700, marginBottom: '0.35rem' }}>
                <AlertCircle style={{ width: '1rem', height: '1rem', color: '#d97706', flexShrink: 0 }} aria-hidden="true" />
                <span>Programa no disponible para inscripción</span>
              </div>
              <p style={{ margin: '0 0 0.5rem 0', lineHeight: 1.4 }}>
                {unavailableItems.length === 1
                  ? `"${unavailableItems[0].title}": ${getUnpurchasableReason(unavailableItems[0])}. Retíralo para continuar.`
                  : 'Algunos programas no están habilitados para inscripción. Retíralos para continuar.'}
              </p>
              <button
                type="button"
                onClick={handleRemoveUnpurchasable}
                style={{
                  background: '#b45309',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '0.25rem',
                  padding: '0.35rem 0.65rem',
                  fontSize: '0.78rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Retirar programas
              </button>
            </div>
          )}

          {cartItems.length === 0 ? (
            <div className="lx-cart-empty">
              <ShoppingBag aria-hidden="true" />
              <h3>Tu carrito está vacío</h3>
              <p>Explora los programas ejecutivos y guarda aquí los que desees cursar antes de comprar.</p>
              <button type="button" onClick={onClose} className="lx-overlay-primary">
                Explorar programas <ArrowRight aria-hidden="true" />
              </button>
            </div>
          ) : (
            <>
              <ol className="lx-cart-items">
                {cartItems.map((item, index) => {
                  const isPurchasable = isPlaybookPurchasable(item);
                  const isSample = item.origin === 'SAMPLE' || item.isSample === true || item.isDemo === true ||
                    (typeof item.id === 'string' && item.id.startsWith('sample-'));
                  const verificationPending = !isSample && item.disponibilidadVerificada !== true;
                  const price = getItemPrice(item);
                  const currency = item.currency?.trim().toUpperCase();
                  const formattedItemPrice = price !== null && currency && ['PEN', 'USD'].includes(currency)
                    ? new Intl.NumberFormat('es-PE', { style: 'currency', currency }).format(price)
                    : 'Precio pendiente de confirmar';
                  const unpurchasableReason = !isPurchasable && !verificationPending && !isSample
                    ? getUnpurchasableReason(item)
                    : '';

                  return (
                    <li key={item.id || item.programId} className={!isPurchasable && !isSample ? 'lx-cart-item-unavailable' : undefined}>
                      <span className="lx-cart-number">{String(index + 1).padStart(2, '0')}</span>
                      <div className="lx-cart-item-main">
                        <span className="lx-cart-category">
                          {item.category} · {item.tier}
                          {!isPurchasable && (
                            <span
                              className="lx-cart-badge-sample"
                              title={unpurchasableReason}
                            >
                              {isSample
                                ? 'Práctica'
                                : verificationPending
                                ? 'Verificación pendiente'
                                : 'No disponible'}
                            </span>
                          )}
                        </span>
                        <h3>{item.title}</h3>
                        <strong>
                          {isPurchasable
                            ? formattedItemPrice
                            : isSample
                            ? `${formattedItemPrice} · Precio referencial`
                            : verificationPending
                            ? formattedItemPrice === 'Precio pendiente de confirmar'
                              ? formattedItemPrice
                              : `${formattedItemPrice} · pendiente de confirmar`
                            : `${formattedItemPrice} · ${unpurchasableReason || 'No disponible'}`}
                        </strong>
                      </div>
                      <button
                        type="button"
                        onClick={() => onRemoveItem(item.programId)}
                        aria-label={`Quitar ${item.title} del carrito`}
                        className="lx-cart-remove"
                      >
                        <Trash2 aria-hidden="true" />
                      </button>
                    </li>
                  );
                })}
              </ol>
              <button type="button" onClick={onClearCart} className="lx-cart-clear">
                Vaciar carrito
              </button>
            </>
          )}
        </div>

        {cartItems.length > 0 && (
          <footer className="lx-cart-summary">
            {hasMixedCurrencies ? (
              <div style={{ marginBottom: '0.5rem' }}>
                <span style={{ display: 'block', fontSize: '0.8rem', color: '#64748b' }}>Totales por moneda:</span>
                {currencies.map((cur) => (
                  <div key={cur} style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700 }}>
                    <span>{cur}:</span>
                    <span>
                      {totalsByCurrency[cur] !== null
                        ? new Intl.NumberFormat('es-PE', { style: 'currency', currency: cur }).format(totalsByCurrency[cur] ?? 0)
                        : 'Importe no disponible'}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <div>
                <span>{primaryCurrency ? `${sampleItems.length > 0 ? 'Total referencial' : 'Total estimado'} (${primaryCurrency})` : 'Total no disponible'}</span>
                <strong>{formattedTotal}</strong>
              </div>
            )}

            <button
              type="button"
              disabled={isProcessing || (!practiceOnly && hasUnpurchasableItems) || hasMixedCurrencies ||
                (!practiceOnly && verificationStatus !== 'VERIFIED')}
              onClick={handleCheckout}
              className="lx-overlay-primary"
              title={
                !practiceOnly && hasUnpurchasableItems
                  ? 'Retira los programas no disponibles para continuar al pago'
                  : hasMixedCurrencies
                  ? 'Separa las compras por moneda para continuar al pago'
                  : undefined
              }
              style={
                (!practiceOnly && hasUnpurchasableItems) || hasMixedCurrencies
                  ? { opacity: 0.5, cursor: 'not-allowed', filter: 'grayscale(0.6)' }
                  : undefined
              }
            >
              {isProcessing ? (
                <>
                  <LoaderCircle className="lx-spin" aria-hidden="true" /> {practiceOnly ? 'Abriendo inscripción…' : 'Conectando con el pago…'}
                </>
              ) : (
                <>
              {practiceOnly ? 'Registrar inscripción' : 'Continuar al pago'} <ArrowRight aria-hidden="true" />
                </>
              )}
            </button>
            <p className="lx-cart-payment-methods-info">
              {practiceOnly
                ? 'La orden de práctica se guardará en un registro separado. No se realizará un cobro ni se habilitará Moodle.'
                : 'Pago seguro con cifrado TLS. Admite tarjeta online (Izipay), transferencia bancaria oficial (BCP / Interbank) y Yape / Plin.'}
            </p>
          </footer>
        )}
      </div>
    </div>
  );
};

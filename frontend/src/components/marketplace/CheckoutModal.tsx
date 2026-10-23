import React, { useState, useEffect } from 'react';
import './checkout-modal.css';
import {
  X,
  AlertCircle,
  LoaderCircle,
  CreditCard,
  Smartphone,
  Building2,
  Copy,
  Check,
  MessageCircle,
  ArrowRight,
} from 'lucide-react';
import visaLogo from '../../assets/payment-logos/visa.svg';
import mastercardLogo from '../../assets/payment-logos/mastercard.svg';
import amexLogo from '../../assets/payment-logos/american-express.svg';
import dinersLogo from '../../assets/payment-logos/diners.svg';
import yapeLogo from '../../assets/payment-logos/yape.png';
import bcpLogo from '../../assets/payment-logos/bcp.svg';
import interbankLogo from '../../assets/payment-logos/interbank.svg';
import { Playbook } from '../../types';
import { storeService } from '../../services/storeService';
import { useAuth } from '../../context/AuthContext';

export interface CheckoutModalProps {
  isOpen: boolean;
  onClose: () => void;
  playbook: Playbook | null;
}

export type PaymentMethod = 'IZIPAY' | 'YAPE_PLIN' | 'TRANSFERENCIA';

export const CheckoutModal: React.FC<CheckoutModalProps> = ({
  isOpen,
  onClose,
  playbook,
}) => {
  const { user } = useAuth();

  const [nombreCompleto, setNombreCompleto] = useState('');
  const [email, setEmail] = useState('');
  const [telefono, setTelefono] = useState('');
  const [tipoComprobante, setTipoComprobante] = useState<'BOLETA' | 'FACTURA'>('BOLETA');
  const [numeroDocumento, setNumeroDocumento] = useState('');
  const [razonSocial, setRazonSocial] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('IZIPAY');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Auto-completar datos si el usuario ya tiene sesión iniciada
  useEffect(() => {
    if (user) {
      if (user.nombre) {
        setNombreCompleto(user.nombre);
      }
      if (user.email) {
        setEmail(user.email);
      }
    }
  }, [user, isOpen]);

  if (!isOpen || !playbook) return null;

  const getPrice = (): number => {
    if (playbook.tier === 'ENTERPRISE') return 950;
    if (playbook.tier === 'ADVANCED') return 899;
    return 799;
  };

  const priceUsd = getPrice();
  const pricePen = Math.round(priceUsd * 3.75);

  const handleDocumentoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const valor = e.target.value.replace(/\D/g, '');
    const maxLen = tipoComprobante === 'BOLETA' ? 8 : 11;
    if (valor.length <= maxLen) {
      setNumeroDocumento(valor);
    }
  };

  const handleCopy = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2500);
  };

  const validateFields = (): boolean => {
    setErrorMessage('');

    if (!nombreCompleto.trim() || nombreCompleto.trim().length < 3) {
      setErrorMessage('Por favor ingresa tus nombres y apellidos para emitir tu certificación oficial.');
      return false;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) {
      setErrorMessage('Por favor indícanos un correo electrónico válido para enviarte tus credenciales de acceso.');
      return false;
    }

    if (tipoComprobante === 'BOLETA') {
      if (numeroDocumento.length !== 8) {
        setErrorMessage('El DNI debe contener exactamente 8 dígitos.');
        return false;
      }
    } else {
      if (numeroDocumento.length !== 11) {
        setErrorMessage('El RUC de la empresa debe contener exactamente 11 dígitos.');
        return false;
      }
      if (!razonSocial.trim() || razonSocial.trim().length < 3) {
        setErrorMessage('Por favor ingresa la Razón Social de la empresa para la factura deducible.');
        return false;
      }
    }

    return true;
  };

  const handleSubmitIzipay = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateFields()) return;

    setIsSubmitting(true);
    setErrorMessage('');

    try {
      const response = await storeService.createCheckoutSession({
        programaIds: [playbook.programId],
        programaTitulo: playbook.title,
        email: email.trim(),
        nombreCompleto: nombreCompleto.trim(),
        telefono: telefono.trim(),
        tipoComprobante,
        numeroDocumento: numeroDocumento.trim(),
        nombreFacturacion: tipoComprobante === 'FACTURA' ? razonSocial.trim() : nombreCompleto.trim(),
        metodoPago: 'IZIPAY',
      });

      const targetUrl = response.checkoutUrl || response.stripeCheckoutUrl;
      if (targetUrl) {
        window.location.href = targetUrl;
      } else {
        throw new Error('No se pudo generar la sesión de pago con izipay Online');
      }
    } catch (err: any) {
      setErrorMessage(
        err.response?.data?.message ||
          err.response?.data?.error ||
          err.message ||
          'Ocurrió un inconveniente al conectar con la pasarela izipay. Por favor intenta de nuevo o escríbenos por WhatsApp.'
      );
      setIsSubmitting(false);
    }
  };

  const handleWhatsAppConfirmation = async (methodName: string) => {
    if (!validateFields()) return;

    // Registrar intención de orden en storage
    try {
      await storeService.createCheckoutSession({
        programaIds: [playbook.programId],
        programaTitulo: playbook.title,
        email: email.trim(),
        nombreCompleto: nombreCompleto.trim(),
        telefono: telefono.trim(),
        tipoComprobante,
        numeroDocumento: numeroDocumento.trim(),
        nombreFacturacion: tipoComprobante === 'FACTURA' ? razonSocial.trim() : nombreCompleto.trim(),
        metodoPago: methodName,
      });
    } catch {
      // Continuar con apertura de WhatsApp incluso en modo desconectado
    }

    const message = `¡Hola Lysandri! He solicitado mi matrícula en el programa ejecutivo:\n\n` +
      `Programa: *${playbook.title}*\n` +
      `Método de pago: *${methodName}*\n` +
      `Monto: $${priceUsd} USD (S/ ${pricePen.toLocaleString('es-PE')} PEN)\n\n` +
      `Datos de inscripción:\n` +
      `- *Nombres:* ${nombreCompleto.trim()}\n` +
      `- *Correo:* ${email.trim()}\n` +
      `- *Teléfono:* ${telefono.trim() || 'No especificado'}\n` +
      `- *Comprobante Solicitado:* ${tipoComprobante} (${numeroDocumento.trim()})\n` +
      (tipoComprobante === 'FACTURA' && razonSocial.trim() ? `- *Razón Social:* ${razonSocial.trim()}\n` : '') +
      `\nAdjunto el comprobante de pago para la activación de mis credenciales en el Campus Virtual Moodle.`;

    window.open(`https://wa.me/51941238905?text=${encodeURIComponent(message)}`, '_blank');
  };


  return (
    <div className="lx-checkout-shell">
      <button className="lx-checkout-backdrop" type="button" aria-label="Cerrar pago" onClick={onClose} />
      <section className="lx-checkout-dialog" role="dialog" aria-modal="true" aria-labelledby="checkout-title">
        <header className="lx-checkout-header">
          <div className="lx-checkout-course"><h2 id="checkout-title">{playbook.title}</h2></div>
          <div className="lx-checkout-price"><strong>S/ {pricePen.toLocaleString('es-PE')}</strong><span>${priceUsd} USD</span></div>
          <button type="button" onClick={onClose} className="lx-checkout-close" aria-label="Cerrar"><X size={19} /></button>
        </header>
        <form className="lx-checkout-body" onSubmit={handleSubmitIzipay}>
          <section className="lx-checkout-section" aria-labelledby="checkout-data-heading">
            <div className="lx-section-heading"><div><h3 id="checkout-data-heading">Tus datos</h3><p>Para emitir tu comprobante y habilitar el acceso.</p></div></div>
            <div className="lx-fields-grid">
              <label className="lx-field lx-field-wide"><span>Nombre completo</span><input autoComplete="name" value={nombreCompleto} onChange={(e) => setNombreCompleto(e.target.value)} placeholder="Como aparecerá en tu certificado" /></label>
              <label className="lx-field"><span>Correo electrónico</span><input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="nombre@correo.com" /></label>
              <label className="lx-field"><span>Celular <small>Opcional</small></span><input type="tel" autoComplete="tel" value={telefono} onChange={(e) => setTelefono(e.target.value)} placeholder="+51 999 999 999" /></label>
            </div>
            <fieldset className="lx-invoice-picker"><legend>Comprobante</legend><div className="lx-invoice-options">
              <button type="button" aria-pressed={tipoComprobante === 'BOLETA'} className={tipoComprobante === 'BOLETA' ? 'is-selected' : ''} onClick={() => { setTipoComprobante('BOLETA'); setNumeroDocumento(''); }}><span>Boleta</span><small>DNI</small></button>
              <button type="button" aria-pressed={tipoComprobante === 'FACTURA'} className={tipoComprobante === 'FACTURA' ? 'is-selected' : ''} onClick={() => { setTipoComprobante('FACTURA'); setNumeroDocumento(''); }}><span>Factura</span><small>RUC</small></button>
            </div></fieldset>
            <div className="lx-fields-grid lx-document-fields">
              <label className="lx-field"><span>{tipoComprobante === 'BOLETA' ? 'DNI' : 'RUC'}</span><input inputMode="numeric" value={numeroDocumento} onChange={handleDocumentoChange} placeholder={tipoComprobante === 'BOLETA' ? '8 dígitos' : '11 dígitos'} /></label>
              {tipoComprobante === 'FACTURA' && <label className="lx-field"><span>Razón social</span><input value={razonSocial} onChange={(e) => setRazonSocial(e.target.value)} placeholder="Nombre de la empresa" /></label>}
            </div>
          </section>
          <section className="lx-checkout-section lx-payment-section" aria-labelledby="checkout-payment-heading">
            <div className="lx-section-heading"><div><h3 id="checkout-payment-heading">Método de pago</h3><p>Elige cómo quieres completar tu matrícula.</p></div></div>
            <div className="lx-payment-options" role="group" aria-label="Métodos de pago">
              <button type="button" aria-pressed={paymentMethod === 'IZIPAY'} className={paymentMethod === 'IZIPAY' ? 'is-selected' : ''} onClick={() => setPaymentMethod('IZIPAY')}><CreditCard size={18} /><span>Tarjeta</span><small>Crédito o débito</small></button>
              <button type="button" aria-pressed={paymentMethod === 'YAPE_PLIN'} className={paymentMethod === 'YAPE_PLIN' ? 'is-selected' : ''} onClick={() => setPaymentMethod('YAPE_PLIN')}><Smartphone size={18} /><span>Yape / Plin</span><small>Pago móvil</small></button>
              <button type="button" aria-pressed={paymentMethod === 'TRANSFERENCIA'} className={paymentMethod === 'TRANSFERENCIA' ? 'is-selected' : ''} onClick={() => setPaymentMethod('TRANSFERENCIA')}><Building2 size={18} /><span>Transferencia</span><small>Banca móvil</small></button>
            </div>
            {paymentMethod === 'IZIPAY' && <div className="lx-method-panel" key="card"><div className="lx-method-intro"><div><h4>Pago seguro con tarjeta</h4><p>Ingresa los datos en la pasarela protegida.</p></div><span className="lx-secure-mark">256-bit SSL</span></div>
              <div className="lx-brand-row" aria-label="Tarjetas aceptadas"><img src={visaLogo} alt="Visa" /><img src={mastercardLogo} alt="Mastercard" /><img src={amexLogo} alt="American Express" /><img src={dinersLogo} alt="Diners Club" /></div>
              <button type="submit" className="lx-checkout-primary-action" disabled={isSubmitting}>{isSubmitting ? <><LoaderCircle size={17} className="lx-spinner" /> Conectando con izipay…</> : <>Continuar al pago <ArrowRight size={17} /><span>S/ {pricePen.toLocaleString('es-PE')}</span></>}</button>
            </div>}
            {paymentMethod === 'YAPE_PLIN' && <div className="lx-method-panel" key="wallet"><div className="lx-method-intro"><div><h4>Yape o Plin</h4><p>Transfiere desde tu billetera y envía la constancia.</p></div><img className="lx-yape-logo" src={yapeLogo} alt="Yape" /></div>
              <div className="lx-account-row"><div><small>Número de pago</small><strong>941 238 905</strong></div><button type="button" onClick={() => handleCopy('941238905', 'yape-num')} aria-label="Copiar número de pago">{copiedKey === 'yape-num' ? <Check size={16} /> : <Copy size={16} />}<span>{copiedKey === 'yape-num' ? 'Copiado' : 'Copiar'}</span></button></div>
              <p className="lx-payment-note">A nombre de Lysandri · Importe: <strong>S/ {pricePen.toLocaleString('es-PE')}</strong></p><button type="button" className="lx-checkout-whatsapp-action" onClick={() => handleWhatsAppConfirmation('Yape / Plin')}><MessageCircle size={17} /> Enviar constancia por WhatsApp <ArrowRight size={16} /></button>
            </div>}
            {paymentMethod === 'TRANSFERENCIA' && <div className="lx-method-panel lx-bank-panel" key="bank"><div className="lx-method-intro"><div><h4>Transferencia bancaria</h4><p>Selecciona una cuenta y comparte el comprobante.</p></div></div>
              <div className="lx-bank-list">
                <article className="lx-bank-card"><img src={bcpLogo} alt="Banco de Crédito del Perú" /><div className="lx-bank-details"><small>Cuenta corriente · Soles</small><strong>193-98218491-0-28</strong><small>CCI 0021930098218491028</small></div><button type="button" onClick={() => handleCopy('193-98218491-0-28', 'bcp-cta')} aria-label="Copiar cuenta BCP">{copiedKey === 'bcp-cta' ? <Check size={16} /> : <Copy size={16} />}</button></article>
                <article className="lx-bank-card"><img src={interbankLogo} alt="Interbank" /><div className="lx-bank-details"><small>Cuenta corriente · Soles</small><strong>200-3001849128</strong><small>CCI 00320000300184912831</small></div><button type="button" onClick={() => handleCopy('200-3001849128', 'ibk-cta')} aria-label="Copiar cuenta Interbank">{copiedKey === 'ibk-cta' ? <Check size={16} /> : <Copy size={16} />}</button></article>
              </div><p className="lx-payment-note">Importe a transferir: <strong>S/ {pricePen.toLocaleString('es-PE')}</strong></p><button type="button" className="lx-checkout-whatsapp-action" onClick={() => handleWhatsAppConfirmation('Transferencia bancaria')}><MessageCircle size={17} /> Enviar constancia por WhatsApp <ArrowRight size={16} /></button>
            </div>}
          </section>
          {errorMessage && <div className="lx-checkout-error" role="alert"><AlertCircle size={18} /><span>{errorMessage}</span></div>}
          <p className="lx-checkout-footnote">Al continuar, confirmas que los datos del comprobante son correctos.</p>
        </form>
      </section>
    </div>
  );
};

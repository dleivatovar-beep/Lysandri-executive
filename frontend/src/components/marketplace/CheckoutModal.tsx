import React, { useState } from 'react';
import {
  X,
  Lock,
  FileText,
  Building,
  User,
  Mail,
  AlertCircle,
  LoaderCircle,
  ShieldCheck,
  CheckCircle2,
} from 'lucide-react';
import { Playbook } from '../../types';
import { storeService } from '../../services/storeService';

export interface CheckoutModalProps {
  isOpen: boolean;
  onClose: () => void;
  playbook: Playbook | null;
}

export const CheckoutModal: React.FC<CheckoutModalProps> = ({
  isOpen,
  onClose,
  playbook,
}) => {
  const [nombreCompleto, setNombreCompleto] = useState('');
  const [email, setEmail] = useState('');
  const [tipoComprobante, setTipoComprobante] = useState<'BOLETA' | 'FACTURA'>('BOLETA');
  const [numeroDocumento, setNumeroDocumento] = useState('');
  const [razonSocial, setRazonSocial] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  if (!isOpen || !playbook) return null;

  const getPrice = (): number => {
    if (playbook.tier === 'ENTERPRISE') return 950;
    if (playbook.tier === 'ADVANCED') return 899;
    return 799;
  };

  const handleDocumentoChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const valor = e.target.value.replace(/\D/g, '');
    const maxLen = tipoComprobante === 'BOLETA' ? 8 : 11;
    if (valor.length <= maxLen) {
      setNumeroDocumento(valor);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');

    if (!nombreCompleto.trim() || nombreCompleto.trim().length < 3) {
      setErrorMessage('Ingresa tus nombres y apellidos completos.');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email.trim())) {
      setErrorMessage('Ingresa un correo electrónico válido.');
      return;
    }

    if (tipoComprobante === 'BOLETA') {
      if (numeroDocumento.length !== 8) {
        setErrorMessage('El DNI debe contener 8 dígitos.');
        return;
      }
    } else {
      if (numeroDocumento.length !== 11) {
        setErrorMessage('El RUC debe contener 11 dígitos.');
        return;
      }
      if (!razonSocial.trim() || razonSocial.trim().length < 3) {
        setErrorMessage('Ingresa la razón social para la factura.');
        return;
      }
    }

    setIsSubmitting(true);

    try {
      const response = await storeService.createCheckoutSession({
        programaIds: [playbook.programId],
        email: email.trim(),
        nombreCompleto: nombreCompleto.trim(),
        tipoComprobante,
        numeroDocumento: numeroDocumento.trim(),
        nombreFacturacion: tipoComprobante === 'FACTURA' ? razonSocial.trim() : nombreCompleto.trim(),
      });

      const targetUrl = response.checkoutUrl || response.stripeCheckoutUrl;
      if (targetUrl) {
        window.location.href = targetUrl;
      } else {
        throw new Error('No se pudo generar la sesión de pago');
      }
    } catch (err: any) {
      setErrorMessage(
        err.response?.data?.message ||
          err.response?.data?.error ||
          err.message ||
          'Ocurrió un error al iniciar el proceso de pago.'
      );
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto">
      <div
        className="fixed inset-0 bg-slate-950/80 backdrop-blur-md transition-opacity"
        onClick={onClose}
      />

      <div className="relative w-full max-w-lg overflow-hidden rounded-2xl border border-cyan-500/30 bg-[#0d131f] text-slate-100 shadow-[0_0_50px_rgba(6,182,212,0.15)] my-8">
        <div className="relative border-b border-slate-800 bg-gradient-to-r from-slate-900 via-slate-900 to-[#121c2e] p-6">
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar modal"
            className="absolute right-4 top-4 rounded-xl p-2 text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
          >
            <X className="h-5 w-5" />
          </button>

          <div className="pr-8">
            <span className="inline-block rounded-md border border-cyan-500/30 bg-cyan-950/60 px-2.5 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider text-cyan-400 mb-2">
              Inscripción directa
            </span>
            <h2 className="text-xl font-black text-white leading-tight">
              {playbook.title}
            </h2>
            <div className="mt-3 flex items-baseline gap-2">
              <span className="text-2xl font-black text-cyan-400">
                ${getPrice()}
              </span>
              <span className="text-xs font-semibold text-slate-400">
                USD (Pago único)
              </span>
            </div>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {errorMessage && (
            <div className="flex items-start gap-2.5 rounded-xl border border-rose-500/40 bg-rose-950/40 p-3 text-xs text-rose-300">
              <AlertCircle className="h-4 w-4 shrink-0 text-rose-400 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">
              Nombres y apellidos completos *
            </label>
            <div className="relative">
              <User className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
              <input
                type="text"
                required
                value={nombreCompleto}
                onChange={(e) => setNombreCompleto(e.target.value)}
                placeholder="Carlos Mendoza Alarcón"
                className="w-full rounded-xl border border-slate-700 bg-slate-900/90 pl-9 pr-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400 transition-colors"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">
              Correo electrónico *
            </label>
            <div className="relative">
              <Mail className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="c.mendoza@empresa.com"
                className="w-full rounded-xl border border-slate-700 bg-slate-900/90 pl-9 pr-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400 transition-colors"
              />
            </div>
            <p className="mt-1 text-[11px] text-slate-400">
              Recibirás tus credenciales de acceso y comprobante de pago en esta dirección.
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-2">
              Tipo de comprobante *
            </label>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => {
                  setTipoComprobante('BOLETA');
                  setNumeroDocumento('');
                }}
                className={`flex items-center justify-center gap-2 rounded-xl border p-2.5 text-xs font-bold transition-all ${
                  tipoComprobante === 'BOLETA'
                    ? 'border-cyan-400 bg-cyan-500/10 text-cyan-300 shadow-sm shadow-cyan-500/20'
                    : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700'
                }`}
              >
                <FileText className="h-4 w-4" />
                <span>Boleta (DNI)</span>
              </button>

              <button
                type="button"
                onClick={() => {
                  setTipoComprobante('FACTURA');
                  setNumeroDocumento('');
                }}
                className={`flex items-center justify-center gap-2 rounded-xl border p-2.5 text-xs font-bold transition-all ${
                  tipoComprobante === 'FACTURA'
                    ? 'border-indigo-400 bg-indigo-500/10 text-indigo-300 shadow-sm shadow-indigo-500/20'
                    : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700'
                }`}
              >
                <Building className="h-4 w-4" />
                <span>Factura (RUC)</span>
              </button>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">
              {tipoComprobante === 'BOLETA' ? 'Número de DNI (8 dígitos) *' : 'Número de RUC (11 dígitos) *'}
            </label>
            <input
              type="text"
              required
              inputMode="numeric"
              value={numeroDocumento}
              onChange={handleDocumentoChange}
              placeholder={tipoComprobante === 'BOLETA' ? '8 dígitos' : '11 dígitos'}
              className="w-full rounded-xl border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-white placeholder-slate-500 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400 transition-colors"
            />
          </div>

          {tipoComprobante === 'FACTURA' && (
            <div className="animate-fadeIn">
              <label className="block text-xs font-semibold text-indigo-300 mb-1">
                Razón Social *
              </label>
              <input
                type="text"
                required
                value={razonSocial}
                onChange={(e) => setRazonSocial(e.target.value)}
                placeholder="Nombre de la empresa"
                className="w-full rounded-xl border border-indigo-500/40 bg-slate-900/90 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400 transition-colors"
              />
            </div>
          )}

          <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-3 space-y-1.5 text-[11px] text-slate-400">
            <div className="flex items-center gap-2 text-emerald-400">
              <CheckCircle2 className="h-3.5 w-3.5" />
              <span>Acceso inmediato al aula virtual</span>
            </div>
            <div className="flex items-center gap-2 text-cyan-400">
              <ShieldCheck className="h-3.5 w-3.5" />
              <span>Emisión de comprobante electrónico</span>
            </div>
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-6 py-3 text-xs font-bold text-white shadow-lg shadow-cyan-500/20 hover:opacity-95 active:scale-[0.98] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? (
              <>
                <LoaderCircle className="h-4 w-4 animate-spin" />
                <span>Procesando...</span>
              </>
            ) : (
              <>
                <Lock className="h-4 w-4" />
                <span>Continuar al pago • ${getPrice()} USD</span>
              </>
            )}
          </button>

          <p className="text-center text-[10px] text-slate-500 flex items-center justify-center gap-1">
            <Lock className="h-3 w-3" />
            <span>Pagos procesados de forma segura con Stripe</span>
          </p>
        </form>
      </div>
    </div>
  );
};

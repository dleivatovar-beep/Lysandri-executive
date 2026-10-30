import React, { useState, useEffect } from 'react';
import {
  X,
  Lock,
  FileText,
  Building,
  User,
  Mail,
  Phone,
  AlertCircle,
  LoaderCircle,
  ShieldCheck,
  GraduationCap,
  Sparkles,
  CreditCard,
  Smartphone,
  Building2,
  Copy,
  Check,
  MessageCircle,
  QrCode,
  ArrowRight,
  ExternalLink,
  CheckCircle2,
  HeartHandshake,
  Info,
} from 'lucide-react';
import { Playbook } from '../../types';
import { storeService, CuentaBancariaAprobada } from '../../services/storeService';
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

  // Cuentas bancarias configurables desde el backend
  const [cuentasBancarias, setCuentasBancarias] = useState<CuentaBancariaAprobada[]>([]);
  const [cargandoCuentas, setCargandoCuentas] = useState(false);

  // Estado posterior al registro de orden manual
  const [ordenRegistradaCodigo, setOrdenRegistradaCodigo] = useState<string | null>(null);
  const [ordenRegistradaMetodo, setOrdenRegistradaMetodo] = useState<string | null>(null);

  const getBuyerScope = (): string => {
    return storeService.getBuyerScope(user, email);
  };

  const getIdempotencyKey = (): string => {
    if (!playbook) return '';
    const scope = getBuyerScope();
    return storeService.getOrCreateIdempotencyKey(scope, playbook.programId);
  };

  const handleStartNewOrder = () => {
    if (playbook) {
      const scope = getBuyerScope();
      storeService.clearCheckoutIntent(scope, playbook.programId);
    }
    setOrdenRegistradaCodigo(null);
    setOrdenRegistradaMetodo(null);
    setErrorMessage('');
  };

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

  // Cargar cuentas bancarias oficiales.
  useEffect(() => {
    if (isOpen && playbook) {
      setErrorMessage('');
      setCargandoCuentas(true);

      storeService
        .obtenerCuentasBancarias()
        .then((cuentas) => {
          setCuentasBancarias(cuentas || []);
        })
        .catch((err) => {
          setCuentasBancarias([]);
          setErrorMessage(
            err.response?.data?.message ||
              err.response?.data?.error ||
              'No se pudieron cargar los destinos bancarios oficiales. Intenta nuevamente.'
          );
        })
        .finally(() => {
          setCargandoCuentas(false);
        });
    }
  }, [isOpen, playbook]);

  // Recuperar órdenes solo para la identidad actual del comprador.
  useEffect(() => {
    if (!isOpen || !playbook) return;

    const scope = getBuyerScope();
    const existingIntent = storeService.getCheckoutIntent(scope, playbook.programId);
    if (existingIntent && existingIntent.codigoOrden) {
      setOrdenRegistradaCodigo(existingIntent.codigoOrden);
      setOrdenRegistradaMetodo(existingIntent.metodoPago || null);
      if (existingIntent.nombreCompleto) setNombreCompleto(existingIntent.nombreCompleto);
      if (existingIntent.numeroDocumento) setNumeroDocumento(existingIntent.numeroDocumento);
      if (existingIntent.tipoComprobante) setTipoComprobante(existingIntent.tipoComprobante);
      if (existingIntent.nombreFacturacion) setRazonSocial(existingIntent.nombreFacturacion);
    } else {
      setOrdenRegistradaCodigo(null);
      setOrdenRegistradaMetodo(null);
    }
  }, [isOpen, playbook, user, email]);

  if (!isOpen || !playbook) return null;

  const getPrice = (): number => {
    if (playbook.price && playbook.price > 0) return playbook.price;
    if (playbook.tier === 'ENTERPRISE') return 950;
    if (playbook.tier === 'ADVANCED') return 899;
    return 799;
  };

  const priceAmount = getPrice();
  const priceCurrency = playbook.currency || 'USD';

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
    if (isSubmitting) return;

    setIsSubmitting(true);
    setErrorMessage('');

    try {
      const scope = getBuyerScope();
      const key = getIdempotencyKey();
      const isGuest = !user || !user.id;
      let guestToken: string | undefined = undefined;

      if (isGuest) {
        guestToken = await storeService.getOrCreateGuestSession(email.trim(), scope, playbook.programId);
        if (!guestToken || guestToken.trim().length === 0) {
          throw new Error('No se pudo establecer una sesión de compra segura. Por favor intente nuevamente.');
        }
      }

      // Persistir estado de intención y credencial criptográfica ANTES del envío a la red
      storeService.saveCheckoutIntent({
        claveIdempotencia: key,
        programId: playbook.programId,
        buyerScope: scope,
        email: email.trim(),
        nombreCompleto: nombreCompleto.trim(),
        tipoComprobante,
        numeroDocumento: numeroDocumento.trim(),
        nombreFacturacion: tipoComprobante === 'FACTURA' ? razonSocial.trim() : nombreCompleto.trim(),
        metodoPago: 'IZIPAY',
        guestSessionToken: guestToken,
        status: 'PENDIENTE',
        createdAt: Date.now(),
      });

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
        claveIdempotencia: key,
        guestSessionToken: guestToken,
      });

      const targetUrl = response.checkoutUrl || response.stripeCheckoutUrl;
      if (targetUrl) {
        storeService.clearCheckoutIntent(scope, playbook.programId);
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
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleManualOrderRegistration = async (metodoTecnico: 'TRANSFERENCIA' | 'YAPE' | 'PLIN', nombreVisible: string) => {
    if (!validateFields()) return;
    if (isSubmitting) return;

    setIsSubmitting(true);
    setErrorMessage('');

    try {
      const scope = getBuyerScope();
      const key = getIdempotencyKey();
      const isGuest = !user || !user.id;
      let guestToken: string | undefined = undefined;

      if (isGuest) {
        guestToken = await storeService.getOrCreateGuestSession(email.trim(), scope, playbook.programId);
        if (!guestToken || guestToken.trim().length === 0) {
          throw new Error('No se pudo establecer una sesión de compra segura. Por favor intente nuevamente.');
        }
      }

      // Persistir estado de intención previo al envío a la red
      storeService.saveCheckoutIntent({
        claveIdempotencia: key,
        programId: playbook.programId,
        buyerScope: scope,
        email: email.trim(),
        nombreCompleto: nombreCompleto.trim(),
        tipoComprobante,
        numeroDocumento: numeroDocumento.trim(),
        nombreFacturacion: tipoComprobante === 'FACTURA' ? razonSocial.trim() : nombreCompleto.trim(),
        metodoPago: nombreVisible,
        guestSessionToken: guestToken,
        status: 'PENDIENTE',
        createdAt: Date.now(),
      });

      const response = await storeService.createCheckoutSession({
        programaIds: [playbook.programId],
        programaTitulo: playbook.title,
        email: email.trim(),
        nombreCompleto: nombreCompleto.trim(),
        telefono: telefono.trim(),
        tipoComprobante,
        numeroDocumento: numeroDocumento.trim(),
        nombreFacturacion: tipoComprobante === 'FACTURA' ? razonSocial.trim() : nombreCompleto.trim(),
        metodoPago: metodoTecnico,
        claveIdempotencia: key,
        guestSessionToken: guestToken,
      });

      if (!response || !response.codigoOrden) {
        throw new Error('El servidor no retornó un código de orden válido para el registro manual.');
      }

      // Persistir código de orden confirmado para recuperación tras recarga o reintentos
      storeService.saveCheckoutIntent({
        claveIdempotencia: key,
        programId: playbook.programId,
        buyerScope: scope,
        email: email.trim(),
        nombreCompleto: nombreCompleto.trim(),
        tipoComprobante,
        numeroDocumento: numeroDocumento.trim(),
        nombreFacturacion: tipoComprobante === 'FACTURA' ? razonSocial.trim() : nombreCompleto.trim(),
        metodoPago: nombreVisible,
        codigoOrden: response.codigoOrden,
        guestSessionToken: response.guestSessionToken || guestToken,
        status: 'REGISTRADA',
        createdAt: Date.now(),
      });

      setOrdenRegistradaCodigo(response.codigoOrden);
      setOrdenRegistradaMetodo(nombreVisible);
    } catch (err: any) {
      // Bloquear estrictamente la apertura de WhatsApp si no hay orden confirmada por backend
      const mensaje =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'No se pudo registrar la orden en el servidor. Por favor intenta de nuevo.';
      setErrorMessage(mensaje);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleOpenWhatsAppForRegisteredOrder = () => {
    if (!ordenRegistradaCodigo) return;

    const message =
      `¡Hola Lysandri! He registrado mi orden de matrícula:\n\n` +
      `📋 *Código de Orden:* ${ordenRegistradaCodigo}\n` +
      `🎓 *Programa:* ${playbook.title}\n` +
      `💳 *Método de Pago:* ${ordenRegistradaMetodo || 'Transferencia Bancaria'}\n` +
      `💵 *Monto:* ${priceAmount} ${priceCurrency}\n\n` +
      `📌 *Datos del Titular / Comprobante:*\n` +
      `- *Nombres:* ${nombreCompleto.trim()}\n` +
      `- *Correo:* ${email.trim()}\n` +
      `- *Teléfono:* ${telefono.trim() || 'No especificado'}\n` +
      `- *Comprobante:* ${tipoComprobante} (${numeroDocumento.trim()})\n` +
      (tipoComprobante === 'FACTURA' && razonSocial.trim() ? `- *Razón Social:* ${razonSocial.trim()}\n` : '') +
      `\nAdjunto mi comprobante de abono bancario para la verificación de nuestro equipo administrativo y posterior activación de credenciales en Moodle.`;

    window.open(`https://wa.me/51941238905?text=${encodeURIComponent(message)}`, '_blank');
  };

  const cuentasActivas = cuentasBancarias.filter((c) => c.activo && c.numeroCuenta);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 overflow-y-auto">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-950/85 backdrop-blur-md transition-opacity"
        onClick={onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-2xl overflow-hidden rounded-3xl border border-cyan-500/30 bg-[#0c121e] text-slate-100 shadow-[0_0_70px_rgba(6,182,212,0.18)] my-8 max-h-[92vh] flex flex-col">
        {/* Header con estilo ejecutivo y cálido */}
        <div className="relative border-b border-slate-800 bg-gradient-to-r from-slate-900 via-[#0f172a] to-[#171328] p-5 sm:p-6 shrink-0">
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar modal"
            className="absolute right-4 top-4 rounded-xl p-2 text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
          >
            <X className="h-5 w-5" />
          </button>

          <div className="pr-8">
            <div className="flex flex-wrap items-center gap-2 mb-2">
              <span className="inline-flex items-center gap-1.5 rounded-full border border-fuchsia-500/30 bg-fuchsia-950/50 px-3 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider text-fuchsia-300">
                <Sparkles className="h-3 w-3 text-fuchsia-400" />
                Matrícula Ejecutiva Oficial
              </span>
              <span className="inline-flex items-center gap-1 rounded-full border border-cyan-500/30 bg-cyan-950/50 px-2.5 py-0.5 font-mono text-[10px] text-cyan-300">
                Campus Moodle 24/7
              </span>
            </div>

            <h2 className="text-lg sm:text-xl font-black text-white leading-tight">
              {playbook.title}
            </h2>

            <div className="mt-2.5 flex flex-wrap items-baseline gap-3">
              <div className="flex items-baseline gap-1.5">
                <span className="text-2xl sm:text-3xl font-black text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-fuchsia-400">
                  {priceCurrency === 'USD' ? `$${priceAmount}` : `S/ ${priceAmount}`}
                </span>
                <span className="text-xs font-semibold text-slate-400">
                  {priceCurrency}
                </span>
              </div>
              <span className="text-xs font-mono text-slate-400">
                (Comprobante oficial SUNAT: Boleta o Factura deducible)
              </span>
            </div>
          </div>
        </div>

        {/* Scrollable Body */}
        <div className="overflow-y-auto p-5 sm:p-6 space-y-6 flex-1">
          {/* Tarjeta de Asesora Académica */}
          <div className="rounded-2xl border border-indigo-500/30 bg-gradient-to-r from-indigo-950/40 via-purple-950/25 to-slate-900/80 p-4 shadow-sm">
            <div className="flex items-start gap-3.5">
              <div className="relative shrink-0">
                <div className="h-11 w-11 rounded-full bg-gradient-to-tr from-cyan-400 via-indigo-500 to-fuchsia-500 p-0.5 shadow-md">
                  <div className="h-full w-full rounded-full bg-slate-900 flex items-center justify-center text-cyan-300 font-bold text-xs">
                    SV
                  </div>
                </div>
                <span className="absolute bottom-0 right-0 h-3 w-3 rounded-full bg-emerald-500 border-2 border-slate-900" title="En línea para asistirte" />
              </div>
              <div className="space-y-1 flex-1 min-w-0">
                <div className="flex flex-wrap items-center justify-between gap-1">
                  <div>
                    <h4 className="text-xs font-bold text-white flex items-center gap-1.5">
                      <span>Lic. Sofía Valdivia</span>
                      <span className="text-[10px] font-normal text-indigo-300">· Coordinadora de Admisiones</span>
                    </h4>
                    <p className="text-[11px] text-slate-300 leading-snug mt-0.5">
                      «¡Hola! Te acompaño en tu inscripción. Con izipay Online tu aula virtual se activa de inmediato. En transferencias y depósitos bancarios, nuestro equipo administrativo verifica el comprobante para dar de alta tus accesos.»
                    </p>
                  </div>
                </div>
                <div className="pt-1 flex flex-wrap items-center gap-3 text-[10px] text-slate-400">
                  <span className="inline-flex items-center gap-1 text-emerald-400">
                    <CheckCircle2 className="h-3 w-3" /> Acompañamiento 1-a-1
                  </span>
                  <span className="inline-flex items-center gap-1 text-cyan-400">
                    <GraduationCap className="h-3 w-3" /> Alta en Moodle tras confirmación
                  </span>
                  <span className="inline-flex items-center gap-1 text-fuchsia-400">
                    <ShieldCheck className="h-3 w-3" /> Certificación oficial con QR
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Banner de Confirmación de Orden Registrada (cuando aplica) */}
          {ordenRegistradaCodigo ? (
            <div className="rounded-2xl border border-emerald-500/40 bg-emerald-950/30 p-5 space-y-4">
              <div className="flex items-start gap-3">
                <CheckCircle2 className="h-6 w-6 text-emerald-400 shrink-0 mt-0.5" />
                <div>
                  <h3 className="text-sm font-bold text-white">
                    ¡Orden Registrada en el Sistema Lysandri!
                  </h3>
                  <p className="text-xs font-mono text-emerald-300 mt-0.5">
                    Código Oficial: <span className="text-base font-black text-white">{ordenRegistradaCodigo}</span>
                  </p>
                  <p className="text-[11px] text-slate-300 mt-2 leading-relaxed">
                    Tu orden se encuentra en estado <strong className="text-amber-300 font-mono">PENDIENTE_PAGO</strong>.
                    Por favor remite tu comprobante de abono bancario indicando este código.
                    Nuestro equipo administrativo validará el abono en cuenta bancaria antes de activar tus credenciales en el Campus Virtual.
                  </p>
                </div>
              </div>

              <div className="rounded-xl border border-slate-800 bg-slate-900/80 p-3 text-xs space-y-1">
                <div className="flex justify-between text-slate-300">
                  <span>Importe Registrado:</span>
                  <span className="font-mono font-bold text-white">{priceAmount} {priceCurrency}</span>
                </div>
                <div className="flex justify-between text-slate-300">
                  <span>Titular:</span>
                  <span className="text-white">{nombreCompleto}</span>
                </div>
                <div className="flex justify-between text-slate-300">
                  <span>Comprobante:</span>
                  <span className="text-white">{tipoComprobante} ({numeroDocumento})</span>
                </div>
              </div>

              <div className="flex flex-col sm:flex-row gap-2 pt-1">
                <button
                  type="button"
                  onClick={handleOpenWhatsAppForRegisteredOrder}
                  className="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 px-4 py-3 text-xs font-bold text-white shadow-lg hover:opacity-95 transition"
                >
                  <MessageCircle className="h-4 w-4" />
                  <span>Enviar Comprobante por WhatsApp</span>
                  <ExternalLink className="h-3.5 w-3.5 ml-1" />
                </button>
                <button
                  type="button"
                  onClick={handleStartNewOrder}
                  className="rounded-xl border border-slate-700 px-4 py-3 text-xs font-semibold text-slate-300 hover:bg-slate-800 transition"
                >
                  Iniciar Nueva Orden
                </button>
              </div>
            </div>
          ) : (
            <>
              {/* Línea de progreso */}
              <div className="flex items-center justify-between px-1 border-b border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-cyan-500 text-[10px] font-black text-slate-950">
                    1
                  </span>
                  <span className="text-xs font-bold text-white">Tus Datos & Facturación</span>
                </div>
                <div className="h-px w-8 bg-slate-700" />
                <div className="flex items-center gap-2">
                  <span className="flex h-5 w-5 items-center justify-center rounded-full bg-fuchsia-500 text-[10px] font-black text-white">
                    2
                  </span>
                  <span className="text-xs font-bold text-slate-300">Método de Pago</span>
                </div>
                <div className="h-px w-8 bg-slate-700 hidden sm:block" />
                <div className="hidden sm:flex items-center gap-1.5 text-[11px] text-slate-400">
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" />
                  <span>Acceso Tras Verificación</span>
                </div>
              </div>

              {errorMessage && (
                <div className="flex flex-col gap-2 rounded-xl border border-rose-500/40 bg-rose-950/40 p-3 text-xs text-rose-300">
                  <div className="flex items-start gap-2.5">
                    <AlertCircle className="h-4 w-4 shrink-0 text-rose-400 mt-0.5" />
                    <span className="flex-1">{errorMessage}</span>
                  </div>
                  {(errorMessage.toLowerCase().includes('conflicto') ||
                    errorMessage.toLowerCase().includes('difieren') ||
                    errorMessage.toLowerCase().includes('clave')) && (
                    <button
                      type="button"
                      onClick={handleStartNewOrder}
                      className="self-start text-[11px] font-bold text-amber-300 hover:text-amber-200 underline mt-1"
                    >
                      Iniciar una nueva compra con nueva clave
                    </button>
                  )}
                </div>
              )}

              {/* Form: Información del Alumno y Facturación */}
              <div className="space-y-4">
                <h3 className="text-xs font-bold uppercase tracking-wider text-cyan-400 flex items-center gap-2">
                  <User className="h-3.5 w-3.5" />
                  <span>1. ¿A nombre de quién emitimos tu certificación oficial?</span>
                </h3>

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
                      placeholder="Ej. Carlos Alberto Mendoza Alarcón"
                      className="w-full rounded-xl border border-slate-700 bg-slate-900/90 pl-9 pr-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400 transition-colors"
                    />
                  </div>
                  <p className="mt-1 text-[10px] text-slate-400">
                    Así figurará exactamente en tu diploma directivo con código QR y verificación en LinkedIn.
                  </p>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">
                      Tu correo electrónico principal *
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
                    <p className="mt-1 text-[10px] text-slate-400">
                      Aquí te enviaremos tus accesos al aula virtual y tu factura/boleta.
                    </p>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">
                      Celular o WhatsApp (Recomendado)
                    </label>
                    <div className="relative">
                      <Phone className="absolute left-3 top-2.5 h-4 w-4 text-slate-500" />
                      <input
                        type="tel"
                        value={telefono}
                        onChange={(e) => setTelefono(e.target.value)}
                        placeholder="+51 984 567 123"
                        className="w-full rounded-xl border border-slate-700 bg-slate-900/90 pl-9 pr-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400 transition-colors"
                      />
                    </div>
                    <p className="mt-1 text-[10px] text-slate-400">
                      Para darte la bienvenida y que tu tutor académico pueda orientarte.
                    </p>
                  </div>
                </div>

                {/* Comprobante Fiscal SUNAT */}
                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-2">
                    ¿Tu comprobante es a título personal o para tu empresa? *
                  </label>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    <button
                      type="button"
                      onClick={() => {
                        setTipoComprobante('BOLETA');
                        setNumeroDocumento('');
                      }}
                      className={`flex flex-col items-start rounded-xl border p-3 text-xs text-left transition-all ${
                        tipoComprobante === 'BOLETA'
                          ? 'border-cyan-400 bg-cyan-950/40 text-cyan-200 shadow-sm shadow-cyan-500/20'
                          : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center gap-2 font-bold text-white">
                        <FileText className="h-4 w-4 text-cyan-400" />
                        <span>Personal (Boleta con DNI)</span>
                      </div>
                      <span className="text-[10px] text-slate-400 mt-1">
                        Para estudiar por tu cuenta o deducir gastos personales.
                      </span>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        setTipoComprobante('FACTURA');
                        setNumeroDocumento('');
                      }}
                      className={`flex flex-col items-start rounded-xl border p-3 text-xs text-left transition-all ${
                        tipoComprobante === 'FACTURA'
                          ? 'border-indigo-400 bg-indigo-950/40 text-indigo-200 shadow-sm shadow-indigo-500/20'
                          : 'border-slate-800 bg-slate-900/60 text-slate-400 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center gap-2 font-bold text-white">
                        <Building className="h-4 w-4 text-indigo-400" />
                        <span>Empresa (Factura con RUC)</span>
                      </div>
                      <span className="text-[10px] text-slate-400 mt-1">
                        100% deducible de impuestos (Crédito fiscal SUNAT).
                      </span>
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-300 mb-1">
                    {tipoComprobante === 'BOLETA' ? 'Número de DNI del Estudiante (8 dígitos) *' : 'Número de RUC de la Empresa (11 dígitos) *'}
                  </label>
                  <input
                    type="text"
                    required
                    inputMode="numeric"
                    value={numeroDocumento}
                    onChange={handleDocumentoChange}
                    placeholder={tipoComprobante === 'BOLETA' ? 'Ej. 45892147' : 'Ej. 20609812451'}
                    className="w-full rounded-xl border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs font-mono text-white placeholder-slate-500 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400 transition-colors"
                  />
                </div>

                {tipoComprobante === 'FACTURA' && (
                  <div>
                    <label className="block text-xs font-semibold text-indigo-300 mb-1">
                      Razón Social de la Empresa *
                    </label>
                    <input
                      type="text"
                      required
                      value={razonSocial}
                      onChange={(e) => setRazonSocial(e.target.value)}
                      placeholder="Ej. Inversiones Tecnológicas del Perú S.A.C."
                      className="w-full rounded-xl border border-indigo-500/40 bg-slate-900/90 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400 transition-colors"
                    />
                  </div>
                )}
              </div>

              {/* Selector de Métodos de Pago */}
              <div className="space-y-4 pt-2 border-t border-slate-800">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-fuchsia-400 flex items-center gap-2">
                    <CreditCard className="h-3.5 w-3.5" />
                    <span>2. Elige tu método de pago preferido</span>
                  </h3>
                  <span className="text-[10px] text-slate-400 font-mono">
                    Transacción Protegida
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                  {/* Tarjeta Internacional / izipay Online */}
                  <button
                    type="button"
                    onClick={() => setPaymentMethod('IZIPAY')}
                    className={`flex flex-col items-start p-3.5 rounded-2xl border text-left transition-all relative ${
                      paymentMethod === 'IZIPAY'
                        ? 'border-fuchsia-400/80 bg-gradient-to-br from-[#1e0d2b] via-[#0f172a] to-slate-900 text-white shadow-lg shadow-fuchsia-500/20'
                        : 'border-slate-800 bg-slate-900/50 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                    }`}
                  >
                    <div className="flex items-center justify-between w-full mb-2">
                      <CreditCard className={`h-4 w-4 ${paymentMethod === 'IZIPAY' ? 'text-fuchsia-400' : 'text-slate-500'}`} />
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-fuchsia-500/20 text-fuchsia-300 font-bold">
                        izipay Online
                      </span>
                    </div>
                    <div className="text-xs font-bold text-white">Tarjeta Online</div>
                    <div className="text-[10px] text-slate-400 mt-0.5">Visa, Mastercard, Amex, Apple/Google Pay</div>
                  </button>

                  {/* Yape / Plin */}
                  <button
                    type="button"
                    onClick={() => setPaymentMethod('YAPE_PLIN')}
                    className={`flex flex-col items-start p-3.5 rounded-2xl border text-left transition-all relative ${
                      paymentMethod === 'YAPE_PLIN'
                        ? 'border-purple-400 bg-gradient-to-br from-purple-950/60 to-slate-900 text-white shadow-lg shadow-purple-500/10'
                        : 'border-slate-800 bg-slate-900/50 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                    }`}
                  >
                    <div className="flex items-center justify-between w-full mb-2">
                      <Smartphone className={`h-4 w-4 ${paymentMethod === 'YAPE_PLIN' ? 'text-purple-400' : 'text-slate-500'}`} />
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-purple-500/20 text-purple-300 font-bold">
                        Móvil
                      </span>
                    </div>
                    <div className="text-xs font-bold text-white">Yape / Plin</div>
                    <div className="text-[10px] text-slate-400 mt-0.5">Pago móvil por QR / Celular</div>
                  </button>

                  {/* Transferencia BCP / Interbank */}
                  <button
                    type="button"
                    onClick={() => setPaymentMethod('TRANSFERENCIA')}
                    className={`flex flex-col items-start p-3.5 rounded-2xl border text-left transition-all relative ${
                      paymentMethod === 'TRANSFERENCIA'
                        ? 'border-indigo-400 bg-gradient-to-br from-indigo-950/60 to-slate-900 text-white shadow-lg shadow-indigo-500/10'
                        : 'border-slate-800 bg-slate-900/50 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                    }`}
                  >
                    <div className="flex items-center justify-between w-full mb-2">
                      <Building2 className={`h-4 w-4 ${paymentMethod === 'TRANSFERENCIA' ? 'text-indigo-400' : 'text-slate-500'}`} />
                      <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-300 font-bold">
                        Bancario
                      </span>
                    </div>
                    <div className="text-xs font-bold text-white">Transferencia Bancaria</div>
                    <div className="text-[10px] text-slate-400 mt-0.5">BCP e Interbank Empresarial</div>
                  </button>
                </div>

                {/* Contenido según método */}
                {paymentMethod === 'IZIPAY' && (
                  <div className="rounded-2xl border border-fuchsia-500/35 bg-gradient-to-b from-[#180e29]/70 via-slate-950/80 to-[#0e1628]/70 p-4 sm:p-5 space-y-4">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-800 pb-3">
                      <div className="flex items-center gap-2 text-xs font-bold text-white">
                        <ShieldCheck className="h-4 w-4 text-fuchsia-400" />
                        <span>Pasarela Segura izipay Online • Cifrado SSL 3D Secure</span>
                      </div>
                      <div className="flex flex-wrap items-center gap-1 text-[9px] font-mono text-slate-300">
                        <span className="bg-slate-800 px-1.5 py-0.5 rounded text-white font-bold border border-slate-700">VISA</span>
                        <span className="bg-slate-800 px-1.5 py-0.5 rounded text-white font-bold border border-slate-700">MC</span>
                        <span className="bg-slate-800 px-1.5 py-0.5 rounded text-white font-bold border border-slate-700">AMEX</span>
                        <span className="bg-slate-800 px-1.5 py-0.5 rounded text-white font-bold border border-slate-700">DINERS</span>
                        <span className="bg-slate-800 px-1.5 py-0.5 rounded text-white font-bold border border-slate-700"> PAY</span>
                        <span className="bg-slate-800 px-1.5 py-0.5 rounded text-white font-bold border border-slate-700">G PAY</span>
                      </div>
                    </div>

                    <p className="text-xs text-slate-300 leading-relaxed">
                      Al presionar el botón ingresarás a la pasarela protegida de <strong>izipay Online</strong>. Tu pago se procesa en tiempo real con liquidación bancaria directa a través de Interbank y BCP, habilitando tus accesos al instante sin tiempos de espera.
                    </p>

                    <button
                      type="button"
                      onClick={handleSubmitIzipay}
                      disabled={isSubmitting}
                      className="w-full flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-fuchsia-600 via-indigo-600 to-cyan-500 px-6 py-4 text-xs sm:text-sm font-bold text-white shadow-xl shadow-fuchsia-500/20 hover:opacity-95 active:scale-[0.98] transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {isSubmitting ? (
                        <>
                          <LoaderCircle className="h-4 w-4 animate-spin" />
                          <span>Conectando con izipay Online...</span>
                        </>
                      ) : (
                        <>
                          <Lock className="h-4 w-4" />
                          <span>Pagar con izipay Online • {priceAmount} {priceCurrency}</span>
                          <ArrowRight className="h-4 w-4 ml-1" />
                        </>
                      )}
                    </button>
                  </div>
                )}

                {paymentMethod === 'YAPE_PLIN' && (
                  <div className="rounded-2xl border border-purple-500/30 bg-slate-950/70 p-4 sm:p-5 space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div>
                        <div className="text-xs font-bold text-white flex items-center gap-2">
                          <Smartphone className="h-4 w-4 text-purple-400" />
                          <span>Billeteras Móviles Yape y Plin</span>
                        </div>
                        <p className="text-[11px] text-slate-400 mt-0.5">
                          Monto a abonar: <strong className="text-purple-300 font-mono text-sm">{priceAmount} {priceCurrency}</strong>
                        </p>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-center">
                      <div className="flex flex-col items-center justify-center p-4 rounded-xl border border-purple-500/20 bg-purple-950/20 text-center">
                        <div className="relative p-2.5 rounded-xl bg-white text-slate-950 shadow-md">
                          <QrCode className="h-28 w-28 text-slate-900" />
                          <div className="absolute inset-0 flex items-center justify-center">
                            <span className="bg-purple-600 text-white font-mono text-[9px] font-black px-1.5 py-0.5 rounded shadow">
                              YAPEAR
                            </span>
                          </div>
                        </div>
                        <span className="text-[10px] font-mono text-purple-300 mt-2 font-semibold">
                          Escanea desde tu app Yape o Plin
                        </span>
                      </div>

                      <div className="space-y-3 text-xs">
                        <div>
                          <span className="text-slate-400 text-[11px]">Número Celular Oficial:</span>
                          <div className="flex items-center justify-between mt-1 p-2 rounded-lg bg-slate-900 border border-slate-700">
                            <span className="font-mono font-bold text-sm text-purple-300 tracking-wider">
                              941 238 905
                            </span>
                            <button
                              type="button"
                              onClick={() => handleCopy('941238905', 'yape-num')}
                              className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-white transition-colors"
                            >
                              {copiedKey === 'yape-num' ? (
                                <Check className="h-3.5 w-3.5 text-emerald-400" />
                              ) : (
                                <Copy className="h-3.5 w-3.5" />
                              )}
                              <span>{copiedKey === 'yape-num' ? 'Copiado' : 'Copiar'}</span>
                            </button>
                          </div>
                        </div>

                        <div>
                          <span className="text-slate-400 text-[11px]">Titular Registrado:</span>
                          <div className="font-bold text-slate-200 mt-0.5">
                            LYSANDRI S.A.C.
                          </div>
                          <div className="text-[10px] font-mono text-slate-400">
                            RUC: 20609812451
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="pt-2 border-t border-slate-800">
                      <p className="text-[11px] text-slate-300 mb-3">
                        Al presionar el botón, el backend registrará tu orden oficial en estado <strong className="text-amber-300">PENDIENTE_PAGO</strong> y te asignará un código único para que adjuntes tu voucher. La activación en Moodle se realizará tras la verificación administrativa.
                      </p>

                      <button
                        type="button"
                        disabled={isSubmitting}
                        onClick={() => handleManualOrderRegistration('YAPE', 'Yape / Plin')}
                        className="w-full flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-purple-600 to-teal-600 px-6 py-3.5 text-xs font-bold text-white shadow-lg hover:opacity-95 active:scale-[0.98] transition-all disabled:opacity-50"
                      >
                        {isSubmitting ? (
                          <>
                            <LoaderCircle className="h-4 w-4 animate-spin" />
                            <span>Registrando orden en el servidor...</span>
                          </>
                        ) : (
                          <>
                            <MessageCircle className="h-4 w-4" />
                            <span>Registrar Orden y Continuar con Voucher</span>
                            <ArrowRight className="h-3.5 w-3.5 ml-1" />
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                )}

                {paymentMethod === 'TRANSFERENCIA' && (
                  <div className="rounded-2xl border border-indigo-500/30 bg-slate-950/70 p-4 sm:p-5 space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div>
                        <div className="text-xs font-bold text-white flex items-center gap-2">
                          <Building2 className="h-4 w-4 text-indigo-400" />
                          <span>Cuentas Bancarias Oficiales (BCP e Interbank)</span>
                        </div>
                        <div className="text-[11px] text-slate-400 mt-0.5">
                          Titular: <strong className="text-slate-200">LYSANDRI S.A.C.</strong> · RUC: <strong className="font-mono text-slate-200">20609812451</strong>
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="font-mono text-xs font-bold text-indigo-300">
                          {priceAmount} {priceCurrency}
                        </span>
                      </div>
                    </div>

                    {cargandoCuentas ? (
                      <div className="flex items-center justify-center py-6 text-xs text-slate-400 gap-2">
                        <LoaderCircle className="h-4 w-4 animate-spin text-cyan-400" />
                        <span>Consultando cuentas bancarias oficiales autorizadas...</span>
                      </div>
                    ) : cuentasActivas.length > 0 ? (
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 font-mono text-xs">
                        {cuentasActivas.map((cta) => (
                          <div
                            key={cta.id}
                            className="rounded-xl border border-slate-800 bg-slate-900/90 p-3.5 space-y-2"
                          >
                            <div className="flex items-center justify-between font-sans">
                              <span className="font-bold text-indigo-400 text-xs">{cta.banco}</span>
                              <span className="text-[9px] bg-indigo-950 text-indigo-300 px-1.5 py-0.5 rounded font-mono">
                                {cta.tipoCuenta} ({cta.moneda})
                              </span>
                            </div>
                            <div className="space-y-1.5 text-[11px]">
                              {cta.numeroCuenta && (
                                <div>
                                  <div className="text-slate-500 text-[10px]">N° Cuenta:</div>
                                  <div className="flex justify-between items-center text-white font-bold">
                                    <span>{cta.numeroCuenta}</span>
                                    <button
                                      type="button"
                                      onClick={() => handleCopy(cta.numeroCuenta!, `${cta.id}-num`)}
                                      className="text-slate-400 hover:text-cyan-400 transition-colors"
                                    >
                                      {copiedKey === `${cta.id}-num` ? (
                                        <Check className="h-3 w-3 text-emerald-400" />
                                      ) : (
                                        <Copy className="h-3 w-3" />
                                      )}
                                    </button>
                                  </div>
                                </div>
                              )}
                              {cta.cci && (
                                <div>
                                  <div className="text-slate-500 text-[10px]">CCI Interbancario:</div>
                                  <div className="flex justify-between items-center text-slate-300">
                                    <span className="text-[10px]">{cta.cci}</span>
                                    <button
                                      type="button"
                                      onClick={() => handleCopy(cta.cci!, `${cta.id}-cci`)}
                                      className="text-slate-400 hover:text-cyan-400 transition-colors"
                                    >
                                      {copiedKey === `${cta.id}-cci` ? (
                                        <Check className="h-3 w-3 text-emerald-400" />
                                      ) : (
                                        <Copy className="h-3 w-3" />
                                      )}
                                    </button>
                                  </div>
                                </div>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="rounded-xl border border-amber-500/30 bg-amber-950/20 p-3.5 text-xs text-amber-300 flex items-start gap-2.5">
                        <Info className="h-4 w-4 shrink-0 text-amber-400 mt-0.5" />
                        <div>
                          <p className="font-bold text-white">Cuentas Corrientes en Configuración Oficial</p>
                          <p className="text-[11px] text-slate-300 mt-1 leading-relaxed">
                            Las cuentas bancarias corporativas para transferencias directas se encuentran en proceso de formalización técnica en este entorno. Puedes registrar tu orden para recibir la orientación de abono verificada por parte de nuestra asesora académica, o pagar con tarjeta mediante izipay Online.
                          </p>
                        </div>
                      </div>
                    )}

                    <div className="pt-2 border-t border-slate-800">
                      <p className="text-[11px] text-slate-300 mb-3">
                        Al presionar el botón, el backend registrará formalmente tu orden de compra con método <strong className="text-indigo-300 font-mono">TRANSFERENCIA</strong> en estado <strong className="text-amber-300 font-mono">PENDIENTE_PAGO</strong>. Una vez que tu abono sea verificado por personal administrativo autorizado, se activarán tus accesos en el Campus Virtual.
                      </p>

                      <button
                        type="button"
                        disabled={isSubmitting}
                        onClick={() => handleManualOrderRegistration('TRANSFERENCIA', 'Transferencia Bancaria')}
                        className="w-full flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-indigo-600 to-cyan-600 px-6 py-3.5 text-xs font-bold text-white shadow-lg shadow-indigo-600/20 hover:opacity-95 active:scale-[0.98] transition-all disabled:opacity-50"
                      >
                        {isSubmitting ? (
                          <>
                            <LoaderCircle className="h-4 w-4 animate-spin" />
                            <span>Registrando orden en el servidor...</span>
                          </>
                        ) : (
                          <>
                            <Building2 className="h-4 w-4" />
                            <span>Registrar Orden y Coordinar Transferencia</span>
                            <ArrowRight className="h-3.5 w-3.5 ml-1" />
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </>
          )}

          {/* Banner inferior de confianza */}
          <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-4 flex flex-wrap items-center justify-between gap-3 text-xs">
            <div className="flex items-center gap-2.5 text-slate-300">
              <div className="rounded-full bg-emerald-500/10 p-2 text-emerald-400">
                <HeartHandshake className="h-4 w-4" />
              </div>
              <div>
                <p className="font-semibold text-white">¿Deseas atención personalizada o pago corporativo?</p>
                <p className="text-[11px] text-slate-400">Un asesor académico de Lysandri te atiende en tiempo real por WhatsApp.</p>
              </div>
            </div>
            <a
              href="https://wa.me/51941238905?text=Hola%20Lysandri,%20deseo%20asistencia%20con%20mi%20matr%C3%ADcula%20y%20m%C3%A9todos%20de%20pago"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-500/30 bg-emerald-950/40 px-3.5 py-2 font-bold text-emerald-400 hover:bg-emerald-900/40 transition-colors"
            >
              <MessageCircle className="h-4 w-4" />
              <span>Chatear por WhatsApp</span>
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};

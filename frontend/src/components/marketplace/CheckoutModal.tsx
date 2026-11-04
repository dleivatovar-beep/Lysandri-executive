import React, { useState, useEffect, useCallback, useRef } from 'react';
import './checkout-modal.css';
import {
  X,
  Lock,
  FileText,
  Building,
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
  ArrowRight,
  ExternalLink,
  CheckCircle2,
  HeartHandshake,
} from 'lucide-react';
import visaLogo from '../../assets/payment-logos/visa.svg';
import mastercardLogo from '../../assets/payment-logos/mastercard.svg';
import amexLogo from '../../assets/payment-logos/american-express.svg';
import dinersLogo from '../../assets/payment-logos/diners.svg';
import yapeLogo from '../../assets/payment-logos/yape.png';
import bcpLogo from '../../assets/payment-logos/bcp.svg';
import interbankLogo from '../../assets/payment-logos/interbank.svg';
import { Playbook } from '../../types';
import { storeService, CuentaBancariaAprobada } from '../../services/storeService';
import { useAuth } from '../../context/AuthContext';

export interface CheckoutModalProps {
  isOpen: boolean;
  onClose: () => void;
  playbook: Playbook | null;
}

export type PaymentMethod = 'IZIPAY' | 'YAPE_PLIN' | 'TRANSFERENCIA';

// Gestor centralizado de carga de script de Krypton (compartido entre sesiones)
export type ScriptLoadStatus = 'IDLE' | 'LOADING' | 'LOADED' | 'FAILED';
let kryptonScriptStatus: ScriptLoadStatus = 'IDLE';
let kryptonScriptPromise: Promise<any> | null = null;

// Contador global incremental para sesiones de Izipay (garantiza unicidad entre diferentes montajes)
let globalSessionSequence = 0;

// Estado de propiedad global del SDK entre instancias del componente
export interface GlobalSdkOwnership {
  activeRequestedSessionId: string | null;
  activeOwnerSessionId: string | null;
  ownerInstanceId: string | null;
}

export const getGlobalSdkOwnership = (): GlobalSdkOwnership => {
  if (typeof window !== 'undefined') {
    if (!(window as any).__krSdkOwnership) {
      (window as any).__krSdkOwnership = {
        activeRequestedSessionId: null,
        activeOwnerSessionId: null,
        ownerInstanceId: null,
      };
    }
    return (window as any).__krSdkOwnership;
  }
  return {
    activeRequestedSessionId: null,
    activeOwnerSessionId: null,
    ownerInstanceId: null,
  };
};

export const resetGlobalSdkOwnership = (): void => {
  const state = getGlobalSdkOwnership();
  state.activeRequestedSessionId = null;
  state.activeOwnerSessionId = null;
  state.ownerInstanceId = null;
};

export const getKryptonScriptStatus = (): ScriptLoadStatus => kryptonScriptStatus;

export const resetKryptonScriptLoader = (): void => {
  kryptonScriptStatus = 'IDLE';
  kryptonScriptPromise = null;
  if (typeof document !== 'undefined') {
    const existing = document.getElementById('izipay-krypton-client-sdk');
    if (existing) {
      existing.remove();
    }
  }
};

export const loadKryptonClientScript = (publicKey: string): Promise<any> => {
  if (typeof window === 'undefined') {
    return Promise.resolve(null);
  }

  if ((window as any).KR) {
    kryptonScriptStatus = 'LOADED';
    kryptonScriptPromise = null;
    return Promise.resolve((window as any).KR);
  }

  if (kryptonScriptStatus === 'LOADING' && kryptonScriptPromise) {
    return kryptonScriptPromise;
  }

  const scriptId = 'izipay-krypton-client-sdk';
  if (typeof document !== 'undefined') {
    const existing = document.getElementById(scriptId);
    if (existing) {
      existing.remove();
    }
  }

  kryptonScriptStatus = 'LOADING';
  kryptonScriptPromise = new Promise((resolve, reject) => {
    if (typeof document === 'undefined') {
      kryptonScriptStatus = 'LOADED';
      kryptonScriptPromise = null;
      resolve((window as any).KR || null);
      return;
    }

    const script = document.createElement('script');
    script.id = scriptId;
    script.src = 'https://static.micuentaweb.pe/static/js/krypton-client/V4.0/stable/kr-payment-form.min.js';
    script.setAttribute('kr-public-key', publicKey);
    script.setAttribute('kr-post-url-success', '');
    script.setAttribute('kr-spa-mode', 'true');

    script.onload = () => {
      kryptonScriptStatus = 'LOADED';
      kryptonScriptPromise = null;
      resolve((window as any).KR);
    };

    script.onerror = () => {
      kryptonScriptStatus = 'FAILED';
      kryptonScriptPromise = null;
      if (script.parentNode) {
        script.parentNode.removeChild(script);
      }
      reject(new Error('Error de conexión al cargar la pasarela segura izipay. Por favor verifique su conexión e intente nuevamente.'));
    };

    document.head.appendChild(script);
  });

  return kryptonScriptPromise;
};

// Cola de serialización para operaciones asíncronas globales sobre el SDK (KR)
let activeSdkOperationPromise: Promise<any> = Promise.resolve();

export function enqueueSdkOperation<T>(op: () => Promise<T>): Promise<T> {
  const nextPromise = activeSdkOperationPromise
    .catch(() => {}) // Un fallo en operación previa no debe bloquear operaciones subsiguientes
    .then(() => op());

  activeSdkOperationPromise = nextPromise.then(() => {}, () => {});
  return nextPromise;
}

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

  // Estado para formulario incrustado de izipay Online y ciclo de vida de Krypton SDK
  const [izipayData, setIzipayData] = useState<{
    formToken: string;
    publicKey?: string;
    codigoOrden?: string;
  } | null>(null);
  const [izipaySdkLoading, setIzipaySdkLoading] = useState(false);
  const [izipayFormReady, setIzipayFormReady] = useState(false);
  const [izipaySdkError, setIzipaySdkError] = useState<string | null>(null);
  const [izipayVerifying, setIzipayVerifying] = useState(false);

  // Ciclo de vida explícito para sesiones y listeners de pasarela izipay (Krypton SDK)
  const instanceIdRef = useRef<string>(`modal_${Math.random().toString(36).substring(2, 10)}`);
  const isMountedRef = useRef<boolean>(true);
  const activeOrderCodeRef = useRef<string | null>(null);
  const activeSessionIdRef = useRef<string | null>(null);
  const formReadyRef = useRef<boolean>(false);
  const generationRef = useRef<number>(0);

  // Generador de identificadores de sesión globalmente únicos entre distintos montajes
  const generateUniqueSessionId = useCallback((orderCode: string, instanceId: string): string => {
    const seq = ++globalSessionSequence;
    const timestamp = Date.now();
    const rand = Math.random().toString(36).substring(2, 9);
    return `izi_sess_${seq}_${timestamp}_${rand}_${instanceId}_${orderCode || 'sin_orden'}`;
  }, []);

  // Coordinación asíncrona de limpieza: garantiza propiedad global antes de modificar el SDK
  const coordinateAsyncCleanup = useCallback((sessionIdToClean: string | null) => {
    if (!sessionIdToClean) return;

    // Si la sesión a limpiar era la solicitada activa globalmente, liberarla
    const ownership = getGlobalSdkOwnership();
    if (ownership.activeRequestedSessionId === sessionIdToClean) {
      ownership.activeRequestedSessionId = null;
    }

    enqueueSdkOperation(async () => {
      const globalState = getGlobalSdkOwnership();

      // 1. Si otra sesión ya está solicitada o activa (inter o intra-instancia), no interferir
      if (globalState.activeRequestedSessionId !== null && globalState.activeRequestedSessionId !== sessionIdToClean) {
        return;
      }

      // 2. Si el SDK posee recursos que pertenecen a otra sesión propietaria, no borrarlos
      if (globalState.activeOwnerSessionId !== null && globalState.activeOwnerSessionId !== sessionIdToClean) {
        return;
      }

      // 3. Comprobar referencia local si la instancia aún está montada
      if (activeSessionIdRef.current !== null && activeSessionIdRef.current !== sessionIdToClean) {
        return;
      }

      // 4. Limpieza del coordinador global: solo si pertenece a sessionIdToClean
      if (typeof window !== 'undefined') {
        const coord = (window as any).__krCoordinator;
        if (coord && coord.sessionId === sessionIdToClean) {
          (window as any).__krCoordinator = null;
        }
      }

      // 5. En este punto, no hay otra sesión solicitada ni propietaria.
      // O bien sessionIdToClean es la dueña, o son recursos huérfanos que le pertenecían.
      if (typeof window !== 'undefined') {
        const KR = (window as any).KR;
        if (KR) {
          if (typeof KR.removeForms === 'function') {
            await KR.removeForms();
          } else if (typeof KR.removeEventCallbacks === 'function') {
            await KR.removeEventCallbacks();
          }
        }
      }

      // Si sessionIdToClean era la dueña registrada, liberar la propiedad
      if (globalState.activeOwnerSessionId === sessionIdToClean) {
        globalState.activeOwnerSessionId = null;
        globalState.ownerInstanceId = null;
      }
    }).catch((err) => {
      console.warn('Advertencia en limpieza coordinada de pasarela izipay:', err);
    });
  }, []);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      generationRef.current += 1;
      const sessToClean = activeSessionIdRef.current;
      activeSessionIdRef.current = null;
      activeOrderCodeRef.current = null;
      formReadyRef.current = false;
      if (typeof window !== 'undefined') {
        const coord = (window as any).__krCoordinator;
        if (coord && (!coord.sessionId || coord.sessionId === sessToClean)) {
          (window as any).__krCoordinator = null;
        }
      }
      coordinateAsyncCleanup(sessToClean);
    };
  }, [coordinateAsyncCleanup]);

  // Al cerrar la modal, invalidar la sesión activa y limpiar formularios
  useEffect(() => {
    if (!isOpen) {
      generationRef.current += 1;
      const sessToClean = activeSessionIdRef.current;
      activeSessionIdRef.current = null;
      activeOrderCodeRef.current = null;
      formReadyRef.current = false;
      setIzipayFormReady(false);
      if (typeof window !== 'undefined') {
        const coord = (window as any).__krCoordinator;
        if (coord && (!coord.sessionId || coord.sessionId === sessToClean)) {
          (window as any).__krCoordinator = null;
        }
      }
      coordinateAsyncCleanup(sessToClean);
      setIzipayData(null);
      setIzipaySdkError(null);
      setIzipaySdkLoading(false);
      setIzipayVerifying(false);
      setErrorMessage('');
    }
  }, [isOpen, coordinateAsyncCleanup]);

  const getBuyerScope = (): string => {
    return storeService.getBuyerScope(user, email);
  };

  const getIdempotencyKey = (): string => {
    if (!playbook) return '';
    const scope = getBuyerScope();
    return storeService.getOrCreateIdempotencyKey(scope, playbook.programId);
  };

  const initKryptonSdk = useCallback(async () => {
    if (!izipayData || !izipayData.formToken || !izipayData.publicKey) return;

    setIzipaySdkError(null);
    setIzipaySdkLoading(true);
    setIzipayFormReady(false);
    formReadyRef.current = false;

    const sessionOrderCode = izipayData.codigoOrden || '';
    const currentSessionId = generateUniqueSessionId(sessionOrderCode, instanceIdRef.current);
    activeSessionIdRef.current = currentSessionId;
    activeOrderCodeRef.current = sessionOrderCode;
    const currentGen = ++generationRef.current;

    // Registrar sesión como globalmente solicitada
    const ownership = getGlobalSdkOwnership();
    ownership.activeRequestedSessionId = currentSessionId;
    ownership.ownerInstanceId = instanceIdRef.current;

    const isSessionActive = () => {
      const globalState = getGlobalSdkOwnership();
      return (
        isMountedRef.current &&
        isOpen &&
        activeSessionIdRef.current === currentSessionId &&
        globalState.activeRequestedSessionId === currentSessionId
      );
    };

    // Callback de error con aislamiento estricto por sesión
    const handleSessionError = (ev: any) => {
      if (!isSessionActive()) return;
      const eventOrderId = ev?.orderId || 
                           ev?.orderDetails?.orderId || 
                           ev?.clientAnswer?.orderDetails?.orderId || 
                           ev?.clientAnswer?.orderId;
      if (eventOrderId && typeof eventOrderId === 'string' && eventOrderId.trim() && eventOrderId.trim() !== sessionOrderCode) {
        return;
      }
      const msg = ev?.errorMessage || 'Ocurrió un error al procesar el pago con la pasarela.';
      setIzipaySdkError(msg);
      setIzipayVerifying(false);
    };

    // Callback de submit con verificación de orden y backend
    const handleSessionSubmit = (paymentData: any) => {
      if (!isSessionActive() || !formReadyRef.current) return false;
      const paidOrderId = paymentData?.orderId || 
                          paymentData?.orderDetails?.orderId || 
                          paymentData?.clientAnswer?.orderDetails?.orderId ||
                          paymentData?.clientAnswer?.orderId;

      if (!paidOrderId || typeof paidOrderId !== 'string' || !paidOrderId.trim()) {
        console.warn('Evento de pago descartado: no contiene identificador de orden.');
        return false;
      }

      if (paidOrderId.trim() !== sessionOrderCode) {
        console.warn('Evento de pago descartado: no coincide con la orden activa actual.', {
          esperado: sessionOrderCode,
          recibido: paidOrderId,
        });
        return false;
      }

      setIzipayVerifying(true);
      window.location.href = `/pago-exitoso?orden=${encodeURIComponent(sessionOrderCode)}`;
      return false;
    };

    // Publicar coordinador activo de inmediato para inspección externa e interoperabilidad
    if (typeof window !== 'undefined') {
      (window as any).__krCoordinator = {
        ownerId: instanceIdRef.current,
        sessionId: currentSessionId,
        generation: currentGen,
        activeOrderCode: sessionOrderCode,
        isMounted: isSessionActive,
        isReady: () => isSessionActive() && formReadyRef.current,
        onError: handleSessionError,
        onSubmit: handleSessionSubmit,
        cleanup: () => coordinateAsyncCleanup(currentSessionId),
      };
    }

    try {
      // 1. Cargar script centralizado (o esperar si ya está cargando)
      if (!isSessionActive()) return;
      await loadKryptonClientScript(izipayData.publicKey);
      if (!isSessionActive()) return;

      // 2. Coordinar operaciones en cola para el SDK global
      await enqueueSdkOperation(async () => {
        if (!isSessionActive()) return;

        const KR = (window as any).KR;
        if (!KR) {
          throw new Error('SDK de izipay (KR) no disponible tras la carga del script.');
        }

        // Limpieza previa si existen recursos de otra sesión anterior
        if (typeof KR.removeForms === 'function') {
          await KR.removeForms();
        } else if (typeof KR.removeEventCallbacks === 'function') {
          await KR.removeEventCallbacks();
        }

        // Comprobar vigencia tras esperar la limpieza
        if (!isSessionActive()) return;

        // Configurar el nuevo token
        await KR.setFormToken(izipayData.formToken);

        // Comprobar vigencia tras setFormToken
        if (!isSessionActive()) return;

        // Renderizar el formulario mediante renderElements (contrato oficial Lyra / Krypton)
        // La promesa devuelta por renderElements confirma que los elementos fueron inyectados en el DOM.
        // Si renderElements rechaza (por cualquier error, incluyendo "already rendered"),
        // no se asume disponibilidad ni se valida por la mera existencia de contenedores vacíos en el DOM.
        if (typeof KR.renderElements === 'function') {
          await KR.renderElements('.kr-embedded');
        } else {
          throw new Error('Método KR.renderElements no disponible en el SDK de izipay.');
        }

        // Comprobar vigencia de la sesión tras la confirmación del renderizado
        if (!isSessionActive()) return;

        // Registrar propiedad de los recursos creados en el SDK
        const ownershipSuccess = getGlobalSdkOwnership();
        ownershipSuccess.activeOwnerSessionId = currentSessionId;
        ownershipSuccess.ownerInstanceId = instanceIdRef.current;

        formReadyRef.current = true;
        setIzipayFormReady(true);
        setIzipaySdkLoading(false);

        // Registrar callbacks directamente en el SDK solo tras confirmar la disponibilidad documentada
        if (typeof KR.onError === 'function') {
          KR.onError(handleSessionError);
        }
        if (typeof KR.onSubmit === 'function') {
          KR.onSubmit(handleSessionSubmit);
        }
      });
    } catch (err: any) {
      if (isSessionActive()) {
        formReadyRef.current = false;
        setIzipayFormReady(false);
        setIzipaySdkLoading(false);
        const detail = err?.message ? `: ${err.message}` : '';
        const msg = `No se pudo inicializar el formulario seguro de izipay${detail}. Por favor reintente.`;
        setIzipaySdkError(msg);
      }
    }
  }, [izipayData, isOpen, coordinateAsyncCleanup, generateUniqueSessionId]);

  useEffect(() => {
    if (izipayData) {
      initKryptonSdk();
    }
    const sessToClean = activeSessionIdRef.current;
    return () => {
      // Solo limpiar si la sesión que se desmonta o cambia es la que aún está activa
      coordinateAsyncCleanup(sessToClean);
    };
  }, [izipayData, initKryptonSdk, coordinateAsyncCleanup]);

  useEffect(() => {
    const sessToClean = activeSessionIdRef.current;
    activeSessionIdRef.current = null;
    activeOrderCodeRef.current = null;
    coordinateAsyncCleanup(sessToClean);
    setIzipayData(null);
    setIzipaySdkError(null);
    setIzipaySdkLoading(false);
    setIzipayVerifying(false);
  }, [playbook?.programId, coordinateAsyncCleanup]);

  const handleStartNewOrder = () => {
    generationRef.current += 1;
    const sessToClean = activeSessionIdRef.current;
    activeSessionIdRef.current = null;
    activeOrderCodeRef.current = null;
    formReadyRef.current = false;
    setIzipayFormReady(false);
    if (typeof window !== 'undefined') {
      const coord = (window as any).__krCoordinator;
      if (coord && (!coord.sessionId || coord.sessionId === sessToClean)) {
        (window as any).__krCoordinator = null;
      }
    }
    coordinateAsyncCleanup(sessToClean);
    if (playbook) {
      const scope = getBuyerScope();
      storeService.clearCheckoutIntent(scope, playbook.programId);
    }
    setOrdenRegistradaCodigo(null);
    setOrdenRegistradaMetodo(null);
    setIzipayData(null);
    setIzipaySdkError(null);
    setIzipaySdkLoading(false);
    setIzipayVerifying(false);
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
    const parsedId = Number(playbook.id);
    if (!playbook.id || isNaN(parsedId) || parsedId <= 0 || !Number.isInteger(parsedId)) {
      setErrorMessage('El programa seleccionado no es válido o no está disponible para compra.');
      return;
    }
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

      if (!isMountedRef.current || !isOpen) return;

      if (response.formToken) {
        if (!response.publicKey || !response.publicKey.trim()) {
          throw new Error('Respuesta de checkout incompleta: la pasarela no suministró la clave pública requerida.');
        }
        setIzipayData({
          formToken: response.formToken,
          publicKey: response.publicKey,
          codigoOrden: response.codigoOrden,
        });
      } else if (response.checkoutUrl || response.stripeCheckoutUrl) {
        storeService.clearCheckoutIntent(scope, playbook.programId);
        window.location.href = response.checkoutUrl || response.stripeCheckoutUrl!;
      } else {
        throw new Error('No se pudo generar la sesión de pago con izipay Online');
      }
    } catch (err: any) {
      if (!isMountedRef.current || !isOpen) return;
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
    const parsedId = Number(playbook.id);
    if (!playbook.id || isNaN(parsedId) || parsedId <= 0 || !Number.isInteger(parsedId)) {
      setErrorMessage('El programa seleccionado no es válido o no está disponible para compra.');
      return;
    }
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
    <div
      className="lx-checkout-shell"
      role="dialog"
      aria-modal="true"
      aria-labelledby="checkout-dialog-title"
    >
      {/* Backdrop */}
      <button
        type="button"
        className="lx-checkout-backdrop"
        onClick={onClose}
        aria-label="Cerrar modal"
      />

      {/* Modal Dialog */}
      <section className="lx-checkout-dialog">
        {/* Header con estilo editorial */}
        <header className="lx-checkout-header">
          <div className="lx-checkout-course">
            <p className="lx-checkout-kicker">
              <Sparkles size={12} style={{ display: 'inline', verticalAlign: '-1px', marginRight: '4px' }} />
              Matrícula ejecutiva oficial · Campus Moodle 24/7
            </p>
            <h2 id="checkout-dialog-title">{playbook.title}</h2>
          </div>

          <div className="lx-checkout-price">
            <strong>{priceCurrency === 'USD' ? `$${priceAmount}` : `S/ ${priceAmount}`}</strong>
            <span>{priceCurrency} · SUNAT Oficial</span>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar modal"
            className="lx-checkout-close"
          >
            <X size={18} />
          </button>
        </header>

        {/* Scrollable Body */}
        <div className="lx-checkout-body">
          

          {/* Tarjeta de Asesora Académica */}
          <div className="lx-advisor-box">
            <GraduationCap size={18} />
            <div style={{ flex: 1 }}>
              <strong>Lic. Sofía Valdivia · Coordinadora de Admisiones</strong>
              <p style={{ margin: '3px 0 0', fontSize: '11px', color: '#9eb0bc' }}>
                «¡Hola! Te acompaño en tu inscripción. Con izipay Online tus accesos se aprovisionan una vez confirmada la notificación segura de pago (IPN). En transferencias bancarias, nuestro equipo administrativo verifica el comprobante para dar de alta tus accesos.»
              </p>
              <div style={{ display: 'flex', gap: '12px', marginTop: '6px', fontSize: '10px', color: '#8da2af', flexWrap: 'wrap' }}>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px', color: '#34d399' }}>
                  <CheckCircle2 size={12} /> Acompañamiento 1-a-1
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px', color: '#53cbe5' }}>
                  <GraduationCap size={12} /> Alta en Moodle tras confirmación
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px', color: '#c084fc' }}>
                  <ShieldCheck size={12} /> Certificación oficial con QR
                </span>
              </div>
            </div>
          </div>

          {/* Banner de Confirmación de Orden Registrada (cuando aplica) */}
          {ordenRegistradaCodigo ? (
            <div className="lx-order-confirmed">
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                <CheckCircle2 size={22} style={{ color: '#34d399', flexShrink: 0, marginTop: '2px' }} />
                <div style={{ flex: 1 }}>
                  <h4>¡Orden Registrada en el Sistema Lysandri!</h4>
                  <p style={{ margin: '4px 0 8px', fontSize: '12px', fontFamily: 'monospace', color: '#6ee7b7' }}>
                    Código Oficial: <strong style={{ fontSize: '16px', color: '#ffffff' }}>{ordenRegistradaCodigo}</strong>
                  </p>
                  <p style={{ margin: '0 0 10px', fontSize: '11px', lineHeight: 1.45, color: '#d1fae5' }}>
                    Tu orden se encuentra en estado <strong style={{ color: '#fbbf24', fontFamily: 'monospace' }}>PENDIENTE_PAGO</strong>.
                    Por favor remite tu comprobante de abono bancario indicando este código.
                    Nuestro equipo administrativo validará el abono en cuenta bancaria antes de activar tus credenciales en el Campus Virtual.
                  </p>
                  <div style={{ background: 'rgba(0,0,0,0.25)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', padding: '10px', fontSize: '11px', marginBottom: '12px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                      <span style={{ color: '#9eb0bc' }}>Importe Registrado:</span>
                      <strong style={{ color: '#ffffff', fontFamily: 'monospace' }}>{priceAmount} {priceCurrency}</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                      <span style={{ color: '#9eb0bc' }}>Titular:</span>
                      <span style={{ color: '#ffffff' }}>{nombreCompleto}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ color: '#9eb0bc' }}>Comprobante:</span>
                      <span style={{ color: '#ffffff' }}>{tipoComprobante} ({numeroDocumento})</span>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      onClick={handleOpenWhatsAppForRegisteredOrder}
                      className="lx-checkout-whatsapp-action"
                      style={{ marginTop: 0, flex: '1 1 200px' }}
                    >
                      <MessageCircle size={16} />
                      <span>Enviar Comprobante por WhatsApp</span>
                      <ExternalLink size={14} />
                    </button>
                    <button
                      type="button"
                      onClick={handleStartNewOrder}
                      className="lx-checkout-secondary-action"
                      style={{ marginTop: 0, flex: '0 0 auto', padding: '0 16px' }}
                    >
                      Iniciar Nueva Orden
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <>
              {errorMessage && (
                <div className="lx-checkout-error" role="alert">
                  <AlertCircle size={18} />
                  <div style={{ flex: 1 }}>
                    <span>{errorMessage}</span>
                    {(errorMessage.toLowerCase().includes('conflicto') ||
                      errorMessage.toLowerCase().includes('difieren') ||
                      errorMessage.toLowerCase().includes('clave')) && (
                      <div style={{ marginTop: '6px' }}>
                        <button
                          type="button"
                          onClick={handleStartNewOrder}
                          style={{ background: 'none', border: 'none', padding: 0, color: '#fcd34d', fontSize: '11px', textDecoration: 'underline', cursor: 'pointer', fontWeight: 'bold' }}
                        >
                          Iniciar una nueva compra con nueva clave
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* Sección 1: Datos del Alumno y Facturación */}
              <section className="lx-checkout-section" aria-labelledby="checkout-customer-heading">
                <div className="lx-section-heading">
                  <div>
                    <h3 id="checkout-customer-heading">1. Datos del Alumno y Facturación</h3>
                    <p>Emisión oficial de diploma directivo y comprobante fiscal SUNAT.</p>
                  </div>
                </div>

                <div className="lx-fields-grid">
                  <div className="lx-field lx-field-wide">
                    <label htmlFor="checkout-name">
                      Nombres y apellidos completos <small>*</small>
                    </label>
                    <input
                      id="checkout-name"
                      type="text"
                      required
                      value={nombreCompleto}
                      onChange={(e) => setNombreCompleto(e.target.value)}
                      placeholder="Ej. Carlos Alberto Mendoza Alarcón"
                    />
                    <small>Así figurará exactamente en tu diploma directivo con código QR y LinkedIn.</small>
                  </div>

                  <div className="lx-field">
                    <label htmlFor="checkout-email">
                      Correo electrónico principal <small>*</small>
                    </label>
                    <input
                      id="checkout-email"
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder="c.mendoza@empresa.com"
                    />
                    <small>Para enviarte tus accesos al campus y comprobante de pago.</small>
                  </div>

                  <div className="lx-field">
                    <label htmlFor="checkout-phone">
                      Celular o WhatsApp <small>(Recomendado)</small>
                    </label>
                    <input
                      id="checkout-phone"
                      type="tel"
                      value={telefono}
                      onChange={(e) => setTelefono(e.target.value)}
                      placeholder="+51 984 567 123"
                    />
                    <small>Para acompañamiento académico y tutoría directa.</small>
                  </div>
                </div>

                <fieldset className="lx-invoice-picker">
                  <legend>¿Tu comprobante es a título personal o para tu empresa? *</legend>
                  <div className="lx-invoice-options">
                    <button
                      type="button"
                      className={tipoComprobante === 'BOLETA' ? 'is-selected' : ''}
                      onClick={() => {
                        setTipoComprobante('BOLETA');
                        setNumeroDocumento('');
                      }}
                    >
                      <FileText size={16} />
                      <span>Personal (Boleta)</span>
                      <small>DNI 8 dígitos</small>
                    </button>
                    <button
                      type="button"
                      className={tipoComprobante === 'FACTURA' ? 'is-selected' : ''}
                      onClick={() => {
                        setTipoComprobante('FACTURA');
                        setNumeroDocumento('');
                      }}
                    >
                      <Building size={16} />
                      <span>Empresa (Factura)</span>
                      <small>RUC 11 dígitos deducible</small>
                    </button>
                  </div>
                </fieldset>

                <div className="lx-fields-grid lx-document-fields">
                  <div className={`lx-field ${tipoComprobante === 'BOLETA' ? 'lx-field-wide' : ''}`}>
                    <label htmlFor="checkout-doc">
                      {tipoComprobante === 'BOLETA' ? 'Número de DNI (8 dígitos) *' : 'Número de RUC (11 dígitos) *'}
                    </label>
                    <input
                      id="checkout-doc"
                      type="text"
                      required
                      inputMode="numeric"
                      value={numeroDocumento}
                      onChange={handleDocumentoChange}
                      placeholder={tipoComprobante === 'BOLETA' ? 'Ej. 45892147' : 'Ej. 20609812451'}
                    />
                  </div>

                  {tipoComprobante === 'FACTURA' && (
                    <div className="lx-field">
                      <label htmlFor="checkout-company">
                        Razón Social de la Empresa *
                      </label>
                      <input
                        id="checkout-company"
                        type="text"
                        required
                        value={razonSocial}
                        onChange={(e) => setRazonSocial(e.target.value)}
                        placeholder="Ej. Inversiones Tecnológicas del Perú S.A.C."
                      />
                    </div>
                  )}
                </div>
              </section>

              {/* Sección 2: Métodos de Pago */}
              <section className="lx-checkout-section" aria-labelledby="checkout-payment-heading">
                <div className="lx-section-heading">
                  <div>
                    <h3 id="checkout-payment-heading">2. Elige tu método de pago preferido</h3>
                    <p>Selecciona cómo completar tu matrícula con respaldo oficial.</p>
                  </div>
                </div>

                <div className="lx-payment-options" role="group" aria-label="Métodos de pago">
                  <button
                    type="button"
                    aria-pressed={paymentMethod === 'IZIPAY'}
                    className={paymentMethod === 'IZIPAY' ? 'is-selected' : ''}
                    onClick={() => setPaymentMethod('IZIPAY')}
                  >
                    <CreditCard size={18} />
                    <span>Tarjeta Online</span>
                    <small>izipay Online</small>
                  </button>

                  <button
                    type="button"
                    aria-pressed={paymentMethod === 'YAPE_PLIN'}
                    className={paymentMethod === 'YAPE_PLIN' ? 'is-selected' : ''}
                    onClick={() => setPaymentMethod('YAPE_PLIN')}
                  >
                    <Smartphone size={18} />
                    <span>Yape / Plin</span>
                    <small>Pago móvil</small>
                  </button>

                  <button
                    type="button"
                    aria-pressed={paymentMethod === 'TRANSFERENCIA'}
                    className={paymentMethod === 'TRANSFERENCIA' ? 'is-selected' : ''}
                    onClick={() => setPaymentMethod('TRANSFERENCIA')}
                  >
                    <Building2 size={18} />
                    <span>Transferencia Bancaria</span>
                    <small>BCP e Interbank</small>
                  </button>
                </div>

                {/* Panel IZIPAY */}
                {paymentMethod === 'IZIPAY' && (
                  <div className="lx-method-panel" key="card">
                    {izipayData ? (
                      <div>
                        <div className="lx-method-intro">
                          <div>
                            <h4>Pasarela Oficial izipay Online</h4>
                            <p>Ingresa los datos de tu tarjeta en el formulario seguro.</p>
                          </div>
                          <span className="lx-secure-mark">Conexión Cifrada SSL 3D Secure</span>
                        </div>

                        <div style={{ marginTop: '12px', display: 'grid', gap: '6px', fontSize: '11px' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                            <span style={{ color: '#9eb0bc' }}>Código de Orden:</span>
                            <strong style={{ color: '#53cbe5', fontFamily: 'monospace' }}>{izipayData.codigoOrden}</strong>
                          </div>
                          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                            <span style={{ color: '#9eb0bc' }}>Total a Pagar:</span>
                            <strong style={{ color: '#ffffff' }}>{priceCurrency === 'PEN' ? 'S/' : '$'} {priceAmount} {priceCurrency}</strong>
                          </div>
                        </div>

                        {izipaySdkError && (
                          <div className="lx-checkout-error" style={{ marginTop: '12px' }}>
                            <AlertCircle size={18} />
                            <div style={{ flex: 1 }}>
                              <p style={{ margin: 0 }}>{izipaySdkError}</p>
                              <button
                                type="button"
                                onClick={initKryptonSdk}
                                className="lx-checkout-secondary-action"
                                style={{ marginTop: '8px', minHeight: '32px', fontSize: '11px', width: 'auto', padding: '0 12px' }}
                              >
                                Reintentar carga de pasarela
                              </button>
                            </div>
                          </div>
                        )}

                        {izipayVerifying ? (
                          <div style={{ textAlign: 'center', padding: '24px 12px' }}>
                            <LoaderCircle size={32} className="lx-spinner" style={{ margin: '0 auto 8px', color: '#53cbe5' }} />
                            <h4 style={{ color: '#ffffff', fontSize: '14px' }}>Verificando transacción de forma segura...</h4>
                            <p style={{ color: '#9eb0bc', fontSize: '11px', margin: '4px 0 0' }}>Por favor espere mientras confirmamos la operación con el servidor.</p>
                          </div>
                        ) : (
                          <div style={{ margin: '14px 0', padding: '14px', background: '#0b1622', border: '1px dashed #304858', borderRadius: '8px', textAlign: 'center' }}>
                            <div className="kr-embedded" kr-form-token={izipayData.formToken}>
                              {izipaySdkLoading && (
                                <div style={{ padding: '20px 0' }}>
                                  <LoaderCircle size={28} className="lx-spinner" style={{ margin: '0 auto 8px', color: '#53cbe5' }} />
                                  <p style={{ fontSize: '12px', color: '#d1e2ec' }}>Cargando pasarela segura izipay Online...</p>
                                </div>
                              )}
                              {izipayFormReady && !izipaySdkError && !izipaySdkLoading && (
                                <div style={{ padding: '8px 0', color: '#d1e2ec' }}>
                                  <p style={{ fontSize: '12px', fontWeight: 'bold' }}>Formulario inteligente de pago preparado</p>
                                  <p style={{ fontSize: '10px', color: '#8da2af', maxWidth: '380px', margin: '4px auto 0' }}>
                                    Token seguro emitido por el servidor. La transacción se valida directamente con la pasarela mediante notificación segura IPN.
                                  </p>
                                </div>
                              )}
                              <div className="kr-pan my-2"></div>
                              <div className="kr-expiry my-2"></div>
                              <div className="kr-security-code my-2"></div>
                              {izipayFormReady && !izipaySdkError && !izipaySdkLoading && (
                                <button className="kr-payment-button w-full mt-3">
                                  Pagar {priceCurrency === 'PEN' ? 'S/' : '$'} {priceAmount} {priceCurrency}
                                </button>
                              )}
                              <div className="kr-form-error text-xs text-rose-400 mt-2"></div>
                            </div>
                          </div>
                        )}

                        <p style={{ fontSize: '11px', color: '#8da2af', margin: '10px 0' }}>
                          Al completar el pago en el formulario seguro, el backend registrará el cobro y procesará la matrícula en el Campus Virtual.
                        </p>

                        <button
                          type="button"
                          onClick={handleStartNewOrder}
                          className="lx-checkout-secondary-action"
                        >
                          Volver a opciones de pago
                        </button>
                      </div>
                    ) : (
                      <div>
                        <div className="lx-method-intro">
                          <div>
                            <h4>Pago seguro con tarjeta</h4>
                            <p>Ingresa los datos en la pasarela protegida de izipay Online.</p>
                          </div>
                          <span className="lx-secure-mark">256-bit SSL · 3D Secure</span>
                        </div>

                        <div className="lx-brand-row" aria-label="Tarjetas aceptadas">
                          <img src={visaLogo} alt="Visa" />
                          <img src={mastercardLogo} alt="Mastercard" />
                          <img src={amexLogo} alt="American Express" />
                          <img src={dinersLogo} alt="Diners Club" />
                        </div>

                        <p className="lx-payment-note" style={{ marginBottom: '14px' }}>
                          Tu pago se procesa de forma segura con liquidación bancaria y validación 3D Secure, aprovisionando tus accesos una vez recibida la confirmación IPN de la pasarela.
                        </p>

                        <button
                          type="button"
                          onClick={handleSubmitIzipay}
                          disabled={isSubmitting}
                          className="lx-checkout-primary-action"
                        >
                          {isSubmitting ? (
                            <>
                              <LoaderCircle size={17} className="lx-spinner" />
                              <span>Conectando con izipay Online...</span>
                            </>
                          ) : (
                            <>
                              <Lock size={16} />
                              <span>Pagar con izipay Online • {priceCurrency === 'PEN' ? 'S/' : '$'} {priceAmount} {priceCurrency}</span>
                              <ArrowRight size={16} />
                            </>
                          )}
                        </button>
                      </div>
                    )}
                  </div>
                )}

                {/* Panel YAPE_PLIN */}
                {paymentMethod === 'YAPE_PLIN' && (
                  <div className="lx-method-panel" key="wallet">
                    <div className="lx-method-intro">
                      <div>
                        <h4>Yape o Plin</h4>
                        <p>Transfiere desde tu billetera móvil y registra tu comprobante.</p>
                      </div>
                      <img className="lx-yape-logo" src={yapeLogo} alt="Yape" />
                    </div>

                    <div className="lx-account-row">
                      <div>
                        <small>Número de celular oficial</small>
                        <strong>941 238 905</strong>
                        <small>Titular: LYSANDRI S.A.C. · RUC: 20609812451</small>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleCopy('941238905', 'yape-num')}
                        aria-label="Copiar número de pago"
                      >
                        {copiedKey === 'yape-num' ? <Check size={16} /> : <Copy size={16} />}
                        <span>{copiedKey === 'yape-num' ? 'Copiado' : 'Copiar'}</span>
                      </button>
                    </div>

                    <p className="lx-payment-note">
                      Monto a abonar: <strong>{priceCurrency === 'PEN' ? 'S/' : '$'} {priceAmount} {priceCurrency}</strong>
                    </p>

                    <p style={{ fontSize: '11px', color: '#8da2af', margin: '10px 0 14px' }}>
                      Al presionar el botón, el backend registrará tu orden oficial en estado <strong style={{ color: '#fbbf24' }}>PENDIENTE_PAGO</strong> y te asignará un código único para que adjuntes tu voucher. La activación en Moodle se realizará tras la verificación administrativa.
                    </p>

                    <button
                      type="button"
                      disabled={isSubmitting}
                      onClick={() => handleManualOrderRegistration('YAPE', 'Yape / Plin')}
                      className="lx-checkout-primary-action"
                    >
                      {isSubmitting ? (
                        <>
                          <LoaderCircle size={17} className="lx-spinner" />
                          <span>Registrando orden en el servidor...</span>
                        </>
                      ) : (
                        <>
                          <Smartphone size={16} />
                          <span>Registrar Orden y Continuar con Voucher</span>
                          <ArrowRight size={16} />
                        </>
                      )}
                    </button>
                  </div>
                )}

                {/* Panel TRANSFERENCIA */}
                {paymentMethod === 'TRANSFERENCIA' && (
                  <div className="lx-method-panel lx-bank-panel" key="bank">
                    <div className="lx-method-intro">
                      <div>
                        <h4>Transferencia Bancaria Oficial</h4>
                        <p>Titular: <strong>LYSANDRI S.A.C.</strong> · RUC: <strong>20609812451</strong></p>
                      </div>
                      <span className="lx-secure-mark font-mono">
                        {priceCurrency === 'PEN' ? 'S/' : '$'} {priceAmount} {priceCurrency}
                      </span>
                    </div>

                    {cargandoCuentas ? (
                      <div style={{ textAlign: 'center', padding: '20px 0', fontSize: '11px', color: '#9eb0bc' }}>
                        <LoaderCircle size={20} className="lx-spinner" style={{ margin: '0 auto 6px', color: '#53cbe5' }} />
                        <span>Consultando cuentas bancarias oficiales autorizadas...</span>
                      </div>
                    ) : cuentasActivas.length > 0 ? (
                      <div className="lx-bank-list">
                        {cuentasActivas.map((cta) => {
                          const isBcp = (cta.banco || '').toUpperCase().includes('BCP');
                          const isIbk = (cta.banco || '').toUpperCase().includes('INTERBANK');
                          const logo = isBcp ? bcpLogo : isIbk ? interbankLogo : null;
                          return (
                            <article className="lx-bank-card" key={cta.id}>
                              {logo ? (
                                <img src={logo} alt={cta.banco} />
                              ) : (
                                <div style={{ width: '60px', height: '32px', display: 'grid', placeItems: 'center', background: '#1a2f42', borderRadius: '4px' }}>
                                  <Building2 size={18} style={{ color: '#53cbe5' }} />
                                </div>
                              )}
                              <div className="lx-bank-details">
                                <strong className="font-bold text-xs">{cta.banco}</strong>
                                <small>{cta.tipoCuenta} · {cta.moneda}</small>
                                {cta.numeroCuenta && (
                                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '2px' }}>
                                    <strong style={{ fontSize: '11px', color: '#f1f6f8' }}>{cta.numeroCuenta}</strong>
                                    <button
                                      type="button"
                                      onClick={() => handleCopy(cta.numeroCuenta!, `${cta.id}-num`)}
                                      aria-label={`Copiar cuenta ${cta.banco}`}
                                    >
                                      {copiedKey === `${cta.id}-num` ? <Check size={12} /> : <Copy size={12} />}
                                    </button>
                                  </div>
                                )}
                                {cta.cci && (
                                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '9px', color: '#8da2af' }}>
                                    <span>CCI {cta.cci}</span>
                                    <button
                                      type="button"
                                      onClick={() => handleCopy(cta.cci!, `${cta.id}-cci`)}
                                      aria-label={`Copiar CCI ${cta.banco}`}
                                    >
                                      {copiedKey === `${cta.id}-cci` ? <Check size={12} /> : <Copy size={12} />}
                                    </button>
                                  </div>
                                )}
                              </div>
                            </article>
                          );
                        })}
                      </div>
                    ) : (
                      <div style={{ padding: '12px', background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.3)', borderRadius: '8px', color: '#fef3c7', fontSize: '11px', marginTop: '12px' }}>
                        <strong>Cuentas Corrientes en Configuración Oficial</strong>
                        <p style={{ margin: '4px 0 0', lineHeight: 1.4 }}>
                          Las cuentas bancarias corporativas para transferencias directas se encuentran en proceso de formalización técnica en este entorno. Puedes registrar tu orden para recibir la orientación de abono verificada por parte de nuestra asesora académica, o pagar con tarjeta mediante izipay Online.
                        </p>
                      </div>
                    )}

                    <p className="lx-payment-note">
                      Importe a transferir: <strong>{priceCurrency === 'PEN' ? 'S/' : '$'} {priceAmount} {priceCurrency}</strong>
                    </p>

                    <p style={{ fontSize: '11px', color: '#8da2af', margin: '10px 0 14px' }}>
                      Al presionar el botón, el backend registrará formalmente tu orden de compra con método <strong style={{ color: '#53cbe5', fontFamily: 'monospace' }}>TRANSFERENCIA</strong> en estado <strong style={{ color: '#fbbf24', fontFamily: 'monospace' }}>PENDIENTE_PAGO</strong>. Una vez que tu abono sea verificado por personal administrativo autorizado, se activarán tus accesos en el Campus Virtual.
                    </p>

                    <button
                      type="button"
                      disabled={isSubmitting}
                      onClick={() => handleManualOrderRegistration('TRANSFERENCIA', 'Transferencia Bancaria')}
                      className="lx-checkout-primary-action"
                    >
                      {isSubmitting ? (
                        <>
                          <LoaderCircle size={17} className="lx-spinner" />
                          <span>Registrando orden en el servidor...</span>
                        </>
                      ) : (
                        <>
                          <Building2 size={16} />
                          <span>Registrar Orden y Coordinar Transferencia</span>
                          <ArrowRight size={16} />
                        </>
                      )}
                    </button>
                  </div>
                )}
              </section>
            </>
          )}

          {/* Footer con asistencia directa y nota */}
          <div style={{ marginTop: '16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 14px', background: '#0e1a26', border: '1px solid #253a4a', borderRadius: '8px', fontSize: '11px', flexWrap: 'wrap', gap: '8px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <HeartHandshake size={18} style={{ color: '#34d399' }} />
              <div>
                <strong style={{ color: '#ffffff' }}>¿Deseas atención personalizada o pago corporativo?</strong>
                <p style={{ margin: 0, color: '#8da2af', fontSize: '10px' }}>Un asesor académico de Lysandri te asiste en tiempo real por WhatsApp.</p>
              </div>
            </div>
            <a
              href="https://wa.me/51941238905?text=Hola%20Lysandri,%20deseo%20asistencia%20con%20mi%20matr%C3%ADcula%20y%20m%C3%A9todos%20de%20pago"
              target="_blank"
              rel="noopener noreferrer"
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '6px 12px', background: '#123d36', border: '1px solid #287c68', borderRadius: '6px', color: '#e7fff6', fontSize: '10px', fontWeight: 'bold', textDecoration: 'none' }}
            >
              <MessageCircle size={14} />
              <span>Chatear por WhatsApp</span>
            </a>
          </div>

          <p className="lx-checkout-footnote">
            Al continuar, confirmas que los datos del comprobante son correctos.
          </p>
        </div>
      </section>
    </div>
  );
};

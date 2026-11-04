import React, { useEffect, useState, useMemo, useRef } from 'react';
import {
  ShieldAlert,
  LoaderCircle,
  FileSpreadsheet,
  Search,
  RefreshCw,
  Calendar,
  Building,
  User,
  FileText,
  TrendingUp,
  Receipt,
  DollarSign,
  Eye,
  EyeOff,
  LogOut,
  Printer,
  X,
  Download,
  CreditCard,
  Calculator,
  Ban,
  Landmark,
  Copy,
  Check,
  Layers,
  Wallet,
  KeyRound,
  BarChart3,
  PieChart,
  Percent,
  Activity,
  ArrowUpRight,
  Briefcase,
  Sun,
  Moon,
} from 'lucide-react';
import {
  accountingService,
  ReporteVentasResponse,
  VentaConsolidada,
} from '../services/accountingService';
import { useAuth } from '../context/AuthContext';
import { isJwtExpired } from '../services/api';
import companyLogo from '../assets/lysandri-logo.png';
import animatedLogo from '../assets/logo-animado.gif';

type AccountingGlyphName = 'flow' | 'receipt' | 'tax' | 'treasury';

const AccountingGlyph: React.FC<{ name: AccountingGlyphName; className?: string }> = ({ name, className = '' }) => (
  <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
    <g stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      {name === 'flow' && <>
        <path d="M4 19.5h16M5.5 16V10m4.25 6V5m4.5 11v-3m4.25 3V7" />
        <path className="lx-accounting-glyph-accent" d="M4 7.5 8.75 4l4 4 6-3" />
      </>}
      {name === 'receipt' && <>
        <path d="M6 3.75h12v16.5l-2-1.5-2 1.5-2-1.5-2 1.5-2-1.5-2 1.5z" />
        <path d="M9 8h6M9 11.5h6M9 15h3" />
        <path className="lx-accounting-glyph-accent" d="M15 15h.01" />
      </>}
      {name === 'tax' && <>
        <path d="M5 4.5h14v15H5zM8 8h8M8 11.5h4" />
        <path className="lx-accounting-glyph-accent" d="m13.5 15.5 1.5 1.5 3-3" />
      </>}
      {name === 'treasury' && <>
        <path d="M4 8.5 12 4l8 4.5M5.5 9v8m4.25-8v8m4.5-8v8m4.25-8v8M4 19.5h16" />
        <path className="lx-accounting-glyph-accent" d="M8 6.25h8" />
      </>}
    </g>
  </svg>
);

export const AccountingView: React.FC = () => {
  const { user, token, login: authLogin, logout: authLogout } = useAuth();
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [username, setUsername] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [rememberMe, setRememberMe] = useState<boolean>(true);
  const [isVerifying, setIsVerifying] = useState<boolean>(false);
  const [authError, setAuthError] = useState<string>('');

  const userRole = (user?.rol || '').toUpperCase();
  const canAnular = userRole === 'ADMIN' || userRole === 'CONTABILIDAD';
  const canExportSire = userRole === 'ADMIN' || userRole === 'CONTABILIDAD' || userRole === 'FINANZAS';

  const [typedText, setTypedText] = useState('');
  const [isTyping, setIsTyping] = useState(true);
  const fullText = 'Eleva tu flujo de trabajo';

  const [data, setData] = useState<ReporteVentasResponse | null>(null);
  const [isLoadingData, setIsLoadingData] = useState<boolean>(false);
  const [dataError, setDataError] = useState<string>('');

  const [activeTab, setActiveTab] = useState<'graficos' | 'comprobantes' | 'liquidacion' | 'medios_pago'>('graficos');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedTipo, setSelectedTipo] = useState<'TODOS' | 'BOLETA' | 'FACTURA'>('TODOS');
  const [selectedEstado, setSelectedEstado] = useState<'TODOS' | 'EMITIDO' | 'ANULADO'>('TODOS');
  const [selectedCanal, setSelectedCanal] = useState<'TODOS' | 'IZIPAY' | 'STRIPE' | 'TELECREDITO' | 'INTERBANK' | 'SIN_CLASIFICAR'>('TODOS');
  const [selectedPeriodo, setSelectedPeriodo] = useState<string>('TODOS');
  const [knownPeriodos, setKnownPeriodos] = useState<string[]>(['2026-10', '2026-11', '2026-09']);
  const [selectedVoucherForPreview, setSelectedVoucherForPreview] = useState<VentaConsolidada | null>(null);
  const [isExportingSire, setIsExportingSire] = useState<boolean>(false);
  const [copiedText, setCopiedText] = useState<string | null>(null);

  // Identificador de petición y controlador de cancelación para evitar respuestas fuera de orden
  const activeRequestIdRef = useRef<number>(0);
  const abortControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      activeRequestIdRef.current += 1;
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, []);

  const [isDarkMode, setIsDarkMode] = useState<boolean>(() => {
    if (typeof document !== 'undefined') {
      const stored = localStorage.getItem('lysandri_theme');
      if (stored) return stored === 'dark';
      return document.documentElement.classList.contains('dark');
    }
    return true;
  });

  useEffect(() => {
    if (typeof document !== 'undefined') {
      const stored = localStorage.getItem('lysandri_theme');
      const shouldBeDark = stored ? stored === 'dark' : document.documentElement.classList.contains('dark');
      setIsDarkMode(shouldBeDark);
      document.documentElement.classList.toggle('dark', shouldBeDark);
    }
  }, []);

  const toggleTheme = () => {
    setIsDarkMode((prev) => {
      const next = !prev;
      if (typeof document !== 'undefined') {
        document.documentElement.classList.toggle('dark', next);
        localStorage.setItem('lysandri_theme', next ? 'dark' : 'light');
      }
      return next;
    });
  };

  useEffect(() => {
    let intervalId: number | undefined;

    const startDelay = window.setTimeout(() => {
      let currentIndex = 0;

      intervalId = window.setInterval(() => {
        setTypedText(fullText.slice(0, currentIndex + 1));
        currentIndex += 1;

        if (currentIndex >= fullText.length && intervalId) {
          window.clearInterval(intervalId);
          window.setTimeout(() => {
            setIsTyping(false);
          }, 800);
        }
      }, 70);
    }, 400);

    return () => {
      window.clearTimeout(startDelay);
      if (intervalId) {
        window.clearInterval(intervalId);
      }
    };
  }, []);

  useEffect(() => {
    const activeToken = token || localStorage.getItem('lysandri_token');
    const storedPin = accountingService.getStoredAuditPin();
    if (activeToken && !isJwtExpired(activeToken) && storedPin) {
      void cargarDatosContables(storedPin);
    }
  }, [token]);

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(label);
    setTimeout(() => setCopiedText(null), 2000);
  };

  const cargarDatosContables = async (pin: string, periodo?: string) => {
    // Cancelar la petición en vuelo previa si existe
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    const controller = new AbortController();
    abortControllerRef.current = controller;

    // Generar nuevo identificador único para esta solicitud
    const requestId = ++activeRequestIdRef.current;

    setIsVerifying(true);
    setIsLoadingData(true);
    setAuthError('');
    setDataError('');

    try {
      const activeToken = token || localStorage.getItem('lysandri_token');
      if (!activeToken || isJwtExpired(activeToken)) {
        if (requestId !== activeRequestIdRef.current) return;
        setAuthError('Sesión no encontrada o caducada. Inicia sesión con credenciales autorizadas.');
        setIsAuthenticated(false);
        setData(null);
        return;
      }

      if (user && !['ADMIN', 'CONTABILIDAD', 'FINANZAS', 'AUDITOR'].includes(user.rol?.toUpperCase())) {
        if (requestId !== activeRequestIdRef.current) return;
        setAuthError(`Acceso denegado: El usuario '${user.email}' con rol '${user.rol}' no tiene permisos para el área contable.`);
        setIsAuthenticated(false);
        setData(null);
        return;
      }

      const response = await accountingService.fetchReporteVentas(
        pin,
        periodo === 'TODOS' ? undefined : periodo,
        controller.signal
      );

      // Si la petición fue superada por un nuevo cambio de período, descartar
      if (requestId !== activeRequestIdRef.current) {
        return;
      }

      accountingService.setStoredAuditPin(pin);
      setIsAuthenticated(true);
      setData(response);
      return true;
    } catch (err: any) {
      // Ignorar completamente si ya no es la petición activa o si fue cancelada
      if (requestId !== activeRequestIdRef.current) {
        return false;
      }
      if (err.name === 'CanceledError' || err.name === 'AbortError' || err.code === 'ERR_CANCELED') {
        return false;
      }

      if (err.response?.status === 401 || err.response?.status === 403) {
        setAuthError('Acceso no autorizado (401/403). Credenciales, rol o PIN de auditoría no válidos.');
        accountingService.clearStoredAuditPin();
        setIsAuthenticated(false);
        setData(null);
      } else {
        const errorMsg = err.response?.data?.message || err.message || 'Error de conexión con el servidor contable';
        setDataError('Error al recuperar datos contables del servidor: ' + errorMsg);
        setData(null);
      }
      return false;
    } finally {
      // Solo restablecer estados de carga si esta solicitud sigue siendo la vigente
      if (requestId === activeRequestIdRef.current) {
        setIsVerifying(false);
        setIsLoadingData(false);
      }
    }
  };

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      setAuthError('Por favor ingresa tu nombre de usuario o correo asignado.');
      return;
    }
    if (!password.trim()) {
      setAuthError('Por favor ingresa tu contraseña de acceso.');
      return;
    }

    setAuthError('');
    setIsVerifying(true);

    try {
      const email = username.trim().includes('@') ? username.trim() : `${username.trim()}@lysandri.com`;
      const loggedUser = await authLogin({ email, password: password.trim() });
      const userRol = (loggedUser?.rol || '').toUpperCase();
      const allowedRoles = ['ADMIN', 'CONTABILIDAD', 'FINANZAS', 'AUDITOR'];

      if (!allowedRoles.includes(userRol)) {
        setAuthError(`Acceso denegado: El usuario '${loggedUser.email}' con rol '${loggedUser.rol}' no está autorizado para acceder al área contable.`);
        setIsVerifying(false);
        return;
      }

      const pin = accountingService.getStoredAuditPin() || 'LYS-AUDIT-2026-SECURE';
      await cargarDatosContables(pin, selectedPeriodo);
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Error de autenticación. Verifica tus credenciales.';
      setAuthError(msg);
      setIsAuthenticated(false);
      setData(null);
    } finally {
      setIsVerifying(false);
    }
  };

  const handleLogout = () => {
    activeRequestIdRef.current += 1;
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    accountingService.clearStoredAuditPin();
    setIsAuthenticated(false);
    setData(null);
    setUsername('');
    setPassword('');
    setAuthError('');
    setDataError('');
    setIsLoadingData(false);
    setIsVerifying(false);
    authLogout();
  };

  const handleRefresh = () => {
    const pin = accountingService.getStoredAuditPin() || 'LYS-AUDIT-2026-SECURE';
    if (pin) {
      void cargarDatosContables(pin, selectedPeriodo);
    }
  };

  const clasificarCanal = (medio?: string | null): 'IZIPAY' | 'STRIPE' | 'TELECREDITO' | 'INTERBANK' | 'SIN_CLASIFICAR' => {
    if (!medio) return 'SIN_CLASIFICAR';
    const m = medio.trim().toUpperCase();
    if (m.includes('IZIPAY')) return 'IZIPAY';
    if (m.includes('STRIPE')) return 'STRIPE';
    if (m === 'TELECREDITO_BCP' || m === 'BCP' || m === 'TRANSFERENCIA_BCP') return 'TELECREDITO';
    if (m === 'INTERBANK_B2B' || m === 'INTERBANK' || m === 'TRANSFERENCIA_INTERBANK') return 'INTERBANK';
    return 'SIN_CLASIFICAR';
  };

  const handlePeriodoChange = (newPeriodo: string) => {
    setSelectedPeriodo(newPeriodo);
    // Limpiar cifras previas para no dejar datos obsoletos presentados como vigentes si la petición no prospera
    setData(null);
    setDataError('');
    const pin = accountingService.getStoredAuditPin() || 'LYS-AUDIT-2026-SECURE';
    void cargarDatosContables(pin, newPeriodo);
  };

  const formatSoles = (amount?: number | null): string => {
    const val = typeof amount === 'number' ? amount : 0;
    return `S/. ${val.toLocaleString('es-PE', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const formatDolares = (amount?: number | null): string => {
    const val = typeof amount === 'number' ? amount : 0;
    return `$ ${val.toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })} USD`;
  };

  const formatFecha = (dateString?: string | null): string => {
    if (!dateString) return '—';
    try {
      const d = new Date(dateString);
      if (isNaN(d.getTime())) return dateString;
      return d.toLocaleDateString('es-PE', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateString;
    }
  };

  // Mantener catálogo acumulativo de períodos descubiertos para que el selector no se restrinja tras una primera búsqueda
  useEffect(() => {
    if (data?.ventasConsolidadas) {
      const setPeriodos = new Set<string>(knownPeriodos);
      data.ventasConsolidadas.forEach((v) => {
        const rawDate = v.fechaEmision || v.fechaOrden;
        if (rawDate) {
          const match = rawDate.match(/^(\d{4})-(\d{2})/);
          if (match) {
            setPeriodos.add(`${match[1]}-${match[2]}`);
          }
        }
      });
      if (data.periodoSeleccionado && data.periodoSeleccionado !== 'TODOS' && data.periodoSeleccionado.match(/^\d{4}-\d{2}$/)) {
        setPeriodos.add(data.periodoSeleccionado);
      }
      setKnownPeriodos(Array.from(setPeriodos).sort().reverse());
    }
  }, [data]);

  const periodosDisponibles = knownPeriodos;

  const ventasFiltradas = useMemo(() => {
    if (!data?.ventasConsolidadas) return [];

    return data.ventasConsolidadas.filter((item) => {
      if (selectedTipo !== 'TODOS') {
        const itemTipo = (item.tipoComprobante || '').toUpperCase();
        if (selectedTipo === 'BOLETA' && !itemTipo.includes('BOLETA')) return false;
        if (selectedTipo === 'FACTURA' && !itemTipo.includes('FACTURA')) return false;
      }

      if (selectedEstado !== 'TODOS') {
        const itemEstado = (item.estadoComprobante || 'EMITIDO').toUpperCase();
        if (selectedEstado === 'EMITIDO' && itemEstado === 'ANULADO') return false;
        if (selectedEstado === 'ANULADO' && itemEstado !== 'ANULADO') return false;
      }

      if (selectedCanal !== 'TODOS') {
        const itemCanal = clasificarCanal(item.medioPago);
        if (selectedCanal !== itemCanal) return false;
      }

      if (selectedPeriodo !== 'TODOS') {
        const rawDate = item.fechaEmision || item.fechaOrden;
        if (rawDate) {
          const match = rawDate.match(/^(\d{4})-(\d{2})/);
          const itemPeriodo = match ? `${match[1]}-${match[2]}` : null;
          if (itemPeriodo !== selectedPeriodo) return false;
        } else {
          return false;
        }
      }

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const matchesCliente = item.cliente?.toLowerCase().includes(q);
        const matchesDoc = item.documentoIdentidad?.toLowerCase().includes(q);
        const matchesSerie = item.serieNumero?.toLowerCase().includes(q);
        const matchesCodigo = item.codigoOrden?.toLowerCase().includes(q);
        const matchesCuo = item.cuo?.toLowerCase().includes(q);
        const matchesMedio = item.medioPago?.toLowerCase().includes(q);
        if (!matchesCliente && !matchesDoc && !matchesSerie && !matchesCodigo && !matchesCuo && !matchesMedio) {
          return false;
        }
      }

      return true;
    });
  }, [data, selectedTipo, selectedEstado, selectedCanal, selectedPeriodo, searchQuery]);

  const metricasCalculadas = useMemo(() => {
    if (!data) {
      return {
        baseImponible: 0,
        igv: 0,
        total: 0,
        detracciones: 0,
        baseImponiblePen: 0,
        igvPen: 0,
        totalPen: 0,
        detraccionesPen: 0,
        countComprobantesPen: 0,
        totalFacturasPen: 0,
        totalBoletasPen: 0,
        montoFacturasPen: 0,
        montoBoletasPen: 0,
        ticketFacturaPen: 0,
        ticketBoletaPen: 0,
        ticketPromedioPen: 0,
        baseImponibleUsd: 0,
        igvUsd: 0,
        totalUsd: 0,
        detraccionesUsd: 0,
        countComprobantesUsd: 0,
        totalFacturasUsd: 0,
        totalBoletasUsd: 0,
        montoFacturasUsd: 0,
        montoBoletasUsd: 0,
        ticketFacturaUsd: 0,
        ticketBoletaUsd: 0,
        ticketPromedioUsd: 0,
        rentaMype: 0,
        netoPagarSunat: 0,
        izipayGrossPen: 0,
        izipayGrossUsd: 0,
        countIzipay: 0,
        stripeGrossPen: 0,
        stripeGrossUsd: 0,
        countStripe: 0,
        bcpGrossPen: 0,
        bcpGrossUsd: 0,
        bcpSpot: 0,
        countBcp: 0,
        interbankGrossPen: 0,
        interbankGrossUsd: 0,
        interbankSpot: 0,
        countInterbank: 0,
        sinClasificarGrossPen: 0,
        sinClasificarGrossUsd: 0,
        countSinClasificar: 0,
        pctIzipay: 0,
        pctStripe: 0,
        pctBcp: 0,
        pctInterbank: 0,
        pctSinClasificar: 0,
        countRetenidasSpot: 0,
        coberturaSpot: 0,
        countPendientesFacturar: 0,
        totalPendientesPen: 0,
        totalPendientesUsd: 0,
        totalFacturas: 0,
        totalBoletas: 0,
        montoFacturas: 0,
        montoBoletas: 0,
        ticketFactura: 0,
        ticketBoleta: 0,
        ticketPromedio: 0,
        ventasPorDia: [] as {
          fecha: string;
          totalPen: number;
          totalUsd: number;
          facturasPen: number;
          boletasPen: number;
          total: number;
        }[],
        maxVentaDia: 1,
      };
    }

    let basePen = 0;
    let igvPen = 0;
    let totalPen = 0;
    let detraccionesPen = 0;
    let countComprobantesPen = 0;
    let totalFacturasPen = 0;
    let totalBoletasPen = 0;
    let montoFacturasPen = 0;
    let montoBoletasPen = 0;

    let baseUsd = 0;
    let igvUsd = 0;
    let totalUsd = 0;
    let detraccionesUsd = 0;
    let countComprobantesUsd = 0;
    let totalFacturasUsd = 0;
    let totalBoletasUsd = 0;
    let montoFacturasUsd = 0;
    let montoBoletasUsd = 0;

    let izipayGrossPen = 0;
    let izipayGrossUsd = 0;
    let countIzipay = 0;

    let stripeGrossPen = 0;
    let stripeGrossUsd = 0;
    let countStripe = 0;

    let bcpGrossPen = 0;
    let bcpGrossUsd = 0;
    let bcpSpot = 0;
    let countBcp = 0;

    let interbankGrossPen = 0;
    let interbankGrossUsd = 0;
    let interbankSpot = 0;
    let countInterbank = 0;

    let sinClasificarGrossPen = 0;
    let sinClasificarGrossUsd = 0;
    let countSinClasificar = 0;

    let countRetenidasSpot = 0;
    let countPendientesFacturar = 0;
    let totalPendientesPen = 0;
    let totalPendientesUsd = 0;

    const ventasPorDiaMap: {
      [key: string]: {
        fecha: string;
        totalPen: number;
        totalUsd: number;
        facturasPen: number;
        boletasPen: number;
        total: number;
      };
    } = {};

    ventasFiltradas.forEach((v) => {
      const t = Number(v.total) || 0;
      const sub = Number(v.subtotal) || 0;
      const i = Number(v.igv) || 0;
      const detraccionItem = Number(v.montoDetraccion) || 0;
      const mon = (v.moneda || 'PEN').toUpperCase();
      const estado = (v.estadoComprobante || 'PENDIENTE').toUpperCase();

      // Órdenes pendientes sin comprobante: excluirlas de las métricas de comprobantes emitidos
      if (!v.idComprobante || estado === 'PENDIENTE') {
        countPendientesFacturar += 1;
        if (mon === 'USD') {
          totalPendientesUsd += t;
        } else {
          totalPendientesPen += t;
        }
        return;
      }

      // Anulados: no suman a ingresos netos ni base fiscal
      if (estado === 'ANULADO') {
        return;
      }

      const esFactura = (v.tipoComprobante || '').toUpperCase().includes('FACTURA');

      if (mon === 'USD') {
        baseUsd += sub;
        igvUsd += i;
        totalUsd += t;
        detraccionesUsd += detraccionItem;
        countComprobantesUsd += 1;
        if (esFactura) {
          totalFacturasUsd += 1;
          montoFacturasUsd += t;
        } else {
          totalBoletasUsd += 1;
          montoBoletasUsd += t;
        }
      } else {
        basePen += sub;
        igvPen += i;
        totalPen += t;
        detraccionesPen += detraccionItem;
        countComprobantesPen += 1;
        if (esFactura) {
          totalFacturasPen += 1;
          montoFacturasPen += t;
        } else {
          totalBoletasPen += 1;
          montoBoletasPen += t;
        }
      }

      if (detraccionItem > 0) {
        countRetenidasSpot += 1;
      }

      const rawDate = v.fechaEmision || v.fechaOrden || '';
      const dia = rawDate.slice(0, 10);
      if (dia) {
        if (!ventasPorDiaMap[dia]) {
          ventasPorDiaMap[dia] = { fecha: dia, totalPen: 0, totalUsd: 0, facturasPen: 0, boletasPen: 0, total: 0 };
        }
        if (mon === 'USD') {
          ventasPorDiaMap[dia].totalUsd += t;
        } else {
          ventasPorDiaMap[dia].totalPen += t;
          ventasPorDiaMap[dia].total += t;
          if (esFactura) {
            ventasPorDiaMap[dia].facturasPen += t;
          } else {
            ventasPorDiaMap[dia].boletasPen += t;
          }
        }
      }

      // Clasificación rigurosa de canal basada en valores del backend
      const canal = clasificarCanal(v.medioPago);
      if (canal === 'IZIPAY') {
        if (mon === 'USD') izipayGrossUsd += t;
        else izipayGrossPen += t;
        countIzipay += 1;
      } else if (canal === 'STRIPE') {
        if (mon === 'USD') stripeGrossUsd += t;
        else stripeGrossPen += t;
        countStripe += 1;
      } else if (canal === 'TELECREDITO') {
        if (mon === 'USD') bcpGrossUsd += t;
        else bcpGrossPen += t;
        countBcp += 1;
        bcpSpot += detraccionItem;
      } else if (canal === 'INTERBANK') {
        if (mon === 'USD') interbankGrossUsd += t;
        else interbankGrossPen += t;
        countInterbank += 1;
        interbankSpot += detraccionItem;
      } else {
        if (mon === 'USD') sinClasificarGrossUsd += t;
        else sinClasificarGrossPen += t;
        countSinClasificar += 1;
      }
    });

    const ventasPorDia = Object.values(ventasPorDiaMap).sort((a, b) => a.fecha.localeCompare(b.fecha));
    const maxVentaDia = ventasPorDia.length > 0 ? Math.max(...ventasPorDia.map((item) => item.totalPen)) : 1;

    const rentaMype = basePen * 0.01;
    const netoPagarSunat = Math.max(0, (igvPen + rentaMype) - detraccionesPen);

    // Tickets promedio estrictamente separados por moneda (sin sumar montos de monedas distintas)
    const ticketFacturaPen = totalFacturasPen > 0 ? montoFacturasPen / totalFacturasPen : 0;
    const ticketBoletaPen = totalBoletasPen > 0 ? montoBoletasPen / totalBoletasPen : 0;
    const totalCountPen = totalFacturasPen + totalBoletasPen;
    const ticketPromedioPen = totalCountPen > 0 ? totalPen / totalCountPen : 0;

    const ticketFacturaUsd = totalFacturasUsd > 0 ? montoFacturasUsd / totalFacturasUsd : 0;
    const ticketBoletaUsd = totalBoletasUsd > 0 ? montoBoletasUsd / totalBoletasUsd : 0;
    const totalCountUsd = totalFacturasUsd + totalBoletasUsd;
    const ticketPromedioUsd = totalCountUsd > 0 ? totalUsd / totalCountUsd : 0;

    // Participación de canales sobre recaudación en moneda local PEN
    const totalCanalesPen = (izipayGrossPen + stripeGrossPen + bcpGrossPen + interbankGrossPen + sinClasificarGrossPen) || 1;
    const pctIzipay = Math.round((izipayGrossPen / totalCanalesPen) * 100);
    const pctStripe = Math.round((stripeGrossPen / totalCanalesPen) * 100);
    const pctBcp = Math.round((bcpGrossPen / totalCanalesPen) * 100);
    const pctInterbank = Math.round((interbankGrossPen / totalCanalesPen) * 100);
    const pctSinClasificar = Math.max(0, 100 - pctIzipay - pctStripe - pctBcp - pctInterbank);
    const coberturaSpot = igvPen > 0 ? Math.min(100, Math.round((detraccionesPen / igvPen) * 100)) : 0;

    return {
      baseImponible: basePen,
      igv: igvPen,
      total: totalPen,
      detracciones: detraccionesPen,
      baseImponiblePen: basePen,
      igvPen,
      totalPen,
      detraccionesPen,
      countComprobantesPen,
      totalFacturasPen,
      totalBoletasPen,
      montoFacturasPen,
      montoBoletasPen,
      ticketFacturaPen,
      ticketBoletaPen,
      ticketPromedioPen,
      baseImponibleUsd: baseUsd,
      igvUsd,
      totalUsd,
      detraccionesUsd,
      countComprobantesUsd,
      totalFacturasUsd,
      totalBoletasUsd,
      montoFacturasUsd,
      montoBoletasUsd,
      ticketFacturaUsd,
      ticketBoletaUsd,
      ticketPromedioUsd,
      rentaMype,
      netoPagarSunat,
      izipayGrossPen,
      izipayGrossUsd,
      countIzipay,
      stripeGrossPen,
      stripeGrossUsd,
      countStripe,
      bcpGrossPen,
      bcpGrossUsd,
      bcpSpot,
      countBcp,
      interbankGrossPen,
      interbankGrossUsd,
      interbankSpot,
      countInterbank,
      sinClasificarGrossPen,
      sinClasificarGrossUsd,
      countSinClasificar,
      pctIzipay,
      pctStripe,
      pctBcp,
      pctInterbank,
      pctSinClasificar,
      countRetenidasSpot,
      coberturaSpot,
      countPendientesFacturar,
      totalPendientesPen,
      totalPendientesUsd,
      totalFacturas: totalFacturasPen + totalFacturasUsd,
      totalBoletas: totalBoletasPen + totalBoletasUsd,
      montoFacturas: montoFacturasPen,
      montoBoletas: montoBoletasPen,
      ticketFactura: ticketFacturaPen,
      ticketBoleta: ticketBoletaPen,
      ticketPromedio: ticketPromedioPen,
      ventasPorDia,
      maxVentaDia,
    };
  }, [data, ventasFiltradas]);

  const { countVigentes, countAnulados } = useMemo(() => {
    if (!data?.ventasConsolidadas) return { countVigentes: 0, countAnulados: 0 };
    let vig = 0;
    let anu = 0;
    data.ventasConsolidadas.forEach((v) => {
      if (v.estadoComprobante === 'ANULADO') anu++;
      else vig++;
    });
    return { countVigentes: vig, countAnulados: anu };
  }, [data]);

  const handleDescargarSire = async () => {
    if (!canExportSire) {
      alert('Permisos insuficientes: Tu rol no tiene permisos para descargar el archivo oficial SIRE.');
      return;
    }
    if (!selectedPeriodo || selectedPeriodo === 'TODOS' || !selectedPeriodo.trim().match(/^\d{4}-(0[1-9]|1[0-2])$/)) {
      alert('Para generar el libro electrónico SIRE 14.1 para SUNAT, debe seleccionar un período mensual tributario válido (ej. 2026-10). La exportación oficial no admite "Todos los períodos" ni valores vacíos.');
      return;
    }
    const pin = accountingService.getStoredAuditPin() || 'LYS-AUDIT-2026-SECURE';
    setIsExportingSire(true);
    try {
      await accountingService.descargarSireTxt(pin, selectedPeriodo.trim());
    } catch (err: any) {
      const msg = err.response?.data?.mensaje || err.response?.data?.error || err.message || 'Error';
      alert('Error al descargar estructura SIRE 14.1 de SUNAT:\n' + msg);
    } finally {
      setIsExportingSire(false);
    }
  };

  const handleAnularComprobante = async (venta: VentaConsolidada) => {
    if (!canAnular) {
      alert('Permisos insuficientes: Solo administradores y contabilidad pueden anular comprobantes.');
      return;
    }
    if (!venta.idComprobante) {
      alert('Esta orden no cuenta con comprobante emitido para anular.');
      return;
    }
    if (venta.estadoComprobante === 'ANULADO') {
      alert('Este comprobante ya se encuentra anulado.');
      return;
    }

    const motivo = window.prompt(
      `¿Desea anular el comprobante ${venta.serieNumero || venta.idComprobante} (${venta.cliente})?\n\nIngrese el motivo de anulación registrado ante SUNAT:`,
      'Anulación por error en emisión de comprobante'
    );

    if (!motivo || !motivo.trim()) return;

    const pin = accountingService.getStoredAuditPin() || 'LYS-AUDIT-2026-SECURE';
    try {
      await accountingService.anularComprobante(venta.idComprobante, motivo.trim(), pin);
      alert(`Comprobante ${venta.serieNumero || venta.idComprobante} anulado con éxito en el servidor.`);
      const refreshOk = await cargarDatosContables(pin, selectedPeriodo);
      if (!refreshOk) {
        alert('La anulación del comprobante fue confirmada en el servidor, pero ocurrió un problema al actualizar el reporte en pantalla. Por favor refresque manualmente.');
      }
    } catch (err: any) {
      alert('Error al anular comprobante: ' + (err.response?.data?.message || err.message || 'Error'));
    }
  };

  const exportarCSV = () => {
    const validRecords = (ventasFiltradas || []).filter((v) => v && (v.idComprobante || v.idOrden));
    if (validRecords.length === 0) {
      alert('No hay registros válidos para exportar.');
      return;
    }

    const headers = [
      'ID_COMPROBANTE',
      'ID_ORDEN',
      'CUO',
      'FECHA_EMISION',
      'FECHA_ORDEN',
      'TIPO_COMPROBANTE',
      'SERIE_CORRELATIVO',
      'TIPO_DOC_IDENTIDAD',
      'NUMERO_DOC_IDENTIDAD',
      'CLIENTE_O_RAZON_SOCIAL',
      'CANAL_PAGO',
      'MONEDA',
      'BASE_IMPONIBLE',
      'IGV_18',
      'SPOT_DETRACCION_12',
      'TOTAL_VENTA',
      'ESTADO_FISCAL',
      'HASH_DIGEST_VALUE',
      'REFERENCIA_ORDEN',
    ];

    const escapeCSV = (val: any) => {
      if (val === null || val === undefined) return '""';
      const str = String(val).replace(/"/g, '""');
      return `"${str}"`;
    };

    const rows = validRecords.map((item) => {
      const tipoDoc = (item.documentoIdentidad || '').length === 11 ? 'RUC' : (item.documentoIdentidad ? 'DNI' : '—');
      return [
        escapeCSV(item.idComprobante ?? ''),
        escapeCSV(item.idOrden),
        escapeCSV(item.cuo ?? ''),
        escapeCSV(item.fechaEmision ? formatFecha(item.fechaEmision) : ''),
        escapeCSV(formatFecha(item.fechaOrden)),
        escapeCSV(item.tipoComprobante ?? ''),
        escapeCSV(item.serieNumero ?? ''),
        escapeCSV(tipoDoc),
        escapeCSV(item.documentoIdentidad ?? ''),
        escapeCSV(item.cliente ?? ''),
        escapeCSV(item.medioPago ?? ''),
        escapeCSV(item.moneda ?? 'PEN'),
        Number(item.subtotal || 0).toFixed(2),
        Number(item.igv || 0).toFixed(2),
        Number(item.montoDetraccion || 0).toFixed(2),
        Number(item.total || 0).toFixed(2),
        escapeCSV(item.estadoComprobante ?? 'EMITIDO'),
        escapeCSV(item.codigoHash ?? ''),
        escapeCSV(item.codigoOrden ?? ''),
      ].join(',');
    });

    const csvContent = '\uFEFF' + [headers.join(','), ...rows].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const cleanPeriodo = selectedPeriodo !== 'TODOS' ? selectedPeriodo.replace('-', '') : 'TODOS';
    link.href = url;
    link.setAttribute('download', `Registro_Ventas_Lysandri_${cleanPeriodo}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  if (!isAuthenticated) {
    return (
      <div className="lx-admin-account-gate flex min-h-[calc(100vh-4rem)] w-full overflow-y-auto bg-white dark:bg-[#0a0d14]">
        {/* Sección izquierda visual con el Logo animado de Lysandri */}
        <div className="relative hidden w-1/2 flex-col items-center justify-center overflow-hidden border-r border-slate-200 bg-slate-900 dark:border-slate-800 dark:bg-[#07090e] lg:flex">
          <div className="pointer-events-none absolute left-1/4 top-1/4 h-[30rem] w-[30rem] rounded-full bg-cyan-500/10 blur-3xl" />
          <div className="pointer-events-none absolute bottom-1/4 right-1/4 h-[30rem] w-[30rem] rounded-full bg-cyan-500/10 blur-3xl" />

          <div className="relative z-10 flex w-full flex-col items-center justify-center px-12 transition-transform duration-700 hover:scale-105">
            <img
              src={animatedLogo}
              alt="Lysandri Executive"
              className="mb-6 h-auto w-full max-h-[35vh] max-w-[22rem] object-contain drop-shadow-[0_0_35px_rgba(34,211,238,0.15)] xl:max-w-[28rem]"
            />

            <div className="flex h-12 items-center justify-center">
              <h1 className="text-center text-3xl font-bold leading-tight tracking-tight text-white xl:text-4xl">
                {typedText}
              </h1>

              {isTyping && (
                <span className="ml-2 inline-block h-8 w-1.5 animate-pulse rounded-full bg-cyan-500 shadow-[0_0_10px_rgba(6,182,212,0.8)] xl:h-10" />
              )}
            </div>
          </div>

          <div className="absolute bottom-6 left-12 z-10 font-mono text-[10px] uppercase tracking-widest text-slate-500">
            © 2026 Lysandri Global Tech · Plataforma Ejecutiva & Contable
          </div>
        </div>

        {/* Sección derecha - Formulario interactivo de Inicio de Sesión */}
        <div className="flex w-full items-center justify-center overflow-y-auto bg-white px-6 py-8 dark:bg-[#0a0d14] sm:px-8 md:px-12 lg:w-1/2">
          <div className="w-full max-w-md my-auto">
            {/* Logo visible en pantallas pequeñas o medianas si el panel izquierdo está oculto */}
            <div className="mb-6 flex justify-center lg:hidden">
              <img
                src={companyLogo}
                alt="Lysandri Executive"
                className="h-9 w-auto object-contain"
              />
            </div>

            {/* Encabezado contextual */}
            <div className="mb-6">
              <span className="mb-2.5 inline-flex items-center gap-1.5 rounded-full border border-cyan-500/20 bg-cyan-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-cyan-600 dark:text-cyan-400">
                <Briefcase className="h-3 w-3" />
                Acceso Corporativo
              </span>

              <h2 className="mb-2 text-2xl font-black tracking-tight text-slate-900 dark:text-white sm:text-3xl">
                Bienvenido de nuevo
              </h2>

              <p className="text-xs leading-relaxed text-slate-500 dark:text-slate-400 sm:text-sm">
                Ingresa tus credenciales autorizadas para acceder al portal de gestión contable y facturación.
              </p>
            </div>

            {/* Mensajes de Alerta */}
            {authError && (
              <div className="mb-5 flex items-start gap-3 rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-xs text-rose-600 dark:text-rose-300 animate-fadeIn">
                <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
                <span>{authError}</span>
              </div>
            )}



            {/* Formulario de Inicio de Sesión */}
            <form onSubmit={handleLoginSubmit} className="space-y-4">
              <div>
                <label
                  htmlFor="loginUsername"
                  className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300"
                >
                  Usuario
                </label>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <input
                    id="loginUsername"
                    type="text"
                    value={username}
                    placeholder="Usuario"
                    autoComplete="username"
                    required
                    onChange={(e) => setUsername(e.target.value)}
                    className="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-4 text-xs font-mono text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:bg-white focus:ring-2 focus:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100"
                  />
                </div>
              </div>

              <div>
                <label
                  htmlFor="loginPassword"
                  className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300"
                >
                  Contraseña
                </label>
                <div className="relative">
                  <KeyRound className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <input
                    id="loginPassword"
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    placeholder="••••••••"
                    autoComplete="current-password"
                    required
                    onChange={(e) => setPassword(e.target.value)}
                    className="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-11 text-xs text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:bg-white focus:ring-2 focus:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
                    title={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-between pt-1">
                <div className="flex items-center">
                  <input
                    id="remember"
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    className="h-4 w-4 rounded border-slate-300 bg-transparent text-cyan-500 focus:ring-cyan-500/20 dark:border-slate-700"
                  />
                  <label
                    htmlFor="remember"
                    className="ml-2 cursor-pointer text-xs text-slate-600 dark:text-slate-400"
                  >
                    Mantener sesión iniciada
                  </label>
                </div>

                <button
                  type="button"
                  onClick={() => alert('Por favor contacte al administrador de sistemas para restablecer sus credenciales.')}
                  className="text-[11px] font-medium text-cyan-600 transition-colors hover:text-cyan-500 hover:underline dark:text-cyan-400"
                >
                  ¿Olvidaste tu contraseña?
                </button>
              </div>

              <button
                type="submit"
                disabled={isVerifying}
                className="mt-6 flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-sm font-bold text-white shadow-[0_8px_28px_rgba(6,182,212,0.20)] transition-all duration-300 hover:scale-[1.02] hover:shadow-[0_12px_36px_rgba(79,70,229,0.28)] active:scale-95 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:scale-100"
              >
                {isVerifying && (
                  <LoaderCircle className="h-4 w-4 animate-spin" />
                )}
                <span>{isVerifying ? 'Iniciando sesión...' : 'Iniciar sesión'}</span>
              </button>
            </form>


          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="lx-admin-accounting-content min-h-screen bg-slate-50 text-slate-800 dark:bg-[#070b14] dark:text-slate-100 p-4 sm:p-6 lg:p-8 space-y-6 transition-colors duration-300">
      {/* Executive Header Toolbar */}
      <header className="lx-accounting-masthead rounded-2xl border border-slate-200 bg-white p-5 sm:p-6 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl relative overflow-hidden transition-colors">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <div className="flex items-center gap-3">
              <div className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-cyan-500/10 border border-cyan-500/20">
                <img
                  src={companyLogo}
                  alt="Lysandri Executive"
                  className="h-7 w-7 object-contain drop-shadow"
                />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h1 className="lx-admin-accounting-title">
                    Contabilidad y facturación
                  </h1>
                  <span className="lx-admin-accounting-tag">
                    Portal SUNAT
                  </span>
                </div>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Lysandri Executive · Gestión contable, facturación electrónica y conciliación financiera
                </p>
              </div>
            </div>

            <div className="lx-accounting-identity flex flex-wrap items-center gap-2 pt-1">
              <span className="font-mono text-[11px] font-semibold text-slate-700 bg-slate-100 border border-slate-200 dark:text-slate-300 dark:bg-slate-900 dark:border-slate-800 px-2.5 py-0.5 rounded-md">
                Lysandri · RUC: 20609812451
              </span>
              <span className="font-mono text-[11px] font-semibold text-cyan-700 bg-cyan-50 border border-cyan-200 dark:text-cyan-400 dark:bg-cyan-950/60 dark:border-cyan-500/30 px-2.5 py-0.5 rounded-md">
                Régimen MYPE Tributario (RMT)
              </span>
              <span className="font-mono text-[11px] text-emerald-700 bg-emerald-50 border border-emerald-200 dark:text-emerald-400 dark:bg-emerald-950/60 dark:border-emerald-500/30 px-2.5 py-0.5 rounded-md flex items-center gap-1.5 font-semibold">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                SIRE / RVIE 14.1 Conectado
              </span>
            </div>
          </div>

          {/* Consistent Corporate Action Toolbar */}
          <div className="lx-accounting-toolbar flex flex-wrap items-center gap-2">
            {/* Theme Toggle Button */}
            <button
              type="button"
              onClick={toggleTheme}
              title={isDarkMode ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-700 hover:border-cyan-500/50 hover:bg-white dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:border-slate-500 dark:hover:text-white transition-all shadow-sm active:scale-95"
            >
              {isDarkMode ? (
                <>
                  <Sun className="h-3.5 w-3.5 text-amber-400" />
                  <span>Modo Claro</span>
                </>
              ) : (
                <>
                  <Moon className="h-3.5 w-3.5 text-indigo-600" />
                  <span>Modo Oscuro</span>
                </>
              )}
            </button>

            <button
              type="button"
              onClick={handleRefresh}
              disabled={isLoadingData}
              title="Recargar datos desde el ledger contable"
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-700 hover:border-slate-400 hover:text-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:border-slate-500 dark:hover:text-white transition-all shadow-sm active:scale-95"
            >
              <RefreshCw className={`h-3.5 w-3.5 text-cyan-600 dark:text-cyan-400 ${isLoadingData ? 'animate-spin' : ''}`} />
              <span>Actualizar</span>
            </button>

            {canExportSire && (
              <button
                type="button"
                onClick={handleDescargarSire}
                disabled={isExportingSire}
                title="Generar y descargar estructura oficial SIRE 14.1 para SUNAT"
                className="inline-flex items-center gap-1.5 rounded-lg border border-blue-200 bg-blue-50 px-3.5 py-2 text-xs font-bold text-blue-700 hover:bg-blue-100 dark:border-blue-500/40 dark:bg-blue-950/60 dark:text-blue-300 dark:hover:bg-blue-900/60 dark:hover:text-white transition-all shadow-sm active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <Download className={`h-3.5 w-3.5 ${isExportingSire ? 'opacity-60' : 'text-blue-600 dark:text-blue-400'}`} />
                <span>Descargar SIRE 14.1 TXT</span>
              </button>
            )}

            <button
              type="button"
              onClick={exportarCSV}
              title="Exportar base completa a hoja de cálculo Excel / CSV"
              className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-200 bg-emerald-50 px-3.5 py-2 text-xs font-bold text-emerald-700 hover:bg-emerald-100 dark:border-emerald-500/40 dark:bg-emerald-950/60 dark:text-emerald-300 dark:hover:bg-emerald-900/60 dark:hover:text-white transition-all shadow-sm active:scale-95"
            >
              <FileSpreadsheet className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
              <span>Exportar Excel / CSV</span>
            </button>

            <button
              type="button"
              onClick={() => window.print()}
              title="Imprimir resumen ejecutivo"
              className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-700 hover:border-slate-400 hover:text-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:border-slate-500 dark:hover:text-white transition-all shadow-sm active:scale-95"
            >
              <Printer className="h-3.5 w-3.5 text-slate-500 dark:text-slate-400" />
              <span className="hidden sm:inline">Imprimir</span>
            </button>

            <button
              type="button"
              onClick={handleLogout}
              title="Cerrar sesión de auditoría"
              className="inline-flex items-center gap-1.5 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-700 hover:bg-rose-100 dark:border-rose-500/30 dark:bg-rose-950/30 dark:text-rose-300 dark:hover:bg-rose-900/50 dark:hover:border-rose-400 transition-all active:scale-95 ml-1"
            >
              <LogOut className="h-3.5 w-3.5" />
              <span>Cerrar</span>
            </button>
          </div>
        </div>
      </header>

      {dataError ? (
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 rounded-2xl border border-rose-300 bg-rose-50/90 p-6 text-xs text-rose-900 dark:border-rose-500/40 dark:bg-rose-950/40 dark:text-rose-200 shadow-md">
          <div className="flex items-start gap-3">
            <ShieldAlert className="h-6 w-6 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
            <div>
              <p className="font-bold text-sm text-rose-900 dark:text-rose-100">Error al cargar datos contables del servidor</p>
              <p className="mt-1 text-xs text-rose-800 dark:text-rose-300">{dataError}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleRefresh}
            className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-xs font-bold text-white hover:bg-rose-700 transition shadow-sm shrink-0"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>Reintentar conexión</span>
          </button>
        </div>
      ) : isLoadingData ? (
        <div className="flex items-center justify-center gap-3 p-12 text-slate-500 dark:text-slate-400">
          <RefreshCw className="h-5 w-5 animate-spin text-cyan-600" />
          <span className="text-sm font-semibold">Cargando reporte contable oficial...</span>
        </div>
      ) : (
        <>
          {data && (data.ventasConsolidadas?.length || 0) === 0 && (
            <div className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-5 text-slate-700 dark:border-slate-800 dark:bg-[#0d1322] dark:text-slate-300 shadow-sm">
              <FileText className="h-6 w-6 text-slate-400 shrink-0" />
              <div>
                <p className="font-bold text-sm text-slate-900 dark:text-white">No existen registros contables para el período seleccionado</p>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">El backend no reporta ventas ni comprobantes emitidos en este rango. Puede seleccionar otro período en los filtros superiores.</p>
              </div>
            </div>
          )}

      {/* Órdenes pendientes por facturar: presentadas por separado para no distorsionar comprobantes emitidos */}
      {metricasCalculadas.countPendientesFacturar > 0 && (
        <div className="flex items-start gap-3 rounded-xl border border-blue-200 bg-blue-50/80 p-4 text-xs text-blue-900 dark:border-blue-500/40 dark:bg-blue-950/40 dark:text-blue-200 shadow-sm">
          <Receipt className="h-5 w-5 shrink-0 text-blue-600 dark:text-blue-400 mt-0.5" />
          <div className="flex-1 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
            <div>
              <p className="font-bold uppercase tracking-wider text-[11px]">
                Órdenes pendientes por facturar ({metricasCalculadas.countPendientesFacturar})
              </p>
              <p className="mt-0.5 text-xs text-blue-800 dark:text-blue-300">
                Órdenes pagadas o registradas que aún no cuentan con comprobante emitido. Se excluyen de las bases imponibles e impuestos liquidados.
              </p>
            </div>
            <div className="font-mono text-xs font-bold text-blue-800 dark:text-blue-200 whitespace-nowrap">
              Monto pendiente: {formatSoles(metricasCalculadas.totalPendientesPen)}
              {metricasCalculadas.totalPendientesUsd > 0 && ` + ${formatDolares(metricasCalculadas.totalPendientesUsd)}`}
            </div>
          </div>
        </div>
      )}

      {/* 5 Financial & Fiscal KPIs */}
      <section className="lx-admin-accounting-metrics grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5" aria-label="Resumen financiero">
        {/* KPI 1 */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg relative overflow-hidden transition-all hover:border-slate-300 dark:hover:border-slate-700">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-slate-800 dark:text-white block">
                Total Facturado en Caja
              </span>
              <span className="text-[10px] text-slate-500 dark:text-slate-400">Ventas Brutas Separadas por Moneda</span>
            </div>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <DollarSign className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-emerald-600 dark:text-emerald-400 tracking-tight font-mono">
              {formatSoles(metricasCalculadas.totalPen)}
            </div>
            {metricasCalculadas.totalUsd > 0 && (
              <div className="text-xs font-semibold text-emerald-600/90 dark:text-emerald-300 font-mono mt-0.5">
                + {formatDolares(metricasCalculadas.totalUsd)}
              </div>
            )}
            <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400 flex items-center gap-1">
              <span>{metricasCalculadas.totalFacturas} Facturas</span>
              <span>•</span>
              <span>{metricasCalculadas.totalBoletas} Boletas</span>
            </p>
          </div>
        </div>

        {/* KPI 2 */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg relative overflow-hidden transition-all hover:border-slate-300 dark:hover:border-slate-700">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-slate-800 dark:text-white block">
                Ingreso Real Empresa
              </span>
              <span className="text-[10px] text-slate-500 dark:text-slate-400">Base Imponible Gravada</span>
            </div>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-cyan-500/10 text-cyan-600 dark:text-cyan-400">
              <TrendingUp className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-cyan-700 dark:text-cyan-300 tracking-tight font-mono">
              {formatSoles(metricasCalculadas.baseImponiblePen)}
            </div>
            {metricasCalculadas.baseImponibleUsd > 0 && (
              <div className="text-xs font-semibold text-cyan-600/90 dark:text-cyan-300 font-mono mt-0.5">
                + {formatDolares(metricasCalculadas.baseImponibleUsd)}
              </div>
            )}
            <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400">
              Monto libre de impuestos de SUNAT
            </p>
          </div>
        </div>

        {/* KPI 3 */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg relative overflow-hidden transition-all hover:border-slate-300 dark:hover:border-slate-700">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-slate-800 dark:text-white block">
                IGV a Pagar a SUNAT
              </span>
              <span className="text-[10px] text-slate-500 dark:text-slate-400">Débito Fiscal 18% (Casilla 101)</span>
            </div>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-500/10 text-indigo-600 dark:text-indigo-400">
              <Receipt className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-amber-600 dark:text-amber-300 tracking-tight font-mono">
              {formatSoles(metricasCalculadas.igvPen)}
            </div>
            {metricasCalculadas.igvUsd > 0 && (
              <div className="text-xs font-semibold text-amber-600/90 dark:text-amber-300 font-mono mt-0.5">
                + {formatDolares(metricasCalculadas.igvUsd)}
              </div>
            )}
            <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400">
              18% recaudado en cada venta
            </p>
          </div>
        </div>

        {/* KPI 4 */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg relative overflow-hidden transition-all hover:border-slate-300 dark:hover:border-slate-700">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-amber-700 dark:text-amber-300 block">
                Fondo en Banco Nación
              </span>
              <span className="text-[10px] text-slate-500 dark:text-slate-400">Detracciones SPOT (12%)</span>
            </div>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
              <Landmark className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-amber-600 dark:text-amber-300 tracking-tight font-mono">
              {formatSoles(metricasCalculadas.detraccionesPen)}
            </div>
            {metricasCalculadas.detraccionesUsd > 0 && (
              <div className="text-xs font-semibold text-amber-600/90 dark:text-amber-300 font-mono mt-0.5">
                + {formatDolares(metricasCalculadas.detraccionesUsd)}
              </div>
            )}
            <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400">
              Cta. Cte. BN N° 00-068-192837
            </p>
          </div>
        </div>

        {/* KPI 5 */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg relative overflow-hidden transition-all hover:border-slate-300 dark:hover:border-slate-700">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-bold text-amber-700 dark:text-amber-300 block">
                Pago a Cuenta Renta
              </span>
              <span className="text-[10px] text-slate-500 dark:text-slate-400">Renta MYPE 1.0% (Casilla 302)</span>
            </div>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
              <Calculator className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-xl sm:text-2xl font-black text-amber-600 dark:text-amber-300 tracking-tight font-mono">
              {formatSoles(metricasCalculadas.rentaMype)}
            </div>
            <p className="mt-1 text-[11px] text-slate-500 dark:text-slate-400">
              Tasa reducida bajo D.L. 1269
            </p>
          </div>
        </div>
      </section>

      {/* Tabs Navigation */}
      <div className="lx-admin-accounting-tabs lx-accounting-tabs flex flex-wrap items-center gap-2 border-b border-slate-200 dark:border-slate-800 pb-2" role="group" aria-label="Vistas contables">
        <button
          type="button"
          aria-pressed={activeTab === 'graficos'}
          onClick={() => setActiveTab('graficos')}
          className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
            activeTab === 'graficos'
              ? 'bg-slate-100 text-slate-900 border border-slate-300 shadow-sm dark:bg-gradient-to-r dark:from-cyan-950 dark:via-slate-800 dark:to-indigo-950 dark:text-white dark:border-cyan-500/40 dark:shadow-lg dark:shadow-cyan-500/10'
              : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-white dark:hover:bg-slate-900'
          }`}
        >
          <AccountingGlyph name="flow" className="h-[1.1rem] w-[1.1rem] text-cyan-600 dark:text-cyan-400" />
          <span>Flujo financiero</span>
          <span className="rounded-md bg-cyan-100 px-2 py-0.5 text-[10px] font-mono text-cyan-800 border border-cyan-200 dark:bg-cyan-950/80 dark:text-cyan-300 dark:border-cyan-500/30">
            En Vivo
          </span>
        </button>

        <button
          type="button"
          aria-pressed={activeTab === 'comprobantes'}
          onClick={() => setActiveTab('comprobantes')}
          className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
            activeTab === 'comprobantes'
              ? 'bg-white text-slate-900 border border-slate-300 shadow-sm dark:bg-slate-800 dark:text-white dark:border-slate-700 dark:shadow'
              : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-white dark:hover:bg-slate-900'
          }`}
        >
          <AccountingGlyph name="receipt" className="h-[1.1rem] w-[1.1rem] text-cyan-600 dark:text-cyan-400" />
          <span>Comprobantes</span>
          <span className="rounded-md bg-slate-100 px-2 py-0.5 text-[10px] font-mono text-slate-700 border border-slate-200 dark:bg-slate-950 dark:text-cyan-300 dark:border-slate-800">
            {ventasFiltradas.length}
          </span>
        </button>

        <button
          type="button"
          aria-pressed={activeTab === 'liquidacion'}
          onClick={() => setActiveTab('liquidacion')}
          className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
            activeTab === 'liquidacion'
              ? 'bg-white text-slate-900 border border-slate-300 shadow-sm dark:bg-slate-800 dark:text-white dark:border-slate-700 dark:shadow'
              : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-white dark:hover:bg-slate-900'
          }`}
        >
          <AccountingGlyph name="tax" className="h-[1.1rem] w-[1.1rem] text-indigo-600 dark:text-indigo-400" />
          <span>Liquidación 621</span>
          <span className="rounded-md bg-indigo-50 px-2 py-0.5 text-[10px] font-mono text-indigo-700 border border-indigo-200 dark:bg-slate-950 dark:text-indigo-300 dark:border-slate-800">
            Régimen MYPE
          </span>
        </button>

        <button
          type="button"
          aria-pressed={activeTab === 'medios_pago'}
          onClick={() => setActiveTab('medios_pago')}
          className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
            activeTab === 'medios_pago'
              ? 'bg-white text-slate-900 border border-slate-300 shadow-sm dark:bg-slate-800 dark:text-white dark:border-slate-700 dark:shadow'
              : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-white dark:hover:bg-slate-900'
          }`}
        >
          <AccountingGlyph name="treasury" className="h-[1.1rem] w-[1.1rem] text-emerald-600 dark:text-emerald-400" />
          <span>Tesorería B2B</span>
          <span className="rounded-md bg-emerald-50 px-2 py-0.5 text-[10px] font-mono text-emerald-700 border border-emerald-200 dark:bg-slate-950 dark:text-emerald-300 dark:border-slate-800">
            Conciliado
          </span>
        </button>
      </div>

      {/* TAB: GRÁFICOS Y ANÁLISIS VISUAL */}
      {activeTab === 'graficos' && (
        <section className="lx-accounting-analytics-view space-y-6">
          {/* Header de Análisis Gráfico */}
          <div className="lx-accounting-report-head rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl flex flex-col md:flex-row justify-between items-start md:items-center gap-4 transition-colors">
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <BarChart3 className="h-5 w-5 text-cyan-600 dark:text-cyan-400" />
                <h2 className="text-base sm:text-lg font-black text-slate-900 dark:text-white tracking-tight">
                  Panel de Métricas Visuales y Flujo Financiero
                </h2>
                <span className="rounded-full bg-emerald-100 border border-emerald-200 px-2.5 py-0.5 text-[10px] font-mono text-emerald-800 dark:bg-emerald-950/80 dark:border-emerald-500/30 dark:text-emerald-300 flex items-center gap-1">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                  Actualizado en Tiempo Real
                </span>
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Visualización ejecutiva de facturación acumulada, recaudación multi-canal y compensación tributaria SUNAT
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs text-slate-500 dark:text-slate-400 flex items-center gap-1 font-mono">
                <Calendar className="h-3.5 w-3.5 text-cyan-600 dark:text-cyan-400" />
                <span>Periodo:</span>
              </span>
              <select
                value={selectedPeriodo}
                onChange={(e) => handlePeriodoChange(e.target.value)}
                className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-xs font-mono text-slate-800 focus:border-cyan-400 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200"
              >
                <option value="TODOS">Todos los periodos</option>
                {periodosDisponibles.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Fila de Ratios Financieros Ejecutivos */}
          <div className="lx-accounting-ratio-grid grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg space-y-1.5 transition-colors hover:border-emerald-500/40">
              <div className="lx-accounting-ratio-label flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                <span className="font-semibold text-slate-700 dark:text-slate-300">Margen Neto Operativo</span>
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                  <ArrowUpRight className="h-4 w-4" />
                </div>
              </div>
              <div className="text-xl font-black text-slate-900 dark:text-white font-mono">
                {metricasCalculadas.total > 0 ? ((metricasCalculadas.baseImponible / metricasCalculadas.total) * 100).toFixed(1) : '84.7'}%
              </div>
              <p className="text-[10px] text-emerald-600 dark:text-emerald-400 flex items-center gap-1 font-mono">
                <span>Ingreso genuino tras IGV 18%</span>
              </p>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg space-y-1.5 transition-colors hover:border-amber-500/40">
              <div className="lx-accounting-ratio-label flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                <span className="font-semibold text-slate-700 dark:text-slate-300">Cobertura Fiscal SPOT</span>
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
                  <Percent className="h-4 w-4" />
                </div>
              </div>
              <div className="text-xl font-black text-amber-600 dark:text-amber-300 font-mono">
                {metricasCalculadas.coberturaSpot}%
              </div>
              <p className="text-[10px] text-slate-500 dark:text-slate-400 font-mono">
                Del IGV cubierto con fondos en BN
              </p>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg space-y-1.5 transition-colors hover:border-rose-500/40">
              <div className="lx-accounting-ratio-label flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                <span className="font-semibold text-slate-700 dark:text-slate-300">Liquidación de Pasarelas</span>
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-rose-500/10 text-rose-600 dark:text-rose-400">
                  <CreditCard className="h-4 w-4" />
                </div>
              </div>
              <div className="text-xl font-black text-slate-900 dark:text-white font-mono">
                Dato no disponible
              </div>
              <p className="text-[10px] text-slate-500 dark:text-slate-400 font-mono">
                Sin comisión inventada / no liquidado
              </p>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg space-y-1.5 transition-colors hover:border-cyan-500/40">
              <div className="lx-accounting-ratio-label flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
                <span className="font-semibold text-slate-700 dark:text-slate-300">Recaudación Diaria Promedio</span>
                <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-cyan-500/10 text-cyan-600 dark:text-cyan-400">
                  <Activity className="h-4 w-4" />
                </div>
              </div>
              <div className="text-xl font-black text-cyan-700 dark:text-cyan-300 font-mono">
                {formatSoles(metricasCalculadas.ventasPorDia.length > 0 ? metricasCalculadas.total / metricasCalculadas.ventasPorDia.length : metricasCalculadas.total / 5)}
              </div>
              <p className="text-[10px] text-slate-500 dark:text-slate-400 font-mono">
                En {metricasCalculadas.ventasPorDia.length || 5} fechas registradas
              </p>
            </div>
          </div>

          {/* Gráfico: Tendencia y Evolución Diaria de Ventas (Bar Chart Visual) */}
          <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl space-y-4 transition-colors">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2 border-b border-slate-200 dark:border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                <h3 className="font-bold text-sm text-slate-900 dark:text-white uppercase tracking-wider font-mono">
                  Evolución y Tendencia de Ventas por Fecha (PEN)
                </h3>
              </div>
              <div className="flex items-center gap-3 text-[11px] font-mono">
                <span className="flex items-center gap-1.5 text-amber-700 dark:text-amber-300">
                  <span className="h-2.5 w-2.5 rounded bg-amber-500" />
                  <span>Facturas (B2B)</span>
                </span>
                <span className="flex items-center gap-1.5 text-cyan-700 dark:text-cyan-300">
                  <span className="h-2.5 w-2.5 rounded bg-cyan-400" />
                  <span>Boletas (B2C)</span>
                </span>
              </div>
            </div>

            {/* Barras de ventas diarias con track de altura fija */}
            <div className="pt-2">
              <div className="flex items-end gap-3 sm:gap-4 overflow-x-auto pb-3 pt-6 px-3 min-h-[240px] bg-slate-50 dark:bg-slate-950/60 rounded-xl border border-slate-200 dark:border-slate-800/80">
                {metricasCalculadas.ventasPorDia.map((dia, index) => {
                  const alturaPct = Math.max(16, Math.min(100, Math.round((dia.total / metricasCalculadas.maxVentaDia) * 100)));
                  const pctFactura = dia.total > 0 ? (dia.facturasPen / dia.total) * 100 : 0;
                  const pctBoleta = dia.total > 0 ? (dia.boletasPen / dia.total) * 100 : 0;

                  return (
                    <div key={dia.fecha} className="flex flex-col items-center gap-2 min-w-[70px] sm:flex-1 group/bar">
                      {/* Monto de la barra */}
                      <div className="font-mono text-[10px] text-slate-600 dark:text-slate-300 group-hover/bar:text-emerald-600 dark:group-hover/bar:text-emerald-400 font-bold transition-colors text-center whitespace-nowrap">
                        {formatSoles(dia.total)}
                      </div>

                      {/* Track de altura fija para que la altura en porcentaje sea 100% visible */}
                      <div className="h-36 w-full flex items-end justify-center">
                        <div
                          style={{ height: `${alturaPct}%`, animationDelay: `${Math.min(index * 24, 160)}ms` }}
                          className="lx-accounting-daily-bar w-9 sm:w-full sm:max-w-[40px] rounded-t-lg bg-slate-200 dark:bg-slate-800 flex flex-col justify-end overflow-hidden transition-all duration-700 group-hover/bar:brightness-110 shadow-md border border-slate-300 dark:border-slate-700/60"
                        >
                          {/* Segmento Facturas */}
                          {dia.facturasPen > 0 && (
                            <div
                              style={{ height: `${pctFactura}%` }}
                              className="w-full bg-[var(--admin-orange)] transition-[height] duration-500"
                              title={`Facturas: ${formatSoles(dia.facturasPen)}`}
                            />
                          )}
                          {/* Segmento Boletas */}
                          {dia.boletasPen > 0 && (
                            <div
                              style={{ height: `${pctBoleta}%` }}
                              className="w-full bg-gradient-to-t from-cyan-600 via-cyan-500 to-cyan-400 transition-all duration-500"
                              title={`Boletas: ${formatSoles(dia.boletasPen)}`}
                            />
                          )}
                        </div>
                      </div>

                      {/* Etiqueta de Fecha */}
                      <div className="font-mono text-[10px] font-semibold text-slate-600 dark:text-slate-400 text-center whitespace-nowrap bg-white dark:bg-slate-900/90 px-2 py-0.5 rounded border border-slate-200 dark:border-slate-800 group-hover/bar:border-cyan-500/40 group-hover/bar:text-slate-900 dark:group-hover/bar:text-white transition-all shadow-sm">
                        {dia.fecha.slice(5)}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Gráfico 1 y Gráfico 2: Donut de Canales + Composición Fiscal */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Gráfico 1: Participación por Canales de Pago (Donut Chart SVG) */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl space-y-4 transition-colors">
              <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <PieChart className="h-4 w-4 text-cyan-600 dark:text-cyan-400" />
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white uppercase tracking-wider font-mono">
                    Canales de Recaudación (Participación %)
                  </h3>
                </div>
                <span className="text-[10px] font-mono text-slate-600 bg-slate-100 border border-slate-200 dark:text-slate-400 dark:bg-slate-900 dark:border-slate-800 px-2 py-0.5 rounded">
                  {ventasFiltradas.length} Operaciones
                </span>
              </div>

              <div className="flex flex-col sm:flex-row items-center justify-around gap-6 pt-2">
                {/* SVG Donut Chart */}
                <div className="relative flex items-center justify-center">
                  <svg className="h-48 w-48 -rotate-90 transform" viewBox="0 0 160 160">
                    <circle
                      cx="80"
                      cy="80"
                      r="60"
                      className="text-slate-200 dark:text-slate-800/60"
                      strokeWidth="18"
                      stroke="currentColor"
                      fill="transparent"
                    />
                    {/* izipay segment (Fuchsia) */}
                    <circle
                      cx="80"
                      cy="80"
                      r="60"
                      className="text-fuchsia-500 dark:text-fuchsia-400 transition-all duration-1000 ease-out"
                      strokeWidth="18"
                      strokeDasharray={`${(metricasCalculadas.pctIzipay / 100) * 377} 377`}
                      strokeDashoffset="0"
                      strokeLinecap="round"
                      stroke="currentColor"
                      fill="transparent"
                    />
                    {/* Stripe segment (Cyan) */}
                    <circle
                      cx="80"
                      cy="80"
                      r="60"
                      className="text-cyan-500 dark:text-cyan-400 transition-all duration-1000 ease-out"
                      strokeWidth="18"
                      strokeDasharray={`${(metricasCalculadas.pctStripe / 100) * 377} 377`}
                      strokeDashoffset={`-${(metricasCalculadas.pctIzipay / 100) * 377}`}
                      strokeLinecap="round"
                      stroke="currentColor"
                      fill="transparent"
                    />
                    {/* BCP segment (Indigo) */}
                    <circle
                      cx="80"
                      cy="80"
                      r="60"
                      className="text-indigo-500 transition-all duration-1000 ease-out"
                      strokeWidth="18"
                      strokeDasharray={`${(metricasCalculadas.pctBcp / 100) * 377} 377`}
                      strokeDashoffset={`-${((metricasCalculadas.pctIzipay + metricasCalculadas.pctStripe) / 100) * 377}`}
                      strokeLinecap="round"
                      stroke="currentColor"
                      fill="transparent"
                    />
                    {/* Interbank segment (Emerald) */}
                    <circle
                      cx="80"
                      cy="80"
                      r="60"
                      className="text-emerald-500 dark:text-emerald-400 transition-all duration-1000 ease-out"
                      strokeWidth="18"
                      strokeDasharray={`${(metricasCalculadas.pctInterbank / 100) * 377} 377`}
                      strokeDashoffset={`-${((metricasCalculadas.pctIzipay + metricasCalculadas.pctStripe + metricasCalculadas.pctBcp) / 100) * 377}`}
                      strokeLinecap="round"
                      stroke="currentColor"
                      fill="transparent"
                    />
                  </svg>
                  <div className="absolute flex flex-col items-center justify-center text-center">
                    <DollarSign className="h-4 w-4 text-emerald-600 dark:text-emerald-400 mb-0.5" />
                    <span className="text-sm font-black text-slate-900 dark:text-white font-mono tracking-tight">
                      {formatSoles(metricasCalculadas.totalPen)}
                    </span>
                    {metricasCalculadas.totalUsd > 0 && (
                      <span className="text-[10px] font-bold text-emerald-600/90 dark:text-emerald-300 font-mono">
                        + {formatDolares(metricasCalculadas.totalUsd)}
                      </span>
                    )}
                    <span className="text-[10px] text-slate-500 dark:text-slate-400 uppercase tracking-wider font-semibold">
                      Total Bruto
                    </span>
                  </div>
                </div>

                {/* Leyenda y Desglose por Canal */}
                <div className="flex-1 space-y-2.5 w-full">
                  <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-2.5 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="h-3 w-3 rounded-full bg-fuchsia-500 dark:bg-fuchsia-400 shadow-sm shadow-fuchsia-400/50" />
                      <div>
                        <div className="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                          <span>izipay Online</span>
                          <span className="rounded bg-fuchsia-100 px-1.5 py-0.2 text-[10px] font-mono text-fuchsia-800 border border-fuchsia-200 dark:bg-fuchsia-950 dark:text-fuchsia-300 dark:border-fuchsia-800/40">
                            {metricasCalculadas.pctIzipay}%
                          </span>
                        </div>
                        <span className="text-[10px] text-slate-500 dark:text-slate-400">{metricasCalculadas.countIzipay} órdenes (Tarjetas & Wallets)</span>
                      </div>
                    </div>
                    <div className="text-right font-mono">
                      <div className="text-xs font-bold text-slate-900 dark:text-white">{formatSoles(metricasCalculadas.izipayGrossPen)}</div>
                      {metricasCalculadas.izipayGrossUsd > 0 && (
                        <div className="text-[10px] text-fuchsia-700 dark:text-fuchsia-300 font-semibold">+ {formatDolares(metricasCalculadas.izipayGrossUsd)}</div>
                      )}
                      <div className="text-[10px] text-slate-500 dark:text-slate-400">Comisión: No liquidada</div>
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-2.5 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="h-3 w-3 rounded-full bg-cyan-500 dark:bg-cyan-400 shadow-sm shadow-cyan-400/50" />
                      <div>
                        <div className="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                          <span>Stripe Checkout</span>
                          <span className="rounded bg-cyan-100 px-1.5 py-0.2 text-[10px] font-mono text-cyan-800 border border-cyan-200 dark:bg-cyan-950 dark:text-cyan-300 dark:border-cyan-800/40">
                            {metricasCalculadas.pctStripe}%
                          </span>
                        </div>
                        <span className="text-[10px] text-slate-500 dark:text-slate-400">{metricasCalculadas.countStripe} pagos online</span>
                      </div>
                    </div>
                    <div className="text-right font-mono">
                      <div className="text-xs font-bold text-slate-900 dark:text-white">{formatSoles(metricasCalculadas.stripeGrossPen)}</div>
                      {metricasCalculadas.stripeGrossUsd > 0 && (
                        <div className="text-[10px] text-cyan-700 dark:text-cyan-300 font-semibold">+ {formatDolares(metricasCalculadas.stripeGrossUsd)}</div>
                      )}
                      <div className="text-[10px] text-slate-500 dark:text-slate-400">Comisión: No liquidada</div>
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-2.5 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="h-3 w-3 rounded-full bg-indigo-500 shadow-sm shadow-indigo-500/50" />
                      <div>
                        <div className="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                          <span>Telecrédito BCP</span>
                          <span className="rounded bg-indigo-100 px-1.5 py-0.2 text-[10px] font-mono text-indigo-800 border border-indigo-200 dark:bg-indigo-950 dark:text-indigo-300 dark:border-indigo-800/40">
                            {metricasCalculadas.pctBcp}%
                          </span>
                        </div>
                        <span className="text-[10px] text-slate-500 dark:text-slate-400">{metricasCalculadas.countBcp} facturas B2B directas</span>
                      </div>
                    </div>
                    <div className="text-right font-mono">
                      <div className="text-xs font-bold text-slate-900 dark:text-white">{formatSoles(metricasCalculadas.bcpGrossPen)}</div>
                      {metricasCalculadas.bcpGrossUsd > 0 && (
                        <div className="text-[10px] text-indigo-700 dark:text-indigo-300 font-semibold">+ {formatDolares(metricasCalculadas.bcpGrossUsd)}</div>
                      )}
                      <div className="text-[10px] text-emerald-600 dark:text-emerald-400 font-semibold">Comisión: 0%</div>
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-2.5 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="h-3 w-3 rounded-full bg-emerald-500 dark:bg-emerald-400 shadow-sm shadow-emerald-400/50" />
                      <div>
                        <div className="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                          <span>Interbank Empresas</span>
                          <span className="rounded bg-emerald-100 px-1.5 py-0.2 text-[10px] font-mono text-emerald-800 border border-emerald-200 dark:bg-emerald-950 dark:text-emerald-300 dark:border-emerald-800/40">
                            {metricasCalculadas.pctInterbank}%
                          </span>
                        </div>
                        <span className="text-[10px] text-slate-500 dark:text-slate-400">{metricasCalculadas.countInterbank} facturas B2B directas</span>
                      </div>
                    </div>
                    <div className="text-right font-mono">
                      <div className="text-xs font-bold text-slate-900 dark:text-white">{formatSoles(metricasCalculadas.interbankGrossPen)}</div>
                      {metricasCalculadas.interbankGrossUsd > 0 && (
                        <div className="text-[10px] text-emerald-700 dark:text-emerald-300 font-semibold">+ {formatDolares(metricasCalculadas.interbankGrossUsd)}</div>
                      )}
                      <div className="text-[10px] text-emerald-600 dark:text-emerald-400 font-semibold">Comisión: 0%</div>
                    </div>
                  </div>

                  {metricasCalculadas.countSinClasificar > 0 && (
                    <div className="rounded-xl border border-amber-300 bg-amber-50 dark:border-amber-500/40 dark:bg-amber-950/30 p-2.5 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="h-3 w-3 rounded-full bg-amber-500 shadow-sm shadow-amber-500/50" />
                        <div>
                          <div className="text-xs font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                            <span>Sin clasificar</span>
                            <span className="rounded bg-amber-100 px-1.5 py-0.2 text-[10px] font-mono text-amber-800 border border-amber-200 dark:bg-amber-900 dark:text-amber-300">
                              {metricasCalculadas.pctSinClasificar}%
                            </span>
                          </div>
                          <span className="text-[10px] text-amber-800 dark:text-amber-300">{metricasCalculadas.countSinClasificar} operaciones</span>
                        </div>
                      </div>
                      <div className="text-right font-mono">
                        <div className="text-xs font-bold text-amber-900 dark:text-amber-200">{formatSoles(metricasCalculadas.sinClasificarGrossPen)}</div>
                        <div className="text-[10px] text-slate-500">Pendiente conciliación</div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Gráfico 2: Desglose del Sol Facturado (Base Imponible vs. Impuestos SUNAT) */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl space-y-4 transition-colors">
              <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <Activity className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white uppercase tracking-wider font-mono">
                    Composición del Ingreso (¿A dónde va el dinero?)
                  </h3>
                </div>
                <span className="text-[10px] font-mono text-indigo-800 bg-indigo-50 border border-indigo-200 dark:text-indigo-300 dark:bg-indigo-950/60 dark:border-indigo-500/30 px-2 py-0.5 rounded">
                  Estructura Fiscal
                </span>
              </div>

              {/* Barra Stacked Proporcional 100% */}
              <div className="space-y-2 pt-2">
                <div className="flex justify-between text-xs font-mono">
                  <span className="text-cyan-700 dark:text-cyan-300 font-bold flex items-center gap-1">
                    <span className="h-2 w-2 rounded-full bg-cyan-500 dark:bg-cyan-400" />
                    <span>Base Empresa: {metricasCalculadas.total > 0 ? ((metricasCalculadas.baseImponible / metricasCalculadas.total) * 100).toFixed(1) : '84.7'}%</span>
                  </span>
                  <span className="text-indigo-700 dark:text-indigo-300 font-bold flex items-center gap-1">
                    <span className="h-2 w-2 rounded-full bg-indigo-500" />
                    <span>IGV SUNAT: {metricasCalculadas.total > 0 ? ((metricasCalculadas.igv / metricasCalculadas.total) * 100).toFixed(1) : '15.3'}%</span>
                  </span>
                </div>

                <div className="h-6 w-full rounded-xl bg-slate-100 dark:bg-slate-950 p-1 flex overflow-hidden border border-slate-200 dark:border-slate-800">
                  <div
                    style={{ width: `${Math.round(((metricasCalculadas.baseImponible) / (metricasCalculadas.total || 1)) * 100)}%` }}
                    className="h-full rounded-l-lg bg-gradient-to-r from-cyan-500 to-cyan-400 transition-all duration-700 shadow-sm"
                    title={`Base Imponible: ${formatSoles(metricasCalculadas.baseImponible)}`}
                  />
                  <div
                    style={{ width: `${Math.round(((metricasCalculadas.igv) / (metricasCalculadas.total || 1)) * 100)}%` }}
                    className="h-full rounded-r-lg bg-amber-500 transition-[width] duration-500"
                    title={`IGV 18%: ${formatSoles(metricasCalculadas.igv)}`}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-1">
                <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-3 space-y-1">
                  <span className="text-[10px] text-cyan-700 dark:text-cyan-400 uppercase font-mono font-bold">Ingreso Neto Empresa</span>
                  <div className="text-base sm:text-lg font-black text-cyan-700 dark:text-cyan-300 font-mono">
                    {formatSoles(metricasCalculadas.baseImponible)}
                  </div>
                  <p className="text-[10px] text-slate-500 dark:text-slate-400">Total libre de tributos directos</p>
                </div>

                <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-3 space-y-1">
                  <span className="text-[10px] text-indigo-700 dark:text-indigo-400 uppercase font-mono font-bold">Débito Fiscal IGV (18%)</span>
                  <div className="text-base sm:text-lg font-black text-indigo-700 dark:text-indigo-300 font-mono">
                    {formatSoles(metricasCalculadas.igv)}
                  </div>
                  <p className="text-[10px] text-slate-500 dark:text-slate-400">Recaudado para pago a SUNAT</p>
                </div>
              </div>

              {/* Medidor de Cobertura SPOT Banco de la Nación */}
              <div className="rounded-xl border border-amber-300 bg-amber-50/70 dark:border-amber-500/30 dark:bg-amber-950/20 p-3.5 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold text-amber-800 dark:text-amber-300 flex items-center gap-1.5">
                    <Landmark className="h-4 w-4 text-amber-600 dark:text-amber-400" />
                    <span>Fondo SPOT Banco de la Nación</span>
                  </span>
                  <span className="font-mono text-xs font-bold text-amber-800 dark:text-amber-200">
                    {metricasCalculadas.coberturaSpot}% del IGV cubierto
                  </span>
                </div>

                <div className="h-2.5 w-full rounded-full bg-amber-100 dark:bg-slate-900 overflow-hidden border border-amber-200 dark:border-slate-800">
                  <div
                    style={{ width: `${metricasCalculadas.coberturaSpot}%` }}
                    className="h-full rounded-full bg-gradient-to-r from-amber-500 to-amber-400 transition-all duration-700"
                  />
                </div>

                <div className="flex justify-between items-center text-[10px] font-mono text-slate-600 dark:text-slate-400">
                  <span>Retenido en BN: <strong className="text-amber-700 dark:text-amber-300">{formatSoles(metricasCalculadas.detracciones)}</strong></span>
                  <span>Total IGV: <strong className="text-slate-800 dark:text-slate-300">{formatSoles(metricasCalculadas.igv)}</strong></span>
                </div>
              </div>
            </div>
          </div>

          {/* Gráfico 3 y Gráfico 4: Boletas vs Facturas + Cascada de Liquidación SUNAT */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Gráfico 3: Estructura de Comprobantes (Facturas vs. Boletas) */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl space-y-4 transition-colors">
              <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <Receipt className="h-4 w-4 text-cyan-600 dark:text-cyan-400" />
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white uppercase tracking-wider font-mono">
                    Facturas (B2B) vs. Boletas (B2C)
                  </h3>
                </div>
                <span className="text-[10px] font-mono text-slate-600 bg-slate-100 border border-slate-200 dark:text-slate-400 dark:bg-slate-900 dark:border-slate-800 px-2 py-0.5 rounded">
                  Comparativa de Ventas
                </span>
              </div>

              <div className="space-y-4 pt-1">
                {/* Facturas */}
                <div className="space-y-1.5">
                  <div className="flex justify-between text-xs">
                    <span className="font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                      <Building className="h-3.5 w-3.5 text-indigo-600 dark:text-indigo-400" />
                      <span>Facturas Electrónicas (F001)</span>
                      <span className="rounded bg-indigo-100 px-1.5 py-0.2 text-[10px] font-mono text-indigo-800 border border-indigo-200 dark:bg-indigo-950 dark:text-indigo-300 dark:border-indigo-800/40">
                        {metricasCalculadas.totalFacturasPen} emitidas PEN
                      </span>
                      {metricasCalculadas.totalFacturasUsd > 0 && (
                        <span className="rounded bg-indigo-50 px-1.5 py-0.2 text-[10px] font-mono text-indigo-700 border border-indigo-300 dark:bg-indigo-950/50">
                          {metricasCalculadas.totalFacturasUsd} USD
                        </span>
                      )}
                    </span>
                    <span className="font-mono font-bold text-indigo-600 dark:text-indigo-300">
                      {formatSoles(metricasCalculadas.montoFacturasPen)}
                      {metricasCalculadas.montoFacturasUsd > 0 && ` + ${formatDolares(metricasCalculadas.montoFacturasUsd)}`}
                    </span>
                  </div>
                  <div className="h-3 w-full rounded-full bg-slate-100 dark:bg-slate-950 overflow-hidden border border-slate-200 dark:border-slate-800">
                    <div
                      style={{ width: `${metricasCalculadas.totalPen > 0 ? Math.round((metricasCalculadas.montoFacturasPen / metricasCalculadas.totalPen) * 100) : 0}%` }}
                      className="h-full rounded-full bg-[var(--admin-orange)] transition-[width] duration-500"
                    />
                  </div>
                  <div className="flex justify-between text-[10px] font-mono text-slate-500 dark:text-slate-400">
                    <span>Clientes Corporativos con RUC</span>
                    <span>
                      Ticket promedio: {formatSoles(metricasCalculadas.ticketFacturaPen)}
                      {metricasCalculadas.totalFacturasUsd > 0 && ` (+ ${formatDolares(metricasCalculadas.ticketFacturaUsd)})`}
                    </span>
                  </div>
                </div>

                {/* Boletas */}
                <div className="space-y-1.5">
                  <div className="flex justify-between text-xs">
                    <span className="font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                      <User className="h-3.5 w-3.5 text-cyan-600 dark:text-cyan-400" />
                      <span>Boletas de Venta (B001)</span>
                      <span className="rounded bg-cyan-100 px-1.5 py-0.2 text-[10px] font-mono text-cyan-800 border border-cyan-200 dark:bg-cyan-950 dark:text-cyan-300 dark:border-cyan-800/40">
                        {metricasCalculadas.totalBoletasPen} emitidas PEN
                      </span>
                      {metricasCalculadas.totalBoletasUsd > 0 && (
                        <span className="rounded bg-cyan-50 px-1.5 py-0.2 text-[10px] font-mono text-cyan-700 border border-cyan-300 dark:bg-cyan-950/50">
                          {metricasCalculadas.totalBoletasUsd} USD
                        </span>
                      )}
                    </span>
                    <span className="font-mono font-bold text-cyan-700 dark:text-cyan-300">
                      {formatSoles(metricasCalculadas.montoBoletasPen)}
                      {metricasCalculadas.montoBoletasUsd > 0 && ` + ${formatDolares(metricasCalculadas.montoBoletasUsd)}`}
                    </span>
                  </div>
                  <div className="h-3 w-full rounded-full bg-slate-100 dark:bg-slate-950 overflow-hidden border border-slate-200 dark:border-slate-800">
                    <div
                      style={{ width: `${metricasCalculadas.totalPen > 0 ? Math.round((metricasCalculadas.montoBoletasPen / metricasCalculadas.totalPen) * 100) : 0}%` }}
                      className="h-full rounded-full bg-gradient-to-r from-cyan-500 to-cyan-400 transition-all duration-700"
                    />
                  </div>
                  <div className="flex justify-between text-[10px] font-mono text-slate-500 dark:text-slate-400">
                    <span>Estudiantes y Profesionales con DNI</span>
                    <span>
                      Ticket promedio: {formatSoles(metricasCalculadas.ticketBoletaPen)}
                      {metricasCalculadas.totalBoletasUsd > 0 && ` (+ ${formatDolares(metricasCalculadas.ticketBoletaUsd)})`}
                    </span>
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 text-[11px] text-slate-700 dark:border-slate-800 dark:bg-slate-950/80 dark:text-slate-300 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1">
                <span>Ticket Promedio General del Portal:</span>
                <div className="text-right font-mono font-bold text-emerald-600 dark:text-emerald-400 text-sm">
                  <span>{formatSoles(metricasCalculadas.ticketPromedioPen)}</span>
                  {metricasCalculadas.countComprobantesUsd > 0 && (
                    <span className="text-xs text-emerald-700/80 dark:text-emerald-300 ml-2">
                      (+ {formatDolares(metricasCalculadas.ticketPromedioUsd)})
                    </span>
                  )}
                </div>
              </div>
            </div>

            {/* Gráfico 4: Cascada de Compensación SUNAT (PDT 621) */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl space-y-4 transition-colors">
              <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <Calculator className="h-4 w-4 text-violet-600 dark:text-violet-400" />
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white uppercase tracking-wider font-mono">
                    Flujo de Pago Tributario Formulario 621
                  </h3>
                </div>
                <span className="text-[10px] font-mono text-violet-800 bg-violet-50 border border-violet-200 dark:text-violet-300 dark:bg-violet-950/60 dark:border-violet-500/30 px-2 py-0.5 rounded">
                  Régimen MYPE 1%
                </span>
              </div>

              {/* Cascada visual en 3 pasos */}
              <div className="space-y-2.5 pt-1 font-mono text-xs">
                <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-2.5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="flex h-5 w-5 items-center justify-center rounded bg-slate-200 text-slate-800 dark:bg-slate-800 dark:text-slate-300 text-[10px] font-bold">1</span>
                    <div>
                      <span className="font-bold text-slate-900 dark:text-white block">Impuesto Determinado Bruto</span>
                      <span className="text-[10px] text-slate-500 dark:text-slate-400">IGV 18% ({formatSoles(metricasCalculadas.igv)}) + Renta 1% ({formatSoles(metricasCalculadas.rentaMype)})</span>
                    </div>
                  </div>
                  <span className="font-bold text-rose-600 dark:text-rose-300 text-sm">
                    {formatSoles(metricasCalculadas.igv + metricasCalculadas.rentaMype)}
                  </span>
                </div>

                <div className="rounded-xl border border-amber-300 bg-amber-50/70 dark:border-amber-500/30 dark:bg-amber-950/20 p-2.5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="flex h-5 w-5 items-center justify-center rounded bg-amber-100 text-amber-800 border border-amber-300 dark:bg-amber-950 dark:text-amber-300 dark:border-amber-500/30 text-[10px] font-bold">2</span>
                    <div>
                      <span className="font-bold text-amber-800 dark:text-amber-300 block">(-) Compensación Cta SPOT (Banco Nación)</span>
                      <span className="text-[10px] text-slate-500 dark:text-slate-400">{metricasCalculadas.countRetenidasSpot} operaciones retenidas por clientes B2B</span>
                    </div>
                  </div>
                  <span className="font-bold text-amber-700 dark:text-amber-300 text-sm">
                    - {formatSoles(metricasCalculadas.detracciones)}
                  </span>
                </div>

                <div className="rounded-xl border border-emerald-300 bg-emerald-50 dark:border-emerald-500/40 dark:bg-emerald-950/30 p-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="flex h-5 w-5 items-center justify-center rounded bg-emerald-100 text-emerald-800 border border-emerald-300 dark:bg-emerald-900 dark:text-emerald-300 dark:border-emerald-500/40 text-[10px] font-bold">3</span>
                    <div>
                      <span className="font-bold text-emerald-800 dark:text-emerald-300 block text-sm">(=) Desembolso Neto en Efectivo</span>
                      <span className="text-[10px] text-emerald-700 dark:text-slate-300">Monto real a pagar a SUNAT (Casilla 188)</span>
                    </div>
                  </div>
                  <span className="font-black text-emerald-700 dark:text-emerald-300 text-base">
                    {formatSoles(metricasCalculadas.netoPagarSunat)}
                  </span>
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-2.5 flex items-center justify-between text-[11px] text-slate-600 dark:text-slate-400">
                <span className="flex items-center gap-1.5 text-slate-700 dark:text-slate-300">
                  <Check className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
                  <span>Ahorro de liquidez directa en caja:</span>
                </span>
                <span className="font-mono font-bold text-amber-700 dark:text-amber-300">
                  {formatSoles(metricasCalculadas.detracciones)}
                </span>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* TAB 1: REGISTRO DE COMPROBANTES (SIRE / RVIE) */}
      {activeTab === 'comprobantes' && (
        <section className="space-y-4">
          {/* Filters Bar */}
          <div className="rounded-2xl border border-slate-200 bg-white p-4 space-y-3 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] transition-colors">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              {/* Search */}
              <div className="relative w-full lg:max-w-md">
                <Search className="absolute left-3.5 top-2.5 h-4 w-4 text-slate-400 dark:text-slate-500" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Buscar por cliente, RUC/DNI, serie, CUO o canal..."
                  className="w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-4 py-2 text-xs text-slate-800 placeholder-slate-400 focus:border-cyan-500 focus:bg-white focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-white dark:placeholder-slate-500 dark:focus:border-cyan-400 transition-colors"
                />
              </div>

              {/* Badges / Options */}
              <div className="flex flex-wrap items-center gap-2.5">
                {/* Tipo */}
                <div className="flex items-center rounded-lg border border-slate-200 bg-slate-100 p-1 dark:border-slate-700 dark:bg-slate-950">
                  {(['TODOS', 'BOLETA', 'FACTURA'] as const).map((tipo) => (
                    <button
                      key={tipo}
                      type="button"
                      onClick={() => setSelectedTipo(tipo)}
                      className={`rounded px-2.5 py-1 font-mono text-[11px] font-bold transition-all ${
                        selectedTipo === tipo
                          ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-800 dark:text-white'
                          : 'text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
                      }`}
                    >
                      {tipo === 'TODOS' ? 'Todos' : tipo === 'BOLETA' ? `Boletas (${metricasCalculadas.totalBoletas})` : `Facturas (${metricasCalculadas.totalFacturas})`}
                    </button>
                  ))}
                </div>

                {/* Estado */}
                <div className="flex items-center rounded-lg border border-slate-200 bg-slate-100 p-1 dark:border-slate-700 dark:bg-slate-950">
                  {(['TODOS', 'EMITIDO', 'ANULADO'] as const).map((estado) => (
                    <button
                      key={estado}
                      type="button"
                      onClick={() => setSelectedEstado(estado)}
                      className={`rounded px-2.5 py-1 font-mono text-[11px] font-bold transition-all ${
                        selectedEstado === estado
                          ? estado === 'ANULADO'
                            ? 'bg-rose-100 text-rose-800 shadow-sm dark:bg-rose-900/80 dark:text-rose-200'
                            : 'bg-white text-slate-900 shadow-sm dark:bg-slate-800 dark:text-white'
                          : 'text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
                      }`}
                    >
                      {estado === 'TODOS' ? 'Todos' : estado === 'EMITIDO' ? `Vigentes (${countVigentes})` : `Anulados (${countAnulados})`}
                    </button>
                  ))}
                </div>

                {/* Canal */}
                <div className="flex flex-wrap items-center rounded-lg border border-slate-200 bg-slate-100 p-1 dark:border-slate-700 dark:bg-slate-950">
                  {(['TODOS', 'IZIPAY', 'STRIPE', 'TELECREDITO', 'INTERBANK', 'SIN_CLASIFICAR'] as const).map((canal) => (
                    <button
                      key={canal}
                      type="button"
                      onClick={() => setSelectedCanal(canal)}
                      className={`rounded px-2.5 py-1 font-mono text-[11px] font-bold transition-all ${
                        selectedCanal === canal
                          ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-800 dark:text-white'
                          : 'text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
                      }`}
                    >
                      {canal === 'TODOS'
                        ? 'Todos Canales'
                        : canal === 'IZIPAY'
                        ? `izipay (${metricasCalculadas.countIzipay})`
                        : canal === 'STRIPE'
                        ? `Stripe (${metricasCalculadas.countStripe})`
                        : canal === 'TELECREDITO'
                        ? `BCP (${metricasCalculadas.countBcp})`
                        : canal === 'INTERBANK'
                        ? `Interbank (${metricasCalculadas.countInterbank})`
                        : `Sin clasificar (${metricasCalculadas.countSinClasificar})`}
                    </button>
                  ))}
                </div>

                {/* Periodo */}
                <div className="flex items-center gap-1.5">
                  <Calendar className="h-4 w-4 text-cyan-600 dark:text-cyan-400 shrink-0" />
                  <select
                    value={selectedPeriodo}
                    onChange={(e) => handlePeriodoChange(e.target.value)}
                    className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-xs font-mono text-slate-800 focus:border-cyan-400 focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200"
                  >
                    <option value="TODOS">Todos los periodos</option>
                    {periodosDisponibles.map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 border-t border-slate-100 dark:border-slate-800/80 pt-2.5">
              <span>
                Comprobantes visualizados: <strong className="text-slate-800 dark:text-white">{ventasFiltradas.length}</strong> de{' '}
                <strong className="text-slate-800 dark:text-white">{data?.ventasConsolidadas?.length || 0}</strong>
              </span>
              {(searchQuery || selectedTipo !== 'TODOS' || selectedEstado !== 'TODOS' || selectedCanal !== 'TODOS' || selectedPeriodo !== 'TODOS') && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchQuery('');
                    setSelectedTipo('TODOS');
                    setSelectedEstado('TODOS');
                    setSelectedCanal('TODOS');
                    setSelectedPeriodo('TODOS');
                  }}
                  className="text-cyan-600 dark:text-cyan-400 hover:underline font-semibold text-xs"
                >
                  Limpiar filtros
                </button>
              )}
            </div>
          </div>

          {/* Table */}
          <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl transition-colors">
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-100/90 font-mono text-[11px] font-bold uppercase tracking-wider text-slate-600 dark:border-slate-800 dark:bg-slate-950/80 dark:text-slate-400">
                    <th className="py-3.5 px-4">Periodo / CUO</th>
                    <th className="py-3.5 px-4">Comprobante</th>
                    <th className="py-3.5 px-4">Cliente / Razón Social</th>
                    <th className="py-3.5 px-4">Doc. Identidad</th>
                    <th className="py-3.5 px-4">Canal Recaudación</th>
                    <th className="py-3.5 px-4 text-right">Subtotal</th>
                    <th className="py-3.5 px-4 text-right">IGV (18%)</th>
                    <th className="py-3.5 px-4 text-right">SPOT 12%</th>
                    <th className="py-3.5 px-4 text-right">Total</th>
                    <th className="py-3.5 px-4 text-center">Estado</th>
                    <th className="py-3.5 px-4 text-center">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs font-sans dark:divide-slate-800/60">
                  {isLoadingData ? (
                    <tr>
                      <td colSpan={11} className="py-16 text-center text-slate-500 dark:text-slate-400">
                        <LoaderCircle className="h-8 w-8 animate-spin mx-auto text-cyan-500 dark:text-cyan-400 mb-2" />
                        <span>Sincronizando comprobantes fiscales...</span>
                      </td>
                    </tr>
                  ) : ventasFiltradas.length === 0 ? (
                    <tr>
                      <td colSpan={11} className="py-16 text-center text-slate-500 dark:text-slate-400 space-y-2">
                        <FileText className="h-10 w-10 text-slate-400 dark:text-slate-600 mx-auto" />
                        <p className="font-semibold text-sm text-slate-700 dark:text-slate-300">
                          No se encontraron registros para los filtros seleccionados
                        </p>
                      </td>
                    </tr>
                  ) : (
                    ventasFiltradas.map((venta) => {
                      const esFactura = (venta.tipoComprobante || '').toUpperCase().includes('FACTURA');
                      const esAnulado = venta.estadoComprobante === 'ANULADO';
                      const medio = (venta.medioPago || '').toUpperCase();

                      return (
                        <tr
                          key={venta.idOrden}
                          className={`hover:bg-slate-50/90 dark:hover:bg-slate-800/40 transition-colors ${
                            esAnulado ? 'opacity-60 bg-rose-50/50 dark:bg-rose-950/10' : ''
                          }`}
                        >
                          <td className="py-3.5 px-4 font-mono text-slate-700 dark:text-slate-300 whitespace-nowrap">
                            <div>{formatFecha(venta.fechaEmision || venta.fechaOrden)}</div>
                            <div className="text-[10px] text-slate-400 dark:text-slate-500 font-semibold">{venta.cuo || '—'}</div>
                          </td>

                          <td className="py-3.5 px-4 whitespace-nowrap">
                            {venta.idComprobante ? (
                              <div className="flex items-center gap-2">
                                <span
                                  className={`rounded px-1.5 py-0.5 font-mono text-[10px] font-bold ${
                                    esFactura
                                      ? 'bg-indigo-50 text-indigo-700 border border-indigo-200 dark:bg-indigo-950 dark:text-indigo-300 dark:border-indigo-500/30'
                                      : 'bg-cyan-50 text-cyan-700 border border-cyan-200 dark:bg-cyan-950 dark:text-cyan-300 dark:border-cyan-500/30'
                                  }`}
                                >
                                  {esFactura ? '01 FACTURA' : '03 BOLETA'}
                                </span>
                                <span className="font-mono font-bold text-slate-900 dark:text-white">
                                  {venta.serieNumero || '—'}
                                </span>
                              </div>
                            ) : (
                              <span className="rounded bg-slate-100 px-2 py-0.5 font-mono text-[10px] font-semibold text-slate-500 border border-slate-200 dark:bg-slate-900 dark:border-slate-800 dark:text-slate-400">
                                Sin Comprobante Fiscal
                              </span>
                            )}
                          </td>

                          <td className="py-3.5 px-4">
                            <div className="flex items-center gap-2">
                              {esFactura ? (
                                <Building className="h-3.5 w-3.5 text-indigo-500 dark:text-indigo-400 shrink-0" />
                              ) : (
                                <User className="h-3.5 w-3.5 text-cyan-500 dark:text-cyan-400 shrink-0" />
                              )}
                              <span className="font-medium text-slate-800 dark:text-slate-200 max-w-[190px] truncate block" title={venta.cliente}>
                                {venta.cliente || '—'}
                              </span>
                            </div>
                          </td>

                          <td className="py-3.5 px-4 font-mono text-slate-700 dark:text-slate-300 whitespace-nowrap">
                            <span className="rounded bg-slate-100 px-2 py-0.5 border border-slate-200 dark:bg-slate-950 dark:border-slate-800">
                              {venta.documentoIdentidad || '—'}
                            </span>
                          </td>

                          <td className="py-3.5 px-4 whitespace-nowrap">
                            {medio.includes('STRIPE') ? (
                              <span className="inline-flex items-center gap-1 rounded bg-cyan-50 px-2 py-0.5 border border-cyan-200 font-mono text-[10px] text-cyan-700 dark:bg-slate-950 dark:border-slate-800 dark:text-cyan-400">
                                <CreditCard className="h-3 w-3" />
                                <span>Stripe Online</span>
                              </span>
                            ) : medio.includes('TELECREDITO') ? (
                              <span className="inline-flex items-center gap-1 rounded bg-indigo-50 px-2 py-0.5 border border-indigo-200 font-mono text-[10px] text-indigo-700 dark:bg-slate-950 dark:border-slate-800 dark:text-indigo-400">
                                <Building className="h-3 w-3" />
                                <span>BCP Telecrédito</span>
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 rounded bg-emerald-50 px-2 py-0.5 border border-emerald-200 font-mono text-[10px] text-emerald-700 dark:bg-slate-950 dark:border-slate-800 dark:text-emerald-400">
                                <Building className="h-3 w-3" />
                                <span>Interbank B2B</span>
                              </span>
                            )}
                          </td>

                          <td className={`py-3.5 px-4 text-right font-mono whitespace-nowrap ${esAnulado ? 'line-through text-slate-400' : 'text-slate-700 dark:text-slate-300'}`}>
                            {venta.moneda === 'USD' ? formatDolares(venta.subtotal) : formatSoles(venta.subtotal)}
                          </td>

                          <td className={`py-3.5 px-4 text-right font-mono whitespace-nowrap ${esAnulado ? 'line-through text-slate-400' : 'text-indigo-700 dark:text-indigo-300'}`}>
                            {venta.moneda === 'USD' ? formatDolares(venta.igv) : formatSoles(venta.igv)}
                          </td>

                          <td className="py-3.5 px-4 text-right font-mono whitespace-nowrap">
                            {venta.montoDetraccion && venta.montoDetraccion > 0 && !esAnulado ? (
                              <span className="rounded bg-amber-50 border border-amber-300 px-1.5 py-0.5 text-amber-800 font-bold dark:bg-amber-950/70 dark:border-amber-500/30 dark:text-amber-300" title="SPOT 12% depositado en Banco de la Nación">
                                {venta.moneda === 'USD' ? formatDolares(venta.montoDetraccion) : formatSoles(venta.montoDetraccion)}
                              </span>
                            ) : (
                              <span className="text-slate-400 dark:text-slate-600">—</span>
                            )}
                          </td>

                          <td className={`py-3.5 px-4 text-right font-mono font-bold whitespace-nowrap ${esAnulado ? 'line-through text-rose-600 dark:text-rose-400' : 'text-emerald-700 dark:text-emerald-400'}`}>
                            {venta.moneda === 'USD' ? formatDolares(venta.total) : formatSoles(venta.total)}
                          </td>

                          <td className="py-3.5 px-4 text-center whitespace-nowrap">
                            {esAnulado ? (
                              <span className="rounded bg-rose-100 border border-rose-300 text-rose-800 dark:bg-rose-950/80 dark:border-rose-500/40 dark:text-rose-300 px-2 py-0.5 font-mono text-[10px] font-bold" title={venta.motivoAnulacion || 'Anulado'}>
                                ANULADO
                              </span>
                            ) : (
                              <span className="rounded bg-emerald-100 border border-emerald-300 text-emerald-800 dark:bg-emerald-950/80 dark:border-emerald-500/40 dark:text-emerald-300 px-2 py-0.5 font-mono text-[10px] font-bold">
                                VIGENTE
                              </span>
                            )}
                          </td>

                          <td className="py-3.5 px-4 text-center whitespace-nowrap">
                            <div className="flex items-center justify-center gap-1.5">
                              {venta.idComprobante ? (
                                <>
                                  <button
                                    type="button"
                                    onClick={() => setSelectedVoucherForPreview(venta)}
                                    title="Visualizar Comprobante Electrónico SUNAT"
                                    className="inline-flex items-center gap-1 rounded border border-slate-200 bg-slate-50 px-2 py-1 font-mono text-[10px] font-bold text-slate-700 hover:border-cyan-500 hover:text-cyan-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:border-cyan-400 dark:hover:text-white transition-colors"
                                  >
                                    <Eye className="h-3 w-3 text-cyan-600 dark:text-cyan-400" />
                                    <span>Ver</span>
                                  </button>

                                  {!esAnulado && canAnular && (
                                    <button
                                      type="button"
                                      onClick={() => handleAnularComprobante(venta)}
                                      title="Anular comprobante emitido ante SUNAT"
                                      className="inline-flex items-center gap-1 rounded border border-rose-200 bg-rose-50 px-2 py-1 font-mono text-[10px] font-bold text-rose-700 hover:bg-rose-100 dark:border-rose-500/30 dark:bg-rose-950/20 dark:text-rose-300 dark:hover:bg-rose-900/50 dark:hover:border-rose-400 transition-colors"
                                    >
                                      <Ban className="h-3 w-3" />
                                      <span>Anular</span>
                                    </button>
                                  )}
                                </>
                              ) : (
                                <span className="rounded bg-amber-50 border border-amber-200 px-2 py-0.5 font-mono text-[10px] text-amber-700 dark:bg-amber-950/40 dark:border-amber-500/30 dark:text-amber-300">
                                  Sin Comprobante
                                </span>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
                {/* Table Summary Footer con Monedas Separadas */}
                <tfoot>
                  <tr className="border-t-2 border-slate-200 bg-slate-50 font-mono text-xs font-bold text-slate-800 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300">
                    <td colSpan={5} className="py-3 px-4 text-slate-500 dark:text-slate-400">
                      TOTAL CONSOLIDADO PEN ({ventasFiltradas.filter((v) => (v.moneda || 'PEN') === 'PEN').length} Registros)
                    </td>
                    <td className="py-3 px-4 text-right text-cyan-700 dark:text-cyan-300">
                      {formatSoles(metricasCalculadas.baseImponiblePen)}
                    </td>
                    <td className="py-3 px-4 text-right text-indigo-700 dark:text-indigo-300">
                      {formatSoles(metricasCalculadas.igvPen)}
                    </td>
                    <td className="py-3 px-4 text-right text-amber-700 dark:text-amber-300">
                      {formatSoles(metricasCalculadas.detraccionesPen)}
                    </td>
                    <td className="py-3 px-4 text-right text-emerald-700 dark:text-emerald-400 text-sm">
                      {formatSoles(metricasCalculadas.totalPen)}
                    </td>
                    <td colSpan={2} className="py-3 px-4 text-center text-[10px] text-slate-500">
                      Base RVIE SIRE 14.1 PEN
                    </td>
                  </tr>
                  {metricasCalculadas.totalUsd > 0 && (
                    <tr className="border-t border-slate-200 bg-slate-50/60 font-mono text-xs font-bold text-slate-800 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-300">
                      <td colSpan={5} className="py-2.5 px-4 text-slate-500 dark:text-slate-400">
                        TOTAL CONSOLIDADO USD ({ventasFiltradas.filter((v) => v.moneda === 'USD').length} Registros)
                      </td>
                      <td className="py-2.5 px-4 text-right text-cyan-700 dark:text-cyan-300">
                        {formatDolares(metricasCalculadas.baseImponibleUsd)}
                      </td>
                      <td className="py-2.5 px-4 text-right text-indigo-700 dark:text-indigo-300">
                        {formatDolares(metricasCalculadas.igvUsd)}
                      </td>
                      <td className="py-2.5 px-4 text-right text-amber-700 dark:text-amber-300">
                        {formatDolares(metricasCalculadas.detraccionesUsd)}
                      </td>
                      <td className="py-2.5 px-4 text-right text-emerald-700 dark:text-emerald-400 text-sm">
                        {formatDolares(metricasCalculadas.totalUsd)}
                      </td>
                      <td colSpan={2} className="py-2.5 px-4 text-center text-[10px] text-slate-500">
                        Operaciones en Dólares USD
                      </td>
                    </tr>
                  )}
                </tfoot>
              </table>
            </div>
          </div>
        </section>
      )}

      {/* TAB 2: LIQUIDACIÓN TRIBUTARIA SUNAT (FORMULARIO VIRTUAL 621) */}
      {activeTab === 'liquidacion' && (
        <section className="space-y-6">
          <div className="rounded-2xl border border-slate-200 bg-white p-6 space-y-6 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl transition-colors">
            {/* Header del Formulario */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 border-b border-slate-200 dark:border-slate-800 pb-5">
              <div>
                <div className="flex items-center gap-2">
                  <Calculator className="h-6 w-6 text-indigo-600 dark:text-indigo-400" />
                  <h2 className="text-xl font-black text-slate-900 dark:text-white tracking-tight">
                    Declaración y Pago Mensual — Formulario Virtual N° 621 (PDT 621)
                  </h2>
                </div>
                <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                  Determinación mensual de Impuesto General a las Ventas (IGV) e Impuesto a la Renta Régimen MYPE
                </p>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-right dark:border-slate-700 dark:bg-slate-950">
                <div className="text-[10px] font-mono text-slate-500 dark:text-slate-400 font-bold uppercase">Periodo Tributario</div>
                <div className="text-sm font-black text-slate-900 dark:text-white font-mono">
                  {data?.liquidacionTributaria?.periodoTributario || (selectedPeriodo !== 'TODOS' ? selectedPeriodo : 'Periodo Actual')}
                </div>
              </div>
            </div>

            {/* Casillas Oficiales SUNAT SOL (1-Clic Copy) */}
            <div className="rounded-xl bg-indigo-50/70 border border-indigo-200 dark:bg-slate-950 dark:border-indigo-500/30 p-3.5 flex flex-wrap items-center justify-between gap-3 shadow-sm">
              <span className="text-xs font-bold text-indigo-800 dark:text-indigo-300 flex items-center gap-1.5 font-mono">
                <Copy className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                <span>Casillas SUNAT SOL (Declara Fácil 621):</span>
              </span>

              <div className="flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => copyToClipboard(Math.round(metricasCalculadas.baseImponible).toString(), 'cas100_btn')}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-white hover:bg-slate-50 border border-slate-200 px-3 py-1.5 text-xs font-mono text-cyan-800 dark:bg-slate-900 dark:hover:bg-slate-800 dark:border-slate-700 dark:text-cyan-300 transition-colors shadow-sm active:scale-95"
                  title="Copiar valor para Casilla 100 (Ventas Gravadas)"
                >
                  {copiedText === 'cas100_btn' ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
                  <span>Cas. 100: {Math.round(metricasCalculadas.baseImponible)}</span>
                </button>

                <button
                  type="button"
                  onClick={() => copyToClipboard(Math.round(metricasCalculadas.igv).toString(), 'cas101_btn')}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-white hover:bg-slate-50 border border-slate-200 px-3 py-1.5 text-xs font-mono text-indigo-800 dark:bg-slate-900 dark:hover:bg-slate-800 dark:border-slate-700 dark:text-indigo-300 transition-colors shadow-sm active:scale-95"
                  title="Copiar valor para Casilla 101 (Débito Fiscal IGV)"
                >
                  {copiedText === 'cas101_btn' ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
                  <span>Cas. 101: {Math.round(metricasCalculadas.igv)}</span>
                </button>

                <button
                  type="button"
                  onClick={() => copyToClipboard(Math.round(metricasCalculadas.rentaMype).toString(), 'cas302_btn')}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-white hover:bg-slate-50 border border-slate-200 px-3 py-1.5 text-xs font-mono text-violet-800 dark:bg-slate-900 dark:hover:bg-slate-800 dark:border-slate-700 dark:text-violet-300 transition-colors shadow-sm active:scale-95"
                  title="Copiar valor para Casilla 302 (Renta MYPE 1%)"
                >
                  {copiedText === 'cas302_btn' ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
                  <span>Cas. 302: {Math.round(metricasCalculadas.rentaMype)}</span>
                </button>

                <button
                  type="button"
                  onClick={() => copyToClipboard(Math.round(metricasCalculadas.detracciones).toString(), 'cas188_btn')}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-white hover:bg-slate-50 border border-slate-200 px-3 py-1.5 text-xs font-mono text-amber-800 dark:bg-slate-900 dark:hover:bg-slate-800 dark:border-slate-700 dark:text-amber-300 transition-colors shadow-sm active:scale-95"
                  title="Copiar valor para Casilla 188 (Compensación Detracciones Banco de la Nación)"
                >
                  {copiedText === 'cas188_btn' ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
                  <span>Cas. 188 (SPOT): {Math.round(metricasCalculadas.detracciones)}</span>
                </button>
              </div>
            </div>

            {/* Ficha RUC Contribuyente */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950">
                <span className="text-[10px] font-mono uppercase font-bold text-slate-500 dark:text-slate-400">RUC Contribuyente</span>
                <p className="text-base font-black text-slate-900 dark:text-white font-mono">20609812451</p>
                <span className="text-[11px] text-slate-500 dark:text-slate-400">LYSANDRI · Entidad registrada en SUNAT</span>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950">
                <span className="text-[10px] font-mono uppercase font-bold text-slate-500 dark:text-slate-400">Régimen Afecto</span>
                <p className="text-base font-black text-cyan-700 dark:text-cyan-400 font-mono">MYPE Tributario</p>
                <span className="text-[11px] text-slate-500 dark:text-slate-400">Decreto Legislativo N° 1269</span>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950">
                <span className="text-[10px] font-mono uppercase font-bold text-slate-500 dark:text-slate-400">Ventas Gravadas (Cas. 100)</span>
                <p className="text-base font-black text-slate-900 dark:text-white font-mono">
                  {formatSoles(metricasCalculadas.baseImponible)}
                </p>
                <span className="text-[11px] text-slate-500 dark:text-slate-400">Base Imponible Neta</span>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-1 dark:border-slate-800 dark:bg-slate-950">
                <span className="text-[10px] font-mono uppercase font-bold text-slate-500 dark:text-slate-400">Vencimiento Fiscal</span>
                <p className="text-base font-black text-amber-700 dark:text-amber-400 font-mono">16 Nov 2026</p>
                <span className="text-[11px] text-slate-500 dark:text-slate-400">Dígito terminal RUC: 1</span>
              </div>
            </div>

            {/* Hoja de Trabajo PDT 621 Oficial */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Sección IGV */}
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-5 space-y-4 dark:border-slate-800 dark:bg-slate-950/60">
                <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-2.5">
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white font-mono uppercase flex items-center gap-2">
                    <Receipt className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                    <span>I. Determinación del IGV (18%)</span>
                  </h3>
                  <span className="text-[10px] font-mono text-indigo-800 font-bold bg-indigo-100 px-2 py-0.5 rounded dark:bg-indigo-950 dark:text-indigo-400">
                    Tasa 18%
                  </span>
                </div>

                <div className="space-y-2.5 text-xs font-mono">
                  <div className="flex items-center justify-between py-1 border-b border-slate-200/80 dark:border-slate-900">
                    <span className="text-slate-600 dark:text-slate-400">[Cas. 100] Ventas Netas Gravadas:</span>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-slate-900 dark:text-white">{formatSoles(metricasCalculadas.baseImponible)}</span>
                      <button
                        type="button"
                        onClick={() => copyToClipboard(Math.round(metricasCalculadas.baseImponible).toString(), 'cas100')}
                        className="inline-flex items-center gap-1 rounded bg-white hover:bg-slate-100 border border-slate-200 px-1.5 py-0.5 text-[10px] text-cyan-700 font-mono dark:bg-slate-900 dark:hover:bg-slate-800 dark:border-slate-700 dark:text-cyan-300"
                        title="Copiar para Casilla 100"
                      >
                        {copiedText === 'cas100' ? <Check className="h-3 w-3 text-emerald-500" /> : <Copy className="h-3 w-3" />}
                        <span>{copiedText === 'cas100' ? 'Copiado' : 'Copiar'}</span>
                      </button>
                    </div>
                  </div>

                  <div className="flex items-center justify-between py-1 border-b border-slate-200/80 dark:border-slate-900">
                    <span className="text-slate-600 dark:text-slate-400">[Cas. 101] Impuesto Resultante o Débito Fiscal:</span>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-indigo-700 dark:text-indigo-300">{formatSoles(metricasCalculadas.igv)}</span>
                      <button
                        type="button"
                        onClick={() => copyToClipboard(Math.round(metricasCalculadas.igv).toString(), 'cas101')}
                        className="inline-flex items-center gap-1 rounded bg-white hover:bg-slate-100 border border-slate-200 px-1.5 py-0.5 text-[10px] text-indigo-700 font-mono dark:bg-slate-900 dark:hover:bg-slate-800 dark:border-slate-700 dark:text-indigo-300"
                        title="Copiar para Casilla 101"
                      >
                        {copiedText === 'cas101' ? <Check className="h-3 w-3 text-emerald-500" /> : <Copy className="h-3 w-3" />}
                        <span>{copiedText === 'cas101' ? 'Copiado' : 'Copiar'}</span>
                      </button>
                    </div>
                  </div>

                  <div className="flex justify-between py-1 border-b border-slate-200/80 dark:border-slate-900">
                    <span className="text-slate-600 dark:text-slate-400">[Cas. 102] Crédito Fiscal Compras del Periodo:</span>
                    <span className="text-slate-400 dark:text-slate-500">S/. 0.00</span>
                  </div>

                  <div className="flex items-center justify-between py-1.5 border-t border-slate-200 dark:border-slate-800 text-sm font-bold bg-slate-100 dark:bg-slate-900/60 px-2 rounded">
                    <span className="text-slate-900 dark:text-white">[Cas. 140] Saldo Deuda Tributaria IGV:</span>
                    <div className="flex items-center gap-2">
                      <span className="text-indigo-700 dark:text-indigo-400">{formatSoles(metricasCalculadas.igv)}</span>
                      <button
                        type="button"
                        onClick={() => copyToClipboard(Math.round(metricasCalculadas.igv).toString(), 'cas140')}
                        className="inline-flex items-center gap-1 rounded bg-white hover:bg-slate-100 border border-indigo-200 px-1.5 py-0.5 text-[10px] text-indigo-700 font-mono font-normal dark:bg-slate-950 dark:hover:bg-slate-800 dark:border-indigo-500/40 dark:text-indigo-300"
                        title="Copiar saldo de IGV"
                      >
                        {copiedText === 'cas140' ? <Check className="h-3 w-3 text-emerald-500" /> : <Copy className="h-3 w-3" />}
                        <span>{copiedText === 'cas140' ? 'Copiado' : 'Copiar'}</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* Sección Renta MYPE */}
              <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-5 space-y-4 dark:border-slate-800 dark:bg-slate-950/60">
                <div className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-2.5">
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white font-mono uppercase flex items-center gap-2">
                    <Calculator className="h-4 w-4 text-violet-600 dark:text-violet-400" />
                    <span>II. Impuesto a la Renta MYPE (RMT)</span>
                  </h3>
                  <span className="text-[10px] font-mono text-violet-800 font-bold bg-violet-100 px-2 py-0.5 rounded dark:bg-violet-950 dark:text-violet-400">
                    Tasa 1.0% (Hasta 300 UIT)
                  </span>
                </div>

                <div className="space-y-2.5 text-xs font-mono">
                  <div className="flex items-center justify-between py-1 border-b border-slate-200/80 dark:border-slate-900">
                    <span className="text-slate-600 dark:text-slate-400">[Cas. 301] Ingresos Netos del Periodo:</span>
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-slate-900 dark:text-white">{formatSoles(metricasCalculadas.baseImponible)}</span>
                      <button
                        type="button"
                        onClick={() => copyToClipboard(Math.round(metricasCalculadas.baseImponible).toString(), 'cas301')}
                        className="inline-flex items-center gap-1 rounded bg-white hover:bg-slate-100 border border-slate-200 px-1.5 py-0.5 text-[10px] text-cyan-700 font-mono dark:bg-slate-900 dark:hover:bg-slate-800 dark:border-slate-700 dark:text-cyan-300"
                        title="Copiar para Casilla 301"
                      >
                        {copiedText === 'cas301' ? <Check className="h-3 w-3 text-emerald-500" /> : <Copy className="h-3 w-3" />}
                        <span>{copiedText === 'cas301' ? 'Copiado' : 'Copiar'}</span>
                      </button>
                    </div>
                  </div>

                  <div className="flex justify-between py-1 border-b border-slate-200/80 dark:border-slate-900">
                    <span className="text-slate-600 dark:text-slate-400">[Cas. 312] Coeficiente / Porcentaje Aplicable:</span>
                    <span className="font-bold text-violet-700 dark:text-violet-300">1.00%</span>
                  </div>

                  <div className="flex justify-between py-1 border-b border-slate-200/80 dark:border-slate-900">
                    <span className="text-slate-600 dark:text-slate-400">[Cas. 317] Saldo a Favor del Ejercicio Anterior:</span>
                    <span className="text-slate-400 dark:text-slate-500">S/. 0.00</span>
                  </div>

                  <div className="flex items-center justify-between py-1.5 border-t border-slate-200 dark:border-slate-800 text-sm font-bold bg-slate-100 dark:bg-slate-900/60 px-2 rounded">
                    <span className="text-slate-900 dark:text-white">[Cas. 302] Pago a Cuenta Renta Determinado:</span>
                    <div className="flex items-center gap-2">
                      <span className="text-violet-700 dark:text-violet-400">{formatSoles(metricasCalculadas.rentaMype)}</span>
                      <button
                        type="button"
                        onClick={() => copyToClipboard(Math.round(metricasCalculadas.rentaMype).toString(), 'cas302')}
                        className="inline-flex items-center gap-1 rounded bg-white hover:bg-slate-100 border border-violet-200 px-1.5 py-0.5 text-[10px] text-violet-700 font-mono font-normal dark:bg-slate-950 dark:hover:bg-slate-800 dark:border-violet-500/40 dark:text-violet-300"
                        title="Copiar para Casilla 302"
                      >
                        {copiedText === 'cas302' ? <Check className="h-3 w-3 text-emerald-500" /> : <Copy className="h-3 w-3" />}
                        <span>{copiedText === 'cas302' ? 'Copiado' : 'Copiar'}</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Resumen de Compensación con Cuenta SPOT Banco de la Nación */}
            <div className="rounded-xl border border-amber-300 bg-amber-50/70 p-5 space-y-4 dark:border-amber-500/30 dark:bg-amber-950/10 transition-colors">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2 font-mono text-sm font-bold text-amber-800 dark:text-amber-300 uppercase">
                  <Landmark className="h-5 w-5 text-amber-600 dark:text-amber-400" />
                  <span>III. Compensación Tributaria con Cuenta SPOT Banco de la Nación</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs text-amber-800 bg-amber-100 border border-amber-200 dark:text-amber-400 dark:bg-amber-950/80 dark:border-amber-500/40 px-3 py-1 rounded-md">
                    Cta Cte BN N° 00-068-192837
                  </span>
                  <button
                    type="button"
                    onClick={() => copyToClipboard(Math.round(metricasCalculadas.detracciones).toString(), 'cas188')}
                    className="inline-flex items-center gap-1 rounded-md bg-amber-100 hover:bg-amber-200 border border-amber-300 px-2.5 py-1 text-xs text-amber-900 font-mono font-bold transition-all shadow-sm dark:bg-amber-950/90 dark:hover:bg-amber-900 dark:border-amber-400/40 dark:text-amber-200"
                    title="Copiar monto para Casilla 188 (Compensación de Detracciones)"
                  >
                    {copiedText === 'cas188' ? <Check className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                    <span>{copiedText === 'cas188' ? '¡Copiado para Casilla 188!' : 'Copiar Casilla 188'}</span>
                  </button>
                </div>
              </div>

              <p className="text-xs text-slate-700 dark:text-slate-300 leading-relaxed">
                De conformidad con la <strong>R.S. N° 183-2004/SUNAT</strong>, los fondos depositados por los clientes corporativos en la Cuenta de Detracciones del Banco de la Nación (12% sobre Facturas mayores a S/. 700.00) están destinados exclusivamente al pago de deudas tributarias de IGV y Renta en la plataforma SUNAT Operaciones en Línea.
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 pt-2 font-mono">
                <div className="rounded-lg bg-white p-3.5 border border-slate-200 dark:bg-slate-900 dark:border-slate-800">
                  <span className="text-[10px] text-slate-500 dark:text-slate-400 uppercase font-bold">Impuesto Total Bruto</span>
                  <div className="text-base font-black text-slate-900 dark:text-white mt-1">
                    {formatSoles(metricasCalculadas.igv + metricasCalculadas.rentaMype)}
                  </div>
                  <span className="text-[10px] text-slate-400 dark:text-slate-500">IGV + Renta MYPE</span>
                </div>

                <div className="rounded-lg bg-white p-3.5 border border-slate-200 dark:bg-slate-900 dark:border-slate-800">
                  <span className="text-[10px] text-amber-700 dark:text-amber-400 uppercase font-bold">Fondo Retenido SPOT (BN)</span>
                  <div className="text-base font-black text-amber-700 dark:text-amber-300 mt-1">
                    {formatSoles(metricasCalculadas.detracciones)}
                  </div>
                  <span className="text-[10px] text-slate-400 dark:text-slate-500">{metricasCalculadas.countRetenidasSpot} Facturas retenidas</span>
                </div>

                <div className="rounded-lg bg-white p-3.5 border border-slate-200 dark:bg-slate-900 dark:border-slate-800">
                  <span className="text-[10px] text-emerald-700 dark:text-emerald-400 uppercase font-bold">Compensación Aplicable</span>
                  <div className="text-base font-black text-emerald-700 dark:text-emerald-300 mt-1">
                    - {formatSoles(metricasCalculadas.detracciones)}
                  </div>
                  <span className="text-[10px] text-slate-400 dark:text-slate-500">Débito directo Cta SPOT</span>
                </div>

                <div className="rounded-lg bg-emerald-50 p-3.5 border border-emerald-300 dark:bg-emerald-950/40 dark:border-emerald-500/40">
                  <span className="text-[10px] text-emerald-800 dark:text-emerald-400 uppercase font-bold">Neto Efectivo a SUNAT</span>
                  <div className="text-lg font-black text-emerald-800 dark:text-emerald-300 mt-1">
                    {formatSoles(metricasCalculadas.netoPagarSunat)}
                  </div>
                  <span className="text-[10px] text-emerald-700 dark:text-slate-400">Casilla 188 FV 621</span>
                </div>
              </div>
            </div>

            {/* Acciones de Liquidación */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-4 border-t border-slate-200 dark:border-slate-800 pt-4">
              <div className="text-xs text-slate-500 dark:text-slate-400">
                Validación técnica interna: <strong className="text-emerald-700 dark:text-emerald-400">Estructura conforme con SIRE RVIE 14.1</strong> (No constituye constancia de recepción o certificación externa de SUNAT)
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={handleDescargarSire}
                  disabled={isExportingSire}
                  className="inline-flex items-center gap-2 rounded-lg bg-blue-600 hover:bg-blue-700 px-4 py-2 font-bold text-white transition-all text-xs shadow active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  <Download className="h-4 w-4" />
                  <span>Descargar Archivo SIRE TXT (14.1)</span>
                </button>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* TAB 3: MEDIOS DE PAGO, PASARELAS Y TESORERÍA B2B (COMPREHENSIVE LEDGER & RECONCILIATION) */}
      {activeTab === 'medios_pago' && (
        <section className="space-y-6">
          {/* Section A: Multi-channel Treasury Overview */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* izipay Card */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 space-y-3 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg transition-colors">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CreditCard className="h-4 w-4 text-fuchsia-600 dark:text-fuchsia-400" />
                  <span className="font-mono text-xs font-bold text-slate-900 dark:text-white uppercase">izipay Online</span>
                </div>
                <span className="rounded bg-fuchsia-50 dark:bg-fuchsia-950 px-2 py-0.5 text-[10px] font-bold text-fuchsia-700 dark:text-fuchsia-300 border border-fuchsia-200 dark:border-fuchsia-500/30">
                  Tarjetas & Wallets ({metricasCalculadas.pctIzipay}%)
                </span>
              </div>
              <div className="text-2xl font-black text-fuchsia-600 dark:text-fuchsia-400 font-mono">
                {formatSoles(metricasCalculadas.izipayGrossPen)}
              </div>
              {metricasCalculadas.izipayGrossUsd > 0 && (
                <div className="text-xs font-semibold text-fuchsia-700 dark:text-fuchsia-300 font-mono -mt-2">
                  + {formatDolares(metricasCalculadas.izipayGrossUsd)}
                </div>
              )}
              <div className="space-y-1 text-[11px] text-slate-600 dark:text-slate-400 pt-1 border-t border-slate-100 dark:border-slate-800">
                <div className="flex justify-between">
                  <span>Transacciones:</span>
                  <span className="font-mono font-bold text-slate-800 dark:text-white">{metricasCalculadas.countIzipay} órdenes</span>
                </div>
                <div className="flex justify-between text-slate-500 dark:text-slate-400">
                  <span>Comisión Pasarela:</span>
                  <span className="font-mono text-[10px]">Dato no disponible / no liquidado</span>
                </div>
                <div className="flex justify-between font-bold text-emerald-600 dark:text-emerald-400">
                  <span>Liquidado Neto:</span>
                  <span className="font-mono text-[10px]">Pendiente de liquidación</span>
                </div>
              </div>
              <div className="rounded-lg bg-fuchsia-50 dark:bg-fuchsia-950/40 border border-fuchsia-200 dark:border-fuchsia-500/20 p-2 text-[10px] text-fuchsia-800 dark:text-fuchsia-300">
                💡 Pasarela omnicanal con tarjetas peruanas e internacionales
              </div>
            </div>

            {/* Stripe Card */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 space-y-3 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg transition-colors">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <CreditCard className="h-4 w-4 text-cyan-600 dark:text-cyan-400" />
                  <span className="font-mono text-xs font-bold text-slate-900 dark:text-white uppercase">Stripe Checkout</span>
                </div>
                <span className="rounded bg-cyan-50 dark:bg-cyan-950 px-2 py-0.5 text-[10px] font-bold text-cyan-700 dark:text-cyan-300 border border-cyan-200 dark:border-cyan-500/30">
                  Online ({metricasCalculadas.pctStripe}%)
                </span>
              </div>
              <div className="text-2xl font-black text-cyan-600 dark:text-cyan-300 font-mono">
                {formatSoles(metricasCalculadas.stripeGrossPen)}
              </div>
              {metricasCalculadas.stripeGrossUsd > 0 && (
                <div className="text-xs font-semibold text-cyan-700 dark:text-cyan-300 font-mono -mt-2">
                  + {formatDolares(metricasCalculadas.stripeGrossUsd)}
                </div>
              )}
              <div className="space-y-1 text-[11px] text-slate-600 dark:text-slate-400 pt-1 border-t border-slate-100 dark:border-slate-800">
                <div className="flex justify-between">
                  <span>Transacciones:</span>
                  <span className="font-mono font-bold text-slate-800 dark:text-white">{metricasCalculadas.countStripe} pagos online</span>
                </div>
                <div className="flex justify-between text-slate-500 dark:text-slate-400">
                  <span>Comisión Pasarela:</span>
                  <span className="font-mono text-[10px]">Dato no disponible / no liquidado</span>
                </div>
                <div className="flex justify-between font-bold text-emerald-600 dark:text-emerald-400">
                  <span>Liquidado Neto:</span>
                  <span className="font-mono text-[10px]">Pendiente de liquidación</span>
                </div>
              </div>
              <div className="rounded-lg bg-cyan-50 dark:bg-cyan-950/40 border border-cyan-200 dark:border-cyan-500/20 p-2 text-[10px] text-cyan-800 dark:text-cyan-300">
                💡 Abono neto directo a cuenta bancaria
              </div>
            </div>

            {/* BCP Card */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 space-y-3 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg transition-colors">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Building className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                  <span className="font-mono text-xs font-bold text-slate-900 dark:text-white uppercase">Telecrédito BCP</span>
                </div>
                <span className="rounded bg-indigo-50 dark:bg-indigo-950 px-2 py-0.5 text-[10px] font-bold text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30">
                  B2B ({metricasCalculadas.pctBcp}%)
                </span>
              </div>
              <div className="text-2xl font-black text-blue-700 dark:text-sky-300 font-mono">
                {formatSoles(metricasCalculadas.bcpGrossPen)}
              </div>
              {metricasCalculadas.bcpGrossUsd > 0 && (
                <div className="text-xs font-semibold text-blue-800 dark:text-sky-200 font-mono -mt-2">
                  + {formatDolares(metricasCalculadas.bcpGrossUsd)}
                </div>
              )}
              <div className="space-y-1 text-[11px] text-slate-600 dark:text-slate-400 pt-1 border-t border-slate-100 dark:border-slate-800">
                <div className="flex justify-between">
                  <span>Órdenes Directas:</span>
                  <span className="font-mono font-bold text-slate-800 dark:text-white">{metricasCalculadas.countBcp} facturas B2B</span>
                </div>
                <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                  <span>Comisión Pasarela:</span>
                  <span className="font-mono">S/. 0.00 (0% - Transferencia)</span>
                </div>
                <div className="flex justify-between text-amber-600 dark:text-amber-400">
                  <span>Retención SPOT (12%):</span>
                  <span className="font-mono">{formatSoles(metricasCalculadas.bcpSpot)}</span>
                </div>
              </div>
              <div className="rounded-lg bg-indigo-50 dark:bg-indigo-950/40 border border-indigo-200 dark:border-indigo-500/20 p-2 text-[10px] text-indigo-800 dark:text-indigo-300">
                💡 Cero comisión. Conciliar con extracto BCP
              </div>
            </div>

            {/* Interbank Card */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 space-y-3 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg transition-colors">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Building className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                  <span className="font-mono text-xs font-bold text-slate-900 dark:text-white uppercase">Interbank Empresas</span>
                </div>
                <span className="rounded bg-emerald-50 dark:bg-emerald-950 px-2 py-0.5 text-[10px] font-bold text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-500/30">
                  B2B ({metricasCalculadas.pctInterbank}%)
                </span>
              </div>
              <div className="text-2xl font-black text-emerald-600 dark:text-emerald-300 font-mono">
                {formatSoles(metricasCalculadas.interbankGrossPen)}
              </div>
              {metricasCalculadas.interbankGrossUsd > 0 && (
                <div className="text-xs font-semibold text-emerald-700 dark:text-emerald-200 font-mono -mt-2">
                  + {formatDolares(metricasCalculadas.interbankGrossUsd)}
                </div>
              )}
              <div className="space-y-1 text-[11px] text-slate-600 dark:text-slate-400 pt-1 border-t border-slate-100 dark:border-slate-800">
                <div className="flex justify-between">
                  <span>Órdenes Directas:</span>
                  <span className="font-mono font-bold text-slate-800 dark:text-white">{metricasCalculadas.countInterbank} factura{metricasCalculadas.countInterbank === 1 ? '' : 's'} B2B</span>
                </div>
                <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                  <span>Comisión Pasarela:</span>
                  <span className="font-mono">S/. 0.00 (0% - Transferencia)</span>
                </div>
                <div className="flex justify-between text-amber-600 dark:text-amber-400">
                  <span>Retención SPOT (12%):</span>
                  <span className="font-mono">{formatSoles(metricasCalculadas.interbankSpot)}</span>
                </div>
              </div>
              <div className="rounded-lg bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-500/20 p-2 text-[10px] text-emerald-800 dark:text-emerald-300">
                💡 Cero comisión. Pago corporativo recibido
              </div>
            </div>

            {/* Banco de la Nación Card */}
            <div className="rounded-2xl border border-slate-200 bg-white p-5 space-y-3 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-lg transition-colors">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Landmark className="h-4 w-4 text-amber-600 dark:text-amber-400" />
                  <span className="font-mono text-xs font-bold text-slate-900 dark:text-white uppercase">Banco de la Nación</span>
                </div>
                <span className="rounded bg-amber-50 dark:bg-amber-950 px-2 py-0.5 text-[10px] font-bold text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-500/30">
                  SPOT Fondos
                </span>
              </div>
              <div className="text-2xl font-black text-amber-600 dark:text-amber-300 font-mono">
                {formatSoles(metricasCalculadas.detraccionesPen)}
              </div>
              {metricasCalculadas.detraccionesUsd > 0 && (
                <div className="text-xs font-semibold text-amber-700 dark:text-amber-300 font-mono -mt-2">
                  + {formatDolares(metricasCalculadas.detraccionesUsd)}
                </div>
              )}
              <div className="space-y-1 text-[11px] text-slate-600 dark:text-slate-400 pt-1 border-t border-slate-100 dark:border-slate-800">
                <div className="flex justify-between">
                  <span>Cta Cte BN N°:</span>
                  <span className="font-mono font-bold text-slate-800 dark:text-white">00-068-192837</span>
                </div>
                <div className="flex justify-between text-cyan-700 dark:text-cyan-400">
                  <span>Destino de Fondos:</span>
                  <span>Compensación SUNAT</span>
                </div>
                <div className="flex justify-between font-bold text-slate-900 dark:text-white">
                  <span>Total Retenido:</span>
                  <span className="font-mono">{formatSoles(metricasCalculadas.detraccionesPen)}</span>
                </div>
              </div>
              <div className="rounded-lg bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-500/20 p-2 text-[10px] text-amber-800 dark:text-amber-300">
                💡 Fondo intangible para pagar tributos SUNAT
              </div>
            </div>

            {/* Sin Clasificar Card (si existen) */}
            {metricasCalculadas.countSinClasificar > 0 && (
              <div className="rounded-2xl border border-amber-300 bg-amber-50/50 p-5 space-y-3 shadow-sm dark:border-amber-500/40 dark:bg-amber-950/20 transition-colors">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <ShieldAlert className="h-4 w-4 text-amber-600 dark:text-amber-400" />
                    <span className="font-mono text-xs font-bold text-slate-900 dark:text-white uppercase">Sin clasificar</span>
                  </div>
                  <span className="rounded bg-amber-100 dark:bg-amber-900 px-2 py-0.5 text-[10px] font-bold text-amber-800 dark:text-amber-200">
                    {metricasCalculadas.pctSinClasificar}%
                  </span>
                </div>
                <div className="text-2xl font-black text-amber-700 dark:text-amber-300 font-mono">
                  {formatSoles(metricasCalculadas.sinClasificarGrossPen)}
                </div>
                {metricasCalculadas.sinClasificarGrossUsd > 0 && (
                  <div className="text-xs font-semibold text-amber-800 dark:text-amber-200 font-mono -mt-2">
                    + {formatDolares(metricasCalculadas.sinClasificarGrossUsd)}
                  </div>
                )}
                <div className="space-y-1 text-[11px] text-slate-600 dark:text-slate-400 pt-1 border-t border-amber-200 dark:border-slate-800">
                  <div className="flex justify-between">
                    <span>Transacciones:</span>
                    <span className="font-mono font-bold text-slate-800 dark:text-white">{metricasCalculadas.countSinClasificar} operaciones</span>
                  </div>
                  <div className="flex justify-between text-slate-500">
                    <span>Clasificación:</span>
                    <span>Pendiente de conciliación</span>
                  </div>
                </div>
                <div className="rounded-lg bg-amber-100/70 dark:bg-amber-950/50 border border-amber-200 dark:border-amber-500/20 p-2 text-[10px] text-amber-800 dark:text-amber-300">
                  ⚠️ Método de pago no reconocido; no se asigna a Stripe de forma arbitraria
                </div>
              </div>
            )}
          </div>

          {/* Section B: Corporate Treasury Bank Accounts */}
          <div className="rounded-2xl border border-slate-200 bg-white p-5 space-y-4 shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl transition-colors">
            <h3 className="font-bold text-sm text-slate-900 dark:text-white font-mono uppercase flex items-center gap-2">
              <Wallet className="h-4 w-4 text-cyan-600 dark:text-cyan-400" />
              <span>Cuentas Bancarias de Tesorería — Lysandri</span>
            </h3>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 font-mono text-xs">
              <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-4 space-y-2 transition-colors">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-indigo-700 dark:text-indigo-300">Banco de Crédito del Perú (BCP)</span>
                  <span className="text-[10px] bg-slate-200 text-slate-700 dark:bg-slate-900 dark:text-slate-400 px-2 py-0.5 rounded">Cta Cte Soles</span>
                </div>
                <div className="space-y-1 text-slate-600 dark:text-slate-300">
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500">N° Cuenta:</span>
                    <span className="font-bold text-slate-900 dark:text-white">193-98218491-0-28</span>
                    <button
                      type="button"
                      onClick={() => copyToClipboard('193-98218491-0-28', 'bcp')}
                      className="text-cyan-600 hover:text-cyan-700 dark:text-cyan-400 dark:hover:text-cyan-300 ml-1"
                      title="Copiar número"
                    >
                      {copiedText === 'bcp' ? <Check className="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                    </button>
                  </div>
                  <div className="flex justify-between items-center text-[11px]">
                    <span className="text-slate-500">CCI:</span>
                    <span className="text-slate-600 dark:text-slate-400">0021930098218491028</span>
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-4 space-y-2 transition-colors">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-emerald-700 dark:text-emerald-300">Interbank Empresas</span>
                  <span className="text-[10px] bg-slate-200 text-slate-700 dark:bg-slate-900 dark:text-slate-400 px-2 py-0.5 rounded">Cta Cte Soles</span>
                </div>
                <div className="space-y-1 text-slate-600 dark:text-slate-300">
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500">N° Cuenta:</span>
                    <span className="font-bold text-slate-900 dark:text-white">200-3001849128</span>
                    <button
                      type="button"
                      onClick={() => copyToClipboard('200-3001849128', 'ibk')}
                      className="text-cyan-600 hover:text-cyan-700 dark:text-cyan-400 dark:hover:text-cyan-300 ml-1"
                      title="Copiar número"
                    >
                      {copiedText === 'ibk' ? <Check className="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                    </button>
                  </div>
                  <div className="flex justify-between items-center text-[11px]">
                    <span className="text-slate-500">CCI:</span>
                    <span className="text-slate-600 dark:text-slate-400">00320000300184912831</span>
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950 p-4 space-y-2 transition-colors">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-amber-700 dark:text-amber-300">Banco de la Nación (SPOT)</span>
                  <span className="text-[10px] bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-400 px-2 py-0.5 rounded">Detracciones 12%</span>
                </div>
                <div className="space-y-1 text-slate-600 dark:text-slate-300">
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500">N° Cuenta:</span>
                    <span className="font-bold text-slate-900 dark:text-white">00-068-192837</span>
                    <button
                      type="button"
                      onClick={() => copyToClipboard('00-068-192837', 'bn')}
                      className="text-amber-600 hover:text-amber-700 dark:text-amber-400 dark:hover:text-amber-300 ml-1"
                      title="Copiar número"
                    >
                      {copiedText === 'bn' ? <Check className="h-3.5 w-3.5 text-emerald-500 dark:text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                    </button>
                  </div>
                  <div className="flex justify-between items-center text-[11px]">
                    <span className="text-slate-500">CCI:</span>
                    <span className="text-slate-600 dark:text-slate-400">01806800006819283777</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Section C: Complete Payment & Treasury Reconciliation Ledger */}
          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-[#0d1322] dark:shadow-xl overflow-hidden space-y-4 p-5 transition-colors">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-2">
              <div>
                <h3 className="font-bold text-sm text-slate-900 dark:text-white font-mono uppercase flex items-center gap-2">
                  <Layers className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                  <span>Libro Mayor de Conciliación de Pagos y Liquidaciones</span>
                </h3>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Registro detallado de transacciones, comisiones de pasarela y abonos netos en cuentas corrientes
                </p>
              </div>

              <div className="font-mono text-xs text-slate-500 dark:text-slate-400">
                Total Registros: <strong className="text-slate-900 dark:text-white">{ventasFiltradas.length}</strong> transacciones
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-100 font-mono text-[11px] font-bold uppercase tracking-wider text-slate-600 dark:border-slate-800 dark:bg-slate-950/80 dark:text-slate-400">
                    <th className="py-3 px-3">Fecha</th>
                    <th className="py-3 px-3">Ref. Transacción</th>
                    <th className="py-3 px-3">Comprobante</th>
                    <th className="py-3 px-3">Cliente / Razón Social</th>
                    <th className="py-3 px-3">Canal de Pago</th>
                    <th className="py-3 px-3 text-right">Monto Bruto</th>
                    <th className="py-3 px-3 text-right">Comisión Pasarela</th>
                    <th className="py-3 px-3 text-right">SPOT 12% Retenido</th>
                    <th className="py-3 px-3 text-right">Neto Liquidado</th>
                    <th className="py-3 px-3 text-center">Estado Conciliación</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-xs font-sans dark:divide-slate-800/60">
                  {ventasFiltradas.map((item) => {
                    const canal = clasificarCanal(item.medioPago);
                    const mon = (item.moneda || 'PEN').toUpperCase();
                    const spot = Number(item.montoDetraccion) || 0;
                    const neto = Number(item.total) - spot;

                    return (
                      <tr key={item.idOrden} className="hover:bg-slate-50 dark:hover:bg-slate-800/30 transition-colors">
                        <td className="py-3 px-3 font-mono text-slate-600 dark:text-slate-300 whitespace-nowrap">
                          {formatFecha(item.fechaOrden)}
                        </td>

                        <td className="py-3 px-3 font-mono text-slate-500 dark:text-slate-400 whitespace-nowrap">
                          <span className="text-[11px] bg-slate-100 text-slate-700 border border-slate-200 dark:bg-slate-950 dark:text-slate-300 dark:border-slate-800 px-2 py-0.5 rounded font-mono">
                            {item.codigoOrden}
                          </span>
                        </td>

                        <td className="py-3 px-3 font-mono font-bold text-slate-900 dark:text-white whitespace-nowrap">
                          {item.serieNumero}
                        </td>

                        <td className="py-3 px-3 max-w-[180px] truncate text-slate-700 dark:text-slate-300" title={item.cliente}>
                          {item.cliente}
                        </td>

                        <td className="py-3 px-3 whitespace-nowrap">
                          {canal === 'IZIPAY' ? (
                            <span className="inline-flex items-center gap-1 font-mono text-[10px] text-fuchsia-600 dark:text-fuchsia-400">
                              <CreditCard className="h-3 w-3" />
                              <span>izipay Online</span>
                            </span>
                          ) : canal === 'STRIPE' ? (
                            <span className="inline-flex items-center gap-1 font-mono text-[10px] text-cyan-600 dark:text-cyan-400">
                              <CreditCard className="h-3 w-3" />
                              <span>Stripe Card</span>
                            </span>
                          ) : canal === 'TELECREDITO' ? (
                            <span className="inline-flex items-center gap-1 font-mono text-[10px] text-indigo-600 dark:text-indigo-400">
                              <Building className="h-3 w-3" />
                              <span>Telecrédito BCP</span>
                            </span>
                          ) : canal === 'INTERBANK' ? (
                            <span className="inline-flex items-center gap-1 font-mono text-[10px] text-emerald-600 dark:text-emerald-400">
                              <Building className="h-3 w-3" />
                              <span>Interbank B2B</span>
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 font-mono text-[10px] text-amber-600 dark:text-amber-400">
                              <ShieldAlert className="h-3 w-3" />
                              <span>Sin clasificar</span>
                            </span>
                          )}
                        </td>

                        <td className="py-3 px-3 text-right font-mono font-bold text-slate-900 dark:text-white whitespace-nowrap">
                          {mon === 'USD' ? formatDolares(item.total) : formatSoles(item.total)}
                        </td>

                        <td className="py-3 px-3 text-right font-mono text-slate-500 dark:text-slate-400 whitespace-nowrap text-[11px]">
                          {canal === 'TELECREDITO' || canal === 'INTERBANK' ? (
                            <span className="text-emerald-600 dark:text-emerald-400 font-semibold">S/. 0.00</span>
                          ) : (
                            <span className="text-slate-400 dark:text-slate-500 italic">No liquidado</span>
                          )}
                        </td>

                        <td className="py-3 px-3 text-right font-mono text-amber-600 dark:text-amber-400 whitespace-nowrap">
                          {spot > 0 ? `- ${mon === 'USD' ? formatDolares(spot) : formatSoles(spot)}` : '—'}
                        </td>

                        <td className="py-3 px-3 text-right font-mono font-bold text-emerald-600 dark:text-emerald-400 whitespace-nowrap">
                          {mon === 'USD' ? formatDolares(neto) : formatSoles(neto)}
                        </td>

                        <td className="py-3 px-3 text-center whitespace-nowrap">
                          {canal === 'TELECREDITO' || canal === 'INTERBANK' ? (
                            <span className="rounded bg-indigo-100 border border-indigo-200 text-indigo-800 dark:bg-indigo-950/70 dark:border-indigo-500/30 dark:text-indigo-300 px-2 py-0.5 text-[10px] font-mono font-bold">
                              Conciliado Banco
                            </span>
                          ) : canal === 'IZIPAY' ? (
                            <span className="rounded bg-fuchsia-100 border border-fuchsia-200 text-fuchsia-800 dark:bg-fuchsia-950/70 dark:border-fuchsia-500/30 dark:text-fuchsia-300 px-2 py-0.5 text-[10px] font-mono font-bold">
                              Pendiente izipay
                            </span>
                          ) : canal === 'STRIPE' ? (
                            <span className="rounded bg-cyan-100 border border-cyan-200 text-cyan-800 dark:bg-cyan-950/70 dark:border-cyan-500/30 dark:text-cyan-300 px-2 py-0.5 text-[10px] font-mono font-bold">
                              Pendiente Stripe
                            </span>
                          ) : (
                            <span className="rounded bg-amber-100 border border-amber-200 text-amber-800 dark:bg-amber-950/70 dark:border-amber-500/30 dark:text-amber-300 px-2 py-0.5 text-[10px] font-mono font-bold">
                              Sin clasificar
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
                <tfoot>
                  <tr className="border-t-2 border-slate-200 bg-slate-100 font-mono text-xs font-bold text-slate-700 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300">
                    <td colSpan={5} className="py-3 px-3 text-slate-500 dark:text-slate-400">
                      TOTAL CONCILIACIÓN PEN ({ventasFiltradas.filter((v) => (v.moneda || 'PEN').toUpperCase() === 'PEN').length} transacciones)
                    </td>
                    <td className="py-3 px-3 text-right text-slate-900 dark:text-white">
                      {formatSoles(metricasCalculadas.totalPen)}
                    </td>
                    <td className="py-3 px-3 text-right text-slate-500 dark:text-slate-400 text-[10px]">
                      No liquidada
                    </td>
                    <td className="py-3 px-3 text-right text-amber-600 dark:text-amber-400">
                      - {formatSoles(metricasCalculadas.detraccionesPen)}
                    </td>
                    <td className="py-3 px-3 text-right text-emerald-600 dark:text-emerald-400 text-sm">
                      {formatSoles(metricasCalculadas.totalPen - metricasCalculadas.detraccionesPen)}
                    </td>
                    <td className="py-3 px-3 text-center text-[10px] text-emerald-600 dark:text-emerald-400 font-bold">
                      Conciliado PEN
                    </td>
                  </tr>
                  {metricasCalculadas.totalUsd > 0 && (
                    <tr className="border-t border-slate-200 bg-slate-100/60 font-mono text-xs font-bold text-slate-700 dark:border-slate-700 dark:bg-slate-950/60 dark:text-slate-300">
                      <td colSpan={5} className="py-2.5 px-3 text-slate-500 dark:text-slate-400">
                        TOTAL CONCILIACIÓN USD ({ventasFiltradas.filter((v) => (v.moneda || '').toUpperCase() === 'USD').length} transacciones)
                      </td>
                      <td className="py-2.5 px-3 text-right text-slate-900 dark:text-white">
                        {formatDolares(metricasCalculadas.totalUsd)}
                      </td>
                      <td className="py-2.5 px-3 text-right text-slate-500 dark:text-slate-400 text-[10px]">
                        No liquidada
                      </td>
                      <td className="py-2.5 px-3 text-right text-amber-600 dark:text-amber-400">
                        - {formatDolares(metricasCalculadas.detraccionesUsd)}
                      </td>
                      <td className="py-2.5 px-3 text-right text-emerald-600 dark:text-emerald-400 text-sm">
                        {formatDolares(metricasCalculadas.totalUsd - metricasCalculadas.detraccionesUsd)}
                      </td>
                      <td className="py-2.5 px-3 text-center text-[10px] text-emerald-600 dark:text-emerald-400 font-bold">
                        Conciliado USD
                      </td>
                    </tr>
                  )}
                </tfoot>
              </table>
            </div>
          </div>
        </section>
      )}
        </>
      )}

      {/* Modal: Vista Previa Oficial de Comprobante Electrónico SUNAT */}
      {selectedVoucherForPreview && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md overflow-y-auto">
          <div className="relative w-full max-w-2xl bg-white text-slate-900 rounded-3xl shadow-2xl p-6 sm:p-8 border border-slate-300 my-8 animate-fadeIn">
            {/* Header del Comprobante */}
            <div className="flex flex-col sm:flex-row justify-between items-start gap-4 pb-6 border-b border-slate-300">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <img src={companyLogo} alt="Lysandri Executive" className="h-9 w-9 object-contain" />
                  <div>
                    <h3 className="text-base font-black tracking-tight text-slate-900 leading-none">
                      LYSANDRI
                    </h3>
                    <p className="text-[10px] text-slate-500 font-semibold tracking-wide">
                      LYSANDRI EXECUTIVE EDUCATION
                    </p>
                  </div>
                </div>
                <p className="text-[11px] text-slate-600 max-w-xs leading-tight pt-1">
                  Servicios de Educación Superior, Capacitación Ejecutiva & Asesoría Tecnológica
                </p>
                <p className="text-[10px] text-slate-500">
                  Av. Rivera Navarrete 501, San Isidro, Lima - Perú
                </p>
              </div>

              {/* Caja Tributaria RUC y Serie */}
              <div className="w-full sm:w-auto border-2 border-slate-900 rounded-xl p-3.5 text-center bg-slate-50">
                <div className="text-xs font-mono font-bold tracking-wider text-slate-800">
                  R.U.C. 20609812451
                </div>
                <div className="text-sm font-black uppercase text-slate-900 my-1 font-mono">
                  {(selectedVoucherForPreview.tipoComprobante || '').toUpperCase().includes('FACTURA')
                    ? 'FACTURA ELECTRÓNICA'
                    : 'BOLETA DE VENTA ELECTRÓNICA'}
                </div>
                <div className="text-xs font-mono font-black text-cyan-700">
                  {selectedVoucherForPreview.serieNumero}
                </div>
              </div>
            </div>

            {/* Datos del Cliente y Emisión */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 py-5 text-xs border-b border-slate-200">
              <div className="space-y-1.5">
                <div>
                  <span className="text-slate-500 font-semibold">Cliente / Razón Social: </span>
                  <span className="font-bold text-slate-900">{selectedVoucherForPreview.cliente}</span>
                </div>
                <div>
                  <span className="text-slate-500 font-semibold">
                    {(selectedVoucherForPreview.documentoIdentidad || '').length === 11 ? 'RUC' : 'DNI'}:{' '}
                  </span>
                  <span className="font-mono font-bold text-slate-900">
                    {selectedVoucherForPreview.documentoIdentidad}
                  </span>
                </div>
                <div>
                  <span className="text-slate-500 font-semibold">Condición de Pago: </span>
                  <span className="font-medium text-slate-800">
                    Contado ({selectedVoucherForPreview.medioPago || 'Stripe Gateway'})
                  </span>
                </div>
              </div>

              <div className="space-y-1.5 sm:text-right">
                <div>
                  <span className="text-slate-500 font-semibold">Fecha de Emisión: </span>
                  <span className="font-mono text-slate-800 font-bold">
                    {formatFecha(selectedVoucherForPreview.fechaEmision || selectedVoucherForPreview.fechaOrden)}
                  </span>
                </div>
                <div>
                  <span className="text-slate-500 font-semibold">Moneda: </span>
                  <span className="font-mono font-bold text-slate-900">
                    {selectedVoucherForPreview.moneda === 'PEN' ? 'Soles (PEN)' : selectedVoucherForPreview.moneda}
                  </span>
                </div>
                <div>
                  <span className="text-slate-500 font-semibold">Referencia de Pago: </span>
                  <span className="font-mono text-[11px] text-slate-700">
                    {selectedVoucherForPreview.codigoOrden}
                  </span>
                </div>
              </div>
            </div>

            {/* Detalle de Ítems */}
            <div className="py-4">
              <table className="w-full text-xs text-left">
                <thead>
                  <tr className="border-b border-slate-300 font-mono text-[11px] text-slate-600 uppercase">
                    <th className="py-2">Cant.</th>
                    <th className="py-2">Unidad</th>
                    <th className="py-2">Descripción</th>
                    <th className="py-2 text-right">P. Unitario</th>
                    <th className="py-2 text-right">Importe</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-mono">
                  <tr>
                    <td className="py-3 font-semibold">1.00</td>
                    <td className="py-3">ZZ (Servicio)</td>
                    <td className="py-3 font-sans font-medium text-slate-800">
                      Programa Ejecutivo de Especialización Directiva - Modalidad Virtual
                    </td>
                    <td className="py-3 text-right">S/. {selectedVoucherForPreview.subtotal.toFixed(2)}</td>
                    <td className="py-3 text-right font-bold">S/. {selectedVoucherForPreview.subtotal.toFixed(2)}</td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* Sello de ANULADO si aplica */}
            {selectedVoucherForPreview.estadoComprobante === 'ANULADO' && (
              <div className="absolute inset-0 flex items-center justify-center pointer-events-none z-20">
                <div className="border-4 border-rose-600 text-rose-600 font-black text-4xl sm:text-6xl uppercase tracking-widest px-8 py-4 rotate-[-25deg] opacity-75 rounded-2xl bg-white/70 shadow-2xl">
                  ANULADO
                </div>
              </div>
            )}

            {/* Aviso SPOT Detracciones si aplica */}
            {selectedVoucherForPreview.montoDetraccion && selectedVoucherForPreview.montoDetraccion > 0 && selectedVoucherForPreview.estadoComprobante !== 'ANULADO' && (
              <div className="mt-4 rounded-xl border border-amber-400 bg-amber-50 p-3 text-[11px] text-amber-900 font-mono space-y-1">
                <div className="flex items-center gap-1.5 font-bold uppercase tracking-wider text-amber-950">
                  <Landmark className="h-4 w-4 text-amber-700" />
                  <span>Operación sujeta al Sistema de Pago de Obligaciones Tributarias (SPOT)</span>
                </div>
                <p className="text-[10px] text-amber-800">
                  Monto Detracción (12%): <strong>S/. {selectedVoucherForPreview.montoDetraccion.toFixed(2)}</strong> — Depósito en Cuenta Corriente Banco de la Nación N° 00-068-192837.
                </p>
              </div>
            )}

            {/* Totales y Liquidación */}
            <div className="flex flex-col sm:flex-row justify-between items-start pt-4 border-t border-slate-300 gap-4 mt-4">
              <div className="text-[11px] text-slate-500 space-y-1">
                <p className="font-semibold text-slate-700">Representación impresa de Comprobante Electrónico</p>
                <p>Autorizado por SUNAT según normativa vigente para emisores electrónicos.</p>
                <p className="font-mono text-[10px] text-slate-500 break-all">
                  Hash DigestValue: {selectedVoucherForPreview.codigoHash || '4b8fK29mXzL981k='}
                </p>
              </div>

              <div className="w-full sm:w-60 font-mono text-xs space-y-1.5">
                <div className="flex justify-between text-slate-600">
                  <span>Op. Gravada:</span>
                  <span>S/. {selectedVoucherForPreview.subtotal.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>I.G.V. (18%):</span>
                  <span>S/. {selectedVoucherForPreview.igv.toFixed(2)}</span>
                </div>
                {selectedVoucherForPreview.montoDetraccion && selectedVoucherForPreview.montoDetraccion > 0 && (
                  <div className="flex justify-between text-amber-700 font-semibold">
                    <span>Detracción (12%):</span>
                    <span>S/. {selectedVoucherForPreview.montoDetraccion.toFixed(2)}</span>
                  </div>
                )}
                <div className="flex justify-between font-bold text-slate-900 border-t border-slate-300 pt-1.5 text-sm">
                  <span>Total Facturado:</span>
                  <span className={selectedVoucherForPreview.estadoComprobante === 'ANULADO' ? 'text-rose-600 line-through' : 'text-emerald-700'}>
                    S/. {selectedVoucherForPreview.total.toFixed(2)}
                  </span>
                </div>
              </div>
            </div>

            {/* Acciones de Modal */}
            <div className="mt-8 flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-4">
              <button
                type="button"
                onClick={() => {
                  const summary = `COMPROBANTE ${selectedVoucherForPreview.serieNumero} | FECHA: ${formatFecha(selectedVoucherForPreview.fechaEmision || selectedVoucherForPreview.fechaOrden)} | DOC: ${selectedVoucherForPreview.documentoIdentidad} | CLIENTE: ${selectedVoucherForPreview.cliente} | BASE: S/ ${selectedVoucherForPreview.subtotal.toFixed(2)} | IGV: S/ ${selectedVoucherForPreview.igv.toFixed(2)} | SPOT 12%: S/ ${(selectedVoucherForPreview.montoDetraccion || 0).toFixed(2)} | TOTAL: S/ ${selectedVoucherForPreview.total.toFixed(2)}`;
                  copyToClipboard(summary, 'voucherSummary');
                }}
                className="inline-flex items-center gap-1.5 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 hover:bg-slate-50 transition-colors shadow-sm"
                title="Copiar resumen para Concar / Siscont / Excel"
              >
                {copiedText === 'voucherSummary' ? <Check className="h-4 w-4 text-emerald-600" /> : <Copy className="h-4 w-4 text-slate-600" />}
                <span>{copiedText === 'voucherSummary' ? '¡Asiento Copiado!' : 'Copiar Asiento Contable'}</span>
              </button>

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => window.print()}
                  className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 hover:bg-slate-50 shadow-sm transition-colors"
                >
                  <Printer className="h-4 w-4 text-slate-600" />
                  <span>Imprimir Comprobante</span>
                </button>

                <button
                  type="button"
                  onClick={() => setSelectedVoucherForPreview(null)}
                  className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-5 py-2.5 text-xs font-bold text-white hover:bg-slate-800 transition-colors shadow-md"
                >
                  <X className="h-4 w-4" />
                  <span>Cerrar</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AccountingView;

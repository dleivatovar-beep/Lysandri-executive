import React, { useEffect, useState, useMemo } from 'react';
import {
  Lock,
  ShieldCheck,
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
  Copy,
  Check,
  Eye,
  EyeOff,
  LogOut,
} from 'lucide-react';
import {
  accountingService,
  ReporteVentasResponse,
} from '../services/accountingService';
import companyLogo from '../assets/mi-logo.png';

export const AccountingView: React.FC = () => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  const [pinInput, setPinInput] = useState<string>('');
  const [showPin, setShowPin] = useState<boolean>(false);
  const [isVerifying, setIsVerifying] = useState<boolean>(false);
  const [authError, setAuthError] = useState<string>('');

  const [data, setData] = useState<ReporteVentasResponse | null>(null);
  const [isLoadingData, setIsLoadingData] = useState<boolean>(false);
  const [dataError, setDataError] = useState<string>('');
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedTipo, setSelectedTipo] = useState<'TODOS' | 'BOLETA' | 'FACTURA'>('TODOS');
  const [selectedPeriodo, setSelectedPeriodo] = useState<string>('TODOS');

  useEffect(() => {
    const storedPin = accountingService.getStoredAuditPin();
    if (storedPin) {
      void cargarDatosContables(storedPin);
    }
  }, []);

  const cargarDatosContables = async (pin: string) => {
    setIsVerifying(true);
    setIsLoadingData(true);
    setAuthError('');
    setDataError('');

    try {
      const response = await accountingService.fetchReporteVentas(pin);
      accountingService.setStoredAuditPin(pin);
      setIsAuthenticated(true);
      setData(response);
    } catch (err: any) {
      if (err.response?.status === 401 || err.response?.status === 403) {
        setAuthError('Acceso no autorizado. El PIN ingresado no es válido.');
        accountingService.clearStoredAuditPin();
        setIsAuthenticated(false);
      } else {
        setDataError(
          err.response?.data?.mensaje ||
            err.message ||
            'No se pudo conectar con el servicio contable.'
        );
      }
    } finally {
      setIsVerifying(false);
      setIsLoadingData(false);
    }
  };

  const handlePinSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!pinInput.trim()) {
      setAuthError('Ingresa el PIN de acceso.');
      return;
    }
    void cargarDatosContables(pinInput.trim());
  };

  const handleLogout = () => {
    accountingService.clearStoredAuditPin();
    setIsAuthenticated(false);
    setData(null);
    setPinInput('');
    setAuthError('');
  };

  const handleRefresh = () => {
    const pin = accountingService.getStoredAuditPin() || pinInput;
    if (pin) {
      void cargarDatosContables(pin);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(text);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const formatSoles = (amount?: number | null): string => {
    const val = typeof amount === 'number' ? amount : 0;
    return `S/. ${val.toLocaleString('es-PE', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const formatFecha = (dateString?: string | null): string => {
    if (!dateString) return '—';
    try {
      const d = new Date(dateString);
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

  const periodosDisponibles = useMemo(() => {
    if (!data?.ventasConsolidadas) return [];
    const setPeriodos = new Set<string>();
    data.ventasConsolidadas.forEach((v) => {
      const rawDate = v.fechaEmision || v.fechaOrden;
      if (rawDate) {
        try {
          const d = new Date(rawDate);
          const mes = String(d.getMonth() + 1).padStart(2, '0');
          const anio = d.getFullYear();
          setPeriodos.add(`${anio}-${mes}`);
        } catch {
          // ignore invalid date
        }
      }
    });
    return Array.from(setPeriodos).sort().reverse();
  }, [data]);

  const ventasFiltradas = useMemo(() => {
    if (!data?.ventasConsolidadas) return [];

    return data.ventasConsolidadas.filter((item) => {
      if (selectedTipo !== 'TODOS') {
        const itemTipo = (item.tipoComprobante || '').toUpperCase();
        if (selectedTipo === 'BOLETA' && !itemTipo.includes('BOLETA')) return false;
        if (selectedTipo === 'FACTURA' && !itemTipo.includes('FACTURA')) return false;
      }

      if (selectedPeriodo !== 'TODOS') {
        const rawDate = item.fechaEmision || item.fechaOrden;
        if (rawDate) {
          const d = new Date(rawDate);
          const itemPeriodo = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
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
        if (!matchesCliente && !matchesDoc && !matchesSerie && !matchesCodigo) {
          return false;
        }
      }

      return true;
    });
  }, [data, selectedTipo, selectedPeriodo, searchQuery]);

  const metricasCalculadas = useMemo(() => {
    if (!data) {
      return { baseImponible: 0, igv: 0, total: 0 };
    }

    if (selectedTipo === 'TODOS' && selectedPeriodo === 'TODOS' && !searchQuery.trim()) {
      return {
        baseImponible: data.totalBaseImponible || 0,
        igv: data.totalIgvRecaudado || 0,
        total: data.totalVentas || 0,
      };
    }

    let base = 0;
    let igv = 0;
    let total = 0;
    ventasFiltradas.forEach((v) => {
      base += Number(v.subtotal) || 0;
      igv += Number(v.igv) || 0;
      total += Number(v.total) || 0;
    });

    return { baseImponible: base, igv, total };
  }, [data, ventasFiltradas, selectedTipo, selectedPeriodo, searchQuery]);

  const exportarCSV = () => {
    if (!ventasFiltradas || ventasFiltradas.length === 0) {
      alert('No hay registros de ventas para exportar.');
      return;
    }

    const headers = [
      'ID_ORDEN',
      'FECHA_EMISION',
      'TIPO_COMPROBANTE',
      'SERIE_CORRELATIVO',
      'TIPO_DOC_IDENTIDAD',
      'NUMERO_DOC_IDENTIDAD',
      'CLIENTE_O_RAZON_SOCIAL',
      'BASE_IMPONIBLE_PEN',
      'IGV_18_PEN',
      'TOTAL_VENTA_PEN',
      'MONEDA_ORIGINAL',
      'ESTADO_ORDEN',
      'REFERENCIA_STRIPE',
    ];

    const escapeCSV = (val: any) => {
      if (val === null || val === undefined) return '""';
      const str = String(val).replace(/"/g, '""');
      return `"${str}"`;
    };

    const rows = ventasFiltradas.map((item) => {
      const tipoDoc = (item.documentoIdentidad || '').length === 11 ? 'RUC' : 'DNI';
      return [
        escapeCSV(item.idOrden),
        escapeCSV(formatFecha(item.fechaEmision || item.fechaOrden)),
        escapeCSV(item.tipoComprobante),
        escapeCSV(item.serieNumero),
        escapeCSV(tipoDoc),
        escapeCSV(item.documentoIdentidad),
        escapeCSV(item.cliente),
        Number(item.subtotal || 0).toFixed(2),
        Number(item.igv || 0).toFixed(2),
        Number(item.total || 0).toFixed(2),
        escapeCSV(item.moneda || 'PEN'),
        escapeCSV(item.estadoOrden),
        escapeCSV(item.codigoOrden),
      ].join(',');
    });

    const csvContent = '\uFEFF' + [headers.join(','), ...rows].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');

    const fechaHoy = new Date().toISOString().slice(0, 10);
    link.href = url;
    link.setAttribute('download', `Registro_Ventas_Lysandri_Executive_${fechaHoy}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  if (!isAuthenticated) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#050811] px-4">
        <div className="pointer-events-none absolute -left-20 -top-20 h-96 w-96 rounded-full bg-cyan-500/10 blur-[120px]" />
        <div className="pointer-events-none absolute -bottom-20 -right-20 h-96 w-96 rounded-full bg-indigo-500/10 blur-[120px]" />

        <div className="relative w-full max-w-md overflow-hidden rounded-3xl border border-cyan-500/30 bg-[#0a101d] p-8 text-center text-slate-100 shadow-[0_0_80px_rgba(6,182,212,0.15)] backdrop-blur-2xl">
          <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-2xl border border-cyan-500/40 bg-gradient-to-br from-cyan-950/80 to-indigo-950/80 text-cyan-400 shadow-xl shadow-cyan-500/20">
            <Lock className="h-8 w-8" />
          </div>

          <div className="space-y-2">
            <span className="inline-block rounded-full border border-cyan-500/30 bg-cyan-950/70 px-3 py-1 font-mono text-[10px] font-bold uppercase tracking-widest text-cyan-300">
              Auditoría Interna
            </span>
            <h1 className="text-2xl font-black tracking-tight text-white">
              PIN de Auditoría Contable
            </h1>
            <p className="text-xs text-slate-400 max-w-sm mx-auto leading-relaxed">
              Ingresa el PIN de seguridad para acceder al registro de ventas y comprobantes electrónicos.
            </p>
          </div>

          {authError && (
            <div className="mt-5 flex items-start gap-2.5 rounded-xl border border-rose-500/40 bg-rose-950/50 p-3 text-left text-xs text-rose-300 animate-fadeIn">
              <ShieldAlert className="h-4 w-4 shrink-0 text-rose-400 mt-0.5" />
              <span>{authError}</span>
            </div>
          )}

          <form onSubmit={handlePinSubmit} className="mt-6 space-y-4">
            <div className="relative text-left">
              <label className="block text-[11px] font-mono uppercase tracking-wider text-slate-400 mb-1.5 font-semibold">
                PIN de acceso
              </label>
              <div className="relative">
                <input
                  type={showPin ? 'text' : 'password'}
                  required
                  autoFocus
                  value={pinInput}
                  onChange={(e) => setPinInput(e.target.value)}
                  placeholder="••••••••••••"
                  className="w-full rounded-xl border border-slate-700 bg-slate-900/90 px-4 py-3 text-sm font-mono text-white placeholder-slate-600 focus:border-cyan-400 focus:outline-none focus:ring-2 focus:ring-cyan-500/20 transition-all pr-11"
                />
                <button
                  type="button"
                  onClick={() => setShowPin(!showPin)}
                  className="absolute right-3 top-3 text-slate-500 hover:text-slate-300 transition-colors"
                >
                  {showPin ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isVerifying}
              className="w-full flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-cyan-500 bg-size-200 px-6 py-3.5 text-xs font-bold text-white shadow-lg shadow-cyan-500/25 hover:opacity-95 active:scale-[0.98] transition-all disabled:opacity-50"
            >
              {isVerifying ? (
                <>
                  <LoaderCircle className="h-4 w-4 animate-spin" />
                  <span>Verificando...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="h-4 w-4" />
                  <span>Acceder al panel</span>
                </>
              )}
            </button>
          </form>

          <div className="mt-6 border-t border-slate-800/80 pt-4 text-[10px] font-mono text-slate-500 flex items-center justify-center gap-2">
            <Lock className="h-3 w-3" />
            <span>Yunix Ingenieros E.I.R.L.</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#06090f] text-slate-100 p-4 sm:p-6 lg:p-8 space-y-8">
      <header className="rounded-3xl border border-cyan-500/20 bg-gradient-to-r from-slate-900 via-[#0a1122] to-slate-900 p-6 md:p-8 shadow-2xl relative overflow-hidden">
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-cyan-500/10 blur-3xl" />
        <div className="pointer-events-none absolute -left-20 -bottom-20 h-64 w-64 rounded-full bg-indigo-500/10 blur-3xl" />

        <div className="relative z-10 flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2.5">
              <img
                src={companyLogo}
                alt="Lysandri"
                className="h-8 w-8 object-contain drop-shadow-[0_0_8px_rgba(34,211,238,0.4)]"
              />
              <span className="font-mono text-[10px] font-bold uppercase tracking-widest text-cyan-400 bg-cyan-950/70 border border-cyan-500/30 px-3 py-1 rounded-full">
                Administración
              </span>
              <span className="font-mono text-[10px] text-emerald-400 font-bold bg-emerald-950/60 border border-emerald-500/30 px-2.5 py-1 rounded-full flex items-center gap-1">
                <span className="h-2 w-2 rounded-full bg-emerald-400" />
                Sesión activa
              </span>
            </div>

            <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
              Registro de Ventas e Ingresos - Lysandri Executive
            </h1>
            <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
              Comprobantes emitidos por <strong>Yunix Ingenieros E.I.R.L.</strong> y pagos procesados vía Stripe.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2.5">
            <button
              type="button"
              onClick={handleRefresh}
              disabled={isLoadingData}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-700 bg-slate-800/80 px-4 py-2.5 text-xs font-semibold text-slate-200 hover:border-cyan-400 hover:text-white transition-all shadow-sm active:scale-95"
            >
              <RefreshCw className={`h-4 w-4 text-cyan-400 ${isLoadingData ? 'animate-spin' : ''}`} />
              <span>Actualizar</span>
            </button>

            <button
              type="button"
              onClick={exportarCSV}
              className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 px-4 py-2.5 text-xs font-bold text-white shadow-lg shadow-emerald-600/20 hover:opacity-95 transition-all active:scale-95"
            >
              <FileSpreadsheet className="h-4 w-4" />
              <span>Exportar a CSV / Excel</span>
            </button>

            <button
              type="button"
              onClick={handleLogout}
              title="Cerrar sesión"
              className="inline-flex items-center gap-2 rounded-xl border border-rose-500/30 bg-rose-950/30 px-3.5 py-2.5 text-xs font-semibold text-rose-300 hover:bg-rose-900/50 hover:border-rose-400 transition-all active:scale-95"
            >
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Cerrar sesión</span>
            </button>
          </div>
        </div>
      </header>

      {dataError && (
        <div className="flex items-start gap-2.5 rounded-xl border border-amber-500/40 bg-amber-950/40 p-4 text-xs text-amber-300">
          <ShieldAlert className="h-5 w-5 shrink-0 text-amber-400 mt-0.5" />
          <span>{dataError}</span>
        </div>
      )}

      {/* Resumen métrico */}
      <section className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
        <div className="relative overflow-hidden rounded-2xl border border-cyan-500/25 bg-slate-900/90 p-6 shadow-xl backdrop-blur-xl transition-all duration-300 hover:border-cyan-400/50 hover:shadow-cyan-500/10">
          <div className="flex items-center justify-between">
            <span className="font-mono text-xs font-bold uppercase tracking-wider text-slate-400">
              Base Imponible Acumulada (S/.)
            </span>
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-400">
              <TrendingUp className="h-5 w-5" />
            </div>
          </div>
          <div className="mt-4">
            <div className="text-2xl sm:text-3xl font-black text-cyan-300 tracking-tight font-mono">
              {formatSoles(metricasCalculadas.baseImponible)}
            </div>
            <p className="mt-1 text-[11px] text-slate-400">
              Subtotal facturable neto
            </p>
          </div>
        </div>

        <div className="relative overflow-hidden rounded-2xl border border-indigo-500/25 bg-slate-900/90 p-6 shadow-xl backdrop-blur-xl transition-all duration-300 hover:border-indigo-400/50 hover:shadow-indigo-500/10">
          <div className="flex items-center justify-between">
            <span className="font-mono text-xs font-bold uppercase tracking-wider text-slate-400">
              IGV Recaudado 18% (S/.)
            </span>
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-400">
              <Receipt className="h-5 w-5" />
            </div>
          </div>
          <div className="mt-4">
            <div className="text-2xl sm:text-3xl font-black text-indigo-300 tracking-tight font-mono">
              {formatSoles(metricasCalculadas.igv)}
            </div>
            <p className="mt-1 text-[11px] text-slate-400">
              Impuesto aplicado a comprobantes
            </p>
          </div>
        </div>

        <div className="relative overflow-hidden rounded-2xl border border-emerald-500/25 bg-slate-900/90 p-6 shadow-xl backdrop-blur-xl transition-all duration-300 hover:border-emerald-400/50 hover:shadow-emerald-500/10 sm:col-span-2 lg:col-span-1">
          <div className="flex items-center justify-between">
            <span className="font-mono text-xs font-bold uppercase tracking-wider text-slate-400">
              Ventas Brutas Totales (S/.)
            </span>
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-400">
              <DollarSign className="h-5 w-5" />
            </div>
          </div>
          <div className="mt-4">
            <div className="text-2xl sm:text-3xl font-black text-emerald-400 tracking-tight font-mono">
              {formatSoles(metricasCalculadas.total)}
            </div>
            <p className="mt-1 text-[11px] text-slate-400">
              Monto total procesado
            </p>
          </div>
        </div>
      </section>

      {/* Filtros y búsqueda */}
      <section className="rounded-2xl border border-slate-800 bg-slate-900/80 p-5 space-y-4">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="relative w-full lg:max-w-md">
            <Search className="absolute left-3.5 top-3 h-4 w-4 text-slate-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Buscar por cliente, documento o referencia..."
              className="w-full rounded-xl border border-slate-700 bg-slate-950/80 pl-10 pr-4 py-2.5 text-xs text-white placeholder-slate-500 focus:border-cyan-400 focus:outline-none focus:ring-1 focus:ring-cyan-400 transition-colors"
            />
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-1 rounded-xl border border-slate-700 bg-slate-950/80 p-1">
              {(['TODOS', 'BOLETA', 'FACTURA'] as const).map((tipo) => (
                <button
                  key={tipo}
                  type="button"
                  onClick={() => setSelectedTipo(tipo)}
                  className={`rounded-lg px-3 py-1.5 font-mono text-[11px] font-bold transition-all ${
                    selectedTipo === tipo
                      ? 'bg-gradient-to-r from-cyan-500 to-indigo-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  {tipo === 'TODOS' ? 'Todos' : tipo === 'BOLETA' ? 'Boletas' : 'Facturas'}
                </button>
              ))}
            </div>

            <div className="flex items-center gap-2">
              <Calendar className="h-4 w-4 text-cyan-400 shrink-0" />
              <select
                value={selectedPeriodo}
                onChange={(e) => setSelectedPeriodo(e.target.value)}
                className="rounded-xl border border-slate-700 bg-slate-950/80 px-3 py-2 text-xs font-mono text-slate-200 focus:border-cyan-400 focus:outline-none"
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

        <div className="flex items-center justify-between text-xs text-slate-400 border-t border-slate-800 pt-3">
          <span>
            Registros visibles: <strong>{ventasFiltradas.length}</strong> de{' '}
            <strong>{data?.ventasConsolidadas?.length || 0}</strong>
          </span>
          {(searchQuery || selectedTipo !== 'TODOS' || selectedPeriodo !== 'TODOS') && (
            <button
              type="button"
              onClick={() => {
                setSearchQuery('');
                setSelectedTipo('TODOS');
                setSelectedPeriodo('TODOS');
              }}
              className="text-cyan-400 hover:underline font-semibold text-xs"
            >
              Limpiar filtros
            </button>
          )}
        </div>
      </section>

      {/* Tabla */}
      <section className="overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/90 shadow-2xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/90 font-mono text-[11px] font-bold uppercase tracking-wider text-slate-400">
                <th className="py-4 px-4 sm:px-6">Fecha</th>
                <th className="py-4 px-4">Comprobante</th>
                <th className="py-4 px-4">Cliente</th>
                <th className="py-4 px-4">Documento</th>
                <th className="py-4 px-4 text-right">Subtotal</th>
                <th className="py-4 px-4 text-right">IGV</th>
                <th className="py-4 px-4 text-right">Total</th>
                <th className="py-4 px-4 sm:px-6 text-center">Referencia Stripe</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/80 text-xs">
              {isLoadingData ? (
                <tr>
                  <td colSpan={8} className="py-16 text-center text-slate-400">
                    <LoaderCircle className="h-8 w-8 animate-spin mx-auto text-cyan-400 mb-2" />
                    <span>Cargando información...</span>
                  </td>
                </tr>
              ) : ventasFiltradas.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-16 text-center text-slate-400 space-y-2">
                    <FileText className="h-10 w-10 text-slate-600 mx-auto" />
                    <p className="font-semibold text-sm text-slate-300">
                      No hay registros para mostrar
                    </p>
                    <p className="text-xs text-slate-500 max-w-sm mx-auto">
                      Modifica los filtros de búsqueda o de periodo.
                    </p>
                  </td>
                </tr>
              ) : (
                ventasFiltradas.map((venta) => {
                  const esFactura = (venta.tipoComprobante || '').toUpperCase().includes('FACTURA');
                  return (
                    <tr
                      key={venta.idOrden}
                      className="hover:bg-slate-800/40 transition-colors group"
                    >
                      <td className="py-4 px-4 sm:px-6 font-mono text-slate-300 whitespace-nowrap">
                        {formatFecha(venta.fechaEmision || venta.fechaOrden)}
                      </td>

                      <td className="py-4 px-4 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <span
                            className={`rounded-md px-2 py-0.5 font-mono text-[10px] font-bold ${
                              esFactura
                                ? 'bg-indigo-950/80 border border-indigo-500/40 text-indigo-300'
                                : 'bg-cyan-950/80 border border-cyan-500/40 text-cyan-300'
                            }`}
                          >
                            {esFactura ? 'FACTURA' : 'BOLETA'}
                          </span>
                          <span className="font-mono font-bold text-white">
                            {venta.serieNumero || '—'}
                          </span>
                        </div>
                      </td>

                      <td className="py-4 px-4">
                        <div className="flex items-center gap-2">
                          {esFactura ? (
                            <Building className="h-3.5 w-3.5 text-indigo-400 shrink-0" />
                          ) : (
                            <User className="h-3.5 w-3.5 text-cyan-400 shrink-0" />
                          )}
                          <span className="font-medium text-slate-200 max-w-[200px] truncate block" title={venta.cliente}>
                            {venta.cliente || '—'}
                          </span>
                        </div>
                      </td>

                      <td className="py-4 px-4 font-mono text-slate-300 whitespace-nowrap">
                        <span className="rounded bg-slate-950 px-2 py-1 border border-slate-800">
                          {venta.documentoIdentidad || '—'}
                        </span>
                      </td>

                      <td className="py-4 px-4 text-right font-mono text-slate-300 whitespace-nowrap">
                        {formatSoles(venta.subtotal)}
                      </td>

                      <td className="py-4 px-4 text-right font-mono text-indigo-300 whitespace-nowrap">
                        {formatSoles(venta.igv)}
                      </td>

                      <td className="py-4 px-4 text-right font-mono font-bold text-emerald-400 whitespace-nowrap">
                        {formatSoles(venta.total)}
                      </td>

                      <td className="py-4 px-4 sm:px-6 text-center whitespace-nowrap">
                        <button
                          type="button"
                          onClick={() => copyToClipboard(venta.codigoOrden)}
                          title="Copiar referencia"
                          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-950/70 px-2.5 py-1 font-mono text-[11px] text-slate-400 hover:border-cyan-400 hover:text-cyan-300 transition-colors"
                        >
                          <span>{venta.codigoOrden ? `${venta.codigoOrden.substring(0, 10)}...` : '—'}</span>
                          {copiedId === venta.codigoOrden ? (
                            <Check className="h-3.5 w-3.5 text-emerald-400" />
                          ) : (
                            <Copy className="h-3.5 w-3.5 opacity-60 group-hover:opacity-100" />
                          )}
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
};

export default AccountingView;

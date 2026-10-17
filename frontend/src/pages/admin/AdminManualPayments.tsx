import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CreditCard,
  Search,
  RefreshCw,
  CheckCircle2,
  Clock,
  AlertCircle,
  Copy,
  Check,
  ShieldCheck,
  X,
  LoaderCircle,
  Banknote,
  GraduationCap,
} from 'lucide-react';
import { storeService, OrdenResponse } from '../../services/storeService';
import { getApiErrorMessage } from '../../services/authService';

type FilterStatus = 'TODAS' | 'PENDIENTE_PAGO' | 'PAGADO' | 'CANCELADO';

export const AdminManualPayments: React.FC = () => {
  const [orders, setOrders] = useState<OrdenResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<FilterStatus>('PENDIENTE_PAGO');

  // Modal de aprobación
  const [selectedOrderToApprove, setSelectedOrderToApprove] = useState<OrdenResponse | null>(null);
  const [inputNumeroOperacion, setInputNumeroOperacion] = useState('');
  const [inputMetodoPago, setInputMetodoPago] = useState('YAPE');
  const [inputNotasAdmin, setInputNotasAdmin] = useState('');
  const [isApproving, setIsApproving] = useState(false);
  const [approvalSuccessOrder, setApprovalSuccessOrder] = useState<OrdenResponse | null>(null);

  // Copiado temporal
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const loadOrders = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      const data = await storeService.listarOrdenes(statusFilter, search.trim() || undefined);
      setOrders(data);
    } catch (err: any) {
      setErrorMessage(getApiErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter, search]);

  useEffect(() => {
    void loadOrders();
  }, [loadOrders]);

  const handleOpenApproveModal = (orden: OrdenResponse) => {
    setSelectedOrderToApprove(orden);
    setInputNumeroOperacion(orden.numeroOperacion || '');
    setInputMetodoPago(orden.metodoPago || 'YAPE');
    setInputNotasAdmin(orden.notasAdmin || 'Verificación manual de voucher/constancia conforme');
    setApprovalSuccessOrder(null);
  };

  const handleConfirmApproval = async () => {
    if (!selectedOrderToApprove) return;
    setIsApproving(true);
    setErrorMessage('');

    try {
      const updated = await storeService.confirmarPagoManual(selectedOrderToApprove.idOrden, {
        numeroOperacion: inputNumeroOperacion.trim() || undefined,
        metodoPago: inputMetodoPago,
        notasAdmin: inputNotasAdmin.trim() || undefined,
      });

      setApprovalSuccessOrder(updated);
      // Actualizar listado local
      setOrders((prev) =>
        prev.map((o) => (o.idOrden === updated.idOrden ? { ...o, ...updated, estadoOrden: 'PAGADO', moodleMatriculaSincronizada: true } : o))
      );
    } catch (err: any) {
      setErrorMessage(getApiErrorMessage(err));
    } finally {
      setIsApproving(false);
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2500);
  };

  // Métricas calculadas
  const metrics = useMemo(() => {
    const pendientes = orders.filter((o) => o.estadoOrden === 'PENDIENTE_PAGO');
    const pagadas = orders.filter((o) => o.estadoOrden === 'PAGADO');
    const totalRecaudado = pagadas.reduce((acc, curr) => acc + (Number(curr.total) || 0), 0);

    return {
      totalPendientes: pendientes.length,
      totalPagadas: pagadas.length,
      totalRecaudado,
    };
  }, [orders]);

  const filteredOrders = useMemo(() => {
    return orders.filter((o) => {
      if (statusFilter !== 'TODAS' && o.estadoOrden !== statusFilter) return false;
      if (!search.trim()) return true;
      const q = search.toLowerCase();
      const codigo = (o.codigoOrden || '').toLowerCase();
      const email = (o.usuario?.email || '').toLowerCase();
      const nombre = `${o.usuario?.nombres || ''} ${o.usuario?.apellidos || ''}`.toLowerCase();
      const facturacion = (o.nombreFacturacion || '').toLowerCase();
      const operacion = (o.numeroOperacion || '').toLowerCase();
      const doc = (o.numeroDocumentoCliente || '').toLowerCase();
      return (
        codigo.includes(q) ||
        email.includes(q) ||
        nombre.includes(q) ||
        facturacion.includes(q) ||
        operacion.includes(q) ||
        doc.includes(q)
      );
    });
  }, [orders, statusFilter, search]);

  return (
    <section className="space-y-6">
      {/* Cabecera */}
      <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-7 dark:border-slate-800 dark:bg-[#0c111a] md:p-9">
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-cyan-500/10 blur-3xl" />

        <div className="relative flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-cyan-500/20 bg-cyan-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-cyan-600 dark:text-cyan-400">
              <Banknote className="h-3 w-3" />
              Gestión Financiera & Aprovisionamiento
            </span>

            <h1 className="mt-4 text-3xl font-black text-slate-950 dark:text-white">
              Aprobación de Pagos Manuales
            </h1>

            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">
              Valida constancias y números de operación de transferencias bancarias, Yape y Plin.
              Al aprobar, el sistema sincroniza automáticamente las credenciales con el Campus Moodle,
              emite el comprobante tributario y envía los accesos al ejecutivo.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void loadOrders()}
              disabled={isLoading}
              className="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-xs font-bold text-slate-700 shadow-sm transition hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200"
            >
              <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
              <span>Actualizar</span>
            </button>
          </div>
        </div>
      </div>

      {/* Tarjetas de Métricas */}
      <div className="grid gap-4 sm:grid-cols-3">
        <article className="rounded-2xl border border-amber-500/20 bg-amber-500/5 p-5 dark:border-amber-500/10 dark:bg-amber-500/[0.03]">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-bold text-amber-700 dark:text-amber-400">Pendientes de Aprobación</p>
              <p className="mt-2 font-mono text-3xl font-black text-slate-950 dark:text-white">
                {metrics.totalPendientes}
              </p>
            </div>
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-amber-500/10 text-amber-600 dark:text-amber-400">
              <Clock className="h-5 w-5" />
            </span>
          </div>
        </article>

        <article className="rounded-2xl border border-emerald-500/20 bg-emerald-500/5 p-5 dark:border-emerald-500/10 dark:bg-emerald-500/[0.03]">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-bold text-emerald-700 dark:text-emerald-400">Órdenes Aprobadas</p>
              <p className="mt-2 font-mono text-3xl font-black text-slate-950 dark:text-white">
                {metrics.totalPagadas}
              </p>
            </div>
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
              <CheckCircle2 className="h-5 w-5" />
            </span>
          </div>
        </article>

        <article className="rounded-2xl border border-cyan-500/20 bg-cyan-500/5 p-5 dark:border-cyan-500/10 dark:bg-cyan-500/[0.03]">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-xs font-bold text-cyan-700 dark:text-cyan-400">Total Liquidado</p>
              <p className="mt-2 font-mono text-3xl font-black text-slate-950 dark:text-white">
                ${metrics.totalRecaudado.toLocaleString('en-US', { minimumFractionDigits: 2 })}
              </p>
            </div>
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-600 dark:text-cyan-400">
              <CreditCard className="h-5 w-5" />
            </span>
          </div>
        </article>
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-[#0c111a] md:flex-row md:items-center md:justify-between">
        <div className="flex flex-wrap items-center gap-1.5">
          {(
            [
              { value: 'PENDIENTE_PAGO', label: 'Pendientes' },
              { value: 'PAGADO', label: 'Pagadas' },
              { value: 'TODAS', label: 'Todas' },
            ] as const
          ).map((tab) => (
            <button
              key={tab.value}
              type="button"
              onClick={() => setStatusFilter(tab.value)}
              className={`rounded-xl px-4 py-2 text-xs font-bold transition ${
                statusFilter === tab.value
                  ? 'bg-gradient-to-r from-cyan-500 to-indigo-600 text-white shadow-md shadow-cyan-500/20'
                  : 'text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-900'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="relative flex-1 md:max-w-xs">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por código, email, operación..."
            className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50 pl-9 pr-3 text-xs outline-none transition focus:border-cyan-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
          />
        </div>
      </div>

      {/* Mensajes de error */}
      {errorMessage && (
        <div className="flex items-center gap-3 rounded-2xl border border-rose-500/20 bg-rose-500/10 p-4 text-xs font-semibold text-rose-600 dark:text-rose-400">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Lista de Órdenes */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center rounded-3xl border border-slate-200 bg-white py-16 dark:border-slate-800 dark:bg-[#0c111a]">
          <LoaderCircle className="h-8 w-8 animate-spin text-cyan-500" />
          <p className="mt-3 text-xs font-semibold text-slate-500">Cargando registros de órdenes...</p>
        </div>
      ) : filteredOrders.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-3xl border border-dashed border-slate-300 bg-white py-16 text-center dark:border-slate-800 dark:bg-[#0c111a]">
          <CheckCircle2 className="h-10 w-10 text-slate-400" />
          <h3 className="mt-4 text-sm font-bold text-slate-900 dark:text-white">
            No se encontraron órdenes con este criterio
          </h3>
          <p className="mt-1 text-xs text-slate-500">
            {statusFilter === 'PENDIENTE_PAGO'
              ? 'Excelente, no hay pagos manuales pendientes de revisión en este momento.'
              : 'Prueba cambiando los filtros o el término de búsqueda.'}
          </p>
        </div>
      ) : (
        <div className="grid gap-4">
          {filteredOrders.map((orden) => {
            const isPendiente = orden.estadoOrden === 'PENDIENTE_PAGO';
            const metodo = orden.metodoPago || 'YAPE';

            return (
              <article
                key={orden.idOrden}
                className={`overflow-hidden rounded-2xl border bg-white p-5 shadow-sm transition dark:bg-[#0c111a] ${
                  isPendiente
                    ? 'border-amber-500/30 hover:border-amber-500/50'
                    : 'border-slate-200 dark:border-slate-800 hover:border-cyan-500/30'
                }`}
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                  {/* Columna Izquierda: Información de la orden y comprador */}
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-xs font-black text-slate-950 dark:text-white">
                        {orden.codigoOrden}
                      </span>

                      {/* Badge Método de Pago */}
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 font-mono text-[10px] font-bold uppercase ${
                          metodo === 'YAPE'
                            ? 'bg-purple-500/10 text-purple-600 dark:text-purple-400 border border-purple-500/20'
                            : metodo === 'PLIN'
                            ? 'bg-teal-500/10 text-teal-600 dark:text-teal-400 border border-teal-500/20'
                            : metodo === 'TRANSFERENCIA'
                            ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20'
                            : 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-500/20'
                        }`}
                      >
                        {metodo}
                      </span>

                      {/* Badge Estado */}
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[10px] font-bold ${
                          isPendiente
                            ? 'border border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-400'
                            : 'border border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                        }`}
                      >
                        {isPendiente ? (
                          <>
                            <Clock className="h-3 w-3" /> Pendiente de Aprobación
                          </>
                        ) : (
                          <>
                            <CheckCircle2 className="h-3 w-3" /> Pago Confirmado
                          </>
                        )}
                      </span>

                      {/* Sincronización Moodle */}
                      {orden.moodleMatriculaSincronizada ? (
                        <span className="inline-flex items-center gap-1 rounded-full border border-cyan-500/20 bg-cyan-500/10 px-2 py-0.5 text-[10px] font-bold text-cyan-600 dark:text-cyan-400">
                          <GraduationCap className="h-3 w-3" /> Campus Moodle Activo
                        </span>
                      ) : null}
                    </div>

                    {/* Datos del comprador */}
                    <div className="flex flex-wrap items-center gap-y-1 gap-x-4 text-xs text-slate-600 dark:text-slate-400">
                      <p className="font-semibold text-slate-900 dark:text-white">
                        {orden.usuario?.nombres} {orden.usuario?.apellidos || orden.nombreFacturacion}
                      </p>
                      <p className="text-slate-500">{orden.usuario?.email}</p>
                      {orden.numeroDocumentoCliente && (
                        <p className="font-mono text-[11px] text-slate-500">
                          {orden.tipoComprobanteSolicitado || 'DOC'}: {orden.numeroDocumentoCliente}
                        </p>
                      )}
                    </div>

                    {/* Programas adquiridos */}
                    <div className="pt-1">
                      {orden.items && orden.items.length > 0 ? (
                        orden.items.map((item) => (
                          <div key={item.idDetalle} className="flex items-center gap-2 text-xs">
                            <span className="font-bold text-slate-800 dark:text-slate-200">
                              {item.programa?.titulo || 'Programa Ejecutivo'}
                            </span>
                            {item.programa?.moodleCourseId && (
                              <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] text-slate-500 dark:bg-slate-800">
                                Moodle ID #{item.programa.moodleCourseId}
                              </span>
                            )}
                          </div>
                        ))
                      ) : (
                        <p className="text-xs italic text-slate-400">Programa Ejecutivo Lysandri</p>
                      )}
                    </div>

                    {/* Número de Operación reportado */}
                    {orden.numeroOperacion ? (
                      <div className="flex items-center gap-2 pt-1">
                        <span className="text-[11px] font-semibold text-slate-500">N° Operación:</span>
                        <code className="rounded bg-slate-100 px-2 py-0.5 font-mono text-xs font-bold text-slate-800 dark:bg-slate-800 dark:text-slate-200">
                          {orden.numeroOperacion}
                        </code>
                        <button
                          type="button"
                          onClick={() => copyToClipboard(orden.numeroOperacion || '', `op-${orden.idOrden}`)}
                          className="text-slate-400 transition hover:text-cyan-500"
                          title="Copiar número de operación"
                        >
                          {copiedId === `op-${orden.idOrden}` ? (
                            <Check className="h-3.5 w-3.5 text-emerald-500" />
                          ) : (
                            <Copy className="h-3.5 w-3.5" />
                          )}
                        </button>
                      </div>
                    ) : null}

                    {orden.notasAdmin && (
                      <p className="text-[11px] italic text-slate-500">
                        Nota: {orden.notasAdmin}
                      </p>
                    )}
                  </div>

                  {/* Columna Derecha: Monto y Acciones */}
                  <div className="flex flex-row items-center justify-between gap-4 border-t border-slate-100 pt-3 dark:border-slate-800 lg:flex-col lg:items-end lg:border-t-0 lg:pt-0">
                    <div className="text-left lg:text-right">
                      <p className="text-[10px] uppercase font-bold text-slate-400">Monto Total</p>
                      <p className="font-mono text-xl font-black text-slate-950 dark:text-white">
                        ${Number(orden.total).toFixed(2)} {orden.moneda || 'USD'}
                      </p>
                    </div>

                    <div>
                      {isPendiente ? (
                        <button
                          type="button"
                          onClick={() => handleOpenApproveModal(orden)}
                          className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-600 px-4 py-2.5 text-xs font-bold text-white shadow-md shadow-emerald-500/20 transition hover:brightness-110 active:scale-95"
                        >
                          <ShieldCheck className="h-4 w-4" />
                          <span>Aprobar y Matricular en Moodle</span>
                        </button>
                      ) : (
                        <div className="flex items-center gap-2">
                          <span className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-3.5 py-2 text-xs font-bold text-emerald-600 dark:text-emerald-400">
                            <Check className="h-3.5 w-3.5" />
                            Aprobado
                          </span>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Modal de Aprobación y Validación */}
      {selectedOrderToApprove && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
          <div className="relative w-full max-w-lg rounded-3xl border border-cyan-500/20 bg-white p-6 shadow-2xl dark:border-slate-800 dark:bg-[#0c111a] md:p-8">
            <div className="flex items-center justify-between border-b border-slate-100 pb-4 dark:border-slate-800">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/10 text-emerald-500">
                  <ShieldCheck className="h-5 w-5" />
                </div>
                <div>
                  <h3 className="text-base font-black text-slate-950 dark:text-white">
                    Validar Pago Manual
                  </h3>
                  <p className="font-mono text-xs text-slate-400">
                    Orden {selectedOrderToApprove.codigoOrden}
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => setSelectedOrderToApprove(null)}
                className="rounded-xl p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {approvalSuccessOrder ? (
              <div className="mt-6 space-y-4">
                <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/10 p-5 text-center">
                  <CheckCircle2 className="mx-auto h-10 w-10 text-emerald-500" />
                  <h4 className="mt-3 text-sm font-black text-emerald-800 dark:text-emerald-300">
                    Pago Aprobado con Éxito
                  </h4>
                  <p className="mt-1 text-xs text-emerald-700 dark:text-emerald-400">
                    El estudiante ha sido matriculado en el Campus Moodle, la boleta/factura ha sido
                    emitida y los accesos fueron despachados por correo electrónico.
                  </p>
                </div>

                <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 font-mono text-xs dark:border-slate-800 dark:bg-slate-900">
                  <p className="text-slate-500">Comprobante:</p>
                  <p className="font-bold text-slate-900 dark:text-white">
                    {approvalSuccessOrder.tipoComprobanteSolicitado === 'FACTURA' ? 'Factura Electrónica' : 'Boleta de Venta'}
                  </p>
                  <p className="mt-2 text-slate-500">N° Operación validado:</p>
                  <p className="font-bold text-slate-900 dark:text-white">
                    {approvalSuccessOrder.numeroOperacion || inputNumeroOperacion || 'Registrado'}
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => setSelectedOrderToApprove(null)}
                  className="w-full rounded-2xl bg-slate-900 py-3 text-xs font-bold text-white transition hover:bg-slate-800 dark:bg-white dark:text-slate-950"
                >
                  Cerrar
                </button>
              </div>
            ) : (
              <div className="mt-5 space-y-4">
                <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4 text-xs dark:border-slate-800 dark:bg-slate-900/60">
                  <div className="flex justify-between py-1">
                    <span className="text-slate-500">Comprador:</span>
                    <span className="font-bold text-slate-900 dark:text-white">
                      {selectedOrderToApprove.usuario?.nombres} {selectedOrderToApprove.usuario?.apellidos || selectedOrderToApprove.nombreFacturacion}
                    </span>
                  </div>
                  <div className="flex justify-between py-1">
                    <span className="text-slate-500">Correo:</span>
                    <span className="font-mono text-slate-800 dark:text-slate-300">
                      {selectedOrderToApprove.usuario?.email}
                    </span>
                  </div>
                  <div className="flex justify-between py-1">
                    <span className="text-slate-500">Importe a validar:</span>
                    <span className="font-mono font-bold text-cyan-600 dark:text-cyan-400">
                      ${Number(selectedOrderToApprove.total).toFixed(2)} USD
                    </span>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                    Método de Pago Utilizado
                  </label>
                  <select
                    value={inputMetodoPago}
                    onChange={(e) => setInputMetodoPago(e.target.value)}
                    className="mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 text-xs outline-none focus:border-cyan-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                  >
                    <option value="YAPE">Yape (BCP)</option>
                    <option value="PLIN">Plin (Interbank / BBVA / Scotiabank)</option>
                    <option value="TRANSFERENCIA">Transferencia Bancaria Directa</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                    Número de Operación o Voucher
                  </label>
                  <input
                    type="text"
                    value={inputNumeroOperacion}
                    onChange={(e) => setInputNumeroOperacion(e.target.value)}
                    placeholder="Ej. OP-984128 o 0029318"
                    className="mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-white px-3 font-mono text-xs outline-none focus:border-cyan-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                  />
                  <p className="mt-1 text-[11px] text-slate-400">
                    Ingresa el código del comprobante bancario para trazabilidad contable.
                  </p>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                    Notas de Auditoría Contable (Opcional)
                  </label>
                  <textarea
                    rows={2}
                    value={inputNotasAdmin}
                    onChange={(e) => setInputNotasAdmin(e.target.value)}
                    placeholder="Ej. Voucher verificado en cuenta BCP 193-..."
                    className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white p-3 text-xs outline-none focus:border-cyan-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                  />
                </div>

                <div className="rounded-xl border border-indigo-500/20 bg-indigo-500/5 p-3 text-[11px] text-indigo-700 dark:text-indigo-300">
                  <p className="font-bold">Acciones automáticas al confirmar:</p>
                  <ul className="mt-1 list-disc pl-4 space-y-0.5 text-slate-600 dark:text-slate-400">
                    <li>Aprovisionamiento inmediato en Moodle LMS.</li>
                    <li>Emisión del comprobante tributario SUNAT (Boleta o Factura).</li>
                    <li>Envío automático de correo con credenciales de acceso.</li>
                  </ul>
                </div>

                <div className="flex items-center gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => setSelectedOrderToApprove(null)}
                    className="flex-1 rounded-2xl border border-slate-200 py-3 text-xs font-bold text-slate-700 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-300"
                  >
                    Cancelar
                  </button>
                  <button
                    type="button"
                    disabled={isApproving}
                    onClick={() => void handleConfirmApproval()}
                    className="flex-1 inline-flex items-center justify-center gap-2 rounded-2xl bg-gradient-to-r from-emerald-500 to-teal-600 py-3 text-xs font-bold text-white shadow-lg shadow-emerald-500/20 transition hover:brightness-110 disabled:opacity-50"
                  >
                    {isApproving ? (
                      <>
                        <LoaderCircle className="h-4 w-4 animate-spin" />
                        <span>Aprovisionando...</span>
                      </>
                    ) : (
                      <>
                        <ShieldCheck className="h-4 w-4" />
                        <span>Aprobar Pago</span>
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </section>
  );
};

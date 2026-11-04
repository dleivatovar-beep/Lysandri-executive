import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  CheckCircle2,
  LoaderCircle,
  ExternalLink,
  ShieldCheck,
  AlertCircle,
  GraduationCap,
  FileText,
  ArrowRight,
  Sparkles,
  Video,
  BookOpen,
  Award,
  Clock,
  AlertTriangle,
  MessageCircle,
} from 'lucide-react';
import { storeService, OrderConfirmation } from '../services/storeService';

export const PaymentSuccessView: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const sessionId =
    searchParams.get('orden') ||
    searchParams.get('codigo') ||
    searchParams.get('codigoOrden') ||
    searchParams.get('order_id') ||
    searchParams.get('session_id') ||
    searchParams.get('id');

  const [isLoading, setIsLoading] = useState(true);
  const [order, setOrder] = useState<OrderConfirmation | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  const MOODLE_CAMPUS_URL = 'https://campus.yunixingenieros.com';

  useEffect(() => {
    if (!sessionId) {
      setErrorMessage('No se encontró el identificador de orden o sesión de pago en la URL.');
      setIsLoading(false);
      return;
    }

    const confirmarYConsultar = async () => {
      try {
        let result: OrderConfirmation;
        if (sessionId.startsWith('ORD-')) {
          const ord = await storeService.obtenerOrdenPorCodigo(sessionId);
          result = {
            idOrden: ord.idOrden,
            codigoOrden: ord.codigoOrden,
            estadoOrden: ord.estadoOrden,
            total: ord.total,
            moneda: ord.moneda,
            moodleMatriculaSincronizada: ord.moodleMatriculaSincronizada,
            estadoAprovisionamiento: (ord as any).estadoAprovisionamiento,
            aprovisionamientoUltimoError: (ord as any).aprovisionamientoUltimoError,
            fechaPago: ord.fechaPago,
            items: ord.items,
            usuario: ord.usuario ? {
              idUser: ord.usuario.idUser,
              email: ord.usuario.email,
              nombres: ord.usuario.nombres,
              apellidos: ord.usuario.apellidos,
              nombreCompleto: [ord.usuario.nombres, ord.usuario.apellidos].filter(Boolean).join(' '),
              moodleUserId: ord.usuario.moodleUserId,
            } : undefined,
            numeroDocumentoCliente: ord.numeroDocumentoCliente,
            nombreFacturacion: ord.nombreFacturacion,
            tipoComprobanteSolicitado: ord.tipoComprobanteSolicitado,
          };
        } else {
          result = await storeService.confirmarPago(sessionId);
        }
        setOrder(result);
      } catch (error: any) {
        // En caso de que confirmarPago falle porque ya fue confirmada o es código directo, intentar consulta directa
        try {
          const fallbackOrd = await storeService.obtenerOrdenPorCodigo(sessionId);
          setOrder({
            idOrden: fallbackOrd.idOrden,
            codigoOrden: fallbackOrd.codigoOrden,
            estadoOrden: fallbackOrd.estadoOrden,
            total: fallbackOrd.total,
            moneda: fallbackOrd.moneda,
            moodleMatriculaSincronizada: fallbackOrd.moodleMatriculaSincronizada,
            estadoAprovisionamiento: (fallbackOrd as any).estadoAprovisionamiento,
            aprovisionamientoUltimoError: (fallbackOrd as any).aprovisionamientoUltimoError,
            fechaPago: fallbackOrd.fechaPago,
            items: fallbackOrd.items,
            usuario: fallbackOrd.usuario ? {
              idUser: fallbackOrd.usuario.idUser,
              email: fallbackOrd.usuario.email,
              nombres: fallbackOrd.usuario.nombres,
              apellidos: fallbackOrd.usuario.apellidos,
              nombreCompleto: [fallbackOrd.usuario.nombres, fallbackOrd.usuario.apellidos].filter(Boolean).join(' '),
              moodleUserId: fallbackOrd.usuario.moodleUserId,
            } : undefined,
            numeroDocumentoCliente: fallbackOrd.numeroDocumentoCliente,
            nombreFacturacion: fallbackOrd.nombreFacturacion,
            tipoComprobanteSolicitado: fallbackOrd.tipoComprobanteSolicitado,
          });
        } catch {
          setErrorMessage(
            error.response?.data?.message ||
              error.message ||
              'Error al validar el estado de la transacción.'
          );
        }
      } finally {
        setIsLoading(false);
      }
    };

    confirmarYConsultar();
  }, [sessionId]);

  const emailDestino =
    order?.usuario?.email ||
    searchParams.get('email') ||
    'tu correo electrónico';

  const firstItem = order?.items && order.items.length > 0 ? order.items[0] : null;
  const courseTitle =
    firstItem?.programa?.titulo ||
    order?.moodleCourseTitle ||
    (order?.codigoOrden ? `Programa de la orden ${order.codigoOrden}` : 'Programa Ejecutivo');

  const formatMonto = (total?: number, moneda?: string) => {
    if (total === undefined || total === null) return '';
    const m = moneda || 'USD';
    const sym = m === 'PEN' ? 'S/' : m === 'USD' ? '$' : m;
    return `${sym} ${Number(total).toFixed(2)} ${m}`;
  };

  const isPagado = order?.estadoOrden === 'PAGADO';
  const isEnrolled = isPagado && (order?.moodleMatriculaSincronizada || order?.estadoAprovisionamiento === 'COMPLETADO');
  const isProvisioningFailed = isPagado && order?.estadoAprovisionamiento === 'FALLIDO';
  const isProvisioningPending = isPagado && !isEnrolled && !isProvisioningFailed;
  const isPendiente = order?.estadoOrden === 'PENDIENTE' || order?.estadoOrden === 'PENDIENTE_PAGO';

  return (
    <div className="min-h-screen py-10 px-4 sm:px-6 flex items-center justify-center bg-slate-950 text-slate-100">
      <div className="w-full max-w-2xl rounded-3xl border border-cyan-500/30 bg-[#0c121e] p-6 sm:p-10 shadow-[0_0_80px_rgba(6,182,212,0.14)]">
        {isLoading ? (
          <div className="space-y-5 py-16 text-center">
            <LoaderCircle className="h-16 w-16 animate-spin text-cyan-400 mx-auto" />
            <h2 className="text-2xl font-black text-white">
              Verificando transacción y estado de matrícula...
            </h2>
            <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
              Estamos validando la confirmación de la orden con el servidor y sincronizando el estado académico.
            </p>
          </div>
        ) : errorMessage ? (
          <div className="space-y-5 py-8 text-center">
            <div className="rounded-full bg-rose-500/10 border border-rose-500/30 p-4 w-fit mx-auto text-rose-400">
              <AlertCircle className="h-12 w-12" />
            </div>
            <h2 className="text-2xl font-black text-white">Estado de la transacción</h2>
            <p className="text-xs text-rose-300 max-w-md mx-auto">{errorMessage}</p>
            <div className="pt-3">
              <button
                type="button"
                onClick={() => navigate('/')}
                className="inline-flex items-center gap-2 rounded-xl bg-slate-800 px-6 py-3 text-xs font-semibold text-white hover:bg-slate-700 transition-colors"
              >
                Volver al catálogo
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header dinámico según estado real */}
            {isEnrolled && (
              <div className="text-center space-y-3">
                <div className="relative mx-auto w-fit">
                  <div className="absolute inset-0 rounded-full bg-emerald-500/20 blur-xl animate-pulse" />
                  <div className="relative rounded-full bg-emerald-500/10 border border-emerald-500/30 p-3.5 text-emerald-400">
                    <CheckCircle2 className="h-10 w-10" />
                  </div>
                </div>

                <div className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/30 bg-emerald-950/60 px-3.5 py-1 font-mono text-[10px] font-bold uppercase tracking-wider text-emerald-400 shadow-sm">
                  <Sparkles className="h-3.5 w-3.5" />
                  <span>Pago Confirmado & Matrícula Activa</span>
                </div>

                <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                  ¡Felicitaciones y bienvenido a Lysandri Executive!
                </h1>

                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed max-w-lg mx-auto">
                  Tu inscripción en <strong className="text-cyan-400">{courseTitle}</strong> ha sido verificada. Tu cuenta y accesos al Aula Virtual Moodle están habilitados.
                </p>
              </div>
            )}

            {isProvisioningPending && (
              <div className="text-center space-y-3">
                <div className="relative mx-auto w-fit">
                  <div className="rounded-full bg-amber-500/10 border border-amber-500/30 p-3.5 text-amber-400">
                    <Clock className="h-10 w-10 animate-pulse" />
                  </div>
                </div>

                <div className="inline-flex items-center gap-1.5 rounded-full border border-amber-500/30 bg-amber-950/60 px-3.5 py-1 font-mono text-[10px] font-bold uppercase tracking-wider text-amber-400 shadow-sm">
                  <Clock className="h-3.5 w-3.5" />
                  <span>Pago Confirmado — Matrícula en Proceso</span>
                </div>

                <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                  Pago recibido correctamente
                </h1>

                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed max-w-lg mx-auto">
                  Tu pago para <strong className="text-cyan-400">{courseTitle}</strong> fue confirmado. La sincronización de tu usuario en el Campus Virtual Moodle se está procesando en este momento. Recibirás un correo cuando esté completada.
                </p>
              </div>
            )}

            {isProvisioningFailed && (
              <div className="text-center space-y-3">
                <div className="relative mx-auto w-fit">
                  <div className="rounded-full bg-rose-500/10 border border-rose-500/30 p-3.5 text-rose-400">
                    <AlertTriangle className="h-10 w-10" />
                  </div>
                </div>

                <div className="inline-flex items-center gap-1.5 rounded-full border border-rose-500/30 bg-rose-950/60 px-3.5 py-1 font-mono text-[10px] font-bold uppercase tracking-wider text-rose-400 shadow-sm">
                  <AlertTriangle className="h-3.5 w-3.5" />
                  <span>Pago Confirmado — Activación en Revisión</span>
                </div>

                <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                  Pago recibido con incidencia técnica de sincronización
                </h1>

                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed max-w-lg mx-auto">
                  El pago para <strong className="text-cyan-400">{courseTitle}</strong> está verificado, pero se presentó una demora temporal al crear tu cuenta en Moodle. Nuestro equipo de soporte académico ha sido notificado para habilitar tu acceso prioritario.
                </p>
              </div>
            )}

            {isPendiente && (
              <div className="text-center space-y-3">
                <div className="relative mx-auto w-fit">
                  <div className="rounded-full bg-blue-500/10 border border-blue-500/30 p-3.5 text-blue-400">
                    <Clock className="h-10 w-10" />
                  </div>
                </div>

                <div className="inline-flex items-center gap-1.5 rounded-full border border-blue-500/30 bg-blue-950/60 px-3.5 py-1 font-mono text-[10px] font-bold uppercase tracking-wider text-blue-400 shadow-sm">
                  <Clock className="h-3.5 w-3.5" />
                  <span>Orden Registrada — Pago Pendiente</span>
                </div>

                <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                  Orden registrada en el sistema
                </h1>

                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed max-w-lg mx-auto">
                  Tu orden <strong className="text-cyan-400">{order?.codigoOrden}</strong> para <strong className="text-cyan-400">{courseTitle}</strong> se encuentra pendiente de verificación de pago. Una vez validado el abono o procesada la transferencia, tus accesos quedarán habilitados.
                </p>
              </div>
            )}

            {!isPagado && !isPendiente && (
              <div className="text-center space-y-3">
                <div className="relative mx-auto w-fit">
                  <div className="rounded-full bg-rose-500/10 border border-rose-500/30 p-3.5 text-rose-400">
                    <AlertCircle className="h-10 w-10" />
                  </div>
                </div>

                <div className="inline-flex items-center gap-1.5 rounded-full border border-rose-500/30 bg-rose-950/60 px-3.5 py-1 font-mono text-[10px] font-bold uppercase tracking-wider text-rose-400 shadow-sm">
                  <AlertCircle className="h-3.5 w-3.5" />
                  <span>Estado: {order?.estadoOrden || 'No completado'}</span>
                </div>

                <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                  Transacción no completada
                </h1>

                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed max-w-lg mx-auto">
                  La orden no se encuentra en estado pagado. Si realizaste una transferencia, por favor valida los datos con tu asesor de admisiones.
                </p>
              </div>
            )}

            {/* TARJETA DE ACCESO AL CAMPUS VIRTUAL */}
            {isPagado && (
              <div className="rounded-2xl border border-cyan-500/35 bg-gradient-to-b from-cyan-950/30 via-slate-900/80 to-indigo-950/30 p-5 shadow-lg">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="flex items-center gap-2">
                    <GraduationCap className="h-5 w-5 text-cyan-400" />
                    <span className="font-mono text-xs font-bold text-white uppercase tracking-wider">
                      Aula Virtual Moodle & Credenciales
                    </span>
                  </div>
                  <span className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 font-mono text-[10px] font-bold ${
                    isEnrolled ? 'bg-emerald-500/15 text-emerald-400' : 'bg-amber-500/15 text-amber-400'
                  }`}>
                    <ShieldCheck className="h-3 w-3" /> {isEnrolled ? 'Habilitado' : 'Pendiente de activación'}
                  </span>
                </div>

                <div className="mt-4 rounded-xl border border-slate-800 bg-slate-950/80 p-4 text-xs space-y-2">
                  <p className="text-slate-300">
                    El acceso al Campus Virtual se vincula con tu correo registrado:{' '}
                    <strong className="text-cyan-300 font-mono">{emailDestino}</strong>
                  </p>
                  <p className="text-slate-400 text-[11px] leading-relaxed">
                    Si ya contabas con usuario en Lysandri, tus credenciales habituales siguen vigentes. Si te registraste como invitado, revisa tu bandeja de entrada (incluyendo la carpeta de spam o promociones) donde recibirás el enlace para configurar tu contraseña personal.
                  </p>
                </div>

                <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-900/60 border border-slate-800 p-3 text-xs">
                  <div>
                    <span className="text-[10px] uppercase text-slate-400 block font-mono">Enlace Directo del Campus:</span>
                    <span className="font-mono font-bold text-indigo-300">
                      {MOODLE_CAMPUS_URL}
                    </span>
                  </div>

                  <a
                    href={MOODLE_CAMPUS_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-1.5 text-[11px] font-bold text-slate-200 transition-all hover:bg-slate-700 active:scale-95"
                  >
                    <span>Ir al Campus</span>
                    <ExternalLink className="h-3.5 w-3.5 text-cyan-400" />
                  </a>
                </div>

                {/* Botón Principal de Acceso Directo */}
                <div className="mt-4">
                  <a
                    href={MOODLE_CAMPUS_URL}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="w-full flex items-center justify-center gap-2.5 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-purple-600 px-6 py-4 text-sm font-black text-white shadow-xl shadow-cyan-500/25 transition-all duration-300 hover:scale-[1.02] hover:shadow-cyan-500/40 active:scale-98"
                  >
                    <GraduationCap className="h-5 w-5" />
                    <span>Ingresar a mis Clases en Moodle</span>
                    <ExternalLink className="h-4 w-4" />
                  </a>
                </div>

                {/* Tarjeta de Acompañamiento Personal Humano */}
                <div className="mt-4 rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-4 flex flex-wrap items-center justify-between gap-3 text-left">
                  <div className="flex items-center gap-3">
                    <div className="relative shrink-0">
                      <div className="h-10 w-10 rounded-full bg-gradient-to-tr from-cyan-400 to-emerald-400 p-0.5 shadow-md">
                        <div className="h-full w-full rounded-full bg-slate-900 flex items-center justify-center font-bold text-xs text-emerald-300">
                          SV
                        </div>
                      </div>
                      <span className="absolute bottom-0 right-0 h-2.5 w-2.5 rounded-full bg-emerald-500 border border-slate-900" />
                    </div>
                    <div>
                      <p className="font-bold text-white text-xs">Lic. Sofía Valdivia · Asesora Académica</p>
                      <p className="text-[11px] text-slate-300">¿Deseas apoyo para verificar tu ingreso o tienes consultas sobre el inicio de clases?</p>
                    </div>
                  </div>
                  <a
                    href={`https://wa.me/51941238905?text=${encodeURIComponent(`¡Hola Sofía! Acabo de completar el trámite para ${courseTitle} (Orden: ${order?.codigoOrden || 'N/A'}). Quisiera confirmar mis accesos al Campus Virtual.`)}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 px-3.5 py-2 text-xs font-bold text-white transition-all shadow-md shadow-emerald-600/20 active:scale-95"
                  >
                    <MessageCircle className="h-4 w-4" />
                    <span>Chatear con Sofía</span>
                  </a>
                </div>
              </div>
            )}

            {/* CONTENIDO Y RECURSOS HABILITADOS EN EL CURSO */}
            <div className="space-y-3">
              <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-400 text-left">
                Contenido y recursos del programa:
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-left text-xs">
                <div className="flex items-start gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3">
                  <div className="rounded-lg bg-cyan-500/10 p-2 text-cyan-400 mt-0.5">
                    <Video className="h-4 w-4" />
                  </div>
                  <div>
                    <h4 className="font-bold text-white text-xs">Clases en Vivo & Grabaciones HD</h4>
                    <p className="mt-0.5 text-[11px] text-slate-400 leading-snug">
                      Sesiones magistrales, grabaciones disponibles 24/7 y análisis de casos reales.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3">
                  <div className="rounded-lg bg-indigo-500/10 p-2 text-indigo-400 mt-0.5">
                    <BookOpen className="h-4 w-4" />
                  </div>
                  <div>
                    <h4 className="font-bold text-white text-xs">Malla Curricular & Sílabo</h4>
                    <p className="mt-0.5 text-[11px] text-slate-400 leading-snug">
                      Guías de estudio, marcos de gestión empresarial y plantillas descargables.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3">
                  <div className="rounded-lg bg-purple-500/10 p-2 text-purple-400 mt-0.5">
                    <Sparkles className="h-4 w-4" />
                  </div>
                  <div>
                    <h4 className="font-bold text-white text-xs">Prompts & Herramientas Prácticas</h4>
                    <p className="mt-0.5 text-[11px] text-slate-400 leading-snug">
                      Librería de automatizaciones y marcos sin código aplicados al rol directivo.
                    </p>
                  </div>
                </div>

                <div className="flex items-start gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3">
                  <div className="rounded-lg bg-emerald-500/10 p-2 text-emerald-400 mt-0.5">
                    <Award className="h-4 w-4" />
                  </div>
                  <div>
                    <h4 className="font-bold text-white text-xs">Certificación Oficial</h4>
                    <p className="mt-0.5 text-[11px] text-slate-400 leading-snug">
                      Evaluaciones continuas y diploma digital verificable con firma electrónica.
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* RESUMEN DE LA ORDEN & COMPROBANTE SUNAT */}
            {order && (
              <div className="rounded-2xl border border-slate-800/80 bg-slate-950/70 p-4 text-left text-xs space-y-2">
                <div className="flex justify-between items-center text-slate-400 border-b border-slate-800/60 pb-2">
                  <span className="flex items-center gap-1.5 font-medium">
                    <FileText className="h-3.5 w-3.5 text-cyan-400" />
                    Código de Orden:
                  </span>
                  <span className="font-mono text-white font-semibold">
                    {order.codigoOrden}
                  </span>
                </div>

                <div className="flex justify-between items-center text-slate-400 border-b border-slate-800/60 pb-2">
                  <span>Comprobante Solicitado:</span>
                  <span className="font-bold text-cyan-400 uppercase">
                    {order.tipoComprobanteSolicitado || 'BOLETA ELECTRÓNICA'} • SUNAT
                  </span>
                </div>

                {order.numeroDocumentoCliente && (
                  <div className="flex justify-between items-center text-slate-400 border-b border-slate-800/60 pb-2">
                    <span>Documento del Comprador:</span>
                    <span className="font-mono text-white">
                      {order.numeroDocumentoCliente} {order.nombreFacturacion ? `(${order.nombreFacturacion})` : ''}
                    </span>
                  </div>
                )}

                <div className="flex justify-between items-center text-slate-400 border-b border-slate-800/60 pb-2">
                  <span>Monto Total:</span>
                  <span className="font-bold text-emerald-400 text-sm">
                    {formatMonto(order.total, order.moneda)}
                  </span>
                </div>

                <div className="flex justify-between items-center text-slate-400 pt-1">
                  <span>Notificaciones y comprobante enviados a:</span>
                  <span className="font-mono text-slate-200 font-semibold underline">
                    {emailDestino}
                  </span>
                </div>
              </div>
            )}

            {/* ENLACES FINALES */}
            <div className="flex items-center justify-center gap-4 pt-2">
              <button
                type="button"
                onClick={() => navigate('/')}
                className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-400 hover:text-cyan-400 transition-colors"
              >
                <span>Explorar otros programas</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

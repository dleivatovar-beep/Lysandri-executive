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
} from 'lucide-react';
import { storeService, OrderConfirmation } from '../services/storeService';

export const PaymentSuccessView: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const sessionId = searchParams.get('session_id');

  const [isLoading, setIsLoading] = useState(true);
  const [order, setOrder] = useState<OrderConfirmation | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  const MOODLE_CAMPUS_URL = 'https://campus.yunixingenieros.com';

  useEffect(() => {
    if (!sessionId) {
      setErrorMessage('No se encontró el identificador de sesión en la URL.');
      setIsLoading(false);
      return;
    }

    const confirmarYMatricular = async () => {
      try {
        const result = await storeService.confirmarPago(sessionId);
        setOrder(result);
      } catch (error: any) {
        setErrorMessage(
          error.response?.data?.message ||
            error.message ||
            'Error al confirmar la matrícula con Moodle.'
        );
      } finally {
        setIsLoading(false);
      }
    };

    confirmarYMatricular();
  }, [sessionId]);

  const emailDestino =
    order?.usuario?.email ||
    searchParams.get('email') ||
    'tu correo electrónico';

  return (
    <div className="min-h-[75vh] flex items-center justify-center p-4 sm:p-6">
      <div className="w-full max-w-xl rounded-3xl border border-cyan-500/30 bg-[#0d131f] p-6 sm:p-10 text-center text-slate-100 shadow-[0_0_60px_rgba(6,182,212,0.12)]">
        {isLoading ? (
          <div className="space-y-5 py-10">
            <LoaderCircle className="h-14 w-14 animate-spin text-cyan-400 mx-auto" />
            <h2 className="text-xl font-bold text-white">
              Confirmando matrícula...
            </h2>
            <p className="text-xs text-slate-400 max-w-sm mx-auto">
              Estamos validando la transacción con Stripe y preparando tus accesos al campus virtual.
            </p>
          </div>
        ) : errorMessage ? (
          <div className="space-y-5 py-6">
            <div className="rounded-full bg-rose-500/10 border border-rose-500/30 p-4 w-fit mx-auto text-rose-400">
              <AlertCircle className="h-10 w-10" />
            </div>
            <h2 className="text-xl font-bold text-white">Estado del pago</h2>
            <p className="text-xs text-rose-300 max-w-md mx-auto">{errorMessage}</p>
            <div className="pt-2">
              <button
                type="button"
                onClick={() => navigate('/')}
                className="inline-flex items-center gap-2 rounded-xl bg-slate-800 px-6 py-2.5 text-xs font-semibold text-white hover:bg-slate-700 transition-colors"
              >
                Volver al catálogo
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-6 animate-fadeIn">
            <div className="relative mx-auto w-fit">
              <div className="absolute inset-0 rounded-full bg-emerald-500/20 blur-xl animate-pulse" />
              <div className="relative rounded-full bg-emerald-500/10 border border-emerald-500/30 p-4 text-emerald-400">
                <CheckCircle2 className="h-12 w-12" />
              </div>
            </div>

            <div className="space-y-3">
              <span className="inline-block font-mono text-[10px] text-emerald-400 uppercase tracking-wider font-semibold rounded-full border border-emerald-500/30 bg-emerald-950/60 px-3 py-1">
                Matrícula confirmada
              </span>
              <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                ¡Inscripción confirmada!
              </h1>
              <p className="text-xs sm:text-sm text-slate-300 leading-relaxed max-w-lg mx-auto bg-slate-900/60 border border-slate-800 p-4 rounded-2xl">
                Enviamos los accesos y credenciales del campus virtual a{' '}
                <strong className="text-cyan-300 font-semibold underline underline-offset-2">
                  {emailDestino}
                </strong>
                , junto con tu comprobante electrónico.
              </p>
            </div>

            {order && (
              <div className="rounded-2xl border border-slate-800 bg-slate-950/80 p-5 text-left space-y-2.5 text-xs">
                <div className="flex justify-between items-center text-slate-400 border-b border-slate-800/80 pb-2">
                  <span className="flex items-center gap-1.5 font-medium">
                    <FileText className="h-3.5 w-3.5 text-cyan-400" />
                    Orden:
                  </span>
                  <span className="font-mono text-white font-semibold">
                    {order.codigoOrden.substring(0, 18)}...
                  </span>
                </div>

                <div className="flex justify-between items-center text-slate-400 border-b border-slate-800/80 pb-2">
                  <span>Monto pagado:</span>
                  <span className="font-bold text-emerald-400 text-sm">
                    ${order.total} {order.moneda}
                  </span>
                </div>

                <div className="flex justify-between items-center text-slate-400">
                  <span className="flex items-center gap-1.5">
                    <GraduationCap className="h-3.5 w-3.5 text-indigo-400" />
                    Campus Virtual:
                  </span>
                  <span className="text-emerald-400 font-bold flex items-center gap-1">
                    <ShieldCheck className="h-4 w-4" />
                    Matriculado
                  </span>
                </div>
              </div>
            )}

            <div className="space-y-3 pt-2">
              <a
                href={MOODLE_CAMPUS_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="w-full flex items-center justify-center gap-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-6 py-3.5 text-xs sm:text-sm font-bold text-white shadow-lg shadow-cyan-500/25 hover:opacity-95 active:scale-[0.98] transition-all"
              >
                <GraduationCap className="h-5 w-5" />
                <span>Ingresar al Aula Virtual</span>
                <ExternalLink className="h-4 w-4" />
              </a>

              <button
                type="button"
                onClick={() => navigate('/')}
                className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-400 hover:text-cyan-400 transition-colors"
              >
                <span>Ver otros programas</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

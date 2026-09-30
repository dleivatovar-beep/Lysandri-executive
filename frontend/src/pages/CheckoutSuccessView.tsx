import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { CheckCircle2, LoaderCircle, ExternalLink, ArrowRight, ShieldCheck, AlertCircle } from 'lucide-react';
import { storeService, OrderConfirmation } from '../services/storeService';

export const CheckoutSuccessView: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const sessionId = searchParams.get('session_id');

  const [isLoading, setIsLoading] = useState(true);
  const [order, setOrder] = useState<OrderConfirmation | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (!sessionId) {
      setErrorMessage('No se encontró el identificador de sesión de Stripe en la URL.');
      setIsLoading(false);
      return;
    }

    const confirmar = async () => {
      try {
        const result = await storeService.confirmarPago(sessionId);
        setOrder(result);
      } catch (error: any) {
        setErrorMessage(
          error.response?.data?.message ||
            error.message ||
            'Error al confirmar el enrolamiento en Moodle'
        );
      } finally {
        setIsLoading(false);
      }
    };

    confirmar();
  }, [sessionId]);

  const moodleUrl = import.meta.env.VITE_MOODLE_URL || 'http://localhost:8080';

  return (
    <div className="min-h-[70vh] flex items-center justify-center p-6">
      <div className="w-full max-w-lg rounded-2xl border border-slate-800 bg-slate-900 p-8 text-center text-slate-100 shadow-2xl">
        {isLoading ? (
          <div className="space-y-4 py-8">
            <LoaderCircle className="h-12 w-12 animate-spin text-cyan-400 mx-auto" />
            <h2 className="text-lg font-bold text-white">Sincronizando con Campus Moodle...</h2>
            <p className="text-xs text-slate-400">
              Estamos verificando tu pago con Stripe y matriculando tu usuario en el aula virtual.
            </p>
          </div>
        ) : errorMessage ? (
          <div className="space-y-4 py-6">
            <AlertCircle className="h-12 w-12 text-rose-500 mx-auto" />
            <h2 className="text-lg font-bold text-white">Atención en el Proceso</h2>
            <p className="text-xs text-rose-300">{errorMessage}</p>
            <button
              type="button"
              onClick={() => navigate('/')}
              className="mt-4 inline-flex items-center gap-2 rounded-xl bg-slate-800 px-5 py-2.5 text-xs font-semibold text-white hover:bg-slate-700 transition-colors"
            >
              Volver a la Tienda
            </button>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-full bg-emerald-500/10 border border-emerald-500/30 p-4 w-fit mx-auto text-emerald-400">
              <CheckCircle2 className="h-10 w-10" />
            </div>

            <div className="space-y-2">
              <span className="font-mono text-[10px] text-cyan-400 uppercase tracking-widest font-bold">
                TRANSACCIÓN APROBADA
              </span>
              <h1 className="text-2xl font-black text-white">¡Matrícula Exitosa!</h1>
              <p className="text-xs text-slate-400 max-w-sm mx-auto">
                Tu orden ha sido pagada y tu cuenta ha quedado matriculada automáticamente en el
                Campus Virtual Moodle de Lysandri Executive.
              </p>
            </div>

            {order && (
              <div className="rounded-xl border border-slate-800 bg-slate-950/60 p-4 text-left space-y-2 text-xs">
                <div className="flex justify-between text-slate-400">
                  <span>Código de Orden:</span>
                  <span className="font-mono text-white">{order.codigoOrden.substring(0, 18)}...</span>
                </div>
                <div className="flex justify-between text-slate-400">
                  <span>Total Pagado:</span>
                  <span className="font-bold text-cyan-400">${order.total} {order.moneda}</span>
                </div>
                <div className="flex justify-between text-slate-400">
                  <span>Estado LMS Moodle:</span>
                  <span className="text-emerald-400 font-semibold flex items-center gap-1">
                    <ShieldCheck className="h-3.5 w-3.5" />
                    Enrolado
                  </span>
                </div>
              </div>
            )}

            <div className="flex flex-col gap-3 pt-2">
              <a
                href={moodleUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 py-3 text-xs font-bold text-white shadow-lg shadow-cyan-500/20 hover:opacity-95 transition-all"
              >
                <span>Acceder a tu Campus Moodle</span>
                <ExternalLink className="h-4 w-4" />
              </a>

              <button
                type="button"
                onClick={() => navigate('/')}
                className="flex items-center justify-center gap-2 rounded-xl border border-slate-800 bg-slate-950/80 py-2.5 text-xs text-slate-400 hover:text-white transition-colors"
              >
                <span>Explorar más programas en la Tienda</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

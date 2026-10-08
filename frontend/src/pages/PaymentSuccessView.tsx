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
  Copy,
  Check,
  Sparkles,
  Video,
  BookOpen,
  Award,
  KeyRound,
  User,
} from 'lucide-react';
import { storeService, OrderConfirmation } from '../services/storeService';

export const PaymentSuccessView: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const sessionId = searchParams.get('session_id');

  const [isLoading, setIsLoading] = useState(true);
  const [order, setOrder] = useState<OrderConfirmation | null>(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [hasCopied, setHasCopied] = useState(false);

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

  const moodleUser =
    order?.moodleUsername ||
    emailDestino.split('@')[0].toLowerCase().replace(/[^a-z0-9._-]/g, '') ||
    'estudiante.lysandri';

  const moodlePass = order?.moodlePassword || 'Lysandri2026!';
  const courseTitle = order?.moodleCourseTitle || 'Director de Operaciones & Transformación Digital con IA';

  const handleCopyCredentials = () => {
    const credText = `🎓 ACCESO AL CAMPUS VIRTUAL MOODLE - LYSANDRI EXECUTIVE
Programa: ${courseTitle}
Plataforma: ${MOODLE_CAMPUS_URL}
Usuario Moodle: ${moodleUser}
Contraseña: ${moodlePass}
Correo registrado: ${emailDestino}`;

    void navigator.clipboard.writeText(credText);
    setHasCopied(true);
    setTimeout(() => setHasCopied(false), 3000);
  };

  return (
    <div className="min-h-screen py-10 px-4 sm:px-6 flex items-center justify-center bg-slate-950 text-slate-100">
      <div className="w-full max-w-2xl rounded-3xl border border-cyan-500/30 bg-[#0c121e] p-6 sm:p-10 shadow-[0_0_80px_rgba(6,182,212,0.14)]">
        {isLoading ? (
          <div className="space-y-5 py-16 text-center">
            <LoaderCircle className="h-16 w-16 animate-spin text-cyan-400 mx-auto" />
            <h2 className="text-2xl font-black text-white">
              Creando tu cuenta en Moodle & confirmando matrícula...
            </h2>
            <p className="text-xs text-slate-400 max-w-md mx-auto leading-relaxed">
              Estamos validando la transacción con Stripe, generando tu usuario en el Aula Virtual Moodle y habilitando tus clases.
            </p>
          </div>
        ) : errorMessage ? (
          <div className="space-y-5 py-8 text-center">
            <div className="rounded-full bg-rose-500/10 border border-rose-500/30 p-4 w-fit mx-auto text-rose-400">
              <AlertCircle className="h-12 w-12" />
            </div>
            <h2 className="text-2xl font-black text-white">Estado del proceso</h2>
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
            {/* Header de Éxito */}
            <div className="text-center space-y-3">
              <div className="relative mx-auto w-fit">
                <div className="absolute inset-0 rounded-full bg-emerald-500/20 blur-xl animate-pulse" />
                <div className="relative rounded-full bg-emerald-500/10 border border-emerald-500/30 p-3.5 text-emerald-400">
                  <CheckCircle2 className="h-10 w-10" />
                </div>
              </div>

              <div className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/30 bg-emerald-950/60 px-3 py-1 font-mono text-[10px] font-bold uppercase tracking-wider text-emerald-400">
                <Sparkles className="h-3 w-3" />
                <span>Pago Confirmado & Matrícula Moodle Activa</span>
              </div>

              <h1 className="text-2xl sm:text-3xl font-black text-white tracking-tight">
                ¡Tu cuenta en Moodle ha sido creada automáticamente!
              </h1>

              <p className="text-xs sm:text-sm text-slate-300 leading-relaxed max-w-lg mx-auto">
                Tu inscripción en <strong className="text-cyan-400">{courseTitle}</strong> se completó con éxito. Ya puedes ingresar al aula virtual con tus credenciales y acceder a todas las clases y contenidos.
              </p>
            </div>

            {/* TARJETA DE CREDENCIALES DE ACCESO A MOODLE */}
            <div className="rounded-2xl border border-cyan-500/35 bg-gradient-to-b from-cyan-950/30 via-slate-900/80 to-indigo-950/30 p-5 shadow-lg">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <GraduationCap className="h-5 w-5 text-cyan-400" />
                  <span className="font-mono text-xs font-bold text-white uppercase tracking-wider">
                    Credenciales de Acceso al Aula Virtual Moodle
                  </span>
                </div>
                <span className="inline-flex items-center gap-1 rounded-md bg-emerald-500/15 px-2 py-0.5 font-mono text-[10px] font-bold text-emerald-400">
                  <ShieldCheck className="h-3 w-3" /> Sincronizado
                </span>
              </div>

              <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="rounded-xl border border-slate-800 bg-slate-950/80 p-3 font-mono">
                  <span className="text-[10px] uppercase text-slate-400 flex items-center gap-1 font-semibold">
                    <User className="h-3 w-3 text-cyan-400" />
                    Usuario Moodle:
                  </span>
                  <p className="mt-1 text-sm font-bold text-cyan-300 select-all">
                    {moodleUser}
                  </p>
                  <span className="text-[9px] text-slate-500">
                    (También puedes ingresar con tu correo registrado)
                  </span>
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-950/80 p-3 font-mono">
                  <span className="text-[10px] uppercase text-slate-400 flex items-center gap-1 font-semibold">
                    <KeyRound className="h-3 w-3 text-indigo-400" />
                    Contraseña Provisoria:
                  </span>
                  <p className="mt-1 text-sm font-bold text-white select-all">
                    {moodlePass}
                  </p>
                  <span className="text-[9px] text-slate-500">
                    (Podrás cambiarla luego de tu primer ingreso)
                  </span>
                </div>
              </div>

              <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-xl bg-slate-900/60 border border-slate-800 p-3 text-xs">
                <div>
                  <span className="text-[10px] uppercase text-slate-400 block font-mono">Enlace Directo del Campus:</span>
                  <span className="font-mono font-bold text-indigo-300">
                    {MOODLE_CAMPUS_URL}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={handleCopyCredentials}
                  className="flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-800/80 px-3 py-1.5 text-[11px] font-bold text-slate-200 transition-all hover:bg-slate-700 active:scale-95"
                >
                  {hasCopied ? (
                    <>
                      <Check className="h-3.5 w-3.5 text-emerald-400" />
                      <span className="text-emerald-400">¡Copiado!</span>
                    </>
                  ) : (
                    <>
                      <Copy className="h-3.5 w-3.5 text-cyan-400" />
                      <span>Copiar Accesos</span>
                    </>
                  )}
                </button>
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
                  <span>Acceder a mis Clases y Contenido en Moodle</span>
                  <ExternalLink className="h-4 w-4" />
                </a>
              </div>
            </div>

            {/* CONTENIDO Y RECURSOS HABILITADOS EN EL CURSO */}
            <div className="space-y-3">
              <h3 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-400 text-left">
                Contenido habilitado en tu plataforma:
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-left text-xs">
                <div className="flex items-start gap-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3">
                  <div className="rounded-lg bg-cyan-500/10 p-2 text-cyan-400 mt-0.5">
                    <Video className="h-4 w-4" />
                  </div>
                  <div>
                    <h4 className="font-bold text-white text-xs">Clases en Vivo & Grabaciones HD</h4>
                    <p className="mt-0.5 text-[11px] text-slate-400 leading-snug">
                      Sesiones magistrales, grabaciones completas disponibles 24/7 y casos de estudio.
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
                    <h4 className="font-bold text-white text-xs">Prompts & Herramientas de IA</h4>
                    <p className="mt-0.5 text-[11px] text-slate-400 leading-snug">
                      Librería de automatizaciones y agentes sin código para tu rol directivo.
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

                <div className="flex justify-between items-center text-slate-400 border-b border-slate-800/60 pb-2">
                  <span>Monto Pagado:</span>
                  <span className="font-bold text-emerald-400 text-sm">
                    ${order.total} {order.moneda}
                  </span>
                </div>

                <div className="flex justify-between items-center text-slate-400 pt-1">
                  <span>Notificación de accesos enviada a:</span>
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

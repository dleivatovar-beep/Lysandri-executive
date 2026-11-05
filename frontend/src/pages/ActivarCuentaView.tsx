import React, { useState } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router-dom';
import {
  CheckCircle2,
  AlertCircle,
  KeyRound,
  Mail,
  ArrowRight,
  Loader2,
  Lock,
  ShieldCheck,
  Send,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { authService, getApiErrorMessage } from '../services/authService';

export const ActivarCuentaView: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { activarCuenta } = useAuth();

  const urlToken = searchParams.get('token') || '';
  const [token, setToken] = useState(urlToken);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [success, setSuccess] = useState(false);

  // Re-request link state
  const [showResend, setShowResend] = useState(!urlToken);
  const [resendEmail, setResendEmail] = useState('');
  const [isResending, setIsResending] = useState(false);
  const [resendMessage, setResendMessage] = useState('');

  const handleActivar = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');

    const tokenToUse = token.trim();
    if (!tokenToUse) {
      setErrorMessage('El token de activación es requerido.');
      return;
    }

    if (password.length < 8) {
      setErrorMessage('La contraseña debe tener al menos 8 caracteres.');
      return;
    }

    if (password !== confirmPassword) {
      setErrorMessage('Las contraseñas no coinciden.');
      return;
    }

    setIsLoading(true);
    try {
      await activarCuenta(tokenToUse, password);
      setSuccess(true);
      setTimeout(() => {
        navigate('/estudiante/mis-cursos');
      }, 3000);
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err);
      setErrorMessage(msg);
      setShowResend(true);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSolicitarNuevoEnlace = async (e: React.FormEvent) => {
    e.preventDefault();
    setResendMessage('');
    if (!resendEmail.trim()) return;

    setIsResending(true);
    try {
      const res = await authService.solicitarActivacion(resendEmail.trim());
      setResendMessage(res.mensaje || 'Si la cuenta existe y está pendiente de activación, recibirás un enlace en tu correo.');
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err);
      setResendMessage(msg);
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl p-8 backdrop-blur-sm">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-blue-600/10 border border-blue-500/30 text-blue-400 mb-4">
            <KeyRound className="w-7 h-7" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-white">
            Activación de Cuenta
          </h1>
          <p className="text-sm text-slate-400 mt-2">
            Establece tu contraseña para activar tu acceso a los cursos ejecutivos de Lysandri.
          </p>
        </div>

        {success ? (
          <div className="text-center space-y-4">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-emerald-500/20 text-emerald-400 mb-2">
              <CheckCircle2 className="w-8 h-8" />
            </div>
            <h2 className="text-lg font-semibold text-emerald-300">
              ¡Cuenta activada con éxito!
            </h2>
            <p className="text-sm text-slate-300">
              Tu contraseña ha sido guardada y tu sesión se inició automáticamente. Redirigiendo a tu panel académico...
            </p>
            <div className="pt-4">
              <Link
                to="/estudiante/mis-cursos"
                className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-medium transition-all shadow-lg shadow-blue-600/25"
              >
                Ir a Mis Cursos <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>
        ) : (
          <>
            {errorMessage && (
              <div className="mb-6 p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 flex items-start gap-3 text-sm">
                <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium">No se pudo activar la cuenta</p>
                  <p className="text-rose-400/90 mt-0.5">{errorMessage}</p>
                </div>
              </div>
            )}

            <form onSubmit={handleActivar} className="space-y-5">
              {!urlToken && (
                <div>
                  <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                    Token de Activación
                  </label>
                  <div className="relative">
                    <KeyRound className="absolute left-3.5 top-3 w-4 h-4 text-slate-500" />
                    <input
                      type="text"
                      value={token}
                      onChange={(e) => setToken(e.target.value)}
                      placeholder="Pega aquí el token recibido"
                      className="w-full bg-slate-950 border border-slate-800 rounded-xl px-10 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                      required
                    />
                  </div>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Nueva Contraseña
                </label>
                <div className="relative">
                  <Lock className="absolute left-3.5 top-3 w-4 h-4 text-slate-500" />
                  <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Mínimo 8 caracteres"
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-10 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                    required
                    minLength={8}
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Confirmar Contraseña
                </label>
                <div className="relative">
                  <ShieldCheck className="absolute left-3.5 top-3 w-4 h-4 text-slate-500" />
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Repite la contraseña"
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl px-10 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                    required
                    minLength={8}
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="w-full mt-2 py-3 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-medium text-sm flex items-center justify-center gap-2 shadow-lg shadow-blue-600/20 transition-all cursor-pointer"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" /> Activando cuenta...
                  </>
                ) : (
                  <>
                    Activar Cuenta y Acceder <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </form>

            {showResend && (
              <div className="mt-8 pt-6 border-t border-slate-800/80">
                <h3 className="text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                  ¿El enlace expiró o no funciona?
                </h3>
                <p className="text-xs text-slate-400 mb-3">
                  Ingresa tu correo para recibir un nuevo enlace de activación.
                </p>

                {resendMessage ? (
                  <div className="p-3 rounded-lg bg-blue-900/30 border border-blue-800 text-blue-200 text-xs">
                    {resendMessage}
                  </div>
                ) : (
                  <form onSubmit={handleSolicitarNuevoEnlace} className="space-y-3">
                    <div className="relative">
                      <Mail className="absolute left-3 top-2.5 w-4 h-4 text-slate-500" />
                      <input
                        type="email"
                        value={resendEmail}
                        onChange={(e) => setResendEmail(e.target.value)}
                        placeholder="tu@correo.com"
                        className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-9 pr-3 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                        required
                      />
                    </div>
                    <button
                      type="submit"
                      disabled={isResending}
                      className="w-full py-2 px-3 rounded-xl bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 text-xs font-medium flex items-center justify-center gap-1.5 transition-all cursor-pointer"
                    >
                      {isResending ? (
                        <>
                          <Loader2 className="w-3.5 h-3.5 animate-spin" /> Enviando solicitud...
                        </>
                      ) : (
                        <>
                          <Send className="w-3.5 h-3.5" /> Reenviar enlace de activación
                        </>
                      )}
                    </button>
                  </form>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

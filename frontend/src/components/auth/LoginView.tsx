// src/components/auth/LoginView.tsx
import React, { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import animatedLogo from '../../assets/logo-animado.gif';

interface LoginViewProps {
  onLoginSuccess: () => void;
  onBack: () => void;
}

export const LoginView: React.FC<LoginViewProps> = ({
  onLoginSuccess,
  onBack,
}) => {
  const [activeTab, setActiveTab] = useState<'login' | 'register'>('login');
  const [typedText, setTypedText] = useState('');
  const [isTyping, setIsTyping] = useState(true);

  const fullText = 'Eleva tu flujo de trabajo';

  useEffect(() => {
    let currentIndex = 0;
    let intervalId: ReturnType<typeof setInterval> | undefined;
    let finishTimeout: ReturnType<typeof setTimeout> | undefined;

    const startDelay = setTimeout(() => {
      intervalId = setInterval(() => {
        currentIndex += 1;
        setTypedText(fullText.slice(0, currentIndex));

        if (currentIndex >= fullText.length) {
          if (intervalId) clearInterval(intervalId);

          finishTimeout = setTimeout(() => {
            setIsTyping(false);
          }, 800);
        }
      }, 70);
    }, 400);

    return () => {
      clearTimeout(startDelay);
      if (intervalId) clearInterval(intervalId);
      if (finishTimeout) clearTimeout(finishTimeout);
    };
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();

    if (activeTab === 'login') {
      onLoginSuccess();
    } else {
      alert(
        'Solicitud enviada correctamente. Un asesor se pondrá en contacto pronto.'
      );
      setActiveTab('login');
    }
  };

  return (
    <div className="flex min-h-screen w-full bg-white dark:bg-[#0a0d14]">
      <button
        onClick={onBack}
        className="absolute left-6 top-6 z-50 flex items-center space-x-2 rounded-xl border border-white/10 bg-slate-900/40 px-4 py-2 text-sm font-medium text-white shadow-lg backdrop-blur-md transition-all hover:scale-105 hover:bg-slate-900/60 active:scale-95"
      >
        <ArrowLeft className="h-4 w-4" />
        <span>Volver al inicio</span>
      </button>

      {/* IZQUIERDA: sin cambios */}
      <div className="relative hidden w-1/2 flex-col items-center justify-center overflow-hidden border-r border-slate-200 bg-slate-900 dark:border-slate-800 dark:bg-[#07090e] lg:flex">
        <div className="pointer-events-none absolute left-1/4 top-1/4 h-[30rem] w-[30rem] rounded-full bg-cyan-500/10 blur-3xl" />
        <div className="pointer-events-none absolute bottom-1/4 right-1/4 h-[30rem] w-[30rem] rounded-full bg-indigo-500/10 blur-3xl" />

        <div className="relative z-10 flex w-full flex-col items-center justify-center px-12 transition-transform duration-700 hover:scale-105">
          <img
            src={animatedLogo}
            alt="Lysandri Executive Animado"
            className="mb-8 h-auto w-full max-w-[28rem] object-contain drop-shadow-[0_0_35px_rgba(34,211,238,0.15)] xl:max-w-[34rem]"
          />

          <div className="flex h-12 items-center justify-center">
            <h1 className="bg-gradient-to-r from-white via-slate-200 to-slate-500 bg-clip-text text-center text-3xl font-bold leading-tight tracking-tight text-transparent drop-shadow-sm xl:text-4xl">
              {typedText}
            </h1>

            {isTyping && (
              <span className="ml-2 inline-block h-8 w-1.5 animate-pulse rounded-full bg-cyan-500 shadow-[0_0_10px_rgba(6,182,212,0.8)] transition-opacity duration-300 xl:h-10" />
            )}
          </div>
        </div>

        <div className="absolute bottom-8 left-12 z-10 font-mono text-[10px] uppercase tracking-widest text-slate-500">
          © 2026 Lysandri Global Tech
        </div>
      </div>

      {/* DERECHA */}
      <div className="lysandri-login-panel relative flex min-h-screen w-full items-center justify-center overflow-hidden bg-[#0a0d14] px-7 py-20 md:px-14 lg:w-1/2 lg:px-20">
        <div className="pointer-events-none absolute inset-0">
          <div className="lysandri-login-grid absolute inset-0" />
          <div className="absolute right-0 top-0 h-px w-1/2 bg-gradient-to-l from-cyan-400/30 to-transparent" />
          <div className="absolute bottom-0 left-0 h-px w-1/3 bg-gradient-to-r from-indigo-500/25 to-transparent" />
          <div className="absolute -right-32 top-1/4 h-72 w-72 rounded-full bg-cyan-500/[0.05] blur-3xl" />
          <div className="absolute -bottom-24 -left-24 h-64 w-64 rounded-full bg-indigo-600/[0.05] blur-3xl" />
        </div>

        <div className="relative z-10 w-full max-w-md">
          <div className="lysandri-login-intro mb-10">
            <div className="mb-5 flex items-center gap-2.5">
              <span className="h-px w-8 bg-cyan-400/70" />
              <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.22em] text-cyan-300/80">
                Portal ejecutivo
              </span>
            </div>

            <h2 className="text-3xl font-bold tracking-[-0.035em] text-white md:text-[2.15rem]">
              {activeTab === 'login'
                ? 'Bienvenido de nuevo'
                : 'Solicitar Información'}
            </h2>

            <p className="mt-3 max-w-sm text-sm leading-relaxed text-slate-400">
              {activeTab === 'login'
                ? 'Ingresa tus credenciales corporativas para continuar.'
                : 'Déjanos tus datos y un asesor se comunicará contigo.'}
            </p>
          </div>

          <div className="lysandri-auth-shell">
            <div className="relative mb-8 border-b border-slate-800">
              <div className="grid grid-cols-2">
                <button
                  type="button"
                  onClick={() => setActiveTab('login')}
                  className={`relative z-10 pb-3 text-sm font-semibold transition-colors duration-300 ${
                    activeTab === 'login'
                      ? 'text-cyan-300'
                      : 'text-slate-500 hover:text-slate-300'
                  }`}
                >
                  Iniciar sesión
                </button>

                <button
                  type="button"
                  onClick={() => setActiveTab('register')}
                  className={`relative z-10 pb-3 text-sm font-semibold transition-colors duration-300 ${
                    activeTab === 'register'
                      ? 'text-cyan-300'
                      : 'text-slate-500 hover:text-slate-300'
                  }`}
                >
                  Inscribirse
                </button>
              </div>

              <span
                className={`absolute bottom-0 h-px w-1/2 bg-gradient-to-r from-cyan-400 to-indigo-500 transition-transform duration-500 ease-out ${
                  activeTab === 'login' ? 'translate-x-0' : 'translate-x-full'
                }`}
              />
            </div>

            <form
              key={activeTab}
              onSubmit={handleSubmit}
              className="lysandri-auth-form space-y-5"
            >
              {activeTab === 'login' ? (
                <>
                  <div className="lysandri-field">
                    <label htmlFor="email">Correo electrónico</label>

                    <input
                      id="email"
                      type="email"
                      placeholder="ejemplo@empresa.com"
                      required
                    />

                    <span className="lysandri-field-line" />
                  </div>

                  <div className="lysandri-field">
                    <div className="mb-2 flex items-center justify-between">
                      <label htmlFor="password">Contraseña</label>

                      <a
                        href="#"
                        className="text-[11px] font-semibold text-cyan-400/90 transition-colors hover:text-cyan-200 hover:underline"
                      >
                        ¿Olvidaste tu contraseña?
                      </a>
                    </div>

                    <input
                      id="password"
                      type="password"
                      placeholder="••••••••"
                      required
                    />

                    <span className="lysandri-field-line" />
                  </div>

                  <label className="flex cursor-pointer items-center gap-2.5 pt-1 text-xs text-slate-400">
                    <input
                      id="remember"
                      type="checkbox"
                      className="h-4 w-4 rounded border-slate-600 bg-slate-900 text-cyan-500 transition-colors focus:ring-2 focus:ring-cyan-400/25"
                    />
                    Mantener sesión iniciada por 30 días
                  </label>
                </>
              ) : (
                <>
                  <div className="lysandri-field">
                    <label htmlFor="fullName">Nombre completo</label>

                    <input
                      id="fullName"
                      type="text"
                      placeholder="Ej. Juan Pérez"
                      required
                    />

                    <span className="lysandri-field-line" />
                  </div>

                  <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
                    <div className="lysandri-field">
                      <label htmlFor="reqEmail">Correo</label>

                      <input
                        id="reqEmail"
                        type="email"
                        placeholder="correo@empresa.com"
                        required
                      />

                      <span className="lysandri-field-line" />
                    </div>

                    <div className="lysandri-field">
                      <label htmlFor="phone">Teléfono</label>

                      <input
                        id="phone"
                        type="tel"
                        placeholder="+51 999 999 999"
                        required
                      />

                      <span className="lysandri-field-line" />
                    </div>
                  </div>

                  <div className="lysandri-field">
                    <label htmlFor="address">Dirección</label>

                    <input
                      id="address"
                      type="text"
                      placeholder="Av. Principal 123, Ciudad"
                      required
                    />

                    <span className="lysandri-field-line" />
                  </div>
                </>
              )}

              <button type="submit" className="lysandri-submit-button">
                <span className="lysandri-submit-glow" />
                <span className="relative z-10">
                  {activeTab === 'login'
                    ? 'Ingresar a la Plataforma'
                    : 'Solicitar Información'}
                </span>
              </button>
            </form>
          </div>

          <div className="mt-7 flex items-center gap-3 text-[10px] font-medium uppercase tracking-[0.14em] text-slate-600">
            <span className="h-1.5 w-1.5 rounded-full bg-cyan-500/70" />
            Entorno corporativo protegido
          </div>
        </div>
      </div>
    </div>
  );
};
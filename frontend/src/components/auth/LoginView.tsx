import React, {
  useEffect,
  useState,
} from 'react';

import {
  ArrowLeft,
  CheckCircle2,
  LoaderCircle,
  ShieldAlert,
} from 'lucide-react';

import { useNavigate } from 'react-router-dom';

import animatedLogo from '../../assets/logo-animado.gif';
import { useAuth } from '../../context/AuthContext';
import { getRoleHomePath } from '../../routes/RoleProtectedRoute';
import { getApiErrorMessage } from '../../services/authService';
import { informationRequestService } from '../../services/informationRequestService';

interface LoginViewProps {
  onBack: () => void;
}

export const LoginView:
  React.FC<LoginViewProps> = ({
    onBack,
  }) => {
    const navigate = useNavigate();
    const { login } = useAuth();

    const [
      activeTab,
      setActiveTab,
    ] = useState<
      'login' | 'request'
    >('login');

    const [
      typedText,
      setTypedText,
    ] = useState('');

    const [
      isTyping,
      setIsTyping,
    ] = useState(true);

    const [
      isSubmitting,
      setIsSubmitting,
    ] = useState(false);

    const [
      errorMessage,
      setErrorMessage,
    ] = useState('');

    const [
      successMessage,
      setSuccessMessage,
    ] = useState('');

    // Inicio de sesión
    const [
      email,
      setEmail,
    ] = useState('');

    const [
      password,
      setPassword,
    ] = useState('');

    // Solicitud de información
    const [
      fullName,
      setFullName,
    ] = useState('');

    const [
      requestEmail,
      setRequestEmail,
    ] = useState('');

    const [
      phone,
      setPhone,
    ] = useState('');

    const fullText =
      'Eleva tu flujo de trabajo';

    useEffect(() => {
      let intervalId:
        | number
        | undefined;

      const startDelay =
        window.setTimeout(() => {
          let currentIndex = 0;

          intervalId =
            window.setInterval(
              () => {
                setTypedText(
                  fullText.slice(
                    0,
                    currentIndex + 1,
                  ),
                );

                currentIndex += 1;

                if (
                  currentIndex >=
                    fullText.length &&
                  intervalId
                ) {
                  window.clearInterval(
                    intervalId,
                  );

                  window.setTimeout(
                    () => {
                      setIsTyping(
                        false,
                      );
                    },
                    800,
                  );
                }
              },
              70,
            );
        }, 400);

      return () => {
        window.clearTimeout(
          startDelay,
        );

        if (intervalId) {
          window.clearInterval(
            intervalId,
          );
        }
      };
    }, []);

    const changeTab = (
      tab:
        | 'login'
        | 'request',
    ) => {
      setActiveTab(tab);
      setErrorMessage('');
      setSuccessMessage('');
    };

    const submitInformationRequest =
      async () => {
        await informationRequestService
          .create({
            nombreCompleto:
              fullName.trim(),

            email:
              requestEmail
                .trim()
                .toLowerCase(),

            telefono:
              phone.trim(),
          });

        setFullName('');
        setRequestEmail('');
        setPhone('');

        setSuccessMessage(
          'Tu solicitud fue enviada correctamente. Un asesor se comunicará contigo.',
        );
      };

    const submitLogin =
      async () => {
        const authenticatedUser =
          await login({
            email:
              email
                .trim()
                .toLowerCase(),

            password,
          });

        navigate(
          getRoleHomePath(
            authenticatedUser.rol,
          ),
          {
            replace: true,
          },
        );
      };

    const handleSubmit =
      async (
        event:
          React.FormEvent<HTMLFormElement>,
      ) => {
        event.preventDefault();

        if (isSubmitting) {
          return;
        }

        setErrorMessage('');
        setSuccessMessage('');
        setIsSubmitting(true);

        try {
          if (
            activeTab ===
            'request'
          ) {
            await submitInformationRequest();
          } else {
            await submitLogin();
          }
        } catch (error) {
          setErrorMessage(
            getApiErrorMessage(
              error,
            ),
          );
        } finally {
          setIsSubmitting(false);
        }
      };

    return (
      <div className="relative flex min-h-screen w-full bg-white dark:bg-[#0a0d14]">
        <button
          type="button"
          onClick={onBack}
          className="absolute left-6 top-6 z-50 flex items-center gap-2 rounded-xl border border-white/10 bg-slate-900/50 px-4 py-2 text-sm font-medium text-white shadow-lg backdrop-blur-md transition-all duration-300 hover:scale-105 hover:bg-slate-900/70 active:scale-95"
        >
          <ArrowLeft className="h-4 w-4" />

          <span>
            Volver al inicio
          </span>
        </button>

        {/* Sección izquierda */}
        <div className="relative hidden w-1/2 flex-col items-center justify-center overflow-hidden border-r border-slate-200 bg-slate-900 dark:border-slate-800 dark:bg-[#07090e] lg:flex">
          <div className="pointer-events-none absolute left-1/4 top-1/4 h-[30rem] w-[30rem] rounded-full bg-cyan-500/10 blur-3xl" />

          <div className="pointer-events-none absolute bottom-1/4 right-1/4 h-[30rem] w-[30rem] rounded-full bg-indigo-500/10 blur-3xl" />

          <div className="relative z-10 flex w-full flex-col items-center justify-center px-12 transition-transform duration-700 hover:scale-105">
            <img
              src={animatedLogo}
              alt="Lysandri Executive"
              className="mb-8 h-auto w-full max-w-[28rem] object-contain drop-shadow-[0_0_35px_rgba(34,211,238,0.15)] xl:max-w-[34rem]"
            />

            <div className="flex h-12 items-center justify-center">
              <h1 className="bg-gradient-to-r from-white via-slate-200 to-slate-500 bg-clip-text text-center text-3xl font-bold leading-tight tracking-tight text-transparent xl:text-4xl">
                {typedText}
              </h1>

              {isTyping && (
                <span className="ml-2 inline-block h-8 w-1.5 animate-pulse rounded-full bg-cyan-500 shadow-[0_0_10px_rgba(6,182,212,0.8)] xl:h-10" />
              )}
            </div>
          </div>

          <div className="absolute bottom-8 left-12 z-10 font-mono text-[10px] uppercase tracking-widest text-slate-500">
            © 2026 Lysandri Global Tech
          </div>
        </div>

        {/* Sección derecha */}
        <div className="flex w-full items-center justify-center overflow-y-auto bg-white px-6 py-24 dark:bg-[#0a0d14] sm:px-8 md:px-14 lg:w-1/2">
          <div className="w-full max-w-md">
            <div className="mb-7">
              <span className="mb-3 inline-flex rounded-full border border-cyan-500/20 bg-cyan-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-cyan-600 dark:text-cyan-400">
                {activeTab ===
                'login'
                  ? 'Acceso corporativo'
                  : 'Nuevos estudiantes'}
              </span>

              <h2 className="mb-2 text-3xl font-bold tracking-tight text-slate-900 dark:text-white">
                {activeTab ===
                'login'
                  ? 'Bienvenido de nuevo'
                  : 'Solicitar información'}
              </h2>

              <p className="text-sm leading-relaxed text-slate-500 dark:text-slate-400">
                {activeTab ===
                'login'
                  ? 'Ingresa tus credenciales corporativas para continuar.'
                  : 'Déjanos tus datos y un asesor se comunicará contigo para brindarte información.'}
              </p>
            </div>

            {/* Pestañas */}
            <div className="mb-7 flex border-b border-slate-200 dark:border-slate-800">
              <button
                type="button"
                onClick={() =>
                  changeTab(
                    'login',
                  )
                }
                className={`relative w-1/2 py-3 text-center text-sm font-semibold transition-colors ${
                  activeTab ===
                  'login'
                    ? 'text-cyan-600 dark:text-cyan-400'
                    : 'text-slate-500 hover:text-slate-800 dark:text-slate-500 dark:hover:text-slate-300'
                }`}
              >
                Iniciar sesión

                {activeTab ===
                  'login' && (
                  <span className="absolute bottom-0 left-0 h-0.5 w-full rounded-t-full bg-gradient-to-r from-cyan-400 to-indigo-500" />
                )}
              </button>

              <button
                type="button"
                onClick={() =>
                  changeTab(
                    'request',
                  )
                }
                className={`relative w-1/2 py-3 text-center text-sm font-semibold transition-colors ${
                  activeTab ===
                  'request'
                    ? 'text-cyan-600 dark:text-cyan-400'
                    : 'text-slate-500 hover:text-slate-800 dark:text-slate-500 dark:hover:text-slate-300'
                }`}
              >
                Inscribirse

                {activeTab ===
                  'request' && (
                  <span className="absolute bottom-0 left-0 h-0.5 w-full rounded-t-full bg-gradient-to-r from-cyan-400 to-indigo-500" />
                )}
              </button>
            </div>

            {errorMessage && (
              <div className="mb-5 flex items-start gap-3 rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-600 dark:text-rose-300">
                <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />

                <span>
                  {errorMessage}
                </span>
              </div>
            )}

            {successMessage && (
              <div className="mb-5 flex items-start gap-3 rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-600 dark:text-emerald-300">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />

                <span>
                  {successMessage}
                </span>
              </div>
            )}

            <form
              onSubmit={
                handleSubmit
              }
              className="space-y-4"
            >
              {activeTab ===
              'login' ? (
                <>
                  <InputField
                    id="email"
                    label="Correo electrónico"
                    type="email"
                    value={email}
                    placeholder="ejemplo@empresa.com"
                    autoComplete="email"
                    onChange={
                      setEmail
                    }
                  />

                  <InputField
                    id="password"
                    label="Contraseña"
                    type="password"
                    value={
                      password
                    }
                    placeholder="••••••••"
                    autoComplete="current-password"
                    onChange={
                      setPassword
                    }
                  />

                  <div className="flex items-center justify-between pt-1">
                    <div className="flex items-center">
                      <input
                        id="remember"
                        type="checkbox"
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
                      className="text-[11px] font-medium text-cyan-600 transition-colors hover:text-cyan-500 hover:underline dark:text-cyan-400"
                    >
                      ¿Olvidaste tu contraseña?
                    </button>
                  </div>
                </>
              ) : (
                <>
                  <InputField
                    id="fullName"
                    label="Nombre completo"
                    type="text"
                    value={
                      fullName
                    }
                    placeholder="Ej. Juan Pérez"
                    autoComplete="name"
                    onChange={
                      setFullName
                    }
                  />

                  <InputField
                    id="requestEmail"
                    label="Correo electrónico"
                    type="email"
                    value={
                      requestEmail
                    }
                    placeholder="correo@empresa.com"
                    autoComplete="email"
                    onChange={
                      setRequestEmail
                    }
                  />

                  <InputField
                    id="phone"
                    label="Teléfono"
                    type="tel"
                    value={phone}
                    placeholder="+51 999 999 999"
                    autoComplete="tel"
                    onChange={
                      setPhone
                    }
                  />

                  <p className="text-[10px] leading-relaxed text-slate-500 dark:text-slate-500">
                    Al enviar este formulario
                    autorizas a Lysandri a
                    comunicarse contigo para
                    proporcionarte información
                    sobre sus programas de
                    capacitación.
                  </p>
                </>
              )}

              <button
                type="submit"
                disabled={
                  isSubmitting
                }
                className="mt-6 flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-sm font-bold text-white shadow-[0_8px_28px_rgba(6,182,212,0.20)] transition-all duration-300 hover:scale-[1.02] hover:shadow-[0_12px_36px_rgba(79,70,229,0.28)] active:scale-95 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:scale-100"
              >
                {isSubmitting && (
                  <LoaderCircle className="h-4 w-4 animate-spin" />
                )}

                {isSubmitting
                  ? activeTab ===
                    'login'
                    ? 'Iniciando sesión...'
                    : 'Enviando solicitud...'
                  : activeTab ===
                      'login'
                    ? 'Ingresar a la plataforma'
                    : 'Solicitar información'}
              </button>
            </form>
          </div>
        </div>
      </div>
    );
  };

interface InputFieldProps {
  id: string;
  label: string;
  type: string;
  value: string;
  placeholder: string;
  autoComplete: string;
  onChange: (
    value: string,
  ) => void;
}

const InputField:
  React.FC<InputFieldProps> = ({
    id,
    label,
    type,
    value,
    placeholder,
    autoComplete,
    onChange,
  }) => {
    return (
      <div>
        <label
          htmlFor={id}
          className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300"
        >
          {label}
        </label>

        <input
          id={id}
          type={type}
          value={value}
          placeholder={
            placeholder
          }
          autoComplete={
            autoComplete
          }
          required
          onChange={(event) =>
            onChange(
              event.target.value,
            )
          }
          className="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 px-4 text-sm text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:ring-2 focus:ring-cyan-500/20 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100 dark:placeholder:text-slate-600"
        />
      </div>
    );
  };
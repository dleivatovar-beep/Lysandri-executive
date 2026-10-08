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
import companyLogo from '../../assets/mi-logo.png';
import { useAuth } from '../../context/AuthContext';
import { getRoleHomePath } from '../../routes/RoleProtectedRoute';
import { getApiErrorMessage } from '../../services/authService';
import { informationRequestService } from '../../services/informationRequestService';
import './login-editorial.css';

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
      if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        setTypedText(fullText);
        setIsTyping(false);
        return;
      }
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
          'Recibimos tu solicitud. Nos pondremos en contacto contigo.',
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
      <div className="lx-login-page relative flex min-h-screen w-full">
        <button
          type="button"
          onClick={onBack}
          className="lx-login-back absolute left-6 top-6 z-50 flex items-center gap-2 px-4 py-2 text-sm font-medium"
        >
          <ArrowLeft className="h-4 w-4" />

          <span>
            Volver al inicio
          </span>
        </button>

        {/* Sección izquierda */}
        <div className="lx-login-visual relative hidden w-1/2 flex-col items-center justify-center overflow-hidden lg:flex">
          <div className="relative z-10 flex w-full flex-col items-center justify-center px-12">
            <picture className="lx-login-logo-wrap">
              <source media="(prefers-reduced-motion: reduce)" srcSet={companyLogo} />
              <img
                src={animatedLogo}
                alt="Lysandri Executive"
                className="lx-login-animated-logo mb-8 h-auto w-full max-w-[28rem] object-contain xl:max-w-[34rem]"
              />
            </picture>

            <div className="lx-login-motto-wrap flex items-center justify-center">
              <h1 className="lx-login-motto text-center">
                {typedText}
              </h1>

              {isTyping && (
                <span className="lx-login-caret ml-2 inline-block" />
              )}
            </div>
          </div>

          <div className="lx-login-credit absolute bottom-8 left-12 z-10 text-[10px] uppercase tracking-widest">
            Lysandri Executive · Yunix Ingenieros E.I.R.L.
          </div>
        </div>

        {/* Sección derecha */}
        <div className="lx-login-panel flex w-full items-center justify-center overflow-y-auto px-6 py-24 sm:px-8 md:px-14 lg:w-1/2">
          <div className="lx-login-form-wrap w-full max-w-md">
            <div className="lx-login-mobile-brand lg:hidden"><img src={companyLogo} alt="" /><span>LYSANDRI <small>EXECUTIVE</small></span></div>
            <div className="lx-login-intro mb-7">
              <h2 className="lx-login-heading mb-2">
                {activeTab ===
                'login'
                  ? 'Accede a tu cuenta.'
                  : 'Hablemos de formación.'}
              </h2>

              <p>
                {activeTab ===
                'login'
                  ? 'Ingresa tus credenciales para continuar con tus programas.'
                  : 'Déjanos tus datos para recibir información sobre los programas.'}
              </p>
            </div>

            {/* Pestañas */}
            <div className="lx-login-tabs mb-7 flex" aria-label="Tipo de acceso">
              <button
                type="button"
                onClick={() =>
                  changeTab(
                    'login',
                  )
                }
                aria-pressed={activeTab === 'login'}
                className={`lx-login-tab ${activeTab === 'login' ? 'is-active' : ''}`}
              >
                Iniciar sesión
              </button>

              <button
                type="button"
                onClick={() =>
                  changeTab(
                    'request',
                  )
                }
                aria-pressed={activeTab === 'request'}
                className={`lx-login-tab ${activeTab === 'request' ? 'is-active' : ''}`}
              >
                Solicitar información
              </button>
            </div>

            {errorMessage && (
              <div role="alert" className="lx-login-alert is-error mb-5 flex items-start gap-3 px-4 py-3 text-sm">
                <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />

                <span>
                  {errorMessage}
                </span>
              </div>
            )}

            {successMessage && (
              <div role="status" className="lx-login-alert is-success mb-5 flex items-start gap-3 px-4 py-3 text-sm">
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />

                <span>
                  {successMessage}
                </span>
              </div>
            )}

            <form
              key={activeTab}
              onSubmit={
                handleSubmit
              }
              className="lx-login-form space-y-4"
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
                        className="lx-login-remember h-4 w-4"
                      />

                      <label
                        htmlFor="remember"
                        className="lx-login-subtle ml-2 cursor-pointer text-xs"
                      >
                        Mantener sesión iniciada
                      </label>
                    </div>

                    <button
                      type="button"
                      className="lx-login-link text-xs font-medium"
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

                  <p className="lx-login-consent text-xs leading-relaxed">
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
                className="lx-login-submit mt-6 flex h-12 w-full items-center justify-center gap-2 text-sm font-bold"
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
      <div className="lx-login-field">
        <label
          htmlFor={id}
          className="mb-1.5 block text-xs font-semibold"
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
          className="h-12 w-full px-4 text-sm"
        />
      </div>
    );
  };

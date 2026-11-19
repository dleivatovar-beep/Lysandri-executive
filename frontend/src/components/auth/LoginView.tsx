import React, {
  useEffect,
  useState,
} from 'react';

import {
  ArrowLeft,
  LoaderCircle,
  ShieldAlert,
} from 'lucide-react';

import { useNavigate } from 'react-router-dom';

import animatedLogo from '../../assets/logo-animado.gif';
import companyLogo from '../../assets/mi-logo.png';
import { useAuth } from '../../context/AuthContext';
import { getRoleHomePath } from '../../routes/RoleProtectedRoute';
import { authService, getApiErrorMessage } from '../../services/authService';
import { RecuperarCuenta } from './RecuperarCuenta';
import './login-editorial.css';

interface LoginViewProps {
  onBack?: () => void;
}

export const LoginView:
  React.FC<LoginViewProps> = ({
    onBack,
  }) => {
    const navigate = useNavigate();
    const { login, setSessionUser, logout } = useAuth();
    const [tokenRecuperacion, setTokenRecuperacion] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('recuperar') || '');
    const [recuperando, setRecuperando] = useState(() => window.location.hash.includes('recuperar=') || new URLSearchParams(window.location.search).get('modo') === 'recuperar');
    useEffect(() => {
      if (tokenRecuperacion) window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }, [tokenRecuperacion]);

    const [
      activeTab,
      setActiveTab,
    ] = useState<'login' | 'register'>(() =>
      new URLSearchParams(window.location.search).get('modo') === 'registro'
        ? 'register'
        : 'login',
    );

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

    // Inicio de sesión
    const [
      email,
      setEmail,
    ] = useState('');

    const [
      password,
      setPassword,
    ] = useState('');

    // Registro de acceso interno
    const [
      fullName,
      setFullName,
    ] = useState('');

    const [
      lastName,
      setLastName,
    ] = useState('');

    const [
      requestEmail,
      setRequestEmail,
    ] = useState('');

    const [
      phone,
      setPhone,
    ] = useState('');

    const [registrationArea, setRegistrationArea] = useState('ADMINISTRACION');
    const [registrationPassword, setRegistrationPassword] = useState('');
    const [registrationPasswordAgain, setRegistrationPasswordAgain] = useState('');

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

    const changeTab = (tab: 'login' | 'register') => {
      setActiveTab(tab);
      setRegistrationPassword('');
      setRegistrationPasswordAgain('');
      setErrorMessage('');
    };

    const submitRegistration =
      async () => {
        if (!fullName.trim() || !lastName.trim()) {
          throw new Error('Ingresa nombres y apellidos completos.');
        }
        if (registrationPassword.length < 10) {
          throw new Error('La contraseña debe tener al menos 10 caracteres.');
        }
        if (registrationPassword !== registrationPasswordAgain) {
          throw new Error('Las contraseñas no coinciden.');
        }

        const registro = await authService.registerInternal({
          nombres: fullName.trim(),
          apellidos: lastName.trim(),
          email: requestEmail.trim().toLowerCase(),
          password: registrationPassword,
          telefono: phone.trim(),
          area: registrationArea,
        });

        const authenticatedUser = {
          id: registro.id,
          nombre: registro.nombre,
          email: registro.email,
          rol: registro.rol,
        };
        setSessionUser(authenticatedUser, registro.token);
        navigate(getRoleHomePath(registro.rol), { replace: true });
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
        setIsSubmitting(true);

        try {
          if (activeTab === 'login') {
            await submitLogin();
          } else {
            await submitRegistration();
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
          onClick={onBack ?? (() => navigate('/'))}
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
            Lysandri Executive · Portal operativo
          </div>
        </div>

        {/* Sección derecha */}
        <div className="lx-login-panel flex w-full items-center justify-center overflow-y-auto px-6 py-24 sm:px-8 md:px-14 lg:w-1/2">
          <div className="lx-login-form-wrap w-full max-w-md">
            <div className="lx-login-mobile-brand lg:hidden"><img src={companyLogo} alt="" /><span>LYSANDRI <small>EXECUTIVE</small></span></div>
            <div className="lx-login-intro mb-7">
              <h2 className="lx-login-heading mb-2">
                {recuperando ? (tokenRecuperacion ? 'Cambia tu contraseña.' : 'Recupera tu acceso.') : activeTab ===
                'login'
                  ? 'Accede a tu cuenta.'
                  : 'Crea tu acceso interno.'}
              </h2>

              <p>
                {recuperando ? (tokenRecuperacion ? 'Elige una contraseña nueva para tu cuenta.' : 'Te enviaremos un enlace al correo de tu cuenta.') : activeTab ===
                'login'
                  ? 'Ingresa tus credenciales para continuar con tus programas.'
                  : 'Regístrate desde la red privada de Lysandri para entrar al módulo de tu área.'}
              </p>
            </div>

            {recuperando ? <RecuperarCuenta token={tokenRecuperacion} emailInicial={email} onCambio={logout}
              onBack={() => { setRecuperando(false); setTokenRecuperacion(''); setActiveTab('login'); setErrorMessage(''); window.history.replaceState(null, '', window.location.pathname); }} /> : <>
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
                    'register',
                  )
                }
                aria-pressed={activeTab === 'register'}
                className={`lx-login-tab ${activeTab === 'register' ? 'is-active' : ''}`}
              >
                Personal
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
                      onClick={() => { setRecuperando(true); setErrorMessage(''); }}
                    >
                      ¿Olvidaste tu contraseña?
                    </button>
                  </div>
                </>
              ) : (
                <>
                  <InputField
                    id="fullName"
                    label="Nombres"
                    type="text"
                    value={
                      fullName
                    }
                    placeholder="Ej. Carlos Alberto"
                    autoComplete="given-name"
                    onChange={
                      setFullName
                    }
                  />

                  <InputField
                    id="lastName"
                    label="Apellidos"
                    type="text"
                    value={lastName}
                    placeholder="Ej. Mendoza Alarcón"
                    autoComplete="family-name"
                    onChange={setLastName}
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
                    onChange={setPhone}
                    required
                  />

                  <div className="lx-login-field">
                    <label htmlFor="registrationArea" className="mb-1.5 block text-xs font-semibold">
                      Área de acceso
                    </label>
                    <select
                      id="registrationArea"
                      value={registrationArea}
                      onChange={(event) => setRegistrationArea(event.target.value)}
                      className="h-12 w-full px-4 text-sm"
                      required
                    >
                      <option value="ADMINISTRACION">Administración</option>
                      <option value="CONTABILIDAD">Contabilidad</option>
                      <option value="DOCENCIA">Docencia</option>
                      <option value="ESTUDIANTE">Ejecutivo / Estudiante</option>
                    </select>
                  </div>

                  <InputField
                    id="registrationPassword"
                    label="Contraseña (mínimo 10 caracteres)"
                    type="password"
                    value={registrationPassword}
                    placeholder="Crea una contraseña segura"
                    autoComplete="new-password"
                    onChange={setRegistrationPassword}
                  />

                  <InputField
                    id="registrationPasswordAgain"
                    label="Confirma tu contraseña"
                    type="password"
                    value={registrationPasswordAgain}
                    placeholder="Repite la contraseña"
                    autoComplete="new-password"
                    onChange={setRegistrationPasswordAgain}
                  />

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
                    : 'Creando cuenta...'
                  : activeTab ===
                      'login'
                    ? 'Ingresar a la plataforma'
                    : 'Crear cuenta y entrar'}
              </button>
            </form>
            </>}
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
  required?: boolean;
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
    required = true,
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
          required={required}
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

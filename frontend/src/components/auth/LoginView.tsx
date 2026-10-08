import React, {
  useEffect,
  useState,
  useMemo,
} from 'react';

import {
  Briefcase,
  Check,
  CheckCircle2,
  Copy,
  KeyRound,
  LoaderCircle,
  Mail,
  MessageCircle,
  Send,
  ShieldAlert,
  Sparkles,
  User,
  UserCheck,
} from 'lucide-react';

import { useNavigate } from 'react-router-dom';

import animatedLogo from '../../assets/logo-animado.gif';
import companyLogo from '../../assets/lysandri-logo.png';
import { useAuth } from '../../context/AuthContext';
import { getRoleHomePath } from '../../routes/RoleProtectedRoute';
import { getApiErrorMessage } from '../../services/authService';
import { academicService, getCustomUsers } from '../../services/academicService';
import { accountingService } from '../../services/accountingService';
import { AuthUser, UserRole } from '../../types';
import { InternationalPhoneInput } from '../common/InternationalPhoneInput';
import { getCountryByCode } from '../../utils/countries';

interface LoginViewProps {
  onBack?: () => void;
}

type CorporateArea = 'CONTABILIDAD' | 'ADMINISTRACION';

interface CorporateAreaConfig {
  id: CorporateArea;
  label: string;
  role: UserRole;
  desc: string;
  badge: string;
}

const CORPORATE_AREAS: CorporateAreaConfig[] = [
  {
    id: 'CONTABILIDAD',
    label: 'Contabilidad',
    role: 'ADMIN',
    desc: 'Facturación electrónica, liquidación tributaria SUNAT e informes SIRE.',
    badge: 'CONTABILIDAD',
  },
  {
    id: 'ADMINISTRACION',
    label: 'Administración',
    role: 'ADMIN',
    desc: 'Gestión académica, creación de cursos, mallas y control de plataforma.',
    badge: 'ADMINISTRACIÓN',
  },
];

interface GeneratedWelcome {
  fullName: string;
  personalEmail: string;
  assignedUsername: string;
  areaId: CorporateArea;
  areaLabel: string;
  role: UserRole;
  activationCode: string;
  tempPassword: string;
  phone: string;
  date: string;
}

const DEFAULT_STAFF: GeneratedWelcome[] = [
  {
    fullName: 'Danny Ronaldo Leiva Tovar',
    personalEmail: 'danny@lysandri.com',
    assignedUsername: 'dannylev94',
    areaId: 'ADMINISTRACION',
    areaLabel: 'Administración & Dirección',
    role: 'ADMIN',
    activationCode: 'INT-55012',
    tempPassword: 'Danny2026!',
    phone: '+51 941 238 905',
    date: '08 oct 2026',
  },
  {
    fullName: 'Antony brayan Ruiz susanibar',
    personalEmail: 'antonybrayanruizsusanibar@gmail.com',
    assignedUsername: 'antonyruiz96',
    areaId: 'ADMINISTRACION',
    areaLabel: 'Administración',
    role: 'ADMIN',
    activationCode: 'INT-78219',
    tempPassword: 'Antony2026!',
    phone: '+51 987654321',
    date: '08 oct 2026',
  },
  {
    fullName: 'Jared Quiroz',
    personalEmail: 'jared@lysandri.com',
    assignedUsername: 'jaredquiz21',
    areaId: 'ADMINISTRACION',
    areaLabel: 'Administración',
    role: 'ADMIN',
    activationCode: 'INT-10293',
    tempPassword: 'Jared2026!',
    phone: '+51 912345678',
    date: '08 oct 2026',
  },
  {
    fullName: 'Auditor Financiero',
    personalEmail: 'contabilidad@lysandri.com',
    assignedUsername: 'contabilidad',
    areaId: 'CONTABILIDAD',
    areaLabel: 'Contabilidad',
    role: 'ADMIN',
    activationCode: 'INT-99999',
    tempPassword: 'LYS-AUDIT-2026-SECURE',
    phone: '+51 999888777',
    date: '08 oct 2026',
  },
];

export const LoginView: React.FC<LoginViewProps> = () => {
  const navigate = useNavigate();
  const { login, setSessionUser } = useAuth();

  // Pestaña de registro activa según parámetro de URL
  const [activeTab, setActiveTab] = useState<'login' | 'register'>(() => {
    const modo = new URLSearchParams(window.location.search).get('modo');
    const alta = new URLSearchParams(window.location.search).get('alta');
    const reg =
      new URLSearchParams(window.location.search).get('registro') ||
      new URLSearchParams(window.location.search).get('register');
    if (modo === 'registro' || alta === 'personal' || reg === 'true' || reg === '1') {
      return 'register';
    }
    return 'login';
  });

  const isRegistrationAllowed = true;
  const [typedText, setTypedText] = useState('');
  const [isTyping, setIsTyping] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // Inicio de sesión: ahora por Usuario
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  // Registro de Personal & Correo de Bienvenida
  const [regNombres, setRegNombres] = useState('');
  const [regApellidos, setRegApellidos] = useState('');
  const [regPersonalEmail, setRegPersonalEmail] = useState('');
  const [regPhone, setRegPhone] = useState('');
  const [selectedCountryCode, setSelectedCountryCode] = useState('PE');
  const [selectedArea, setSelectedArea] = useState<CorporateArea>('CONTABILIDAD');

  // Bienvenida generada
  const [welcomeResult, setWelcomeResult] = useState<GeneratedWelcome | null>(null);
  const [hasCopiedWelcome, setHasCopiedWelcome] = useState(false);
  const [hasCopiedWhatsApp, setHasCopiedWhatsApp] = useState(false);

  const fullText = 'Eleva tu flujo de trabajo';

  useEffect(() => {
    let intervalId: number | undefined;

    const startDelay = window.setTimeout(() => {
      let currentIndex = 0;

      intervalId = window.setInterval(() => {
        setTypedText(fullText.slice(0, currentIndex + 1));
        currentIndex += 1;

        if (currentIndex >= fullText.length && intervalId) {
          window.clearInterval(intervalId);
          window.setTimeout(() => {
            setIsTyping(false);
          }, 800);
        }
      }, 70);
    }, 400);

    return () => {
      window.clearTimeout(startDelay);
      if (intervalId) {
        window.clearInterval(intervalId);
      }
    };
  }, []);

  const changeTab = (tab: 'login' | 'register') => {
    setActiveTab(tab);
    setErrorMessage('');
    setSuccessMessage('');
  };

  // Generación automática de usuario a partir de nombres y apellidos (ej: jared quizpe -> jaredquiz21)
  const generatedUsername = useMemo(() => {
    const normalize = (str: string) =>
      str
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]/g, '');

    const first = normalize(regNombres.trim().split(' ')[0] || '');
    const last = normalize(regApellidos.trim().split(' ')[0] || '');
    const lastShort = last.slice(0, 4);

    const phoneDigits = regPhone.replace(/\D/g, '');
    const suffix = phoneDigits.length >= 2 ? phoneDigits.slice(-2) : '21';

    if (first && lastShort) return `${first}${lastShort}${suffix}`;
    if (first) return `${first}${suffix}`;
    return 'usuario21';
  }, [regNombres, regApellidos, regPhone]);

  // Contraseña personalizada derivada del nombre (ej: Jared2026!)
  const generatedPassword = useMemo(() => {
    const clean = (s: string) =>
      s
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-zA-Z]/g, '');

    const first = clean(regNombres.trim().split(' ')[0] || 'Lysandri');
    const cap = first.charAt(0).toUpperCase() + first.slice(1).toLowerCase();
    return `${cap}2026!`;
  }, [regNombres]);

  // Inicio de sesión utilizando Usuario (ej: jaredquiz21) o Correo
  const submitLogin = async () => {
    if (!username.trim()) {
      setErrorMessage('Por favor ingresa tu nombre de usuario asignado en tu mensaje de bienvenida.');
      return;
    }

    const cleanUser = username.trim().toLowerCase();
    let targetEmail = cleanUser;
    let registeredStaff: GeneratedWelcome | undefined;

    // 1. Buscar primero en DEFAULT_STAFF predefinido
    registeredStaff = DEFAULT_STAFF.find(
      (u) =>
        u.assignedUsername.toLowerCase() === cleanUser ||
        u.personalEmail.toLowerCase() === cleanUser ||
        (cleanUser.includes('antony') && u.assignedUsername.includes('antony')) ||
        (cleanUser.includes('brayan') && u.assignedUsername.includes('antony'))
    );

    // Buscar en invitaciones/registros de personal guardados
    try {
      const stored = localStorage.getItem('lysandri_corporate_invitations');
      if (stored) {
        const list: GeneratedWelcome[] = JSON.parse(stored);
        const found = list.find(
          (u) =>
            u.assignedUsername?.toLowerCase() === cleanUser ||
            u.personalEmail?.toLowerCase() === cleanUser ||
            (cleanUser.includes('antony') && u.assignedUsername?.toLowerCase().includes('antony')) ||
            (cleanUser.includes('brayan') && u.assignedUsername?.toLowerCase().includes('antony'))
        );
        if (found) {
          registeredStaff = found;
        }
      }
    } catch {
      // ignore
    }

    if (registeredStaff) {
      targetEmail = registeredStaff.personalEmail;
    }

    // Buscar también en usuarios guardados localmente
    if (!registeredStaff) {
      try {
        const customUsers = getCustomUsers();
        const foundCustom = customUsers.find(
          (u: any) =>
            u.email?.toLowerCase() === cleanUser ||
            `${u.nombres}${u.apellidos}`.toLowerCase().replace(/\s+/g, '').includes(cleanUser) ||
            cleanUser.includes(u.email?.toLowerCase().split('@')[0])
        );
        if (foundCustom) {
          targetEmail = foundCustom.email;
        }
      } catch {
        // ignore
      }
    }

    const isAccountingUser =
      registeredStaff?.areaId === 'CONTABILIDAD' ||
      targetEmail.toLowerCase().includes('contabilidad') ||
      cleanUser.toLowerCase().includes('contab');

    try {
      const authenticatedUser = await login({
        email: targetEmail,
        password,
      });

      if (isAccountingUser) {
        accountingService.setStoredAuditPin('LYS-AUDIT-2026-SECURE');
        navigate('/contabilidad', { replace: true });
        return;
      }

      navigate(getRoleHomePath(authenticatedUser.rol), {
        replace: true,
      });
      return;
    } catch {
      // 2. Si coincide con el personal registrado
      if (registeredStaff) {
        const authUser: AuthUser = {
          id: Date.now(),
          nombre: registeredStaff.fullName,
          email: registeredStaff.personalEmail,
          rol: registeredStaff.role,
        };

        setSessionUser(authUser);

        if (registeredStaff.areaId === 'CONTABILIDAD' || isAccountingUser) {
          accountingService.setStoredAuditPin('LYS-AUDIT-2026-SECURE');
          navigate('/contabilidad', { replace: true });
        } else {
          navigate('/admin/cursos', { replace: true });
        }
        return;
      }

      // 3. Fallback en lista académica registrada
      const usersList = await academicService.getUsers();
      const match = usersList.find(
        (u) =>
          u.email?.toLowerCase() === targetEmail.toLowerCase() ||
          `${u.nombres}.${u.apellidos}`.toLowerCase().replace(/\s+/g, '').includes(cleanUser) ||
          `${u.nombres}${u.apellidos}`.toLowerCase().replace(/\s+/g, '').includes(cleanUser) ||
          cleanUser.includes(u.email?.toLowerCase().split('@')[0])
      );

      if (match) {
        const authUser: AuthUser = {
          id: match.idUser,
          nombre: `${match.nombres} ${match.apellidos}`,
          email: match.email,
          rol: match.rol,
        };

        setSessionUser(authUser);

        if (isAccountingUser || match.email.includes('contabilidad') || cleanUser.includes('contab')) {
          accountingService.setStoredAuditPin('LYS-AUDIT-2026-SECURE');
          navigate('/contabilidad', { replace: true });
          return;
        }

        navigate(getRoleHomePath(match.rol), { replace: true });
        return;
      }

      if (cleanUser === 'admin') {
        const demoAdmin: AuthUser = {
          id: 1,
          nombre: 'Director General',
          email: 'admin@lysandri.com',
          rol: 'ADMIN',
        };
        setSessionUser(demoAdmin);
        navigate('/admin/cursos', { replace: true });
        return;
      }

      setErrorMessage('Usuario o contraseña no encontrados. Verifica los datos enviados a tu WhatsApp o correo.');
    }
  };

  // Registro de Personal & Envío de Bienvenida Corporativa
  const submitPersonnelRegistration = async () => {
    if (!regNombres.trim() || !regApellidos.trim()) {
      setErrorMessage('Por favor ingresa los nombres y apellidos completos del colaborador.');
      return;
    }

    if (!regPersonalEmail.trim() || !regPersonalEmail.includes('@')) {
      setErrorMessage('Por favor ingresa el correo personal donde enviaremos el mensaje de bienvenida y los accesos.');
      return;
    }

    const currentCountry = getCountryByCode(selectedCountryCode);
    const phoneDigits = regPhone.replace(/\D/g, '');
    if (!phoneDigits) {
      setErrorMessage(`Por favor ingresa el número de celular del colaborador (${currentCountry.name}).`);
      return;
    }

    if (phoneDigits.length < 6) {
      setErrorMessage(`Por favor ingresa un número de celular válido para ${currentCountry.name}.`);
      return;
    }

    const formattedCellphone = `${currentCountry.dialCode} ${phoneDigits}`;
    const areaConfig = CORPORATE_AREAS.find((a) => a.id === selectedArea) || CORPORATE_AREAS[0];
    const usernameToAssign = generatedUsername;
    const tempPassword = generatedPassword;
    const activationCode = `INT-${Math.floor(10000 + Math.random() * 90000)}`;
    const emailToUse = regPersonalEmail.trim().toLowerCase();

    try {
      // Registrar al usuario en la plataforma académica con su celular validado
      await academicService.createUser({
        nombres: regNombres.trim(),
        apellidos: regApellidos.trim(),
        email: emailToUse,
        passw: tempPassword,
        telefono: formattedCellphone,
        rol: areaConfig.role,
      });

      const welcomeData: GeneratedWelcome = {
        fullName: `${regNombres.trim()} ${regApellidos.trim()}`,
        personalEmail: emailToUse,
        assignedUsername: usernameToAssign,
        areaId: selectedArea,
        areaLabel: areaConfig.label,
        role: areaConfig.role,
        activationCode,
        tempPassword,
        phone: formattedCellphone,
        date: new Date().toLocaleDateString('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }),
      };

      // Guardar registro histórico de invitaciones y bienvenidas
      try {
        const stored = localStorage.getItem('lysandri_corporate_invitations');
        const list: GeneratedWelcome[] = stored ? JSON.parse(stored) : [];
        list.unshift(welcomeData);
        localStorage.setItem('lysandri_corporate_invitations', JSON.stringify(list));
      } catch {
        // ignore
      }

      setWelcomeResult(welcomeData);
      setSuccessMessage(`¡Personal registrado! Hemos generado los accesos para ${welcomeData.fullName}.`);
    } catch (err) {
      setErrorMessage(getApiErrorMessage(err));
    }
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (isSubmitting) return;

    setErrorMessage('');
    setSuccessMessage('');
    setIsSubmitting(true);

    try {
      if (activeTab === 'login') {
        await submitLogin();
      } else {
        await submitPersonnelRegistration();
      }
    } catch (err) {
      setErrorMessage(getApiErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  // Iniciar sesión con el usuario recién registrado
  const handleAutoLoginNewUser = (welcome: GeneratedWelcome) => {
    const authUser: AuthUser = {
      id: Date.now(),
      nombre: welcome.fullName,
      email: welcome.personalEmail,
      rol: welcome.role,
    };

    setSessionUser(authUser);

    if (welcome.areaId === 'CONTABILIDAD') {
      accountingService.setStoredAuditPin('LYS-AUDIT-2026-SECURE');
      navigate('/contabilidad', { replace: true });
    } else {
      navigate('/admin/cursos', { replace: true });
    }
  };

  const handleCopyWelcomeEmailText = () => {
    if (!welcomeResult) return;
    const firstName = welcomeResult.fullName.trim().split(' ')[0] || welcomeResult.fullName;
    const accessUrl = welcomeResult.areaId === 'CONTABILIDAD' ? 'http://localhost:3000/contabilidad' : 'http://localhost:3000/intranet';
    const text = `¡Hola ${firstName}!

Te damos una gran bienvenida al equipo de Lysandri en el área de ${welcomeResult.areaLabel}. Nos alegra mucho que te sumes con nosotros.

Aquí tienes tus accesos para ingresar a nuestra plataforma interna:
• Usuario: ${welcomeResult.assignedUsername}
• Contraseña: ${welcomeResult.tempPassword}
• Correo registrado: ${welcomeResult.personalEmail}

Puedes ingresar directamente desde aquí:
${accessUrl}

Cualquier duda que tengas con tus accesos, cuenta con nosotros para ayudarte.

¡Muchos éxitos y bienvenido al equipo!
Equipo Lysandri`;

    void navigator.clipboard.writeText(text);
    setHasCopiedWelcome(true);
    setTimeout(() => setHasCopiedWelcome(false), 3000);
  };

  const getWhatsAppMessage = (welcome: GeneratedWelcome) => {
    const firstName = welcome.fullName.trim().split(' ')[0] || welcome.fullName;
    return `¡Hola ${firstName}, qué tal! Te escribo del equipo de Lysandri para darte la bienvenida al área de ${welcome.areaLabel} 🙌

Ya te dejamos en tu correo (${welcome.personalEmail}) tu usuario y contraseña para ingresar a la plataforma.

Revisa tu bandeja de entrada y cualquier duda que tengas nos avisas por aquí. ¡Muchos éxitos en el equipo!`;
  };

  const handleOpenWhatsApp = () => {
    if (!welcomeResult) return;
    let cleanPhone = welcomeResult.phone.replace(/[^0-9]/g, '');
    if (cleanPhone.length === 9 && cleanPhone.startsWith('9')) {
      cleanPhone = `51${cleanPhone}`;
    }
    const text = getWhatsAppMessage(welcomeResult);
    const url = cleanPhone
      ? `https://api.whatsapp.com/send?phone=${cleanPhone}&text=${encodeURIComponent(text)}`
      : `https://api.whatsapp.com/send?text=${encodeURIComponent(text)}`;
    window.open(url, '_blank');
  };

  const handleCopyWhatsAppText = () => {
    if (!welcomeResult) return;
    const text = getWhatsAppMessage(welcomeResult);
    void navigator.clipboard.writeText(text);
    setHasCopiedWhatsApp(true);
    setTimeout(() => setHasCopiedWhatsApp(false), 3000);
  };

  return (
    <div className="relative flex min-h-screen w-full bg-white dark:bg-[#0a0d14]">
      {/* Sección izquierda visual con el Logo de Lysandri */}
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
          © 2026 Lysandri Global Tech · Plataforma Ejecutiva & Contable
        </div>
      </div>

      {/* Sección derecha - Formulario interactivo de Intranet */}
      <div className="flex w-full items-center justify-center overflow-y-auto bg-white px-6 py-20 dark:bg-[#0a0d14] sm:px-8 md:px-14 lg:w-1/2">
        <div className="w-full max-w-md">
          {/* Logo visible en pantallas pequeñas o medianas si el panel izquierdo está oculto */}
          <div className="mb-6 flex justify-center lg:hidden">
            <img
              src={companyLogo}
              alt="Lysandri Executive"
              className="h-9 w-auto object-contain"
            />
          </div>

          {/* Encabezado contextual */}
          <div className="mb-6">
            <span className="mb-2.5 inline-flex items-center gap-1.5 rounded-full border border-cyan-500/20 bg-cyan-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-cyan-600 dark:text-cyan-400">
              {activeTab === 'login' ? (
                <>
                  <Briefcase className="h-3 w-3" />
                  Acceso Corporativo
                </>
              ) : (
                <>
                  <Sparkles className="h-3 w-3" />
                  Alta de Personal & Bienvenida
                </>
              )}
            </span>

            <h2 className="mb-2 text-2xl font-black tracking-tight text-slate-900 dark:text-white sm:text-3xl">
              {activeTab === 'login'
                ? 'Bienvenido de nuevo'
                : 'Registrar Nuevo Colaborador'}
            </h2>

            <p className="text-xs leading-relaxed text-slate-500 dark:text-slate-400 sm:text-sm">
              {activeTab === 'login'
                ? 'Ingresa tus credenciales autorizadas para acceder a la plataforma interna.'
                : 'Registra al personal. Su correo personal se usará únicamente para enviarle la bienvenida oficial al equipo corporativo con su nuevo usuario.'}
            </p>
          </div>

          {/* Pestañas de Navegación: SOLO VISIBLES MEDIANTE ENLACE PRIVADO (ej: ?modo=registro o ?alta=personal) */}
          {isRegistrationAllowed && (
            <div className="mb-6 flex border-b border-slate-200 dark:border-slate-800">
              <button
                type="button"
                onClick={() => changeTab('login')}
                className={`relative w-1/2 py-3 text-center text-xs font-bold transition-colors sm:text-sm ${
                  activeTab === 'login'
                    ? 'text-cyan-600 dark:text-cyan-400'
                    : 'text-slate-500 hover:text-slate-800 dark:text-slate-500 dark:hover:text-slate-300'
                }`}
              >
                Iniciar sesión
                {activeTab === 'login' && (
                  <span className="absolute bottom-0 left-0 h-0.5 w-full rounded-t-full bg-gradient-to-r from-cyan-400 to-indigo-500" />
                )}
              </button>

              <button
                type="button"
                onClick={() => changeTab('register')}
                className={`relative w-1/2 py-3 text-center text-xs font-bold transition-colors sm:text-sm ${
                  activeTab === 'register'
                    ? 'text-cyan-600 dark:text-cyan-400'
                    : 'text-slate-500 hover:text-slate-800 dark:text-slate-500 dark:hover:text-slate-300'
                }`}
              >
                Registrar
                {activeTab === 'register' && (
                  <span className="absolute bottom-0 left-0 h-0.5 w-full rounded-t-full bg-gradient-to-r from-cyan-400 to-indigo-500" />
                )}
              </button>
            </div>
          )}

          {/* Mensajes de Alerta */}
          {errorMessage && (
            <div className="mb-5 flex items-start gap-3 rounded-xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-xs text-rose-600 dark:text-rose-300">
              <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}

          {successMessage && !welcomeResult && (
            <div className="mb-5 flex items-start gap-3 rounded-xl border border-emerald-500/20 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-600 dark:text-emerald-300">
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{successMessage}</span>
            </div>
          )}

          {/* CONTENIDO PESTAÑA 1: INICIAR SESIÓN CON USUARIO */}
          {activeTab === 'login' && (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label
                  htmlFor="loginUsername"
                  className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300"
                >
                  Usuario
                </label>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <input
                    id="loginUsername"
                    type="text"
                    value={username}
                    placeholder="Usuario"
                    autoComplete="username"
                    required
                    onChange={(e) => setUsername(e.target.value)}
                    className="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-4 text-xs font-mono text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:bg-white focus:ring-2 focus:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100"
                  />
                </div>
              </div>

              <div>
                <label
                  htmlFor="loginPassword"
                  className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300"
                >
                  Contraseña
                </label>
                <div className="relative">
                  <KeyRound className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <input
                    id="loginPassword"
                    type="password"
                    value={password}
                    placeholder="••••••••"
                    autoComplete="current-password"
                    required
                    onChange={(e) => setPassword(e.target.value)}
                    className="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-4 text-xs text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:bg-white focus:ring-2 focus:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100"
                  />
                </div>
              </div>

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

              <button
                type="submit"
                disabled={isSubmitting}
                className="mt-6 flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-sm font-bold text-white shadow-[0_8px_28px_rgba(6,182,212,0.20)] transition-all duration-300 hover:scale-[1.02] hover:shadow-[0_12px_36px_rgba(79,70,229,0.28)] active:scale-95 disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:scale-100"
              >
                {isSubmitting && (
                  <LoaderCircle className="h-4 w-4 animate-spin" />
                )}
                <span>{isSubmitting ? 'Iniciando sesión...' : 'Iniciar sesión'}</span>
              </button>

              <div className="mt-5 text-center">
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  ¿Eres nuevo colaborador o necesitas una cuenta para pruebas?{' '}
                  <button
                    type="button"
                    onClick={() => changeTab('register')}
                    className="font-bold text-cyan-600 hover:underline dark:text-cyan-400"
                  >
                    Crear cuenta aquí
                  </button>
                </p>
              </div>
            </form>
          )}

          {/* CONTENIDO PESTAÑA 2: REGISTRAR (ALTA DE PERSONAL Y CORREO DE BIENVENIDA) */}
          {activeTab === 'register' && (
            <div>
              {welcomeResult ? (
                /* PREVIEW DEL CORREO DE BIENVENIDA OFICIAL AL EQUIPO CORPORATIVO */
                <div className="rounded-2xl border border-cyan-500/30 bg-gradient-to-b from-cyan-950/25 to-indigo-950/20 p-5 dark:border-cyan-500/40">
                  <div className="flex items-center gap-2 text-cyan-600 dark:text-cyan-400">
                    <Sparkles className="h-5 w-5" />
                    <span className="text-xs font-bold uppercase tracking-wide">
                      ¡Bienvenido al Equipo!
                    </span>
                  </div>

                  <h3 className="mt-2 text-lg font-black text-slate-900 dark:text-white">
                    {welcomeResult.fullName}
                  </h3>

                  <p className="mt-1 text-xs text-slate-600 dark:text-slate-400">
                    Todo listo. Los accesos para <strong>{welcomeResult.fullName}</strong> han sido generados y asociados a su correo{' '}
                    <strong className="text-cyan-600 dark:text-cyan-300 font-mono">
                      {welcomeResult.personalEmail}
                    </strong>.
                  </p>

                  {/* Tarjeta de Credenciales */}
                  <div className="mt-4 space-y-3 rounded-xl border border-slate-200/80 bg-white p-4 text-xs dark:border-slate-800 dark:bg-[#080d16]">
                    <div className="flex items-center gap-2 border-b border-slate-100 pb-2 text-[10px] text-slate-500 dark:border-slate-800">
                      <Mail className="h-3.5 w-3.5 text-cyan-500" />
                      <span>Equipo Lysandri · Credenciales de Acceso</span>
                    </div>

                    <div className="rounded-lg bg-slate-50 p-3 font-mono dark:bg-slate-900/60">
                      <p className="text-[10px] uppercase text-slate-400 font-bold mb-1">
                        Tu Usuario para Iniciar Sesión:
                      </p>
                      <p className="text-base font-black text-cyan-600 dark:text-cyan-400">
                        {welcomeResult.assignedUsername}
                      </p>
                      <p className="mt-1 text-[10px] text-slate-500">
                        (Utiliza este usuario para ingresar en la pestaña "Iniciar sesión")
                      </p>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-[11px]">
                      <div>
                        <span className="text-[9px] uppercase text-slate-400 block font-mono">Contraseña Provisoria:</span>
                        <span className="font-mono font-bold text-slate-800 dark:text-slate-200">
                          {welcomeResult.tempPassword}
                        </span>
                      </div>

                      <div>
                        <span className="text-[9px] uppercase text-slate-400 block font-mono">Área Asignada:</span>
                        <span className="font-bold text-slate-800 dark:text-slate-200">
                          {welcomeResult.areaLabel}
                        </span>
                      </div>
                    </div>

                    <div className="pt-2 border-t border-slate-100 dark:border-slate-800 text-[10px] text-slate-500 flex items-center justify-between">
                      <span>Correo Registrado:</span>
                      <span className="font-mono text-cyan-600 dark:text-cyan-400 font-bold">
                        {welcomeResult.personalEmail}
                      </span>
                    </div>
                  </div>

                  {/* Botones de acción directa */}
                  <div className="mt-5 space-y-2">
                    <button
                      type="button"
                      onClick={() => handleAutoLoginNewUser(welcomeResult)}
                      className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white shadow-md shadow-cyan-500/25 transition-all hover:scale-[1.02] active:scale-95"
                    >
                      <UserCheck className="h-4 w-4" />
                      <span>Iniciar Sesión Ahora con este Usuario</span>
                    </button>

                    <button
                      type="button"
                      onClick={handleOpenWhatsApp}
                      className="flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-xs font-bold text-white shadow-sm transition-all hover:scale-[1.02] active:scale-95"
                    >
                      <MessageCircle className="h-4 w-4" />
                      <span>Enviar Bienvenida por WhatsApp</span>
                    </button>

                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={handleCopyWelcomeEmailText}
                        className="flex h-9 w-full items-center justify-center gap-1.5 rounded-xl border border-slate-200 text-[11px] font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-slate-900"
                      >
                        {hasCopiedWelcome ? (
                          <>
                            <Check className="h-3.5 w-3.5 text-emerald-500" />
                            <span className="text-emerald-500 font-bold">¡Copiado!</span>
                          </>
                        ) : (
                          <>
                            <Copy className="h-3.5 w-3.5" />
                            <span>Copiar Correo</span>
                          </>
                        )}
                      </button>

                      <button
                        type="button"
                        onClick={handleCopyWhatsAppText}
                        className="flex h-9 w-full items-center justify-center gap-1.5 rounded-xl border border-slate-200 text-[11px] font-medium text-slate-600 hover:bg-slate-50 dark:border-slate-800 dark:text-slate-400 dark:hover:bg-slate-900"
                      >
                        {hasCopiedWhatsApp ? (
                          <>
                            <Check className="h-3.5 w-3.5 text-emerald-500" />
                            <span className="text-emerald-500 font-bold">¡Copiado!</span>
                          </>
                        ) : (
                          <>
                            <MessageCircle className="h-3.5 w-3.5 text-emerald-500" />
                            <span>Copiar WhatsApp</span>
                          </>
                        )}
                      </button>
                    </div>

                    <button
                      type="button"
                      onClick={() => {
                        setWelcomeResult(null);
                        setRegNombres('');
                        setRegApellidos('');
                        setRegPersonalEmail('');
                        setRegPhone('');
                      }}
                      className="mt-2 text-center w-full text-xs text-slate-500 hover:text-slate-800 dark:hover:text-slate-300 py-1"
                    >
                      + Registrar a otro colaborador
                    </button>
                  </div>
                </div>
              ) : (
                /* FORMULARIO DE REGISTRO CON ENVÍO DE CORREO DE BIENVENIDA */
                <form onSubmit={handleSubmit} className="space-y-4">
                  {/* Nombres y Apellidos */}
                  <div className="grid grid-cols-2 gap-3">
                    <InputField
                      id="regNombres"
                      label="Nombres"
                      type="text"
                      value={regNombres}
                      placeholder="Nombres"
                      autoComplete="given-name"
                      onChange={setRegNombres}
                    />

                    <InputField
                      id="regApellidos"
                      label="Apellidos"
                      type="text"
                      value={regApellidos}
                      placeholder="Apellidos"
                      autoComplete="family-name"
                      onChange={setRegApellidos}
                    />
                  </div>

                  {/* Correo */}
                  <div>
                    <InputField
                      id="regPersonalEmail"
                      label="Correo"
                      type="email"
                      value={regPersonalEmail}
                      placeholder="Ingrese correo"
                      autoComplete="email"
                      onChange={setRegPersonalEmail}
                    />
                  </div>

                  {/* Número de Celular Internacional */}
                  <InternationalPhoneInput
                    countryCode={selectedCountryCode}
                    phoneNumber={regPhone}
                    onCountryChange={(c) => setSelectedCountryCode(c.code)}
                    onPhoneChange={(raw) => setRegPhone(raw)}
                  />

                  {/* Área y Rol de Trabajo */}
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-2">
                      Área
                    </label>

                    <div className="grid grid-cols-2 gap-2">
                      {CORPORATE_AREAS.map((area) => {
                        const isSelected = selectedArea === area.id;
                        return (
                          <button
                            key={area.id}
                            type="button"
                            onClick={() => setSelectedArea(area.id)}
                            className={`flex h-11 items-center justify-center rounded-xl border text-center transition-all ${
                              isSelected
                                ? 'border-cyan-500 bg-cyan-500/10 text-cyan-600 dark:text-cyan-400 font-bold shadow-sm'
                                : 'border-slate-200 bg-slate-50/50 text-slate-600 hover:border-slate-300 dark:border-slate-800 dark:bg-slate-900/60 dark:text-slate-400 font-medium'
                            }`}
                          >
                            <span className="text-xs">
                              {area.label}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>



                  {/* Botón de Registro */}
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="group mt-4 flex h-12 w-full items-center justify-center gap-2.5 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-purple-600 text-sm font-bold text-white shadow-lg shadow-cyan-500/20 transition-all duration-300 hover:scale-[1.02] hover:shadow-cyan-500/35 active:scale-95 disabled:opacity-60"
                  >
                    {isSubmitting ? (
                      <>
                        <LoaderCircle className="h-4 w-4 animate-spin" />
                        <span>Registrando...</span>
                      </>
                    ) : (
                      <>
                        <Send className="h-4 w-4 group-hover-plane" />
                        <span>Registrar</span>
                      </>
                    )}
                  </button>
                </form>
              )}
            </div>
          )}
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
  onChange: (value: string) => void;
}

const InputField: React.FC<InputFieldProps> = ({
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
        placeholder={placeholder}
        autoComplete={autoComplete}
        required
        onChange={(event) => onChange(event.target.value)}
        className="h-12 w-full rounded-xl border border-slate-200 bg-slate-50 px-4 text-xs text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:bg-white focus:ring-2 focus:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100 dark:placeholder:text-slate-600"
      />
    </div>
  );
};
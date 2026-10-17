import React, {
  useCallback,
  useEffect,
  useState,
  useMemo,
} from 'react';

import {
  BookOpen,
  Building2,
  Check,
  Copy,
  Edit3,
  GraduationCap,
  Layers,
  LoaderCircle,
  Mail,
  MessageCircle,
  Plus,
  RefreshCw,
  Search,
  Send,
  ShieldAlert,
  Sparkles,
  Trash2,
  UserCheck,
  Users,
  ShieldCheck,
  X,
} from 'lucide-react';

import { getApiErrorMessage } from '../../services/authService';
import { academicService } from '../../services/academicService';
import { ProgramEditorModal } from '../../components/admin/ProgramEditorModal';

import {
  ProgramaResponse,
  UserRole,
  UsuarioResponse,
} from '../../types';

import {
  formatInternationalPhoneDisplay,
  parsePhoneString,
  getCountryByCode,
} from '../../utils/countries';
import { InternationalPhoneInput } from '../../components/common/InternationalPhoneInput';

interface AdminDashboardProps {
  initialSection?: 'users' | 'programs';
}

const ROLE_LABELS: Record<UserRole | string, string> = {
  ESTUDIANTE: 'Ejecutivo',
  CLIENTE: 'Ejecutivo',
  INSTRUCTOR: 'Profesor',
  DOCENTE: 'Profesor',
  ADMIN: 'Administrador',
};

const formatDate = (date?: string): string => {
  if (!date) {
    return 'Sin fecha';
  }
  try {
    const d = date.includes('T') ? new Date(date) : new Date(`${date}T00:00:00`);
    if (isNaN(d.getTime())) return date;
    return new Intl.DateTimeFormat('es-PE', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    }).format(d);
  } catch {
    return date;
  }
};

export const AdminDashboard: React.FC<
  AdminDashboardProps
> = ({ initialSection = 'programs' }) => {
  const [section, setSection] = useState<
    'users' | 'programs'
  >(initialSection);

  const [users, setUsers] = useState<
    UsuarioResponse[]
  >([]);

  const [programs, setPrograms] = useState<
    ProgramaResponse[]
  >([]);

  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] =
    useState(true);

  const [errorMessage, setErrorMessage] =
    useState('');

  // Course & Syllabus Editor State
  const [isEditorOpen, setIsEditorOpen] = useState(false);
  const [editingProgram, setEditingProgram] = useState<ProgramaResponse | null>(null);
  const [editorInitialStep, setEditorInitialStep] = useState<1 | 2 | 3 | 4>(1);

  // User Editor State
  const [isUserEditorOpen, setIsUserEditorOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UsuarioResponse | null>(null);

  useEffect(() => {
    setSection(initialSection);
    setSearch('');
  }, [initialSection]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');

    try {
      const [usersResult, programsResult] =
        await Promise.allSettled([
          academicService.getUsers(),
          academicService.getPrograms(),
        ]);

      if (usersResult.status === 'fulfilled') {
        setUsers(usersResult.value);
      }
      if (programsResult.status === 'fulfilled') {
        setPrograms(programsResult.value);
      }

      if (
        usersResult.status === 'rejected' &&
        programsResult.status === 'rejected'
      ) {
        setErrorMessage(
          getApiErrorMessage(programsResult.reason),
        );
      }
    } catch (error) {
      setErrorMessage(
        getApiErrorMessage(error),
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    setSection(initialSection);
    setSearch('');
  }, [initialSection]);

  const handleOpenNewProgram = () => {
    setEditingProgram(null);
    setEditorInitialStep(1);
    setIsEditorOpen(true);
  };

  const handleEditProgram = (program: ProgramaResponse) => {
    setEditingProgram(program);
    setEditorInitialStep(1);
    setIsEditorOpen(true);
  };

  const handleDeleteProgram = async (program: ProgramaResponse) => {
    const confirm = window.confirm(
      `¿Estás seguro de que deseas eliminar el programa "${program.tituloPrograma}"?\nEsta acción no se puede deshacer.`
    );
    if (!confirm) return;

    try {
      await academicService.deleteProgram(program.idPrograma);
      await loadData();
    } catch (error) {
      alert('Error al eliminar el programa: ' + getApiErrorMessage(error));
    }
  };

  const handleSaveProgram = async (programData: Partial<ProgramaResponse>) => {
    await academicService.saveProgram(programData);
    setIsEditorOpen(false);
    await loadData();
  };

  const handleEditUser = (user: UsuarioResponse) => {
    setEditingUser(user);
    setIsUserEditorOpen(true);
  };

  const handleOpenNewUser = () => {
    setEditingUser(null);
    setIsUserEditorOpen(true);
  };

  const handleDeleteUser = async (user: UsuarioResponse) => {
    const confirm = window.confirm(
      `¿Estás seguro de que deseas eliminar al usuario "${user.nombres} ${user.apellidos}"?\nEsta acción no se puede deshacer.`
    );
    if (!confirm) return;

    try {
      await academicService.deleteUser(user.idUser);
      await loadData();
    } catch (error) {
      alert('Error al eliminar el usuario: ' + getApiErrorMessage(error));
    }
  };

  const handleSaveUser = async (userData: Partial<UsuarioResponse> & { passw?: string }) => {
    if (editingUser) {
      await academicService.updateUser(editingUser.idUser, userData);
    } else {
      await academicService.createUser({
        nombres: userData.nombres || '',
        apellidos: userData.apellidos || '',
        email: userData.email || '',
        passw: userData.passw || '123456',
        telefono: userData.telefono || '',
        rol: userData.rol || 'ESTUDIANTE',
      });
    }
    setIsUserEditorOpen(false);
    await loadData();
  };

  const normalizedSearch =
    search.trim().toLowerCase();

  const filteredUsers = users.filter((user) => {
    if (!user) return false;
    const completeName = `${user.nombres || ''} ${user.apellidos || ''}`.toLowerCase();
    const email = (user.email || '').toLowerCase();
    const rol = (user.rol || '').toLowerCase();

    return (
      !normalizedSearch ||
      completeName.includes(normalizedSearch) ||
      email.includes(normalizedSearch) ||
      rol.includes(normalizedSearch)
    );
  });

  const filteredPrograms = programs.filter(
    (program) => {
      return (
        !normalizedSearch ||
        (program.titulo || program.tituloPrograma || '')
          .toLowerCase()
          .includes(normalizedSearch) ||
        program.nombreInstructor
          ?.toLowerCase()
          .includes(normalizedSearch) ||
        program.tipoPrograma
          ?.toLowerCase()
          .includes(normalizedSearch)
      );
    },
  );

  const executiveCount = users.filter(
    (user) => user && (user.rol === 'ESTUDIANTE' || (user.rol as string) === 'CLIENTE'),
  ).length;

  const instructorCount = users.filter(
    (user) => user && (user.rol === 'INSTRUCTOR' || (user.rol as string) === 'DOCENTE'),
  ).length;

  return (
    <section className={`lx-admin-page lx-admin-register-page lx-admin-${section}`}>
      <header className="lx-admin-pagehead">
        <div className="lx-admin-pagehead-copy">
          <div>
            <h1>
              {section === 'users'
                ? 'Usuarios y accesos'
                : 'Programas académicos'}
            </h1>

            <p>
              {section === 'users'
                ? 'Administra las cuentas y los accesos de ejecutivos, profesores y administradores.'
                : 'Organiza programas, docentes y mallas curriculares desde un solo registro.'}
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={section === 'users' ? handleOpenNewUser : handleOpenNewProgram}
          className="lx-admin-primary"
        >
          <Plus className="h-4 w-4" />
          <span>{section === 'users' ? 'Nuevo usuario' : 'Nuevo programa'}</span>
        </button>
      </header>

      {section === 'programs' ? (
        <div className="lx-admin-metric-strip" aria-label="Resumen de programas">
          <MetricCard
            label="Programas en catálogo"
            value={String(programs.length)}
            icon={<BookOpen />}
            tone="cyan"
          />

          <MetricCard
            label="Mallas curriculares"
            value={String(programs.filter((p) => p.syllabus && p.syllabus.length > 0).length)}
            icon={<Layers />}
            tone="orange"
          />

          <MetricCard
            label="Docentes titulares"
            value={String(new Set(programs.map((p) => p.nombreInstructor).filter(Boolean)).size)}
            icon={<GraduationCap />}
            tone="violet"
          />

          <MetricCard
            label="Nivel Enterprise"
            value={String(programs.filter((p) => p.level === 'ENTERPRISE').length)}
            icon={<Building2 />}
            tone="green"
          />
        </div>
      ) : (
        <div className="lx-admin-metric-strip" aria-label="Resumen de usuarios">
          <MetricCard
            label="Usuarios registrados"
            value={String(users.length)}
            icon={<Users />}
            tone="cyan"
          />

          <MetricCard
            label="Ejecutivos inscritos"
            value={String(executiveCount)}
            icon={<UserCheck />}
            tone="orange"
          />

          <MetricCard
            label="Profesores titulares"
            value={String(instructorCount)}
            icon={<GraduationCap />}
            tone="violet"
          />

          <MetricCard
            label="Administradores"
            value={String(users.filter((u) => u.rol === 'ADMIN').length)}
            icon={<ShieldCheck />}
            tone="green"
          />
        </div>
      )}

      <section className="lx-admin-register">
        <div className="lx-admin-register-toolbar">
          <div className="lx-admin-register-title">
            <h2>{section === 'programs' ? 'Registro de programas' : 'Directorio de usuarios'}</h2>
            <span>{section === 'programs' ? programs.length : users.length} registros</span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="relative flex-1 md:w-64">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />

              <input
                type="search"
                value={search}
                onChange={(event) =>
                  setSearch(event.target.value)
                }
                placeholder={
                  section === 'users'
                    ? 'Buscar usuario...'
                    : 'Buscar por título o instructor...'
                }
                className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50 pl-10 pr-3 text-xs outline-none focus:border-cyan-500/50 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
              />
            </div>

            <button
              type="button"
              onClick={() => void loadData()}
              aria-label="Actualizar datos"
              className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 text-slate-500 transition-colors hover:border-cyan-500/30 hover:text-cyan-500 dark:border-slate-800"
            >
              <RefreshCw className="h-4 w-4" />
            </button>
          </div>
        </div>

        {errorMessage && (
          <div className="m-4 flex items-center gap-3 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-600 dark:text-rose-300">
            <ShieldAlert className="h-5 w-5 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {isLoading ? (
          <div className="flex min-h-72 items-center justify-center">
            <div className="flex items-center gap-3 text-sm text-slate-500">
              <LoaderCircle className="h-5 w-5 animate-spin text-cyan-500" />
              Cargando información...
            </div>
          </div>
        ) : section === 'users' ? (
          <UsersTable
            users={filteredUsers}
            onEditUser={handleEditUser}
            onDeleteUser={handleDeleteUser}
          />
        ) : (
          <ProgramsTable
            programs={filteredPrograms}
            onEditProgram={handleEditProgram}
            onDeleteProgram={handleDeleteProgram}
            onAddNewProgram={handleOpenNewProgram}
          />
        )}
      </section>

      {/* Course & Syllabus Creator Modal */}
      <ProgramEditorModal
        isOpen={isEditorOpen}
        onClose={() => setIsEditorOpen(false)}
        program={editingProgram}
        initialStep={editorInitialStep}
        onSave={handleSaveProgram}
      />

      {/* Modal para Editar Usuario & Corregir Celular */}
      <UserEditorModal
        isOpen={isUserEditorOpen}
        onClose={() => setIsUserEditorOpen(false)}
        user={editingUser}
        onSave={handleSaveUser}
      />
    </section>
  );
};

interface MetricCardProps {
  label: string;
  value: string;
  icon: React.ReactNode;
  tone: 'cyan' | 'orange' | 'violet' | 'green';
}

const MetricCard: React.FC<MetricCardProps> = ({
  label,
  value,
  icon,
  tone,
}) => {
  return (
    <article className="lx-admin-metric">
      <div className={`lx-admin-metric-mark lx-admin-metric-mark-${tone}`} aria-hidden="true">{icon}</div>
      <div className="lx-admin-metric-copy">
        <p className="text-xs text-slate-500">{label}</p>
        <p className="mt-2 font-mono text-3xl font-black text-slate-950 dark:text-white">{value}</p>
      </div>
    </article>
  );
};

const renderUserPhoneCell = (phone?: string) => {
  if (!phone) {
    return <span className="italic text-slate-400">Sin registrar</span>;
  }
  const { text, isDniWarning } = formatInternationalPhoneDisplay(phone);
  if (isDniWarning) {
    return (
      <div className="flex flex-col gap-1">
        <span className="font-mono text-xs font-bold text-slate-900 dark:text-slate-100">
          {phone}
        </span>
        <span className="lx-user-phone-warning">
          <ShieldAlert aria-hidden="true" /> Ingresado como DNI
        </span>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-1.5 font-mono text-xs font-semibold text-slate-800 dark:text-slate-200">
      <span>{text}</span>
    </div>
  );
};

interface UsersTableProps {
  users: UsuarioResponse[];
  onEditUser: (user: UsuarioResponse) => void;
  onDeleteUser: (user: UsuarioResponse) => void;
}

const UsersTable: React.FC<UsersTableProps> = ({
  users,
  onEditUser,
  onDeleteUser,
}) => {
  if (users.length === 0) {
    return (
      <EmptyState message="No se encontraron usuarios." />
    );
  }

  return (
    <div className="lx-user-directory" role="list" aria-label="Directorio de usuarios">
      <div className="lx-user-directory-heading" aria-hidden="true">
        <span>PERSONA / ROL</span>
        <span>CORREO DE ACCESO</span>
        <span>CONTACTO</span>
        <span />
      </div>
      {users.map((user) => {
        const displayName = `${user.nombres || ''} ${user.apellidos || ''}`.trim();
        const initials = displayName
          .split(/\s+/)
          .filter(Boolean)
          .slice(0, 2)
          .map((part) => part[0])
          .join('')
          .toUpperCase() || 'U';
        const roleLabel = ROLE_LABELS[user.rol] || user.rol || 'Ejecutivo';

        return (
          <article className="lx-user-row" role="listitem" key={user.idUser}>
            <div className="lx-user-record">
              <span className={`lx-user-avatar lx-user-avatar-${String(user.rol).toLowerCase()}`} aria-hidden="true">{initials}</span>
              <div className="lx-user-name-wrap">
                <div className="lx-user-name-line">
                  <h3>{displayName || 'Usuario sin nombre'}</h3>
                  <span className={`lx-user-role lx-user-role-${String(user.rol).toLowerCase()}`}>{roleLabel}</span>
                </div>
                <p className="lx-user-id" title={`ID #${user.idUser}`}>Cuenta #{user.idUser}</p>
              </div>
            </div>
            <div className="lx-user-contact">
              <span>Correo</span>
              <strong title={user.email}>{user.email || 'Sin registrar'}</strong>
            </div>
            <div className="lx-user-contact lx-user-phone">
              <span>Contacto</span>
              <div className="lx-user-phone-value">{renderUserPhoneCell(user.telefono)}</div>
            </div>
            <div className="lx-user-row-actions">
              <button
                type="button"
                onClick={() => onEditUser(user)}
                title="Editar usuario o corregir celular"
                aria-label={`Editar a ${displayName || 'este usuario'}`}
                className="lx-user-edit"
              >
                <Edit3 className="h-3.5 w-3.5" />
                <span>Editar usuario</span>
              </button>
              <button
                type="button"
                onClick={() => onDeleteUser(user)}
                title="Eliminar usuario"
                aria-label={`Eliminar a ${displayName || 'este usuario'}`}
                className="lx-user-delete"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
          </article>
        );
      })}
    </div>
  );
};

interface CorporateAreaConfig {
  id: string;
  label: string;
  role: UserRole;
  desc: string;
}

const REGISTRATION_AREAS: CorporateAreaConfig[] = [
  {
    id: 'ADMINISTRACION',
    label: 'Administración',
    role: 'ADMIN',
    desc: 'Gestión académica, creación de cursos, mallas y control de plataforma.',
  },
  {
    id: 'CONTABILIDAD',
    label: 'Contabilidad',
    role: 'ADMIN',
    desc: 'Facturación electrónica, liquidación tributaria SUNAT e informes SIRE.',
  },
  {
    id: 'DOCENTE',
    label: 'Docente',
    role: 'INSTRUCTOR',
    desc: 'Dictado de clases, tutoría académica y asignación de notas.',
  },
  {
    id: 'ESTUDIANTE',
    label: 'Ejecutivo',
    role: 'ESTUDIANTE',
    desc: 'Acceso a programas ejecutivos, aulas virtuales y certificaciones.',
  },
];

interface GeneratedWelcome {
  fullName: string;
  personalEmail: string;
  assignedUsername: string;
  areaId: string;
  areaLabel: string;
  role: UserRole;
  activationCode: string;
  tempPassword: string;
  phone: string;
  date: string;
}

interface UserEditorModalProps {
  isOpen: boolean;
  onClose: () => void;
  user: UsuarioResponse | null;
  onSave: (userData: Partial<UsuarioResponse> & { passw?: string }) => Promise<void>;
}

const UserEditorModal: React.FC<UserEditorModalProps> = ({
  isOpen,
  onClose,
  user,
  onSave,
}) => {
  const isEditing = Boolean(user);
  const [nombres, setNombres] = useState('');
  const [apellidos, setApellidos] = useState('');
  const [email, setEmail] = useState('');
  const [telefono, setTelefono] = useState('');
  const [selectedCountryCode, setSelectedCountryCode] = useState('PE');
  const [selectedArea, setSelectedArea] = useState('ADMINISTRACION');
  const [rol, setRol] = useState<UserRole>('ADMIN');
  const [error, setError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  // Estados de bienvenida oficiales idénticos a LoginView
  const [welcomeResult, setWelcomeResult] = useState<GeneratedWelcome | null>(null);
  const [hasCopiedWelcome, setHasCopiedWelcome] = useState(false);
  const [hasCopiedWhatsApp, setHasCopiedWhatsApp] = useState(false);

  useEffect(() => {
    if (user) {
      setNombres(user.nombres || '');
      setApellidos(user.apellidos || '');
      setEmail(user.email || '');

      const { country, localDigits } = parsePhoneString(user.telefono);
      setSelectedCountryCode(country.code);
      setTelefono(localDigits);

      setRol(user.rol || 'ESTUDIANTE');
      const foundArea = REGISTRATION_AREAS.find((a) => a.role === user.rol);
      setSelectedArea(foundArea?.id || 'ESTUDIANTE');
      setError('');
      setWelcomeResult(null);
    } else {
      setNombres('');
      setApellidos('');
      setEmail('');
      setSelectedCountryCode('PE');
      setTelefono('');
      setSelectedArea('ADMINISTRACION');
      setRol('ADMIN');
      setError('');
      setWelcomeResult(null);
    }
  }, [user, isOpen]);

  // Generación automática de usuario a partir de nombres y apellidos (ej: jared quizpe -> jaredquiz21)
  const generatedUsername = useMemo(() => {
    const normalize = (str: string) =>
      str
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]/g, '');

    const first = normalize(nombres.trim().split(' ')[0] || '');
    const last = normalize(apellidos.trim().split(' ')[0] || '');
    const lastShort = last.slice(0, 4);

    const phoneDigits = telefono.replace(/\D/g, '');
    const suffix = phoneDigits.length >= 2 ? phoneDigits.slice(-2) : '21';

    if (first && lastShort) return `${first}${lastShort}${suffix}`;
    if (first) return `${first}${suffix}`;
    return 'usuario21';
  }, [nombres, apellidos, telefono]);

  // Contraseña personalizada derivada del nombre (ej: Jared2026!)
  const generatedPassword = useMemo(() => {
    const clean = (s: string) =>
      s
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-zA-Z]/g, '');

    const first = clean(nombres.trim().split(' ')[0] || 'Lysandri');
    const cap = first.charAt(0).toUpperCase() + first.slice(1).toLowerCase();
    return `${cap}2026!`;
  }, [nombres]);

  if (!isOpen) return null;

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

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!nombres.trim() || !apellidos.trim()) {
      setError('Por favor ingresa los nombres y apellidos completos.');
      return;
    }

    if (!email.trim() || !email.includes('@')) {
      setError('Por favor ingresa el correo personal donde se enviará la bienvenida.');
      return;
    }

    const currentCountry = getCountryByCode(selectedCountryCode);
    const phoneDigits = telefono.replace(/\D/g, '');
    if (!phoneDigits) {
      setError(`Por favor ingresa el número de celular (${currentCountry.name}).`);
      return;
    }

    if (phoneDigits.length < 6) {
      setError(`Por favor ingresa un número de celular válido para ${currentCountry.name}.`);
      return;
    }

    const formattedPhone = `${currentCountry.dialCode} ${phoneDigits}`;
    const areaConfig = REGISTRATION_AREAS.find((a) => a.id === selectedArea) || REGISTRATION_AREAS[0];
    const roleToAssign = isEditing ? rol : areaConfig.role;

    setIsSaving(true);
    try {
      if (isEditing) {
        await onSave({
          nombres: nombres.trim(),
          apellidos: apellidos.trim(),
          email: email.trim().toLowerCase(),
          telefono: formattedPhone,
          rol: roleToAssign,
        });
        onClose();
      } else {
        const tempPassword = generatedPassword;
        const usernameToAssign = generatedUsername;
        const activationCode = `INT-${Math.floor(10000 + Math.random() * 90000)}`;

        await onSave({
          nombres: nombres.trim(),
          apellidos: apellidos.trim(),
          email: email.trim().toLowerCase(),
          telefono: formattedPhone,
          rol: roleToAssign,
          passw: tempPassword,
        });

        const welcomeData: GeneratedWelcome = {
          fullName: `${nombres.trim()} ${apellidos.trim()}`,
          personalEmail: email.trim().toLowerCase(),
          assignedUsername: usernameToAssign,
          areaId: selectedArea,
          areaLabel: areaConfig.label,
          role: roleToAssign,
          activationCode,
          tempPassword,
          phone: formattedPhone,
          date: new Date().toLocaleDateString('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }),
        };

        try {
          const stored = localStorage.getItem('lysandri_corporate_invitations');
          const list: GeneratedWelcome[] = stored ? JSON.parse(stored) : [];
          list.unshift(welcomeData);
          localStorage.setItem('lysandri_corporate_invitations', JSON.stringify(list));
        } catch {
          // ignore
        }

        setWelcomeResult(welcomeData);
      }
    } catch (err) {
      setError(getApiErrorMessage(err));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="lx-admin-modal fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg rounded-3xl border border-cyan-500/20 bg-white p-6 shadow-2xl dark:border-slate-800 dark:bg-[#0c111a] sm:p-7 max-h-[92vh] overflow-y-auto">
        {/* Cabecera del Panel */}
        <div className="flex items-center justify-between pb-4 border-b border-slate-100 dark:border-slate-800">
          <div>
            <span className="font-mono text-[10px] uppercase tracking-wider text-cyan-600 dark:text-cyan-400 font-bold">
              {isEditing ? 'Administración de Usuario' : 'Registro de Personal'}
            </span>
            <h3 className="text-xl font-black text-slate-900 dark:text-white mt-0.5">
              {isEditing ? 'Editar Usuario & Celular' : 'Registrar'}
            </h3>
            {!welcomeResult && (
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1 leading-relaxed">
                {isEditing
                  ? 'Actualiza los datos o corrige el número de celular del usuario.'
                  : 'Registra al personal. Su correo personal se usará únicamente para enviarle la bienvenida oficial al equipo corporativo con su nuevo usuario.'}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors shrink-0"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {error && (
          <div className="mt-4 flex items-start gap-2.5 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-xs text-rose-600 dark:text-rose-300">
            <ShieldAlert className="h-4 w-4 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {welcomeResult ? (
          /* PREVIEW DEL CORREO DE BIENVENIDA OFICIAL AL EQUIPO CORPORATIVO (IDÉNTICO A LOGINVIEW) */
          <div className="mt-4 rounded-2xl border border-cyan-500/30 bg-gradient-to-b from-cyan-950/25 to-indigo-950/20 p-5 dark:border-cyan-500/40">
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
                  (Utiliza este usuario para ingresar en la plataforma)
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
            <div className="mt-4 space-y-2">
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

              <div className="flex items-center gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => {
                    setWelcomeResult(null);
                    setNombres('');
                    setApellidos('');
                    setEmail('');
                    setTelefono('');
                  }}
                  className="flex-1 text-center text-xs font-bold text-cyan-600 dark:text-cyan-400 hover:underline py-2"
                >
                  + Registrar a otro colaborador
                </button>

                <button
                  type="button"
                  onClick={onClose}
                  className="rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-4 py-2 text-xs font-bold text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                >
                  Listo / Cerrar
                </button>
              </div>
            </div>
          </div>
        ) : (
          /* FORMULARIO DE REGISTRO CON ENVÍO DE CORREO DE BIENVENIDA (ESPECIFICACIONES IDÉNTICAS A LOGINVIEW) */
          <form onSubmit={handleFormSubmit} className="mt-5 space-y-4">
            {/* Nombres y Apellidos */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Nombres
                </label>
                <input
                  type="text"
                  required
                  value={nombres}
                  placeholder="Nombres"
                  autoComplete="given-name"
                  onChange={(e) => setNombres(e.target.value)}
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 text-xs text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:bg-white focus:ring-2 focus:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Apellidos
                </label>
                <input
                  type="text"
                  required
                  value={apellidos}
                  placeholder="Apellidos"
                  autoComplete="family-name"
                  onChange={(e) => setApellidos(e.target.value)}
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 text-xs text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:bg-white focus:ring-2 focus:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100"
                />
              </div>
            </div>

            {/* Correo */}
            <div>
              <label className="mb-1.5 block text-xs font-semibold text-slate-700 dark:text-slate-300">
                Correo
              </label>
              <input
                type="email"
                required
                value={email}
                placeholder="Ingrese correo"
                autoComplete="email"
                onChange={(e) => setEmail(e.target.value)}
                className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 text-xs text-slate-900 outline-none transition-all placeholder:text-slate-400 focus:border-cyan-500/60 focus:bg-white focus:ring-2 focus:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-100 font-mono"
              />
            </div>

            {/* Número de Celular Internacional */}
            <InternationalPhoneInput
              countryCode={selectedCountryCode}
              phoneNumber={telefono}
              onCountryChange={(c) => setSelectedCountryCode(c.code)}
              onPhoneChange={(raw) => setTelefono(raw)}
            />

            {/* Área y Rol de Trabajo (Tarjetas idénticas a LoginView) */}
            {!isEditing ? (
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-2">
                  Área
                </label>

                <div className="grid grid-cols-2 gap-2">
                  {REGISTRATION_AREAS.map((area) => {
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
                        <span className="text-xs">{area.label}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ) : (
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1">
                  Rol del Usuario
                </label>
                <select
                  value={rol}
                  onChange={(e) => setRol(e.target.value as UserRole)}
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 text-xs outline-none focus:border-cyan-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                >
                  <option value="ESTUDIANTE">Ejecutivo / Alumno</option>
                  <option value="INSTRUCTOR">Profesor / Docente</option>
                  <option value="ADMIN">Administrador</option>
                </select>
              </div>
            )}

            {/* Botón de Registro con gradiente e ícono Send */}
            <div className="pt-2">
              <button
                type="submit"
                disabled={isSaving}
                className="group flex h-12 w-full items-center justify-center gap-2.5 rounded-xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-purple-600 text-xs font-bold text-white shadow-lg shadow-cyan-500/20 transition-all duration-300 hover:scale-[1.01] hover:shadow-cyan-500/35 active:scale-95 disabled:opacity-60"
              >
                {isSaving ? (
                  <>
                    <LoaderCircle className="h-4 w-4 animate-spin" />
                    <span>{isEditing ? 'Guardando...' : 'Registrando...'}</span>
                  </>
                ) : (
                  <>
                    <Send className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
                    <span>{isEditing ? 'Guardar Cambios' : 'Registrar'}</span>
                  </>
                )}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

interface ProgramsTableProps {
  programs: ProgramaResponse[];
  onEditProgram: (program: ProgramaResponse) => void;
  onDeleteProgram: (program: ProgramaResponse) => void;
  onAddNewProgram: () => void;
}

const ProgramsTable: React.FC<ProgramsTableProps> = ({
  programs,
  onEditProgram,
  onDeleteProgram,
  onAddNewProgram,
}) => {
  if (programs.length === 0) {
    return (
      <div className="flex min-h-64 flex-col items-center justify-center p-8 text-center">
        <BookOpen className="h-10 w-10 text-slate-300 dark:text-slate-700" />
        <p className="mt-3 text-sm text-slate-500">
          No se encontraron programas académicos.
        </p>
        <button
          type="button"
          onClick={onAddNewProgram}
          className="mt-4 inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-4 py-2.5 text-xs font-bold text-white shadow-md shadow-cyan-500/20 hover:brightness-110"
        >
          <Plus className="h-4 w-4" />
          <span>Crear Primer Programa</span>
        </button>
      </div>
    );
  }

  return (
    <div className="lx-program-grid" role="list" aria-label="Catálogo de programas">
      {programs.map((program, index) => {
            const hasCustomSyllabus = Boolean(program.syllabus && program.syllabus.length > 0);
            const totalModules = program.syllabus?.length || 0;
            const totalSessions = program.syllabus?.reduce((acc, m) => acc + (m.temas?.length || 0), 0) || 0;
            const level = program.level || 'Nivel no definido';

            return (
              <article
                key={program.idPrograma}
                className="lx-program-card"
                role="listitem"
              >
                <div className="lx-program-artwork">
                  {program.coverUrl ? (
                    <img src={program.coverUrl} alt="" loading={index > 3 ? 'lazy' : 'eager'} />
                  ) : (
                    <div className="lx-program-placeholder">
                      <BookOpen aria-hidden="true" />
                      <span>Portada no disponible</span>
                    </div>
                  )}
                </div>

                <div className="lx-program-card-content">
                  <div className="lx-program-meta">
                    <span>{program.tipoPrograma || 'Programa de Especialización'}</span>
                    <span>{level}</span>
                  </div>
                  <h3>{program.tituloPrograma}</h3>

                  <div className="lx-program-faculty">
                    <span className="lx-faculty-avatar" aria-hidden="true">
                      {(program.nombreInstructor || 'S').split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase()}
                    </span>
                    <div>
                      <span>Docente titular</span>
                      <strong>{program.nombreInstructor || 'Sin asignar'}</strong>
                    </div>
                  </div>

                  <dl className="lx-program-facts">
                    <div>
                      <dt>Precio</dt>
                      <dd>{typeof program.precio === 'number' ? `S/ ${program.precio.toLocaleString('es-PE')}` : 'Por definir'}</dd>
                    </div>
                    <div>
                      <dt>Duración</dt>
                      <dd>{program.duracionPrograma || 'Por definir'}</dd>
                      <small>Inicio · {formatDate(program.fechaInicioGlobal)}</small>
                    </div>
                  </dl>

                  <div className={`lx-program-syllabus ${hasCustomSyllabus ? 'is-structured' : 'is-standard'}`}>
                    <span>
                      <strong>{hasCustomSyllabus ? `${totalModules} módulos · ${totalSessions} temas` : 'Malla estándar'}</strong>
                      <small>{hasCustomSyllabus ? 'Estructura curricular activa' : 'Sin personalizar'}</small>
                    </span>
                  </div>

                  <div className="lx-program-actions">
                    <button type="button" onClick={() => onEditProgram(program)} title="Editar programa">
                      <Edit3 aria-hidden="true" /> Editar programa
                    </button>
                    <button
                      type="button"
                      onClick={() => onDeleteProgram(program)}
                      title="Eliminar programa del catálogo"
                      aria-label={`Eliminar ${program.tituloPrograma}`}
                    >
                      <Trash2 aria-hidden="true" />
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
    </div>
  );
};

const EmptyState: React.FC<{
  message: string;
}> = ({ message }) => {
  return (
    <div className="flex min-h-64 flex-col items-center justify-center p-8 text-center">
      <BookOpen className="h-10 w-10 text-slate-300 dark:text-slate-700" />

      <p className="mt-3 text-sm text-slate-500">
        {message}
      </p>
    </div>
  );
};

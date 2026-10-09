import React, {
  useCallback,
  useEffect,
  useState,
} from 'react';

import {
  BookOpen,
  CalendarDays,
  Check,
  CheckCircle2,
  Copy,
  DollarSign,
  Edit3,
  GraduationCap,
  KeyRound,
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
  Users,
  X,
} from 'lucide-react';

import { getApiErrorMessage } from '../../services/authService';
import { academicService } from '../../services/academicService';
import { ProgramEditorModal } from '../../components/admin/ProgramEditorModal';
import {
  generateUniqueUsername,
  generateSecurePassword,
  checkDuplicateAccount,
} from '../../utils/credentials';

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

const ROLE_STYLES: Record<UserRole | string, string> = {
  ESTUDIANTE:
    'border-cyan-500/20 bg-cyan-500/10 text-cyan-600 dark:text-cyan-300',
  CLIENTE:
    'border-cyan-500/20 bg-cyan-500/10 text-cyan-600 dark:text-cyan-300',
  INSTRUCTOR:
    'border-indigo-500/20 bg-indigo-500/10 text-indigo-600 dark:text-indigo-300',
  DOCENTE:
    'border-indigo-500/20 bg-indigo-500/10 text-indigo-600 dark:text-indigo-300',
  ADMIN:
    'border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-300',
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
      setIsUserEditorOpen(false);
    } else {
      await academicService.createUser({
        nombres: userData.nombres || '',
        apellidos: userData.apellidos || '',
        email: userData.email || '',
        passw: userData.passw || '',
        telefono: userData.telefono || '',
        rol: userData.rol || 'ESTUDIANTE',
      });
      // Dejar modal abierto en pantalla de bienvenida para que el administrador copie credenciales o envíe WhatsApp
    }
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

  const filteredPrograms = programs.filter((program) => {
    if (!program) return false;
    const title = (program.tituloPrograma || '').toLowerCase();
    const instructor = (program.nombreInstructor || '').toLowerCase();
    const type = (program.tipoPrograma || '').toLowerCase();

    return (
      !normalizedSearch ||
      title.includes(normalizedSearch) ||
      instructor.includes(normalizedSearch) ||
      type.includes(normalizedSearch)
    );
  });

  const executiveCount = users.filter(
    (user) => user && (user.rol === 'ESTUDIANTE' || (user.rol as string) === 'CLIENTE'),
  ).length;

  const instructorCount = users.filter(
    (user) => user && (user.rol === 'INSTRUCTOR' || (user.rol as string) === 'DOCENTE'),
  ).length;

  return (
    <section className="space-y-6">
      <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-7 dark:border-slate-800 dark:bg-[#0c111a] md:p-9">
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-cyan-500/10 blur-3xl" />

        <div className="relative flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <span className="inline-flex rounded-full border border-cyan-500/20 bg-cyan-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-cyan-600 dark:text-cyan-400">
              Administración Académica
            </span>

            <h1 className="mt-4 text-3xl font-black text-slate-950 dark:text-white">
              {section === 'users'
                ? 'Gestión de Usuarios & Accesos'
                : 'Gestión de Programas'}
            </h1>

            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">
              {section === 'users'
                ? 'Administra las cuentas y credenciales de ejecutivos, profesores y administradores de la plataforma.'
                : 'Crea nuevos cursos ejecutivos, estructura mallas curriculares por módulos y sesiones, y administra los contenidos.'}
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {section === 'users' ? (
              <button
                type="button"
                onClick={handleOpenNewUser}
                className="inline-flex items-center gap-2 rounded-2xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-purple-600 px-5 py-3.5 text-xs font-bold text-white shadow-lg shadow-cyan-500/25 transition-all hover:scale-[1.02] hover:shadow-cyan-500/40 active:scale-95"
              >
                <Plus className="h-4 w-4 stroke-[3]" />
                <span>Nuevo Usuario</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={handleOpenNewProgram}
                className="inline-flex items-center gap-2 rounded-2xl bg-gradient-to-r from-cyan-500 via-indigo-600 to-purple-600 px-5 py-3.5 text-xs font-bold text-white shadow-lg shadow-cyan-500/25 transition-all hover:scale-[1.02] hover:shadow-cyan-500/40 active:scale-95"
              >
                <Plus className="h-4 w-4 stroke-[3]" />
                <span>Nuevo Programa</span>
              </button>
            )}
          </div>
        </div>
      </div>

      {section === 'programs' ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard
            label="Programas en catálogo"
            value={String(programs.length)}
            icon={<Layers className="h-5 w-5 text-indigo-500" />}
          />

          <MetricCard
            label="Mallas curriculares"
            value={String(programs.filter((p) => p.syllabus && p.syllabus.length > 0).length)}
            icon={<BookOpen className="h-5 w-5 text-cyan-500" />}
          />

          <MetricCard
            label="Docentes titulares"
            value={String(new Set(programs.map((p) => p.nombreInstructor).filter(Boolean)).size)}
            icon={<GraduationCap className="h-5 w-5 text-purple-500" />}
          />

          <MetricCard
            label="Nivel Enterprise"
            value={String(programs.filter((p) => p.level === 'ENTERPRISE').length)}
            icon={<Layers className="h-5 w-5 text-emerald-500" />}
          />
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <MetricCard
            label="Usuarios registrados"
            value={String(users.length)}
            icon={<Users className="h-5 w-5" />}
          />

          <MetricCard
            label="Ejecutivos inscritos"
            value={String(executiveCount)}
            icon={<Users className="h-5 w-5" />}
          />

          <MetricCard
            label="Profesores titulares"
            value={String(instructorCount)}
            icon={<BookOpen className="h-5 w-5" />}
          />

          <MetricCard
            label="Administradores"
            value={String(users.filter((u) => u.rol === 'ADMIN').length)}
            icon={<Layers className="h-5 w-5 text-indigo-500" />}
          />
        </div>
      )}

      <div className="overflow-hidden rounded-2xl border border-cyan-500/10 bg-white dark:border-slate-800 dark:bg-[#0c111a]">
        <div className="flex flex-col gap-4 border-b border-slate-200 p-4 dark:border-slate-800 md:flex-row md:items-center md:justify-between">
          <div className="flex items-center gap-2">
            <span className="rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-4 py-2 text-xs font-bold text-white shadow-md shadow-cyan-500/20">
              {section === 'programs'
                ? `Programas (${programs.length})`
                : `Usuarios (${users.length})`}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            {section === 'programs' ? (
              <button
                type="button"
                onClick={handleOpenNewProgram}
                className="flex h-10 items-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-3.5 text-xs font-bold text-white shadow-sm transition-all hover:brightness-110 active:scale-95"
              >
                <Plus className="h-3.5 w-3.5 stroke-[3]" />
                <span className="hidden sm:inline">Nuevo Programa</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={handleOpenNewUser}
                className="flex h-10 items-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-3.5 text-xs font-bold text-white shadow-sm transition-all hover:brightness-110 active:scale-95"
              >
                <Plus className="h-3.5 w-3.5 stroke-[3]" />
                <span className="hidden sm:inline">Nuevo Usuario</span>
              </button>
            )}

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
      </div>

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
        existingUsers={users}
      />
    </section>
  );
};

interface MetricCardProps {
  label: string;
  value: string;
  icon: React.ReactNode;
}

const MetricCard: React.FC<MetricCardProps> = ({
  label,
  value,
  icon,
}) => {
  return (
    <article className="rounded-2xl border border-cyan-500/10 bg-white p-5 dark:border-slate-800 dark:bg-[#0c111a]">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-xs text-slate-500">
            {label}
          </p>

          <p className="mt-2 font-mono text-3xl font-black text-slate-950 dark:text-white">
            {value}
          </p>
        </div>

        <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-500">
          {icon}
        </span>
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
        <span className="inline-flex items-center gap-1 w-fit rounded-md bg-amber-500/10 border border-amber-500/30 px-2 py-0.5 text-[9px] font-bold text-amber-600 dark:text-amber-400">
          ⚠️ Ingresado como DNI
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
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px]">
        <thead className="bg-slate-50 dark:bg-slate-900/60">
          <tr className="text-left text-[10px] uppercase tracking-wider text-slate-500">
            <th className="px-5 py-3">Usuario</th>
            <th className="px-5 py-3">Correo</th>
            <th className="px-5 py-3">Celular</th>
            <th className="px-5 py-3">Rol</th>
            <th className="px-5 py-3 text-right">Acciones</th>
          </tr>
        </thead>

        <tbody>
          {users.map((user) => (
            <tr
              key={user.idUser}
              className="border-t border-slate-100 text-xs dark:border-slate-800 hover:bg-slate-50/50 dark:hover:bg-slate-900/30 transition-colors"
            >
              <td className="px-5 py-4">
                <p className="font-bold text-slate-900 dark:text-white">
                  {user.nombres} {user.apellidos}
                </p>

                <p className="mt-1 font-mono text-[9px] text-slate-400">
                  ID #{user.idUser}
                </p>
              </td>

              <td className="px-5 py-4 text-slate-600 dark:text-slate-400 font-mono text-[11px]">
                {user.email}
              </td>

              <td className="px-5 py-4">
                {renderUserPhoneCell(user.telefono)}
              </td>

              <td className="px-5 py-4">
                <span
                  className={`inline-flex rounded-full border px-2.5 py-1 font-mono text-[9px] font-bold uppercase ${ROLE_STYLES[user.rol] || 'border-cyan-500/20 bg-cyan-500/10 text-cyan-400'}`}
                >
                  {ROLE_LABELS[user.rol] || user.rol || 'Ejecutivo'}
                </span>
              </td>

              <td className="px-5 py-4 text-right">
                <div className="flex items-center justify-end gap-1.5">
                  <button
                    type="button"
                    onClick={() => onEditUser(user)}
                    title="Editar usuario o corregir celular"
                    className="inline-flex items-center gap-1.5 rounded-lg border border-cyan-500/30 bg-cyan-500/10 px-2.5 py-1.5 text-xs font-bold text-cyan-600 transition-all hover:bg-cyan-500 hover:text-white dark:border-cyan-500/40 dark:text-cyan-400"
                  >
                    <Edit3 className="h-3.5 w-3.5" />
                    <span>Editar</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => onDeleteUser(user)}
                    title="Eliminar usuario"
                    className="inline-flex items-center justify-center rounded-lg border border-rose-500/20 bg-rose-500/5 p-1.5 text-rose-500 transition-all hover:bg-rose-500 hover:text-white"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
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
  existingUsers?: UsuarioResponse[];
}

const UserEditorModal: React.FC<UserEditorModalProps> = ({
  isOpen,
  onClose,
  user,
  onSave,
  existingUsers = [],
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

  // Credenciales generadas únicas y diferenciadas (sin repetición)
  const [assignedUsername, setAssignedUsername] = useState('');
  const [tempPassword, setTempPassword] = useState('');
  const [isManualUsername, setIsManualUsername] = useState(false);
  const [isManualPassword, setIsManualPassword] = useState(false);

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
      setIsManualUsername(false);
      setIsManualPassword(false);
      setAssignedUsername('');
      setTempPassword('');
    }
  }, [user, isOpen]);

  // Generación dinámica de usuario único a partir de nombres y apellidos
  useEffect(() => {
    if (!isEditing && !isManualUsername) {
      const knownUsernames = existingUsers.map((u) => u.email.split('@')[0]);
      setAssignedUsername(generateUniqueUsername(nombres, apellidos, knownUsernames));
    }
  }, [nombres, apellidos, isEditing, isManualUsername, existingUsers]);

  // Contraseña única y aleatorizada de alta seguridad (sin repetición)
  useEffect(() => {
    if (!isEditing && !isManualPassword) {
      setTempPassword(generateSecurePassword(nombres));
    }
  }, [nombres, isEditing, isManualPassword]);

  const handleRegenerateUsername = () => {
    const knownUsernames = existingUsers.map((u) => u.email.split('@')[0]);
    setAssignedUsername(generateUniqueUsername(nombres, apellidos, knownUsernames));
    setIsManualUsername(false);
  };

  const handleRegeneratePassword = () => {
    setTempPassword(generateSecurePassword(nombres));
    setIsManualPassword(false);
  };

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
    const targetEmail = email.trim().toLowerCase();

    // Validar duplicados de cuenta (correo o celular) contra usuarios existentes
    if (existingUsers.length > 0) {
      const duplicateCheck = checkDuplicateAccount(
        {
          email: targetEmail,
          phone: formattedPhone,
          excludeUserId: user?.idUser,
        },
        existingUsers
      );

      if (duplicateCheck.isDuplicate) {
        setError(
          duplicateCheck.message ||
            'No se permiten cuentas duplicadas: el correo o celular ya se encuentra registrado.'
        );
        return;
      }
    }

    const areaConfig = REGISTRATION_AREAS.find((a) => a.id === selectedArea) || REGISTRATION_AREAS[0];
    const roleToAssign = isEditing ? rol : areaConfig.role;

    setIsSaving(true);
    try {
      if (isEditing) {
        await onSave({
          nombres: nombres.trim(),
          apellidos: apellidos.trim(),
          email: targetEmail,
          telefono: formattedPhone,
          rol: roleToAssign,
        });
        onClose();
      } else {
        const passToAssign = tempPassword.trim() || generateSecurePassword(nombres);
        const usernameToAssign = assignedUsername.trim() || generateUniqueUsername(nombres, apellidos);
        const activationCode = `INT-${Math.floor(10000 + Math.random() * 90000)}`;

        await onSave({
          nombres: nombres.trim(),
          apellidos: apellidos.trim(),
          email: targetEmail,
          telefono: formattedPhone,
          rol: roleToAssign,
          passw: passToAssign,
        });

        const welcomeData: GeneratedWelcome = {
          fullName: `${nombres.trim()} ${apellidos.trim()}`,
          personalEmail: targetEmail,
          assignedUsername: usernameToAssign,
          areaId: selectedArea,
          areaLabel: areaConfig.label,
          role: roleToAssign,
          activationCode,
          tempPassword: passToAssign,
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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
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
                    setIsManualUsername(false);
                    setIsManualPassword(false);
                    setAssignedUsername('');
                    setTempPassword('');
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

            {/* Vista Previa de Credenciales Asignadas Únicas y Diferenciadas */}
            {!isEditing && (
              <div className="rounded-2xl border border-cyan-500/20 bg-slate-50/80 p-3.5 dark:border-slate-800 dark:bg-slate-900/40">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-cyan-600 dark:text-cyan-400 flex items-center gap-1.5">
                    <KeyRound className="h-3.5 w-3.5" />
                    Credenciales Asignadas (Únicas)
                  </span>
                  <span className="text-[10px] text-slate-400 font-medium">Sin duplicados · Alta seguridad</span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
                  {/* Usuario Único */}
                  <div className="rounded-xl border border-slate-200 bg-white p-2.5 dark:border-slate-800 dark:bg-[#0a0d14]">
                    <div className="flex items-center justify-between mb-1">
                      <label className="text-[10px] uppercase font-bold text-slate-500">Usuario Asignado</label>
                      <button
                        type="button"
                        onClick={handleRegenerateUsername}
                        title="Generar otro usuario único"
                        className="text-cyan-600 hover:text-cyan-500 text-[10px] flex items-center gap-1 font-semibold transition-colors"
                      >
                        <RefreshCw className="h-2.5 w-2.5" />
                        Cambiar
                      </button>
                    </div>
                    <input
                      type="text"
                      value={assignedUsername}
                      onChange={(e) => {
                        setAssignedUsername(e.target.value);
                        setIsManualUsername(true);
                      }}
                      placeholder="usuario.aleatorio"
                      className="w-full font-mono text-xs font-bold text-cyan-700 dark:text-cyan-400 bg-transparent outline-none"
                    />
                  </div>

                  {/* Contraseña Segura No Repetitiva */}
                  <div className="rounded-xl border border-slate-200 bg-white p-2.5 dark:border-slate-800 dark:bg-[#0a0d14]">
                    <div className="flex items-center justify-between mb-1">
                      <label className="text-[10px] uppercase font-bold text-slate-500">Contraseña Segura</label>
                      <button
                        type="button"
                        onClick={handleRegeneratePassword}
                        title="Generar nueva contraseña segura aleatoria"
                        className="text-cyan-600 hover:text-cyan-500 text-[10px] flex items-center gap-1 font-semibold transition-colors"
                      >
                        <RefreshCw className="h-2.5 w-2.5" />
                        Regenerar
                      </button>
                    </div>
                    <input
                      type="text"
                      value={tempPassword}
                      onChange={(e) => {
                        setTempPassword(e.target.value);
                        setIsManualPassword(true);
                      }}
                      placeholder="ContraseñaSegura!23"
                      className="w-full font-mono text-xs font-bold text-slate-800 dark:text-slate-100 bg-transparent outline-none"
                    />
                  </div>
                </div>
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
    <div className="overflow-x-auto">
      <table className="w-full min-w-[960px]">
        <thead className="bg-slate-50 dark:bg-slate-900/60">
          <tr className="text-left text-[10px] uppercase tracking-wider text-slate-500">
            <th className="px-5 py-3">Programa Ejecutivo</th>
            <th className="px-5 py-3">Instructor Titular</th>
            <th className="px-5 py-3">Inversión & Nivel</th>
            <th className="px-5 py-3">Malla Curricular</th>
            <th className="px-5 py-3">Duración & Fechas</th>
            <th className="px-5 py-3 text-right">Acciones</th>
          </tr>
        </thead>

        <tbody>
          {programs.map((program) => {
            const hasCustomSyllabus = Boolean(program.syllabus && program.syllabus.length > 0);
            const totalModules = program.syllabus?.length || 0;
            const totalSessions = program.syllabus?.reduce((acc, m) => acc + (m.temas?.length || 0), 0) || 0;

            return (
              <tr
                key={program.idPrograma}
                className="border-t border-slate-100 text-xs transition-colors hover:bg-slate-50/50 dark:border-slate-800 dark:hover:bg-slate-900/40"
              >
                {/* Programa con portada */}
                <td className="px-5 py-4">
                  <div className="flex items-center gap-3">
                    {program.coverUrl ? (
                      <img
                        src={program.coverUrl}
                        alt=""
                        className="h-11 w-16 rounded-lg object-cover border border-slate-200 shadow-sm dark:border-slate-800 shrink-0"
                      />
                    ) : (
                      <div className="flex h-11 w-16 items-center justify-center rounded-lg border border-cyan-500/20 bg-cyan-500/10 text-cyan-600 dark:text-cyan-400 shrink-0">
                        <BookOpen className="h-5 w-5" />
                      </div>
                    )}
                    <div>
                      <p className="font-bold text-slate-900 dark:text-white line-clamp-1">
                        {program.tituloPrograma}
                      </p>
                      <p className="mt-0.5 text-[10px] text-slate-500">
                        {program.tipoPrograma || 'Programa de Especialización'}
                      </p>
                    </div>
                  </div>
                </td>

                {/* Instructor */}
                <td className="px-5 py-4 text-slate-600 dark:text-slate-400">
                  <span className="font-medium text-slate-800 dark:text-slate-200">
                    {program.nombreInstructor || 'Sin asignar'}
                  </span>
                </td>

                {/* Inversión & Nivel */}
                <td className="px-5 py-4">
                  <div className="flex flex-col gap-1">
                    <span className="inline-flex items-center gap-1 font-mono text-xs font-bold text-emerald-600 dark:text-emerald-400">
                      <DollarSign className="h-3 w-3" />
                      S/ {(program.precio !== undefined ? program.precio : 5).toLocaleString('es-PE')}
                    </span>
                    <span className="inline-block w-fit rounded-full border border-indigo-500/20 bg-indigo-500/10 px-2 py-0.5 font-mono text-[9px] uppercase font-bold text-indigo-500">
                      {program.level || 'ENTERPRISE'}
                    </span>
                  </div>
                </td>

                {/* Malla Curricular Status */}
                <td className="px-5 py-4">
                  {hasCustomSyllabus ? (
                    <div className="flex flex-col gap-1">
                      <span className="inline-flex items-center gap-1 w-fit rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2.5 py-0.5 font-mono text-[10px] font-bold text-emerald-600 dark:text-emerald-400">
                        <CheckCircle2 className="h-3 w-3" />
                        {totalModules} Módulos ({totalSessions} Temas)
                      </span>
                      <span className="text-[10px] text-slate-400">
                        Malla estructurada activa
                      </span>
                    </div>
                  ) : (
                    <div className="flex flex-col gap-1">
                      <span className="inline-flex items-center gap-1 w-fit rounded-full border border-amber-500/20 bg-amber-500/10 px-2.5 py-0.5 font-mono text-[10px] text-amber-600 dark:text-amber-400">
                        <Layers className="h-3 w-3" />
                        Malla estándar
                      </span>
                      <span className="text-[10px] text-slate-400">
                        Sin personalizar
                      </span>
                    </div>
                  )}
                </td>

                {/* Duración & Fechas */}
                <td className="px-5 py-4 text-slate-600 dark:text-slate-400">
                  <div>
                    <span className="font-semibold text-slate-800 dark:text-slate-200">
                      {program.duracionPrograma || '6 Semanas'}
                    </span>
                    <span className="mt-1 flex items-center gap-1 text-[10px] text-slate-500">
                      <CalendarDays className="h-3.5 w-3.5 text-cyan-500" />
                      {formatDate(program.fechaInicioGlobal)}
                    </span>
                  </div>
                </td>

                {/* Acciones */}
                <td className="px-5 py-4 text-right">
                  <div className="flex items-center justify-end gap-1.5">
                    {/* Botón Editar */}
                    <button
                      type="button"
                      onClick={() => onEditProgram(program)}
                      title="Editar programa"
                      className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-xs font-semibold text-slate-600 transition-all hover:border-slate-300 hover:text-slate-900 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300 dark:hover:text-white"
                    >
                      <Edit3 className="h-3.5 w-3.5" />
                      <span>Editar</span>
                    </button>

                    {/* Botón Eliminar */}
                    <button
                      type="button"
                      onClick={() => onDeleteProgram(program)}
                      title="Eliminar programa del catálogo"
                      className="inline-flex items-center justify-center rounded-lg border border-rose-500/20 bg-rose-500/5 p-1.5 text-rose-500 transition-all hover:bg-rose-500 hover:text-white"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
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
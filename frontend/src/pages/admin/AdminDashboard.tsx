import React, {
  useCallback,
  useEffect,
  useState,
} from 'react';

import {
  BookOpen,
  CalendarDays,
  LoaderCircle,
  RefreshCw,
  Search,
  ShieldAlert,
  Users,
} from 'lucide-react';

import { getApiErrorMessage } from '../../services/authService';
import { academicService } from '../../services/academicService';

import {
  ProgramaResponse,
  UserRole,
  UsuarioResponse,
} from '../../types';

interface AdminDashboardProps {
  initialSection?: 'users' | 'programs';
}

const ROLE_LABELS: Record<UserRole, string> = {
  ESTUDIANTE: 'Ejecutivo',
  INSTRUCTOR: 'Profesor',
  ADMIN: 'Administrador',
};

const ROLE_STYLES: Record<UserRole, string> = {
  ESTUDIANTE:
    'border-cyan-500/20 bg-cyan-500/10 text-cyan-600 dark:text-cyan-300',
  INSTRUCTOR:
    'border-indigo-500/20 bg-indigo-500/10 text-indigo-600 dark:text-indigo-300',
  ADMIN:
    'border-amber-500/20 bg-amber-500/10 text-amber-600 dark:text-amber-300',
};

const formatDate = (
  date?: string,
): string => {
  if (!date) {
    return 'Sin fecha';
  }

  return new Intl.DateTimeFormat('es-PE', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(new Date(`${date}T00:00:00`));
};

export const AdminDashboard: React.FC<
  AdminDashboardProps
> = ({ initialSection = 'users' }) => {
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

  useEffect(() => {
    setSection(initialSection);
    setSearch('');
  }, [initialSection]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');

    try {
      const [usersResponse, programsResponse] =
        await Promise.all([
          academicService.getUsers(),
          academicService.getPrograms(),
        ]);

      setUsers(usersResponse);
      setPrograms(programsResponse);
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

  const normalizedSearch =
    search.trim().toLowerCase();

  const filteredUsers = users.filter(
    (user) => {
      const completeName =
        `${user.nombres} ${user.apellidos}`.toLowerCase();

      return (
        !normalizedSearch ||
        completeName.includes(normalizedSearch) ||
        user.email
          .toLowerCase()
          .includes(normalizedSearch) ||
        user.rol
          .toLowerCase()
          .includes(normalizedSearch)
      );
    },
  );

  const filteredPrograms = programs.filter(
    (program) => {
      return (
        !normalizedSearch ||
        program.tituloPrograma
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
    (user) => user.rol === 'ESTUDIANTE',
  ).length;

  const instructorCount = users.filter(
    (user) => user.rol === 'INSTRUCTOR',
  ).length;

  return (
    <section className="space-y-6">
      <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-7 dark:border-slate-800 dark:bg-[#0c111a] md:p-9">
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-cyan-500/10 blur-3xl" />

        <div className="relative">
          <span className="inline-flex rounded-full border border-cyan-500/20 bg-cyan-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-cyan-600 dark:text-cyan-400">
            Administración
          </span>

          <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
            Control de la plataforma
          </h1>

          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">
            Consulta los usuarios registrados y los
            programas académicos disponibles.
          </p>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <MetricCard
          label="Usuarios registrados"
          value={String(users.length)}
          icon={<Users className="h-5 w-5" />}
        />

        <MetricCard
          label="Ejecutivos"
          value={String(executiveCount)}
          icon={<Users className="h-5 w-5" />}
        />

        <MetricCard
          label="Profesores"
          value={String(instructorCount)}
          icon={<BookOpen className="h-5 w-5" />}
        />
      </div>

      <div className="overflow-hidden rounded-2xl border border-cyan-500/10 bg-white dark:border-slate-800 dark:bg-[#0c111a]">
        <div className="flex flex-col gap-4 border-b border-slate-200 p-4 dark:border-slate-800 md:flex-row md:items-center md:justify-between">
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => {
                setSection('users');
                setSearch('');
              }}
              className={`rounded-xl px-4 py-2 text-xs font-bold transition-all ${
                section === 'users'
                  ? 'bg-gradient-to-r from-cyan-500 to-indigo-600 text-white'
                  : 'bg-slate-100 text-slate-500 hover:text-slate-900 dark:bg-slate-900 dark:hover:text-white'
              }`}
            >
              Usuarios
            </button>

            <button
              type="button"
              onClick={() => {
                setSection('programs');
                setSearch('');
              }}
              className={`rounded-xl px-4 py-2 text-xs font-bold transition-all ${
                section === 'programs'
                  ? 'bg-gradient-to-r from-cyan-500 to-indigo-600 text-white'
                  : 'bg-slate-100 text-slate-500 hover:text-slate-900 dark:bg-slate-900 dark:hover:text-white'
              }`}
            >
              Programas
            </button>
          </div>

          <div className="flex gap-2">
            <div className="relative flex-1 md:w-72">
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
                    : 'Buscar programa...'
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
          <UsersTable users={filteredUsers} />
        ) : (
          <ProgramsTable
            programs={filteredPrograms}
          />
        )}
      </div>
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

const UsersTable: React.FC<{
  users: UsuarioResponse[];
}> = ({ users }) => {
  if (users.length === 0) {
    return (
      <EmptyState message="No se encontraron usuarios." />
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px]">
        <thead className="bg-slate-50 dark:bg-slate-900/60">
          <tr className="text-left text-[10px] uppercase tracking-wider text-slate-500">
            <th className="px-5 py-3">Usuario</th>
            <th className="px-5 py-3">Correo</th>
            <th className="px-5 py-3">Teléfono</th>
            <th className="px-5 py-3">Rol</th>
          </tr>
        </thead>

        <tbody>
          {users.map((user) => (
            <tr
              key={user.idUser}
              className="border-t border-slate-100 text-xs dark:border-slate-800"
            >
              <td className="px-5 py-4">
                <p className="font-bold text-slate-900 dark:text-white">
                  {user.nombres} {user.apellidos}
                </p>

                <p className="mt-1 font-mono text-[9px] text-slate-400">
                  ID #{user.idUser}
                </p>
              </td>

              <td className="px-5 py-4 text-slate-600 dark:text-slate-400">
                {user.email}
              </td>

              <td className="px-5 py-4 text-slate-600 dark:text-slate-400">
                {user.telefono || 'No registrado'}
              </td>

              <td className="px-5 py-4">
                <span
                  className={`inline-flex rounded-full border px-2.5 py-1 font-mono text-[9px] font-bold uppercase ${ROLE_STYLES[user.rol]}`}
                >
                  {ROLE_LABELS[user.rol]}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

const ProgramsTable: React.FC<{
  programs: ProgramaResponse[];
}> = ({ programs }) => {
  if (programs.length === 0) {
    return (
      <EmptyState message="No se encontraron programas." />
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[850px]">
        <thead className="bg-slate-50 dark:bg-slate-900/60">
          <tr className="text-left text-[10px] uppercase tracking-wider text-slate-500">
            <th className="px-5 py-3">Programa</th>
            <th className="px-5 py-3">Instructor</th>
            <th className="px-5 py-3">Nivel</th>
            <th className="px-5 py-3">Duración</th>
            <th className="px-5 py-3">Fechas</th>
          </tr>
        </thead>

        <tbody>
          {programs.map((program) => (
            <tr
              key={program.idPrograma}
              className="border-t border-slate-100 text-xs dark:border-slate-800"
            >
              <td className="px-5 py-4">
                <p className="font-bold text-slate-900 dark:text-white">
                  {program.tituloPrograma}
                </p>

                <p className="mt-1 text-[10px] text-slate-500">
                  {program.tipoPrograma ||
                    'Programa académico'}
                </p>
              </td>

              <td className="px-5 py-4 text-slate-600 dark:text-slate-400">
                {program.nombreInstructor ||
                  'Sin asignar'}
              </td>

              <td className="px-5 py-4">
                <span className="rounded-full border border-indigo-500/20 bg-indigo-500/10 px-2.5 py-1 font-mono text-[9px] uppercase text-indigo-500">
                  {program.level || 'Sin nivel'}
                </span>
              </td>

              <td className="px-5 py-4 text-slate-600 dark:text-slate-400">
                {program.duracionPrograma ||
                  'No definida'}
              </td>

              <td className="px-5 py-4 text-slate-600 dark:text-slate-400">
                <span className="flex items-center gap-2">
                  <CalendarDays className="h-4 w-4 text-cyan-500" />

                  {formatDate(
                    program.fechaInicioGlobal,
                  )}
                </span>
              </td>
            </tr>
          ))}
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
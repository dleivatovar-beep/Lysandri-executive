import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  CheckCircle2,
  GraduationCap,
  LoaderCircle,
  RefreshCw,
  ShieldAlert,
  UserPlus,
  Users,
} from 'lucide-react';

import { academicService } from '../../services/academicService';
import { getApiErrorMessage } from '../../services/authService';

import {
  ProgramaResponse,
  UsuarioRequest,
  UsuarioResponse,
} from '../../types';

const EMPTY_USER: UsuarioRequest = {
  nombres: '',
  apellidos: '',
  email: '',
  passw: '',
  telefono: '',
  rol: 'ESTUDIANTE',
};

const CONTROL_CLASS =
  'h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none transition focus:border-cyan-500 focus:ring-2 focus:ring-cyan-500/10 dark:border-slate-800 dark:bg-slate-900 dark:text-white';

export const AdminEnrollments:
  React.FC = () => {
    const [users, setUsers] = useState<
      UsuarioResponse[]
    >([]);

    const [programs, setPrograms] =
      useState<ProgramaResponse[]>([]);

    const [
      selectedUserId,
      setSelectedUserId,
    ] = useState('');

    const [
      selectedProgramId,
      setSelectedProgramId,
    ] = useState('');

    const [newUser, setNewUser] =
      useState<UsuarioRequest>(
        EMPTY_USER,
      );

    const [isLoading, setIsLoading] =
      useState(true);

    const [isCreating, setIsCreating] =
      useState(false);

    const [isEnrolling, setIsEnrolling] =
      useState(false);

    const [
      errorMessage,
      setErrorMessage,
    ] = useState('');

    const [
      successMessage,
      setSuccessMessage,
    ] = useState('');

    const students = useMemo(
      () =>
        users.filter(
          (user) =>
            user.rol === 'ESTUDIANTE',
        ),
      [users],
    );

    const loadData =
      useCallback(async () => {
        setIsLoading(true);
        setErrorMessage('');

        try {
          const [
            usersResponse,
            programsResponse,
          ] = await Promise.all([
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

    useEffect(() => {
      if (!successMessage) {
        return;
      }

      const timeout =
        window.setTimeout(() => {
          setSuccessMessage('');
        }, 5000);

      return () =>
        window.clearTimeout(timeout);
    }, [successMessage]);

    const createStudent = async (
      event:
        React.FormEvent<HTMLFormElement>,
    ) => {
      event.preventDefault();

      setIsCreating(true);
      setErrorMessage('');
      setSuccessMessage('');

      try {
        const created =
          await academicService.createUser({
            ...newUser,

            nombres:
              newUser.nombres.trim(),

            apellidos:
              newUser.apellidos.trim(),

            email:
              newUser.email.trim(),

            telefono:
              newUser.telefono?.trim() ||
              undefined,

            rol: 'ESTUDIANTE',
          });

        setUsers((current) => [
          ...current,
          created,
        ]);

        setSelectedUserId(
          String(created.idUser),
        );

        setNewUser(EMPTY_USER);

        setSuccessMessage(
          `${created.nombres} ${created.apellidos} fue creado. Ya puedes seleccionar un programa y completar su inscripción.`,
        );
      } catch (error) {
        setErrorMessage(
          getApiErrorMessage(error),
        );
      } finally {
        setIsCreating(false);
      }
    };

    const enrollStudent = async (
      event:
        React.FormEvent<HTMLFormElement>,
    ) => {
      event.preventDefault();

      if (
        !selectedUserId ||
        !selectedProgramId
      ) {
        setErrorMessage(
          'Selecciona un estudiante y un programa.',
        );

        return;
      }

      setIsEnrolling(true);
      setErrorMessage('');
      setSuccessMessage('');

      try {
        const enrollment =
          await academicService.enroll({
            usuarioId: Number(
              selectedUserId,
            ),

            programaId: Number(
              selectedProgramId,
            ),
          });

        setSuccessMessage(
          `${enrollment.nombreEstudiante} fue inscrito correctamente en “${enrollment.tituloPrograma}”.`,
        );

        setSelectedProgramId('');
      } catch (error) {
        setErrorMessage(
          getApiErrorMessage(error),
        );
      } finally {
        setIsEnrolling(false);
      }
    };

    return (
      <section className="space-y-6">
        <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-8 dark:border-slate-800 dark:bg-[#0c111a]">
          <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />

          <div className="relative">
            <span className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-cyan-600 dark:text-cyan-300">
              Administración
            </span>

            <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
              Usuarios e inscripciones
            </h1>

            <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">
              Registra nuevos ejecutivos y
              asígnalos a los programas
              académicos disponibles.
            </p>
          </div>
        </div>

        {errorMessage && (
          <div className="flex items-start gap-3 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-600 dark:text-rose-300">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />

            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div className="flex items-start gap-3 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-700 dark:text-emerald-300">
            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />

            <span>
              {successMessage}
            </span>
          </div>
        )}

        {isLoading ? (
          <div className="flex min-h-72 items-center justify-center rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-[#0c111a]">
            <div className="flex items-center gap-3 text-sm text-slate-500">
              <LoaderCircle className="h-5 w-5 animate-spin text-cyan-500" />

              Cargando usuarios y
              programas...
            </div>
          </div>
        ) : (
          <div className="grid gap-6 xl:grid-cols-2">
            <article className="rounded-2xl border border-cyan-500/10 bg-white p-6 dark:border-slate-800 dark:bg-[#0c111a]">
              <div className="flex items-center gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-500">
                  <UserPlus className="h-5 w-5" />
                </span>

                <div>
                  <h2 className="font-black text-slate-950 dark:text-white">
                    Crear estudiante
                  </h2>

                  <p className="mt-1 text-xs text-slate-500">
                    Registra una cuenta con
                    rol Ejecutivo.
                  </p>
                </div>
              </div>

              <form
                onSubmit={createStudent}
                className="mt-6 space-y-4"
              >
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Nombres">
                    <input
                      required
                      value={
                        newUser.nombres
                      }
                      onChange={(event) =>
                        setNewUser({
                          ...newUser,

                          nombres:
                            event.target
                              .value,
                        })
                      }
                      className={
                        CONTROL_CLASS
                      }
                      placeholder="Juan"
                    />
                  </Field>

                  <Field label="Apellidos">
                    <input
                      required
                      value={
                        newUser.apellidos
                      }
                      onChange={(event) =>
                        setNewUser({
                          ...newUser,

                          apellidos:
                            event.target
                              .value,
                        })
                      }
                      className={
                        CONTROL_CLASS
                      }
                      placeholder="Pérez"
                    />
                  </Field>
                </div>

                <Field label="Correo electrónico">
                  <input
                    required
                    type="email"
                    value={newUser.email}
                    onChange={(event) =>
                      setNewUser({
                        ...newUser,

                        email:
                          event.target
                            .value,
                      })
                    }
                    className={
                      CONTROL_CLASS
                    }
                    placeholder="juan@empresa.com"
                  />
                </Field>

                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Contraseña temporal">
                    <input
                      required
                      type="password"
                      minLength={8}
                      value={
                        newUser.passw
                      }
                      onChange={(event) =>
                        setNewUser({
                          ...newUser,

                          passw:
                            event.target
                              .value,
                        })
                      }
                      className={
                        CONTROL_CLASS
                      }
                      placeholder="Mínimo 8 caracteres"
                    />
                  </Field>

                  <Field label="Teléfono">
                    <input
                      type="tel"
                      value={
                        newUser.telefono ??
                        ''
                      }
                      onChange={(event) =>
                        setNewUser({
                          ...newUser,

                          telefono:
                            event.target
                              .value,
                        })
                      }
                      className={
                        CONTROL_CLASS
                      }
                      placeholder="999 999 999"
                    />
                  </Field>
                </div>

                <button
                  type="submit"
                  disabled={isCreating}
                  className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white transition hover:scale-[1.01] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isCreating ? (
                    <LoaderCircle className="h-4 w-4 animate-spin" />
                  ) : (
                    <UserPlus className="h-4 w-4" />
                  )}

                  {isCreating
                    ? 'Creando estudiante...'
                    : 'Crear estudiante'}
                </button>
              </form>
            </article>

            <article className="rounded-2xl border border-indigo-500/10 bg-white p-6 dark:border-slate-800 dark:bg-[#0c111a]">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-500">
                    <GraduationCap className="h-5 w-5" />
                  </span>

                  <div>
                    <h2 className="font-black text-slate-950 dark:text-white">
                      Inscribir en un
                      programa
                    </h2>

                    <p className="mt-1 text-xs text-slate-500">
                      Selecciona un
                      estudiante y un
                      programa.
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() =>
                    void loadData()
                  }
                  aria-label="Actualizar listas"
                  className="flex h-9 w-9 items-center justify-center rounded-xl border border-slate-200 text-slate-500 hover:text-cyan-500 dark:border-slate-800"
                >
                  <RefreshCw className="h-4 w-4" />
                </button>
              </div>

              <form
                onSubmit={enrollStudent}
                className="mt-6 space-y-5"
              >
                <Field label="Estudiante">
                  <select
                    required
                    value={
                      selectedUserId
                    }
                    onChange={(event) =>
                      setSelectedUserId(
                        event.target.value,
                      )
                    }
                    className={
                      CONTROL_CLASS
                    }
                  >
                    <option value="">
                      Selecciona un
                      estudiante
                    </option>

                    {students.map(
                      (student) => (
                        <option
                          key={
                            student.idUser
                          }
                          value={
                            student.idUser
                          }
                        >
                          {
                            student.nombres
                          }{' '}
                          {
                            student.apellidos
                          }{' '}
                          — {student.email}
                        </option>
                      ),
                    )}
                  </select>
                </Field>

                <Field label="Programa académico">
                  <select
                    required
                    value={
                      selectedProgramId
                    }
                    onChange={(event) =>
                      setSelectedProgramId(
                        event.target.value,
                      )
                    }
                    className={
                      CONTROL_CLASS
                    }
                  >
                    <option value="">
                      Selecciona un
                      programa
                    </option>

                    {programs.map(
                      (program) => (
                        <option
                          key={
                            program.idPrograma
                          }
                          value={
                            program.idPrograma
                          }
                        >
                          {
                            program.tituloPrograma
                          }
                        </option>
                      ),
                    )}
                  </select>
                </Field>

                <div className="grid grid-cols-2 gap-3">
                  <InfoCard
                    icon={
                      <Users className="h-4 w-4" />
                    }
                    label="Estudiantes"
                    value={String(
                      students.length,
                    )}
                  />

                  <InfoCard
                    icon={
                      <GraduationCap className="h-4 w-4" />
                    }
                    label="Programas"
                    value={String(
                      programs.length,
                    )}
                  />
                </div>

                {students.length ===
                  0 && (
                  <p className="rounded-xl border border-amber-500/20 bg-amber-500/10 p-3 text-xs text-amber-700 dark:text-amber-300">
                    Primero crea un
                    estudiante para poder
                    realizar la inscripción.
                  </p>
                )}

                {programs.length ===
                  0 && (
                  <p className="rounded-xl border border-amber-500/20 bg-amber-500/10 p-3 text-xs text-amber-700 dark:text-amber-300">
                    No hay programas
                    disponibles. Debes
                    registrar un programa
                    antes de inscribir
                    estudiantes.
                  </p>
                )}

                <button
                  type="submit"
                  disabled={
                    isEnrolling ||
                    students.length ===
                      0 ||
                    programs.length === 0
                  }
                  className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white transition hover:scale-[1.01] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isEnrolling ? (
                    <LoaderCircle className="h-4 w-4 animate-spin" />
                  ) : (
                    <GraduationCap className="h-4 w-4" />
                  )}

                  {isEnrolling
                    ? 'Procesando inscripción...'
                    : 'Inscribir estudiante'}
                </button>
              </form>
            </article>
          </div>
        )}

        <p className="text-xs leading-5 text-slate-500">
          El backend actual permite crear
          e inscribir estudiantes, pero
          todavía no ofrece un endpoint
          para listar todas las
          inscripciones administrativas.
        </p>
      </section>
    );
  };

interface FieldProps {
  label: string;
  children: React.ReactNode;
}

const Field: React.FC<FieldProps> = ({
  label,
  children,
}) => (
  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
    <span className="mb-1.5 block">
      {label}
    </span>

    {children}
  </label>
);

interface InfoCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
}

const InfoCard:
  React.FC<InfoCardProps> = ({
    icon,
    label,
    value,
  }) => (
    <div className="rounded-xl bg-slate-50 p-3 dark:bg-slate-900/60">
      <div className="flex items-center gap-2 text-cyan-500">
        {icon}

        <span className="text-[10px] text-slate-500">
          {label}
        </span>
      </div>

      <p className="mt-2 font-mono text-xl font-black text-slate-950 dark:text-white">
        {value}
      </p>
    </div>
  );
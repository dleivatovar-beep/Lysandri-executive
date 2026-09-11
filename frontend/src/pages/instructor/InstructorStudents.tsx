import React, {
  useCallback,
  useEffect,
  useState,
} from 'react';

import {
  BookOpen,
  CalendarDays,
  GraduationCap,
  LoaderCircle,
  Mail,
  Phone,
  RefreshCw,
  ShieldAlert,
  Users,
} from 'lucide-react';

import { academicService } from '../../services/academicService';
import { getApiErrorMessage } from '../../services/authService';

import {
  EstudianteInscritoResponse,
  ProgramaResponse,
} from '../../types';

const formatDate = (
  value: string,
): string => {
  if (!value) {
    return 'Sin fecha';
  }

  return new Intl.DateTimeFormat(
    'es-PE',
    {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    },
  ).format(new Date(value));
};

export const InstructorStudents:
  React.FC = () => {
    const [programs, setPrograms] =
      useState<ProgramaResponse[]>([]);

    const [
      selectedProgramId,
      setSelectedProgramId,
    ] = useState<number | null>(null);

    const [students, setStudents] =
      useState<
        EstudianteInscritoResponse[]
      >([]);

    const [
      isLoadingPrograms,
      setIsLoadingPrograms,
    ] = useState(true);

    const [
      isLoadingStudents,
      setIsLoadingStudents,
    ] = useState(false);

    const [
      errorMessage,
      setErrorMessage,
    ] = useState('');

    const loadStudents =
      useCallback(
        async (
          programId: number,
        ) => {
          setIsLoadingStudents(true);
          setErrorMessage('');

          try {
            const response =
              await academicService
                .getInstructorStudents(
                  programId,
                );

            setStudents(response);
          } catch (error) {
            setStudents([]);

            setErrorMessage(
              getApiErrorMessage(error),
            );
          } finally {
            setIsLoadingStudents(
              false,
            );
          }
        },
        [],
      );

    const loadPrograms =
      useCallback(async () => {
        setIsLoadingPrograms(true);
        setErrorMessage('');

        try {
          const response =
            await academicService
              .getInstructorPrograms();

          setPrograms(response);

          if (response.length > 0) {
            const firstId =
              response[0].idPrograma;

            setSelectedProgramId(
              firstId,
            );

            await loadStudents(
              firstId,
            );
          } else {
            setSelectedProgramId(
              null,
            );

            setStudents([]);
          }
        } catch (error) {
          setPrograms([]);
          setStudents([]);

          setErrorMessage(
            getApiErrorMessage(error),
          );
        } finally {
          setIsLoadingPrograms(false);
        }
      }, [loadStudents]);

    useEffect(() => {
      void loadPrograms();
    }, [loadPrograms]);

    const selectProgram = (
      value: string,
    ) => {
      const programId =
        Number(value);

      setSelectedProgramId(
        programId,
      );

      void loadStudents(programId);
    };

    const selectedProgram =
      programs.find(
        (program) =>
          program.idPrograma ===
          selectedProgramId,
      );

    const averageProgress =
      students.length > 0
        ? Math.round(
            students.reduce(
              (total, student) =>
                total +
                Number(
                  student
                    .porcentajeProgreso ||
                    0,
                ),
              0,
            ) / students.length,
          )
        : 0;

    return (
      <section className="space-y-6">
        <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-8 dark:border-slate-800 dark:bg-[#0c111a]">
          <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />

          <div className="relative">
            <span className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-cyan-600 dark:text-cyan-300">
              Área del profesor
            </span>

            <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
              Estudiantes
            </h1>

            <p className="mt-3 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
              Consulta los estudiantes
              inscritos en los programas
              que tienes asignados.
            </p>
          </div>
        </div>

        {errorMessage && (
          <div className="flex items-start gap-3 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-600 dark:text-rose-300">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />

            <span>
              {errorMessage}
            </span>
          </div>
        )}

        {isLoadingPrograms ? (
          <div className="flex min-h-72 items-center justify-center">
            <LoaderCircle className="h-7 w-7 animate-spin text-cyan-500" />
          </div>
        ) : programs.length === 0 ? (
          <EmptyState
            icon={
              <BookOpen className="h-8 w-8" />
            }
            title="No tienes programas asignados"
            description="Cuando te asignen un programa, podrás consultar aquí sus estudiantes."
          />
        ) : (
          <>
            <div className="flex flex-col gap-4 rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-[#0c111a] md:flex-row md:items-end md:justify-between">
              <label className="block w-full max-w-xl text-xs font-bold text-slate-700 dark:text-slate-300">
                <span className="mb-2 block">
                  Programa académico
                </span>

                <select
                  value={
                    selectedProgramId ??
                    ''
                  }
                  onChange={(event) =>
                    selectProgram(
                      event.target.value,
                    )
                  }
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none focus:border-cyan-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                >
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
                          program
                            .tituloPrograma
                        }
                      </option>
                    ),
                  )}
                </select>
              </label>

              <button
                type="button"
                disabled={
                  !selectedProgramId ||
                  isLoadingStudents
                }
                onClick={() => {
                  if (
                    selectedProgramId
                  ) {
                    void loadStudents(
                      selectedProgramId,
                    );
                  }
                }}
                className="flex h-11 items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 text-xs font-bold text-slate-600 hover:border-cyan-500/40 hover:text-cyan-500 disabled:opacity-50 dark:border-slate-800 dark:text-slate-300"
              >
                <RefreshCw
                  className={`h-4 w-4 ${
                    isLoadingStudents
                      ? 'animate-spin'
                      : ''
                  }`}
                />

                Actualizar
              </button>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              <SummaryCard
                label="Estudiantes inscritos"
                value={String(
                  students.length,
                )}
                icon={
                  <Users className="h-5 w-5" />
                }
              />

              <SummaryCard
                label="Progreso promedio"
                value={`${averageProgress}%`}
                icon={
                  <GraduationCap className="h-5 w-5" />
                }
              />

              <SummaryCard
                label="Programa"
                value={
                  selectedProgram?.level ||
                  'Sin nivel'
                }
                icon={
                  <BookOpen className="h-5 w-5" />
                }
                small
              />
            </div>

            {isLoadingStudents ? (
              <div className="flex min-h-64 items-center justify-center">
                <LoaderCircle className="h-7 w-7 animate-spin text-cyan-500" />
              </div>
            ) : students.length === 0 ? (
              <EmptyState
                icon={
                  <Users className="h-8 w-8" />
                }
                title="No hay estudiantes inscritos"
                description="Este programa todavía no tiene estudiantes matriculados."
              />
            ) : (
              <div className="grid gap-5 lg:grid-cols-2 xl:grid-cols-3">
                {students.map(
                  (student) => {
                    const progress =
                      Math.max(
                        0,
                        Math.min(
                          100,
                          Number(
                            student
                              .porcentajeProgreso ||
                              0,
                          ),
                        ),
                      );

                    return (
                      <article
                        key={
                          student.idInscripcion
                        }
                        className="rounded-2xl border border-cyan-500/10 bg-white p-5 dark:border-slate-800 dark:bg-[#0c111a]"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500/10 font-black text-cyan-500">
                            {student.nombres.charAt(
                              0,
                            )}

                            {student.apellidos.charAt(
                              0,
                            )}
                          </span>

                          <span className="rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2.5 py-1 font-mono text-[9px] uppercase text-emerald-600 dark:text-emerald-300">
                            {student.estado ||
                              'ACTIVO'}
                          </span>
                        </div>

                        <h2 className="mt-4 font-black text-slate-950 dark:text-white">
                          {student.nombres}{' '}
                          {student.apellidos}
                        </h2>

                        <div className="mt-3 space-y-2 text-xs text-slate-500">
                          <p className="flex items-center gap-2">
                            <Mail className="h-3.5 w-3.5 text-cyan-500" />

                            {student.email}
                          </p>

                          <p className="flex items-center gap-2">
                            <Phone className="h-3.5 w-3.5 text-indigo-500" />

                            {student.telefono ||
                              'Sin teléfono'}
                          </p>

                          <p className="flex items-center gap-2">
                            <CalendarDays className="h-3.5 w-3.5 text-cyan-500" />

                            Inscrito el{' '}

                            {formatDate(
                              student
                                .fechaInscripcion,
                            )}
                          </p>
                        </div>

                        <div className="mt-5">
                          <div className="mb-2 flex justify-between text-[10px]">
                            <span className="text-slate-500">
                              Progreso
                            </span>

                            <span className="font-mono font-bold text-cyan-500">
                              {Math.round(
                                progress,
                              )}
                              %
                            </span>
                          </div>

                          <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-900">
                            <div
                              className="h-full rounded-full bg-gradient-to-r from-cyan-500 to-indigo-600"
                              style={{
                                width: `${progress}%`,
                              }}
                            />
                          </div>
                        </div>
                      </article>
                    );
                  },
                )}
              </div>
            )}
          </>
        )}
      </section>
    );
  };

interface SummaryCardProps {
  label: string;
  value: string;
  icon: React.ReactNode;
  small?: boolean;
}

const SummaryCard:
  React.FC<SummaryCardProps> = ({
    label,
    value,
    icon,
    small = false,
  }) => (
    <article className="flex items-center justify-between rounded-2xl border border-cyan-500/10 bg-white p-5 dark:border-slate-800 dark:bg-[#0c111a]">
      <div>
        <p className="text-xs text-slate-500">
          {label}
        </p>

        <p
          className={`mt-2 font-mono font-black text-slate-950 dark:text-white ${
            small
              ? 'text-sm'
              : 'text-2xl'
          }`}
        >
          {value}
        </p>
      </div>

      <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-500">
        {icon}
      </span>
    </article>
  );

interface EmptyStateProps {
  icon: React.ReactNode;
  title: string;
  description: string;
}

const EmptyState:
  React.FC<EmptyStateProps> = ({
    icon,
    title,
    description,
  }) => (
    <div className="flex min-h-64 flex-col items-center justify-center rounded-3xl border border-dashed border-cyan-500/20 bg-white p-8 text-center dark:border-slate-800 dark:bg-[#0c111a]">
      <span className="text-cyan-500">
        {icon}
      </span>

      <h2 className="mt-4 font-black text-slate-950 dark:text-white">
        {title}
      </h2>

      <p className="mt-2 max-w-md text-sm text-slate-500">
        {description}
      </p>
    </div>
  );
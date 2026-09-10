import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  BookOpen,
  CheckCircle2,
  Clock3,
  ExternalLink,
  LoaderCircle,
  PlayCircle,
  RefreshCw,
  TrendingUp,
  X,
} from 'lucide-react';

import { academicService } from '../../services/academicService';
import { getApiErrorMessage } from '../../services/authService';
import { getCourseCover } from '../../services/courseCatalog';

import {
  InscripcionResponse,
  LeccionResponse,
  ProgramaResponse,
  ProgresoPorcentajeResponse,
} from '../../types';

interface StudentCoursesProps {
  mode?: 'courses' | 'progress';
}

interface EnrolledCourse {
  enrollment: InscripcionResponse;
  program: ProgramaResponse | null;
  progress: ProgresoPorcentajeResponse | null;
}

export const StudentCourses:
  React.FC<StudentCoursesProps> = ({
    mode = 'courses',
  }) => {
    const [courses, setCourses] = useState<
      EnrolledCourse[]
    >([]);

    const [isLoading, setIsLoading] =
      useState(true);

    const [errorMessage, setErrorMessage] =
      useState('');

    const [
      selectedCourse,
      setSelectedCourse,
    ] = useState<EnrolledCourse | null>(null);

    const [lessons, setLessons] = useState<
      LeccionResponse[]
    >([]);

    const [
      isLoadingLessons,
      setIsLoadingLessons,
    ] = useState(false);

    const [lessonError, setLessonError] =
      useState('');

    const [
      completingLessonId,
      setCompletingLessonId,
    ] = useState<number | null>(null);

    const [
      completedLessonIds,
      setCompletedLessonIds,
    ] = useState<Set<number>>(
      () => new Set(),
    );

    const loadCourses =
      useCallback(async () => {
        setIsLoading(true);
        setErrorMessage('');

        try {
          const enrollments =
            await academicService.getMyCourses();

          const resolved = await Promise.all(
            enrollments.map(
              async (
                enrollment,
              ): Promise<EnrolledCourse> => {
                const [
                  programResult,
                  progressResult,
                ] = await Promise.allSettled([
                  academicService.getProgram(
                    enrollment.programaId,
                  ),

                  academicService.getProgress(
                    enrollment.programaId,
                  ),
                ]);

                return {
                  enrollment,

                  program:
                    programResult.status ===
                    'fulfilled'
                      ? programResult.value
                      : null,

                  progress:
                    progressResult.status ===
                    'fulfilled'
                      ? progressResult.value
                      : null,
                };
              },
            ),
          );

          setCourses(resolved);
        } catch (error) {
          setCourses([]);

          setErrorMessage(
            getApiErrorMessage(error),
          );
        } finally {
          setIsLoading(false);
        }
      }, []);

    useEffect(() => {
      void loadCourses();
    }, [loadCourses]);

    const openCourse = async (
      course: EnrolledCourse,
    ) => {
      setSelectedCourse(course);
      setLessons([]);
      setLessonError('');
      setIsLoadingLessons(true);

      try {
        const response =
          await academicService.getLessons(
            course.enrollment.programaId,
          );

        setLessons(
          [...response].sort(
            (a, b) => a.orden - b.orden,
          ),
        );
      } catch (error) {
        setLessonError(
          getApiErrorMessage(error),
        );
      } finally {
        setIsLoadingLessons(false);
      }
    };

    const completeLesson = async (
      lessonId: number,
    ) => {
      if (!selectedCourse) {
        return;
      }

      setCompletingLessonId(lessonId);
      setLessonError('');

      try {
        await academicService.completeLesson(
          lessonId,
        );

        setCompletedLessonIds(
          (current) =>
            new Set(current).add(lessonId),
        );

        const progress =
          await academicService.getProgress(
            selectedCourse.enrollment
              .programaId,
          );

        setCourses((current) =>
          current.map((course) =>
            course.enrollment.programaId ===
            progress.programaId
              ? {
                  ...course,
                  progress,
                }
              : course,
          ),
        );

        setSelectedCourse((current) =>
          current
            ? {
                ...current,
                progress,
              }
            : current,
        );
      } catch (error) {
        setLessonError(
          getApiErrorMessage(error),
        );
      } finally {
        setCompletingLessonId(null);
      }
    };

    const availableProgress = courses
      .map((course) => course.progress)
      .filter(
        (
          progress,
        ): progress is ProgresoPorcentajeResponse =>
          Boolean(progress),
      );

    const completedLessons =
      availableProgress.reduce(
        (total, progress) =>
          total +
          progress.leccionesCompletadas,
        0,
      );

    const averageProgress =
      availableProgress.length > 0
        ? Math.round(
            availableProgress.reduce(
              (total, progress) =>
                total +
                progress.porcentaje,
              0,
            ) /
              availableProgress.length,
          )
        : null;

    const visibleCourses =
      useMemo(() => {
        if (mode !== 'progress') {
          return courses;
        }

        return [...courses].sort(
          (a, b) =>
            (b.progress?.porcentaje ??
              -1) -
            (a.progress?.porcentaje ??
              -1),
        );
      }, [courses, mode]);

    return (
      <section className="space-y-6">
        <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-8 dark:border-slate-800 dark:bg-[#0c111a]">
          <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />

          <div className="relative">
            <span className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-cyan-600 dark:text-cyan-300">
              Área del ejecutivo
            </span>

            <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
              {mode === 'progress'
                ? 'Mi progreso'
                : 'Mis cursos'}
            </h1>

            <p className="mt-3 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
              {mode === 'progress'
                ? 'Consulta el avance registrado en tus programas académicos.'
                : 'Accede a tus programas inscritos y continúa con sus lecciones.'}
            </p>
          </div>
        </div>

        {!isLoading &&
          !errorMessage && (
            <div className="grid gap-4 sm:grid-cols-3">
              <SummaryCard
                label="Cursos inscritos"
                value={String(
                  courses.length,
                )}
                icon={
                  <BookOpen className="h-5 w-5" />
                }
              />

              <SummaryCard
                label="Lecciones completadas"
                value={
                  availableProgress.length >
                  0
                    ? String(
                        completedLessons,
                      )
                    : '—'
                }
                icon={
                  <CheckCircle2 className="h-5 w-5" />
                }
              />

              <SummaryCard
                label="Progreso promedio"
                value={
                  averageProgress === null
                    ? '—'
                    : `${averageProgress}%`
                }
                icon={
                  <TrendingUp className="h-5 w-5" />
                }
              />
            </div>
          )}

        {isLoading ? (
          <StatusPanel
            icon={
              <LoaderCircle className="h-7 w-7 animate-spin" />
            }
          >
            Cargando tus cursos...
          </StatusPanel>
        ) : errorMessage ? (
          <StatusPanel
            icon={
              <RefreshCw className="h-7 w-7" />
            }
          >
            <p>{errorMessage}</p>

            <button
              type="button"
              onClick={() =>
                void loadCourses()
              }
              className="mt-4 rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white dark:bg-cyan-500"
            >
              Reintentar
            </button>
          </StatusPanel>
        ) : visibleCourses.length ===
          0 ? (
          <StatusPanel
            icon={
              <BookOpen className="h-7 w-7" />
            }
          >
            Todavía no tienes cursos
            inscritos.
          </StatusPanel>
        ) : (
          <div className="grid gap-6 lg:grid-cols-2 xl:grid-cols-3">
            {visibleCourses.map(
              (course) => {
                const progress =
                  course.progress
                    ?.porcentaje;

                const title =
                  course.enrollment
                    .tituloPrograma;

                const coverUrl =
                  getCourseCover(title);

                return (
                  <article
                    key={
                      course.enrollment.id
                    }
                    className="group flex flex-col overflow-hidden rounded-2xl border border-cyan-500/10 bg-white transition-all duration-300 hover:-translate-y-1 hover:border-cyan-500/30 dark:border-slate-800 dark:bg-[#0c111a]"
                  >
                    <div className="relative aspect-video overflow-hidden bg-gradient-to-br from-cyan-950 to-slate-950">
                      {coverUrl ? (
                        <img
                          src={coverUrl}
                          alt={title}
                          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                        />
                      ) : (
                        <BookOpen className="absolute left-1/2 top-1/2 h-12 w-12 -translate-x-1/2 -translate-y-1/2 text-cyan-400/30" />
                      )}

                      <span className="absolute bottom-3 left-3 rounded-full border border-white/10 bg-slate-950/70 px-2.5 py-1 font-mono text-[9px] uppercase text-cyan-300 backdrop-blur-md">
                        {
                          course
                            .enrollment
                            .estado
                        }
                      </span>
                    </div>

                    <div className="flex flex-1 flex-col p-5">
                      <h2 className="text-base font-black text-slate-950 dark:text-white">
                        {title}
                      </h2>

                      <div className="mt-3 space-y-1 text-xs text-slate-500">
                        {course.program
                          ?.nombreInstructor && (
                          <p>
                            Profesor:{' '}
                            {
                              course
                                .program
                                .nombreInstructor
                            }
                          </p>
                        )}

                        {course.program
                          ?.duracionPrograma && (
                          <p className="flex items-center gap-1.5">
                            <Clock3 className="h-3.5 w-3.5" />

                            {
                              course
                                .program
                                .duracionPrograma
                            }
                          </p>
                        )}
                      </div>

                      <div className="mt-auto pt-5">
                        <div className="mb-2 flex items-center justify-between text-[10px]">
                          <span className="font-semibold text-slate-500">
                            Progreso
                          </span>

                          <span className="font-mono font-bold text-cyan-600 dark:text-cyan-400">
                            {progress ===
                            undefined
                              ? 'No disponible'
                              : `${Math.round(
                                  progress,
                                )}%`}
                          </span>
                        </div>

                        <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-900">
                          {progress !==
                            undefined && (
                            <div
                              className="h-full rounded-full bg-gradient-to-r from-cyan-500 to-indigo-600 transition-all duration-700"
                              style={{
                                width: `${Math.max(
                                  0,
                                  Math.min(
                                    100,
                                    progress,
                                  ),
                                )}%`,
                              }}
                            />
                          )}
                        </div>

                        <button
                          type="button"
                          onClick={() =>
                            void openCourse(
                              course,
                            )
                          }
                          className="mt-5 flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white transition-all hover:scale-[1.02]"
                        >
                          <PlayCircle className="h-4 w-4" />
                          Ver lecciones
                        </button>
                      </div>
                    </div>
                  </article>
                );
              },
            )}
          </div>
        )}

        {selectedCourse && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/75 p-4 backdrop-blur-sm">
            <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl border border-cyan-500/20 bg-white p-6 shadow-2xl dark:border-slate-800 dark:bg-[#0c111a]">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-mono text-[9px] uppercase tracking-widest text-cyan-500">
                    Contenido del programa
                  </p>

                  <h2 className="mt-2 text-xl font-black text-slate-950 dark:text-white">
                    {
                      selectedCourse
                        .enrollment
                        .tituloPrograma
                    }
                  </h2>
                </div>

                <button
                  type="button"
                  aria-label="Cerrar"
                  onClick={() =>
                    setSelectedCourse(
                      null,
                    )
                  }
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-500 dark:bg-slate-900"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              {lessonError && (
                <div className="mt-5 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-sm text-rose-600 dark:text-rose-300">
                  {lessonError}
                </div>
              )}

              {isLoadingLessons ? (
                <div className="flex min-h-44 items-center justify-center">
                  <LoaderCircle className="h-6 w-6 animate-spin text-cyan-500" />
                </div>
              ) : lessons.length ===
                0 ? (
                <p className="mt-6 rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500 dark:border-slate-700">
                  Este programa todavía no
                  tiene lecciones publicadas.
                </p>
              ) : (
                <div className="mt-6 space-y-3">
                  {lessons.map(
                    (lesson) => {
                      const completed =
                        completedLessonIds.has(
                          lesson.id,
                        );

                      return (
                        <article
                          key={lesson.id}
                          className="rounded-xl border border-slate-200 p-4 dark:border-slate-800"
                        >
                          <div className="flex items-start justify-between gap-4">
                            <div>
                              <p className="font-mono text-[9px] uppercase text-cyan-500">
                                Lección{' '}
                                {
                                  lesson.orden
                                }
                              </p>

                              <h3 className="mt-1 text-sm font-bold text-slate-900 dark:text-white">
                                {
                                  lesson.titulo
                                }
                              </h3>

                              {lesson.descripcion && (
                                <p className="mt-2 text-xs leading-5 text-slate-500">
                                  {
                                    lesson.descripcion
                                  }
                                </p>
                              )}

                              {lesson.duracionMinutos !==
                                undefined && (
                                <p className="mt-2 text-[10px] text-slate-500">
                                  {
                                    lesson.duracionMinutos
                                  }{' '}
                                  minutos
                                </p>
                              )}
                            </div>

                            {lesson.contenidoUrl && (
                              <a
                                href={
                                  lesson.contenidoUrl
                                }
                                target="_blank"
                                rel="noreferrer"
                                className="flex items-center gap-1 text-xs font-bold text-cyan-600 dark:text-cyan-400"
                              >
                                Abrir

                                <ExternalLink className="h-3.5 w-3.5" />
                              </a>
                            )}
                          </div>

                          <button
                            type="button"
                            disabled={
                              completed ||
                              completingLessonId ===
                                lesson.id
                            }
                            onClick={() =>
                              void completeLesson(
                                lesson.id,
                              )
                            }
                            className="mt-4 flex h-9 items-center gap-2 rounded-lg bg-slate-900 px-3 text-[11px] font-bold text-white disabled:cursor-not-allowed disabled:opacity-60 dark:bg-cyan-500"
                          >
                            {completingLessonId ===
                            lesson.id ? (
                              <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                            ) : (
                              <CheckCircle2 className="h-3.5 w-3.5" />
                            )}

                            {completed
                              ? 'Completada'
                              : 'Marcar como completada'}
                          </button>
                        </article>
                      );
                    },
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </section>
    );
  };

interface StatusPanelProps {
  icon: React.ReactNode;
  children: React.ReactNode;
}

const StatusPanel:
  React.FC<StatusPanelProps> = ({
    icon,
    children,
  }) => (
    <div className="flex min-h-72 flex-col items-center justify-center rounded-2xl border border-dashed border-cyan-500/20 bg-white p-8 text-center text-sm text-slate-500 dark:border-slate-800 dark:bg-[#0c111a]">
      <span className="mb-4 text-cyan-500">
        {icon}
      </span>

      {children}
    </div>
  );

interface SummaryCardProps {
  label: string;
  value: string;
  icon: React.ReactNode;
}

const SummaryCard:
  React.FC<SummaryCardProps> = ({
    label,
    value,
    icon,
  }) => (
    <article className="flex items-center justify-between rounded-2xl border border-cyan-500/10 bg-white p-5 dark:border-slate-800 dark:bg-[#0c111a]">
      <div>
        <p className="text-xs text-slate-500">
          {label}
        </p>

        <p className="mt-2 font-mono text-2xl font-black text-slate-950 dark:text-white">
          {value}
        </p>
      </div>

      <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-500">
        {icon}
      </span>
    </article>
  );
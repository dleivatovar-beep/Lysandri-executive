import React, {
  useCallback,
  useEffect,
  useState,
} from 'react';

import {
  BookOpen,
  CheckCircle2,
  Edit3,
  FilePlus2,
  LoaderCircle,
  Plus,
  RefreshCw,
  Save,
  Trash2,
  X,
} from 'lucide-react';

import { useAuth } from '../../context/AuthContext';
import { academicService } from '../../services/academicService';
import { getApiErrorMessage } from '../../services/authService';

import {
  LeccionRequest,
  LeccionResponse,
  ProgramaResponse,
} from '../../types';

interface InstructorCoursesProps {
  focusContent?: boolean;
}

interface InstructorCourse {
  program: ProgramaResponse;
  lessons: LeccionResponse[];
}

const EMPTY_FORM: LeccionRequest = {
  titulo: '',
  descripcion: '',
  contenidoUrl: '',
  duracionMinutos: undefined,
  orden: 1,
};

const CONTROL_CLASS =
  'h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm text-slate-900 outline-none transition focus:border-cyan-500 dark:border-slate-700 dark:bg-slate-950 dark:text-white';

const normalize = (value: string) =>
  value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLowerCase();

export const InstructorCourses:
  React.FC<InstructorCoursesProps> = ({
    focusContent = false,
  }) => {
    const { user } = useAuth();

    const [courses, setCourses] = useState<
      InstructorCourse[]
    >([]);

    const [
      selectedCourse,
      setSelectedCourse,
    ] = useState<InstructorCourse | null>(
      null,
    );

    const [
      editingLesson,
      setEditingLesson,
    ] = useState<LeccionResponse | null>(
      null,
    );

    const [form, setForm] =
      useState<LeccionRequest>(EMPTY_FORM);

    const [isLoading, setIsLoading] =
      useState(true);

    const [isSaving, setIsSaving] =
      useState(false);

    const [deletingId, setDeletingId] =
      useState<number | null>(null);

    const [errorMessage, setErrorMessage] =
      useState('');

    const [
      successMessage,
      setSuccessMessage,
    ] = useState('');

    const loadCourses =
      useCallback(async () => {
        setIsLoading(true);
        setErrorMessage('');

        try {
          const programs =
            await academicService.getPrograms();

          const instructorName =
            normalize(user?.nombre ?? '');

          const assigned =
            programs.filter((program) => {
              return (
                Boolean(instructorName) &&
                normalize(
                  program.nombreInstructor ??
                    '',
                ) === instructorName
              );
            });

          const resolved =
            await Promise.all(
              assigned.map(
                async (
                  program,
                ): Promise<InstructorCourse> => {
                  const lessons =
                    await academicService.getLessons(
                      program.idPrograma,
                    );

                  return {
                    program,

                    lessons: [
                      ...lessons,
                    ].sort(
                      (a, b) =>
                        a.orden - b.orden,
                    ),
                  };
                },
              ),
            );

          setCourses(resolved);

          setSelectedCourse(
            (current) => {
              if (!current) {
                return null;
              }

              return (
                resolved.find(
                  ({ program }) =>
                    program.idPrograma ===
                    current.program
                      .idPrograma,
                ) ?? null
              );
            },
          );
        } catch (error) {
          setCourses([]);

          setErrorMessage(
            getApiErrorMessage(error),
          );
        } finally {
          setIsLoading(false);
        }
      }, [user?.nombre]);

    useEffect(() => {
      void loadCourses();
    }, [loadCourses]);

    const openManager = (
      course: InstructorCourse,
    ) => {
      setSelectedCourse(course);
      setEditingLesson(null);

      setForm({
        ...EMPTY_FORM,
        orden:
          course.lessons.length + 1,
      });

      setErrorMessage('');
    };

    const editLesson = (
      lesson: LeccionResponse,
    ) => {
      setEditingLesson(lesson);

      setForm({
        titulo: lesson.titulo,

        descripcion:
          lesson.descripcion ?? '',

        contenidoUrl:
          lesson.contenidoUrl ?? '',

        duracionMinutos:
          lesson.duracionMinutos,

        orden: lesson.orden,
      });
    };

    const resetForm = () => {
      setEditingLesson(null);

      setForm({
        ...EMPTY_FORM,

        orden:
          (selectedCourse?.lessons
            .length ?? 0) + 1,
      });
    };

    const saveLesson = async (
      event:
        React.FormEvent<HTMLFormElement>,
    ) => {
      event.preventDefault();

      if (!selectedCourse) {
        return;
      }

      setIsSaving(true);
      setErrorMessage('');

      try {
        const payload: LeccionRequest = {
          titulo: form.titulo.trim(),

          descripcion:
            form.descripcion?.trim() ||
            undefined,

          contenidoUrl:
            form.contenidoUrl?.trim() ||
            undefined,

          duracionMinutos:
            form.duracionMinutos,

          orden: Number(form.orden),
        };

        if (editingLesson) {
          await academicService.updateLesson(
            editingLesson.id,
            payload,
          );

          setSuccessMessage(
            'Lección actualizada correctamente.',
          );
        } else {
          await academicService.createLesson(
            selectedCourse.program
              .idPrograma,
            payload,
          );

          setSuccessMessage(
            'Lección creada correctamente.',
          );
        }

        await loadCourses();
        resetForm();
      } catch (error) {
        setErrorMessage(
          getApiErrorMessage(error),
        );
      } finally {
        setIsSaving(false);
      }
    };

    const deleteLesson = async (
      lesson: LeccionResponse,
    ) => {
      const confirmed = window.confirm(
        `¿Eliminar la lección “${lesson.titulo}”?`,
      );

      if (!confirmed) {
        return;
      }

      setDeletingId(lesson.id);
      setErrorMessage('');

      try {
        await academicService.deleteLesson(
          lesson.id,
        );

        setSuccessMessage(
          'Lección eliminada correctamente.',
        );

        await loadCourses();

        if (
          editingLesson?.id === lesson.id
        ) {
          resetForm();
        }
      } catch (error) {
        setErrorMessage(
          getApiErrorMessage(error),
        );
      } finally {
        setDeletingId(null);
      }
    };

    useEffect(() => {
      if (!successMessage) {
        return;
      }

      const timeout =
        window.setTimeout(() => {
          setSuccessMessage('');
        }, 3500);

      return () =>
        window.clearTimeout(timeout);
    }, [successMessage]);

    return (
      <section className="space-y-6">
        <div className="relative overflow-hidden rounded-3xl border border-indigo-500/15 bg-white p-8 dark:border-slate-800 dark:bg-[#0c111a]">
          <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-indigo-500/10 blur-3xl" />

          <div className="relative">
            <span className="rounded-full border border-indigo-500/20 bg-indigo-500/10 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-indigo-600 dark:text-indigo-300">
              Área del profesor
            </span>

            <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
              {focusContent
                ? 'Contenido académico'
                : 'Mis cursos'}
            </h1>

            <p className="mt-3 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
              Gestiona las lecciones reales
              de los programas que tienes
              asignados.
            </p>
          </div>
        </div>

        {successMessage && (
          <div className="flex items-center gap-3 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-700 dark:text-emerald-300">
            <CheckCircle2 className="h-5 w-5" />

            {successMessage}
          </div>
        )}

        {errorMessage &&
          !selectedCourse && (
            <ErrorPanel
              message={errorMessage}
              onRetry={loadCourses}
            />
          )}

        {isLoading ? (
          <div className="flex min-h-72 items-center justify-center">
            <LoaderCircle className="h-7 w-7 animate-spin text-cyan-500" />
          </div>
        ) : !errorMessage &&
          courses.length === 0 ? (
          <div className="flex min-h-72 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 p-8 text-center dark:border-slate-800">
            <BookOpen className="h-9 w-9 text-slate-400" />

            <h2 className="mt-4 font-bold text-slate-900 dark:text-white">
              No tienes programas
              asignados
            </h2>

            <p className="mt-2 text-sm text-slate-500">
              Cuando el administrador te
              asigne como instructor de un
              programa, aparecerá aquí.
            </p>
          </div>
        ) : (
          <div className="grid gap-5 lg:grid-cols-2 xl:grid-cols-3">
            {courses.map((course) => (
              <article
                key={
                  course.program.idPrograma
                }
                className="group flex flex-col rounded-2xl border border-cyan-500/10 bg-white p-6 transition-all hover:-translate-y-1 hover:border-cyan-500/30 dark:border-slate-800 dark:bg-[#0c111a]"
              >
                <div className="flex items-start justify-between gap-4">
                  <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-500/10 to-indigo-500/10 text-cyan-500">
                    <BookOpen className="h-5 w-5" />
                  </span>

                  <span className="rounded-full border border-indigo-500/20 bg-indigo-500/10 px-2.5 py-1 font-mono text-[9px] uppercase text-indigo-500 dark:text-indigo-300">
                    {course.program.level ||
                      'Sin nivel'}
                  </span>
                </div>

                <h2 className="mt-5 text-lg font-black text-slate-950 dark:text-white">
                  {
                    course.program
                      .tituloPrograma
                  }
                </h2>

                <p className="mt-2 text-xs text-slate-500">
                  Duración:{' '}

                  {course.program
                    .duracionPrograma ||
                    'No especificada'}
                </p>

                <div className="mt-5 rounded-xl bg-slate-50 p-3 dark:bg-slate-900/60">
                  <FilePlus2 className="h-4 w-4 text-indigo-500" />

                  <p className="mt-2 text-lg font-black text-slate-900 dark:text-white">
                    {
                      course.lessons
                        .length
                    }
                  </p>

                  <p className="text-[10px] text-slate-500">
                    Lecciones publicadas
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() =>
                    openManager(course)
                  }
                  className="mt-5 flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white transition-all hover:scale-[1.02]"
                >
                  <Plus className="h-4 w-4" />
                  Gestionar lecciones
                </button>
              </article>
            ))}
          </div>
        )}

        {selectedCourse && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/75 p-4 backdrop-blur-sm">
            <div className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-2xl border border-cyan-500/20 bg-white p-6 shadow-2xl dark:border-slate-800 dark:bg-[#0c111a]">
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-mono text-[9px] uppercase tracking-wider text-cyan-500">
                    Gestión de lecciones
                  </p>

                  <h2 className="mt-2 text-xl font-black text-slate-950 dark:text-white">
                    {
                      selectedCourse
                        .program
                        .tituloPrograma
                    }
                  </h2>
                </div>

                <button
                  type="button"
                  onClick={() => {
                    setSelectedCourse(
                      null,
                    );

                    setErrorMessage('');
                  }}
                  className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-100 text-slate-500 dark:bg-slate-900"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>

              {errorMessage && (
                <div className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-sm text-rose-600 dark:text-rose-300">
                  {errorMessage}
                </div>
              )}

              <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_0.9fr]">
                <div className="space-y-3">
                  <h3 className="text-sm font-black text-slate-900 dark:text-white">
                    Lecciones publicadas
                  </h3>

                  {selectedCourse.lessons
                    .length === 0 ? (
                    <p className="rounded-xl border border-dashed border-slate-300 p-6 text-center text-xs text-slate-500 dark:border-slate-700">
                      Aún no hay lecciones.
                    </p>
                  ) : (
                    selectedCourse.lessons.map(
                      (lesson) => (
                        <article
                          key={lesson.id}
                          className="rounded-xl border border-slate-200 p-4 dark:border-slate-800"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="font-mono text-[9px] text-cyan-500">
                                ORDEN{' '}
                                {
                                  lesson.orden
                                }
                              </p>

                              <h4 className="mt-1 text-sm font-bold text-slate-900 dark:text-white">
                                {
                                  lesson.titulo
                                }
                              </h4>

                              <p className="mt-1 text-[10px] text-slate-500">
                                {lesson.duracionMinutos ??
                                  '—'}{' '}
                                minutos
                              </p>
                            </div>

                            <div className="flex gap-1">
                              <button
                                type="button"
                                aria-label="Editar"
                                onClick={() =>
                                  editLesson(
                                    lesson,
                                  )
                                }
                                className="rounded-lg bg-slate-100 p-2 text-indigo-500 dark:bg-slate-900"
                              >
                                <Edit3 className="h-3.5 w-3.5" />
                              </button>

                              <button
                                type="button"
                                aria-label="Eliminar"
                                disabled={
                                  deletingId ===
                                  lesson.id
                                }
                                onClick={() =>
                                  void deleteLesson(
                                    lesson,
                                  )
                                }
                                className="rounded-lg bg-rose-500/10 p-2 text-rose-500 disabled:opacity-50"
                              >
                                {deletingId ===
                                lesson.id ? (
                                  <LoaderCircle className="h-3.5 w-3.5 animate-spin" />
                                ) : (
                                  <Trash2 className="h-3.5 w-3.5" />
                                )}
                              </button>
                            </div>
                          </div>
                        </article>
                      ),
                    )
                  )}
                </div>

                <form
                  onSubmit={saveLesson}
                  className="space-y-4 rounded-2xl bg-slate-50 p-5 dark:bg-slate-900/60"
                >
                  <h3 className="text-sm font-black text-slate-900 dark:text-white">
                    {editingLesson
                      ? 'Editar lección'
                      : 'Nueva lección'}
                  </h3>

                  <Field label="Título">
                    <input
                      required
                      value={form.titulo}
                      onChange={(event) =>
                        setForm({
                          ...form,

                          titulo:
                            event.target
                              .value,
                        })
                      }
                      className={
                        CONTROL_CLASS
                      }
                      placeholder="Introducción al curso"
                    />
                  </Field>

                  <Field label="Descripción">
                    <textarea
                      value={
                        form.descripcion ??
                        ''
                      }
                      onChange={(event) =>
                        setForm({
                          ...form,

                          descripcion:
                            event.target
                              .value,
                        })
                      }
                      className={`${CONTROL_CLASS} min-h-20 py-3`}
                      placeholder="Resumen de la lección"
                    />
                  </Field>

                  <Field label="URL del contenido">
                    <input
                      type="url"
                      value={
                        form.contenidoUrl ??
                        ''
                      }
                      onChange={(event) =>
                        setForm({
                          ...form,

                          contenidoUrl:
                            event.target
                              .value,
                        })
                      }
                      className={
                        CONTROL_CLASS
                      }
                      placeholder="https://..."
                    />
                  </Field>

                  <div className="grid grid-cols-2 gap-3">
                    <Field label="Duración (min)">
                      <input
                        type="number"
                        min="0"
                        value={
                          form.duracionMinutos ??
                          ''
                        }
                        onChange={(
                          event,
                        ) =>
                          setForm({
                            ...form,

                            duracionMinutos:
                              event
                                .target
                                .value
                                ? Number(
                                    event
                                      .target
                                      .value,
                                  )
                                : undefined,
                          })
                        }
                        className={
                          CONTROL_CLASS
                        }
                      />
                    </Field>

                    <Field label="Orden">
                      <input
                        required
                        type="number"
                        min="1"
                        value={form.orden}
                        onChange={(
                          event,
                        ) =>
                          setForm({
                            ...form,

                            orden:
                              Number(
                                event
                                  .target
                                  .value,
                              ),
                          })
                        }
                        className={
                          CONTROL_CLASS
                        }
                      />
                    </Field>
                  </div>

                  <div className="flex gap-2">
                    {editingLesson && (
                      <button
                        type="button"
                        onClick={resetForm}
                        className="h-11 flex-1 rounded-xl border border-slate-300 text-xs font-bold text-slate-600 dark:border-slate-700"
                      >
                        Cancelar
                      </button>
                    )}

                    <button
                      type="submit"
                      disabled={isSaving}
                      className="flex h-11 flex-1 items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white disabled:opacity-60"
                    >
                      {isSaving ? (
                        <LoaderCircle className="h-4 w-4 animate-spin" />
                      ) : (
                        <Save className="h-4 w-4" />
                      )}

                      {editingLesson
                        ? 'Guardar cambios'
                        : 'Crear lección'}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        )}
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

interface ErrorPanelProps {
  message: string;
  onRetry: () => Promise<void>;
}

const ErrorPanel:
  React.FC<ErrorPanelProps> = ({
    message,
    onRetry,
  }) => (
    <div className="flex min-h-64 flex-col items-center justify-center rounded-2xl border border-rose-500/20 p-8 text-center">
      <p className="text-sm text-rose-600 dark:text-rose-300">
        {message}
      </p>

      <button
        type="button"
        onClick={() => void onRetry()}
        className="mt-4 flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white dark:bg-cyan-500"
      >
        <RefreshCw className="h-4 w-4" />
        Reintentar
      </button>
    </div>
  );
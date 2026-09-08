import React, {
  useEffect,
  useState,
} from 'react';

import {
  BookOpen,
  CheckCircle2,
  FilePlus2,
  LoaderCircle,
  Plus,
  Users,
  X,
} from 'lucide-react';

import { useAuth } from '../../context/AuthContext';
import { academicService } from '../../services/academicService';
import { INITIAL_PLAYBOOKS } from '../../services/mockData';
import { ProgramaResponse } from '../../types';

interface InstructorCourse {
  id: string;
  title: string;
  level: string;
  duration: string;
  students: number;
  lessons: number;
  provisional: boolean;
}

export const InstructorCourses: React.FC = () => {
  const { user } = useAuth();

  const [courses, setCourses] = useState<
    InstructorCourse[]
  >([]);

  const [selectedCourse, setSelectedCourse] =
    useState<InstructorCourse | null>(null);

  const [contentTitle, setContentTitle] =
    useState('');

  const [contentType, setContentType] =
    useState('Lección');

  const [isLoading, setIsLoading] =
    useState(true);

  const [successMessage, setSuccessMessage] =
    useState('');

  useEffect(() => {
    const normalize = (value: string) =>
      value
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .trim()
        .toLowerCase();

    const loadCourses = async () => {
      setIsLoading(true);

      try {
        const programs =
          await academicService.getPrograms();

        const instructorName = normalize(
          user?.nombre || '',
        );

        const assignedPrograms = programs.filter(
          (program: ProgramaResponse) => {
            const programInstructor = normalize(
              program.nombreInstructor || '',
            );

            return (
              programInstructor === instructorName ||
              programInstructor.includes(
                instructorName,
              ) ||
              instructorName.includes(
                programInstructor,
              )
            );
          },
        );

        if (assignedPrograms.length > 0) {
          setCourses(
            assignedPrograms.map(
              (program, index) => ({
                id: String(program.idPrograma),
                title: program.tituloPrograma,
                level:
                  program.level || 'Sin nivel',
                duration:
                  program.duracionPrograma ||
                  'Por definir',
                students: 24 + index * 7,
                lessons: 6 + index * 2,
                provisional: false,
              }),
            ),
          );

          return;
        }

        setCourses(
          INITIAL_PLAYBOOKS.slice(0, 3).map(
            (playbook, index) => ({
              id: playbook.id,
              title: playbook.title,
              level: playbook.tier,
              duration: `${8 + index * 2} semanas`,
              students: 28 + index * 9,
              lessons: 6 + index * 3,
              provisional: true,
            }),
          ),
        );
      } catch {
        setCourses(
          INITIAL_PLAYBOOKS.slice(0, 3).map(
            (playbook, index) => ({
              id: playbook.id,
              title: playbook.title,
              level: playbook.tier,
              duration: `${8 + index * 2} semanas`,
              students: 28 + index * 9,
              lessons: 6 + index * 3,
              provisional: true,
            }),
          ),
        );
      } finally {
        setIsLoading(false);
      }
    };

    void loadCourses();
  }, [user?.nombre]);

  const handleAddContent = (
    event: React.FormEvent<HTMLFormElement>,
  ) => {
    event.preventDefault();

    if (!selectedCourse || !contentTitle.trim()) {
      return;
    }

    setCourses((currentCourses) =>
      currentCourses.map((course) =>
        course.id === selectedCourse.id
          ? {
              ...course,
              lessons: course.lessons + 1,
            }
          : course,
      ),
    );

    setSuccessMessage(
      `${contentType} “${contentTitle.trim()}” añadida provisionalmente a ${selectedCourse.title}.`,
    );

    setSelectedCourse(null);
    setContentTitle('');
    setContentType('Lección');

    window.setTimeout(() => {
      setSuccessMessage('');
    }, 4000);
  };

  return (
    <section className="space-y-6">
      <div className="relative overflow-hidden rounded-3xl border border-indigo-500/15 bg-white p-8 dark:border-slate-800 dark:bg-[#0c111a]">
        <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-indigo-500/10 blur-3xl" />

        <div className="relative">
          <span className="rounded-full border border-indigo-500/20 bg-indigo-500/10 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-indigo-600 dark:text-indigo-300">
            Área del profesor
          </span>

          <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
            Mis cursos
          </h1>

          <p className="mt-3 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
            Consulta los programas que tienes a cargo
            y administra sus contenidos académicos.
          </p>
        </div>
      </div>

      {successMessage && (
        <div className="flex items-center gap-3 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-700 dark:text-emerald-300">
          <CheckCircle2 className="h-5 w-5 shrink-0" />
          {successMessage}
        </div>
      )}

      {isLoading ? (
        <div className="flex min-h-72 items-center justify-center">
          <LoaderCircle className="h-7 w-7 animate-spin text-cyan-500" />
        </div>
      ) : (
        <div className="grid gap-5 lg:grid-cols-2 xl:grid-cols-3">
          {courses.map((course) => (
            <article
              key={course.id}
              className="group flex flex-col rounded-2xl border border-cyan-500/10 bg-white p-6 transition-all duration-300 hover:-translate-y-1 hover:border-cyan-500/30 dark:border-slate-800 dark:bg-[#0c111a]"
            >
              <div className="flex items-start justify-between gap-4">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-500/10 to-indigo-500/10 text-cyan-500">
                  <BookOpen className="h-5 w-5" />
                </span>

                <span className="rounded-full border border-indigo-500/20 bg-indigo-500/10 px-2.5 py-1 font-mono text-[9px] uppercase text-indigo-500 dark:text-indigo-300">
                  {course.level}
                </span>
              </div>

              <h2 className="mt-5 text-lg font-black text-slate-950 dark:text-white">
                {course.title}
              </h2>

              <p className="mt-2 text-xs text-slate-500">
                Duración: {course.duration}
              </p>

              <div className="mt-5 grid grid-cols-2 gap-3">
                <div className="rounded-xl bg-slate-50 p-3 dark:bg-slate-900/60">
                  <Users className="h-4 w-4 text-cyan-500" />

                  <p className="mt-2 text-lg font-black text-slate-900 dark:text-white">
                    {course.students}
                  </p>

                  <p className="text-[10px] text-slate-500">
                    Estudiantes
                  </p>
                </div>

                <div className="rounded-xl bg-slate-50 p-3 dark:bg-slate-900/60">
                  <FilePlus2 className="h-4 w-4 text-indigo-500" />

                  <p className="mt-2 text-lg font-black text-slate-900 dark:text-white">
                    {course.lessons}
                  </p>

                  <p className="text-[10px] text-slate-500">
                    Contenidos
                  </p>
                </div>
              </div>

              {course.provisional && (
                <p className="mt-4 font-mono text-[9px] uppercase tracking-wider text-amber-500">
                  Información provisional
                </p>
              )}

              <button
                type="button"
                onClick={() =>
                  setSelectedCourse(course)
                }
                className="mt-5 flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white transition-all hover:scale-[1.02]"
              >
                <Plus className="h-4 w-4" />
                Agregar contenido
              </button>
            </article>
          ))}
        </div>
      )}

      {selectedCourse && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl border border-cyan-500/20 bg-white p-6 shadow-2xl dark:border-slate-800 dark:bg-[#0c111a]">
            <div className="flex items-start justify-between">
              <div>
                <p className="font-mono text-[9px] uppercase tracking-wider text-cyan-500">
                  Nuevo contenido
                </p>

                <h2 className="mt-2 text-xl font-black text-slate-950 dark:text-white">
                  {selectedCourse.title}
                </h2>
              </div>

              <button
                type="button"
                onClick={() =>
                  setSelectedCourse(null)
                }
                className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-100 text-slate-500 dark:bg-slate-900"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form
              onSubmit={handleAddContent}
              className="mt-6 space-y-4"
            >
              <div>
                <label className="mb-1.5 block text-xs font-bold text-slate-700 dark:text-slate-300">
                  Tipo de contenido
                </label>

                <select
                  value={contentType}
                  onChange={(event) =>
                    setContentType(
                      event.target.value,
                    )
                  }
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm outline-none dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                >
                  <option>Lección</option>
                  <option>Evaluación</option>
                  <option>Documento</option>
                  <option>Video</option>
                </select>
              </div>

              <div>
                <label className="mb-1.5 block text-xs font-bold text-slate-700 dark:text-slate-300">
                  Título
                </label>

                <input
                  type="text"
                  value={contentTitle}
                  onChange={(event) =>
                    setContentTitle(
                      event.target.value,
                    )
                  }
                  required
                  placeholder="Ej. Introducción al curso"
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm outline-none focus:border-cyan-500/50 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                />
              </div>

              <p className="text-[10px] leading-5 text-slate-500">
                Esta acción es provisional hasta que el
                backend implemente el endpoint de
                lecciones y contenidos.
              </p>

              <button
                type="submit"
                className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white"
              >
                <Plus className="h-4 w-4" />
                Guardar contenido
              </button>
            </form>
          </div>
        </div>
      )}
    </section>
  );
};
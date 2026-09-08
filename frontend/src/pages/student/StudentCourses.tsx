import React from 'react';

import {
  Award,
  BookOpen,
  Clock3,
  PlayCircle,
} from 'lucide-react';

import { INITIAL_PLAYBOOKS } from '../../services/mockData';

const COURSE_PROGRESS = [72, 46, 18];

export const StudentCourses: React.FC = () => {
  const enrolledCourses =
    INITIAL_PLAYBOOKS.slice(0, 3);

  return (
    <section className="space-y-6">
      <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-8 dark:border-slate-800 dark:bg-[#0c111a]">
        <div className="absolute -right-24 -top-24 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />

        <div className="relative">
          <span className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-cyan-600 dark:text-cyan-300">
            Área del ejecutivo
          </span>

          <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
            Mis cursos
          </h1>

          <p className="mt-3 max-w-2xl text-sm text-slate-600 dark:text-slate-400">
            Continúa tu formación desde el último
            contenido revisado y consulta tu progreso.
          </p>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <SummaryCard
          label="Cursos activos"
          value="3"
          icon={<BookOpen className="h-5 w-5" />}
        />

        <SummaryCard
          label="Horas completadas"
          value="18 h"
          icon={<Clock3 className="h-5 w-5" />}
        />

        <SummaryCard
          label="Certificados"
          value="2"
          icon={<Award className="h-5 w-5" />}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-2 xl:grid-cols-3">
        {enrolledCourses.map(
          (course, index) => {
            const progress =
              COURSE_PROGRESS[index] ?? 0;

            return (
              <article
                key={course.id}
                className="group overflow-hidden rounded-2xl border border-cyan-500/10 bg-white transition-all duration-300 hover:-translate-y-1 hover:border-cyan-500/30 dark:border-slate-800 dark:bg-[#0c111a]"
              >
                <div className="relative aspect-[16/9] overflow-hidden bg-slate-950">
                  <img
                    src={course.coverUrl}
                    alt={course.title}
                    className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                  />

                  <div className="absolute inset-0 bg-gradient-to-t from-slate-950/70 via-transparent to-transparent" />

                  <span className="absolute bottom-3 left-3 rounded-full border border-white/10 bg-slate-950/70 px-2.5 py-1 font-mono text-[9px] uppercase text-cyan-300 backdrop-blur-md">
                    {course.category}
                  </span>
                </div>

                <div className="p-5">
                  <h2 className="text-base font-black text-slate-950 dark:text-white">
                    {course.title}
                  </h2>

                  <p className="mt-2 line-clamp-2 text-xs leading-5 text-slate-500 dark:text-slate-400">
                    {course.description}
                  </p>

                  <div className="mt-5">
                    <div className="mb-2 flex items-center justify-between text-[10px]">
                      <span className="font-semibold text-slate-500">
                        Progreso
                      </span>

                      <span className="font-mono font-bold text-cyan-600 dark:text-cyan-400">
                        {progress}%
                      </span>
                    </div>

                    <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-900">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-cyan-500 to-indigo-600 transition-all duration-700"
                        style={{
                          width: `${progress}%`,
                        }}
                      />
                    </div>
                  </div>

                  <button
                    type="button"
                    className="mt-5 flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white transition-all hover:scale-[1.02]"
                  >
                    <PlayCircle className="h-4 w-4" />
                    Continuar curso
                  </button>
                </div>
              </article>
            );
          },
        )}
      </div>

      <p className="font-mono text-[9px] uppercase tracking-wider text-slate-500">
        Los cursos inscritos son provisionales hasta
        que el backend habilite el endpoint de
        inscripciones por estudiante.
      </p>
    </section>
  );
};

interface SummaryCardProps {
  label: string;
  value: string;
  icon: React.ReactNode;
}

const SummaryCard: React.FC<SummaryCardProps> = ({
  label,
  value,
  icon,
}) => {
  return (
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
};
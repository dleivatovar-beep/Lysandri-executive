import React from 'react';

import {
  ArrowRight,
  Clock3,
  Crown,
  GraduationCap,
  ImageIcon,
  LoaderCircle,
  ShieldAlert,
  Tag,
  Zap,
} from 'lucide-react';

import {
  Playbook,
  TierLevel,
} from '../../types';

interface PlaybookCardProps {
  playbook: Playbook;

  onSelect?: (
    playbook: Playbook,
  ) => void | Promise<void>;

  isSubmitting?: boolean;
}

const TIER_CONFIG: Record<
  TierLevel,
  {
    label: string;
    badgeStyle: string;
    icon: React.ReactNode;
  }
> = {
  ESSENTIAL: {
    label: 'ESSENTIAL TIER',

    badgeStyle:
      'border-cyan-200/80 bg-white/85 text-slate-700 dark:border-cyan-500/40 dark:bg-cyan-950/75 dark:text-cyan-300',

    icon: (
      <Zap className="h-3 w-3 text-cyan-600 dark:text-cyan-400" />
    ),
  },

  ADVANCED: {
    label: 'ADVANCED TIER',

    badgeStyle:
      'border-indigo-200/80 bg-white/85 text-slate-700 dark:border-indigo-500/40 dark:bg-indigo-950/75 dark:text-indigo-300',

    icon: (
      <ShieldAlert className="h-3 w-3 text-indigo-600 dark:text-indigo-400" />
    ),
  },

  ENTERPRISE: {
    label: 'ENTERPRISE C-SUITE',

    badgeStyle:
      'border-amber-200/80 bg-white/85 text-slate-700 dark:border-amber-500/50 dark:bg-amber-950/75 dark:text-amber-300',

    icon: (
      <Crown className="h-3 w-3 text-amber-600 dark:text-amber-400" />
    ),
  },
};

export const PlaybookCard:
  React.FC<PlaybookCardProps> = ({
    playbook,
    onSelect,
    isSubmitting = false,
  }) => {
    const tierInfo =
      TIER_CONFIG[playbook.tier];

    return (
      <article className="group flex h-full flex-col overflow-hidden rounded-2xl border border-cyan-100/90 bg-white shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-cyan-300 hover:shadow-lg hover:shadow-cyan-500/10 dark:border-slate-800/80 dark:bg-[#0d121a] dark:shadow-none dark:hover:border-cyan-500/40">
        <div className="relative aspect-video w-full overflow-hidden border-b border-cyan-100 bg-[#08111e] dark:border-slate-800">
          {playbook.coverUrl ? (
            <img
              src={playbook.coverUrl}
              alt={`Vista previa del curso ${playbook.title}`}
              loading="lazy"
              className="absolute inset-0 h-full w-full object-cover object-center transition-transform duration-700 ease-out group-hover:scale-[1.03]"
            />
          ) : (
            <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-cyan-950 via-slate-950 to-indigo-950">
              <ImageIcon className="h-12 w-12 text-cyan-400/40" />
            </div>
          )}

          <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-slate-950/40 via-transparent to-cyan-400/5" />

          <div className="absolute left-4 right-4 top-4 flex items-center justify-between gap-2">
            <span
              className={`inline-flex items-center space-x-1.5 rounded-md border px-2.5 py-1 font-mono text-[9px] font-semibold tracking-wider backdrop-blur-md ${tierInfo.badgeStyle}`}
            >
              {tierInfo.icon}

              <span>
                {tierInfo.label}
              </span>
            </span>

            <span className="max-w-[11rem] truncate rounded-full border border-white/25 bg-slate-950/55 px-2.5 py-1 font-mono text-[9px] font-medium text-white backdrop-blur-md">
              {playbook.category}
            </span>
          </div>

          <div className="absolute bottom-0 left-0 h-px w-full bg-gradient-to-r from-cyan-400 via-indigo-500 to-transparent" />
        </div>

        <div className="flex flex-1 flex-col p-5">
          <div className="space-y-3">
            <h3 className="line-clamp-2 text-sm font-bold leading-snug text-slate-900 transition-colors group-hover:text-indigo-600 md:text-base dark:text-slate-100 dark:group-hover:text-cyan-300">
              {playbook.title}
            </h3>

            <p className="line-clamp-3 text-xs leading-relaxed text-slate-600 dark:text-slate-400">
              {playbook.description}
            </p>

            {playbook.tags.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                {playbook.tags.map(
                  (tag) => (
                    <span
                      key={tag}
                      className="inline-flex items-center space-x-1 rounded border border-cyan-100 bg-cyan-50/50 px-2 py-0.5 font-mono text-[9px] text-slate-600 dark:border-slate-800 dark:bg-slate-900/90 dark:text-slate-400"
                    >
                      <Tag className="h-2.5 w-2.5 text-cyan-600/70 dark:text-cyan-500/70" />

                      <span>
                        {tag}
                      </span>
                    </span>
                  ),
                )}
              </div>
            )}
          </div>

          <div className="mt-auto pt-5">
            <div className="mb-4 space-y-2 border-t border-cyan-100 pt-4 text-[10px] text-slate-500 dark:border-slate-800/80 dark:text-slate-400">
              {playbook.duration && (
                <p className="flex items-center gap-2">
                  <Clock3 className="h-3.5 w-3.5 text-cyan-500" />

                  Duración:{' '}
                  {playbook.duration}
                </p>
              )}

              {playbook.instructor && (
                <p className="flex items-center gap-2">
                  <GraduationCap className="h-3.5 w-3.5 text-indigo-500" />

                  Profesor:{' '}
                  {playbook.instructor}
                </p>
              )}
            </div>

            <button
              type="button"
              disabled={isSubmitting}
              onClick={() =>
                void onSelect?.(playbook)
              }
              className="flex h-10 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-4 text-xs font-bold text-white shadow-md shadow-cyan-500/15 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-cyan-500/25 active:translate-y-0 active:scale-95 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isSubmitting ? (
                <LoaderCircle className="h-4 w-4 animate-spin" />
              ) : (
                <ArrowRight className="h-3.5 w-3.5" />
              )}

              <span>
                {isSubmitting
                  ? 'Inscribiendo...'
                  : 'Inscribirse'}
              </span>
            </button>
          </div>
        </div>
      </article>
    );
  };
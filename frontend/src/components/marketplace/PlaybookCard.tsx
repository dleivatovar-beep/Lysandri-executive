// src/components/marketplace/PlaybookCard.tsx
import React from 'react';
import {
  Star,
  Download,
  Tag,
  ArrowRight,
  ShieldAlert,
  Zap,
  Crown,
} from 'lucide-react';
import { Playbook, TierLevel } from '../../types';

interface PlaybookCardProps {
  playbook: Playbook;
  onSelect?: (playbook: Playbook) => void;
}

const TIER_CONFIG: Record<
  TierLevel,
  { label: string; badgeStyle: string; icon: React.ReactNode }
> = {
  ESSENTIAL: {
    label: 'ESSENTIAL TIER',
    badgeStyle:
      'bg-white/85 dark:bg-cyan-950/75 text-slate-700 dark:text-cyan-300 border-cyan-200/80 dark:border-cyan-500/40',
    icon: <Zap className="h-3 w-3 text-cyan-600 dark:text-cyan-400" />,
  },
  ADVANCED: {
    label: 'ADVANCED TIER',
    badgeStyle:
      'bg-white/85 dark:bg-indigo-950/75 text-slate-700 dark:text-indigo-300 border-indigo-200/80 dark:border-indigo-500/40',
    icon: <ShieldAlert className="h-3 w-3 text-indigo-600 dark:text-indigo-400" />,
  },
  ENTERPRISE: {
    label: 'ENTERPRISE C-SUITE',
    badgeStyle:
      'bg-white/85 dark:bg-amber-950/75 text-slate-700 dark:text-amber-300 border-amber-200/80 dark:border-amber-500/50',
    icon: <Crown className="h-3 w-3 text-amber-600 dark:text-amber-400" />,
  },
};

export const PlaybookCard: React.FC<PlaybookCardProps> = ({
  playbook,
  onSelect,
}) => {
  const tierInfo = TIER_CONFIG[playbook.tier];
  const priceInSoles = (playbook.price * 3.75).toFixed(2);

  return (
    <article className="group flex h-full flex-col overflow-hidden rounded-2xl border border-cyan-100/90 bg-white shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-cyan-300 hover:shadow-lg hover:shadow-cyan-500/10 dark:border-slate-800/80 dark:bg-[#0d121a] dark:shadow-none dark:hover:border-cyan-500/40">
      <div className="relative aspect-video w-full overflow-hidden border-b border-cyan-100 bg-[#08111e] dark:border-slate-800 dark:bg-slate-900">
        <img
          src={playbook.coverUrl}
          alt={`Vista previa del curso ${playbook.title}`}
          loading="lazy"
          className="absolute inset-0 h-full w-full object-cover object-center transition-transform duration-700 ease-out group-hover:scale-[1.03]"
        />

        <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-slate-950/30 via-transparent to-cyan-400/5" />

        <div className="absolute left-4 right-4 top-4 flex items-center justify-between gap-2">
          <span
            className={`inline-flex items-center space-x-1.5 rounded-md border px-2.5 py-1 text-[9px] font-mono font-semibold tracking-wider backdrop-blur-md ${tierInfo.badgeStyle}`}
          >
            {tierInfo.icon}
            <span>{tierInfo.label}</span>
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

          <div className="flex flex-wrap gap-1.5 pt-1">
            {playbook.tags.map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center space-x-1 rounded border border-cyan-100 bg-cyan-50/50 px-2 py-0.5 font-mono text-[9px] text-slate-600 dark:border-slate-800 dark:bg-slate-900/90 dark:text-slate-400"
              >
                <Tag className="h-2.5 w-2.5 text-cyan-600/70 dark:text-cyan-500/70" />
                <span>{tag}</span>
              </span>
            ))}
          </div>
        </div>

        <div className="mt-5 flex items-center justify-between border-t border-cyan-100 pt-4 dark:border-slate-800/80">
          <div className="space-y-1">
            <div className="flex items-baseline space-x-1">
              <span className="font-mono text-base font-extrabold text-slate-900 dark:text-slate-100">
                S/ {priceInSoles}
              </span>
              <span className="font-mono text-[9px] text-slate-500">PEN</span>
            </div>

            <div className="flex items-center space-x-3 font-mono text-[10px] text-slate-600 dark:text-slate-400">
              <span className="flex items-center space-x-1 font-semibold text-amber-500 dark:text-amber-400">
                <Star className="h-3 w-3 fill-amber-500 text-amber-500 dark:fill-amber-400 dark:text-amber-400" />
                <span>{playbook.rating.toFixed(2)}</span>
              </span>

              <span className="flex items-center space-x-1 text-slate-500 dark:text-slate-400">
                <Download className="h-3 w-3 text-cyan-600 dark:text-cyan-400" />
                <span>{playbook.downloadsCount} descargas</span>
              </span>
            </div>
          </div>

          <button
            onClick={() => onSelect?.(playbook)}
            className="flex items-center space-x-1.5 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-3.5 py-2 text-xs font-bold text-white shadow-md shadow-cyan-500/15 transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-cyan-500/25 active:translate-y-0 active:scale-95"
          >
            <span>Inscribirse</span>
            <ArrowRight className="h-3.5 w-3.5 transition-transform duration-300 group-hover:translate-x-0.5" />
          </button>
        </div>
      </div>
    </article>
  );
};
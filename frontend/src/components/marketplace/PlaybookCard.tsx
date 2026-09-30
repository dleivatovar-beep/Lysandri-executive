import React from 'react';
import {
  Clock3,
  Crown,
  GraduationCap,
  ShieldAlert,
  Tag,
  Zap,
  BookOpen,
  Bot,
  ShoppingCart,
  Star,
  Check,
} from 'lucide-react';
import { Playbook, TierLevel } from '../../types';
import { useCart } from '../../context/CartContext';

interface PlaybookCardProps {
  playbook: Playbook;
  onSelect?: (playbook: Playbook) => void | Promise<void>;
  onPreviewSyllabus?: (playbook: Playbook) => void;
  onAddToCart?: (playbook: Playbook) => void;
  onAskAi?: (playbook: Playbook) => void;
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
      'border-cyan-400/40 bg-slate-950/80 text-cyan-300 dark:border-cyan-500/50 dark:bg-cyan-950/80 dark:text-cyan-300',
    icon: <Zap className="h-3 w-3 text-cyan-400" />,
  },
  ADVANCED: {
    label: 'ADVANCED TIER',
    badgeStyle:
      'border-indigo-400/40 bg-slate-950/80 text-indigo-300 dark:border-indigo-500/50 dark:bg-indigo-950/80 dark:text-indigo-300',
    icon: <ShieldAlert className="h-3 w-3 text-indigo-400" />,
  },
  ENTERPRISE: {
    label: 'ENTERPRISE C-SUITE',
    badgeStyle:
      'border-amber-400/50 bg-slate-950/80 text-amber-300 dark:border-amber-500/60 dark:bg-amber-950/80 dark:text-amber-300',
    icon: <Crown className="h-3 w-3 text-amber-400" />,
  },
};

export const PlaybookCard: React.FC<PlaybookCardProps> = ({
  playbook,
  onPreviewSyllabus,
  onAddToCart,
  onAskAi,
}) => {
  const tierInfo = TIER_CONFIG[playbook.tier];

  let isInCart = false;
  try {
    const { cartItems } = useCart();
    isInCart = cartItems.some((item) => item.programId === playbook.programId);
  } catch {
    // Graceful fallback
  }

  const getPrice = (): number => {
    if (playbook.tier === 'ENTERPRISE') return 950;
    if (playbook.tier === 'ADVANCED') return 899;
    return 799;
  };

  return (
    <article className="group flex h-full flex-col overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm transition-all duration-300 hover:-translate-y-1.5 hover:border-cyan-400/50 hover:shadow-xl hover:shadow-cyan-500/10 dark:border-slate-800/80 dark:bg-[#0d121a] dark:shadow-none dark:hover:border-cyan-500/50">
      {/* Media Aspect Header */}
      <div className="relative aspect-video w-full overflow-hidden border-b border-slate-100 bg-[#08111e] dark:border-slate-800">
        {playbook.coverUrl ? (
          <img
            src={playbook.coverUrl}
            alt={`Vista previa del curso ${playbook.title}`}
            loading="lazy"
            className="absolute inset-0 h-full w-full object-cover object-center transition-transform duration-700 ease-out group-hover:scale-[1.05]"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-slate-900 text-slate-600">
            <BookOpen className="h-10 w-10" />
          </div>
        )}

        <div className="absolute inset-0 bg-gradient-to-t from-[#08111e] via-[#08111e]/30 to-transparent opacity-90" />

        {/* Top Badges */}
        <div className="absolute left-3.5 right-3.5 top-3.5 flex items-center justify-between gap-2">
          <span
            className={`inline-flex items-center space-x-1.5 rounded-md border px-2.5 py-1 font-mono text-[9px] font-bold tracking-wider backdrop-blur-md shadow-md ${tierInfo.badgeStyle}`}
          >
            {tierInfo.icon}
            <span>{tierInfo.label}</span>
          </span>

          <span className="max-w-[11rem] truncate rounded-full border border-white/20 bg-slate-950/65 px-2.5 py-1 font-mono text-[9px] font-medium text-slate-200 backdrop-blur-md">
            {playbook.category}
          </span>
        </div>

        {/* Price tag & Moodle Sync pill */}
        <div className="absolute bottom-3 left-3.5 right-3.5 flex items-center justify-between">
          <span className="inline-flex items-center gap-1 rounded-md bg-slate-950/80 border border-slate-700/80 px-2 py-0.5 font-mono text-[9px] text-slate-300 backdrop-blur-md">
            <GraduationCap className="h-3 w-3 text-indigo-400" />
            Moodle 4.x
          </span>

          <div className="rounded-lg bg-slate-950/90 backdrop-blur-md px-2.5 py-1 border border-cyan-500/40 text-xs font-black text-cyan-300 shadow-md">
            ${getPrice()} USD
          </div>
        </div>

        <div className="absolute bottom-0 left-0 h-0.5 w-full bg-gradient-to-r from-cyan-400 via-indigo-500 to-transparent" />
      </div>

      {/* Body Content */}
      <div className="flex flex-1 flex-col p-5">
        <div className="space-y-3">
          {/* Rating and Reviews */}
          <div className="flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400">
            <div className="flex items-center gap-1 text-amber-400 font-bold">
              <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
              <span>4.9</span>
              <span className="text-slate-400 font-normal">/ 5.0</span>
            </div>
            <span className="font-mono text-[10px] text-slate-400">
              120+ graduados
            </span>
          </div>

          <h3 className="line-clamp-2 text-sm font-bold leading-snug text-slate-900 transition-colors group-hover:text-cyan-600 md:text-base dark:text-slate-100 dark:group-hover:text-cyan-300">
            {playbook.title}
          </h3>

          <p className="line-clamp-3 text-xs leading-relaxed text-slate-600 dark:text-slate-400">
            {playbook.description}
          </p>

          {/* Tags */}
          {playbook.tags.length > 0 && (
            <div className="flex flex-wrap gap-1.5 pt-1">
              {playbook.tags.map((tag) => (
                <span
                  key={tag}
                  className="inline-flex items-center space-x-1 rounded border border-cyan-200/60 bg-cyan-50/50 px-2 py-0.5 font-mono text-[9px] text-slate-600 dark:border-slate-800 dark:bg-slate-900/90 dark:text-slate-400"
                >
                  <Tag className="h-2.5 w-2.5 text-cyan-600/70 dark:text-cyan-500/70" />
                  <span>{tag}</span>
                </span>
              ))}
            </div>
          )}
        </div>

        {/* Card Footer Info & Actions */}
        <div className="mt-auto pt-4">
          <div className="mb-3 space-y-1.5 border-t border-slate-100 pt-3 text-[11px] text-slate-500 dark:border-slate-800/80 dark:text-slate-400">
            {playbook.duration && (
              <p className="flex items-center gap-2">
                <Clock3 className="h-3.5 w-3.5 text-cyan-500 shrink-0" />
                <span>Dedicación: <strong className="text-slate-700 dark:text-slate-300 font-medium">{playbook.duration}</strong></span>
              </p>
            )}
            {playbook.instructor && (
              <p className="flex items-center gap-2">
                <GraduationCap className="h-3.5 w-3.5 text-indigo-500 shrink-0" />
                <span className="truncate">Profesor: <strong className="text-slate-700 dark:text-slate-300 font-medium">{playbook.instructor}</strong></span>
              </p>
            )}
          </div>

          {/* Action buttons */}
          <div className="space-y-2">
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => onPreviewSyllabus?.(playbook)}
                className="flex items-center justify-center gap-1.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/70 py-2 text-[11px] font-semibold text-slate-700 dark:text-slate-200 hover:border-cyan-400 hover:text-cyan-600 dark:hover:text-cyan-300 transition-colors shadow-sm"
              >
                <BookOpen className="h-3.5 w-3.5 text-cyan-500" />
                <span>Ver Sílabo</span>
              </button>

              <button
                type="button"
                onClick={() => onAskAi?.(playbook)}
                className="flex items-center justify-center gap-1.5 rounded-xl border border-cyan-500/30 bg-cyan-950/40 py-2 text-[11px] font-semibold text-cyan-400 hover:bg-cyan-900/50 hover:border-cyan-400/60 transition-colors shadow-sm"
              >
                <Bot className="h-3.5 w-3.5" />
                <span>Consultar IA</span>
              </button>
            </div>

            <button
              type="button"
              onClick={() => onAddToCart?.(playbook)}
              className={`flex h-10 w-full items-center justify-center gap-2 rounded-xl px-4 text-xs font-bold transition-all duration-300 active:scale-95 shadow-md ${
                isInCart
                  ? 'border border-emerald-500/40 bg-emerald-950/60 text-emerald-300 shadow-emerald-950/30'
                  : 'bg-gradient-to-r from-cyan-500 to-indigo-600 text-white shadow-cyan-500/20 hover:opacity-95'
              }`}
            >
              {isInCart ? (
                <>
                  <Check className="h-4 w-4 text-emerald-400" />
                  <span>En el Carrito (Ver Orden)</span>
                </>
              ) : (
                <>
                  <ShoppingCart className="h-3.5 w-3.5" />
                  <span>Añadir al Carrito</span>
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </article>
  );
};
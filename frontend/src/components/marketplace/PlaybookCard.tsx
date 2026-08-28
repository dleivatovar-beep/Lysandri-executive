// src/components/marketplace/PlaybookCard.tsx
import React from 'react';
import { 
  Star, 
  Download, 
  Tag, 
  ArrowRight, 
  ShieldAlert, 
  Zap, 
  Crown
} from 'lucide-react';
import { Playbook, TierLevel } from '../../types';

interface PlaybookCardProps {
  playbook: Playbook;
  onSelect?: (playbook: Playbook) => void;
}

const TIER_CONFIG: Record<TierLevel, { label: string; badgeStyle: string; icon: React.ReactNode }> = {
  ESSENTIAL: {
    label: "ESSENTIAL TIER",
    badgeStyle: "bg-cyan-950/60 text-cyan-300 border-cyan-500/40 shadow-sm",
    icon: <Zap className="w-3 h-3 text-cyan-400" />
  },
  ADVANCED: {
    label: "ADVANCED TIER",
    badgeStyle: "bg-indigo-950/60 text-indigo-300 border-indigo-500/40 shadow-sm",
    icon: <ShieldAlert className="w-3 h-3 text-indigo-400" />
  },
  ENTERPRISE: {
    label: "ENTERPRISE C-SUITE",
    badgeStyle: "bg-amber-950/60 text-amber-300 border-amber-500/50 shadow-glow-amber",
    icon: <Crown className="w-3 h-3 text-amber-400" />
  }
};

export const PlaybookCard: React.FC<PlaybookCardProps> = ({ playbook, onSelect }) => {
  const tierInfo = TIER_CONFIG[playbook.tier];

  return (
    <div className="group relative flex flex-col justify-between p-5 rounded-2xl glass-card transition-all duration-300 hover:-translate-y-1 border border-slate-800/80 hover:border-cyan-500/40">
      {/* Top Metadata: Tier & Category */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <span className={`inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-md text-[9px] font-mono tracking-wider font-semibold border ${tierInfo.badgeStyle}`}>
            {tierInfo.icon}
            <span>{tierInfo.label}</span>
          </span>

          <span className="text-[10px] font-medium text-slate-400 bg-slate-900/90 px-2.5 py-0.5 rounded-full border border-slate-800 font-mono">
            {playbook.category}
          </span>
        </div>

        {/* Title */}
        <h3 className="text-sm md:text-base font-bold text-slate-100 group-hover:text-cyan-300 transition-colors line-clamp-2 leading-snug">
          {playbook.title}
        </h3>

        {/* Description */}
        <p className="text-xs text-slate-400 leading-relaxed line-clamp-3">
          {playbook.description}
        </p>

        {/* Tags */}
        <div className="flex flex-wrap gap-1.5 pt-1">
          {playbook.tags.map((tag) => (
            <span 
              key={tag}
              className="inline-flex items-center space-x-1 text-[9px] font-mono px-2 py-0.5 rounded bg-slate-900/90 text-slate-400 border border-slate-800"
            >
              <Tag className="w-2.5 h-2.5 text-cyan-500/70" />
              <span>{tag}</span>
            </span>
          ))}
        </div>
      </div>

      {/* Footer Metrics & Action Button */}
      <div className="pt-4 mt-4 border-t border-slate-800/80 flex items-center justify-between">
        <div className="space-y-1">
          {/* Price */}
          <div className="flex items-baseline space-x-1">
            <span className="text-base font-extrabold font-mono text-slate-100">
              ${playbook.price.toLocaleString()}
            </span>
            <span className="text-[9px] text-slate-500 font-mono">USD</span>
          </div>

          {/* Ratings & Downloads */}
          <div className="flex items-center space-x-3 text-[10px] text-slate-400 font-mono">
            <span className="flex items-center space-x-1 text-amber-400 font-semibold">
              <Star className="w-3 h-3 fill-amber-400 text-amber-400" />
              <span>{playbook.rating.toFixed(2)}</span>
            </span>
            <span className="flex items-center space-x-1 text-slate-400">
              <Download className="w-3 h-3 text-cyan-400" />
              <span>{playbook.downloadsCount} descargas</span>
            </span>
          </div>
        </div>

        {/* Action Button */}
        <button
          onClick={() => onSelect && onSelect(playbook)}
          className="flex items-center space-x-1.5 px-3.5 py-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 hover:from-cyan-400 hover:to-indigo-500 text-slate-950 font-bold text-xs shadow-glow-cyan transition-all duration-200 active:scale-95"
        >
          <span>Adquirir</span>
          <ArrowRight className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
};

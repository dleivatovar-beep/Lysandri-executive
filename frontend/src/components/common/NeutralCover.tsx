import React from 'react';

interface NeutralCoverProps {
  title: string;
  category?: string;
  level?: string;
  className?: string;
}

export const NeutralCover: React.FC<NeutralCoverProps> = ({
  title,
  category = 'Programa Ejecutivo',
  level,
  className = '',
}) => {
  return (
    <div className={`lx-neutral-cover ${className}`} aria-label={`Portada institucional: ${title}`}>
      <div className="lx-neutral-cover-pattern" aria-hidden="true" />
      <div className="lx-neutral-cover-header">
        <span className="lx-neutral-cover-tag">{category}</span>
        {level && <span className="lx-neutral-cover-level"> · {level}</span>}
      </div>
      <div className="lx-neutral-cover-body">
        <h4 className="lx-neutral-cover-title">{title}</h4>
      </div>
      <div className="lx-neutral-cover-footer">
        <span className="lx-neutral-cover-brand">LYSANDRI</span>
        <span className="lx-neutral-cover-label">Temario Oficial</span>
      </div>
    </div>
  );
};


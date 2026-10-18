import React from 'react';
import { ArrowUpRight, BookOpen, Bot, Check, Clock3, CreditCard, ShoppingCart } from 'lucide-react';
import { useCart } from '../../context/CartContext';
import { getCourseCover } from '../../services/courseCatalog';
import { Playbook } from '../../types';

interface ProgramRowProps {
  playbook: Playbook;
  onPreviewSyllabus: (playbook: Playbook) => void;
  onAddToCart: (playbook: Playbook) => void;
  onAskAi: (playbook: Playbook) => void;
  onEnroll?: (playbook: Playbook) => void;
}

export const ProgramRow: React.FC<ProgramRowProps> = ({
  playbook,
  onPreviewSyllabus,
  onAddToCart,
  onAskAi,
  onEnroll,
}) => {
  const { cartItems } = useCart();
  const isInCart = cartItems.some((item) => item.programId === playbook.programId);
  const coverUrl = playbook.coverUrl || getCourseCover(playbook.title);

  return (
    <article className="lx-program-row">
      <div className="lx-program-artwork" aria-hidden="true">
        {coverUrl ? (
          <img src={coverUrl} alt="" loading="lazy" />
        ) : (
          <div className="lx-program-artwork-fallback"><BookOpen /></div>
        )}
        <span className="lx-program-tier">{playbook.tier === 'ENTERPRISE' ? 'Enterprise' : playbook.tier === 'ADVANCED' ? 'Advanced' : 'Essential'}</span>
      </div>
      <div className="lx-program-main">
        <span className="lx-program-category">{playbook.category}{playbook.isDemo && <span className="lx-demo-label"> · Curso simulado</span>}</span>
        <h3>{playbook.title}</h3>
        <p>{playbook.description}</p>
        <div className="lx-program-meta">
          {playbook.duration && <span><Clock3 aria-hidden="true" />{playbook.duration}</span>}
          {playbook.instructor && <span>{playbook.instructor}</span>}
        </div>
      </div>

      <div className="lx-program-actions">
        <p className="lx-program-price">
          <span>Precio</span>
          <strong>{playbook.isDemo ? 'Programa de muestra' : playbook.price != null ? new Intl.NumberFormat('es-PE', { style: 'currency', currency: playbook.currency || 'PEN' }).format(playbook.price) : 'Consultar precio'}</strong>
        </p>
        <button type="button" className="lx-program-primary" onClick={() => onPreviewSyllabus(playbook)}>
          <BookOpen aria-hidden="true" /> Ver sílabo <ArrowUpRight aria-hidden="true" />
        </button>
        {onEnroll && !playbook.isDemo && (
          <button
            type="button"
            className="lx-program-enroll-btn"
            onClick={() => onEnroll(playbook)}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '0.45rem',
              width: '100%',
              minHeight: '2.5rem',
              background: '#091d30',
              color: '#ffffff',
              fontSize: '0.82rem',
              fontWeight: 700,
              cursor: 'pointer',
              border: 'none',
              transition: 'all 150ms ease',
            }}
          >
            <CreditCard aria-hidden="true" style={{ width: '0.9rem', height: '0.9rem', color: '#49c9e8' }} /> Inscribirme
          </button>
        )}
        <div className="lx-program-secondary">
          <button type="button" onClick={() => onAskAi(playbook)} disabled={playbook.isDemo}>
            <Bot aria-hidden="true" /> Consultar IA
          </button>
          <button type="button" onClick={() => onAddToCart(playbook)} disabled={playbook.isDemo}>
            {isInCart ? <Check aria-hidden="true" /> : <ShoppingCart aria-hidden="true" />}
            {playbook.isDemo ? 'Solo demostración' : isInCart ? 'En el carrito' : 'Añadir al carrito'}
          </button>
        </div>
      </div>
    </article>
  );
};

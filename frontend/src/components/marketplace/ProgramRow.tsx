import React from 'react';
import { ArrowUpRight, BookOpen, Bot, Check, Clock3, ShoppingCart } from 'lucide-react';
import { useCart } from '../../context/CartContext';
import { Playbook } from '../../types';

interface ProgramRowProps {
  playbook: Playbook;
  index: number;
  onPreviewSyllabus: (playbook: Playbook) => void;
  onAddToCart: (playbook: Playbook) => void;
  onAskAi: (playbook: Playbook) => void;
}

export const ProgramRow: React.FC<ProgramRowProps> = ({
  playbook,
  index,
  onPreviewSyllabus,
  onAddToCart,
  onAskAi,
}) => {
  const { cartItems } = useCart();
  const isInCart = cartItems.some((item) => item.programId === playbook.programId);

  return (
    <article className="lx-program-row">
      <span className="lx-program-number" aria-hidden="true">
        {String(index + 1).padStart(2, '0')}
      </span>

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
        <button type="button" className="lx-program-primary" onClick={() => onPreviewSyllabus(playbook)}>
          <BookOpen aria-hidden="true" /> Ver sílabo <ArrowUpRight aria-hidden="true" />
        </button>
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

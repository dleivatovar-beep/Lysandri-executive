import React, { useState } from 'react';
import { NeutralCover } from '../common/NeutralCover';
import { ArrowUpRight, BookOpen, Bot, Check, Clock3, CreditCard, ShoppingCart } from 'lucide-react';
import { useCart, isPlaybookPurchasable } from '../../context/CartContext';
import { getCourseCover } from '../../services/courseCatalog';
import { Playbook } from '../../types';

interface ProgramRowProps {
  playbook: Playbook;
  onPreviewSyllabus: (playbook: Playbook) => void;
  onAddToCart: (playbook: Playbook) => void;
  onAskAi: (playbook: Playbook) => void;
  onEnroll?: (playbook: Playbook) => void;
}

const ProgramCoverImage: React.FC<{
  src: string;
  title: string;
  category?: string;
  fallbackSrc?: string;
}> = ({ src, title, category, fallbackSrc }) => {
  const [failedUrls, setFailedUrls] = useState<string[]>([]);
  const currentSrc = [src, fallbackSrc].find((url) => url && !failedUrls.includes(url));

  if (!currentSrc) {
    return <NeutralCover title={title} category={category} />;
  }

  return (
    <img
      src={currentSrc}
      alt={title || 'Portada del programa'}
      loading="lazy"
      onError={() => setFailedUrls((urls) => [...urls, currentSrc])}
    />
  );
};

export const ProgramRow: React.FC<ProgramRowProps> = ({
  playbook,
  onPreviewSyllabus,
  onAddToCart,
  onAskAi,
  onEnroll,
}) => {
  const { cartItems } = useCart();
  const isInCart = cartItems.some((item) => item.programId === playbook.programId);
  const isPurchasable = isPlaybookPurchasable(playbook);
  const isSample = playbook.origin === 'SAMPLE' || playbook.isSample === true || playbook.isDemo === true ||
    (typeof playbook.id === 'string' && playbook.id.startsWith('sample-'));
  const fallbackCover = getCourseCover(playbook.title);
  const coverUrl = playbook.coverUrl || fallbackCover;

  return (
    <article className="lx-program-row">
      <div className="lx-catalog-cover" aria-hidden="true">
        {coverUrl ? (
          <ProgramCoverImage
            src={coverUrl}
            fallbackSrc={fallbackCover}
            title={playbook.title}
            category={playbook.category}
          />
        ) : (
          <NeutralCover title={playbook.title} category={playbook.category} />
        )}
      </div>
      <div className="lx-program-main">
        <span className="lx-program-category">
          {playbook.category}
        </span>
          <span className="lx-program-tier">{playbook.tier === 'ENTERPRISE' ? 'Enterprise' : playbook.tier === 'ADVANCED' ? 'Advanced' : 'Essential'}</span>
        <h3>{playbook.title}</h3>
        <p>{playbook.description}</p>
        <div className="lx-program-meta">
          {playbook.duration && <span><Clock3 aria-hidden="true" />{playbook.duration}</span>}
          {playbook.instructor && <span>{playbook.instructor}</span>}
        </div>
      </div>

      <div className="lx-program-actions">
        <p className="lx-program-price">
          <span>{isSample ? 'Precio referencial' : 'Precio'}</span>
          <strong>
            {playbook.price != null && Number.isFinite(playbook.price) && playbook.currency && ['PEN', 'USD'].includes(playbook.currency.toUpperCase())
              ? new Intl.NumberFormat('es-PE', { style: 'currency', currency: playbook.currency!.toUpperCase() }).format(playbook.price)
              : isSample
              ? 'Precio por confirmar'
              : 'Consultar precio'}
          </strong>
        </p>
        <button type="button" className="lx-program-primary" onClick={() => onPreviewSyllabus(playbook)}>
          <BookOpen aria-hidden="true" /> Ver sílabo <ArrowUpRight aria-hidden="true" />
        </button>
        {onEnroll && (
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
            <CreditCard aria-hidden="true" style={{ width: '0.9rem', height: '0.9rem', color: '#49c9e8' }} /> Inscripción
          </button>
        )}
        <div className="lx-program-secondary">
          <button type="button" onClick={() => onAskAi(playbook)}>
            <Bot aria-hidden="true" /> Consultar IA
          </button>
          {isPurchasable || isSample ? (
            <button type="button" onClick={() => onAddToCart(playbook)}>
              {isInCart ? <Check aria-hidden="true" /> : <ShoppingCart aria-hidden="true" />}
              {isInCart ? 'En el carrito' : 'Añadir al carrito'}
            </button>
          ) : null}
        </div>
      </div>
    </article>
  );
};

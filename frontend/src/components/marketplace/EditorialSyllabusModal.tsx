import React, { useId } from 'react';
import { ArrowUpRight, Bot, CreditCard, ShoppingBag, X } from 'lucide-react';
import { useDialogAccessibility } from '../../hooks/useDialogAccessibility';
import { Playbook } from '../../types';
import './editorial-syllabus.css';

interface EditorialSyllabusModalProps {
  isOpen: boolean;
  onClose: () => void;
  playbook: Playbook | null;
  onAddToCart: (playbook: Playbook) => void;
  onAskAi: (playbook: Playbook) => void;
  onEnroll?: (playbook: Playbook) => void;
}

const levelName: Record<Playbook['tier'], string> = {
  ESSENTIAL: 'Esencial',
  ADVANCED: 'Avanzado',
  ENTERPRISE: 'Empresarial',
};

export const EditorialSyllabusModal: React.FC<EditorialSyllabusModalProps> = ({
  isOpen,
  onClose,
  playbook,
  onAddToCart,
  onAskAi,
  onEnroll,
}) => {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useDialogAccessibility<HTMLDivElement>(isOpen && Boolean(playbook), onClose);

  if (!isOpen || !playbook) return null;

  const topics = playbook.tags.filter((tag) => tag.trim().length > 0);

  return (
    <div className="lx-syllabus-overlay">
      <div className="lx-syllabus-backdrop" onClick={onClose} aria-hidden="true" />
      <div ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={descriptionId} tabIndex={-1} className="lx-syllabus-dialog">
        <button type="button" className="lx-syllabus-close" onClick={onClose} data-dialog-close aria-label="Cerrar sílabo"><X aria-hidden="true" /></button>

        <div className="lx-syllabus-intro">
          <span className="lx-syllabus-category">{playbook.category}{playbook.isDemo && ' · Curso simulado'}</span>
          <h2 id={titleId}>{playbook.title}</h2>
          <p id={descriptionId}>{playbook.description}</p>
          <span className="lx-syllabus-level">Nivel {levelName[playbook.tier]}</span>
        </div>

        <div className="lx-syllabus-body">
          <div className="lx-syllabus-facts">
            {playbook.duration && <div><span>Duración</span><strong>{playbook.duration}</strong></div>}
            {playbook.instructor && <div><span>Instructor</span><strong>{playbook.instructor}</strong></div>}
            <div><span>Nivel</span><strong>{levelName[playbook.tier]}</strong></div>
          </div>

          {topics.length > 0 && (
            <section className="lx-syllabus-section">
              <h3>Temas relacionados</h3>
              <ul className="lx-syllabus-topics">
                {topics.map((topic) => <li key={topic}>{topic}</li>)}
              </ul>
            </section>
          )}

          <section className="lx-syllabus-section lx-syllabus-pending">
            <h3>Temario detallado</h3>
            <p>{playbook.isDemo ? 'Este curso es una muestra visual y no está disponible para compra o inscripción.' : 'La malla de módulos todavía no está publicada en el catálogo. Esta vista muestra únicamente la información disponible del programa.'}</p>
          </section>

          {!playbook.isDemo && (
            <div className="lx-syllabus-actions">
              {onEnroll && (
                <button
                  type="button"
                  className="lx-syllabus-primary"
                  onClick={() => {
                    onEnroll(playbook);
                    onClose();
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '0.5rem',
                    background: '#009fd0',
                    color: '#071827',
                    fontWeight: 800,
                  }}
                >
                  <CreditCard aria-hidden="true" style={{ width: '1.05rem', height: '1.05rem' }} /> Inscribirme / Matrícula Directa <ArrowUpRight aria-hidden="true" />
                </button>
              )}
              <button type="button" className="lx-syllabus-secondary" onClick={() => { onAddToCart(playbook); onClose(); }}>
                <ShoppingBag aria-hidden="true" /> Añadir al carrito <ArrowUpRight aria-hidden="true" />
              </button>
              <button type="button" className="lx-syllabus-secondary" onClick={() => { onAskAi(playbook); onClose(); }}>
                <Bot aria-hidden="true" /> Consultar sobre este programa
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

import React, { useId } from 'react';
import { ArrowUpRight, Bot, CreditCard, ShoppingBag, X } from 'lucide-react';
import { useDialogAccessibility } from '../../hooks/useDialogAccessibility';
import { Playbook } from '../../types';
import { isPlaybookPurchasable } from '../../context/CartContext';
import { obtenerSilabo } from '../../services/silabos';
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

  const isPurchasable = isPlaybookPurchasable(playbook);
  const silabo = obtenerSilabo(playbook.programId);

  return (
    <div className="lx-syllabus-overlay">
      <div className="lx-syllabus-backdrop" onClick={onClose} aria-hidden="true" />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        tabIndex={-1}
        className="lx-syllabus-dialog"
      >
        <button type="button" onClick={onClose} data-dialog-close aria-label="Cerrar sílabo" className="lx-syllabus-close">
          <X aria-hidden="true" />
        </button>
        <header className="lx-syllabus-intro">
          <span className="lx-syllabus-category">{playbook.category}</span>
          <h2 id={titleId}>{playbook.title}</h2>
          <p id={descriptionId}>{silabo?.objetivo || playbook.description}</p>
          <span className="lx-syllabus-level">Nivel {levelName[playbook.tier]}</span>
        </header>
        <div className="lx-syllabus-body">
          <div className="lx-syllabus-facts">
            <div><span>Duración</span><strong>{playbook.duration || 'Por confirmar'}</strong></div>
            <div><span>Docente</span><strong>{playbook.instructor || 'Por confirmar'}</strong></div>
          </div>

          {playbook.tags.length > 0 && (
            <section className="lx-syllabus-section">
              <h3>Competencias y áreas clave</h3>
              <ul className="lx-syllabus-topics">
                {playbook.tags.map((tag) => (
                  <li key={tag}>{tag}</li>
                ))}
              </ul>
            </section>
          )}

          <section className="lx-syllabus-section">
            <h3>Contenido del programa</h3>
            {silabo?.enRevision && <p className="lx-syllabus-review">Temario propuesto, pendiente de revisión académica.</p>}
            {silabo?.modulos.length ? (
              <div className="lx-syllabus-modules">
                {silabo.modulos.map((modulo) => (
                  <section className="lx-syllabus-module" key={modulo.titulo}>
                    <div><h4>{modulo.titulo}</h4><span>{modulo.duracion}</span></div>
                    <p>{modulo.contenido}</p>
                  </section>
                ))}
              </div>
            ) : <p className="lx-syllabus-review">El temario de este programa todavía no está publicado.</p>}
          </section>
          {silabo?.actividad && <section className="lx-syllabus-section">
            <h3>Actividad de aprendizaje</h3><p className="lx-syllabus-description">{silabo.actividad}</p>
          </section>}

          <div className="lx-syllabus-actions">
            {isPurchasable ? (
              <>
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
              </>
            ) : null}
            <button type="button" className="lx-syllabus-secondary" onClick={() => { onAskAi(playbook); onClose(); }}>
              <Bot aria-hidden="true" /> Consultar sobre este programa
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

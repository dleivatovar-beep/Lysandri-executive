import React from 'react';
import { ArrowUp, ArrowUpRight, Mail } from 'lucide-react';
import companyLogo from '../../assets/mi-logo.png';
import './footer-editorial.css';

export const Footer: React.FC = () => (
  <footer className="lx-footer">
    <div className="lx-footer-main">
      <div className="lx-footer-intro">
        <div className="lx-footer-brand">
          <img src={companyLogo} alt="" />
          <span>LYSANDRI<small>EXECUTIVE</small></span>
        </div>
        <p>Formación ejecutiva de Yunix Ingenieros E.I.R.L. para avanzar en tecnología y liderazgo.</p>
      </div>
      <nav className="lx-footer-links" aria-label="Enlaces del pie de página">
        <span>Explorar</span>
        <a href="/#catalogo-section">Programas <ArrowUpRight aria-hidden="true" /></a>
        <a href="/#metodologia-section">Cómo aprendemos <ArrowUpRight aria-hidden="true" /></a>
        <a href="/#in-company-section">Para empresas <ArrowUpRight aria-hidden="true" /></a>
      </nav>
      <div className="lx-footer-contact">
        <span>Contacto</span>
        <a href="mailto:contacto@yunix.pe"><Mail aria-hidden="true" /> contacto@yunix.pe</a>
      </div>
    </div>
    <div className="lx-footer-bottom">
      <span>© {new Date().getFullYear()} Yunix Ingenieros E.I.R.L.</span>
      <button type="button" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}>
        Volver arriba <ArrowUp aria-hidden="true" />
      </button>
    </div>
  </footer>
);

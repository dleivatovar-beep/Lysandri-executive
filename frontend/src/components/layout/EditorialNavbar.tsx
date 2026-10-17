import React, { useEffect, useRef, useState } from 'react';
import { ArrowUpRight, Menu, Moon, ShoppingBag, Sun, X } from 'lucide-react';
import companyLogo from '../../assets/mi-logo.png';
import { useCart } from '../../context/CartContext';
import { getRoleHomePath } from '../../routes/RoleProtectedRoute';
import { UserProfile } from '../../types';
import './editorial-navbar.css';

interface EditorialNavbarProps {
  user: UserProfile | null;
  theme: 'light' | 'dark';
  onThemeToggle: () => void;
  onLogoutClick: () => void;
}

const sections = [
  { id: 'catalogo-section', label: 'Programas' },
  { id: 'metodologia-section', label: 'Cómo aprendemos' },
  { id: 'in-company-section', label: 'Para empresas' },
];

export const EditorialNavbar: React.FC<EditorialNavbarProps> = ({
  user,
  theme,
  onThemeToggle,
  onLogoutClick,
}) => {
  const { itemCount, openCart } = useCart();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [activeSection, setActiveSection] = useState('');
  const headerRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const targets = sections
      .map(({ id }) => document.getElementById(id))
      .filter((element): element is HTMLElement => Boolean(element));
    if (!targets.length) return;

    const observer = new IntersectionObserver((entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (visible) setActiveSection(visible.target.id);
    }, { rootMargin: '-20% 0px -60% 0px', threshold: [0, 0.15, 0.4] });

    targets.forEach((target) => observer.observe(target));
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMobileOpen(false);
        setAccountOpen(false);
      }
    };
    const closeOutside = (event: MouseEvent) => {
      if (headerRef.current && !headerRef.current.contains(event.target as Node)) {
        setMobileOpen(false);
        setAccountOpen(false);
      }
    };
    document.addEventListener('keydown', closeOnEscape);
    document.addEventListener('mousedown', closeOutside);
    return () => {
      document.removeEventListener('keydown', closeOnEscape);
      document.removeEventListener('mousedown', closeOutside);
    };
  }, []);

  const jumpTo = (event: React.MouseEvent<HTMLAnchorElement>, id: string) => {
    setMobileOpen(false);
    const destination = document.getElementById(id);
    if (destination) {
      event.preventDefault();
      destination.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <header ref={headerRef} className="lx-site-header lx-editorial-header">
      <div className="lx-editorial-header-line">
        <a className="lx-editorial-brand" href="/" aria-label="Lysandri Executive, ir al inicio">
          <img src={companyLogo} alt="" />
          <span className="lx-editorial-wordmark">LYSANDRI<span>EXECUTIVE</span></span>
        </a>

        <nav className="lx-editorial-desktop-nav" aria-label="Navegación principal">
          {sections.map((section) => (
            <a key={section.id} className={activeSection === section.id ? 'is-active' : undefined} aria-current={activeSection === section.id ? 'location' : undefined} href={`/#${section.id}`} onClick={(event) => jumpTo(event, section.id)}>
              {section.label}
            </a>
          ))}
        </nav>

        <div className="lx-editorial-actions">
          <button type="button" className="lx-editorial-icon-button lx-editorial-cart" onClick={openCart} aria-label={`Abrir carrito de compras${itemCount ? `, ${itemCount} programas` : ''}`}>
            <ShoppingBag aria-hidden="true" />
            {itemCount > 0 && <span className="lx-editorial-cart-count">{itemCount}</span>}
          </button>
          <button type="button" className="lx-editorial-icon-button lx-editorial-theme" onClick={onThemeToggle} aria-label={theme === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}>
            {theme === 'dark' ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
          </button>

          {user && (
            <div className="lx-editorial-account">
              <button type="button" className="lx-editorial-account-trigger" onClick={() => setAccountOpen((open) => !open)} aria-expanded={accountOpen} aria-haspopup="menu">
                <span>{user.name}</span><ArrowUpRight aria-hidden="true" />
              </button>
              {accountOpen && (
                <div className="lx-editorial-account-menu" role="menu">
                  <span>{user.name}</span>
                  <a role="menuitem" href={getRoleHomePath(user.role)} onClick={() => setAccountOpen(false)}>Ir a mi panel</a>
                  <button type="button" role="menuitem" onClick={() => { setAccountOpen(false); onLogoutClick(); }}>Cerrar sesión</button>
                </div>
              )}
            </div>
          )}

          <button type="button" className="lx-editorial-icon-button lx-editorial-menu-trigger" onClick={() => setMobileOpen((open) => !open)} aria-label={mobileOpen ? 'Cerrar menú' : 'Abrir menú'} aria-expanded={mobileOpen} aria-controls="lx-editorial-mobile-menu">
            {mobileOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
          </button>
        </div>
      </div>

      <nav id="lx-editorial-mobile-menu" className={`lx-editorial-mobile-nav${mobileOpen ? ' is-open' : ''}`} aria-label="Navegación móvil" aria-hidden={!mobileOpen}>
        {sections.map((section) => (
          <a key={section.id} href={`/#${section.id}`} onClick={(event) => jumpTo(event, section.id)} tabIndex={mobileOpen ? 0 : -1}>
            <span>{section.label}</span><ArrowUpRight aria-hidden="true" />
          </a>
        ))}
        <button type="button" className="lx-editorial-mobile-theme" tabIndex={mobileOpen ? 0 : -1} onClick={onThemeToggle}>
          {theme === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
          {theme === 'dark' ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
        </button>
        {user && (
          <a href={getRoleHomePath(user.role)} tabIndex={mobileOpen ? 0 : -1} onClick={() => setMobileOpen(false)}>Ir a mi panel <ArrowUpRight aria-hidden="true" /></a>
        )}
        {user && (
          <button type="button" tabIndex={mobileOpen ? 0 : -1} onClick={() => { setMobileOpen(false); onLogoutClick(); }}>Cerrar sesión</button>
        )}
      </nav>
    </header>
  );
};

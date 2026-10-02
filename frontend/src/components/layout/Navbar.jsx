import React, { useState } from 'react';
import {
  Moon,
  Sun,
  ShoppingCart,
  Sparkles,
  Phone,
  Mail,
  MessageCircle,
  Bot,
  GraduationCap,
} from 'lucide-react';
import companyLogo from '../../assets/mi-logo.png';
import { useCart } from '../../context/CartContext';

export const Navbar = ({
  theme,
  onThemeToggle,
  onOpenAiChat,
}) => {
  const [isContactPopoverOpen, setIsContactPopoverOpen] = useState(false);

  let cartCount = 0;
  let openCartFn = () => {};
  try {
    const cart = useCart();
    cartCount = cart.itemCount;
    openCartFn = cart.openCart;
  } catch {
    // fallback
  }

  const scrollToSection = (id) => {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    } else {
      window.location.href = `/#${id}`;
    }
  };

  const handleOpenChat = () => {
    if (onOpenAiChat) {
      onOpenAiChat();
    } else {
      window.dispatchEvent(new CustomEvent('open-executive-ai-chat'));
    }
  };

  return (
    <>
      <header className="sticky top-0 z-40 h-16 border-b border-cyan-500/10 bg-white/95 shadow-[0_1px_25px_rgba(6,182,212,0.06)] backdrop-blur-xl dark:border-slate-800/80 dark:bg-[#070a10]/95">
        <div className="flex h-full w-full items-center justify-between px-4 sm:px-6 lg:px-8">
          <div className="flex min-w-0 items-center gap-3">
            <a href="/" className="group flex items-center gap-3">
              <div className="relative flex h-10 w-10 shrink-0 items-center justify-center">
                <div className="absolute inset-0 rounded-xl bg-cyan-400/20 opacity-70 blur-lg group-hover:opacity-100 transition-opacity" />
                <img
                  src={companyLogo}
                  alt="Logo"
                  className="relative h-10 w-10 object-contain drop-shadow-[0_0_8px_rgba(34,211,238,0.35)]"
                />
              </div>

              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <span className="truncate text-base font-black tracking-wide text-slate-900 dark:text-white">
                    LYSANDRI
                  </span>
                  <span className="rounded-md border border-cyan-500/30 bg-cyan-500/10 px-2 py-0.5 font-mono text-[9px] font-bold tracking-wider text-cyan-600 dark:text-cyan-400">
                    EXECUTIVE
                  </span>
                </div>
                <span className="hidden font-mono text-[9px] tracking-tight text-slate-500 dark:text-slate-400 sm:block">
                  Yunix Ingenieros E.I.R.L.
                </span>
              </div>
            </a>
          </div>

          <nav className="flex items-center gap-2 sm:gap-4">
            <button
              type="button"
              onClick={() => scrollToSection('catalogo-section')}
              className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-bold text-slate-700 hover:text-cyan-600 hover:bg-slate-100 dark:text-slate-200 dark:hover:text-cyan-400 dark:hover:bg-slate-800/50 transition-colors"
            >
              <GraduationCap className="h-4 w-4 text-cyan-500" />
              <span>Programas</span>
            </button>

            <button
              type="button"
              onClick={handleOpenChat}
              className="flex items-center gap-1.5 rounded-lg border border-cyan-500/30 bg-cyan-500/10 px-3 py-1.5 text-xs font-bold text-cyan-600 hover:bg-cyan-500/20 dark:text-cyan-300 dark:hover:bg-cyan-950/60 transition-colors"
            >
              <Sparkles className="h-3.5 w-3.5 text-cyan-400 animate-pulse" />
              <span className="hidden sm:inline">Asistente IA</span>
              <span className="sm:hidden">IA</span>
            </button>

            <div className="relative">
              <button
                type="button"
                onClick={() => setIsContactPopoverOpen((prev) => !prev)}
                className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold text-slate-600 hover:text-indigo-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:text-indigo-400 dark:hover:bg-slate-800/50 transition-colors"
              >
                <Phone className="h-3.5 w-3.5 text-indigo-400" />
                <span>Contacto</span>
              </button>

              {isContactPopoverOpen && (
                <>
                  <div
                    className="fixed inset-0 z-40"
                    onClick={() => setIsContactPopoverOpen(false)}
                  />
                  <div className="absolute right-0 top-full mt-2 z-50 w-72 rounded-2xl border border-slate-700 bg-slate-900 p-4 text-slate-100 shadow-2xl backdrop-blur-xl">
                    <p className="text-xs font-bold text-white mb-2">
                      Atención y admisiones
                    </p>
                    <div className="space-y-2 text-xs">
                      <a
                        href="https://wa.me/51999888777?text=Hola,%20solicito%20información%20sobre%20los%20programas%20de%20Lysandri%20Executive"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-2.5 rounded-xl bg-emerald-950/50 border border-emerald-500/30 p-2 text-emerald-300 hover:bg-emerald-900/60 transition-colors"
                      >
                        <MessageCircle className="h-4 w-4 text-emerald-400 shrink-0" />
                        <div>
                          <div className="font-semibold">WhatsApp</div>
                          <div className="text-[10px] text-emerald-400/80">+51 999 888 777</div>
                        </div>
                      </a>

                      <a
                        href="mailto:contacto@yunixingenieros.com"
                        className="flex items-center gap-2.5 rounded-xl bg-slate-800/80 border border-slate-700 p-2 text-slate-300 hover:bg-slate-800 transition-colors"
                      >
                        <Mail className="h-4 w-4 text-cyan-400 shrink-0" />
                        <div>
                          <div className="font-semibold">Correo</div>
                          <div className="text-[10px] text-slate-400">contacto@yunixingenieros.com</div>
                        </div>
                      </a>
                    </div>
                  </div>
                </>
              )}
            </div>
          </nav>

          <div className="flex items-center gap-2 sm:gap-3">
            <button
              type="button"
              onClick={openCartFn}
              aria-label="Carrito de compras"
              className="relative flex items-center gap-2 rounded-xl border border-cyan-500/20 bg-slate-100/80 px-3 py-2 text-xs font-semibold text-slate-700 transition-all duration-200 hover:border-cyan-400 hover:bg-cyan-500/10 dark:border-slate-800 dark:bg-slate-900/80 dark:text-slate-200 dark:hover:border-cyan-500/40"
            >
              <ShoppingCart className="h-4 w-4 text-cyan-600 dark:text-cyan-400" />
              <span className="hidden font-mono text-[11px] font-bold sm:inline">
                Carrito
              </span>
              {cartCount > 0 && (
                <span className="flex h-5 min-w-[20px] items-center justify-center rounded-full bg-gradient-to-r from-cyan-500 to-indigo-600 px-1 text-[10px] font-black text-white shadow-md shadow-cyan-500/30">
                  {cartCount}
                </span>
              )}
            </button>

            <button
              type="button"
              onClick={onThemeToggle}
              aria-label={
                theme === 'dark' ? 'Modo claro' : 'Modo oscuro'
              }
              className="flex h-9 w-9 items-center justify-center rounded-xl border border-cyan-500/10 bg-slate-50 text-slate-500 transition-all duration-300 hover:border-cyan-500/30 hover:bg-cyan-500/5 hover:text-cyan-600 dark:border-slate-800 dark:bg-slate-900/70 dark:text-slate-400 dark:hover:border-cyan-500/30 dark:hover:text-cyan-400"
            >
              {theme === 'dark' ? (
                <Sun className="h-4 w-4" />
              ) : (
                <Moon className="h-4 w-4" />
              )}
            </button>
          </div>
        </div>
      </header>

      <button
        type="button"
        onClick={handleOpenChat}
        aria-label="Asistente"
        className="fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-cyan-500 to-indigo-600 text-white shadow-[0_10px_30px_rgba(6,182,212,0.4)] transition-all duration-300 hover:-translate-y-1 hover:scale-105 active:scale-95 group"
      >
        <Bot className="h-7 w-7 transition-transform group-hover:scale-110" />
        <span className="absolute -top-1 -right-1 flex h-3.5 w-3.5">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75" />
          <span className="relative inline-flex rounded-full h-3.5 w-3.5 bg-cyan-500" />
        </span>
      </button>
    </>
  );
};

export default Navbar;

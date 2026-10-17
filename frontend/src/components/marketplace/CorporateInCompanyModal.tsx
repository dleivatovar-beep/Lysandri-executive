import React, { useState } from 'react';
import {
  X,
  Send,
  CheckCircle2,
  LoaderCircle,
  ShieldAlert,
  MessageCircle,
  Users,
  ShieldCheck,
  CalendarCheck,
} from 'lucide-react';
import { informationRequestService } from '../../services/informationRequestService';

interface CorporateInCompanyModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialProgramId?: number;
  initialProgramTitle?: string;
}

const TAMANOS_EQUIPO = [
  '2 a 5 personas',
  '6 a 15 personas',
  'Más de 15 personas',
];

const ENFOQUES_POPULARES = [
  'Inteligencia Artificial aplicada',
  'Automatización de procesos',
  'Ciberseguridad y gobernanza',
  'Programa a la medida',
];

export const CorporateInCompanyModal: React.FC<CorporateInCompanyModalProps> = ({
  isOpen,
  onClose,
  initialProgramId,
  initialProgramTitle,
}) => {
  const [nombreCompleto, setNombreCompleto] = useState('');
  const [email, setEmail] = useState('');
  const [telefono, setTelefono] = useState('');
  const [empresa, setEmpresa] = useState('');
  const [cargo, setCargo] = useState('');
  const [tamanoEquipo, setTamanoEquipo] = useState<string>('2 a 5 personas');
  const [enfoqueSeleccionado, setEnfoqueSeleccionado] = useState<string>(
    initialProgramTitle || 'Inteligencia Artificial aplicada'
  );
  const [mensaje, setMensaje] = useState('');

  const [isLoading, setIsLoading] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setErrorMessage('');

    const detalleCompleto = [
      `Tamaño de equipo estimado: ${tamanoEquipo}`,
      `Enfoque: ${enfoqueSeleccionado}`,
      initialProgramTitle ? `Programa de referencia: ${initialProgramTitle}` : '',
      mensaje.trim() ? `Detalle adicional: ${mensaje.trim()}` : '',
    ]
      .filter(Boolean)
      .join(' | ');

    try {
      await informationRequestService.create({
        nombreCompleto: nombreCompleto.trim(),
        email: email.trim(),
        telefono: telefono.trim(),
        empresa: empresa.trim(),
        cargo: cargo.trim(),
        mensaje: detalleCompleto,
        idPrograma: initialProgramId,
      });

      setIsSuccess(true);
    } catch (error: any) {
      setErrorMessage(
        error.response?.data?.message ||
          error.message ||
          'No se pudo registrar la solicitud en este momento. Por favor verifica tus datos o contáctanos por WhatsApp.'
      );
    } finally {
      setIsLoading(false);
    }
  };

  const handleClose = () => {
    setIsSuccess(false);
    setErrorMessage('');
    onClose();
  };

  const whatsAppDirectText = encodeURIComponent(
    `Hola, quisiera coordinar una propuesta de capacitación corporativa para mi equipo en Lysandri Executive.` +
      (initialProgramTitle ? ` Programa de interés: ${initialProgramTitle}.` : '')
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-950/85 backdrop-blur-md transition-opacity"
        onClick={handleClose}
      />

      <div className="relative z-10 w-full max-w-2xl max-h-[90vh] overflow-y-auto rounded-3xl border border-slate-800 bg-slate-900 text-slate-100 shadow-2xl scrollbar-thin scrollbar-thumb-slate-700">
        {/* Header */}
        <div className="sticky top-0 z-20 border-b border-slate-800 bg-slate-950/95 backdrop-blur-md px-6 py-5">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-1">
              <span className="inline-flex items-center gap-1.5 rounded-full border border-indigo-400/30 bg-indigo-950/60 px-2.5 py-0.5 text-[11px] font-medium text-indigo-300">
                <Users className="h-3 w-3" />
                Formación para Equipos y Empresas
              </span>
              <h2 className="text-lg font-bold text-white sm:text-xl">
                Capacitación a la medida de tu organización
              </h2>
              <p className="text-xs text-slate-400">
                Diseñamos cohortes privadas con casos prácticos enfocados en los objetivos de tu empresa.
              </p>
            </div>
            <button
              type="button"
              onClick={handleClose}
              className="rounded-xl p-2 text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
              aria-label="Cerrar modal"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="p-6">
          {isSuccess ? (
            <div className="py-8 text-center space-y-5">
              <div className="rounded-2xl bg-emerald-500/10 border border-emerald-500/30 p-4 w-fit mx-auto text-emerald-400">
                <CheckCircle2 className="h-10 w-10" />
              </div>
              <div className="space-y-2 max-w-md mx-auto">
                <h3 className="text-lg font-bold text-white">
                  Información recibida
                </h3>
                <p className="text-xs text-slate-300 leading-relaxed">
                  Gracias por escribirnos, {nombreCompleto || 'estimado(a)'}. Un integrante de nuestro equipo de admisiones revisará la solicitud de tu empresa y te responderá hoy mismo para coordinar una breve reunión de alineación.
                </p>
              </div>

              <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-2">
                <a
                  href={`https://wa.me/51941238905?text=${whatsAppDirectText}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-center justify-center gap-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 px-5 py-2.5 text-xs font-semibold text-white transition-colors w-full sm:w-auto"
                >
                  <MessageCircle className="h-4 w-4" />
                  <span>Hablar directo por WhatsApp</span>
                </a>
                <button
                  type="button"
                  onClick={handleClose}
                  className="rounded-xl border border-slate-700 bg-slate-800 hover:bg-slate-700 px-5 py-2.5 text-xs font-medium text-slate-200 transition-colors w-full sm:w-auto"
                >
                  Cerrar y volver al catálogo
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-5">
              {errorMessage && (
                <div className="flex items-center gap-2.5 rounded-xl border border-rose-500/30 bg-rose-950/50 p-3 text-xs text-rose-300">
                  <ShieldAlert className="h-4 w-4 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {/* Acceso rápido a WhatsApp para ejecutivos sin tiempo */}
              <div className="rounded-2xl border border-emerald-500/25 bg-emerald-950/30 p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2 text-xs font-semibold text-emerald-300">
                    <MessageCircle className="h-4 w-4" />
                    <span>¿Prefieres conversar directamente?</span>
                  </div>
                  <p className="text-[11px] text-slate-300">
                    Puedes escribirnos a nuestra línea de atención para coordinar en minutos por chat o llamada corta.
                  </p>
                </div>
                <a
                  href={`https://wa.me/51941238905?text=${whatsAppDirectText}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="shrink-0 inline-flex items-center gap-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 px-3.5 py-2 text-xs font-semibold text-white transition-colors"
                >
                  <MessageCircle className="h-3.5 w-3.5" />
                  <span>Escribir por WhatsApp</span>
                </a>
              </div>

              {/* Selector interactivo: Tamaño de equipo */}
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-300">
                  ¿Cuántas personas conformarían el grupo a capacitar?
                </label>
                <div className="grid grid-cols-3 gap-2">
                  {TAMANOS_EQUIPO.map((size) => {
                    const isSelected = tamanoEquipo === size;
                    return (
                      <button
                        key={size}
                        type="button"
                        onClick={() => setTamanoEquipo(size)}
                        className={`rounded-xl border px-3 py-2 text-xs font-medium transition-all text-center ${
                          isSelected
                            ? 'border-indigo-500 bg-indigo-950/70 text-indigo-200 shadow-sm'
                            : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                        }`}
                      >
                        {size}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Selector interactivo: Área o enfoque */}
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-300">
                  Tema o área de interés principal
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {ENFOQUES_POPULARES.map((enfoque) => {
                    const isSelected = enfoqueSeleccionado === enfoque;
                    return (
                      <button
                        key={enfoque}
                        type="button"
                        onClick={() => setEnfoqueSeleccionado(enfoque)}
                        className={`rounded-xl border px-3 py-2 text-xs font-medium transition-all text-left truncate ${
                          isSelected
                            ? 'border-cyan-500 bg-cyan-950/70 text-cyan-200 shadow-sm'
                            : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:border-slate-700 hover:text-slate-200'
                        }`}
                      >
                        {enfoque}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Datos de contacto */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                <div className="space-y-1">
                  <label className="text-xs font-medium text-slate-300">
                    Nombre y apellido <span className="text-indigo-400">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    value={nombreCompleto}
                    onChange={(e) => setNombreCompleto(e.target.value)}
                    placeholder="Tu nombre completo"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 text-xs text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none transition-colors"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-medium text-slate-300">
                    Correo corporativo <span className="text-indigo-400">*</span>
                  </label>
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="nombre@tuempresa.com"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 text-xs text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none transition-colors"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="space-y-1">
                  <label className="text-xs font-medium text-slate-300">
                    Empresa u organización <span className="text-indigo-400">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    value={empresa}
                    onChange={(e) => setEmpresa(e.target.value)}
                    placeholder="Nombre de la empresa"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 text-xs text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none transition-colors"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-medium text-slate-300">
                    Cargo o área
                  </label>
                  <input
                    type="text"
                    value={cargo}
                    onChange={(e) => setCargo(e.target.value)}
                    placeholder="Ej. Gerencia de TI o RRHH"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 text-xs text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none transition-colors"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-medium text-slate-300">
                    Teléfono / WhatsApp
                  </label>
                  <input
                    type="tel"
                    value={telefono}
                    onChange={(e) => setTelefono(e.target.value)}
                    placeholder="Para coordinar llamada"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2.5 text-xs text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none transition-colors"
                  />
                </div>
              </div>

              {/* Mensaje opcional */}
              <div className="space-y-1">
                <label className="text-xs font-medium text-slate-300">
                  ¿Qué desafío o proyecto específico busca resolver tu equipo? <span className="text-slate-500">(Opcional)</span>
                </label>
                <textarea
                  rows={2}
                  value={mensaje}
                  onChange={(e) => setMensaje(e.target.value)}
                  placeholder="Por ejemplo: queremos que el equipo automatice reportes semanales, o requerimos fechas específicas..."
                  className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3.5 py-2 text-xs text-white placeholder-slate-500 focus:border-indigo-500 focus:outline-none transition-colors resize-none"
                />
              </div>

              {/* Puntos de confianza */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 rounded-2xl border border-slate-800 bg-slate-950/60 p-3 text-[11px] text-slate-300">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="h-4 w-4 text-emerald-400 shrink-0" />
                  <span>Facturación con RUC deducible</span>
                </div>
                <div className="flex items-center gap-2">
                  <CalendarCheck className="h-4 w-4 text-cyan-400 shrink-0" />
                  <span>Horarios y fechas a convenir</span>
                </div>
                <div className="flex items-center gap-2">
                  <Users className="h-4 w-4 text-indigo-400 shrink-0" />
                  <span>Campus Moodle habilitado</span>
                </div>
              </div>

              {/* Acciones */}
              <div className="pt-2 flex flex-col-reverse sm:flex-row items-center justify-end gap-2.5">
                <button
                  type="button"
                  onClick={handleClose}
                  className="w-full sm:w-auto px-4 py-2.5 text-xs font-medium text-slate-400 hover:text-white transition-colors"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isLoading}
                  className="w-full sm:w-auto flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-indigo-500 to-cyan-500 px-6 py-2.5 text-xs font-bold text-white shadow-lg shadow-indigo-500/20 hover:opacity-95 disabled:opacity-50 transition-all"
                >
                  {isLoading ? (
                    <>
                      <LoaderCircle className="h-4 w-4 animate-spin" />
                      <span>Enviando información...</span>
                    </>
                  ) : (
                    <>
                      <Send className="h-3.5 w-3.5" />
                      <span>Solicitar propuesta para mi equipo</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};

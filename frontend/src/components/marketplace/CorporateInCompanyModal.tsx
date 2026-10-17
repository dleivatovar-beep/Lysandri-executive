import React, { useId, useState } from 'react';
import { X, Send, CheckCircle2, LoaderCircle, ShieldAlert } from 'lucide-react';
import { informationRequestService } from '../../services/informationRequestService';
import { useDialogAccessibility } from '../../hooks/useDialogAccessibility';
import './editorial-overlays.css';

interface CorporateInCompanyModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialProgramId?: number;
  initialProgramTitle?: string;
}

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
  const [mensaje, setMensaje] = useState(
    initialProgramTitle
      ? `Solicito propuesta corporativa para capacitar a nuestro equipo en: ${initialProgramTitle}.`
      : ''
  );

  const [isLoading, setIsLoading] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const formId = useId();
  const dialogRef = useDialogAccessibility<HTMLDivElement>(isOpen, () => {
    setIsSuccess(false);
    setErrorMessage('');
    onClose();
  });

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setErrorMessage('');

    try {
      await informationRequestService.create({
        nombreCompleto,
        email,
        telefono,
        empresa,
        cargo,
        mensaje,
        idPrograma: initialProgramId,
      });

      setIsSuccess(true);
    } catch (error: any) {
      setErrorMessage(
        error.response?.data?.message ||
          error.message ||
          'Fallo al registrar la solicitud. Por favor verifique sus datos.'
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

  return (
    <div className="lx-overlay lx-corporate-overlay fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="lx-overlay-backdrop fixed inset-0"
        onClick={handleClose}
        aria-hidden="true"
      />

      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${formId}-title`}
        tabIndex={-1}
        className="lx-corporate-dialog relative z-10 max-h-[calc(100dvh-4rem)] w-full max-w-lg overflow-y-auto"
      >
        {/* Header */}
        <div className="lx-overlay-header p-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div>
                <h2 id={`${formId}-title`}>Formación para equipos</h2>
                <p>Consulta una propuesta para tu organización</p>
              </div>
            </div>
            <button
              type="button"
              onClick={handleClose}
              data-dialog-close
              aria-label="Cerrar solicitud corporativa"
              className="lx-overlay-close flex h-11 w-11 items-center justify-center"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="p-6">
          {isSuccess ? (
            <div className="py-8 text-center space-y-4">
              <div className="rounded-full bg-emerald-500/10 border border-emerald-500/30 p-4 w-fit mx-auto text-emerald-400">
                <CheckCircle2 className="h-10 w-10" />
              </div>
              <h3 className="text-lg font-bold">Solicitud recibida</h3>
              <p className="text-xs text-slate-300 max-w-sm mx-auto">
                Recibimos tus datos. El equipo podrá revisar tu consulta y contactarte para conversar sobre una propuesta.
              </p>
              <button
                type="button"
                onClick={handleClose}
                className="lx-overlay-primary mt-4 px-6 py-2.5"
              >
                Aceptar y Volver
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {errorMessage && (
                <div role="alert" className="flex items-center gap-2 rounded-xl border border-rose-500/30 bg-rose-950/50 p-3 text-xs text-rose-300">
                  <ShieldAlert className="h-4 w-4 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              <p className="text-xs text-slate-300 leading-relaxed">
                Cuéntanos qué programa o tema le interesa a tu equipo y cómo podemos contactarte.
              </p>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1">
                  <label htmlFor={`${formId}-name`} className="text-xs font-semibold text-slate-300">Nombre Completo *</label>
                  <input
                    id={`${formId}-name`}
                    type="text"
                    required
                    autoComplete="name"
                    value={nombreCompleto}
                    onChange={(e) => setNombreCompleto(e.target.value)}
                    placeholder="Ej. Ing. Carlos Mendoza"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label htmlFor={`${formId}-email`} className="text-xs font-semibold text-slate-300">Correo Corporativo *</label>
                  <input
                    id={`${formId}-email`}
                    type="email"
                    required
                    autoComplete="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="carlos@empresa.com"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <div className="space-y-1">
                  <label htmlFor={`${formId}-phone`} className="text-xs font-semibold text-slate-300">Teléfono / WhatsApp</label>
                  <input
                    id={`${formId}-phone`}
                    type="tel"
                    autoComplete="tel"
                    value={telefono}
                    onChange={(e) => setTelefono(e.target.value)}
                    placeholder="+51 999 888 777"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label htmlFor={`${formId}-company`} className="text-xs font-semibold text-slate-300">Empresa *</label>
                  <input
                    id={`${formId}-company`}
                    type="text"
                    required
                    autoComplete="organization"
                    value={empresa}
                    onChange={(e) => setEmpresa(e.target.value)}
                    placeholder="Ej. Banco / Telco"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label htmlFor={`${formId}-role`} className="text-xs font-semibold text-slate-300">Cargo Directivo</label>
                  <input
                    id={`${formId}-role`}
                    type="text"
                    autoComplete="organization-title"
                    value={cargo}
                    onChange={(e) => setCargo(e.target.value)}
                    placeholder="CTO / Lead Arch."
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label htmlFor={`${formId}-message`} className="text-xs font-semibold text-slate-300">Detalle de Requerimiento / Equipo</label>
                <textarea
                  id={`${formId}-message`}
                  rows={3}
                  value={mensaje}
                  onChange={(e) => setMensaje(e.target.value)}
                  placeholder="Número estimado de participantes, objetivos estratégicos o fechas tentativas..."
                  className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                />
              </div>

              <div className="pt-2 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={handleClose}
                  className="min-h-11 px-4 py-2 text-xs text-slate-400 hover:text-white"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isLoading}
                  className="lx-overlay-primary flex min-h-11 items-center gap-2 px-5 py-2.5 disabled:opacity-50"
                >
                  {isLoading ? (
                    <>
                      <LoaderCircle className="h-4 w-4 animate-spin" />
                      <span>Enviando...</span>
                    </>
                  ) : (
                    <>
                      <Send className="h-3.5 w-3.5" />
                      <span>Enviar Solicitud In-Company</span>
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

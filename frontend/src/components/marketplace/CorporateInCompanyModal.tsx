import React, { useState } from 'react';
import { X, Building2, Send, CheckCircle2, LoaderCircle, ShieldAlert } from 'lucide-react';
import { informationRequestService } from '../../services/informationRequestService';

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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-slate-950/80 backdrop-blur-md transition-opacity"
        onClick={handleClose}
      />

      <div className="relative z-10 w-full max-w-lg overflow-hidden rounded-2xl border border-cyan-500/30 bg-slate-900 text-slate-100 shadow-2xl">
        {/* Header */}
        <div className="border-b border-slate-800 bg-slate-950/80 p-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="rounded-xl bg-cyan-500/10 border border-cyan-500/30 p-2.5 text-cyan-400">
                <Building2 className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-base font-bold text-white">Capacitación In-Company & B2B</h2>
                <p className="text-xs text-slate-400">Yunix Ingenieros E.I.R.L. · División Corporativa</p>
              </div>
            </div>
            <button
              type="button"
              onClick={handleClose}
              className="rounded-lg p-2 text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
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
              <h3 className="text-lg font-bold text-white">¡Solicitud Recibida con Éxito!</h3>
              <p className="text-xs text-slate-300 max-w-sm mx-auto">
                Un Director de Cuentas Corporativas de Yunix Ingenieros se comunicará contigo en menos
                de 24 horas hábiles para estructurar la propuesta y cotización corporativa.
              </p>
              <button
                type="button"
                onClick={handleClose}
                className="mt-4 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-6 py-2.5 text-xs font-bold text-white shadow-lg shadow-cyan-500/25 hover:opacity-95"
              >
                Aceptar y Volver
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {errorMessage && (
                <div className="flex items-center gap-2 rounded-xl border border-rose-500/30 bg-rose-950/50 p-3 text-xs text-rose-300">
                  <ShieldAlert className="h-4 w-4 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              <p className="text-xs text-slate-300 leading-relaxed">
                Diseñamos programas ejecutivos a medida para capacitar a líderes, gerentes y equipos en Inteligencia Artificial aplicada, automatización No-Code, ciberseguridad y optimización de costos.
              </p>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className="text-[11px] font-semibold text-slate-300">Nombre Completo *</label>
                  <input
                    type="text"
                    required
                    value={nombreCompleto}
                    onChange={(e) => setNombreCompleto(e.target.value)}
                    placeholder="Ej. Carlos Mendoza"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[11px] font-semibold text-slate-300">Correo Corporativo *</label>
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="carlos@empresa.com"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div className="space-y-1">
                  <label className="text-[11px] font-semibold text-slate-300">Celular / WhatsApp</label>
                  <input
                    type="tel"
                    value={telefono}
                    onChange={(e) => setTelefono(e.target.value)}
                    placeholder="+51 999 888 777"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[11px] font-semibold text-slate-300">Empresa *</label>
                  <input
                    type="text"
                    required
                    value={empresa}
                    onChange={(e) => setEmpresa(e.target.value)}
                    placeholder="Ej. Banco / Telco"
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
                <div className="space-y-1">
                  <label className="text-[11px] font-semibold text-slate-300">Cargo Directivo</label>
                  <input
                    type="text"
                    value={cargo}
                    onChange={(e) => setCargo(e.target.value)}
                    placeholder="CTO / Lead Arch."
                    className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-[11px] font-semibold text-slate-300">Detalle de Requerimiento / Equipo</label>
                <textarea
                  rows={3}
                  value={mensaje}
                  onChange={(e) => setMensaje(e.target.value)}
                  placeholder="Número estimado de participantes, objetivos estratégicos o fechas tentativas..."
                  className="w-full rounded-xl border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-cyan-500 focus:outline-none"
                />
              </div>

              <div className="pt-2 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={handleClose}
                  className="px-4 py-2 text-xs text-slate-400 hover:text-white"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isLoading}
                  className="flex items-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-5 py-2.5 text-xs font-bold text-white shadow-lg shadow-cyan-500/20 hover:opacity-95 disabled:opacity-50"
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

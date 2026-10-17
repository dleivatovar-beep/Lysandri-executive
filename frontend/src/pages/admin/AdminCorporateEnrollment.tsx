import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Building2,
  Plus,
  Trash2,
  Upload,
  CheckCircle2,
  AlertCircle,
  Copy,
  Check,
  RefreshCw,
  LoaderCircle,
  ShieldCheck,
} from 'lucide-react';
import { academicService } from '../../services/academicService';
import { informationRequestService } from '../../services/informationRequestService';
import {
  storeService,
  ColaboradorNominaItem,
  MatriculaNominaResponse,
} from '../../services/storeService';
import { ProgramaResponse, SolicitudInformacionResponse } from '../../types';
import { getApiErrorMessage } from '../../services/authService';

export const AdminCorporateEnrollment: React.FC = () => {
  const [programs, setPrograms] = useState<ProgramaResponse[]>([]);
  const [requests, setRequests] = useState<SolicitudInformacionResponse[]>([]);
  const [isLoadingInitial, setIsLoadingInitial] = useState(true);

  // Formulario principal
  const [selectedProgramId, setSelectedProgramId] = useState<number | ''>('');
  const [selectedRequestId, setSelectedRequestId] = useState<number | ''>('');
  const [empresaNombre, setEmpresaNombre] = useState('');
  const [enviarCredencialesEmail, setEnviarCredencialesEmail] = useState(true);
  const [notasAdmin, setNotasAdmin] = useState('');

  // Modo de ingreso: 'table' | 'paste'
  const [inputMode, setInputMode] = useState<'table' | 'paste'>('table');
  const [pasteRawText, setPasteRawText] = useState('');

  // Lista de colaboradores
  const [colaboradores, setColaboradores] = useState<ColaboradorNominaItem[]>([
    { nombres: '', apellidos: '', email: '', cargo: '', telefono: '' },
  ]);

  // Ejecución
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [resultadoLote, setResultadoLote] = useState<MatriculaNominaResponse | null>(null);

  // Copiado
  const [isCopiedReport, setIsCopiedReport] = useState(false);

  const loadInitialData = useCallback(async () => {
    setIsLoadingInitial(true);
    setErrorMessage('');
    try {
      const [progs, reqs] = await Promise.all([
        academicService.getPrograms(),
        informationRequestService.getAll().catch(() => []),
      ]);
      setPrograms(progs);
      if (progs.length > 0 && !selectedProgramId) {
        setSelectedProgramId(progs[0].idPrograma);
      }
      setRequests(reqs);
    } catch (err: any) {
      setErrorMessage(getApiErrorMessage(err));
    } finally {
      setIsLoadingInitial(false);
    }
  }, [selectedProgramId]);

  useEffect(() => {
    void loadInitialData();
  }, [loadInitialData]);

  // Sincronizar selección de solicitud In-Company con el nombre de empresa
  const handleSelectRequest = (idReq: string) => {
    if (!idReq) {
      setSelectedRequestId('');
      return;
    }
    const id = Number(idReq);
    setSelectedRequestId(id);
    const req = requests.find((r) => r.idSolicitud === id);
    if (req) {
      if (req.empresa) setEmpresaNombre(req.empresa);
      if (req.idPrograma) setSelectedProgramId(req.idPrograma);
      if (req.nombres || req.nombreCompleto) {
        // Pre-llenar primer colaborador si la tabla está vacía
        const nameParts = (req.nombreCompleto || '').split(' ');
        const nom = req.nombres || nameParts[0] || '';
        const ape = req.apellidos || nameParts.slice(1).join(' ') || '';
        setColaboradores((prev) => {
          if (prev.length === 1 && !prev[0].email) {
            return [
              {
                nombres: nom,
                apellidos: ape,
                email: req.email || '',
                cargo: req.cargo || 'Responsable de Equipo',
                telefono: req.telefono || '',
              },
            ];
          }
          return prev;
        });
      }
    }
  };

  const handleAddRow = () => {
    setColaboradores((prev) => [
      ...prev,
      { nombres: '', apellidos: '', email: '', cargo: '', telefono: '' },
    ]);
  };

  const handleRemoveRow = (index: number) => {
    setColaboradores((prev) => {
      if (prev.length <= 1) {
        return [{ nombres: '', apellidos: '', email: '', cargo: '', telefono: '' }];
      }
      return prev.filter((_, i) => i !== index);
    });
  };

  const handleRowChange = (index: number, field: keyof ColaboradorNominaItem, value: string) => {
    setColaboradores((prev) => {
      const copy = [...prev];
      copy[index] = { ...copy[index], [field]: value };
      return copy;
    });
  };

  // Procesar pegado de texto en lote (CSV o Tab-delimited de Excel)
  const handleParsePaste = () => {
    if (!pasteRawText.trim()) return;

    const lines = pasteRawText.split(/\r?\n/).filter((l) => l.trim().length > 0);
    const parsed: ColaboradorNominaItem[] = [];

    for (const line of lines) {
      // Soportar separación por tabulación (Excel) o por comas
      const separator = line.includes('\t') ? '\t' : ',';
      const parts = line.split(separator).map((s) => s.trim());

      if (parts.length >= 3) {
        parsed.push({
          nombres: parts[0] || '',
          apellidos: parts[1] || '',
          email: parts[2] || '',
          cargo: parts[3] || '',
          telefono: parts[4] || '',
        });
      }
    }

    if (parsed.length > 0) {
      setColaboradores(parsed);
      setInputMode('table');
      setPasteRawText('');
    } else {
      setErrorMessage(
        'El formato ingresado no pudo ser procesado. Ingresa al menos: Nombres, Apellidos, Correo por cada línea.'
      );
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage('');
    setResultadoLote(null);

    if (!selectedProgramId) {
      setErrorMessage('Selecciona un programa ejecutivo de destino.');
      return;
    }

    if (!empresaNombre.trim()) {
      setErrorMessage('Ingresa la razón social o nombre de la empresa.');
      return;
    }

    // Filtrar filas válidas (con email)
    const validColaboradores = colaboradores.filter((c) => c.email && c.email.includes('@'));
    if (validColaboradores.length === 0) {
      setErrorMessage('Ingresa al menos un colaborador con correo electrónico válido.');
      return;
    }

    setIsSubmitting(true);

    try {
      const resp = await storeService.matricularNominaCorporativa({
        idPrograma: Number(selectedProgramId),
        idSolicitud: selectedRequestId ? Number(selectedRequestId) : undefined,
        empresaNombre: empresaNombre.trim(),
        colaboradores: validColaboradores,
        enviarCredencialesEmail,
        notasAdmin: notasAdmin.trim() || undefined,
      });

      setResultadoLote(resp);
    } catch (err: any) {
      setErrorMessage(getApiErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCopyReport = () => {
    if (!resultadoLote) return;

    const rows = resultadoLote.resultados.map(
      (r, i) =>
        `${i + 1}. ${r.nombres} ${r.apellidos} | ${r.email} | Usuario Moodle: ${r.moodleUsername || r.email} | Clave: ${r.contrasenaGenerada || 'Enviada al correo'} | Estado: ${r.matriculadoEnCurso ? 'Matriculado' : 'Error'}`
    );

    const text = [
      `REPORTE DE ACCESOS CORPORATIVOS - LYSANDRI EXECUTIVE`,
      `Empresa: ${resultadoLote.empresaNombre}`,
      `Programa: ${resultadoLote.programaTitulo}`,
      `Total Participantes: ${resultadoLote.totalProcesados} (Exitosos: ${resultadoLote.totalExitosos})`,
      `Campus Virtual: https://campus.yunixingenieros.com`,
      `-------------------------------------------------------`,
      ...rows,
      `-------------------------------------------------------`,
      `Fecha de emisión: ${new Date().toLocaleDateString('es-PE')}`,
    ].join('\n');

    navigator.clipboard.writeText(text);
    setIsCopiedReport(true);
    setTimeout(() => setIsCopiedReport(false), 3000);
  };

  const inCompanyRequests = useMemo(() => {
    return requests.filter(
      (r) =>
        r.mensaje?.toLowerCase().includes('in-company') ||
        r.empresa ||
        r.cargo ||
        r.estado === 'PENDIENTE'
    );
  }, [requests]);

  const selectedProgram = useMemo(() => {
    return programs.find((p) => p.idPrograma === Number(selectedProgramId));
  }, [programs, selectedProgramId]);

  return (
    <section className="space-y-6">
      {/* Cabecera Principal */}
      <div className="relative overflow-hidden rounded-3xl border border-indigo-500/15 bg-white p-7 dark:border-slate-800 dark:bg-[#0c111a] md:p-9">
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-indigo-500/10 blur-3xl" />

        <div className="relative flex flex-col justify-between gap-4 md:flex-row md:items-end">
          <div>
            <span className="inline-flex items-center gap-1.5 rounded-full border border-indigo-500/20 bg-indigo-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-[0.18em] text-indigo-600 dark:text-indigo-400">
              <Building2 className="h-3 w-3" />
              Formación Corporativa & In-Company
            </span>

            <h1 className="mt-4 text-3xl font-black text-slate-950 dark:text-white">
              Carga y Matrícula Masiva para Empresas
            </h1>

            <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">
              Aprovisiona en lote a nóminas completas de colaboradores para capacitaciones empresariales.
              El sistema crea las cuentas individuales, matricula en el curso Moodle asignado, despacha
              las credenciales de acceso por correo y actualiza el lead corporativo.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void loadInitialData()}
              disabled={isLoadingInitial}
              className="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-xs font-bold text-slate-700 shadow-sm transition hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200"
            >
              <RefreshCw className={`h-4 w-4 ${isLoadingInitial ? 'animate-spin' : ''}`} />
              <span>Actualizar Catálogo</span>
            </button>
          </div>
        </div>
      </div>

      {/* Formulario de Matrícula Corporativa */}
      <form onSubmit={handleSubmit} className="space-y-6">
        <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-[#0c111a] md:p-8">
          <h2 className="text-base font-black text-slate-950 dark:text-white">
            1. Parámetros del Programa & Entidad Corporativa
          </h2>
          <p className="mt-1 text-xs text-slate-500">
            Define la empresa cliente y el programa ejecutivo de destino en el Campus Moodle.
          </p>

          <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {/* Selección de Programa */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Programa Ejecutivo
              </label>
              <select
                value={selectedProgramId}
                onChange={(e) => setSelectedProgramId(e.target.value ? Number(e.target.value) : '')}
                className="mt-1.5 h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                required
              >
                <option value="">Selecciona un programa...</option>
                {programs.map((p) => (
                  <option key={p.idPrograma} value={p.idPrograma}>
                    {p.tituloPrograma || p.titulo} {p.moodleCourseId ? `(Moodle ID: ${p.moodleCourseId})` : ''}
                  </option>
                ))}
              </select>
              {selectedProgram?.moodleCourseId ? (
                <p className="mt-1 font-mono text-[11px] text-cyan-600 dark:text-cyan-400">
                  ID Curso Moodle: {selectedProgram.moodleCourseId}
                </p>
              ) : null}
            </div>

            {/* Empresa / Razón Social */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Razón Social / Nombre de la Empresa
              </label>
              <input
                type="text"
                value={empresaNombre}
                onChange={(e) => setEmpresaNombre(e.target.value)}
                placeholder="Ej. Banco de Crédito BCP, Rimac Seguros, Alicorp"
                className="mt-1.5 h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                required
              />
            </div>

            {/* Vincular Solicitud In-Company */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Vincular Solicitud Comercial (Opcional)
              </label>
              <select
                value={selectedRequestId}
                onChange={(e) => handleSelectRequest(e.target.value)}
                className="mt-1.5 h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
              >
                <option value="">Ninguna (Matrícula directa)</option>
                {inCompanyRequests.map((req) => (
                  <option key={req.idSolicitud} value={req.idSolicitud}>
                    #{req.idSolicitud} - {req.empresa || req.nombreCompleto} ({req.email})
                  </option>
                ))}
              </select>
              <p className="mt-1 text-[11px] text-slate-400">
                Al finalizar con éxito, la solicitud se marcará como CERRADA.
              </p>
            </div>
          </div>
        </div>

        {/* Sección de Nómina */}
        <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-[#0c111a] md:p-8">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-base font-black text-slate-950 dark:text-white">
                2. Nómina de Colaboradores a Matricular
              </h2>
              <p className="mt-1 text-xs text-slate-500">
                Agrega manualmente o pega desde una hoja de cálculo Excel / archivo CSV.
              </p>
            </div>

            {/* Toggle de Modo */}
            <div className="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-slate-100 p-1 dark:border-slate-800 dark:bg-slate-900">
              <button
                type="button"
                onClick={() => setInputMode('table')}
                className={`rounded-lg px-3 py-1.5 text-xs font-bold transition ${
                  inputMode === 'table'
                    ? 'bg-white text-slate-950 shadow-sm dark:bg-slate-800 dark:text-white'
                    : 'text-slate-600 dark:text-slate-400'
                }`}
              >
                Edición en Tabla
              </button>
              <button
                type="button"
                onClick={() => setInputMode('paste')}
                className={`rounded-lg px-3 py-1.5 text-xs font-bold transition ${
                  inputMode === 'paste'
                    ? 'bg-white text-slate-950 shadow-sm dark:bg-slate-800 dark:text-white'
                    : 'text-slate-600 dark:text-slate-400'
                }`}
              >
                Pegar Texto / CSV
              </button>
            </div>
          </div>

          {inputMode === 'paste' ? (
            <div className="mt-6 space-y-4">
              <div className="rounded-2xl border border-indigo-500/20 bg-indigo-500/5 p-4 text-xs text-indigo-800 dark:text-indigo-300">
                <p className="font-bold">Formato aceptado (una línea por colaborador):</p>
                <p className="mt-1 font-mono text-[11px] text-slate-600 dark:text-slate-400">
                  Nombres, Apellidos, Correo, Cargo (opcional), Celular (opcional)
                </p>
                <p className="mt-1 text-[11px] text-slate-500">
                  También puedes copiar directamente las columnas desde Excel y pegarlas aquí.
                </p>
              </div>

              <textarea
                rows={6}
                value={pasteRawText}
                onChange={(e) => setPasteRawText(e.target.value)}
                placeholder={`Carlos, Mendoza, cmendoza@empresa.com, Jefe TI, 999111222\nAna, García, agarcia@empresa.com, Analista de Datos, 999333444\nRoberto, Silva, rsilva@empresa.com, Subgerente de Operaciones, 999555666`}
                className="w-full rounded-2xl border border-slate-200 bg-slate-50 p-4 font-mono text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
              />

              <button
                type="button"
                onClick={handleParsePaste}
                className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-xs font-bold text-white shadow-md transition hover:bg-indigo-500"
              >
                <Upload className="h-4 w-4" />
                <span>Cargar Nómina a la Tabla</span>
              </button>
            </div>
          ) : (
            <div className="mt-6 space-y-3">
              <div className="overflow-x-auto">
                <table className="w-full min-w-[700px] text-left text-xs">
                  <thead className="border-b border-slate-100 bg-slate-50 text-[10px] uppercase font-bold text-slate-500 dark:border-slate-800 dark:bg-slate-900/50">
                    <tr>
                      <th className="py-2.5 px-3">#</th>
                      <th className="py-2.5 px-3">Nombres *</th>
                      <th className="py-2.5 px-3">Apellidos *</th>
                      <th className="py-2.5 px-3">Correo Institucional *</th>
                      <th className="py-2.5 px-3">Cargo</th>
                      <th className="py-2.5 px-3">Celular</th>
                      <th className="py-2.5 px-3 text-center">Quitar</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {colaboradores.map((colab, idx) => (
                      <tr key={idx} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/20">
                        <td className="py-2 px-3 font-mono text-slate-400">{idx + 1}</td>
                        <td className="py-2 px-2">
                          <input
                            type="text"
                            value={colab.nombres}
                            onChange={(e) => handleRowChange(idx, 'nombres', e.target.value)}
                            placeholder="Ej. Carlos"
                            className="h-9 w-full rounded-lg border border-slate-200 bg-white px-2.5 text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                            required
                          />
                        </td>
                        <td className="py-2 px-2">
                          <input
                            type="text"
                            value={colab.apellidos}
                            onChange={(e) => handleRowChange(idx, 'apellidos', e.target.value)}
                            placeholder="Ej. Mendoza Silva"
                            className="h-9 w-full rounded-lg border border-slate-200 bg-white px-2.5 text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                            required
                          />
                        </td>
                        <td className="py-2 px-2">
                          <input
                            type="email"
                            value={colab.email}
                            onChange={(e) => handleRowChange(idx, 'email', e.target.value)}
                            placeholder="cmendoza@empresa.com"
                            className="h-9 w-full rounded-lg border border-slate-200 bg-white px-2.5 font-mono text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                            required
                          />
                        </td>
                        <td className="py-2 px-2">
                          <input
                            type="text"
                            value={colab.cargo || ''}
                            onChange={(e) => handleRowChange(idx, 'cargo', e.target.value)}
                            placeholder="Ej. Gerente TI"
                            className="h-9 w-full rounded-lg border border-slate-200 bg-white px-2.5 text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                          />
                        </td>
                        <td className="py-2 px-2">
                          <input
                            type="tel"
                            value={colab.telefono || ''}
                            onChange={(e) => handleRowChange(idx, 'telefono', e.target.value)}
                            placeholder="999111222"
                            className="h-9 w-full rounded-lg border border-slate-200 bg-white px-2.5 font-mono text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
                          />
                        </td>
                        <td className="py-2 px-2 text-center">
                          <button
                            type="button"
                            onClick={() => handleRemoveRow(idx)}
                            className="rounded p-1.5 text-slate-400 hover:bg-rose-50 hover:text-rose-600 dark:hover:bg-rose-950/30"
                            title="Eliminar fila"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="pt-2">
                <button
                  type="button"
                  onClick={handleAddRow}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-3.5 py-2 text-xs font-bold text-slate-700 shadow-sm transition hover:bg-slate-50 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span>Agregar Fila</span>
                </button>
              </div>
            </div>
          )}

          {/* Opciones y notas */}
          <div className="mt-6 border-t border-slate-100 pt-5 dark:border-slate-800 space-y-4">
            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={enviarCredencialesEmail}
                onChange={(e) => setEnviarCredencialesEmail(e.target.checked)}
                className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 dark:border-slate-700 dark:bg-slate-800"
              />
              <span className="text-xs font-semibold text-slate-800 dark:text-slate-200">
                Enviar credenciales de acceso y bienvenida institucional por correo electrónico a cada colaborador
              </span>
            </label>

            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Notas del Contrato o Lote (Opcional)
              </label>
              <input
                type="text"
                value={notasAdmin}
                onChange={(e) => setNotasAdmin(e.target.value)}
                placeholder="Ej. Contrato In-Company Q4 - Factura F001-0029"
                className="mt-1.5 h-10 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-xs outline-none focus:border-indigo-500 dark:border-slate-800 dark:bg-slate-900 dark:text-white"
              />
            </div>
          </div>

          {/* Error Message */}
          {errorMessage && (
            <div className="mt-4 flex items-center gap-3 rounded-2xl border border-rose-500/20 bg-rose-500/10 p-4 text-xs font-semibold text-rose-600 dark:text-rose-400">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Botón de Envío */}
          <div className="mt-6 flex justify-end">
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-2 rounded-2xl bg-gradient-to-r from-indigo-600 via-purple-600 to-cyan-600 px-6 py-3.5 text-xs font-bold text-white shadow-lg shadow-indigo-500/25 transition hover:brightness-110 active:scale-95 disabled:opacity-50"
            >
              {isSubmitting ? (
                <>
                  <LoaderCircle className="h-4 w-4 animate-spin" />
                  <span>Procesando Nómina y Moodle LMS...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="h-4 w-4" />
                  <span>Ejecutar Matrícula Masiva en Moodle</span>
                </>
              )}
            </button>
          </div>
        </div>
      </form>

      {/* Resultados de la Ejecución */}
      {resultadoLote && (
        <div className="space-y-6 rounded-3xl border border-emerald-500/20 bg-white p-6 shadow-xl dark:border-emerald-500/10 dark:bg-[#0c111a] md:p-8">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-500">
                <CheckCircle2 className="h-6 w-6" />
              </div>
              <div>
                <h3 className="text-base font-black text-slate-950 dark:text-white">
                  Lote Corporativo Procesado con Éxito
                </h3>
                <p className="text-xs text-slate-500">
                  {resultadoLote.empresaNombre} — {resultadoLote.programaTitulo}
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={handleCopyReport}
              className="inline-flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-2.5 text-xs font-bold text-white transition hover:bg-slate-800 dark:bg-white dark:text-slate-950"
            >
              {isCopiedReport ? <Check className="h-4 w-4 text-emerald-400" /> : <Copy className="h-4 w-4" />}
              <span>{isCopiedReport ? 'Reporte Copiado' : 'Copiar Reporte para RRHH'}</span>
            </button>
          </div>

          {/* Métricas del Lote */}
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/60">
              <p className="text-xs text-slate-500">Total Procesados</p>
              <p className="mt-1 font-mono text-2xl font-black text-slate-950 dark:text-white">
                {resultadoLote.totalProcesados}
              </p>
            </div>
            <div className="rounded-2xl border border-emerald-500/20 bg-emerald-500/5 p-4 dark:border-emerald-500/10">
              <p className="text-xs font-bold text-emerald-700 dark:text-emerald-400">Exitosos en Moodle</p>
              <p className="mt-1 font-mono text-2xl font-black text-emerald-600 dark:text-emerald-400">
                {resultadoLote.totalExitosos}
              </p>
            </div>
            <div className="rounded-2xl border border-rose-500/20 bg-rose-500/5 p-4 dark:border-rose-500/10">
              <p className="text-xs font-bold text-rose-700 dark:text-rose-400">Fallidos</p>
              <p className="mt-1 font-mono text-2xl font-black text-rose-600 dark:text-rose-400">
                {resultadoLote.totalFallidos}
              </p>
            </div>
          </div>

          {/* Tabla de Detalle de Participantes */}
          <div className="overflow-x-auto">
            <table className="w-full min-w-[700px] text-left text-xs">
              <thead className="border-b border-slate-100 bg-slate-50 text-[10px] uppercase font-bold text-slate-500 dark:border-slate-800 dark:bg-slate-900/50">
                <tr>
                  <th className="py-2.5 px-3">Colaborador</th>
                  <th className="py-2.5 px-3">Correo</th>
                  <th className="py-2.5 px-3">Usuario Moodle</th>
                  <th className="py-2.5 px-3">Contraseña Asignada</th>
                  <th className="py-2.5 px-3">Moodle User ID</th>
                  <th className="py-2.5 px-3 text-right">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {resultadoLote.resultados.map((res, i) => (
                  <tr key={i} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/20">
                    <td className="py-3 px-3">
                      <p className="font-bold text-slate-900 dark:text-white">
                        {res.nombres} {res.apellidos}
                      </p>
                      {res.cargo && <p className="text-[11px] text-slate-400">{res.cargo}</p>}
                    </td>
                    <td className="py-3 px-3 font-mono text-slate-600 dark:text-slate-400">
                      {res.email}
                    </td>
                    <td className="py-3 px-3 font-mono font-bold text-indigo-600 dark:text-indigo-400">
                      {res.moodleUsername || res.email.split('@')[0]}
                    </td>
                    <td className="py-3 px-3 font-mono text-slate-800 dark:text-slate-200">
                      {res.contrasenaGenerada || 'Conservó previa'}
                    </td>
                    <td className="py-3 px-3 font-mono text-slate-500">
                      #{res.moodleUserId || '-'}
                    </td>
                    <td className="py-3 px-3 text-right">
                      {res.exitoso ? (
                        <span className="inline-flex items-center gap-1 rounded-full border border-emerald-500/20 bg-emerald-500/10 px-2 py-0.5 text-[10px] font-bold text-emerald-600 dark:text-emerald-400">
                          <Check className="h-3 w-3" /> Matriculado
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full border border-rose-500/20 bg-rose-500/10 px-2 py-0.5 text-[10px] font-bold text-rose-600 dark:text-rose-400">
                          Error: {res.mensajeError}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
};

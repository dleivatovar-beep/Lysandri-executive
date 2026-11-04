import React, { useState, useEffect, useRef } from 'react';
import {
  X,
  BookOpen,
  Plus,
  Trash2,
  DollarSign,
  Clock,
  Award,
  Image as ImageIcon,
  User,
  ArrowRight,
  ArrowLeft,
  Layers,
  GraduationCap,
  Building,
  Save,
  Upload,
  Check,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  ExternalLink,
  Loader2,
} from 'lucide-react';
import { academicService } from '../../services/academicService';
import { CourseModule, ProgramaResponse, TierLevel } from '../../types';

interface ProgramEditorModalProps {
  isOpen: boolean;
  onClose: () => void;
  program?: ProgramaResponse | null;
  onSave: (program: Partial<ProgramaResponse>) => Promise<void> | void;
  initialStep?: 1 | 2 | 3 | 4;
}

const PRESET_CATEGORIES = [
  'Inteligencia Artificial',
  'Finanzas y Gestión',
  'Seguridad y Riesgos',
  'Automatización y Procesos',
  'Liderazgo y Estrategia',
  'Operaciones y Tecnología',
];

const buildDefaultSyllabus = (count = 3): CourseModule[] => {
  return Array.from({ length: count }, (_, i) => ({
    titulo: `Módulo ${i + 1}: Nuevo Módulo`,
    duracion: '2 Semanas',
    temas: ['Sesión 1: Fundamentos y conceptos clave'],
    entregable: 'Caso Práctico y Proyecto Aplicado',
  }));
};

export const ProgramEditorModal: React.FC<ProgramEditorModalProps> = ({
  isOpen,
  onClose,
  program,
  onSave,
  initialStep = 1,
}) => {
  const [step, setStep] = useState<1 | 2 | 3 | 4>(initialStep);
  const [isSaving, setIsSaving] = useState(false);

  // Form Fields
  const [titulo, setTitulo] = useState(program?.titulo || program?.tituloPrograma || '');
  const [categoria, setCategoria] = useState(program?.tipoPrograma || 'Liderazgo y Estrategia');
  const [instructor, setInstructor] = useState(program?.instructorNombre || program?.nombreInstructor || '');
  const [descripcion, setDescripcion] = useState(program?.descripcionCorta || program?.descripcionDetallada || program?.descripcion || '');
  const [coverUrl, setCoverUrl] = useState(program?.imagenPortadaUrl || program?.coverUrl || '');

  const [precio, setPrecio] = useState<number | string>(
    program?.precio !== undefined && program?.precio !== null ? program.precio : (program ? 0 : 5)
  );
  const [moneda, setMoneda] = useState<string>(program?.moneda || 'USD');
  const [level, setLevel] = useState<TierLevel>((program?.level as TierLevel) || 'ENTERPRISE');
  const [duracion, setDuracion] = useState(
    program?.duracionPrograma || (program?.duracionHoras ? `${program.duracionHoras} Horas` : '6 Semanas')
  );
  const [modalidad, setModalidad] = useState(
    program?.modalidad || 'Virtual Asincrónico con Mentoría RAG IA'
  );

  // Syllabus / Malla Curricular Modules (Predeterminados según el curso)
  const [syllabus, setSyllabus] = useState<CourseModule[]>(() =>
    program?.syllabus && program.syllabus.length > 0
      ? program.syllabus
      : buildDefaultSyllabus()
  );

  const [uploadedFileName, setUploadedFileName] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Moodle Course Linkage & Verification State
  const [moodleCourseId, setMoodleCourseId] = useState<number | string>(
    program?.moodleCourseId !== undefined && program?.moodleCourseId !== null ? program.moodleCourseId : ''
  );
  const [moodleCourseNombre, setMoodleCourseNombre] = useState<string>(
    program?.moodleCourseNombre || ''
  );
  const [moodleVerificado, setMoodleVerificado] = useState<boolean>(
    Boolean(program?.moodleVerificado)
  );
  const [moodleEstadoVerificacion, setMoodleEstadoVerificacion] = useState<string>(
    program?.moodleEstadoVerificacion || (program?.moodleCourseId ? 'PENDIENTE_VERIFICACION' : 'SIN_VINCULACION')
  );
  const [isVerifyingMoodle, setIsVerifyingMoodle] = useState(false);
  const [moodleVerificationMessage, setMoodleVerificationMessage] = useState<string>('');
  const [activo, setActivo] = useState<boolean>(
    program?.activo !== undefined ? program.activo : false
  );
  const [desvincularMoodleRequested, setDesvincularMoodleRequested] = useState<boolean>(false);

  useEffect(() => {
    if (isOpen) {
      setTitulo(program?.titulo || program?.tituloPrograma || '');
      setCategoria(program?.tipoPrograma || 'Liderazgo y Estrategia');
      setInstructor(program?.instructorNombre || program?.nombreInstructor || '');
      setDescripcion(program?.descripcionCorta || program?.descripcionDetallada || program?.descripcion || '');
      setCoverUrl(program?.imagenPortadaUrl || program?.coverUrl || '');
      setUploadedFileName('');
      setPrecio(program?.precio !== undefined && program?.precio !== null ? program.precio : (program ? 0 : 5));
      setMoneda(program?.moneda || 'USD');
      setLevel((program?.level as TierLevel) || 'ENTERPRISE');
      setDuracion(
        program?.duracionPrograma || (program?.duracionHoras ? `${program.duracionHoras} Horas` : '6 Semanas')
      );
      setModalidad(program?.modalidad || 'Virtual Asincrónico con Mentoría RAG IA');
      setMoodleCourseId(program?.moodleCourseId !== undefined && program?.moodleCourseId !== null ? program.moodleCourseId : '');
      setMoodleCourseNombre(program?.moodleCourseNombre || '');
      setMoodleVerificado(Boolean(program?.moodleVerificado));
      setMoodleEstadoVerificacion(program?.moodleEstadoVerificacion || (program?.moodleCourseId ? 'PENDIENTE_VERIFICACION' : 'SIN_VINCULACION'));
      setMoodleVerificationMessage('');
      setDesvincularMoodleRequested(false);
      setActivo(program?.activo !== undefined ? program.activo : false);
      setStep(initialStep || 1);
      if (program?.syllabus && program.syllabus.length > 0) {
        setSyllabus(program.syllabus);
      } else {
        setSyllabus(buildDefaultSyllabus());
      }
    }
  }, [program, isOpen, initialStep]);

  const handleVerifyMoodle = async () => {
    const idNum = Number(moodleCourseId);
    if (!idNum || idNum <= 0) {
      setMoodleVerificado(false);
      setMoodleEstadoVerificacion('SIN_VINCULACION');
      setMoodleCourseNombre('');
      setMoodleVerificationMessage('Ingrese un ID numérico positivo de curso Moodle.');
      return;
    }

    setIsVerifyingMoodle(true);
    setMoodleVerificationMessage('');
    try {
      const res = await academicService.verifyMoodleCourse(idNum);
      if (res.status === 'VERIFICADO') {
        setMoodleVerificado(true);
        setMoodleEstadoVerificacion('VERIFICADO');
        setMoodleCourseNombre(res.courseFullName || `Curso #${idNum}`);
        setMoodleVerificationMessage(`Curso verificado en Moodle LMS: "${res.courseFullName || idNum}"`);
      } else if (res.status === 'NO_ENCONTRADO') {
        setMoodleVerificado(false);
        setMoodleEstadoVerificacion('NO_ENCONTRADO');
        setMoodleCourseNombre('');
        setMoodleVerificationMessage(`El curso ID ${idNum} no existe en Moodle LMS.`);
        if (activo) {
          setActivo(false);
        }
      } else if (res.status === 'NO_DISPONIBLE') {
        setMoodleEstadoVerificacion('NO_DISPONIBLE');
        setMoodleVerificationMessage(`Servicio de Moodle no disponible: ${res.detalle || 'Error de conexión'}.`);
        if (activo) {
          setActivo(false);
        }
      }
    } catch (err: any) {
      setMoodleEstadoVerificacion('NO_DISPONIBLE');
      setMoodleVerificationMessage(`Error de conexión al consultar Moodle: ${err.message || 'Error desconocido'}.`);
      if (activo) {
        setActivo(false);
      }
    } finally {
      setIsVerifyingMoodle(false);
    }
  };

    const handleAddModule = () => {
    const newModNumber = syllabus.length + 1;
    setSyllabus([
      ...syllabus,
      {
        titulo: `Módulo ${newModNumber}: Nuevo Módulo`,
        duracion: '2 Semanas',
        temas: ['Sesión 1: Fundamentos y conceptos clave'],
        entregable: 'Caso Práctico y Proyecto Aplicado',
      },
    ]);
  };

  const handleRemoveModule = (index: number) => {
    if (syllabus.length <= 1) {
      alert('El programa debe contener al menos 1 módulo curricular.');
      return;
    }
    setSyllabus(syllabus.filter((_, i) => i !== index));
  };

  const handleUpdateModuleTitle = (index: number, newTitle: string) => {
    const updated = [...syllabus];
    updated[index].titulo = newTitle;
    setSyllabus(updated);
  };

  const handleUpdateModuleDuration = (index: number, newDuration: string) => {
    const updated = [...syllabus];
    updated[index].duracion = newDuration;
    setSyllabus(updated);
  };

  const handleUpdateModuleDeliverable = (index: number, newDeliverable: string) => {
    const updated = [...syllabus];
    updated[index].entregable = newDeliverable;
    setSyllabus(updated);
  };

  const handleAddTopic = (moduleIndex: number) => {
    const updated = [...syllabus];
    const sessionNum = updated[moduleIndex].temas.length + 1;
    updated[moduleIndex].temas.push(`Sesión ${sessionNum}: Fundamentos y conceptos clave`);
    setSyllabus(updated);
  };

  const handleRemoveTopic = (moduleIndex: number, topicIndex: number) => {
    const updated = [...syllabus];
    if (updated[moduleIndex].temas.length <= 1) {
      alert('Cada módulo debe tener al menos una sesión de aprendizaje.');
      return;
    }
    updated[moduleIndex].temas = updated[moduleIndex].temas.filter((_, i) => i !== topicIndex);
    setSyllabus(updated);
  };

  const handleUpdateTopic = (moduleIndex: number, topicIndex: number, newTopic: string) => {
    const updated = [...syllabus];
    updated[moduleIndex].temas[topicIndex] = newTopic;
    setSyllabus(updated);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      alert('Por favor selecciona un archivo de imagen válido (PNG, JPG, WEBP, etc.).');
      return;
    }

    setUploadedFileName(file.name);

    const reader = new FileReader();
    reader.onload = (event) => {
      const result = event.target?.result as string;
      if (!result) return;

      const img = new Image();
      img.onload = () => {
        // Redimensionar a máximo 1200px para mantener alta resolución y optimizar almacenamiento
        const maxDim = 1200;
        let width = img.width;
        let height = img.height;

        if (width > maxDim || height > maxDim) {
          if (width > height) {
            height = Math.round((height * maxDim) / width);
            width = maxDim;
          } else {
            width = Math.round((width * maxDim) / height);
            height = maxDim;
          }
        }

        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (ctx) {
          ctx.drawImage(img, 0, 0, width, height);
          const compressed = canvas.toDataURL('image/jpeg', 0.88);
          setCoverUrl(compressed);
        } else {
          setCoverUrl(result);
        }
      };
      img.src = result;
    };
    reader.readAsDataURL(file);
  };

  if (!isOpen) return null;

  const handleSubmit = async (shouldPublish: boolean = false) => {
    if (!titulo.trim()) {
      alert('Por favor, ingresa el título del programa.');
      setStep(1);
      return;
    }

    const idNum = moodleCourseId !== '' && !isNaN(Number(moodleCourseId)) && Number(moodleCourseId) > 0
      ? Number(moodleCourseId)
      : null;

    if (shouldPublish) {
      if (!idNum) {
        alert('Regla de negocio: Todo programa publicado en la tienda debe estar vinculado a un curso de Moodle LMS. Para guardarlo sin vinculación, elija "Guardar como Borrador".');
        setStep(2);
        return;
      }
      if (!moodleVerificado) {
        alert('El curso de Moodle debe estar verificado antes de publicar. Por favor verifique el curso en el Paso 2.');
        setStep(2);
        return;
      }
    }

    setIsSaving(true);
    try {
      const numericPrecio = typeof precio === 'number'
        ? precio
        : (precio !== '' && !isNaN(Number(precio)) ? Number(precio) : (program?.precio !== undefined ? Number(program.precio) : 0));

      const payload: Partial<ProgramaResponse> = {
        idPrograma: program?.idPrograma,
        titulo: titulo.trim(),
        tituloPrograma: titulo.trim(),
        tipoPrograma: categoria,
        nombreInstructor: instructor.trim() || 'Dirección Académica',
        instructorNombre: instructor.trim() || 'Dirección Académica',
        descripcion: descripcion.trim(),
        descripcionCorta: descripcion.trim(),
        coverUrl,
        imagenPortadaUrl: coverUrl,
        precio: numericPrecio,
        moneda: moneda || program?.moneda || 'USD',
        level,
        duracionPrograma: duracion,
        modalidad,
        syllabus,
        moodleCourseId: idNum || undefined,
        moodleCourseNombre: moodleCourseNombre || undefined,
        moodleVerificado: desvincularMoodleRequested ? false : moodleVerificado,
        moodleEstadoVerificacion: desvincularMoodleRequested ? 'SIN_VINCULACION' : ((moodleEstadoVerificacion as any) || (idNum ? 'PENDIENTE_VERIFICACION' : 'SIN_VINCULACION')),
        desvincularMoodle: desvincularMoodleRequested,
        activo: desvincularMoodleRequested ? false : shouldPublish,
        precioAprobado: program?.precioAprobado !== undefined ? program.precioAprobado : true,
      };

      await onSave(payload);
      onClose();
    } catch (err: any) {
      alert('Error al guardar el programa: ' + (err.message || 'Error'));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="lx-admin-modal fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-slate-950/80 backdrop-blur-md overflow-y-auto">
      <div className="relative w-full max-w-4xl bg-white dark:bg-[#0d1322] text-slate-900 dark:text-slate-100 rounded-3xl border border-slate-200 dark:border-cyan-500/30 shadow-2xl overflow-hidden my-auto flex flex-col max-h-[92vh]">
        {/* Header con Glow */}
        <div className="relative border-b border-slate-200 dark:border-slate-800/80 bg-slate-50 dark:bg-slate-950/80 p-5 sm:p-6 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 shrink-0">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-cyan-500/30 bg-cyan-500/10 text-cyan-600 dark:text-cyan-400 shadow-lg shadow-cyan-500/10">
              <BookOpen className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-mono text-[10px] uppercase font-bold text-cyan-600 dark:text-cyan-400 bg-cyan-500/10 dark:bg-cyan-950/80 border border-cyan-500/30 px-2 py-0.5 rounded">
                  Gestión Curricular
                </span>
                {program && (
                  <span className="text-xs text-slate-500 dark:text-slate-400">
                    Edición de Programa
                  </span>
                )}
              </div>
              <h2 className="text-lg sm:text-xl font-black text-slate-900 dark:text-white tracking-tight mt-0.5">
                {program ? `Editar: ${program.tituloPrograma}` : 'Crear Programa'}
              </h2>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900/80 p-2 text-slate-500 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white hover:border-slate-300 dark:hover:border-slate-700 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Stepper Navigation */}
        <div className="border-b border-slate-200 dark:border-slate-800/80 bg-slate-100/70 dark:bg-slate-900/50 px-6 py-3 shrink-0 flex items-center justify-between gap-2 overflow-x-auto">
          <div className="flex items-center gap-2 sm:gap-4 text-xs font-bold">
            <button
              type="button"
              onClick={() => setStep(1)}
              className={`flex items-center px-3 py-1.5 rounded-xl transition-all ${
                step === 1
                  ? 'bg-cyan-500/15 text-cyan-700 dark:text-cyan-300 border border-cyan-500/30 font-extrabold shadow-sm'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-950 dark:hover:text-white'
              }`}
            >
              <span>Curso</span>
            </button>

            <button
              type="button"
              onClick={() => setStep(2)}
              className={`flex items-center px-3 py-1.5 rounded-xl transition-all ${
                step === 2
                  ? 'bg-cyan-500/15 text-cyan-700 dark:text-cyan-300 border border-cyan-500/30 font-extrabold shadow-sm'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-950 dark:hover:text-white'
              }`}
            >
              <span>Costo e Información</span>
            </button>

            <button
              type="button"
              onClick={() => setStep(3)}
              className={`flex items-center px-3 py-1.5 rounded-xl transition-all ${
                step === 3
                  ? 'bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border border-indigo-500/30 font-extrabold shadow-sm'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-950 dark:hover:text-white'
              }`}
            >
              <span>Malla Curricular</span>
            </button>

            <button
              type="button"
              onClick={() => setStep(4)}
              className={`flex items-center px-3 py-1.5 rounded-xl transition-all ${
                step === 4
                  ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30 font-extrabold shadow-sm'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-950 dark:hover:text-white'
              }`}
            >
              <span>Vista Previa</span>
            </button>
          </div>
        </div>

        {/* Modal Body / Tab Content */}
        <div className="p-6 overflow-y-auto flex-1 space-y-6">
          {/* PASO 1: CURSO */}
          {step === 1 && (
            <div className="space-y-5 animate-fadeIn">
              <div>
                <label className="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">
                  Ingrese título de curso *
                </label>
                <input
                  type="text"
                  required
                  value={titulo}
                  onChange={(e) => setTitulo(e.target.value)}
                  placeholder="Ingrese título de curso"
                  className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-4 py-3 text-sm text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:border-cyan-500 dark:focus:border-cyan-400 focus:outline-none transition-colors"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">
                    Categoría de curso
                  </label>
                  <select
                    value={categoria}
                    onChange={(e) => setCategoria(e.target.value)}
                    className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-4 py-3 text-xs text-slate-900 dark:text-white focus:border-cyan-500 dark:focus:border-cyan-400 focus:outline-none"
                  >
                    {PRESET_CATEGORIES.map((cat) => (
                      <option key={cat} value={cat} className="text-slate-900 dark:text-white dark:bg-slate-900">
                        {cat}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">
                    Docente
                  </label>
                  <div className="relative">
                    <User className="absolute left-3.5 top-3 h-4 w-4 text-slate-400 dark:text-slate-500" />
                    <input
                      type="text"
                      value={instructor}
                      onChange={(e) => setInstructor(e.target.value)}
                      placeholder="Ingrese nombre y apellido del docente"
                      className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 pl-10 pr-4 py-3 text-xs text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:border-cyan-500 dark:focus:border-cyan-400 focus:outline-none"
                    />
                  </div>
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">
                  Descripción
                </label>
                <textarea
                  rows={3}
                  value={descripcion}
                  onChange={(e) => setDescripcion(e.target.value)}
                  placeholder=""
                  className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 p-3.5 text-xs text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:border-cyan-500 dark:focus:border-cyan-400 focus:outline-none transition-colors"
                />
              </div>

              {/* Carga de Imagen de Portada desde el equipo */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <label className="text-xs font-bold text-slate-700 dark:text-slate-200 flex items-center gap-1.5">
                    <ImageIcon className="h-4 w-4 text-cyan-500 dark:text-cyan-400" />
                    <span>Imagen de Portada Ejecutiva</span>
                  </label>
                  <span className="text-[10px] text-slate-500 dark:text-slate-400 font-mono">
                    Sube una imagen desde tu equipo
                  </span>
                </div>

                {/* Input invisible para seleccionar archivo desde el equipo */}
                <input
                  type="file"
                  ref={fileInputRef}
                  accept="image/png, image/jpeg, image/jpg, image/webp, image/gif"
                  onChange={handleFileUpload}
                  className="hidden"
                />

                {coverUrl ? (
                  <div className="flex items-center justify-between gap-3 rounded-2xl border border-cyan-500/40 bg-cyan-50/70 p-3.5 dark:border-cyan-500/30 dark:bg-cyan-950/25">
                    <div className="flex items-center gap-3 min-w-0">
                      <img
                        src={coverUrl}
                        alt="Portada cargada"
                        className="h-14 w-24 rounded-xl object-cover border border-cyan-500/30 shadow-sm shrink-0"
                      />
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5">
                          <Check className="h-4 w-4 text-emerald-600 dark:text-emerald-400 shrink-0" />
                          <span className="text-xs font-bold text-slate-900 dark:text-white truncate">
                            {uploadedFileName || 'Imagen de portada del programa'}
                          </span>
                        </div>
                        <span className="text-[10px] text-slate-500 dark:text-slate-400 font-mono block mt-0.5">
                          Cargada exitosamente desde este equipo
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        type="button"
                        onClick={() => fileInputRef.current?.click()}
                        className="inline-flex items-center gap-1.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2 text-xs font-bold text-slate-700 dark:text-slate-200 hover:text-cyan-600 dark:hover:text-cyan-400 transition-colors shadow-sm"
                      >
                        <Upload className="h-3.5 w-3.5" />
                        <span>Cambiar</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setCoverUrl('');
                          setUploadedFileName('');
                          if (fileInputRef.current) fileInputRef.current.value = '';
                        }}
                        className="rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 p-2 text-slate-500 hover:text-rose-500 dark:text-slate-400 transition-colors shadow-sm"
                        title="Quitar imagen"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="w-full flex flex-col items-center justify-center gap-2.5 rounded-2xl border-2 border-dashed border-slate-300 dark:border-slate-700 bg-slate-50 hover:bg-cyan-50/60 hover:border-cyan-500 dark:bg-slate-900/60 dark:hover:bg-cyan-950/20 dark:hover:border-cyan-400 p-6 text-center transition-all cursor-pointer shadow-sm group"
                  >
                    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-600 dark:text-cyan-400 group-hover:scale-110 transition-transform">
                      <Upload className="h-5 w-5" />
                    </div>
                    <div>
                      <p className="text-xs font-bold text-slate-800 dark:text-slate-200">
                        Subir imagen desde este equipo
                      </p>
                      <p className="text-[10px] text-slate-500 dark:text-slate-400 mt-0.5 font-mono">
                        PNG, JPG, WEBP o GIF (haz clic para subir tu archivo)
                      </p>
                    </div>
                  </button>
                )}

                {/* Portadas del proyecto o URL manual */}
                <div className="mt-3 space-y-2">
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={coverUrl.startsWith('data:') ? '' : coverUrl}
                      onChange={(e) => {
                        setCoverUrl(e.target.value);
                        setUploadedFileName('');
                      }}
                      placeholder="O escribe URL / ruta: /courses/curso1.png o https://..."
                      className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 p-2.5 text-xs text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:border-cyan-500 focus:outline-none transition-colors"
                    />
                  </div>

                  <div className="flex flex-wrap items-center gap-1.5 pt-1">
                    <span className="text-[10px] text-slate-500 dark:text-slate-400 font-mono">
                      Portadas disponibles:
                    </span>
                    {[
                      { path: '/courses/curso1.png', label: 'Curso 1 (FinOps)' },
                      { path: '/courses/curso2.png', label: 'Curso 2 (Ciberseguridad)' },
                      { path: '/courses/curso3.png', label: 'Curso 3 (Liderazgo)' },
                      { path: '/courses/curso4.png', label: 'Curso 4 (Ágiles)' },
                    ].map((item) => (
                      <button
                        key={item.path}
                        type="button"
                        onClick={() => {
                          setCoverUrl(item.path);
                          setUploadedFileName(item.label);
                        }}
                        className={`px-2.5 py-1 rounded-lg text-[10px] font-mono border transition-all ${
                          coverUrl === item.path
                            ? 'bg-cyan-500/20 border-cyan-500 text-cyan-700 dark:text-cyan-300 font-bold'
                            : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-slate-800 text-slate-600 dark:text-slate-400 hover:border-cyan-400'
                        }`}
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* PASO 2: COSTO E INFORMACIÓN */}
          {step === 2 && (
            <div className="space-y-5 animate-fadeIn">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">
                    Costo de programa
                  </label>
                  <div className="relative">
                    <DollarSign className="absolute left-3.5 top-3 h-4 w-4 text-emerald-500 dark:text-emerald-400" />
                    <input
                      type="text"
                      inputMode="decimal"
                      required
                      value={precio}
                      onChange={(e) => {
                        const val = e.target.value;
                        if (val === '' || /^\d*\.?\d*$/.test(val)) {
                          let cleanVal = val;
                          if (cleanVal.length > 1 && cleanVal.startsWith('0') && cleanVal[1] !== '.') {
                            cleanVal = cleanVal.replace(/^0+/, '');
                            if (cleanVal === '') cleanVal = '0';
                          }
                          setPrecio(cleanVal);
                        }
                      }}
                      onBlur={() => {
                        if (precio === '' || isNaN(Number(precio))) {
                          setPrecio(5);
                        } else {
                          setPrecio(Number(precio));
                        }
                      }}
                      placeholder="5.00"
                      className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 pl-10 pr-4 py-3 text-sm font-mono text-emerald-600 dark:text-emerald-300 font-bold focus:border-emerald-500 focus:outline-none"
                    />
                  </div>
                  <span className="text-[10px] text-slate-500 dark:text-slate-400 mt-1 block">
                    Precio total facturado (incluye IGV 18% y opción a Factura con RUC).
                  </span>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">
                    Nivel de educación
                  </label>
                  <select
                    value={level}
                    onChange={(e) => setLevel(e.target.value as TierLevel)}
                    className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-4 py-3 text-xs text-slate-900 dark:text-white focus:border-cyan-500 dark:focus:border-cyan-400 focus:outline-none"
                  >
                    <option value="ESSENTIAL" className="dark:bg-slate-900">ESSENTIAL (Directores y Jefaturas)</option>
                    <option value="ADVANCED" className="dark:bg-slate-900">ADVANCED (Gerentes de Área y Subgerencias)</option>
                    <option value="ENTERPRISE" className="dark:bg-slate-900">ENTERPRISE (C-Suite, Directores & Gerencia General)</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">
                    Duración Estimada
                  </label>
                  <div className="relative">
                    <Clock className="absolute left-3.5 top-3 h-4 w-4 text-cyan-500 dark:text-cyan-400" />
                    <input
                      type="text"
                      value={duracion}
                      onChange={(e) => setDuracion(e.target.value)}
                      placeholder="Ej: 6 Semanas (24 Horas Cronológicas)"
                      className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 pl-10 pr-4 py-3 text-xs text-slate-900 dark:text-white focus:border-cyan-500 dark:focus:border-cyan-400 focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 dark:text-slate-200 mb-1.5">
                    Modalidad
                  </label>
                  <div className="relative">
                    <GraduationCap className="absolute left-3.5 top-3 h-4 w-4 text-indigo-500 dark:text-indigo-400" />
                    <input
                      type="text"
                      value={modalidad}
                      onChange={(e) => setModalidad(e.target.value)}
                      placeholder="Ej: Virtual Asincrónico con Mentoría RAG IA"
                      className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 pl-10 pr-4 py-3 text-xs text-slate-900 dark:text-white focus:border-cyan-500 dark:focus:border-cyan-400 focus:outline-none"
                    />
                  </div>
                </div>
              </div>

              {/* Tarjeta Informativa de Facturación SUNAT */}
              <div className="rounded-2xl border border-indigo-200 dark:border-indigo-500/30 bg-indigo-50/70 dark:bg-indigo-950/20 p-4 text-xs text-indigo-900 dark:text-indigo-200 space-y-1 font-mono">
                <div className="font-bold flex items-center gap-1.5 text-indigo-700 dark:text-indigo-300">
                  <Building className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
                  <span>Tributaria y SPOT automático</span>
                </div>
                <p className="text-[11px] text-slate-600 dark:text-slate-300 font-sans">
                  Al superar los S/. 700.00, el sistema contable aplicará automáticamente la retención SPOT del 12% para depósitos en el Banco de la Nación cuando las empresas soliciten Factura con RUC.
                </p>
              </div>

              {/* Vinculación con Moodle LMS */}
              <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-900/60 p-4 space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div>
                    <label className="block text-xs font-bold text-slate-800 dark:text-slate-100 flex items-center gap-1.5">
                      <ExternalLink className="h-4 w-4 text-cyan-500" />
                      <span>Vinculación con Moodle LMS</span>
                    </label>
                    <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                      Identificador numérico del curso en el campus virtual oficial (https://campus.yunixingenieros.com).
                    </p>
                  </div>

                  <div className="shrink-0">
                    {moodleEstadoVerificacion === 'VERIFICADO' && (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-100 dark:bg-emerald-950/80 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30">
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
                        <span>Curso Verificado</span>
                      </span>
                    )}
                    {moodleEstadoVerificacion === 'NO_ENCONTRADO' && (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-rose-100 dark:bg-rose-950/80 text-rose-700 dark:text-rose-300 border border-rose-500/30">
                        <XCircle className="h-3.5 w-3.5 text-rose-600 dark:text-rose-400" />
                        <span>Curso Inexistente</span>
                      </span>
                    )}
                    {moodleEstadoVerificacion === 'NO_DISPONIBLE' && (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-100 dark:bg-amber-950/80 text-amber-700 dark:text-amber-300 border border-amber-500/30">
                        <AlertTriangle className="h-3.5 w-3.5 text-amber-600 dark:text-amber-400" />
                        <span>No Disponible</span>
                      </span>
                    )}
                    {(moodleEstadoVerificacion === 'SIN_VINCULACION' || !moodleCourseId) && (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-slate-300 dark:border-slate-700">
                        <span>Sin Vinculación (Borrador)</span>
                      </span>
                    )}
                    {moodleEstadoVerificacion === 'PENDIENTE_VERIFICACION' && moodleCourseId && (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-cyan-100 dark:bg-cyan-950/80 text-cyan-700 dark:text-cyan-300 border border-cyan-500/30">
                        <span>Pendiente de Verificación</span>
                      </span>
                    )}
                  </div>
                </div>

                <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
                  <div className="relative flex-1">
                    <input
                      type="number"
                      min="1"
                      value={moodleCourseId}
                      onChange={(e) => {
                        const val = e.target.value;
                        setMoodleCourseId(val);
                        setMoodleVerificado(false);
                        setMoodleCourseNombre('');
                        setMoodleEstadoVerificacion(val ? 'PENDIENTE_VERIFICACION' : 'SIN_VINCULACION');
                        setMoodleVerificationMessage('');
                        setDesvincularMoodleRequested(false);
                      }}
                      placeholder="ID del curso Moodle (ej. 2, 15, 101)"
                      className="w-full rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-4 py-2.5 text-xs text-slate-900 dark:text-white placeholder-slate-400 focus:border-cyan-500 focus:outline-none font-mono"
                    />
                  </div>

                  <button
                    type="button"
                    onClick={handleVerifyMoodle}
                    disabled={isVerifyingMoodle || !moodleCourseId}
                    className="inline-flex items-center justify-center gap-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white px-4 py-2.5 text-xs font-bold transition-all shadow-sm disabled:opacity-50 disabled:cursor-not-allowed shrink-0"
                  >
                    {isVerifyingMoodle ? (
                      <>
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        <span>Verificando...</span>
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        <span>Verificar en Moodle</span>
                      </>
                    )}
                  </button>

                  {(moodleCourseId || program?.moodleCourseId) && (
                    <button
                      type="button"
                      onClick={() => {
                        if (confirm('¿Está seguro de desvincular el curso de Moodle? El programa se guardará como borrador y se desactivará de la tienda pública.')) {
                          setMoodleCourseId('');
                          setMoodleCourseNombre('');
                          setMoodleVerificado(false);
                          setMoodleEstadoVerificacion('SIN_VINCULACION');
                          setMoodleVerificationMessage('Curso desvinculado voluntariamente. El programa quedará en borrador (desactivado).');
                          setDesvincularMoodleRequested(true);
                          setActivo(false);
                        }
                      }}
                      className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-rose-300 dark:border-rose-800 bg-rose-50 dark:bg-rose-950/40 hover:bg-rose-100 text-rose-700 dark:text-rose-300 px-4 py-2.5 text-xs font-bold transition-all shadow-sm shrink-0"
                    >
                      <XCircle className="h-3.5 w-3.5" />
                      <span>Desvincular de Moodle</span>
                    </button>
                  )}
                </div>

                {moodleCourseNombre && (
                  <div className="flex items-center gap-2 p-2.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-500/20 text-emerald-800 dark:text-emerald-300 text-xs">
                    <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-600" />
                    <span><strong>Curso detectado en Moodle:</strong> {moodleCourseNombre}</span>
                  </div>
                )}

                {moodleVerificationMessage && !moodleCourseNombre && (
                  <div className={`p-2.5 rounded-xl border text-xs ${
                    moodleEstadoVerificacion === 'NO_ENCONTRADO'
                      ? 'bg-rose-50 dark:bg-rose-950/30 border-rose-500/30 text-rose-700 dark:text-rose-300'
                      : 'bg-amber-50 dark:bg-amber-950/30 border-amber-500/30 text-amber-700 dark:text-amber-300'
                  }`}>
                    {moodleVerificationMessage}
                  </div>
                )}

                <div className="text-[11px] text-slate-500 dark:text-slate-400 border-t border-slate-200 dark:border-slate-800 pt-3">
                  <p>
                    <strong className="text-slate-700 dark:text-slate-200">Gobernanza académica:</strong> Todo programa real publicado y disponible para compra debe estar vinculado a un curso existente y correcto en Moodle. Si no cuenta con el curso creado aún, guárdelo como borrador para conservarlo sin publicarlo en la tienda pública.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* PASO 3: MALLA CURRICULAR */}
          {step === 3 && (
            <div className="space-y-6 animate-fadeIn">
              {/* Header de la Malla */}
              <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/90 p-4">
                <div>
                  <h3 className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-2">
                    <Layers className="h-4 w-4 text-cyan-500 dark:text-cyan-400" />
                    <span>Malla Curricular</span>
                  </h3>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Estructura académica de módulos y sesiones. Puedes crear, editar o eliminar según el programa.
                  </p>
                </div>

                <button
                  type="button"
                  onClick={handleAddModule}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-white px-3.5 py-2 font-mono text-xs font-bold transition-all shadow-sm active:scale-95"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span>Agregar Módulo</span>
                </button>
              </div>

              {/* Lista de Módulos */}
              <div className="space-y-4">
                {syllabus.map((mod, modIdx) => (
                  <div
                    key={modIdx}
                    className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-950/80 p-5 space-y-4 shadow-sm"
                  >
                    <div className="flex items-center justify-between gap-3 border-b border-slate-200 dark:border-slate-800/80 pb-3">
                      <div className="flex items-center gap-2.5 flex-1">
                        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-cyan-100 dark:bg-cyan-950 text-cyan-700 dark:text-cyan-400 font-mono text-xs font-black border border-cyan-300 dark:border-cyan-500/30 shrink-0">
                          {modIdx + 1}
                        </span>
                        <input
                          type="text"
                          value={mod.titulo}
                          onChange={(e) => handleUpdateModuleTitle(modIdx, e.target.value)}
                          placeholder={`Título del Módulo ${modIdx + 1}`}
                          className="flex-1 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-1.5 text-xs font-bold text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:border-cyan-500 focus:ring-1 focus:ring-cyan-500 transition-all"
                        />
                      </div>

                      <div className="flex items-center gap-2">
                        <input
                          type="text"
                          value={mod.duracion}
                          onChange={(e) => handleUpdateModuleDuration(modIdx, e.target.value)}
                          placeholder="2 Semanas"
                          className="w-24 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs font-mono font-bold text-cyan-700 dark:text-cyan-300 text-center placeholder:text-slate-400 focus:outline-none focus:border-cyan-500 transition-all"
                        />
                        {syllabus.length > 1 && (
                          <button
                            type="button"
                            onClick={() => handleRemoveModule(modIdx)}
                            title="Eliminar este módulo"
                            className="rounded-xl border border-rose-200 dark:border-rose-900/50 bg-rose-50 dark:bg-rose-950/40 p-2 text-rose-600 dark:text-rose-400 hover:bg-rose-600 hover:text-white dark:hover:bg-rose-600 dark:hover:text-white transition-all shadow-sm"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Temas / Sesiones */}
                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-semibold text-slate-700 dark:text-slate-300">
                          Temas y Sesiones de Aprendizaje:
                        </span>
                        <button
                          type="button"
                          onClick={() => handleAddTopic(modIdx)}
                          className="inline-flex items-center gap-1 text-[11px] font-bold text-cyan-600 dark:text-cyan-400 hover:underline"
                        >
                          <Plus className="h-3 w-3" />
                          <span>Agregar Tema</span>
                        </button>
                      </div>

                      <div className="space-y-2 pl-1">
                        {mod.temas.map((tema, temaIdx) => (
                          <div key={temaIdx} className="flex items-center gap-2">
                            <span className="h-1.5 w-1.5 rounded-full bg-cyan-500 dark:bg-cyan-400 shrink-0" />
                            <input
                              type="text"
                              value={tema}
                              onChange={(e) => handleUpdateTopic(modIdx, temaIdx, e.target.value)}
                              placeholder={`Sesión ${temaIdx + 1}`}
                              className="flex-1 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1 text-xs text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:border-cyan-500"
                            />
                            {mod.temas.length > 1 && (
                              <button
                                type="button"
                                onClick={() => handleRemoveTopic(modIdx, temaIdx)}
                                title="Eliminar tema"
                                className="text-slate-400 hover:text-rose-500 p-1 transition-colors"
                              >
                                <Trash2 className="h-3 w-3" />
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Entregable Práctico */}
                    <div className="rounded-xl border border-slate-200 dark:border-slate-800/80 bg-slate-100/70 dark:bg-slate-900/60 p-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-xs">
                      <span className="font-bold text-amber-700 dark:text-amber-300 flex items-center gap-1.5 shrink-0">
                        <Award className="h-4 w-4 text-amber-500 dark:text-amber-400" />
                        <span>Entregable / Caso Práctico:</span>
                      </span>
                      <input
                        type="text"
                        value={mod.entregable || ''}
                        onChange={(e) => handleUpdateModuleDeliverable(modIdx, e.target.value)}
                        placeholder="Ej. Diagnóstico Estratégico y Matriz de Oportunidades"
                        className="flex-1 w-full sm:w-auto rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-950 px-2.5 py-1 text-xs font-semibold text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:border-amber-500"
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* PASO 4: VISTA PREVIA EN VIVO */}
          {step === 4 && (
            <div className="space-y-6 animate-fadeIn">
              <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 p-4 text-xs text-slate-600 dark:text-slate-400 flex items-center justify-between">
                <span>
                  Visualización preliminar: Revisa cómo verán los estudiantes tu programa y sílabo antes de publicarlo.
                </span>
                <span className="rounded-full bg-emerald-100 dark:bg-emerald-950 border border-emerald-300 dark:border-emerald-500/40 px-2.5 py-0.5 text-[10px] font-mono text-emerald-700 dark:text-emerald-300 font-bold">
                  Listo para Publicar
                </span>
              </div>

              {/* Simulación de Tarjeta del Catálogo */}
              <div className="max-w-md mx-auto rounded-3xl border border-cyan-500/20 dark:border-cyan-500/30 bg-white dark:bg-[#0d1322] p-5 shadow-2xl space-y-4">
                <div className="relative overflow-hidden rounded-2xl h-44 border border-slate-200 dark:border-slate-800 bg-slate-100 dark:bg-slate-900 flex items-center justify-center">
                  {coverUrl ? (
                    <img
                      src={coverUrl}
                      alt={titulo}
                      className="h-full w-full object-cover transition-transform duration-700 hover:scale-105"
                    />
                  ) : (
                    <div className="flex flex-col items-center justify-center text-slate-400">
                      <ImageIcon className="h-10 w-10 mb-1 opacity-40" />
                      <span className="text-xs font-mono">Sin imagen de portada</span>
                    </div>
                  )}
                  <div className="absolute top-3 left-3 flex gap-2">
                    <span className="rounded-full bg-slate-900/80 backdrop-blur-md border border-cyan-500/30 px-3 py-1 text-[10px] font-bold text-cyan-300 uppercase font-mono">
                      {categoria}
                    </span>
                    <span className="rounded-full bg-indigo-900/80 backdrop-blur-md border border-indigo-500/30 px-3 py-1 text-[10px] font-bold text-indigo-300 uppercase font-mono">
                      {level}
                    </span>
                  </div>
                </div>

                <div className="space-y-2">
                  <h3 className="text-base font-black text-slate-900 dark:text-white tracking-tight leading-snug">
                    {titulo || 'Título del Programa'}
                  </h3>
                  <p className="text-xs text-slate-600 dark:text-slate-400 line-clamp-2 leading-relaxed">
                    {descripcion || 'Descripción del programa ejecutivo...'}
                  </p>
                  <p className="text-[11px] text-cyan-600 dark:text-cyan-400 font-medium">
                    Docente: {instructor || 'Dirección Académica'}
                  </p>
                </div>

                <div className="border-t border-slate-200 dark:border-slate-800/80 pt-3 flex items-center justify-between">
                  <div>
                    <span className="text-[10px] text-slate-500 dark:text-slate-400 block font-mono">Costo del Programa</span>
                    <span className="font-mono text-lg font-black text-emerald-600 dark:text-emerald-400">
                      S/. {(Number(precio) || 0).toLocaleString('es-PE', { minimumFractionDigits: 2 })}
                    </span>
                  </div>

                  <span className="text-xs font-mono text-slate-500 dark:text-slate-400 flex items-center gap-1">
                    <Clock className="h-3.5 w-3.5 text-cyan-500 dark:text-cyan-400" />
                    <span>{duracion}</span>
                  </span>
                </div>
              </div>

              {/* Resumen de Malla */}
              <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 p-5 space-y-3">
                <h4 className="font-bold text-xs uppercase font-mono text-cyan-600 dark:text-cyan-400 tracking-wider">
                  Malla Curricular
                </h4>
                <div className="space-y-2">
                  {syllabus.map((m, idx) => (
                    <div key={idx} className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-3 text-xs flex justify-between items-center">
                      <div>
                        <strong className="text-slate-900 dark:text-white block">{m.titulo}</strong>
                        <span className="text-[11px] text-slate-500 dark:text-slate-400">{m.temas.length} sesiones • {m.entregable}</span>
                      </div>
                      <span className="font-mono text-[10px] bg-slate-100 dark:bg-slate-950 text-cyan-700 dark:text-cyan-300 px-2 py-1 rounded border border-slate-200 dark:border-slate-800">
                        {m.duracion}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer Controls */}
        <div className="border-t border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/90 p-4 sm:p-5 flex items-center justify-between gap-3 shrink-0">
          <div>
            {step > 1 && (
              <button
                type="button"
                onClick={() => setStep((step - 1) as any)}
                className="inline-flex items-center gap-1.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-4 py-2 text-xs font-bold text-slate-700 dark:text-slate-300 hover:text-slate-950 dark:hover:text-white transition-colors"
              >
                <ArrowLeft className="h-4 w-4" />
                <span>Anterior</span>
              </button>
            )}
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-300 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 py-2 text-xs font-bold text-slate-600 dark:text-slate-400 hover:text-slate-950 dark:hover:text-white transition-colors"
            >
              Cancelar
            </button>

            {step < 4 ? (
              <button
                type="button"
                onClick={() => setStep((step + 1) as any)}
                className="inline-flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-5 py-2 text-xs font-bold text-white shadow-md shadow-cyan-500/20 hover:opacity-95 transition-all"
              >
                <span>Siguiente Paso</span>
                <ArrowRight className="h-4 w-4" />
              </button>
            ) : (
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => handleSubmit(false)}
                  disabled={isSaving}
                  className="inline-flex items-center gap-1.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-4 py-2 text-xs font-bold text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition-all disabled:opacity-50 shadow-sm"
                  title="Guarda como borrador inactivo sin publicar en la tienda"
                >
                  <Save className="h-4 w-4 text-slate-500" />
                  <span>{isSaving ? 'Guardando...' : 'Guardar como Borrador'}</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleSubmit(true)}
                  disabled={isSaving || !moodleVerificado}
                  className="inline-flex items-center gap-2 rounded-xl bg-gradient-to-r from-emerald-500 to-cyan-500 px-5 py-2 text-xs font-black text-white shadow-lg shadow-emerald-500/20 hover:opacity-95 transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                  title={!moodleVerificado ? 'Requiere curso verificado en Moodle para publicar' : 'Publica el programa en el catálogo comercial'}
                >
                  <Check className="h-4 w-4" />
                  <span>{isSaving ? 'Publicando...' : 'Publicar Programa'}</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

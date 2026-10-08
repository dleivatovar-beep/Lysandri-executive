import React, {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  BookOpen,
  CheckCircle2,
  GraduationCap,
  LoaderCircle,
  RefreshCw,
  ShieldAlert,
  Trash2,
  UserCheck,
  UserPlus,
  Users,
} from 'lucide-react';

import { academicService } from '../../services/academicService';
import { getApiErrorMessage } from '../../services/authService';

import {
  ProgramaResponse,
  UsuarioRequest,
  UsuarioResponse,
} from '../../types';

interface TeacherAssignment {
  id: string;
  studentId: number;
  studentName: string;
  programId: number;
  programTitle: string;
  teacherName: string;
  assignedAt: string;
}

const STORAGE_ASSIGNMENTS_KEY = 'lysandri_docente_asignaciones';

const DEFAULT_ASSIGNMENTS: TeacherAssignment[] = [
  {
    id: 'asig-1',
    studentId: 1,
    studentName: 'Carlos Vargas Silva',
    programId: 1,
    programTitle: 'Master en Dirección Financiera',
    teacherName: 'Dra. Elena Alarcón',
    assignedAt: '05 Oct 2026',
  },
  {
    id: 'asig-2',
    studentId: 2,
    studentName: 'Mariana Sánchez Ríos',
    programId: 2,
    programTitle: 'Inteligencia Artificial para Finanzas',
    teacherName: 'Mg. Roberto Valenzuela',
    assignedAt: '06 Oct 2026',
  },
];

const EMPTY_USER: UsuarioRequest = {
  nombres: '',
  apellidos: '',
  email: '',
  passw: '',
  telefono: '',
  rol: 'ESTUDIANTE',
};

const CONTROL_CLASS =
  'h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm text-slate-900 outline-none transition focus:border-cyan-500 focus:ring-2 focus:ring-cyan-500/10 dark:border-slate-800 dark:bg-slate-900 dark:text-white';

export const AdminEnrollments: React.FC = () => {
  const [users, setUsers] = useState<UsuarioResponse[]>([]);
  const [programs, setPrograms] = useState<ProgramaResponse[]>([]);

  const [selectedUserId, setSelectedUserId] = useState('');
  const [selectedProgramId, setSelectedProgramId] = useState('');

  // Estados para la sección de Asignar Profesor a Alumno
  const [assignStudentId, setAssignStudentId] = useState('');
  const [assignProgramId, setAssignProgramId] = useState('');
  const [assignTeacherName, setAssignTeacherName] = useState('');

  const [assignments, setAssignments] = useState<TeacherAssignment[]>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_ASSIGNMENTS_KEY);
      if (raw) return JSON.parse(raw);
    } catch {
      // ignore
    }
    return DEFAULT_ASSIGNMENTS;
  });

  const saveAssignments = (list: TeacherAssignment[]) => {
    setAssignments(list);
    try {
      localStorage.setItem(STORAGE_ASSIGNMENTS_KEY, JSON.stringify(list));
    } catch {
      // ignore
    }
  };

  const [newUser, setNewUser] = useState<UsuarioRequest>(EMPTY_USER);

  const [isLoading, setIsLoading] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [isEnrolling, setIsEnrolling] = useState(false);

  const [errorMessage, setErrorMessage] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const students = useMemo(
    () => users.filter((user) => user.rol === 'ESTUDIANTE'),
    [users],
  );

  // Mapeo dinámico de profesores y los cursos que enseña cada uno
  const teachersWithCourses = useMemo(() => {
    const map = new Map<
      string,
      { id: string; nombre: string; email?: string; cursos: string[] }
    >();

    // 1. Profesores registrados como usuarios con rol INSTRUCTOR
    users
      .filter((u) => u.rol === 'INSTRUCTOR')
      .forEach((u) => {
        const fullName = `${u.nombres} ${u.apellidos}`.trim();
        map.set(fullName.toLowerCase(), {
          id: String(u.idUser),
          nombre: fullName,
          email: u.email,
          cursos: [],
        });
      });

    // 2. Asociar los cursos que enseña cada docente según el catálogo
    programs.forEach((prog) => {
      const rawTeacher = prog.nombreInstructor?.trim();
      if (!rawTeacher) return;
      const key = rawTeacher.toLowerCase();
      const existing = map.get(key);
      if (existing) {
        if (!existing.cursos.includes(prog.tituloPrograma)) {
          existing.cursos.push(prog.tituloPrograma);
        }
      } else {
        map.set(key, {
          id: rawTeacher,
          nombre: rawTeacher,
          cursos: [prog.tituloPrograma],
        });
      }
    });

    return Array.from(map.values());
  }, [users, programs]);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');

    try {
      const [usersResponse, programsResponse] = await Promise.all([
        academicService.getUsers(),
        academicService.getPrograms(),
      ]);

      setUsers(usersResponse);
      setPrograms(programsResponse);
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    if (!successMessage) {
      return;
    }

    const timeout = window.setTimeout(() => {
      setSuccessMessage('');
    }, 5000);

    return () => window.clearTimeout(timeout);
  }, [successMessage]);

  const createStudent = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setIsCreating(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const created = await academicService.createUser({
        ...newUser,
        nombres: newUser.nombres.trim(),
        apellidos: newUser.apellidos.trim(),
        email: newUser.email.trim(),
        telefono: newUser.telefono?.trim() || undefined,
        rol: 'ESTUDIANTE',
      });

      setUsers((current) => [...current, created]);
      setSelectedUserId(String(created.idUser));
      setNewUser(EMPTY_USER);

      setSuccessMessage(
        `${created.nombres} ${created.apellidos} fue creado. Ya puedes seleccionar un programa y completar su inscripción.`,
      );
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsCreating(false);
    }
  };

  const enrollStudent = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!selectedUserId || !selectedProgramId) {
      setErrorMessage('Selecciona un estudiante y un programa.');
      return;
    }

    setIsEnrolling(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      const enrollment = await academicService.enroll({
        usuarioId: Number(selectedUserId),
        programaId: Number(selectedProgramId),
      });

      const student = students.find((s) => String(s.idUser) === selectedUserId);
      const prog = programs.find((p) => String(p.idPrograma) === selectedProgramId);

      // Asignar automáticamente al docente titular del curso
      if (student && prog && prog.nombreInstructor) {
        const autoAsig: TeacherAssignment = {
          id: `asig-${Date.now()}`,
          studentId: student.idUser,
          studentName: `${student.nombres} ${student.apellidos}`,
          programId: prog.idPrograma,
          programTitle: prog.tituloPrograma,
          teacherName: prog.nombreInstructor,
          assignedAt: new Date().toLocaleDateString('es-PE', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
          }),
        };

        saveAssignments([
          autoAsig,
          ...assignments.filter(
            (a) =>
              !(
                a.studentId === student.idUser &&
                a.programId === prog.idPrograma
              ),
          ),
        ]);
      }

      setSuccessMessage(
        `${enrollment.nombreEstudiante} fue inscrito correctamente en “${enrollment.tituloPrograma}”${
          prog?.nombreInstructor
            ? ` y asignado al profesor ${prog.nombreInstructor}`
            : ''
        }.`,
      );

      setSelectedProgramId('');
    } catch (error) {
      setErrorMessage(getApiErrorMessage(error));
    } finally {
      setIsEnrolling(false);
    }
  };

  // Cambio de curso en la sección de asignación de profesor: auto-selecciona al profesor del curso
  const handleAssignProgramChange = (progId: string) => {
    setAssignProgramId(progId);
    if (!progId) return;
    const prog = programs.find((p) => String(p.idPrograma) === progId);
    if (prog?.nombreInstructor) {
      setAssignTeacherName(prog.nombreInstructor);
    }
  };

  // Cambio de profesor: si no hay curso seleccionado, propone el primer curso que enseña
  const handleAssignTeacherChange = (teacherName: string) => {
    setAssignTeacherName(teacherName);
    if (!teacherName) return;
    const teacher = teachersWithCourses.find((t) => t.nombre === teacherName);
    if (teacher && teacher.cursos.length > 0 && !assignProgramId) {
      const matchingProg = programs.find(
        (p) => p.tituloPrograma === teacher.cursos[0],
      );
      if (matchingProg) {
        setAssignProgramId(String(matchingProg.idPrograma));
      }
    }
  };

  // Guardar asignación de profesor al alumno
  const handleCreateAssignment = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setErrorMessage('');
    setSuccessMessage('');

    if (!assignStudentId || !assignProgramId || !assignTeacherName) {
      setErrorMessage(
        'Por favor selecciona el estudiante, el curso y el profesor a asignar.',
      );
      return;
    }

    const student = students.find((s) => String(s.idUser) === assignStudentId);
    const prog = programs.find((p) => String(p.idPrograma) === assignProgramId);

    if (!student || !prog) {
      setErrorMessage('Información inválida para realizar la asignación.');
      return;
    }

    const newAsig: TeacherAssignment = {
      id: `asig-${Date.now()}`,
      studentId: student.idUser,
      studentName: `${student.nombres} ${student.apellidos}`,
      programId: prog.idPrograma,
      programTitle: prog.tituloPrograma,
      teacherName: assignTeacherName,
      assignedAt: new Date().toLocaleDateString('es-PE', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      }),
    };

    const updated = [
      newAsig,
      ...assignments.filter(
        (a) =>
          !(
            a.studentId === student.idUser &&
            a.programId === prog.idPrograma
          ),
      ),
    ];

    saveAssignments(updated);
    setSuccessMessage(
      `Profesor ${assignTeacherName} asignado exitosamente al estudiante ${student.nombres} ${student.apellidos} para el curso “${prog.tituloPrograma}”.`,
    );

    setAssignStudentId('');
    setAssignProgramId('');
    setAssignTeacherName('');
  };

  const handleDeleteAssignment = (id: string) => {
    const updated = assignments.filter((a) => a.id !== id);
    saveAssignments(updated);
    setSuccessMessage('Asignación de profesor eliminada correctamente.');
  };

  const selectedEnrollProgram = programs.find(
    (p) => String(p.idPrograma) === selectedProgramId,
  );

  return (
    <section className="space-y-6">
      <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-8 dark:border-slate-800 dark:bg-[#0c111a]">
        <div className="pointer-events-none absolute -right-24 -top-24 h-72 w-72 rounded-full bg-cyan-500/10 blur-3xl" />

        <div className="relative">
          <span className="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-cyan-600 dark:text-cyan-300">
            Administración
          </span>

          <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
            Usuarios e inscripciones
          </h1>

          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">
            Registra nuevos ejecutivos, asígnalos a los programas académicos y vincula al profesor titular de acuerdo a los cursos que imparte.
          </p>
        </div>
      </div>

      {errorMessage && (
        <div className="flex items-start gap-3 rounded-xl border border-rose-500/20 bg-rose-500/10 p-4 text-sm text-rose-600 dark:text-rose-300">
          <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />
          <span>{errorMessage}</span>
        </div>
      )}

      {successMessage && (
        <div className="flex items-start gap-3 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-4 text-sm text-emerald-700 dark:text-emerald-300">
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
          <span>{successMessage}</span>
        </div>
      )}

      {isLoading ? (
        <div className="flex min-h-72 items-center justify-center rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-[#0c111a]">
          <div className="flex items-center gap-3 text-sm text-slate-500">
            <LoaderCircle className="h-5 w-5 animate-spin text-cyan-500" />
            Cargando usuarios y programas...
          </div>
        </div>
      ) : (
        <div className="space-y-6">
          <div className="grid gap-6 xl:grid-cols-2">
            {/* PANEL 1: CREAR ESTUDIANTE */}
            <article className="rounded-2xl border border-cyan-500/10 bg-white p-6 dark:border-slate-800 dark:bg-[#0c111a]">
              <div className="flex items-center gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-cyan-500/10 text-cyan-500">
                  <UserPlus className="h-5 w-5" />
                </span>

                <div>
                  <h2 className="font-black text-slate-950 dark:text-white">
                    Crear estudiante
                  </h2>

                  <p className="mt-1 text-xs text-slate-500">
                    Registra una cuenta con rol Ejecutivo.
                  </p>
                </div>
              </div>

              <form onSubmit={createStudent} className="mt-6 space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Nombre">
                    <input
                      required
                      value={newUser.nombres}
                      onChange={(event) =>
                        setNewUser({
                          ...newUser,
                          nombres: event.target.value,
                        })
                      }
                      className={CONTROL_CLASS}
                      placeholder="Nombre"
                    />
                  </Field>

                  <Field label="Apellidos">
                    <input
                      required
                      value={newUser.apellidos}
                      onChange={(event) =>
                        setNewUser({
                          ...newUser,
                          apellidos: event.target.value,
                        })
                      }
                      className={CONTROL_CLASS}
                      placeholder="Apellidos"
                    />
                  </Field>
                </div>

                <Field label="Correo electrónico">
                  <input
                    required
                    type="email"
                    value={newUser.email}
                    onChange={(event) =>
                      setNewUser({
                        ...newUser,
                        email: event.target.value,
                      })
                    }
                    className={CONTROL_CLASS}
                    placeholder="Ingrese su correo"
                  />
                </Field>

                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Contraseña temporal">
                    <input
                      required
                      type="password"
                      minLength={8}
                      value={newUser.passw}
                      onChange={(event) =>
                        setNewUser({
                          ...newUser,
                          passw: event.target.value,
                        })
                      }
                      className={CONTROL_CLASS}
                      placeholder="Mínimo 8 caracteres"
                    />
                  </Field>

                  <Field label="Celular">
                    <input
                      type="tel"
                      value={newUser.telefono ?? ''}
                      onChange={(event) => {
                        setNewUser({
                          ...newUser,
                          telefono: event.target.value,
                        });
                      }}
                      className={CONTROL_CLASS}
                      placeholder="Ingrese número"
                      maxLength={20}
                    />
                  </Field>
                </div>

                <button
                  type="submit"
                  disabled={isCreating}
                  className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white transition hover:scale-[1.01] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isCreating ? (
                    <LoaderCircle className="h-4 w-4 animate-spin" />
                  ) : (
                    <UserPlus className="h-4 w-4" />
                  )}

                  {isCreating ? 'Creando estudiante...' : 'Crear estudiante'}
                </button>
              </form>
            </article>

            {/* PANEL 2: INSCRIBIR EN UN PROGRAMA */}
            <article className="rounded-2xl border border-indigo-500/10 bg-white p-6 dark:border-slate-800 dark:bg-[#0c111a]">
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-500">
                    <GraduationCap className="h-5 w-5" />
                  </span>

                  <div>
                    <h2 className="font-black text-slate-950 dark:text-white">
                      Inscribir en un programa
                    </h2>

                    <p className="mt-1 text-xs text-slate-500">
                      Selecciona un estudiante y un programa.
                    </p>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => void loadData()}
                  aria-label="Actualizar listas"
                  className="flex h-9 w-9 items-center justify-center rounded-xl border border-slate-200 text-slate-500 hover:text-cyan-500 dark:border-slate-800"
                >
                  <RefreshCw className="h-4 w-4" />
                </button>
              </div>

              <form onSubmit={enrollStudent} className="mt-6 space-y-4">
                <Field label="Estudiante">
                  <select
                    required
                    value={selectedUserId}
                    onChange={(event) => setSelectedUserId(event.target.value)}
                    className={CONTROL_CLASS}
                  >
                    <option value="">Selecciona un estudiante</option>

                    {students.map((student) => (
                      <option key={student.idUser} value={student.idUser}>
                        {student.nombres} {student.apellidos}
                      </option>
                    ))}
                  </select>
                </Field>

                <Field label="Programa académico">
                  <select
                    required
                    value={selectedProgramId}
                    onChange={(event) =>
                      setSelectedProgramId(event.target.value)
                    }
                    className={CONTROL_CLASS}
                  >
                    <option value="">Selecciona un programa</option>

                    {programs.map((program) => (
                      <option
                        key={program.idPrograma}
                        value={program.idPrograma}
                      >
                        {program.tituloPrograma}
                      </option>
                    ))}
                  </select>
                </Field>

                {/* Docente titular asignado según el curso seleccionado */}
                {selectedEnrollProgram && (
                  <div className="rounded-xl border border-indigo-500/20 bg-indigo-500/5 p-3 text-xs text-indigo-700 dark:text-indigo-300">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <GraduationCap className="h-4 w-4 text-indigo-500 shrink-0" />
                        <span>
                          Docente del curso:{' '}
                          <strong>
                            {selectedEnrollProgram.nombreInstructor ||
                              'Docente Titular Asignado'}
                          </strong>
                        </span>
                      </div>
                      <span className="rounded bg-indigo-500/10 px-2 py-0.5 font-mono text-[10px] font-bold text-indigo-600 dark:text-indigo-400">
                        Asignación automática
                      </span>
                    </div>
                  </div>
                )}

                <div className="grid grid-cols-2 gap-3">
                  <InfoCard
                    icon={<Users className="h-4 w-4" />}
                    label="Estudiantes"
                    value={String(students.length)}
                  />

                  <InfoCard
                    icon={<GraduationCap className="h-4 w-4" />}
                    label="Programas"
                    value={String(programs.length)}
                  />
                </div>

                {students.length === 0 && (
                  <p className="rounded-xl border border-amber-500/20 bg-amber-500/10 p-3 text-xs text-amber-700 dark:text-amber-300">
                    Primero crea un estudiante para poder realizar la inscripción.
                  </p>
                )}

                {programs.length === 0 && (
                  <p className="rounded-xl border border-amber-500/20 bg-amber-500/10 p-3 text-xs text-amber-700 dark:text-amber-300">
                    No hay programas disponibles. Debes registrar un programa antes de inscribir estudiantes.
                  </p>
                )}

                <button
                  type="submit"
                  disabled={
                    isEnrolling ||
                    students.length === 0 ||
                    programs.length === 0
                  }
                  className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 text-xs font-bold text-white transition hover:scale-[1.01] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isEnrolling ? (
                    <LoaderCircle className="h-4 w-4 animate-spin" />
                  ) : (
                    <GraduationCap className="h-4 w-4" />
                  )}

                  {isEnrolling
                    ? 'Procesando inscripción...'
                    : 'Inscribir estudiante'}
                </button>
              </form>
            </article>
          </div>

          {/* PANEL 3: SECCIÓN PARA ASIGNAR AL PROFESOR DE SU ALUMNO DE ACUERDO A QUÉ CURSOS ENSEÑA */}
          <article className="rounded-2xl border border-cyan-500/15 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-[#0c111a]">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between border-b border-slate-100 pb-4 dark:border-slate-800">
              <div className="flex items-center gap-3">
                <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-cyan-500/15 to-indigo-600/15 text-cyan-600 dark:text-cyan-400">
                  <UserCheck className="h-5 w-5" />
                </span>

                <div>
                  <h2 className="text-base font-black text-slate-950 dark:text-white sm:text-lg">
                    Asignar Profesor a Alumno
                  </h2>
                  <p className="text-xs text-slate-500">
                    Asigna al profesor titular de tu alumno según los cursos o programas que enseña.
                  </p>
                </div>
              </div>

              <span className="inline-flex w-fit items-center gap-1.5 rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1 font-mono text-[11px] font-bold text-cyan-700 dark:text-cyan-300">
                <BookOpen className="h-3.5 w-3.5" />
                {teachersWithCourses.length} Profesores disponibles
              </span>
            </div>

            {/* Formulario de asignación docente */}
            <form onSubmit={handleCreateAssignment} className="mt-5 space-y-4">
              <div className="grid gap-4 md:grid-cols-3">
                {/* 1. Selección de Estudiante (Solo Nombre y Apellidos) */}
                <Field label="Alumno / Estudiante">
                  <select
                    required
                    value={assignStudentId}
                    onChange={(e) => setAssignStudentId(e.target.value)}
                    className={CONTROL_CLASS}
                  >
                    <option value="">Selecciona al alumno</option>
                    {students.map((student) => (
                      <option key={student.idUser} value={student.idUser}>
                        {student.nombres} {student.apellidos}
                      </option>
                    ))}
                  </select>
                </Field>

                {/* 2. Selección de Curso */}
                <Field label="Curso / Programa que cursará">
                  <select
                    required
                    value={assignProgramId}
                    onChange={(e) => handleAssignProgramChange(e.target.value)}
                    className={CONTROL_CLASS}
                  >
                    <option value="">Selecciona el curso</option>
                    {programs.map((prog) => (
                      <option key={prog.idPrograma} value={prog.idPrograma}>
                        {prog.tituloPrograma}
                      </option>
                    ))}
                  </select>
                </Field>

                {/* 3. Selección de Profesor de acuerdo a los cursos que enseña */}
                <Field label="Profesor titular asignado">
                  <select
                    required
                    value={assignTeacherName}
                    onChange={(e) => handleAssignTeacherChange(e.target.value)}
                    className={CONTROL_CLASS}
                  >
                    <option value="">Selecciona al profesor</option>
                    {teachersWithCourses.map((t) => (
                      <option key={t.nombre} value={t.nombre}>
                        {t.nombre}
                        {t.cursos.length > 0
                          ? ` (Enseña: ${t.cursos.join(', ')})`
                          : ''}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>

              {/* Información interactiva del docente y cursos que enseña */}
              {assignTeacherName && (
                <div className="rounded-xl border border-cyan-500/20 bg-cyan-500/5 p-3.5 text-xs text-cyan-800 dark:text-cyan-300">
                  <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <span className="font-bold">Profesor: {assignTeacherName}</span>
                      <p className="mt-0.5 text-slate-600 dark:text-slate-400">
                        {(() => {
                          const t = teachersWithCourses.find(
                            (item) => item.nombre === assignTeacherName,
                          );
                          if (!t || t.cursos.length === 0) {
                            return 'Asignado como docente titular para este programa.';
                          }
                          return `Cursos que enseña: ${t.cursos.join(' • ')}`;
                        })()}
                      </p>
                    </div>

                    <button
                      type="submit"
                      className="mt-2 sm:mt-0 flex h-9 items-center justify-center gap-1.5 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-4 text-xs font-bold text-white shadow-sm transition hover:scale-[1.02] active:scale-95"
                    >
                      <UserCheck className="h-3.5 w-3.5" />
                      <span>Asignar Profesor</span>
                    </button>
                  </div>
                </div>
              )}

              {!assignTeacherName && (
                <div className="flex justify-end pt-1">
                  <button
                    type="submit"
                    disabled={
                      !assignStudentId ||
                      !assignProgramId ||
                      students.length === 0 ||
                      programs.length === 0
                    }
                    className="flex h-10 items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-500 to-indigo-600 px-5 text-xs font-bold text-white shadow-sm transition hover:scale-[1.02] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <UserCheck className="h-4 w-4" />
                    <span>Asignar Profesor</span>
                  </button>
                </div>
              )}
            </form>

            {/* Tabla / Lista de Asignaciones Alumno - Profesor */}
            <div className="mt-6 border-t border-slate-100 pt-5 dark:border-slate-800">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">
                  Asignaciones Alumno · Profesor por Curso ({assignments.length})
                </h3>
              </div>

              {assignments.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-200 p-6 text-center text-xs text-slate-400 dark:border-slate-800">
                  No hay asignaciones docentes registradas todavía. Asigna a un profesor en el formulario superior.
                </div>
              ) : (
                <div className="overflow-x-auto rounded-xl border border-slate-100 dark:border-slate-800">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-50 text-[10px] uppercase tracking-wider text-slate-500 dark:bg-slate-900/60">
                      <tr>
                        <th className="px-4 py-3">Alumno</th>
                        <th className="px-4 py-3">Curso / Programa</th>
                        <th className="px-4 py-3">Profesor Asignado</th>
                        <th className="px-4 py-3">Fecha</th>
                        <th className="px-4 py-3 text-right">Acción</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                      {assignments.map((asig) => (
                        <tr
                          key={asig.id}
                          className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30 transition-colors"
                        >
                          <td className="px-4 py-3 font-bold text-slate-900 dark:text-white">
                            {asig.studentName}
                          </td>
                          <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                            {asig.programTitle}
                          </td>
                          <td className="px-4 py-3">
                            <span className="inline-flex items-center gap-1 rounded-md border border-cyan-500/20 bg-cyan-500/10 px-2 py-0.5 text-[11px] font-bold text-cyan-700 dark:text-cyan-300">
                              <GraduationCap className="h-3.5 w-3.5 text-cyan-600 dark:text-cyan-400" />
                              {asig.teacherName}
                            </span>
                          </td>
                          <td className="px-4 py-3 font-mono text-[11px] text-slate-400">
                            {asig.assignedAt}
                          </td>
                          <td className="px-4 py-3 text-right">
                            <button
                              type="button"
                              onClick={() => handleDeleteAssignment(asig.id)}
                              title="Eliminar asignación"
                              className="rounded-lg p-1.5 text-slate-400 hover:bg-rose-500/10 hover:text-rose-500 transition-colors"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </article>
        </div>
      )}

      <p className="text-xs leading-5 text-slate-500">
        El panel administrativo permite gestionar las inscripciones académicas y las asignaciones de profesores titulares a cada estudiante de acuerdo a las mallas y cursos que imparten.
      </p>
    </section>
  );
};

interface FieldProps {
  label: string;
  children: React.ReactNode;
}

const Field: React.FC<FieldProps> = ({ label, children }) => (
  <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
    <span className="mb-1.5 block">{label}</span>
    {children}
  </label>
);

interface InfoCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
}

const InfoCard: React.FC<InfoCardProps> = ({ icon, label, value }) => (
  <div className="rounded-xl bg-slate-50 p-3 dark:bg-slate-900/60">
    <div className="flex items-center gap-2 text-cyan-500">
      {icon}
      <span className="text-[10px] text-slate-500">{label}</span>
    </div>

    <p className="mt-2 font-mono text-xl font-black text-slate-950 dark:text-white">
      {value}
    </p>
  </div>
);
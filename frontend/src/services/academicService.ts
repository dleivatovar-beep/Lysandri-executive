import {
  MoodleCourseVerification,
  EstudianteInscritoResponse,
  InscripcionRequest,
  InscripcionResponse,
  LeccionRequest,
  LeccionResponse,
  ProgramaResponse,
  ProgresoPorcentajeResponse,
  ProgresoResponse,
  UsuarioRequest,
  UsuarioResponse,
} from '../types';

import { apiClient } from './api';
import { getCourseCover } from './courseCatalog';

// Mantiene compatibilidad con módulos administrativos que lean la lista local
export const getCustomUsers = (): UsuarioResponse[] => [];
export const saveCustomUsers = (_users: UsuarioResponse[]) => {};
export const getCustomPrograms = (): ProgramaResponse[] => [];
export const saveCustomPrograms = (_programs: ProgramaResponse[]) => {};

/**
 * Normaliza un objeto de programa proveniente del backend o de contratos históricos,
 * garantizando compatibilidad bidireccional entre titulo / tituloPrograma,
 * instructorNombre / nombreInstructor, coverUrl / imagenPortadaUrl, duracionHoras / duracionPrograma,
 * preservando moneda, precio y vinculación a Moodle sin sobrescrituras accidentales.
 */
export function normalizeProgram(raw: any): ProgramaResponse {
  if (!raw) return raw;
  const titulo = raw.titulo ?? raw.tituloPrograma ?? '';
  const instructor = raw.instructorNombre ?? raw.nombreInstructor ?? '';
  const duracion = raw.duracionPrograma ?? (raw.duracionHoras ? `${raw.duracionHoras} Horas` : '6 Semanas');
  const coverUrl = raw.imagenPortadaUrl || raw.coverUrl || getCourseCover(titulo) || '';
  const descripcion = raw.descripcionCorta ?? raw.descripcionDetallada ?? raw.descripcion ?? '';
  const nivel = raw.nivel ?? raw.level ?? 'ENTERPRISE';

  return {
    ...raw,
    idPrograma: raw.idPrograma ?? raw.id,
    titulo,
    tituloPrograma: titulo,
    instructor,
    instructorNombre: instructor,
    nombreInstructor: instructor,
    duracion,
    duracionHoras: raw.duracionHoras,
    duracionPrograma: duracion,
    imagenPortadaUrl: coverUrl,
    coverUrl,
    descripcion,
    descripcionCorta: raw.descripcionCorta ?? descripcion,
    descripcionDetallada: raw.descripcionDetallada ?? descripcion,
    nivel,
    level: nivel,
    precio: raw.precio !== undefined && raw.precio !== null ? Number(raw.precio) : 0,
    moneda: raw.moneda || 'USD',
    activo: Boolean(raw.activo),
    precioAprobado: Boolean(raw.precioAprobado),
    esGratuito: Boolean(raw.esGratuito),
    moodleCourseId: raw.moodleCourseId !== undefined ? raw.moodleCourseId : null,
    moodleCourseNombre: raw.moodleCourseNombre,
    moodleVerificado: Boolean(raw.moodleVerificado),
    moodleEstadoVerificacion: raw.moodleEstadoVerificacion,
  };
}

export const academicService = {
  normalizeProgram,

  /**
   * Crea un nuevo usuario en el backend. Confirma únicamente con 2xx del servidor.
   */
  async createUser(data: UsuarioRequest): Promise<UsuarioResponse> {
    const response = await apiClient.post<UsuarioResponse>('/usuarios', data);
    return response.data;
  },

  /**
   * Actualiza datos personales de un usuario en el backend (nombres, apellidos, email, teléfono, passw).
   * No envía rol ni estado activo para respetar la auditoría y restricciones del backend.
   */
  async updateUser(
    idUser: number,
    data: Partial<UsuarioRequest>,
  ): Promise<UsuarioResponse> {
    const payload: Record<string, any> = {};
    if (data.nombres !== undefined) payload.nombres = data.nombres;
    if (data.apellidos !== undefined) payload.apellidos = data.apellidos;
    if (data.email !== undefined) payload.email = data.email;
    if (data.telefono !== undefined) payload.telefono = data.telefono;
    if (data.passw !== undefined && data.passw !== '') payload.passw = data.passw;

    const response = await apiClient.put<UsuarioResponse>(`/usuarios/${idUser}`, payload);
    return response.data;
  },

  /**
   * Actualiza el rol de un usuario mediante el endpoint administrativo auditado.
   */
  async updateUserRole(
    idUser: number,
    rol: string,
    motivo?: string,
  ): Promise<UsuarioResponse> {
    const response = await apiClient.patch<UsuarioResponse>(
      `/usuarios/${idUser}/rol`,
      null,
      {
        params: {
          rol,
          motivo: motivo || 'Actualización administrativa de rol',
        },
      }
    );
    return response.data;
  },

  /**
   * Desactiva una cuenta de usuario mediante la operación administrativa auditada.
   */
  async deactivateUser(
    idUser: number,
    motivo?: string,
  ): Promise<UsuarioResponse> {
    const response = await apiClient.patch<UsuarioResponse>(
      `/usuarios/${idUser}/desactivar`,
      null,
      {
        params: {
          motivo: motivo || 'Desactivación administrativa de cuenta',
        },
      }
    );
    return response.data;
  },

  /**
   * Elimina/desactiva un usuario en el backend.
   */
  async deleteUser(idUser: number, motivo?: string): Promise<void> {
    await apiClient.delete(`/usuarios/${idUser}`, {
      params: motivo ? { motivo } : {},
    });
  },

  /**
   * Obtiene todos los usuarios directamente desde el backend.
   */
  async getUsers(): Promise<UsuarioResponse[]> {
    const response = await apiClient.get<UsuarioResponse[]>('/usuarios');
    return response.data;
  },

  /**
   * Obtiene la lista oficial de programas académicos comerciales desde el backend.
   */
  async getPrograms(): Promise<ProgramaResponse[]> {
    const response = await apiClient.get<ProgramaResponse[]>('/programas');
    return (response.data || []).map(normalizeProgram);
  },

  /**
   * Obtiene el detalle de un programa específico por su ID oficial.
   */
  async getProgram(id: number): Promise<ProgramaResponse> {
    const response = await apiClient.get<ProgramaResponse>(`/programas/id/${id}`);
    return normalizeProgram(response.data);
  },

  /**
   * Obtiene todos los programas (incluidos borradores e inactivos) para gestión administrativa.
   */
  async getAllProgramsAdmin(): Promise<ProgramaResponse[]> {
    const response = await apiClient.get<ProgramaResponse[]>('/programas/admin/todos');
    return (response.data || []).map(normalizeProgram);
  },

  /**
   * Consulta el inventario de programas y vinculaciones a Moodle LMS (solo lectura).
   */
  async getMoodleInventory(): Promise<ProgramaResponse[]> {
    const response = await apiClient.get<ProgramaResponse[]>('/programas/admin/inventario-moodle');
    return (response.data || []).map(normalizeProgram);
  },

  /**
   * Consulta en Moodle LMS la existencia y datos de un curso por ID.
   */
  async verifyMoodleCourse(moodleCourseId: number): Promise<MoodleCourseVerification> {
    const response = await apiClient.post<MoodleCourseVerification>(
      `/programas/admin/verificar-curso-moodle?moodleCourseId=${moodleCourseId}`
    );
    return response.data;
  },

  /**
   * Verifica y actualiza el estado Moodle de un programa existente en base de datos.
   */
  async verifyProgramMoodle(programId: number): Promise<ProgramaResponse> {
    const response = await apiClient.post<ProgramaResponse>(
      `/programas/admin/${programId}/verificar-moodle`
    );
    return normalizeProgram(response.data);
  },

  /**
   * Guarda o actualiza un programa académico en el backend (solo personal autorizado).
   * Conserva moneda, precio y vinculación Moodle existentes sin aplicar valores predeterminados accidentales.
   */
  async saveProgram(programData: Partial<ProgramaResponse>): Promise<ProgramaResponse> {
    const payload: Record<string, any> = {};

    if (programData.titulo !== undefined || programData.tituloPrograma !== undefined) {
      payload.titulo = programData.titulo || programData.tituloPrograma;
    }
    if (programData.slug !== undefined) payload.slug = programData.slug;
    if (programData.subtitulo !== undefined) payload.subtitulo = programData.subtitulo;
    if (programData.descripcionCorta !== undefined || programData.descripcion !== undefined) {
      payload.descripcionCorta = programData.descripcionCorta || programData.descripcion;
    }
    if (programData.descripcionDetallada !== undefined || programData.descripcion !== undefined) {
      payload.descripcionDetallada = programData.descripcionDetallada || programData.descripcion;
    }
    if (programData.precio !== undefined) payload.precio = Number(programData.precio);
    if (programData.moneda !== undefined) payload.moneda = programData.moneda;
    if (programData.imagenPortadaUrl !== undefined || programData.coverUrl !== undefined) {
      payload.imagenPortadaUrl = programData.imagenPortadaUrl || programData.coverUrl;
    }
    if (programData.instructorNombre !== undefined || programData.nombreInstructor !== undefined) {
      payload.instructorNombre = programData.instructorNombre || programData.nombreInstructor;
    }
    if (programData.nivel !== undefined || programData.level !== undefined) {
      payload.nivel = programData.nivel || programData.level;
    }
    if (programData.duracionHoras !== undefined) payload.duracionHoras = programData.duracionHoras;
    if (programData.duracionPrograma !== undefined) payload.duracionPrograma = programData.duracionPrograma;
    if (programData.activo !== undefined) payload.activo = programData.activo;
    if (programData.precioAprobado !== undefined) payload.precioAprobado = programData.precioAprobado;
    if (programData.esGratuito !== undefined) payload.esGratuito = programData.esGratuito;
    if (programData.moodleCourseId !== undefined) payload.moodleCourseId = programData.moodleCourseId;
    if (programData.desvincularMoodle !== undefined) payload.desvincularMoodle = programData.desvincularMoodle;

    if (programData.idPrograma) {
      const response = await apiClient.put<ProgramaResponse>(
        `/programas/${programData.idPrograma}`,
        payload
      );
      return normalizeProgram(response.data);
    } else {
      const response = await apiClient.post<ProgramaResponse>(
        '/programas',
        payload
      );
      return normalizeProgram(response.data);
    }
  },

  /**
   * Desactiva un programa académico en el backend.
   */
  async deleteProgram(programId: number): Promise<void> {
    await apiClient.delete(`/programas/${programId}`);
  },

  /**
   * Desvincula voluntariamente un programa académico de cualquier curso en Moodle LMS y lo desactiva.
   */
  async desvincularCursoMoodle(programId: number): Promise<ProgramaResponse> {
    const response = await apiClient.post<ProgramaResponse>(
      `/programas/admin/${programId}/desvincular-moodle`
    );
    return normalizeProgram(response.data);
  },

  /**
   * Rutas académicas planificadas para sincronización directa con Moodle LMS.
   */
  async getInstructorPrograms(): Promise<ProgramaResponse[]> {
    const response = await apiClient.get<ProgramaResponse[]>('/profesor/mis-cursos');
    return (response.data || []).map(normalizeProgram);
  },

  async getInstructorStudents(programId: number): Promise<EstudianteInscritoResponse[]> {
    const response = await apiClient.get<EstudianteInscritoResponse[]>(
      `/profesor/programas/${programId}/estudiantes`
    );
    return response.data;
  },

  async enroll(data: InscripcionRequest): Promise<InscripcionResponse> {
    const response = await apiClient.post<InscripcionResponse>('/inscripciones', data);
    return response.data;
  },

  async getMyCourses(): Promise<InscripcionResponse[]> {
    const response = await apiClient.get<InscripcionResponse[]>('/estudiante/mis-cursos');
    return response.data;
  },

  async getLessons(programId: number): Promise<LeccionResponse[]> {
    const response = await apiClient.get<LeccionResponse[]>(
      `/programas/${programId}/lecciones`
    );
    return response.data;
  },

  async createLesson(
    programId: number,
    data: LeccionRequest
  ): Promise<LeccionResponse> {
    const response = await apiClient.post<LeccionResponse>(
      `/programas/${programId}/lecciones`,
      data
    );
    return response.data;
  },

  async updateLesson(
    lessonId: number,
    data: LeccionRequest
  ): Promise<LeccionResponse> {
    const response = await apiClient.put<LeccionResponse>(
      `/lecciones/${lessonId}`,
      data
    );
    return response.data;
  },

  async deleteLesson(lessonId: number): Promise<void> {
    await apiClient.delete(`/lecciones/${lessonId}`);
  },

  async completeLesson(lessonId: number): Promise<ProgresoResponse> {
    const response = await apiClient.post<ProgresoResponse>(
      `/lecciones/${lessonId}/completar`
    );
    return response.data;
  },

  async getProgress(programId: number): Promise<ProgresoPorcentajeResponse> {
    const response = await apiClient.get<ProgresoPorcentajeResponse>(
      `/programas/${programId}/progreso`
    );
    return response.data;
  },
};

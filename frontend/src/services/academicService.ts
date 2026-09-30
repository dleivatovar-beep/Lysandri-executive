import {
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

export const academicService = {
  async createUser(
    data: UsuarioRequest,
  ): Promise<UsuarioResponse> {
    const response =
      await apiClient.post<UsuarioResponse>(
        '/usuarios',
        data,
      );

    return response.data;
  },

  async getUsers(): Promise<UsuarioResponse[]> {
    const response =
      await apiClient.get<UsuarioResponse[]>(
        '/usuarios',
      );

    return response.data;
  },

  async getPrograms(): Promise<ProgramaResponse[]> {
    const response =
      await apiClient.get<ProgramaResponse[]>(
        '/programas',
      );

    return response.data;
  },

  async getProgram(
    id: number,
  ): Promise<ProgramaResponse> {
    const response =
      await apiClient.get<ProgramaResponse>(
        `/programas/${id}`,
      );

    return response.data;
  },
async getInstructorPrograms(): Promise<
  ProgramaResponse[]
> {
  const response =
    await apiClient.get<
      ProgramaResponse[]
    >('/profesor/mis-cursos');

  return response.data;
},

async getInstructorStudents(
  programId: number,
): Promise<EstudianteInscritoResponse[]> {
  const response =
    await apiClient.get<
      EstudianteInscritoResponse[]
    >(
      `/profesor/programas/${programId}/estudiantes`,
    );

  return response.data;
},
  async enroll(
    data: InscripcionRequest,
  ): Promise<InscripcionResponse> {
    const response =
      await apiClient.post<InscripcionResponse>(
        '/inscripciones',
        data,
      );

    return response.data;
  },

  async getMyCourses(): Promise<
    InscripcionResponse[]
  > {
    const response =
      await apiClient.get<
        InscripcionResponse[]
      >('/estudiante/mis-cursos');

    return response.data;
  },

  async getLessons(
    programId: number,
  ): Promise<LeccionResponse[]> {
    const response =
      await apiClient.get<
        LeccionResponse[]
      >(
        `/programas/${programId}/lecciones`,
      );

    return response.data;
  },

  async createLesson(
    programId: number,
    data: LeccionRequest,
  ): Promise<LeccionResponse> {
    const response =
      await apiClient.post<LeccionResponse>(
        `/programas/${programId}/lecciones`,
        data,
      );

    return response.data;
  },

  async updateLesson(
    lessonId: number,
    data: LeccionRequest,
  ): Promise<LeccionResponse> {
    const response =
      await apiClient.put<LeccionResponse>(
        `/lecciones/${lessonId}`,
        data,
      );

    return response.data;
  },

  async deleteLesson(
    lessonId: number,
  ): Promise<void> {
    await apiClient.delete(
      `/lecciones/${lessonId}`,
    );
  },

  async completeLesson(
    lessonId: number,
  ): Promise<ProgresoResponse> {
    const response =
      await apiClient.post<ProgresoResponse>(
        `/lecciones/${lessonId}/completar`,
      );

    return response.data;
  },

  async getProgress(
    programId: number,
  ): Promise<ProgresoPorcentajeResponse> {
    const response =
      await apiClient.get<ProgresoPorcentajeResponse>(
        `/programas/${programId}/progreso`,
      );

    return response.data;
  },
};
import {
  ProgramaResponse,
  UsuarioResponse,
} from '../types';

import { apiClient } from './api';

export const academicService = {
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
};
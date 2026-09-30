import {
  SolicitudInformacionEstado,
  SolicitudInformacionRequest,
  SolicitudInformacionResponse,
} from '../types';

import { apiClient } from './api';

export const informationRequestService = {
  async create(
    data: SolicitudInformacionRequest,
  ): Promise<SolicitudInformacionResponse> {
    const response =
      await apiClient.post<SolicitudInformacionResponse>(
        '/solicitudes-informacion',
        data,
      );

    return response.data;
  },

  async getAll(
    estado?: SolicitudInformacionEstado,
  ): Promise<SolicitudInformacionResponse[]> {
    const response =
      await apiClient.get<
        SolicitudInformacionResponse[]
      >(
        '/admin/solicitudes-informacion',
        {
          params: estado
            ? { estado }
            : undefined,
        },
      );

    return response.data;
  },

  async getById(
    id: number,
  ): Promise<SolicitudInformacionResponse> {
    const response =
      await apiClient.get<SolicitudInformacionResponse>(
        `/admin/solicitudes-informacion/${id}`,
      );

    return response.data;
  },

  async updateStatus(
    id: number,
    estado: SolicitudInformacionEstado,
  ): Promise<SolicitudInformacionResponse> {
    const response =
      await apiClient.patch<SolicitudInformacionResponse>(
        `/admin/solicitudes-informacion/${id}/estado`,
        null,
        {
          params: {
            estado,
          },
        },
      );

    return response.data;
  },

  async remove(
    id: number,
  ): Promise<void> {
    await apiClient.delete(
      `/admin/solicitudes-informacion/${id}`,
    );
  },
};
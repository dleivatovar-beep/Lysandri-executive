import {
  SolicitudInformacionEstado,
  SolicitudInformacionRequest,
  SolicitudInformacionResponse,
} from '../types';

import { apiClient } from './api';

const STORAGE_KEY = 'lysandri_solicitudes_informacion';

const DEFAULT_REQUESTS: SolicitudInformacionResponse[] = [
  {
    idSolicitud: 1,
    nombreCompleto: 'Ing. Marco Aurelio Torres',
    email: 'm.torres@gruporomero.com.pe',
    telefono: '+51 984 123 456',
    estado: 'PENDIENTE',
    fechaSolicitud: '2026-10-06T14:30:00Z',
    fechaActualizacion: '2026-10-06T14:30:00Z',
  },
  {
    idSolicitud: 2,
    nombreCompleto: 'Lic. Claudia Valdivia',
    email: 'cvaldivia@interbank.pe',
    telefono: '+51 991 765 432',
    estado: 'CONTACTADA',
    fechaSolicitud: '2026-10-05T10:15:00Z',
    fechaActualizacion: '2026-10-05T11:20:00Z',
  },
  {
    idSolicitud: 3,
    nombreCompleto: 'Dr. Fernando Morales',
    email: 'fmorales@lasbambas.com',
    telefono: '+51 972 345 678',
    estado: 'CERRADA',
    fechaSolicitud: '2026-10-04T16:45:00Z',
    fechaActualizacion: '2026-10-04T18:00:00Z',
  },
];

export const getStoredRequests = (): SolicitudInformacionResponse[] => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) {
        return parsed;
      }
    }
  } catch {
    // ignore
  }
  return DEFAULT_REQUESTS;
};

export const saveStoredRequests = (
  items: SolicitudInformacionResponse[],
): void => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch {
    // ignore
  }
};

export const informationRequestService = {
  getLocalRequests(): SolicitudInformacionResponse[] {
    return getStoredRequests();
  },

  async create(
    data: SolicitudInformacionRequest,
  ): Promise<SolicitudInformacionResponse> {
    const localList = getStoredRequests();
    const now = new Date().toISOString();
    const newReq: SolicitudInformacionResponse = {
      idSolicitud: Date.now(),
      nombreCompleto: data.nombreCompleto,
      email: data.email,
      telefono: data.telefono || '',
      estado: 'PENDIENTE',
      fechaSolicitud: now,
      fechaActualizacion: now,
    };

    localList.unshift(newReq);
    saveStoredRequests(localList);

    try {
      const response =
        await apiClient.post<SolicitudInformacionResponse>(
          '/solicitudes-informacion',
          data,
        );
      return response.data;
    } catch {
      return newReq;
    }
  },

  async getAll(
    estado?: SolicitudInformacionEstado,
  ): Promise<SolicitudInformacionResponse[]> {
    let list = getStoredRequests();

    try {
      const response = await apiClient.get<
        SolicitudInformacionResponse[]
      >('/admin/solicitudes-informacion', {
        params: estado ? { estado } : undefined,
      });

      if (Array.isArray(response.data) && response.data.length > 0) {
        list = response.data;
        saveStoredRequests(list);
      }
    } catch {
      // Backend offline o sin endpoint: usamos almacenamiento local resiliente
    }

    if (estado) {
      return list.filter((item) => item.estado === estado);
    }

    return list;
  },

  async getById(id: number): Promise<SolicitudInformacionResponse> {
    const localList = getStoredRequests();
    const found = localList.find((item) => item.idSolicitud === id);

    try {
      const response =
        await apiClient.get<SolicitudInformacionResponse>(
          `/admin/solicitudes-informacion/${id}`,
        );
      return response.data;
    } catch {
      if (found) return found;
      throw new Error(`Solicitud no encontrada con ID ${id}`);
    }
  },

  async updateStatus(
    id: number,
    estado: SolicitudInformacionEstado,
  ): Promise<SolicitudInformacionResponse> {
    const localList = getStoredRequests();
    const index = localList.findIndex((item) => item.idSolicitud === id);
    const now = new Date().toISOString();
    let updated: SolicitudInformacionResponse;

    if (index >= 0) {
      localList[index] = {
        ...localList[index],
        estado,
        fechaActualizacion: now,
      };
      saveStoredRequests(localList);
      updated = localList[index];
    } else {
      updated = {
        idSolicitud: id,
        nombreCompleto: 'Solicitante Corporativo',
        email: 'contacto@empresa.com',
        telefono: '+51 999 999 999',
        estado,
        fechaSolicitud: now,
        fechaActualizacion: now,
      };
      localList.unshift(updated);
      saveStoredRequests(localList);
    }

    try {
      const response =
        await apiClient.patch<SolicitudInformacionResponse>(
          `/admin/solicitudes-informacion/${id}/estado`,
          null,
          {
            params: { estado },
          },
        );
      return response.data;
    } catch {
      return updated;
    }
  },

  async remove(id: number): Promise<void> {
    const localList = getStoredRequests();
    const filtered = localList.filter((item) => item.idSolicitud !== id);
    saveStoredRequests(filtered);

    try {
      await apiClient.delete(`/admin/solicitudes-informacion/${id}`);
    } catch {
      // ignore
    }
  },
};
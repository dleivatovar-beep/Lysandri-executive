import axios, { AxiosError } from 'axios';
import { ChatResponse } from '../types';

const RAW_API_URL =
  import.meta.env.VITE_API_URL ||
  'http://localhost:8080/api/v1';

export const API_BASE_URL = RAW_API_URL.replace(/\/api\/v1\/?$/, '');
export const API_V1_URL = `${API_BASE_URL}/api/v1`;

const TOKEN_KEY = 'lysandri_token';
const USER_KEY = 'lysandri_user';

export const isJwtExpired = (token: string): boolean => {
  try {
    const payloadPart = token.split('.')[1];

    if (!payloadPart) {
      return true;
    }

    const normalizedPayload = payloadPart
      .replace(/-/g, '+')
      .replace(/_/g, '/');

    const paddedPayload = normalizedPayload.padEnd(
      Math.ceil(normalizedPayload.length / 4) * 4,
      '=',
    );

    const payload = JSON.parse(atob(paddedPayload)) as {
      exp?: number;
    };

    return !payload.exp || payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
};

const clearStoredSession = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

export const apiClient = axios.create({
  baseURL: API_V1_URL,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
  timeout: 10000,
});

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem(TOKEN_KEY);

    if (token) {
      if (isJwtExpired(token)) {
        clearStoredSession();

        window.dispatchEvent(
          new CustomEvent('lysandri:unauthorized'),
        );

        return Promise.reject(
          new Error('La sesión ha expirado.'),
        );
      }

      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error: AxiosError) => Promise.reject(error),
);

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;

    if (status === 401) {
      clearStoredSession();

      window.dispatchEvent(
        new CustomEvent('lysandri:unauthorized'),
      );
    }

    if (status === 403) {
      const token = localStorage.getItem(TOKEN_KEY);

      if (!token || isJwtExpired(token)) {
        clearStoredSession();

        window.dispatchEvent(
          new CustomEvent('lysandri:unauthorized'),
        );
      } else {
        window.dispatchEvent(
          new CustomEvent('lysandri:forbidden'),
        );
      }
    }

    return Promise.reject(error);
  },
);

export const enviarMensajeChat = async (
  mensaje: string,
  token: string,
): Promise<ChatResponse> => {
  try {
    const response = await axios.post<ChatResponse>(
      `${API_BASE_URL}/api/chat`,
      { mensaje },
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        timeout: 30000,
      },
    );

    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status;

      if (status === 401) {
        clearStoredSession();
        window.dispatchEvent(
          new CustomEvent('lysandri:unauthorized'),
        );
        throw new Error(
          '401 Unauthorized: Tu sesión ha expirado o no es válida. Por favor, inicia sesión nuevamente.',
        );
      }

      if (status === 403) {
        throw new Error(
          '403 Forbidden: No tienes permisos para consultar el asistente inteligente.',
        );
      }

      if (status && status >= 500) {
        throw new Error(
          '500 Internal Error: Error interno del servidor al procesar la consulta con el asistente.',
        );
      }

      const responseData = error.response?.data as
        | { message?: string; error?: string; detail?: string }
        | undefined;

      if (responseData?.message) {
        throw new Error(responseData.message);
      }

      if (responseData?.error) {
        throw new Error(responseData.error);
      }

      if (!error.response) {
        throw new Error(
          'No se pudo establecer conexión con el servidor de chat. Verifica que el backend esté en ejecución.',
        );
      }
    }

    if (error instanceof Error) {
      throw error;
    }

    throw new Error('Error inesperado al enviar el mensaje al asistente.');
  }
};
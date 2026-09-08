import axios, { AxiosError } from 'axios';
import { Category, ChatMessage, Playbook } from '../types';
import {
  MOCK_CATEGORIES,
  MOCK_CHAT_MESSAGES,
  MOCK_PLAYBOOKS,
} from './mockData';

const API_BASE_URL =
  import.meta.env.VITE_API_URL ||
  'http://localhost:8080/api/v1';

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

    const payload = JSON.parse(atob(normalizedPayload)) as {
      exp?: number;
    };

    if (!payload.exp) {
      return false;
    }

    return payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
};

const clearStoredSession = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
};

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
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

export const PlaybookService = {
  async getAll(): Promise<Playbook[]> {
    try {
      const response =
        await apiClient.get<Playbook[]>('/programas');

      return response.data;
    } catch {
      console.info(
        'No se pudieron cargar los programas del backend. Se usarán datos simulados.',
      );

      return MOCK_PLAYBOOKS;
    }
  },

  async getCategories(): Promise<Category[]> {
    return MOCK_CATEGORIES;
  },
};

export const ChatService = {
  async getMessages(
    sessionId: string,
  ): Promise<ChatMessage[]> {
    try {
      const response = await apiClient.get<ChatMessage[]>(
        `/chat/sessions/${sessionId}/messages`,
      );

      return response.data;
    } catch {
      return MOCK_CHAT_MESSAGES;
    }
  },

  async sendMessage(
    content: string,
    sessionId = 'session-executive-01',
  ): Promise<ChatMessage> {
    try {
      const response = await apiClient.post<ChatMessage>(
        '/chat/query',
        {
          content,
          sessionId,
        },
      );

      return response.data;
    } catch {
      return {
        id: `message-${Date.now()}`,
        sessionId,
        sender: 'ASSISTANT',
        content:
          'He recibido tu consulta. El servicio de inteligencia artificial no está disponible temporalmente, por lo que esta es una respuesta simulada.',
        timestamp: new Date().toLocaleTimeString([], {
          hour: '2-digit',
          minute: '2-digit',
        }),
        sources: [],
      };
    }
  },
};
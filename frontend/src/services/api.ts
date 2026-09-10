import axios, { AxiosError } from 'axios';

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
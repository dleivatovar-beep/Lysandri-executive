import axios from 'axios';
import {
  ApiErrorResponse,
  AuthResponse,
  LoginRequest,
  RegisterRequest,
} from '../types';
import { apiClient } from './api';

export const authService = {
  async login(
    credentials: LoginRequest,
  ): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>(
      '/auth/login',
      credentials,
    );

    return response.data;
  },

  async register(
    data: RegisterRequest,
  ): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>(
      '/auth/register',
      data,
    );

    return response.data;
  },
};

export const getApiErrorMessage = (
  error: unknown,
): string => {
  if (!axios.isAxiosError<ApiErrorResponse>(error)) {
    if (error instanceof Error) {
      return error.message;
    }

    return 'Ocurrió un error inesperado.';
  }

  if (!error.response) {
    return 'No se pudo conectar con el servidor. Verifica que el backend esté encendido.';
  }

  const responseData = error.response.data;

  if (responseData?.message) {
    return responseData.message;
  }

  if (responseData?.errors) {
    const firstMessage = Object.values(
      responseData.errors,
    )[0];

    if (firstMessage) {
      return firstMessage;
    }
  }

  if (error.response.status === 401) {
    return 'Correo o contraseña incorrectos.';
  }

  if (error.response.status === 403) {
    return 'No tienes permisos para realizar esta acción.';
  }

  if (error.response.status >= 500) {
    return 'El servidor presentó un problema. Inténtalo nuevamente.';
  }

  return 'No se pudo completar la solicitud.';
};
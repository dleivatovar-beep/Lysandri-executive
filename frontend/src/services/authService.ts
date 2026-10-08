import axios from 'axios';
import {
  ApiErrorResponse,
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UserRole,
} from '../types';
import { apiClient } from './api';

interface BackendAuthResponse {
  token: string;
  idUser: number;
  email: string;
  nombreCompleto: string;
  rol: UserRole | 'CLIENTE';
}

const toAuthResponse = (response: BackendAuthResponse): AuthResponse => ({
  token: response.token,
  id: response.idUser,
  nombre: response.nombreCompleto,
  email: response.email,
  rol: response.rol === 'CLIENTE' ? 'ESTUDIANTE' : response.rol,
});

export const authService = {
  async login(
    credentials: LoginRequest,
  ): Promise<AuthResponse> {
    const response = await apiClient.post<BackendAuthResponse>(
      '/auth/login',
      credentials,
    );

    return toAuthResponse(response.data);
  },

  async register(
    data: RegisterRequest,
  ): Promise<AuthResponse> {
    const response = await apiClient.post<BackendAuthResponse>(
      '/auth/register',
      {
        nombres: data.nombre,
        apellidos: data.apellidos,
        email: data.email,
        password: data.password,
        telefono: data.telefono,
      },
    );

    return toAuthResponse(response.data);
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

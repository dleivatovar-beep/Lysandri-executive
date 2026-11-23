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

export interface CondicionesRegistro {
  version: string;
  terminos: string;
  privacidad: string;
}

export type RegistroResultado =
  | { pendienteActivacion: true; mensaje: string }
  | { pendienteActivacion: false; sesion: AuthResponse };

export const authService = {
  async condicionesRegistro(signal: AbortSignal): Promise<CondicionesRegistro> {
    const response = await apiClient.get<CondicionesRegistro>('/auth/condiciones-registro', { signal });
    return response.data;
  },
  async cambiarPassword(actual: string, nueva: string): Promise<{ mensaje: string }> {
    const respuesta = await apiClient.post<{ mensaje: string }>('/cuenta/password', { actual, nueva });
    return respuesta.data;
  },
  async recuperarPassword(email: string, origen: string): Promise<{ mensaje: string }> {
    const response = await apiClient.post<{ mensaje: string }>('/auth/recuperar-contrasena', { email, origen });
    return response.data;
  },

  async restablecerPassword(token: string, password: string): Promise<{ mensaje: string }> {
    const response = await apiClient.post<{ mensaje: string }>('/auth/restablecer-contrasena', { token, password });
    return response.data;
  },

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
  ): Promise<RegistroResultado> {
    const response = await apiClient.post<Omit<BackendAuthResponse, 'token'> & {
      token: string | null;
      pendienteActivacion: boolean;
      mensaje?: string;
    }>(
      '/auth/register',
      {
        nombres: data.nombre,
        apellidos: data.apellidos,
        email: data.email,
        password: data.password,
        telefono: data.telefono,
        aceptaTerminos: data.aceptaTerminos,
        aceptaPrivacidad: data.aceptaPrivacidad,
        version: data.version,
      },
    );

    if (response.data.pendienteActivacion && !response.data.token) {
      return { pendienteActivacion: true, mensaje: response.data.mensaje || 'Verifica tu correo antes de iniciar sesión.' };
    }
    if (!response.data.pendienteActivacion && response.data.token && response.data.idUser && response.data.rol === 'CLIENTE') {
      return { pendienteActivacion: false, sesion: toAuthResponse({ ...response.data, token: response.data.token }) };
    }
    throw new Error('El servidor no confirmó una sesión válida de cliente. Intenta iniciar sesión con tu correo y contraseña.');
  },

  async registerInternal(data: {
    nombres: string;
    apellidos: string;
    email: string;
    password: string;
    telefono?: string;
    area: string;
  }): Promise<AuthResponse> {
    const response = await apiClient.post<BackendAuthResponse>(
      '/auth/register-interno',
      data,
    );
    return toAuthResponse(response.data);
  },

  async activarCuenta(
    token: string,
    password: string,
  ): Promise<AuthResponse> {
    const response = await apiClient.post<BackendAuthResponse>(
      '/auth/activar-cuenta',
      {
        token,
        password,
      },
    );

    return toAuthResponse(response.data);
  },

  async solicitarActivacion(
    email: string,
  ): Promise<{ mensaje: string }> {
    const response = await apiClient.post<{ mensaje: string }>(
      '/auth/solicitar-activacion',
      {
        email,
      },
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

  const fieldErrors = responseData?.details ?? responseData?.errors;
  if (fieldErrors) {
    const firstMessage = Object.values(
      fieldErrors,
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

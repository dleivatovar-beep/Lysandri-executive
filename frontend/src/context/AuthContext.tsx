import React, {
  createContext,
  ReactNode,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  AuthResponse,
  AuthUser,
  LoginRequest,
  RegisterRequest,
} from '../types';

import { isJwtExpired } from '../services/api';
import { authService } from '../services/authService';

const TOKEN_KEY = 'lysandri_token';
const USER_KEY = 'lysandri_user';

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<AuthUser>;
  register: (
    data: RegisterRequest,
  ) => Promise<AuthUser>;
  logout: () => void;
}

interface AuthProviderProps {
  children: ReactNode;
}

const AuthContext =
  createContext<AuthContextValue | null>(null);

const getStoredUser = (): AuthUser | null => {
  try {
    const storedUser = localStorage.getItem(USER_KEY);

    if (!storedUser) {
      return null;
    }

    return JSON.parse(storedUser) as AuthUser;
  } catch {
    localStorage.removeItem(USER_KEY);
    return null;
  }
};

export const AuthProvider: React.FC<
  AuthProviderProps
> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(
    null,
  );

  const [token, setToken] = useState<string | null>(
    null,
  );

  const [isLoading, setIsLoading] = useState(true);

  const clearSession = () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);

    setToken(null);
    setUser(null);
  };

  const saveSession = (
    response: AuthResponse,
  ): AuthUser => {
    const authenticatedUser: AuthUser = {
      id: response.id,
      nombre: response.nombre,
      email: response.email,
      rol: response.rol,
    };

    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(
      USER_KEY,
      JSON.stringify(authenticatedUser),
    );

    setToken(response.token);
    setUser(authenticatedUser);

    return authenticatedUser;
  };

  useEffect(() => {
    const storedToken =
      localStorage.getItem(TOKEN_KEY);

    const storedUser = getStoredUser();

    if (
      storedToken &&
      storedUser &&
      !isJwtExpired(storedToken)
    ) {
      setToken(storedToken);
      setUser(storedUser);
    } else {
      clearSession();
    }

    setIsLoading(false);
  }, []);

  useEffect(() => {
    const handleUnauthorized = () => {
      clearSession();
    };

    window.addEventListener(
      'lysandri:unauthorized',
      handleUnauthorized,
    );

    return () => {
      window.removeEventListener(
        'lysandri:unauthorized',
        handleUnauthorized,
      );
    };
  }, []);

  const login = async (
    data: LoginRequest,
  ): Promise<AuthUser> => {
    const response = await authService.login(data);

    return saveSession(response);
  };

  const register = async (
    data: RegisterRequest,
  ): Promise<AuthUser> => {
    const response = await authService.register(data);

    return saveSession(response);
  };

  const logout = () => {
    clearSession();
  };

  const contextValue = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isAuthenticated: Boolean(user && token),
      isLoading,
      login,
      register,
      logout,
    }),
    [user, token, isLoading],
  );

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextValue => {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error(
      'useAuth debe utilizarse dentro de AuthProvider.',
    );
  }

  return context;
};
import React from 'react';
import {
  Navigate,
  Outlet,
  useLocation,
} from 'react-router-dom';

import { useAuth } from '../context/AuthContext';
import { UserRole } from '../types';

interface RoleProtectedRouteProps {
  allowedRoles: UserRole[];
}

export const getRoleHomePath = (
  role: UserRole,
): string => {
  switch (role) {
    case 'ADMIN':
      return '/admin/dashboard';

    case 'INSTRUCTOR':
      return '/profesor/cursos';

    case 'ESTUDIANTE':
      return '/estudiante/mis-cursos';

    default:
      return '/';
  }
};

export const RoleProtectedRoute: React.FC<
  RoleProtectedRouteProps
> = ({ allowedRoles }) => {
  const location = useLocation();

  const {
    user,
    isAuthenticated,
    isLoading,
  } = useAuth();

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-[#06090f]">
        <div className="flex flex-col items-center gap-4">
          <div className="h-10 w-10 animate-spin rounded-full border-2 border-slate-300 border-t-cyan-500 dark:border-slate-800 dark:border-t-cyan-400" />

          <p className="font-mono text-xs text-slate-500">
            Verificando sesión...
          </p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname }}
      />
    );
  }

  if (!allowedRoles.includes(user.rol)) {
    return (
      <Navigate
        to={getRoleHomePath(user.rol)}
        replace
      />
    );
  }

  return <Outlet />;
};
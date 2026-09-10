import React, {
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  Bot,
  CheckCircle2,
  CircleAlert,
  Database,
} from 'lucide-react';

import {
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom';

import companyLogo from './assets/mi-logo.png';

import { LoginView } from './components/auth/LoginView';
import { ChatContainer } from './components/chat/ChatContainer';
import { Footer } from './components/layout/Footer';
import { Navbar } from './components/layout/Navbar';
import { Sidebar } from './components/layout/Sidebar';

import { useAuth } from './context/AuthContext';

import { AdminDashboard } from './pages/admin/AdminDashboard';
import { AdminEnrollments } from './pages/admin/AdminEnrollments';
import { InstructorCourses } from './pages/instructor/InstructorCourses';
import { InstructorStudents } from './pages/instructor/InstructorStudents';
import { MarketplaceView } from './pages/MarketplaceView';
import { StudentCourses } from './pages/student/StudentCourses';

import { RoleProtectedRoute } from './routes/RoleProtectedRoute';

import { academicService } from './services/academicService';
import { getApiErrorMessage } from './services/authService';
import {
  buildCategories,
  toCatalogCourse,
} from './services/courseCatalog';

import {
  ActiveView,
  AuthUser,
  ChatMessage,
  Playbook,
  UserProfile,
  UserRole,
} from './types';

type Theme = 'light' | 'dark';

interface AppLayoutProps {
  theme: Theme;
  onThemeToggle: () => void;
}

interface EmptySectionContent {
  eyebrow: string;
  title: string;
  description: string;
  emptyTitle: string;
  emptyDescription: string;
}

const EMPTY_SECTIONS: Partial<
  Record<ActiveView, EmptySectionContent>
> = {
  PROGRESS: {
    eyebrow: 'Área del ejecutivo',
    title: 'Mi progreso',
    description:
      'Consulta el avance real de tus cursos y programas.',
    emptyTitle: 'No hay información de progreso',
    emptyDescription:
      'El progreso aparecerá cuando el backend registre tus avances y actividades completadas.',
  },

  LIBROS: {
    eyebrow: 'Recursos académicos',
    title: 'Biblioteca ejecutiva',
    description:
      'Consulta libros, documentos y materiales académicos.',
    emptyTitle: 'No hay recursos publicados',
    emptyDescription:
      'Los materiales aparecerán cuando sean publicados por los profesores.',
  },

  INSTRUCTOR_DASHBOARD: {
    eyebrow: 'Área del profesor',
    title: 'Panel docente',
    description:
      'Consulta la información general de tus cursos.',
    emptyTitle: 'No hay métricas disponibles',
    emptyDescription:
      'Las estadísticas aparecerán cuando el backend habilite los datos de actividad docente.',
  },

  CONTENT: {
    eyebrow: 'Área del profesor',
    title: 'Contenido académico',
    description:
      'Gestiona módulos, lecciones, documentos y evaluaciones.',
    emptyTitle: 'No hay contenidos disponibles',
    emptyDescription:
      'Esta sección se habilitará cuando esté disponible el servicio de contenidos académicos.',
  },

  REPORTS: {
    eyebrow: 'Administración',
    title: 'Reportes y métricas',
    description:
      'Consulta información verificable sobre la actividad de la plataforma.',
    emptyTitle: 'No hay reportes disponibles',
    emptyDescription:
      'Los reportes aparecerán cuando existan datos suficientes proporcionados por el backend.',
  },
};

const VIEW_PATHS: Record<
  UserRole,
  Partial<Record<ActiveView, string>>
> = {
  ESTUDIANTE: {
    MARKETPLACE: '/estudiante/cursos',
    MY_COURSES: '/estudiante/mis-cursos',
    PROGRESS: '/estudiante/progreso',
    LIBROS: '/estudiante/biblioteca',
    CHAT: '/estudiante/chat',
  },

  INSTRUCTOR: {
    INSTRUCTOR_DASHBOARD: '/profesor/dashboard',
    MANAGE_COURSES: '/profesor/cursos',
    CONTENT: '/profesor/contenido',
    STUDENTS: '/profesor/estudiantes',
  },

  ADMIN: {
    ADMIN_DASHBOARD: '/admin/dashboard',
    USERS: '/admin/usuarios',
    MANAGE_COURSES: '/admin/cursos',
    ENROLLMENTS: '/admin/inscripciones',
    REPORTS: '/admin/reportes',
  },
};

const PATH_VIEWS: Record<string, ActiveView> = {
  '/estudiante/cursos': 'MARKETPLACE',
  '/estudiante/mis-cursos': 'MY_COURSES',
  '/estudiante/progreso': 'PROGRESS',
  '/estudiante/biblioteca': 'LIBROS',
  '/estudiante/chat': 'CHAT',

  '/profesor/dashboard': 'INSTRUCTOR_DASHBOARD',
  '/profesor/cursos': 'MANAGE_COURSES',
  '/profesor/contenido': 'CONTENT',
  '/profesor/estudiantes': 'STUDENTS',

  '/admin/dashboard': 'ADMIN_DASHBOARD',
  '/admin/usuarios': 'USERS',
  '/admin/cursos': 'MANAGE_COURSES',
  '/admin/inscripciones': 'ENROLLMENTS',
  '/admin/reportes': 'REPORTS',
};

const toNavbarUser = (
  user: AuthUser,
): UserProfile => ({
  id: String(user.id),
  name: user.nombre,
  role: user.rol,

  jobTitle:
    user.rol === 'ADMIN'
      ? 'Administrador'
      : user.rol === 'INSTRUCTOR'
        ? 'Profesor'
        : 'Ejecutivo',

  company: 'Lysandri Global Tech',
  avatarUrl: companyLogo,
});

interface EmptySectionPageProps {
  view: ActiveView;
}

const EmptySectionPage: React.FC<
  EmptySectionPageProps
> = ({ view }) => {
  const content = EMPTY_SECTIONS[view];

  if (!content) {
    return (
      <div className="rounded-2xl border border-cyan-500/10 bg-white p-8 text-center dark:border-slate-800 dark:bg-[#0c111a]">
        <p className="text-sm text-slate-500">
          Esta sección todavía no tiene contenido.
        </p>
      </div>
    );
  }

  return (
    <section className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-500">
      <div className="relative overflow-hidden rounded-3xl border border-cyan-500/15 bg-white p-8 dark:border-slate-800 dark:bg-[#0c111a]">
        <div className="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-cyan-500/10 blur-3xl" />

        <div className="pointer-events-none absolute -bottom-24 right-1/4 h-64 w-64 rounded-full bg-indigo-500/10 blur-3xl" />

        <div className="relative">
          <span className="inline-flex rounded-full border border-cyan-500/20 bg-cyan-500/5 px-3 py-1 font-mono text-[10px] uppercase tracking-widest text-cyan-600 dark:text-cyan-400">
            {content.eyebrow}
          </span>

          <h1 className="mt-5 text-3xl font-black text-slate-950 dark:text-white">
            {content.title}
          </h1>

          <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400">
            {content.description}
          </p>
        </div>
      </div>

      <div className="flex min-h-72 flex-col items-center justify-center rounded-3xl border border-dashed border-cyan-500/20 bg-white px-6 py-12 text-center dark:border-slate-800 dark:bg-[#0c111a]">
        <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-cyan-500/10 to-indigo-500/10 text-cyan-500">
          <Database className="h-7 w-7" />
        </span>

        <h2 className="mt-5 text-lg font-black text-slate-950 dark:text-white">
          {content.emptyTitle}
        </h2>

        <p className="mt-2 max-w-md text-sm leading-6 text-slate-500 dark:text-slate-400">
          {content.emptyDescription}
        </p>
      </div>
    </section>
  );
};

const MarketplacePage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();

  const [playbooks, setPlaybooks] = useState<Playbook[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [selectingId, setSelectingId] = useState<string | null>(
    null,
  );
  const [message, setMessage] = useState('');
  const [messageTone, setMessageTone] = useState<
    'success' | 'error'
  >('success');

  const loadPrograms = async () => {
    setIsLoading(true);
    setErrorMessage('');

    try {
      const programs = await academicService.getPrograms();

      setPlaybooks(
        programs.map(toCatalogCourse),
      );
    } catch (error) {
      setPlaybooks([]);
      setErrorMessage(
        getApiErrorMessage(error),
      );
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadPrograms();
  }, []);

  const handleSelect = async (
    playbook: Playbook,
  ) => {
    if (!user) {
      navigate('/login');
      return;
    }

    if (user.rol !== 'ESTUDIANTE') {
      setMessage(
        'La inscripción solamente está disponible para ejecutivos.',
      );

      setMessageTone('error');
      return;
    }

    setSelectingId(playbook.id);

    try {
      await academicService.enroll({
        programaId: playbook.programId,
      });

      setMessage(
        `Te inscribiste correctamente en “${playbook.title}”.`,
      );

      setMessageTone('success');
    } catch (error) {
      setMessage(
        getApiErrorMessage(error),
      );

      setMessageTone('error');
    } finally {
      setSelectingId(null);
    }
  };

  useEffect(() => {
    if (!message) {
      return;
    }

    const timeout = window.setTimeout(() => {
      setMessage('');
    }, 4500);

    return () => {
      window.clearTimeout(timeout);
    };
  }, [message]);

  return (
    <>
      <MarketplaceView
        playbooks={playbooks}
        categories={buildCategories(playbooks)}
        isLoading={isLoading}
        errorMessage={errorMessage}
        selectingId={selectingId}
        onRetry={() => void loadPrograms()}
        onSelectPlaybook={handleSelect}
      />

      {message && (
        <div
          className={`fixed bottom-6 right-6 z-50 flex max-w-md items-center gap-3 rounded-xl border bg-slate-950 px-4 py-3 text-sm text-white shadow-2xl ${
            messageTone === 'success'
              ? 'border-emerald-500/30'
              : 'border-rose-500/30'
          }`}
        >
          {messageTone === 'success' ? (
            <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-400" />
          ) : (
            <CircleAlert className="h-4 w-4 shrink-0 text-rose-400" />
          )}

          <span>{message}</span>
        </div>
      )}
    </>
  );
};

const ChatPage: React.FC = () => {
  const [messages, setMessages] =
    useState<ChatMessage[]>([]);

  const [isLoading, setIsLoading] =
    useState(false);

  const handleSendMessage = (
    content: string,
  ) => {
    const normalizedContent =
      content.trim();

    if (
      !normalizedContent ||
      isLoading
    ) {
      return;
    }

    const sessionId = 'local-session';

    setMessages((currentMessages) => [
      ...currentMessages,
      {
        id: `user-${Date.now()}`,
        sessionId,
        sender: 'USER',
        content: normalizedContent,

        timestamp:
          new Date().toLocaleTimeString(
            [],
            {
              hour: '2-digit',
              minute: '2-digit',
            },
          ),
      },
    ]);

    setIsLoading(true);

    window.setTimeout(() => {
      setMessages(
        (currentMessages) => [
          ...currentMessages,
          {
            id: `assistant-${Date.now()}`,
            sessionId,
            sender: 'ASSISTANT',

            content:
              'El asistente de inteligencia artificial todavía no está conectado al backend.',

            timestamp:
              new Date().toLocaleTimeString(
                [],
                {
                  hour: '2-digit',
                  minute: '2-digit',
                },
              ),

            sources: [],
          },
        ],
      );

      setIsLoading(false);
    }, 500);
  };

  const handleClearHistory = () => {
    setMessages([]);
    setIsLoading(false);
  };

  return (
    <ChatContainer
      messages={messages}
      onSendMessage={handleSendMessage}
      isLoading={isLoading}
      onClearHistory={handleClearHistory}
    />
  );
};

const ProtectedLayout: React.FC<
  AppLayoutProps
> = ({
  theme,
  onThemeToggle,
}) => {
  const navigate = useNavigate();
  const location = useLocation();

  const { user, logout } = useAuth();

  const activeView = useMemo<ActiveView>(
    () =>
      PATH_VIEWS[location.pathname] ??
      'MARKETPLACE',
    [location.pathname],
  );

  if (!user) {
    return (
      <Navigate
        to="/login"
        replace
      />
    );
  }

  const handleViewChange = (
    view: ActiveView,
  ) => {
    const path =
      VIEW_PATHS[user.rol][view];

    if (path) {
      navigate(path);
    }
  };

  const handleLogout = () => {
    logout();

    navigate('/', {
      replace: true,
    });
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 transition-colors duration-300 dark:bg-[#06090f] dark:text-slate-100">
      <Navbar
        user={toNavbarUser(user)}
        theme={theme}
        onThemeToggle={onThemeToggle}
        onLoginClick={() =>
          navigate('/login')
        }
        onLogoutClick={handleLogout}
      />

      <Sidebar
        activeView={activeView}
        role={user.rol}
        onViewChange={handleViewChange}
      />

      <div className="transition-[padding] duration-300 lg:pl-64">
        <main className="min-h-[calc(100vh-4rem)]">
          <div className="mx-auto w-full max-w-[1920px] px-4 py-7 sm:px-6 lg:px-7">
            <Outlet />
          </div>
        </main>

        <Footer />
      </div>

      {user.rol === 'ESTUDIANTE' &&
        activeView !== 'CHAT' && (
          <button
            type="button"
            onClick={() =>
              navigate(
                '/estudiante/chat',
              )
            }
            aria-label="Abrir asistente IA"
            className="fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-cyan-500 to-indigo-600 text-white shadow-xl transition-all duration-300 hover:-translate-y-1 hover:scale-105 active:scale-95"
          >
            <Bot className="h-6 w-6" />
          </button>
        )}
    </div>
  );
};

const PublicLayout: React.FC<
  AppLayoutProps
> = ({
  theme,
  onThemeToggle,
}) => {
  const navigate = useNavigate();

  const { user, logout } = useAuth();

  const handleLogout = () => {
    logout();

    navigate('/', {
      replace: true,
    });
  };

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 transition-colors duration-300 dark:bg-[#06090f] dark:text-slate-100">
      <Navbar
        user={
          user
            ? toNavbarUser(user)
            : null
        }
        theme={theme}
        onThemeToggle={onThemeToggle}
        onLoginClick={() =>
          navigate('/login')
        }
        onLogoutClick={handleLogout}
      />

      <main className="min-h-[calc(100vh-4rem)]">
        <div className="mx-auto w-full max-w-[1920px] px-4 py-7 sm:px-6 lg:px-7">
          <Outlet />
        </div>
      </main>

      <Footer />
    </div>
  );
};

const App: React.FC = () => {
  const navigate = useNavigate();

  const [theme, setTheme] =
    useState<Theme>('dark');

  useEffect(() => {
    document.documentElement.classList.toggle(
      'dark',
      theme === 'dark',
    );
  }, [theme]);

  const layoutProps: AppLayoutProps = {
    theme,

    onThemeToggle: () => {
      setTheme((currentTheme) =>
        currentTheme === 'dark'
          ? 'light'
          : 'dark',
      );
    },
  };

  return (
    <Routes>
      <Route
        path="/login"
        element={
          <LoginView
            onBack={() =>
              navigate('/')
            }
          />
        }
      />

      <Route
        element={
          <PublicLayout
            {...layoutProps}
          />
        }
      >
        <Route
          index
          element={
            <MarketplacePage />
          }
        />
      </Route>

      <Route
        element={
          <RoleProtectedRoute
            allowedRoles={[
              'ESTUDIANTE',
            ]}
          />
        }
      >
        <Route
          element={
            <ProtectedLayout
              {...layoutProps}
            />
          }
        >
          <Route
            path="/estudiante"
            element={
              <Navigate
                to="/estudiante/mis-cursos"
                replace
              />
            }
          />

          <Route
            path="/estudiante/cursos"
            element={
              <MarketplacePage />
            }
          />

          <Route
            path="/estudiante/mis-cursos"
            element={
              <StudentCourses />
            }
          />

          <Route
            path="/estudiante/progreso"
            element={
              <StudentCourses mode="progress" />
            }
          />

          <Route
            path="/estudiante/biblioteca"
            element={
              <EmptySectionPage view="LIBROS" />
            }
          />

          <Route
            path="/estudiante/chat"
            element={<ChatPage />}
          />
        </Route>
      </Route>

      <Route
        element={
          <RoleProtectedRoute
            allowedRoles={[
              'INSTRUCTOR',
            ]}
          />
        }
      >
        <Route
          element={
            <ProtectedLayout
              {...layoutProps}
            />
          }
        >
          <Route
            path="/profesor"
            element={
              <Navigate
                to="/profesor/cursos"
                replace
              />
            }
          />

          <Route
            path="/profesor/dashboard"
            element={
              <EmptySectionPage view="INSTRUCTOR_DASHBOARD" />
            }
          />

          <Route
            path="/profesor/cursos"
            element={
              <InstructorCourses />
            }
          />

          <Route
            path="/profesor/contenido"
            element={
              <InstructorCourses
                focusContent
              />
            }
          />

          <Route
            path="/profesor/estudiantes"
            element={
              <InstructorStudents />
            }
          />
        </Route>
      </Route>

      <Route
        element={
          <RoleProtectedRoute
            allowedRoles={['ADMIN']}
          />
        }
      >
        <Route
          element={
            <ProtectedLayout
              {...layoutProps}
            />
          }
        >
          <Route
            path="/admin"
            element={
              <Navigate
                to="/admin/dashboard"
                replace
              />
            }
          />

          <Route
            path="/admin/dashboard"
            element={
              <AdminDashboard />
            }
          />

          <Route
            path="/admin/usuarios"
            element={
              <AdminDashboard initialSection="users" />
            }
          />

          <Route
            path="/admin/cursos"
            element={
              <AdminDashboard initialSection="programs" />
            }
          />

          <Route
            path="/admin/inscripciones"
            element={
              <AdminEnrollments />
            }
          />

          <Route
            path="/admin/reportes"
            element={
              <EmptySectionPage view="REPORTS" />
            }
          />
        </Route>
      </Route>

      <Route
        path="*"
        element={
          <Navigate
            to="/"
            replace
          />
        }
      />
    </Routes>
  );
};

export default App;
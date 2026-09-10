export type TierLevel = 'ESSENTIAL' | 'ADVANCED' | 'ENTERPRISE';

export type UserRole = 'ESTUDIANTE' | 'INSTRUCTOR' | 'ADMIN';

export type ActiveView =
  | 'MARKETPLACE'
  | 'MY_COURSES'
  | 'PROGRESS'
  | 'CHAT'
  | 'LIBROS'
  | 'INSTRUCTOR_DASHBOARD'
  | 'MANAGE_COURSES'
  | 'CONTENT'
  | 'STUDENTS'
  | 'ADMIN_DASHBOARD'
  | 'USERS'
  | 'ENROLLMENTS'
  | 'REPORTS'
  | 'LOGIN';

export interface Playbook {
  id: string;
  programId: number;
  title: string;
  coverUrl?: string;
  slug: string;
  description: string;
  category: string;
  tier: TierLevel;
  tags: string[];
  duration?: string;
  instructor?: string;
}

export interface Category {
  id: string;
  name: string;
  count: number;
  iconName: string;
}

export interface UserProfile {
  id: string;
  name: string;
  role: UserRole;
  jobTitle: string;
  company: string;
  avatarUrl: string;
}

export interface AuthUser {
  id: number;
  nombre: string;
  email: string;
  rol: UserRole;
}

export interface AuthResponse extends AuthUser {
  token: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  nombre: string;
  apellidos: string;
  email: string;
  password: string;
  telefono?: string;
  rol: UserRole;
}

export interface ApiErrorResponse {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  errors?: Record<string, string>;
}

export interface UsuarioResponse {
  idUser: number;
  nombres: string;
  apellidos: string;
  email: string;
  telefono?: string;
  rol: UserRole;
}

export interface ProgramaResponse {
  idPrograma: number;
  idInstructorDni: string;
  nombreInstructor: string;
  tituloPrograma: string;
  duracionPrograma?: string;
  tipoPrograma?: string;
  level?: string;
  fechaInicioGlobal?: string;
  fechaFinalGlobal?: string;
  requisitos?: string;
  metodologia?: string;
}

export interface InscripcionRequest {
  programaId: number;
  usuarioId?: number;
}

export interface InscripcionResponse {
  id: number;
  usuarioId: number;
  nombreEstudiante: string;
  programaId: number;
  tituloPrograma: string;
  fechaInscripcion: string;
  estado: string;
}

export interface UsuarioRequest {
  nombres: string;
  apellidos: string;
  email: string;
  passw: string;
  telefono?: string;
  rol: UserRole;
}

export interface LeccionRequest {
  titulo: string;
  descripcion?: string;
  contenidoUrl?: string;
  duracionMinutos?: number;
  orden: number;
}

export interface LeccionResponse {
  id: number;
  programaId: number;
  titulo: string;
  descripcion?: string;
  contenidoUrl?: string;
  duracionMinutos?: number;
  orden: number;
}

export interface ProgresoResponse {
  id: number;
  usuarioId: number;
  leccionId: number;
  completado: boolean;
  fechaCompletado?: string;
}

export interface ProgresoPorcentajeResponse {
  programaId: number;
  totalLecciones: number;
  leccionesCompletadas: number;
  porcentaje: number;
}

export interface ChatMessage {
  id: string;
  sessionId: string;
  sender: 'USER' | 'ASSISTANT';
  content: string;
  timestamp: string;
  sources?: string[];
}

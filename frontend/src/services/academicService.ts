import {
  EstudianteInscritoResponse,
  InscripcionRequest,
  InscripcionResponse,
  LeccionRequest,
  LeccionResponse,
  ProgramaResponse,
  ProgresoPorcentajeResponse,
  ProgresoResponse,
  UsuarioRequest,
  UsuarioResponse,
} from '../types';

import { apiClient } from './api';

const CUSTOM_PROGRAMS_KEY = 'lysandri_custom_programs';
const CUSTOM_USERS_KEY = 'lysandri_custom_users';

export const getCustomUsers = (): UsuarioResponse[] => {
  try {
    const raw = localStorage.getItem(CUSTOM_USERS_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    // ignore
  }
  return [];
};

export const saveCustomUsers = (users: UsuarioResponse[]) => {
  try {
    localStorage.setItem(CUSTOM_USERS_KEY, JSON.stringify(users));
  } catch {
    // ignore
  }
};

export const getCustomPrograms = (): ProgramaResponse[] => {
  try {
    const raw = localStorage.getItem(CUSTOM_PROGRAMS_KEY);
    if (raw) return JSON.parse(raw);
  } catch {
    // ignore
  }
  return [];
};

export const saveCustomPrograms = (programs: ProgramaResponse[]) => {
  try {
    localStorage.setItem(CUSTOM_PROGRAMS_KEY, JSON.stringify(programs));
  } catch {
    // ignore
  }
};

export const academicService = {
  async createUser(
    data: UsuarioRequest,
  ): Promise<UsuarioResponse> {
    const customUsers = getCustomUsers();
    const newUser: UsuarioResponse = {
      idUser: Date.now(),
      nombres: data.nombres,
      apellidos: data.apellidos,
      email: data.email,
      telefono: data.telefono || '',
      rol: data.rol,
    };

    customUsers.push(newUser);
    saveCustomUsers(customUsers);

    try {
      const response =
        await apiClient.post<UsuarioResponse>(
          '/usuarios',
          data,
        );

      return response.data;
    } catch {
      return newUser;
    }
  },

  async updateUser(
    idUser: number,
    data: Partial<UsuarioRequest>,
  ): Promise<UsuarioResponse> {
    const customUsers = getCustomUsers();
    const index = customUsers.findIndex((u) => u.idUser === idUser);
    let updatedUser: UsuarioResponse;

    if (index >= 0) {
      customUsers[index] = {
        ...customUsers[index],
        nombres: data.nombres ?? customUsers[index].nombres,
        apellidos: data.apellidos ?? customUsers[index].apellidos,
        email: data.email ?? customUsers[index].email,
        telefono: data.telefono !== undefined ? data.telefono : customUsers[index].telefono,
        rol: data.rol ?? customUsers[index].rol,
      };
      saveCustomUsers(customUsers);
      updatedUser = customUsers[index];
    } else {
      const allUsers = await this.getUsers();
      const existing = allUsers.find((u) => u.idUser === idUser);
      updatedUser = {
        idUser,
        nombres: data.nombres ?? existing?.nombres ?? '',
        apellidos: data.apellidos ?? existing?.apellidos ?? '',
        email: data.email ?? existing?.email ?? '',
        telefono: data.telefono !== undefined ? data.telefono : existing?.telefono ?? '',
        rol: data.rol ?? existing?.rol ?? 'ESTUDIANTE',
      };
      customUsers.push(updatedUser);
      saveCustomUsers(customUsers);
    }

    try {
      await apiClient.put(`/usuarios/${idUser}`, data);
    } catch {
      // offline/mock fallback
    }

    return updatedUser;
  },

  async deleteUser(idUser: number): Promise<void> {
    const customUsers = getCustomUsers();
    const filtered = customUsers.filter((u) => u.idUser !== idUser);
    saveCustomUsers(filtered);

    try {
      const deletedRaw = localStorage.getItem('lysandri_deleted_users');
      const deletedIds: number[] = deletedRaw ? JSON.parse(deletedRaw) : [];
      if (!deletedIds.includes(idUser)) {
        deletedIds.push(idUser);
        localStorage.setItem('lysandri_deleted_users', JSON.stringify(deletedIds));
      }
    } catch {
      // ignore
    }

    try {
      await apiClient.delete(`/usuarios/${idUser}`);
    } catch {
      // ignore
    }
  },

  async getUsers(): Promise<UsuarioResponse[]> {
    let baseUsers: UsuarioResponse[] = [];
    try {
      const response =
        await apiClient.get<UsuarioResponse[]>(
          '/usuarios',
        );

      baseUsers = response.data;
    } catch {
      baseUsers = [
        {
          idUser: 1,
          nombres: 'Danny Ronaldo',
          apellidos: 'Leiva Tovar',
          email: 'danny@lysandri.com',
          telefono: '+51 941 238 905',
          rol: 'ADMIN',
        },
        {
          idUser: 2,
          nombres: 'Antony Brayan',
          apellidos: 'Ruiz Susanibar',
          email: 'antonybrayanruizsusanibar@gmail.com',
          telefono: '+51 987 242 796',
          rol: 'ADMIN',
        },
        {
          idUser: 3,
          nombres: 'Director General',
          apellidos: 'Lysandri Executive',
          email: 'admin@lysandri.com',
          telefono: '+51 999 000 111',
          rol: 'ADMIN',
        },
        {
          idUser: 4,
          nombres: 'Carlos',
          apellidos: 'Vargas Silva',
          email: 'carlos.vargas@empresa.com',
          telefono: '+51 984 123 456',
          rol: 'ESTUDIANTE',
        },
        {
          idUser: 5,
          nombres: 'Mariana',
          apellidos: 'Sánchez Ríos',
          email: 'm.sanchez@corporacion.pe',
          telefono: '+51 991 765 432',
          rol: 'ESTUDIANTE',
        },
        {
          idUser: 6,
          nombres: 'Dra. Elena',
          apellidos: 'Alarcón',
          email: 'elena.alarcon@lysandri.com',
          telefono: '+51 972 345 678',
          rol: 'INSTRUCTOR',
        },
        {
          idUser: 7,
          nombres: 'Mg. Roberto',
          apellidos: 'Valenzuela',
          email: 'roberto.valenzuela@lysandri.com',
          telefono: '+51 965 890 123',
          rol: 'INSTRUCTOR',
        },
      ];
    }

    let merged = [...baseUsers];

    try {
      const deletedRaw = localStorage.getItem('lysandri_deleted_users');
      const deletedIds: number[] = deletedRaw ? JSON.parse(deletedRaw) : [];
      if (deletedIds.length > 0) {
        merged = merged.filter((u) => !deletedIds.includes(u.idUser));
      }
    } catch {
      // ignore
    }

    const custom = getCustomUsers();
    custom.forEach((u) => {
      const existingIdx = merged.findIndex(
        (m) => m.idUser === u.idUser || m.email.toLowerCase() === u.email.toLowerCase()
      );
      if (existingIdx >= 0) {
        merged[existingIdx] = { ...merged[existingIdx], ...u };
      } else {
        merged.push(u);
      }
    });

    return merged;
  },

  async getPrograms(): Promise<ProgramaResponse[]> {
    let apiPrograms: ProgramaResponse[] = [];
    try {
      const response = await apiClient.get<ProgramaResponse[]>('/programas');
      if (Array.isArray(response.data) && response.data.length > 0) {
        apiPrograms = response.data;
      }
    } catch {
      // fallback
    }

    const customList = getCustomPrograms();

    if (apiPrograms.length === 0) {
      const defaultPrograms: ProgramaResponse[] = [
        {
          idPrograma: 1,
          idInstructorDni: '00000001',
          nombreInstructor: 'Dr. Marcos Sotomayor',
          tituloPrograma: 'Inteligencia Artificial para Directivos & C-Suite',
          duracionPrograma: '6 Semanas',
          tipoPrograma: 'Inteligencia Artificial',
          level: 'ENTERPRISE',
          fechaInicioGlobal: '2026-10-15',
          precio: 4200,
          descripcion: 'Aprende a integrar asistentes inteligentes, automatizar la atención y ventas, y tomar decisiones estratégicas basadas en datos sin necesidad de saber programar.',
          coverUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=800&q=80',
        },
        {
          idPrograma: 2,
          idInstructorDni: '00000002',
          nombreInstructor: 'Dra. Elena Alarcón',
          tituloPrograma: 'Ciberseguridad & Protección Antifraude para Empresas',
          duracionPrograma: '8 Semanas',
          tipoPrograma: 'Seguridad y Riesgos',
          level: 'ADVANCED',
          fechaInicioGlobal: '2026-10-20',
          precio: 3650,
          descripcion: 'Protege a tu organización contra estafas digitales, ransomware, robo de información confidencial y fraudes bancarios. Diseñado para directivos y líderes de negocio.',
          coverUrl: 'https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=800&q=80',
        },
        {
          idPrograma: 3,
          idInstructorDni: '00000003',
          nombreInstructor: 'Mg. Roberto Valenzuela',
          tituloPrograma: 'Gestión Financiera Estratégica & Optimización de Costos',
          duracionPrograma: '8 Semanas',
          tipoPrograma: 'Finanzas y Gestión',
          level: 'ENTERPRISE',
          fechaInicioGlobal: '2026-10-22',
          precio: 3950,
          descripcion: 'Aprende a auditar y reducir hasta un 40% en gastos de tecnología, servicios en la nube y licencias operativas, maximizando el margen de rentabilidad y retorno de inversión (ROI).',
          coverUrl: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=800&q=80',
        },
        {
          idPrograma: 4,
          idInstructorDni: '00000004',
          nombreInstructor: 'Lic. Carlos Mendoza',
          tituloPrograma: 'Automatización de Procesos con Herramientas No-Code',
          duracionPrograma: '6 Semanas',
          tipoPrograma: 'Automatización y Procesos',
          level: 'ESSENTIAL',
          fechaInicioGlobal: '2026-10-25',
          precio: 2890,
          descripcion: 'Automatiza flujos de ventas, cobranzas, reportes y atención al cliente conectando sistemas empresariales fácilmente sin contratar programadores ni escribir código.',
          coverUrl: 'https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop&w=800&q=80',
        },
        {
          idPrograma: 5,
          idInstructorDni: '00000005',
          nombreInstructor: 'MSc. Patricia Arana',
          tituloPrograma: 'Transformación Digital & Liderazgo de Negocios',
          duracionPrograma: '8 Semanas',
          tipoPrograma: 'Liderazgo y Estrategia',
          level: 'ADVANCED',
          fechaInicioGlobal: '2026-10-28',
          precio: 3450,
          descripcion: 'Cómo liderar la modernización de empresas tradicionales hacia modelos digitales rentables, gestionando el cambio organizacional y alineando equipos sin fricción.',
          coverUrl: 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=800&q=80',
        },
        {
          idPrograma: 6,
          idInstructorDni: '00000006',
          nombreInstructor: 'Dr. Fernando Benavides',
          tituloPrograma: 'Dirección Estratégica & Negociación para Alta Gerencia',
          duracionPrograma: '6 Semanas',
          tipoPrograma: 'Liderazgo y Estrategia',
          level: 'ESSENTIAL',
          fechaInicioGlobal: '2026-11-02',
          precio: 2890,
          descripcion: 'Desarrolla habilidades de comunicación con comités de directorio, negociación de contratos corporativos de alto valor y toma de decisiones ágiles en momentos clave.',
          coverUrl: 'https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=800&q=80',
        },
      ];
      apiPrograms = defaultPrograms;
    }

    const merged = [...apiPrograms];
    customList.forEach((custom) => {
      const idx = merged.findIndex((p) => p.idPrograma === custom.idPrograma);
      if (idx >= 0) {
        merged[idx] = custom;
      } else {
        merged.push(custom);
      }
    });

    return merged;
  },

  async saveProgram(programData: Partial<ProgramaResponse>): Promise<ProgramaResponse> {
    const customList = getCustomPrograms();
    let saved: ProgramaResponse;

    if (programData.idPrograma) {
      const existingIdx = customList.findIndex((p) => p.idPrograma === programData.idPrograma);
      saved = {
        idPrograma: programData.idPrograma,
        idInstructorDni: programData.idInstructorDni || '00000000',
        nombreInstructor: programData.nombreInstructor || 'Director Académico',
        tituloPrograma: programData.tituloPrograma || 'Programa Ejecutivo',
        duracionPrograma: programData.duracionPrograma || '6 Semanas',
        tipoPrograma: programData.tipoPrograma || 'Gestión y Estrategia',
        level: programData.level || 'ENTERPRISE',
        fechaInicioGlobal: programData.fechaInicioGlobal || new Date().toISOString().slice(0, 10),
        precio: programData.precio !== undefined ? Number(programData.precio) : 5,
        descripcion: programData.descripcion || '',
        coverUrl: programData.coverUrl || '',
        modalidad: programData.modalidad || 'Virtual Asincrónico con Mentoría',
        syllabus: programData.syllabus || [],
      };
      if (existingIdx >= 0) {
        customList[existingIdx] = saved;
      } else {
        customList.push(saved);
      }
    } else {
      const newId = Date.now();
      saved = {
        idPrograma: newId,
        idInstructorDni: programData.idInstructorDni || '00000000',
        nombreInstructor: programData.nombreInstructor || 'Director Académico',
        tituloPrograma: programData.tituloPrograma || 'Nuevo Programa Ejecutivo',
        duracionPrograma: programData.duracionPrograma || '6 Semanas',
        tipoPrograma: programData.tipoPrograma || 'Gestión y Estrategia',
        level: programData.level || 'ENTERPRISE',
        fechaInicioGlobal: programData.fechaInicioGlobal || new Date().toISOString().slice(0, 10),
        precio: programData.precio !== undefined ? Number(programData.precio) : 5,
        descripcion: programData.descripcion || '',
        coverUrl: programData.coverUrl || '',
        modalidad: programData.modalidad || 'Virtual Asincrónico con Mentoría',
        syllabus: programData.syllabus || [],
      };
      customList.push(saved);
    }

    saveCustomPrograms(customList);

    try {
      if (programData.idPrograma) {
        await apiClient.put(`/programas/${programData.idPrograma}`, saved);
      } else {
        await apiClient.post('/programas', saved);
      }
    } catch {
      // fallback local
    }

    return saved;
  },

  async deleteProgram(programId: number): Promise<void> {
    const customList = getCustomPrograms().filter((p) => p.idPrograma !== programId);
    saveCustomPrograms(customList);
    try {
      await apiClient.delete(`/programas/${programId}`);
    } catch {
      // ignore
    }
  },

  async getProgram(
    id: number,
  ): Promise<ProgramaResponse> {
    const response =
      await apiClient.get<ProgramaResponse>(
        `/programas/${id}`,
      );

    return response.data;
  },
async getInstructorPrograms(): Promise<
  ProgramaResponse[]
> {
  const response =
    await apiClient.get<
      ProgramaResponse[]
    >('/profesor/mis-cursos');

  return response.data;
},

async getInstructorStudents(
  programId: number,
): Promise<EstudianteInscritoResponse[]> {
  const response =
    await apiClient.get<
      EstudianteInscritoResponse[]
    >(
      `/profesor/programas/${programId}/estudiantes`,
    );

  return response.data;
},
  async enroll(
    data: InscripcionRequest,
  ): Promise<InscripcionResponse> {
    const response =
      await apiClient.post<InscripcionResponse>(
        '/inscripciones',
        data,
      );

    return response.data;
  },

  async getMyCourses(): Promise<
    InscripcionResponse[]
  > {
    const response =
      await apiClient.get<
        InscripcionResponse[]
      >('/estudiante/mis-cursos');

    return response.data;
  },

  async getLessons(
    programId: number,
  ): Promise<LeccionResponse[]> {
    const response =
      await apiClient.get<
        LeccionResponse[]
      >(
        `/programas/${programId}/lecciones`,
      );

    return response.data;
  },

  async createLesson(
    programId: number,
    data: LeccionRequest,
  ): Promise<LeccionResponse> {
    const response =
      await apiClient.post<LeccionResponse>(
        `/programas/${programId}/lecciones`,
        data,
      );

    return response.data;
  },

  async updateLesson(
    lessonId: number,
    data: LeccionRequest,
  ): Promise<LeccionResponse> {
    const response =
      await apiClient.put<LeccionResponse>(
        `/lecciones/${lessonId}`,
        data,
      );

    return response.data;
  },

  async deleteLesson(
    lessonId: number,
  ): Promise<void> {
    await apiClient.delete(
      `/lecciones/${lessonId}`,
    );
  },

  async completeLesson(
    lessonId: number,
  ): Promise<ProgresoResponse> {
    const response =
      await apiClient.post<ProgresoResponse>(
        `/lecciones/${lessonId}/completar`,
      );

    return response.data;
  },

  async getProgress(
    programId: number,
  ): Promise<ProgresoPorcentajeResponse> {
    const response =
      await apiClient.get<ProgresoPorcentajeResponse>(
        `/programas/${programId}/progreso`,
      );

    return response.data;
  },
};
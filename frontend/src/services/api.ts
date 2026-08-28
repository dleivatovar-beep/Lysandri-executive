// src/services/api.ts
import axios from 'axios';
import { Playbook, ChatMessage, Category } from '../types';
import { MOCK_PLAYBOOKS, MOCK_CHAT_MESSAGES, MOCK_CATEGORIES } from './mockData';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  },
  timeout: 10000
});

// Request Interceptor
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('lysandri_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Mock Service Helpers (Falls back to local mock data when backend server is offline)
export const PlaybookService = {
  async getAll(): Promise<Playbook[]> {
    try {
      const response = await apiClient.get<Playbook[]>('/playbooks');
      return response.data;
    } catch {
      console.info('Backend unavailable - Using mock Playbooks dataset.');
      return MOCK_PLAYBOOKS;
    }
  },

  async getCategories(): Promise<Category[]> {
    try {
      const response = await apiClient.get<Category[]>('/categories');
      return response.data;
    } catch {
      return MOCK_CATEGORIES;
    }
  }
};

export const ChatService = {
  async getMessages(sessionId: string): Promise<ChatMessage[]> {
    try {
      const response = await apiClient.get<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`);
      return response.data;
    } catch {
      return MOCK_CHAT_MESSAGES;
    }
  },

  async sendMessage(content: string, sessionId: string = 'session-executive-01'): Promise<ChatMessage> {
    try {
      const response = await apiClient.post<ChatMessage>('/chat/query', { content, sessionId });
      return response.data;
    } catch {
      // Simulated executive AI RAG response
      const aiResponse: ChatMessage = {
        id: `msg-${Date.now()}`,
        sessionId,
        sender: 'ASSISTANT',
        content: `Analizando consulta ejecutiva: "${content}"\n\nHe procesado la solicitud cruzando datos con los estándares de arquitectura de Lysandri Executive. Los componentes de software y guías FinOps recomendadas se adaptan a esta especificación.`,
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        sources: [
          "Enterprise_Architecture_Guide_2026.pdf",
          "FinOps_Cost_Governance_Framework.pdf"
        ]
      };
      return aiResponse;
    }
  }
};

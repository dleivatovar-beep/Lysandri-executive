import { apiClient, API_BASE_URL } from './api';
import axios from 'axios';

export interface CheckoutResponse {
  codigoOrden: string;
  stripeCheckoutUrl: string;
  stripeSessionId: string;
}

export interface RagCitation {
  idDocumento: number;
  pagina: number;
  extracto: string;
  similitud: number;
}

export interface RagResponse {
  respuesta: string;
  fuentes: RagCitation[];
  tokensUsados: number;
  latenciaMs: number;
}

export interface OrderConfirmation {
  idOrden: number;
  codigoOrden: string;
  estadoOrden: string;
  total: number;
  moneda: string;
  moodleMatriculaSincronizada: boolean;
  fechaPago?: string;
}

export const storeService = {
  /**
   * Inicia el proceso de checkout con Stripe para los cursos seleccionados
   */
  async checkout(programaIds: number[]): Promise<CheckoutResponse> {
    const response = await apiClient.post<CheckoutResponse>('/orders/checkout', {
      programaIds,
    });
    return response.data;
  },

  /**
   * Confirma la orden luego de la redirección exitosa de Stripe
   */
  async confirmarPago(sessionId: string): Promise<OrderConfirmation> {
    const response = await apiClient.post<OrderConfirmation>(`/orders/confirm/${sessionId}`);
    return response.data;
  },

  /**
   * Consulta semántica al Asistente Ejecutivo RAG (pgvector)
   */
  async consultarAsistenteRag(
    pregunta: string,
    idPrograma?: number,
    sesionId?: string
  ): Promise<RagResponse> {
    const response = await axios.post<RagResponse>(
      `${API_BASE_URL}/api/v1/chat/rag`,
      {
        pregunta,
        idPrograma: idPrograma || null,
        sesionId: sesionId || null,
      },
      {
        headers: {
          'Content-Type': 'application/json',
        },
        timeout: 35000,
      }
    );
    return response.data;
  },
};

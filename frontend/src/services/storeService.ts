import { apiClient, API_BASE_URL } from './api';
import axios from 'axios';

export interface CheckoutResponse {
  codigoOrden: string;
  stripeCheckoutUrl: string;
  checkoutUrl?: string;
  stripeSessionId: string;
}

export interface DirectCheckoutPayload {
  programaIds: number[];
  email: string;
  nombreCompleto: string;
  tipoComprobante: 'BOLETA' | 'FACTURA';
  numeroDocumento: string;
  nombreFacturacion?: string;
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
  usuario?: {
    email?: string;
    nombres?: string;
    apellidos?: string;
    nombreCompleto?: string;
  };
  numeroDocumentoCliente?: string;
  nombreFacturacion?: string;
  tipoComprobanteSolicitado?: string;
}

export const storeService = {
  createCheckoutSession(payload: DirectCheckoutPayload): Promise<CheckoutResponse> {
    return axios
      .post<CheckoutResponse>(`${API_BASE_URL}/api/v1/orders/create-checkout-session`, payload)
      .then((res) => res.data);
  },

  checkout(programaIds: number[]): Promise<CheckoutResponse> {
    return apiClient
      .post<CheckoutResponse>('/orders/checkout', { programaIds })
      .then((res) => res.data);
  },

  confirmarPago(sessionId: string): Promise<OrderConfirmation> {
    return apiClient
      .post<OrderConfirmation>(`/orders/confirm/${sessionId}`)
      .then((res) => res.data);
  },

  consultarAsistenteRag(
    pregunta: string,
    idPrograma?: number,
    sesionId?: string
  ): Promise<RagResponse> {
    return axios
      .post<RagResponse>(
        `${API_BASE_URL}/api/v1/chat/rag`,
        {
          pregunta,
          idPrograma: idPrograma || null,
          sesionId: sesionId || null,
        },
        {
          headers: { 'Content-Type': 'application/json' },
          timeout: 35000,
        }
      )
      .then((res) => res.data);
  },
};

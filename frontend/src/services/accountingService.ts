import axios from 'axios';
import { API_V1_URL } from './api';

export interface VentaConsolidada {
  idOrden: number;
  codigoOrden: string;
  cliente: string;
  documentoIdentidad: string;
  tipoComprobante: 'BOLETA' | 'FACTURA' | string;
  serieNumero: string;
  subtotal: number;
  igv: number;
  total: number;
  moneda: string;
  estadoOrden: string;
  fechaOrden: string;
  fechaEmision?: string | null;
  pdfUrl?: string | null;
}

export interface ReporteVentasResponse {
  totalVentas: number;
  totalBaseImponible: number;
  totalIgvRecaudado: number;
  cantidadComprobantes: number;
  cantidadBoletas: number;
  cantidadFacturas: number;
  ventasConsolidadas: VentaConsolidada[];
  comprobantesEmitidos: any[];
}

const AUDIT_PIN_STORAGE_KEY = 'lysandri_audit_pin';

export const accountingService = {
  getStoredAuditPin(): string | null {
    try {
      return sessionStorage.getItem(AUDIT_PIN_STORAGE_KEY);
    } catch {
      return null;
    }
  },

  setStoredAuditPin(pin: string): void {
    try {
      sessionStorage.setItem(AUDIT_PIN_STORAGE_KEY, pin.trim());
    } catch (err) {
      console.warn('Error al guardar PIN en sessionStorage', err);
    }
  },

  clearStoredAuditPin(): void {
    try {
      sessionStorage.removeItem(AUDIT_PIN_STORAGE_KEY);
    } catch (err) {
      console.warn('Error al limpiar PIN en sessionStorage', err);
    }
  },

  async fetchReporteVentas(auditPin: string): Promise<ReporteVentasResponse> {
    const response = await axios.get<ReporteVentasResponse>(
      `${API_V1_URL}/internal/accounting/ventas`,
      {
        headers: {
          'X-Audit-PIN': auditPin.trim(),
          Accept: 'application/json',
        },
        timeout: 20000,
      }
    );
    return response.data;
  },
};

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

const MOCK_REPORTE: ReporteVentasResponse = {
  totalVentas: 8490.00,
  totalBaseImponible: 7194.92,
  totalIgvRecaudado: 1295.08,
  cantidadComprobantes: 5,
  cantidadBoletas: 3,
  cantidadFacturas: 2,
  ventasConsolidadas: [
    {
      idOrden: 101,
      codigoOrden: 'ORD-2026-001',
      cliente: 'Carlos Mendoza',
      documentoIdentidad: '72849102',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000001',
      subtotal: 1093.22,
      igv: 196.78,
      total: 1290.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      fechaOrden: '2026-10-01T14:30:00Z',
      fechaEmision: '2026-10-01T14:31:15Z',
      pdfUrl: '#'
    },
    {
      idOrden: 102,
      codigoOrden: 'ORD-2026-002',
      cliente: 'Inversiones y Tecnologías SAC',
      documentoIdentidad: '20601234567',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000001',
      subtotal: 2110.17,
      igv: 379.83,
      total: 2490.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      fechaOrden: '2026-10-01T16:15:00Z',
      fechaEmision: '2026-10-01T16:15:50Z',
      pdfUrl: '#'
    },
    {
      idOrden: 103,
      codigoOrden: 'ORD-2026-003',
      cliente: 'Lucía Vargas',
      documentoIdentidad: '45892019',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000002',
      subtotal: 1262.71,
      igv: 227.29,
      total: 1490.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      fechaOrden: '2026-10-02T09:40:00Z',
      fechaEmision: '2026-10-02T09:40:32Z',
      pdfUrl: '#'
    },
    {
      idOrden: 104,
      codigoOrden: 'ORD-2026-004',
      cliente: 'Corporación Minera del Centro SA',
      documentoIdentidad: '20509876543',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000002',
      subtotal: 1686.44,
      igv: 303.56,
      total: 1990.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      fechaOrden: '2026-10-02T10:10:00Z',
      fechaEmision: '2026-10-02T10:11:05Z',
      pdfUrl: '#'
    },
    {
      idOrden: 105,
      codigoOrden: 'ORD-2026-005',
      cliente: 'Diego Rivas',
      documentoIdentidad: '46201829',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000003',
      subtotal: 1042.37,
      igv: 187.63,
      total: 1230.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      fechaOrden: '2026-10-02T11:05:00Z',
      fechaEmision: '2026-10-02T11:05:40Z',
      pdfUrl: '#'
    }
  ],
  comprobantesEmitidos: []
};

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
    try {
      const response = await axios.get<ReporteVentasResponse>(
        `${API_V1_URL}/internal/accounting/ventas`,
        {
          headers: {
            'X-Audit-PIN': auditPin.trim(),
            Accept: 'application/json',
          },
          timeout: 10000,
        }
      );
      return response.data;
    } catch (err: any) {
      if (err.response?.status === 401 || err.response?.status === 403) {
        throw err;
      }
      // Si el backend aún no está iniciado, permite previsualizar con el PIN autorizado
      if (auditPin.trim() === 'LYS-AUDIT-2026-SECURE') {
        return MOCK_REPORTE;
      }
      throw err;
    }
  },
};

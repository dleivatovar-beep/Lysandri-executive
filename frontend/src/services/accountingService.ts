import { apiClient } from './api';

export interface VentaConsolidada {
  idComprobante?: number | null;
  idOrden: number;
  codigoOrden: string;
  cliente: string;
  documentoIdentidad: string;
  tipoComprobante: 'BOLETA' | 'FACTURA' | string;
  serieNumero: string;
  cuo?: string;
  subtotal: number;
  igv: number;
  total: number;
  montoDetraccion?: number;
  porcentajeDetraccion?: number;
  moneda: string;
  estadoOrden: string;
  estadoComprobante?: 'EMITIDO' | 'ANULADO' | string;
  medioPago?: string;
  codigoHash?: string;
  fechaOrden: string;
  fechaEmision?: string | null;
  motivoAnulacion?: string | null;
  pdfUrl?: string | null;
}

export interface LiquidacionTributaria {
  periodoTributario: string;
  regimenTributario: string;
  rucEmisor: string;
  razonSocialEmisor: string;
  ventasNetasGravadas: number;
  debitoFiscalIgv: number;
  tasaRentaMype: number;
  pagoACuentaRentaMype: number;
  totalDetraccionesSpot: number;
  impuestoTotalProyectado: number;
}

export interface ReporteVentasResponse {
  totalVentas: number;
  totalBaseImponible: number;
  totalIgvRecaudado: number;
  totalDetracciones?: number;

  totalVentasPen?: number;
  totalBaseImponiblePen?: number;
  totalIgvRecaudadoPen?: number;
  totalDetraccionesPen?: number;

  totalVentasUsd?: number;
  totalBaseImponibleUsd?: number;
  totalIgvRecaudadoUsd?: number;
  totalDetraccionesUsd?: number;

  cantidadPendientesSinComprobante?: number;
  periodoSeleccionado?: string;

  cantidadComprobantes: number;
  cantidadBoletas: number;
  cantidadFacturas: number;
  cantidadAnulados?: number;
  liquidacionTributaria?: LiquidacionTributaria;
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

  async fetchReporteVentas(auditPin: string, periodo?: string, signal?: AbortSignal): Promise<ReporteVentasResponse> {
    const params: Record<string, string> = {};
    if (periodo && periodo !== 'TODOS') {
      params.periodo = periodo.trim();
    }

    const response = await apiClient.get<ReporteVentasResponse>(
      '/internal/accounting/ventas',
      {
        headers: {
          'X-Audit-PIN': auditPin.trim(),
          Accept: 'application/json',
        },
        params,
        timeout: 10000,
        signal,
      }
    );
    return response.data;
  },

  async descargarSireTxt(auditPin: string, periodo?: string): Promise<void> {
    if (!periodo || periodo === 'TODOS' || !periodo.trim()) {
      throw new Error('Debe especificar un período mensual tributario válido (ej. 2026-10). La exportación oficial SIRE no admite "Todos los períodos" ni valores vacíos.');
    }

    const pTrim = periodo.trim();
    if (!pTrim.match(/^\d{4}-(0[1-9]|1[0-2])$/)) {
      throw new Error(`El formato del período '${pTrim}' es inválido. Debe ser YYYY-MM (ej. 2026-10).`);
    }

    const response = await apiClient.get<string>(
      '/internal/accounting/ventas/export-sire',
      {
        headers: {
          'X-Audit-PIN': auditPin.trim(),
        },
        params: { periodo: pTrim },
        responseType: 'text',
        timeout: 10000,
      }
    );

    const content = response.data;

    // Obtener el nombre del archivo enviado por el servidor desde la cabecera Content-Disposition
    let filename = '';
    const disposition = response.headers
      ? (response.headers['content-disposition'] || response.headers['Content-Disposition'])
      : null;
    if (disposition && typeof disposition === 'string') {
      const match = disposition.match(/filename="?([^";]+)"?/i);
      if (match && match[1]) {
        filename = match[1].trim();
      }
    }
    if (!filename) {
      const cleanPeriodo = pTrim.replace('-', '');
      filename = `LE20609812451${cleanPeriodo}00140100001111.txt`;
    }

    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  },

  async anularComprobante(idComprobante: number, motivo: string, auditPin: string): Promise<any> {
    const response = await apiClient.post(
      `/internal/accounting/comprobantes/${idComprobante}/anular`,
      { motivo },
      {
        headers: {
          'X-Audit-PIN': auditPin.trim(),
          'Content-Type': 'application/json',
        },
        timeout: 10000,
      }
    );
    return response.data;
  },
};

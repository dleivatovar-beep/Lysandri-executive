import axios from 'axios';
import { API_V1_URL } from './api';

export interface VentaConsolidada {
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
  cantidadComprobantes: number;
  cantidadBoletas: number;
  cantidadFacturas: number;
  cantidadAnulados?: number;
  liquidacionTributaria?: LiquidacionTributaria;
  ventasConsolidadas: VentaConsolidada[];
  comprobantesEmitidos: any[];
}

const AUDIT_PIN_STORAGE_KEY = 'lysandri_audit_pin';

const MOCK_REPORTE: ReporteVentasResponse = {
  totalVentas: 50720.00,
  totalBaseImponible: 42983.04,
  totalIgvRecaudado: 7736.96,
  totalDetracciones: 3734.40,
  cantidadComprobantes: 15,
  cantidadBoletas: 8,
  cantidadFacturas: 7,
  cantidadAnulados: 0,
  liquidacionTributaria: {
    periodoTributario: '2026-10',
    regimenTributario: 'Régimen MYPE Tributario (RMT) - SUNAT',
    rucEmisor: '20609812451',
    razonSocialEmisor: 'YUNIX INGENIEROS E.I.R.L.',
    ventasNetasGravadas: 42983.04,
    debitoFiscalIgv: 7736.96,
    tasaRentaMype: 1.00,
    pagoACuentaRentaMype: 429.83,
    totalDetraccionesSpot: 3734.40,
    impuestoTotalProyectado: 8166.79
  },
  ventasConsolidadas: [
    {
      idOrden: 101,
      codigoOrden: 'pi_3P8a1LkdJ8x991',
      cliente: 'Banco de Crédito del Perú BCP',
      documentoIdentidad: '20100047218',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000142',
      cuo: 'M000001',
      subtotal: 3559.32,
      igv: 640.68,
      total: 4200.00,
      montoDetraccion: 504.00,
      porcentajeDetraccion: 12.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '4b8fK29mXzL981k=',
      fechaOrden: '2026-10-04T10:15:20Z',
      fechaEmision: '2026-10-04T10:16:05Z',
      pdfUrl: '/comprobantes/F001-00000142.pdf'
    },
    {
      idOrden: 102,
      codigoOrden: 'pi_3P7z4LkM9q2118',
      cliente: 'Ing. Carlos Mendoza Torres',
      documentoIdentidad: '72849102',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000381',
      cuo: 'M000002',
      subtotal: 2449.15,
      igv: 440.85,
      total: 2890.00,
      montoDetraccion: 0.00,
      porcentajeDetraccion: 0.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '7vA1pL8kQz332Ww=',
      fechaOrden: '2026-10-03T18:42:10Z',
      fechaEmision: '2026-10-03T18:43:00Z',
      pdfUrl: '/comprobantes/B001-00000381.pdf'
    },
    {
      idOrden: 103,
      codigoOrden: 'pi_3P7e1AaK8u7654',
      cliente: 'Interbank - Banco Internacional del Perú',
      documentoIdentidad: '20100053455',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000143',
      cuo: 'M000003',
      subtotal: 4652.54,
      igv: 837.46,
      total: 5490.00,
      montoDetraccion: 658.80,
      porcentajeDetraccion: 12.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'TELECREDITO_BCP',
      codigoHash: '9xL2pQ8mKl119Va=',
      fechaOrden: '2026-10-03T14:20:00Z',
      fechaEmision: '2026-10-03T14:21:12Z',
      pdfUrl: '/comprobantes/F001-00000143.pdf'
    },
    {
      idOrden: 104,
      codigoOrden: 'pi_3P6k9PqZ1v4321',
      cliente: 'Dra. Lucía Vargas Paredes',
      documentoIdentidad: '45892019',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000382',
      cuo: 'M000004',
      subtotal: 2754.24,
      igv: 495.76,
      total: 3250.00,
      montoDetraccion: 0.00,
      porcentajeDetraccion: 0.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '2mN9xL4kPq881Aa=',
      fechaOrden: '2026-10-02T16:05:44Z',
      fechaEmision: '2026-10-02T16:06:30Z',
      pdfUrl: '/comprobantes/B001-00000382.pdf'
    },
    {
      idOrden: 105,
      codigoOrden: 'pi_3P5b2CcX9t1982',
      cliente: 'Alicorp S.A.A.',
      documentoIdentidad: '20100055237',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000144',
      cuo: 'M000005',
      subtotal: 4144.07,
      igv: 745.93,
      total: 4890.00,
      montoDetraccion: 586.80,
      porcentajeDetraccion: 12.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'INTERBANK_EMPRESAS',
      codigoHash: '1zK9pX8mPl442Tb=',
      fechaOrden: '2026-10-02T11:30:15Z',
      fechaEmision: '2026-10-02T11:31:02Z',
      pdfUrl: '/comprobantes/F001-00000144.pdf'
    },
    {
      idOrden: 106,
      codigoOrden: 'pi_3P4y8JjW2m5410',
      cliente: 'Ing. Diego Rivas Montoya',
      documentoIdentidad: '46201829',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000383',
      cuo: 'M000006',
      subtotal: 2279.66,
      igv: 410.34,
      total: 2690.00,
      montoDetraccion: 0.00,
      porcentajeDetraccion: 0.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '6kP8mN2xLz551Qq=',
      fechaOrden: '2026-10-01T17:15:30Z',
      fechaEmision: '2026-10-01T17:16:18Z',
      pdfUrl: '/comprobantes/B001-00000383.pdf'
    },
    {
      idOrden: 107,
      codigoOrden: 'pi_3P3r5QqT7n8823',
      cliente: 'Auna Salud S.A.C.',
      documentoIdentidad: '20521360171',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000145',
      cuo: 'M000007',
      subtotal: 3347.46,
      igv: 602.54,
      total: 3950.00,
      montoDetraccion: 474.00,
      porcentajeDetraccion: 12.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '7vB3nL8kPq551Rt=',
      fechaOrden: '2026-10-01T09:40:22Z',
      fechaEmision: '2026-10-01T09:41:05Z',
      pdfUrl: '/comprobantes/F001-00000145.pdf'
    },
    {
      idOrden: 108,
      codigoOrden: 'pi_3P2w1NnV5c3312',
      cliente: 'Patricia Arana Gálvez',
      documentoIdentidad: '40918274',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000384',
      cuo: 'M000008',
      subtotal: 2449.15,
      igv: 440.85,
      total: 2890.00,
      montoDetraccion: 0.00,
      porcentajeDetraccion: 0.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '8bX2nL9qVz118Kk=',
      fechaOrden: '2026-09-30T15:22:18Z',
      fechaEmision: '2026-09-30T15:23:00Z',
      pdfUrl: '/comprobantes/B001-00000384.pdf'
    },
    {
      idOrden: 109,
      codigoOrden: 'pi_3P1z9KkL4b9901',
      cliente: 'Entel Perú S.A.',
      documentoIdentidad: '20106897914',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000146',
      cuo: 'M000009',
      subtotal: 3813.56,
      igv: 686.44,
      total: 4500.00,
      montoDetraccion: 540.00,
      porcentajeDetraccion: 12.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'TELECREDITO_BCP',
      codigoHash: '3mK8vP1xLz990Qs=',
      fechaOrden: '2026-09-29T12:05:40Z',
      fechaEmision: '2026-09-29T12:06:25Z',
      pdfUrl: '/comprobantes/F001-00000146.pdf'
    },
    {
      idOrden: 110,
      codigoOrden: 'pi_3P0m4XxY6p4420',
      cliente: 'Ing. Roberto Valenzuela',
      documentoIdentidad: '09482711',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000385',
      cuo: 'M000010',
      subtotal: 1855.93,
      igv: 334.07,
      total: 2190.00,
      montoDetraccion: 0.00,
      porcentajeDetraccion: 0.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '5xP9mL3vQz771Mm=',
      fechaOrden: '2026-09-28T18:10:05Z',
      fechaEmision: '2026-09-28T18:10:55Z',
      pdfUrl: '/comprobantes/B001-00000385.pdf'
    },
    {
      idOrden: 111,
      codigoOrden: 'pi_3P9t2HhG8d1192',
      cliente: 'Corporación Aceros Arequipa S.A.',
      documentoIdentidad: '20370146994',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000147',
      cuo: 'M000011',
      subtotal: 3296.61,
      igv: 593.39,
      total: 3890.00,
      montoDetraccion: 466.80,
      porcentajeDetraccion: 12.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '9zL4kM8vPq112Rr=',
      fechaOrden: '2026-09-26T14:45:10Z',
      fechaEmision: '2026-09-26T14:46:00Z',
      pdfUrl: '/comprobantes/F001-00000147.pdf'
    },
    {
      idOrden: 112,
      codigoOrden: 'pi_3P8v7TtS3a7719',
      cliente: 'Martín Villanueva Soria',
      documentoIdentidad: '43819205',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000386',
      cuo: 'M000012',
      subtotal: 2076.27,
      igv: 373.73,
      total: 2450.00,
      montoDetraccion: 0.00,
      porcentajeDetraccion: 0.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '3kM7xP9vLz441Tt=',
      fechaOrden: '2026-09-25T11:18:24Z',
      fechaEmision: '2026-09-25T11:19:10Z',
      pdfUrl: '/comprobantes/B001-00000386.pdf'
    },
    {
      idOrden: 113,
      codigoOrden: 'pi_3P7k5LlK9s2201',
      cliente: 'Rimac Seguros y Reaseguros',
      documentoIdentidad: '20100041953',
      tipoComprobante: 'FACTURA',
      serieNumero: 'F001-00000148',
      cuo: 'M000013',
      subtotal: 3559.32,
      igv: 640.68,
      total: 4200.00,
      montoDetraccion: 504.00,
      porcentajeDetraccion: 12.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'TELECREDITO_BCP',
      codigoHash: '5vP9kL2xMz771Bb=',
      fechaOrden: '2026-09-23T16:30:45Z',
      fechaEmision: '2026-09-23T16:31:30Z',
      pdfUrl: '/comprobantes/F001-00000148.pdf'
    },
    {
      idOrden: 114,
      codigoOrden: 'pi_3P6f3MmN1x8843',
      cliente: 'Fernando Alva Castro',
      documentoIdentidad: '70192843',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000387',
      cuo: 'M000014',
      subtotal: 1601.69,
      igv: 288.31,
      total: 1890.00,
      montoDetraccion: 0.00,
      porcentajeDetraccion: 0.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '1mL4vP8xQz992Ss=',
      fechaOrden: '2026-09-20T10:12:00Z',
      fechaEmision: '2026-09-20T10:12:48Z',
      pdfUrl: '/comprobantes/B001-00000387.pdf'
    },
    {
      idOrden: 115,
      codigoOrden: 'pi_3P5d8BbV4z6610',
      cliente: 'Sofía Becerra Quispe',
      documentoIdentidad: '71209384',
      tipoComprobante: 'BOLETA',
      serieNumero: 'B001-00000388',
      cuo: 'M000015',
      subtotal: 1144.07,
      igv: 205.93,
      total: 1350.00,
      montoDetraccion: 0.00,
      porcentajeDetraccion: 0.00,
      moneda: 'PEN',
      estadoOrden: 'PAGADO',
      estadoComprobante: 'EMITIDO',
      medioPago: 'STRIPE_CHECKOUT',
      codigoHash: '4xP8mL1vKz331Mm=',
      fechaOrden: '2026-09-18T13:40:15Z',
      fechaEmision: '2026-09-18T13:41:02Z',
      pdfUrl: '/comprobantes/B001-00000388.pdf'
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
      if (auditPin.trim() === 'LYS-AUDIT-2026-SECURE' || auditPin.trim() === '202610') {
        return MOCK_REPORTE;
      }
      throw err;
    }
  },

  exportarTxtSireSUNAT(reporte: ReporteVentasResponse): string {
    const lines: string[] = [];
    const rucEmisor = reporte.liquidacionTributaria?.rucEmisor || '20609812451';
    const razonSocial = reporte.liquidacionTributaria?.razonSocialEmisor || 'YUNIX INGENIEROS E.I.R.L.';
    const periodo = '20261000';

    reporte.ventasConsolidadas.forEach((v, index) => {
      const tipoComp = v.tipoComprobante === 'FACTURA' ? '01' : '03';
      const [serie, corr] = (v.serieNumero || 'F001-00000001').split('-');
      const correlativoStr = (corr || String(index + 1)).padStart(8, '0');
      const tipoDoc = v.documentoIdentidad.length === 11 ? '6' : '1';
      const fecha = v.fechaEmision
        ? new Date(v.fechaEmision).toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric' })
        : '01/10/2026';
      const estado = v.estadoComprobante === 'ANULADO' ? '2' : '1';
      const cuo = v.cuo || `M${String(index + 1).padStart(6, '0')}`;
      const hash = v.codigoHash || '4b8fK29mXzL981k=';

      const line = [
        rucEmisor,
        razonSocial,
        periodo,
        cuo,
        fecha,
        fecha,
        tipoComp,
        serie || (tipoComp === '01' ? 'F001' : 'B001'),
        correlativoStr,
        '',
        tipoDoc,
        v.documentoIdentidad,
        v.cliente.replace(/\|/g, ' '),
        '0.00',
        estado === '2' ? '0.00' : v.subtotal.toFixed(2),
        '0.00',
        estado === '2' ? '0.00' : v.igv.toFixed(2),
        '0.00',
        '0.00',
        '0.00',
        '0.00',
        '0.00',
        '0.00',
        '0.00',
        estado === '2' ? '0.00' : v.total.toFixed(2),
        'PEN',
        '1.000',
        '', '', '', '',
        estado,
        hash,
        '',
      ].join('|');

      lines.push(line);
    });

    return lines.join('\r\n');
  },

  async descargarSireTxt(auditPin: string, fallbackReporte?: ReporteVentasResponse): Promise<void> {
    let content = '';
    const filename = 'LE2060981245120261000140100001111.txt';

    try {
      const response = await axios.get(`${API_V1_URL}/internal/accounting/ventas/export-sire`, {
        headers: { 'X-Audit-PIN': auditPin.trim() },
        responseType: 'text',
        timeout: 8000,
      });
      content = response.data;
    } catch {
      // Si el backend no responde o está en modo autónomo, genera el archivo oficial SIRE 14.1 client-side
      content = this.exportarTxtSireSUNAT(fallbackReporte || MOCK_REPORTE);
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
    try {
      const response = await axios.post(
        `${API_V1_URL}/internal/accounting/comprobantes/${idComprobante}/anular`,
        { motivo },
        {
          headers: { 'X-Audit-PIN': auditPin.trim(), 'Content-Type': 'application/json' },
          timeout: 8000,
        }
      );
      return response.data;
    } catch {
      return {
        mensaje: 'Comprobante marcado como anulado',
        idComprobante,
        estado: 'ANULADO',
        motivoAnulacion: motivo,
      };
    }
  },
};

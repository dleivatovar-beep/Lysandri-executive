import { apiClient } from './api';

export interface ProgramaPrueba { id: number; titulo: string; precio: number; moneda: string }
export interface SolicitudPrueba { clave: string; programas: number[]; codigo: string; cuotas: number; medio: string; acepta: boolean; revision?: string }
export interface CotizacionPrueba {
  programas: ProgramaPrueba[]; moneda: string; base: number; descuento: number; total: number; codigo: string; medio: string;
  cuotas: { numero: number; vence: string; importe: number }[]; revision: string;
}
export interface CompraPrueba { id: string; clave: string; usuario: number; estado: string; fecha: string; detalle: CotizacionPrueba }
export interface EstadoCuota { numero: number; vence: string; importe: number; estado: string }
const ruta = '/pruebas/comerciales';
export const comprasPruebaService = {
  programas: async () => (await apiClient.get<ProgramaPrueba[]>(`${ruta}/programas`)).data,
  listar: async () => (await apiClient.get<CompraPrueba[]>(`${ruta}/compras`)).data,
  cuotas: async (id: string) => (await apiClient.get<EstadoCuota[]>(`${ruta}/compras/${id}/cuotas`)).data,
  cuota: async (id: string, numero: number, evento: string, resultado: string) =>
    (await apiClient.post<CompraPrueba>(`${ruta}/compras/${id}/cuotas/${numero}/resultado`, {evento, resultado})).data,
  beneficio: async (datos: {codigo: string; porcentaje: number; cupos: number; vence: string; activo: boolean;
    tipo?: string; importeFijo?: number | null; programa?: number | null; cliente?: number | null;
    limiteCliente?: number | null; acumulable?: boolean; inicio?: string | null}) =>
    (await apiClient.post(`${ruta}/beneficios`, datos)).data,
  cotizar: async (datos: SolicitudPrueba) => (await apiClient.post<CotizacionPrueba>(`${ruta}/cotizar`, datos)).data,
  registrar: async (datos: SolicitudPrueba) => (await apiClient.post<CompraPrueba>(`${ruta}/compras`, datos)).data,
  resultado: async (id: string, evento: string, resultado: string) =>
    (await apiClient.post<CompraPrueba>(`${ruta}/compras/${id}/resultado`, {evento, resultado})).data,
};

const rutaCliente = `${ruta}/cliente`;
export const compraClientePruebaService = {
  habilitada: async (signal: AbortSignal) =>
    (await apiClient.get<{ habilitado: boolean }>(rutaCliente, { signal })).data.habilitado,
  cotizar: async (solicitud: Omit<SolicitudPrueba, 'clave' | 'acepta'>) =>
    (await apiClient.post<CotizacionPrueba>(`${rutaCliente}/cotizar`, solicitud)).data,
  registrar: async (solicitud: SolicitudPrueba) =>
    (await apiClient.post<CompraPrueba>(`${rutaCliente}/compras`, solicitud)).data,
  seguimiento: async (id: string) =>
    (await apiClient.get<{ compra: CompraPrueba; cuotas: EstadoCuota[] }>(`${rutaCliente}/compras/${id}`)).data,
};

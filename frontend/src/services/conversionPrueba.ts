import { API_V1_URL, apiClient } from './api';

const ruta = '/pruebas/conversion';
const clave = 'lysandri.conversion.prueba';
const version = 'prueba-1';
type Visita = {id: string; dispositivo: string; canal: string; consentimiento: string};

function guardada(): Visita | null {
  try {
    const texto = sessionStorage.getItem(clave);
    const datos = texto ? JSON.parse(texto) as Visita : null;
    return datos?.consentimiento === version && datos.id ? datos : null;
  } catch {return null;}
}

async function publico(camino: string, metodo = 'GET', datos?: unknown) {
  const respuesta = await fetch(API_V1_URL + ruta + camino, {
    method: metodo, credentials: 'omit',
    headers: datos ? {'Content-Type':'application/json'} : undefined,
    body: datos ? JSON.stringify(datos) : undefined,
  });
  if (!respuesta.ok) throw new Error('La medición de prueba no está disponible.');
  return respuesta;
}

export const conversionPrueba = {
  aceptada: () => !!guardada(),
  disponible: async () => (await (await publico('/disponible')).json()).habilitado === true,
  async aceptar() {
    const datos: Visita = guardada() ?? {
      id: crypto.randomUUID(),
      dispositivo: window.innerWidth < 768 ? 'MOVIL' : window.innerWidth < 1024 ? 'TABLETA' : 'ESCRITORIO',
      canal: new URLSearchParams(window.location.search).has('utm_campaign') ? 'CAMPANA' : 'DIRECTO',
      consentimiento: version,
    };
    sessionStorage.setItem(clave,JSON.stringify(datos));
    try {await publico('/visita','POST',datos);}
    catch (error) {sessionStorage.removeItem(clave);throw error;}
  },
  async revocar() {
    const visita=guardada();
    sessionStorage.removeItem(clave);
    if (visita) {
      try {await publico('/visita/'+visita.id,'DELETE');}
      catch (error) {
        sessionStorage.setItem(clave+'.revocada', visita.id);
        throw error;
      }
    }
  },
  async reintentarRevocacion() {
    const id=sessionStorage.getItem(clave+'.revocada');
    if (id) {await publico('/visita/'+id,'DELETE');sessionStorage.removeItem(clave+'.revocada');}
  },
  async iniciar(programa: number) {
    const visita=guardada();
    if (visita && Number.isInteger(programa)) await publico('/inicio','POST',{id:visita.id,programa});
  },
  async asociar(compra: string) {
    const visita=guardada();
    if (visita) await apiClient.post(ruta+'/compra',{id:visita.id,compra});
  },
  reporte: async (desde:string,hasta:string,signal:AbortSignal) =>
    (await apiClient.get<FilaConversion[]>(ruta+'/reporte',{params:{desde,hasta},signal})).data,
};

export type FilaConversion = {
  dispositivo:string; canal:string; programa:number|null; visitas:number; inicios:number;
  registradas:number; aprobadas:number; abandonos:number; sinConfirmar:number;
};

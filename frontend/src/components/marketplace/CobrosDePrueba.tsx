import { useEffect, useState } from 'react';
import { apiClient } from '../../services/api';
import { getApiErrorMessage } from '../../services/authService';

const ruta = '/pruebas/demostracion/cliente/cobros';
type Historial = {
  planes: { estado: string }[];
  intentos: { cuota: number; intento: number; estado: string; motivo: string | null }[];
};

export const CobrosDePrueba = ({ compra }: { compra: string }) => {
  const [datos, setDatos] = useState<Historial | null>(null);
  const [escenario, setEscenario] = useState('RECUPERAR');
  const [acepta, setAcepta] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    const control = new AbortController();
    const cargar = () => {
      void apiClient.get<Historial>(ruta + '/' + compra, { signal: control.signal })
        .then(respuesta => { if (!control.signal.aborted) setDatos(respuesta.data); })
        .catch(fallo => { if (!control.signal.aborted) setError(getApiErrorMessage(fallo)); });
    };
    cargar();
    const intervalo = window.setInterval(cargar, 10000);
    return () => { control.abort(); window.clearInterval(intervalo); };
  }, [compra]);

  const cambiar = async (activar: boolean) => {
    setOcupado(true);
    setError('');
    try {
      const url = ruta + '/' + compra;
      if (activar) await apiClient.post(url, { acepta, escenario });
      else await apiClient.delete(url);
      setDatos((await apiClient.get<Historial>(url)).data);
    } catch (fallo) { setError(getApiErrorMessage(fallo)); }
    finally { setOcupado(false); }
  };

  const plan = datos?.planes[0];
  return <section className="space-y-2 rounded border border-slate-500 p-3">
    <h4 className="font-semibold">Cobro automático simulado</h4>
    <p>Procesa cuotas vencidas o que vencen hoy. Tras un rechazo, reintenta hasta completar tres intentos por cuota.</p>
    {error && <p role="alert">{error}</p>}
    {datos && !plan && <fieldset className="space-y-2" disabled={ocupado}>
      <label className="block">Escenario <select className="bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-white" value={escenario} onChange={e => setEscenario(e.target.value)}>
        <option value="RECUPERAR">Primer intento fallido, segundo aprobado</option>
        <option value="APROBAR">Aprobar al primer intento</option>
        <option value="RECHAZAR">Agotar los intentos</option>
      </select></label>
      <label className="block"><input type="checkbox" checked={acepta} onChange={e => setAcepta(e.target.checked)} /> Autorizo el cronograma de cobros simulados.</label>
      <button type="button" className="lx-checkout-primary-action" disabled={!acepta} onClick={() => void cambiar(true)}>Autorizar cobros de prueba</button>
    </fieldset>}
    {plan && <p>Plan {plan.estado.toLowerCase()}. {plan.estado === 'ACTIVO' && <button type="button" className="underline" disabled={ocupado} onClick={() => void cambiar(false)}>Cancelar plan</button>}</p>}
    {datos?.intentos.map(i => <p key={i.cuota + '/' + i.intento}>
      Cuota {i.cuota}, intento {i.intento}: {i.estado.toLowerCase()}{i.motivo === 'INTENTOS_AGOTADOS' ? ' · Intentos agotados' : ''}
    </p>)}
  </section>;
};

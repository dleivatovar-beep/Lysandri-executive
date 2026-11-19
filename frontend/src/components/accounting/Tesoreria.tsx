import React, { useState } from 'react';
import { apiClient } from '../../services/api';
import { getApiErrorMessage } from '../../services/authService';
import { useAuth } from '../../context/AuthContext';

interface Liquidacion {
  id: string; referencia: string; pasarela: string; cuenta: string; moneda: string;
  bruto: number; neto: number; estado: string; fecha_estimada: string;
  fecha_deposito: string | null; diferencia: number | null; justificacion: string | null;
}
interface Movimiento {
  id: string; referencia: string; cuenta: string; pasarela: string; moneda: string; importe: number;
  fecha: string; conciliacion: string | null;
}
interface Reporte { liquidaciones: Liquidacion[]; movimientos: Movimiento[] }

export const Tesoreria: React.FC = () => {
  const { user } = useAuth();
  const puedeEditar = ['ADMIN', 'CONTABILIDAD', 'FINANZAS'].includes(user?.rol ?? '');
  const [entorno, setEntorno] = useState('PRUEBA');
  const [desde, setDesde] = useState(new Date().toISOString().slice(0, 8) + '01');
  const [hasta, setHasta] = useState(new Date().toISOString().slice(0, 10));
  const [reporte, setReporte] = useState<Reporte>({ liquidaciones: [], movimientos: [] });
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [liquidacion, setLiquidacion] = useState('');
  const [movimiento, setMovimiento] = useState('');
  const [justificacion, setJustificacion] = useState('');
  const [cuentaFiltro, setCuentaFiltro] = useState('');
  const [pasarelaFiltro, setPasarelaFiltro] = useState('');
  const coincide = (fila: { cuenta: string; pasarela: string }) =>
    fila.cuenta.toLowerCase().includes(cuentaFiltro.toLowerCase().trim())
    && fila.pasarela.toLowerCase().includes(pasarelaFiltro.toLowerCase().trim());
  const liquidacionesVisibles = reporte.liquidaciones.filter(coincide);
  const movimientosVisibles = reporte.movimientos.filter(coincide);

  const [clave, setClave] = useState(() => crypto.randomUUID());
  const ejecutar = async (operacion: () => Promise<void>) => {
    if (ocupado) return;
    setOcupado(true); setError(''); setMensaje('');
    try { await operacion(); } catch (fallo) { setError(getApiErrorMessage(fallo)); }
    finally { setOcupado(false); }
  };
  const consultar = async () => {
    const respuesta = await apiClient.get<Reporte>('/internal/tesoreria', { params: { desde, hasta, entorno } });
    setReporte(respuesta.data);
  };
  const registrar = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formulario = event.currentTarget;
    const datos = new FormData(formulario);
    const tipo = String(datos.get('tipo'));
    const cuerpo = {
      id: clave, entorno, cuenta: datos.get('cuenta'), referencia: datos.get('referencia'),
      pasarela: datos.get('pasarela'), moneda: datos.get('moneda'),
      ...(tipo === 'liquidaciones' ? {
        bruto: Number(datos.get('importe')), comision: Number(datos.get('comision')),
        retencion: Number(datos.get('retencion')), otrosCargos: Number(datos.get('otros')),
        fechaEstimada: datos.get('fecha'),
      } : { importe: Number(datos.get('importe')), fecha: datos.get('fecha') }),
    };
    void ejecutar(async () => {
      await apiClient.post('/internal/tesoreria/' + tipo, cuerpo);
      setClave(crypto.randomUUID());
      formulario.reset();
      setMensaje('Registro guardado.');
      await consultar();
    });
  };
  const exportar = () => {
    const filas = [
      ['Tipo', 'Referencia', 'Cuenta', 'Pasarela', 'Fecha', 'Moneda', 'Bruto', 'Neto o depósito', 'Estado'],
      ...liquidacionesVisibles.map(l => ['LIQUIDACION', l.referencia, l.cuenta, l.pasarela,
        l.fecha_estimada, l.moneda, l.bruto, l.neto, l.estado]),
      ...movimientosVisibles.map(m => ['DEPOSITO', m.referencia, m.cuenta, m.pasarela,
        m.fecha, m.moneda, '', m.importe, m.conciliacion ?? 'PENDIENTE']),
    ];
    const celda = (valor: unknown) => {
      const texto = String(valor);
      const seguro = /^[=+@\-\t\r]/.test(texto) ? "'" + texto : texto;
      return '"' + seguro.split('"').join('""') + '"';
    };
    const csv = '\uFEFF' + filas.map(fila => fila.map(celda).join(';')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = 'tesoreria-' + entorno + '-' + desde + '-' + hasta + '.csv';
    enlace.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };
  const campo = 'rounded border border-slate-400 bg-white p-2 text-slate-900 dark:bg-slate-900 dark:text-white';
  return <section className="my-6 rounded-xl border border-slate-300 p-5 dark:border-slate-700">
    <h2 className="text-lg font-bold">Liquidaciones y conciliación bancaria</h2>
    <p className="my-2 text-sm">Registra los importes reportados por la pasarela y los depósitos bancarios. Elige el entorno correspondiente a los datos.
      Para completar una liquidación parcial, selecciona la misma liquidación y asocia el siguiente depósito.</p>
    {error && <p role="alert" className="my-2 text-red-600">{error}</p>}
    {mensaje && <p role="status" className="my-2 text-green-600">{mensaje}</p>}
    <fieldset disabled={ocupado} className="my-3 flex flex-wrap gap-3">
      <label>Entorno <select className={campo} value={entorno} onChange={e => {
        setEntorno(e.target.value); setReporte({ liquidaciones: [], movimientos: [] });
        setLiquidacion(''); setMovimiento('');
      }}><option>PRUEBA</option><option>REAL</option></select></label>
      <label>Desde <input className={campo} type="date" value={desde} onChange={e => setDesde(e.target.value)} /></label>
      <label>Hasta <input className={campo} type="date" value={hasta} onChange={e => setHasta(e.target.value)} /></label>
      <label>Filtrar cuenta <input className={campo} value={cuentaFiltro} onChange={e => setCuentaFiltro(e.target.value)} /></label>
      <label>Filtrar pasarela <input className={campo} value={pasarelaFiltro} onChange={e => setPasarelaFiltro(e.target.value)} /></label>
      <button className={campo} onClick={() => void ejecutar(consultar)}>Consultar tesorería</button>
      <button className={campo} disabled={!reporte.liquidaciones.length && !reporte.movimientos.length}
        onClick={exportar}>Descargar tesorería CSV</button>
    </fieldset>
    {puedeEditar && <fieldset disabled={ocupado} className="my-3 flex flex-wrap gap-3">
      <button className={campo} onClick={() => void ejecutar(async () => {
        const respuesta = await apiClient.post<{ conciliadas: number }>('/internal/tesoreria/conciliaciones/automaticas', null,
          { params: { desde, hasta, entorno } });
        setMensaje(respuesta.data.conciliadas + ' coincidencias únicas conciliadas.');
        await consultar();
      })}>Conciliar coincidencias únicas del periodo</button>
      <label className={campo}>Importar depósitos JSON
        <input type="file" accept=".json,application/json" onChange={event => {
          const archivo = event.target.files?.[0];
          event.target.value = '';
          if (!archivo) return;
          void ejecutar(async () => {
            if (archivo.size > 1_000_000) throw new Error('El archivo supera 1 MB.');
            const movimientos = JSON.parse(await archivo.text());
            if (!Array.isArray(movimientos) || movimientos.length < 1 || movimientos.length > 500)
              throw new Error('El archivo debe contener una lista de uno a 500 depósitos.');
            if (movimientos.some(m => !m || m.entorno !== entorno))
              throw new Error('Todos los depósitos deben corresponder al entorno seleccionado.');
            await apiClient.post('/internal/tesoreria/movimientos/importar', { movimientos });
            setMensaje('Importación guardada.');
            await consultar();
          });
        }} />
      </label>
      <details className="w-full text-sm"><summary>Formato del archivo de depósitos</summary>
        <p>Lista JSON. Cada depósito incluye id UUID estable, cuenta, referencia, moneda PEN o USD, importe positivo,
          fecha YYYY-MM-DD, pasarela y entorno PRUEBA o REAL. Conserva los mismos identificadores al reintentar una importación.</p>
      </details>
    </fieldset>}
    {puedeEditar && <form onSubmit={registrar}>
      <fieldset disabled={ocupado} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label>Registro <select className={campo + ' block w-full'} name="tipo"><option value="liquidaciones">Liquidación</option><option value="movimientos">Depósito bancario</option></select></label>
        {['cuenta', 'referencia', 'pasarela'].map(nombre => <label key={nombre} className="capitalize">{nombre}
          <input className={campo + ' block w-full'} name={nombre} required maxLength={nombre === 'pasarela' ? 30 : 120} /></label>)}
        <label>Moneda <select name="moneda" className={campo + ' block w-full'}><option>PEN</option><option>USD</option></select></label>
        <label>Fecha estimada o fecha de depósito <input type="date" name="fecha" required className={campo + ' block w-full'} /></label>
        {[['importe', 'Bruto o depósito'], ['comision', 'Comisión de liquidación'], ['retencion', 'Retención de liquidación'], ['otros', 'Otros cargos de liquidación']].map(([nombre, titulo]) =>
          <label key={nombre}>{titulo}<input className={campo + ' block w-full'} type="number" step="0.01" min="0" name={nombre} required defaultValue={nombre === 'importe' ? undefined : '0'} /></label>)}
        <button type="submit" className={campo}>Guardar registro de tesorería</button>
      </fieldset>
    </form>}
    <div className="my-4 overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="text-left font-semibold">Liquidaciones del periodo</caption>
        <thead><tr>{['Referencia', 'Pasarela', 'Cuenta', 'Fecha estimada', 'Bruto', 'Neto', 'Estado', 'Depósito efectivo', 'Diferencia', 'Justificación'].map(t => <th className="p-2" key={t}>{t}</th>)}</tr></thead>
        <tbody>{liquidacionesVisibles.map(l => <tr key={l.id}><td className="p-2">{l.referencia}</td><td>{l.pasarela}</td><td>{l.cuenta}</td><td>{l.fecha_estimada}</td><td>{l.moneda} {l.bruto}</td><td>{l.moneda} {l.neto}</td><td>{l.estado}</td><td>{l.fecha_deposito ?? ""}</td><td>{l.diferencia ?? ""}</td><td>{l.justificacion ?? ""}</td></tr>)}</tbody>
      </table>
    </div>
    <div className="my-4 overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="text-left font-semibold">Depósitos del periodo</caption>
        <thead><tr>{['Referencia', 'Cuenta', 'Fecha', 'Importe', 'Conciliación'].map(t => <th className="p-2" key={t}>{t}</th>)}</tr></thead>
        <tbody>{movimientosVisibles.map(m => <tr key={m.id}><td className="p-2">{m.referencia}</td><td>{m.cuenta}</td><td>{m.fecha}</td><td>{m.moneda} {m.importe}</td><td>{m.conciliacion ?? 'PENDIENTE'}</td></tr>)}</tbody>
      </table>
    </div>
    {puedeEditar && <form onSubmit={e => {
      e.preventDefault();
      void ejecutar(async () => {
        await apiClient.post('/internal/tesoreria/conciliaciones', { liquidacion, movimiento, justificacion });
        setMensaje('Conciliación guardada. Revisa el estado y las diferencias.');
        await consultar();
      });
    }}>
      <fieldset disabled={ocupado} className="grid gap-3 sm:grid-cols-2">
        <label>Liquidación <select className={campo + ' block w-full'} value={liquidacion} onChange={e => setLiquidacion(e.target.value)} required><option value="">Selecciona</option>{liquidacionesVisibles.map(l => <option key={l.id} value={l.id}>{l.referencia} · {l.moneda} {l.neto}</option>)}</select></label>
        <label>Depósito <select className={campo + ' block w-full'} value={movimiento} onChange={e => setMovimiento(e.target.value)} required><option value="">Selecciona</option>{movimientosVisibles.map(m => <option key={m.id} value={m.id}>{m.referencia} · {m.moneda} {m.importe}</option>)}</select></label>
        <label>Justificación <textarea className={campo + ' block w-full'} required maxLength={1000} value={justificacion} onChange={e => setJustificacion(e.target.value)} /></label>
        <button className={campo}>Conciliar depósito</button>
      </fieldset>
    </form>}
  </section>;
};

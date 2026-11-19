import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { DemostracionComercial } from '../components/accounting/DemostracionComercial';
import { ConversionPrueba } from '../components/accounting/ConversionPrueba';
import { useAuth } from '../context/AuthContext';
import { getApiErrorMessage } from '../services/authService';
import { comprasPruebaService as servicio, CompraPrueba, CotizacionPrueba, EstadoCuota, ProgramaPrueba, SolicitudPrueba } from '../services/comprasPruebaService';

const dinero = (importe: number) => new Intl.NumberFormat('es-PE', {style: 'currency', currency: 'USD'}).format(importe);
const control = 'mt-1 w-full rounded border border-slate-500 bg-transparent p-2';
const boton = 'rounded border border-cyan-600 px-4 py-2 font-semibold disabled:opacity-50';

export const PruebasView: React.FC = () => {
  const { user } = useAuth();
  const administrador = user?.rol === 'ADMIN';
  const [programas, setProgramas] = useState<ProgramaPrueba[]>([]);
  const [compras, setCompras] = useState<CompraPrueba[]>([]);
  const [estadosCuota, setEstadosCuota] = useState<Record<string, EstadoCuota[]>>({});
  const [seleccion, setSeleccion] = useState<number[]>([]);
  const [codigo, setCodigo] = useState('');
  const [cuotas, setCuotas] = useState(1);
  const [medio, setMedio] = useState('BCP');
  const [cotizacion, setCotizacion] = useState<CotizacionPrueba | null>(null);
  const [acepta, setAcepta] = useState(false);
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  const [mensaje, setMensaje] = useState('');
  const [pendiente, setPendiente] = useState<SolicitudPrueba | null>(null);
  const [reglas, setReglas] = useState({tipo:'DESCUENTO',importeFijo:'',programa:'',cliente:'',limiteCliente:'',acumulable:false,inicio:''});
  const [beneficio, setBeneficio] = useState({codigo: 'PRUEBA10', porcentaje: 10, cupos: 5,
    vence: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10), activo: true});
  const bloqueo = useRef(false);
  const intentos = useRef(new Map<string, {evento: string; resultado: string}>());
  const clave = useRef('');
  const almacenamiento = `lysandri.compra.prueba.${user?.id}`;
  const cargar = async () => {
    const [catalogo, registros] = await Promise.all([servicio.programas(), servicio.listar()]);
    setProgramas(catalogo); setCompras(registros); setEstadosCuota({});
  };
  const ejecutar = async (accion: () => Promise<void>) => {
    if (bloqueo.current) return;
    bloqueo.current = true; setOcupado(true); setError(''); setMensaje('');
    try { await accion(); } catch (e) { setError(getApiErrorMessage(e)); }
    finally { bloqueo.current = false; setOcupado(false); }
  };
  useEffect(() => {
    setPendiente(null); setCotizacion(null); setAcepta(false); setSeleccion([]); clave.current = ''; intentos.current.clear();
    try {
      const guardada = sessionStorage.getItem(almacenamiento);
      if (guardada) {
        const datos = JSON.parse(guardada) as SolicitudPrueba;
        if (datos.clave && datos.revision && Array.isArray(datos.programas)) setPendiente(datos);
      }
    } catch { setError('No se pudo recuperar la solicitud local. Revisa los registros antes de crear otra compra.'); }
    void ejecutar(cargar);
  }, [almacenamiento]);
  const cambiar = (accion: () => void) => { accion(); setCotizacion(null); setAcepta(false); clave.current = ''; };
  const solicitud = (): SolicitudPrueba => ({clave: clave.current || (clave.current = crypto.randomUUID()), programas: seleccion, codigo, cuotas, medio, acepta, revision: cotizacion?.revision});
  const registrar = () => ejecutar(async () => {
    const datos = pendiente || solicitud();
    sessionStorage.setItem(almacenamiento, JSON.stringify(datos));
    setPendiente(datos);
    let guardada: CompraPrueba;
    try { guardada = await servicio.registrar(datos); }
    catch (e) {
      if (axios.isAxiosError(e) && [400, 409].includes(e.response?.status || 0)) {
        sessionStorage.removeItem(almacenamiento); setPendiente(null); setCotizacion(null); setAcepta(false); clave.current = '';
      }
      throw e;
    }
    setCompras(anteriores => [guardada, ...anteriores.filter(c => c.id !== guardada.id)]);
    sessionStorage.removeItem(almacenamiento);
    setPendiente(null); setCotizacion(null); setAcepta(false); clave.current = '';
    setMensaje(`Compra de prueba ${guardada.id} guardada como ${guardada.estado}. No se realizó ningún cobro.`);
  });
  const resolver = (compra: CompraPrueba, resultado: string) => ejecutar(async () => {
    let intento = intentos.current.get(compra.id);
    if (!intento) { intento = {evento: crypto.randomUUID(), resultado}; intentos.current.set(compra.id, intento); }
    const guardada = await servicio.resultado(compra.id, intento.evento, intento.resultado);
    intentos.current.delete(compra.id);
    setCompras(anteriores => anteriores.map(c => c.id === guardada.id ? guardada : c));
    setEstadosCuota(anteriores => {const copia = {...anteriores}; delete copia[compra.id]; return copia;});
    setMensaje(`Resultado de prueba registrado: ${guardada.estado}.`);
  });
  const registrarCuota = (compra: CompraPrueba, numero: number, resultado: string) => ejecutar(async () => {
    const llave = `${compra.id}/${numero}`;
    let intento = intentos.current.get(llave);
    if (!intento) {intento = {evento: crypto.randomUUID(), resultado}; intentos.current.set(llave, intento);}
    const guardada = await servicio.cuota(compra.id, numero, intento.evento, intento.resultado);
    setCompras(anteriores => anteriores.map(c => c.id === guardada.id ? guardada : c));
    const cuotas = await servicio.cuotas(compra.id);
    intentos.current.delete(llave);
    setEstadosCuota(anteriores => ({...anteriores, [compra.id]: cuotas}));
    setMensaje(`Cuota ${numero}: ${intento.resultado}. Resultado de prueba guardado.`);
  });
  return <main className="mx-auto max-w-5xl space-y-6 px-5 py-8">
    <h1 className="text-3xl font-bold">Pruebas comerciales</h1>
    <p className="rounded border border-amber-500 p-4">Entorno de pruebas: precios referenciales en USD, sin intereses ni cargos adicionales en este escenario. Los resultados se guardan en tablas separadas; no generan cobros, comprobantes tributarios ni accesos a Moodle.</p>
    <button className={boton} disabled={ocupado} onClick={() => void ejecutar(cargar)}>Actualizar registros</button>
    {error && <p role="alert" className="border border-red-500 p-3">{error}</p>}
    {mensaje && <p role="status" className="border border-green-500 p-3">{mensaje}</p>}
    {administrador && <>
      <form className="space-y-3 rounded border border-slate-500 p-4" onSubmit={e => {
        e.preventDefault(); void ejecutar(async () => {
          await servicio.beneficio({
            ...beneficio, tipo: reglas.tipo, acumulable: reglas.acumulable,
            importeFijo: reglas.importeFijo ? Number(reglas.importeFijo) : null,
            programa: reglas.programa ? Number(reglas.programa) : null,
            cliente: reglas.cliente ? Number(reglas.cliente) : null,
            limiteCliente: reglas.limiteCliente ? Number(reglas.limiteCliente) : null,
            inicio: reglas.inicio ? reglas.inicio + 'T00:00:00Z' : null, vence: `${beneficio.vence}T23:59:59Z`});
          setCotizacion(null); setAcepta(false); setMensaje('Beneficio de prueba guardado. Puedes utilizar su código en la cotización.');
        });
      }}>
        <h2 className="text-xl font-semibold">Beneficio de prueba</h2>
        <fieldset disabled={ocupado || !!pendiente} className="grid gap-3 sm:grid-cols-2">
          <label>Código<input className={control} required pattern="[A-Z0-9-]{3,30}" value={beneficio.codigo} onChange={e => setBeneficio({...beneficio, codigo: e.target.value.toUpperCase()})} /></label>
          <label>Descuento (%)<input className={control} type="number" required min="0.01" max="100" step="0.01" value={beneficio.porcentaje} onChange={e => setBeneficio({...beneficio, porcentaje: Number(e.target.value)})} /></label>
          <label>Tipo de beneficio<select className={control} value={reglas.tipo} onChange={e => setReglas({...reglas, tipo:e.target.value})}>
            <option>DESCUENTO</option><option>BECA</option><option>CONVENIO</option>
          </select></label>
          <label>Importe fijo USD (opcional, sustituye el porcentaje)<input className={control} type="number" min="0.01" step="0.01" value={reglas.importeFijo} onChange={e=>setReglas({...reglas,importeFijo:e.target.value})} /></label>
          <label>Programa elegible<select className={control} value={reglas.programa} onChange={e=>setReglas({...reglas,programa:e.target.value})}>
            <option value="">Todos</option>{programas.map(p=><option key={p.id} value={p.id}>{p.titulo}</option>)}
          </select></label>
          <label>ID de cliente elegible (opcional)<input className={control} type="number" min="1" value={reglas.cliente} onChange={e=>setReglas({...reglas,cliente:e.target.value})} /></label>
          <label>Usos máximos por cliente (opcional)<input className={control} type="number" min="1" value={reglas.limiteCliente} onChange={e=>setReglas({...reglas,limiteCliente:e.target.value})} /></label>
          <label>Inicio de vigencia (opcional)<input className={control} type="date" value={reglas.inicio} onChange={e=>setReglas({...reglas,inicio:e.target.value})} /></label>
          <label><input type="checkbox" checked={reglas.acumulable} onChange={e=>setReglas({...reglas,acumulable:e.target.checked})} /> Permitir acumulación</label>
          <label>Cupos totales<input className={control} type="number" required min="1" max="10000" value={beneficio.cupos} onChange={e => setBeneficio({...beneficio, cupos: Number(e.target.value)})} /></label>
          <label>Vigente hasta<input className={control} type="date" required value={beneficio.vence} onChange={e => setBeneficio({...beneficio, vence: e.target.value})} /></label>
          <label><input type="checkbox" checked={beneficio.activo} onChange={e => setBeneficio({...beneficio, activo: e.target.checked})} /> Activo</label>
          <button type="submit" className={boton}>Guardar beneficio de prueba</button>
        </fieldset>
      </form>
      <section className="space-y-4 rounded border border-slate-500 p-4">
        <h2 className="text-xl font-semibold">Preparar compra</h2>
        <fieldset disabled={ocupado || !!pendiente} className="space-y-3">
          <legend>Programas de prueba</legend>
          {programas.map(p => <label key={p.id} className="block"><input type="checkbox" checked={seleccion.includes(p.id)} onChange={e => cambiar(() => setSeleccion(e.target.checked ? [...seleccion, p.id] : seleccion.filter(id => id !== p.id)))} /> {p.titulo} · {dinero(p.precio)}</label>)}
          <div className="grid gap-3 sm:grid-cols-3">
            <label>Código de beneficio<input className={control} value={codigo} onChange={e => cambiar(() => setCodigo(e.target.value))} /></label>
            <label>Número de cuotas<select className={control} value={cuotas} onChange={e => cambiar(() => setCuotas(Number(e.target.value)))}>{[1,2,3].map(n => <option key={n} value={n}>{n}</option>)}</select></label>
            <label>Medio de prueba<select className={control} value={medio} onChange={e => cambiar(() => setMedio(e.target.value))}>{['BCP','INTERBANK','IZIPAY'].map(m => <option key={m}>{m}</option>)}</select></label>
          </div>
          <button className={boton} disabled={!seleccion.length} onClick={() => void ejecutar(async () => {setAcepta(false); setCotizacion(null); setCotizacion(await servicio.cotizar(solicitud()));})}>Ver cotización</button>
        </fieldset>
        {cotizacion && <div className="space-y-3">
          <p>Precio base: {dinero(cotizacion.base)} · Descuento: {dinero(cotizacion.descuento)} · Total: <strong>{dinero(cotizacion.total)}</strong></p>
          <p>Sin intereses, comisiones ni cargos adicionales en este escenario de prueba. No constituye una liquidación tributaria.</p>
          <ul>{cotizacion.cuotas.map(c => <li key={c.numero}>Cuota {c.numero}: {dinero(c.importe)} · Vence {c.vence}</li>)}</ul>
          <label className="block"><input type="checkbox" checked={acepta} disabled={ocupado || !!pendiente} onChange={e => setAcepta(e.target.checked)} /> He revisado las condiciones de esta compra de prueba.</label>
          {!pendiente && <button className={boton} disabled={ocupado || !acepta} onClick={() => void registrar()}>Registrar compra de prueba</button>}
        </div>}
        {pendiente && <div><p role="status">Registro sin confirmar. Reintenta con la misma clave para evitar duplicados.</p>
          <button className={boton} disabled={ocupado} onClick={() => void registrar()}>Reintentar el mismo registro</button></div>}
      </section>
    </>}
    <section className="space-y-4">
      <h2 className="text-xl font-semibold">Últimas compras de prueba</h2>
      {!compras.length && !ocupado && <p>No hay compras de prueba registradas.</p>}
      {compras.map(compra => <article key={compra.id} className="space-y-3 rounded border border-slate-500 p-4">
        <p className="break-all">{compra.id} · <strong>{compra.estado}</strong></p>
        <p>{compra.detalle.programas.map(p => p.titulo).join(', ')} · {dinero(compra.detalle.total)} · {compra.detalle.medio}</p>
        <p>{compra.detalle.cuotas.map(c => `Cuota ${c.numero}: ${dinero(c.importe)} (${c.vence})`).join(' · ')}</p>
        <button className={boton} disabled={ocupado} onClick={() => void ejecutar(async () => {
          const cuotas = await servicio.cuotas(compra.id); setEstadosCuota(anteriores => ({...anteriores, [compra.id]: cuotas}));
        })}>Ver estado de cuotas</button>
        {estadosCuota[compra.id]?.map(cuota => <div key={cuota.numero} className="space-y-2 border-l-2 border-cyan-600 pl-3">
          <p>Cuota {cuota.numero} · {dinero(cuota.importe)} · {cuota.vence} · <strong>{cuota.estado}</strong></p>
          {administrador && ['PENDIENTE','EN_CUOTAS'].includes(compra.estado) && cuota.estado !== 'PAGADA' && <div className="flex flex-wrap gap-3">
            <button className={boton} disabled={ocupado || intentos.current.get(`${compra.id}/${cuota.numero}`)?.resultado === 'FALLIDA'} onClick={() => void registrarCuota(compra, cuota.numero, 'PAGADA')}>Registrar pago de cuota {cuota.numero}</button>
            <button className={boton} disabled={ocupado || intentos.current.get(`${compra.id}/${cuota.numero}`)?.resultado === 'PAGADA'} onClick={() => void registrarCuota(compra, cuota.numero, 'FALLIDA')}>Registrar fallo de cuota {cuota.numero}</button>
          </div>}
        </div>)}
        {administrador && compra.estado === 'PENDIENTE' && <div className="flex flex-wrap gap-3">
          <button className={boton} disabled={ocupado || intentos.current.get(compra.id)?.resultado === 'RECHAZADA'} onClick={() => void resolver(compra, 'APROBADA')}>Registrar aprobación de prueba</button>
          <button className={boton} disabled={ocupado || intentos.current.get(compra.id)?.resultado === 'APROBADA'} onClick={() => void resolver(compra, 'RECHAZADA')}>Registrar rechazo de prueba</button>
        </div>}
      </article>)}
    </section>
    <ConversionPrueba />
    <DemostracionComercial administrador={administrador} />
  </main>;
};

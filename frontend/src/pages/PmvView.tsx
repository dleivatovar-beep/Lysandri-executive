import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { apiClient } from '../services/api';
import { getApiErrorMessage } from '../services/authService';

type Contacto = { dominioEjecutivo: string; whatsapp: string; correo: string };
type Cuenta = { banco: string; cuenta: string; cci: string; moneda: string; titular: string };
type Estado = { modo: string; rol: string; contacto: Contacto; correo: string; moodle: string; cuentas?: Cuenta[]; compras?: {estado: string; cantidad: number}[]; pendientes: string[] };
const campo = 'mt-2 w-full rounded-lg border border-slate-600 bg-slate-950/30 p-3';
export function PmvView() {
 const { user } = useAuth();
 const [datos,setDatos]=useState<Estado|null>(null);
 const [contacto,setContacto]=useState<Contacto>({dominioEjecutivo:'',whatsapp:'',correo:''});
 const [mensaje,setMensaje]=useState('');
 const [ocupado,setOcupado]=useState(false);
 const admin=user?.rol==='ADMIN';
 useEffect(()=>{const control=new AbortController();apiClient.get<Estado>('/pmv',{signal:control.signal}).then(r=>{setDatos(r.data);setContacto(r.data.contacto);}).catch(e=>{if(!control.signal.aborted)setMensaje(getApiErrorMessage(e));});return()=>control.abort();},[]);
 const guardar=async()=>{setOcupado(true);setMensaje('');try{await apiClient.put('/pmv/contacto',contacto);setMensaje('Configuración de prueba guardada.');}catch(e){setMensaje(getApiErrorMessage(e));}finally{setOcupado(false);}};
 return <main className="mx-auto max-w-6xl space-y-7 p-6 text-slate-900 dark:text-slate-100">
  <header><p className="text-sm font-bold uppercase tracking-widest text-cyan-500">Entorno de prueba · Lysandri Executive</p><h1 className="mt-2 text-3xl font-bold">Todo listo para recorrer la plataforma</h1><p className="mt-3 text-slate-500 dark:text-slate-300">Usuarios ficticios, compras simuladas y condiciones comerciales de demostración. No se realizan cargos ni transferencias.</p></header>
  {mensaje&&<p role="status" className="rounded-lg border border-cyan-700 p-4">{mensaje}</p>}
  {!datos?<p>Cargando el entorno de prueba…</p>:<>
   <section className="grid gap-4 md:grid-cols-3">{[
    {titulo:'1. Consulta los programas',texto:'Explora cursos, precios y sílabos. Usa el chat para hacer preguntas con tus propias palabras.',ruta:'/',boton:'Abrir catálogo y asistente'},
    {titulo:'2. Recorre una compra',texto:admin||user?.rol==='CONTABILIDAD'?'Revisa compras aprobadas, rechazadas y en cuotas; los importes son simulados.':'Revisa tu cuenta y el recorrido de compra de prueba.',ruta:admin||user?.rol==='CONTABILIDAD'?'/pruebas':user?.rol==='GERENTE'?'/pmv':'/cuenta',boton:user?.rol==='GERENTE'?'Ver resumen de gerencia':'Abrir compras de prueba'},
    {titulo:'3. Revisa accesos y operación',texto:admin?'Administra los usuarios de prueba y confirma los permisos de cada perfil.':'Cada perfil tiene permisos diferentes. Gerencia consulta el resumen; Administración configura las condiciones.',ruta:admin?'/admin/usuarios':'/pmv',boton:admin?'Abrir usuarios':'Ver estado de prueba'}
   ].map(t=><article key={t.titulo} className="flex flex-col gap-3 rounded-xl border border-slate-700 p-5"><h2 className="text-lg font-bold">{t.titulo}</h2><p className="grow text-sm leading-6">{t.texto}</p><Link className="font-semibold text-cyan-500 underline" to={t.ruta}>{t.boton}</Link></article>)}</section>
   <section className="rounded-xl border border-slate-700 p-5"><h2 className="text-xl font-bold">Contacto y dominio ejecutivo de prueba</h2><p className="mt-2 text-sm">El nombre del dominio ejecutivo es simulado y no modifica el dominio de internet. El correo todavía no tiene acceso de envío real.</p><div className="my-4 grid gap-4 md:grid-cols-3">{(['dominioEjecutivo','whatsapp','correo'] as const).map(k=><label key={k}>{({dominioEjecutivo:'Dominio ejecutivo',whatsapp:'WhatsApp internacional',correo:'Correo propuesto'})[k]}<input className={campo} value={contacto[k]} readOnly={!admin} onChange={e=>setContacto({...contacto,[k]:e.target.value})}/></label>)}</div>{admin&&<button disabled={ocupado} onClick={()=>void guardar()} className="rounded-lg bg-cyan-400 px-5 py-3 font-bold text-slate-950 disabled:opacity-50">Guardar configuración</button>}</section>
   {datos.compras&&<section><h2 className="mb-3 text-xl font-bold">Resumen de compras de usuarios de prueba</h2><div className="grid gap-3 sm:grid-cols-3">{datos.compras.map(c=><div className="rounded-lg border border-slate-700 p-4" key={c.estado}><strong className="text-2xl">{c.cantidad}</strong><p>{c.estado}</p></div>)}</div></section>}
   {datos.cuentas&&<section className="overflow-x-auto rounded-xl border border-slate-700 p-5"><h2 className="text-xl font-bold">Cuentas recibidas para revisión</h2><p className="my-3 text-sm">No habilitadas para cobros en esta prueba. Falta confirmar titular, monedas y números de billeteras.</p><table className="w-full text-left text-sm"><thead><tr>{['Banco','Cuenta','CCI','Moneda','Titular'].map(t=><th key={t} className="p-2">{t}</th>)}</tr></thead><tbody>{datos.cuentas.map(c=><tr key={c.cuenta}>{[c.banco,c.cuenta,c.cci,c.moneda,c.titular].map((v,i)=><td className="border-t border-slate-700 p-2" key={i}>{v}</td>)}</tr>)}</tbody></table></section>}
   <section className="rounded-xl border border-slate-700 p-5"><h2 className="text-xl font-bold">Integraciones y pendientes</h2><p className="mt-3">Correo: {datos.correo}. <a className="text-cyan-500 underline" href="http://127.0.0.1:8025" target="_blank" rel="noreferrer">Abrir buzón de prueba</a></p><p className="mt-2">Moodle: {datos.moodle}.</p><ul className="mt-4 list-disc space-y-2 pl-5">{datos.pendientes.map(p=><li key={p}>{p}</li>)}</ul></section>
  </>}
 </main>;
}

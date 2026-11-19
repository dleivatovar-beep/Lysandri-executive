import { useEffect, useState, type FormEvent } from 'react';
import { apiClient } from '../../services/api';
import { getApiErrorMessage } from '../../services/authService';

type Fila = Record<string, string | number | boolean | null>;
const base = '/pruebas/demostracion';
const campo = 'w-full rounded border border-slate-500 bg-transparent p-2';
const boton = 'rounded border border-cyan-600 px-3 py-2 font-semibold disabled:opacity-50';
const opciones = ['PENDIENTE','APROBADA','EN_TRANSITO','ENTREGADA','PAGADA','A_CREDITO','ANULADA'];
const titulos: Record<string,string> = {oc:'Orden de compra', estado_pago:'Estado de pago', estado_interno:'Estado interno', estado_pasarela:'Estado en pasarela', limite_cliente:'Límite por cliente', importe_fijo:'Importe fijo'};
const sinId = (filas: Fila[]) => Object.keys(filas[0] ?? {}).filter(col => col !== 'id');

function exportar(nombre: string, filas: Fila[]) {
  const columnas=sinId(filas);
  const celda=(valor: Fila[string]) => {
    let texto=String(valor??'');
    if (/^\s*[=+@-]/.test(texto)) texto="'"+texto;
    return '"'+texto.replace(/"/g,'""')+'"';
  };
  const contenido=[columnas.join(';'),...filas.map(f=>columnas.map(c=>celda(f[c])).join(';'))].join('\r\n');
  const url=URL.createObjectURL(new Blob(['\ufeff',contenido],{type:'text/csv;charset=utf-8'}));
  const enlace=document.createElement('a');
  enlace.href=url; enlace.download=nombre+'.csv'; enlace.click();
  window.setTimeout(()=>URL.revokeObjectURL(url),1000);
}

function Tabla({ filas }: { filas: Fila[] }) {
  const columnas=sinId(filas);
  if (!filas.length) return <p>Sin registros de prueba.</p>;
  return <div className="overflow-x-auto"><table className="w-full text-left text-sm">
    <thead><tr>{columnas.map(c=><th className="p-2" key={c}>{titulos[c]??c.replace(/_/g,' ')}</th>)}</tr></thead>
    <tbody>{filas.map((fila,i)=><tr key={String(fila.id??i)}>{columnas.map(c=><td className="border-t border-slate-500 p-2" key={c}>{String(fila[c]??'—')}</td>)}</tr>)}</tbody>
  </table></div>;
}

function Comprobante({ orden }: { orden: Fila }) {
  const imprimir = () => {
    const ventana=window.open('','_blank');
    if (!ventana) return;
    ventana.opener=null;
    const doc=ventana.document;
    doc.title='Comprobante de demostración';
    const titulo=doc.createElement('h1'); titulo.textContent='Comprobante de demostración';
    doc.body.append(titulo);
    for (const campo of ['comprobante','empresa','oc','titulo','moneda','bruto','descuento','base','impuesto','total','estado_pago']) {
      const linea=doc.createElement('p');
      linea.textContent=(titulos[campo]??campo.replace(/_/g,' '))+': '+String(orden[campo]??'');
      doc.body.append(linea);
    }
    const nota=doc.createElement('p'); nota.textContent='Documento sin valor tributario.'; doc.body.append(nota);
    ventana.focus(); ventana.print();
  };
  return <button type="button" className={boton} disabled={!orden.comprobante} onClick={imprimir}>Imprimir comprobante o guardar PDF</button>;
}

export const DemostracionComercial = ({ administrador = false, cliente = false }: { administrador?: boolean; cliente?: boolean }) => {
  const rutaEmpresas = base + (cliente ? '/cliente/empresas' : '/empresas');
  const [empresas,setEmpresas]=useState<Fila[]>([]);
  const [pasarela,setPasarela]=useState<Fila[]>([]);
  const [beneficios,setBeneficios]=useState<Fila[]>([]);
  const [nomina,setNomina]=useState<Fila[]>([]);
  const [seleccion,setSeleccion]=useState('');
  const [estado,setEstado]=useState('APROBADA');
  const [ocupado,setOcupado]=useState(false);
  const [error,setError]=useState('');
  const [revision,setRevision]=useState(0);
  const [condicion,setCondicion]=useState('CONTADO');
  const [mensaje,setMensaje]=useState('');
  const orden=empresas.find(f=>f.id===seleccion);

  const excel=async()=>{
    try {
      const respuesta=await apiClient.get<Blob>(rutaEmpresas+'/excel',{responseType:'blob'});
      const url=URL.createObjectURL(respuesta.data);
      const enlace=document.createElement('a');
      enlace.href=url;enlace.download='ordenes-empresa-prueba.xlsx';enlace.click();
      window.setTimeout(()=>URL.revokeObjectURL(url),1000);
    } catch(e) {setError(getApiErrorMessage(e));}
  };

  const imprimirHistorial=()=>{
    const ventana=window.open('','_blank');
    if (!ventana) {setError('Permite ventanas emergentes para imprimir el historial.');return;}
    ventana.opener=null;
    const doc=ventana.document;
    doc.title='Historial corporativo de prueba';
    const h=doc.createElement('h1');h.textContent=doc.title;doc.body.append(h);
    const tabla=doc.createElement('table');tabla.style.width='100%';doc.body.append(tabla);
    const columnas=['empresa','oc','titulo','moneda','total','estado','estado_pago','comprobante'];
    const encabezado=tabla.insertRow();
    columnas.forEach(c=>{const th=doc.createElement('th');th.textContent=titulos[c]??c;encabezado.append(th);});
    for(const orden of empresas) {
      const fila=tabla.insertRow();
      columnas.forEach(c=>{fila.insertCell().textContent=String(orden[c]??'—');});
    }
    ventana.focus();ventana.print();
  };

  useEffect(()=>{
    const control=new AbortController();
    const cargar=async()=>{
      try {
        const ordenes=await apiClient.get<Fila[]>(rutaEmpresas,{signal:control.signal});
        if(control.signal.aborted) return;
        setEmpresas(ordenes.data);
        if(!cliente) {
          const [operaciones,promociones]=await Promise.all([
            apiClient.get<Fila[]>(base+'/pasarela',{signal:control.signal}),
            apiClient.get<Fila[]>('/pruebas/comerciales/beneficios',{signal:control.signal}),
          ]);
          if(!control.signal.aborted) {setPasarela(operaciones.data);setBeneficios(promociones.data);}
        }
      } catch(fallo) {if(!control.signal.aborted) setError(getApiErrorMessage(fallo));}
    };
    void cargar();
    const intervalo=window.setInterval(()=>void cargar(),30000);
    return ()=>{control.abort();window.clearInterval(intervalo);};
  },[cliente,rutaEmpresas,revision]);

  useEffect(()=>{
    setNomina([]);
    if(!seleccion) return;
    const control=new AbortController();
    void apiClient.get<Fila[]>(rutaEmpresas+'/'+seleccion+'/participantes',{signal:control.signal})
      .then(r=>{if(!control.signal.aborted) setNomina(r.data);})
      .catch(e=>{if(!control.signal.aborted) setError(getApiErrorMessage(e));});
    return ()=>control.abort();
  },[rutaEmpresas,seleccion,revision]);

  const ejecutar=async(accion:()=>Promise<void>)=>{
    setOcupado(true);setError('');setMensaje('');
    try {await accion();setRevision(r=>r+1);}
    catch(e) {setError(getApiErrorMessage(e));}
    finally {setOcupado(false);}
  };

  const crear=(e:FormEvent<HTMLFormElement>)=>{
    e.preventDefault();
    const f=new FormData(e.currentTarget);
    const participantes=String(f.get('nomina')??'').trim().split(/\r?\n/).map(linea=>{
      const [alias,cargo]=linea.split(';').map(valor=>valor.trim());
      return {alias,cargo};
    });
    void ejecutar(async()=>{
      const respuesta=await apiClient.post<{id:string}>(rutaEmpresas,{
        gestor:Number(f.get('gestor')),empresa:f.get('empresa'),oc:String(f.get('oc')).trim().toUpperCase(),
        programa:Number(f.get('programa')),cohorte:f.get('cohorte'),cupos:Number(f.get('cupos')),
        descuento:Number(f.get('descuento')),condicion,vence:condicion==='CREDITO'?f.get('vence'):null,participantes,
      });
      setSeleccion(respuesta.data.id);setMensaje('Orden corporativa de prueba registrada.');
    });
  };

  return <section className="space-y-4 rounded border border-slate-500 p-4">
    {error && <p role="alert">{error}</p>}
    {mensaje && <p role="status">{mensaje}</p>}
    <h2 className="text-xl font-semibold">Órdenes corporativas de prueba</h2>
    <p>Consulta órdenes, cupos, nómina, crédito y comprobantes de demostración.</p>
    {administrador && !cliente && <form onSubmit={crear}>
      <fieldset className="grid gap-3 sm:grid-cols-3" disabled={ocupado}>
        <legend>Nueva orden corporativa</legend>
        <label>ID del gestor de prueba<input className={campo} name="gestor" type="number" min="1" required /></label>
        <label>Empresa ficticia<input className={campo} name="empresa" maxLength={120} required /></label>
        <label>Orden de compra<input className={campo} name="oc" maxLength={40} required /></label>
        <label>Programa de muestra<select className={campo} name="programa">
          {[1001,1002,1003,1004,1005,1006].map(id=><option key={id} value={id}>{id}</option>)}</select></label>
        <label>Cohorte<input className={campo} name="cohorte" maxLength={80} required /></label>
        <label>Cupos<input className={campo} name="cupos" type="number" min="1" max="50" defaultValue="1" required /></label>
        <label>Descuento en USD<input className={campo} name="descuento" type="number" min="0" step="0.01" defaultValue="0" required /></label>
        <label>Condición<select className={campo} value={condicion} onChange={e=>setCondicion(e.target.value)}><option>CONTADO</option><option>CREDITO</option></select></label>
        {condicion==='CREDITO' && <label>Vencimiento<input className={campo} name="vence" type="date" required /></label>}
        <label>Nómina ficticia, un alias y cargo por línea<textarea className={campo} name="nomina" placeholder="Participante 1;Analista" maxLength={6200} required /></label>
        <button className={boton}>Registrar orden corporativa</button>
      </fieldset>
    </form>}
    <Tabla filas={empresas}/>
    <div className="flex flex-wrap gap-2">
      <button type="button" className={boton} onClick={()=>setRevision(r=>r+1)}>Actualizar</button>
      <button type="button" className={boton} disabled={!empresas.length} onClick={()=>void excel()}>Descargar Excel</button>
      <button type="button" className={boton} disabled={!empresas.length} onClick={imprimirHistorial}>Imprimir historial o guardar PDF</button>
      <label>Ver detalle<select className={campo} value={seleccion} onChange={e=>setSeleccion(e.target.value)}>
        <option value="">Seleccionar orden</option>{empresas.map(f=><option key={String(f.id)} value={String(f.id)}>{f.oc} · {f.empresa}</option>)}
      </select></label>
    </div>
    {orden && <>
      <h3 className="font-semibold">Nómina de la orden {orden.oc}</h3>
      <Tabla filas={nomina}/>
      <Comprobante orden={orden}/>
      {administrador && <form className="flex flex-wrap gap-2" onSubmit={e=>{
        e.preventDefault();void ejecutar(async()=>{await apiClient.post(rutaEmpresas+'/'+seleccion+'/estado',{estado});setMensaje('Estado registrado.');});
      }}>
        <label>Estado de la orden<select className={campo} value={estado} onChange={e=>setEstado(e.target.value)}>
          {opciones.map(o=><option key={o}>{o}</option>)}</select></label>
        <button className={boton} disabled={ocupado}>Registrar estado</button>
      </form>}
    </>}
    {!cliente && <>
      <h2 className="text-xl font-semibold">Conciliación con la pasarela simulada</h2>
      <p>El conciliador contrasta las referencias, importes y estados recibidos con los intentos internos.</p>
      {administrador && <form onSubmit={e=>{
        e.preventDefault();const f=new FormData(e.currentTarget);
        void ejecutar(async()=>{await apiClient.post(base+'/pasarela',Object.fromEntries(f));setMensaje('Movimiento de pasarela importado.');});
      }}><fieldset className="grid gap-2 sm:grid-cols-3" disabled={ocupado}>
        <legend>Importar operación simulada</legend>
        <label>Referencia UUID<input className={campo} name="referencia" required maxLength={36} /></label>
        <label>Importe<input className={campo} name="importe" type="number" min="0" step="0.01" required /></label>
        <label>Moneda<select className={campo} name="moneda"><option>USD</option><option>PEN</option></select></label>
        <label>Resultado<select className={campo} name="estado"><option>PAGADA</option><option>FALLIDA</option></select></label>
        <button className={boton}>Importar movimiento</button>
      </fieldset></form>}
      <Tabla filas={pasarela}/>
      <button type="button" className={boton} disabled={!pasarela.length} onClick={()=>exportar('conciliacion-pasarela-prueba',pasarela)}>Exportar conciliación a CSV</button>
      <h2 className="text-xl font-semibold">Uso de beneficios de prueba</h2>
      <Tabla filas={beneficios}/>
      <button type="button" className={boton} disabled={!beneficios.length} onClick={()=>exportar('beneficios-prueba',beneficios)}>Exportar beneficios a CSV</button>
    </>}
  </section>;
};

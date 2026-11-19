import {useEffect,useState} from 'react';
import {conversionPrueba, type FilaConversion} from '../../services/conversionPrueba';
import {getApiErrorMessage} from '../../services/authService';

const hoy=()=>new Date().toISOString().slice(0,10);
export const ConversionPrueba = () => {
  const [desde,setDesde]=useState(()=>new Date(Date.now()-29*86400000).toISOString().slice(0,10));
  const [hasta,setHasta]=useState(hoy);
  const [filas,setFilas]=useState<FilaConversion[]>([]);
  const [dispositivo,setDispositivo]=useState('');
  const [canal,setCanal]=useState('');
  const [programa,setPrograma]=useState('');
  const [error,setError]=useState('');
  const [cargando,setCargando]=useState(true);
  useEffect(()=>{
    const control=new AbortController();
    setError('');setCargando(true);setFilas([]);
    void conversionPrueba.reporte(desde,hasta,control.signal).then(datos=>{if(!control.signal.aborted)setFilas(datos);})
      .catch(e=>{if(!control.signal.aborted)setError(getApiErrorMessage(e));})
      .finally(()=>{if(!control.signal.aborted)setCargando(false);});
    return ()=>control.abort();
  },[desde,hasta]);
  const datos=filas.filter(f=>(!dispositivo||f.dispositivo===dispositivo)&&(!canal||f.canal===canal)&&(!programa||String(f.programa??'SIN-PROGRAMA')===programa));
  const totales=datos.reduce((a,f)=>[a[0]+f.visitas,a[1]+f.inicios,a[2]+f.registradas,a[3]+f.aprobadas,a[4]+f.abandonos,a[5]+f.sinConfirmar],[0,0,0,0,0,0]);
  const nombres=['Visitas','Inicios','Registradas','Aprobadas','Abandonos','Sin confirmar'];
  const descargar=()=>{
    const csv=['Dispositivo;Canal;Programa;'+nombres.join(';'),...datos.map(f=>[f.dispositivo,f.canal,f.programa??'',f.visitas,f.inicios,f.registradas,f.aprobadas,f.abandonos,f.sinConfirmar].join(';'))].join('\r\n');
    const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));
    const a=document.createElement('a');a.href=url;a.download='conversion-prueba.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  };
  return <section className="space-y-3 rounded border border-slate-500 p-4">
    <h2 className="text-xl font-semibold">Conversión del catálogo de prueba</h2>
    <p>Cuenta sesiones con consentimiento, atribuidas al primer programa de muestra seleccionado. Un inicio sin compra registrada se considera abandono tras 24 horas. Las aprobaciones son de prueba.</p>
    <div className="flex flex-wrap gap-3">
      <label>Desde <input type="date" value={desde} onChange={e=>setDesde(e.target.value)}/></label>
      <label>Hasta <input type="date" value={hasta} onChange={e=>setHasta(e.target.value)}/></label>
      <label>Dispositivo <select value={dispositivo} onChange={e=>setDispositivo(e.target.value)}><option value="">Todos</option>{['MOVIL','TABLETA','ESCRITORIO'].map(v=><option key={v}>{v}</option>)}</select></label>
      <label>Canal <select value={canal} onChange={e=>setCanal(e.target.value)}><option value="">Todos</option><option>DIRECTO</option><option>CAMPANA</option></select></label>
      <label>Programa <select value={programa} onChange={e=>setPrograma(e.target.value)}><option value="">Todos</option><option value="SIN-PROGRAMA">Sin selección</option>{[1001,1002,1003,1004,1005,1006].map(v=><option key={v}>{v}</option>)}</select></label>
      <button type="button" disabled={!datos.length} onClick={descargar}>Descargar CSV</button>
    </div>
    {cargando && <p role="status">Cargando métricas...</p>}
    {error && <p role="alert">{error}</p>}
    {!error && !cargando && <>
      <p>Conversión de sesiones filtradas: {totales[0] ? (totales[3]*100/totales[0]).toFixed(1)+' %':'sin visitas'}</p>
      <dl className="grid gap-2 sm:grid-cols-3">{nombres.map((nombre,i)=><div key={nombre}>
        <dt>{nombre}</dt><dd>{totales[i]} <meter className="align-middle" min={0} max={Math.max(1,totales[0])} value={totales[i]}/></dd>
      </div>)}</dl>
      {datos.length>0 && <div className="overflow-x-auto"><table className="w-full text-left text-sm">
        <thead><tr>{['Dispositivo','Canal','Programa',...nombres].map(n=><th key={n} className="p-2">{n}</th>)}</tr></thead>
        <tbody>{datos.map(f=><tr key={f.dispositivo+f.canal+f.programa}>{[f.dispositivo,f.canal,f.programa??'—',f.visitas,f.inicios,f.registradas,f.aprobadas,f.abandonos,f.sinConfirmar].map((v,i)=><td key={i} className="p-2">{v}</td>)}</tr>)}</tbody>
      </table></div>}
    </>}
  </section>;
};

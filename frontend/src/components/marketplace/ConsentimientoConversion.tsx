import {useEffect,useState} from 'react';
import { conversionPrueba as conversion } from '../../services/conversionPrueba';

export const ConsentimientoConversion = () => {
  const [visible,setVisible]=useState(false);
  const [aceptada,setAceptada]=useState(conversion.aceptada);
  const [error,setError]=useState('');
  const [ocupado,setOcupado]=useState(false);
  useEffect(()=>{
    let activo=true;
    void conversion.disponible().then(async v=>{
      if(activo) setVisible(v);
      if(v) await conversion.reintentarRevocacion();
    }).catch(()=>{});
    return ()=>{activo=false;};
  },[]);
  if(!visible) return null;
  return <aside className="mx-auto my-4 max-w-5xl rounded border border-slate-500 p-3 text-sm" aria-label="Medición voluntaria de prueba">
    <p>Medición opcional del catálogo de prueba. Registra la visita, el tamaño de pantalla y si inicias o completas una compra de muestra. Los datos se conservan hasta 30 días.</p>
    {error && <p role="alert">{error}</p>}
    <button type="button" disabled={ocupado} className="mt-2 rounded border border-cyan-600 px-3 py-1"
      onClick={async()=>{
        setOcupado(true);setError('');
        try {if(aceptada) await conversion.revocar();else {await conversion.reintentarRevocacion();await conversion.aceptar();}}
        catch {setError('No se pudo confirmar el cambio. Puedes reintentarlo.');}
        finally {setAceptada(conversion.aceptada());setOcupado(false);}
      }}>{aceptada?'Retirar consentimiento':'Aceptar medición de prueba'}</button>
  </aside>;
};

import React, { useEffect, useState } from 'react';
import { apiClient } from '../../services/api';
import { getApiErrorMessage } from '../../services/authService';
import { useAuth } from '../../context/AuthContext';

type Fila = Record<string, string | number | null>;
type Reporte = Record<string, Fila[]>;
const secciones: Record<string, string> = {
  monedas: 'Ingresos por moneda', programas: 'Ventas por programa', canales: 'Operaciones por medio de pago',
  corporativos: 'Historial de matrículas corporativas', notificaciones: 'Estados de notificaciones',
};
const etiquetas: Record<string, string> = {
  titulo: 'Programa', moneda: 'Moneda', operaciones: 'Operaciones', cobrado: 'Cobrado',
  pendiente: 'Pendiente', rechazadas: 'Rechazadas', sin_matricula: 'Pagadas sin matrícula',
  vendido: 'Vendido', unidades: 'Unidades', matriculados: 'Matrículas',
  canal: 'Medio de pago', estado: 'Estado', importe: 'Importe', empresa_nombre: 'Empresa',
  programa: 'Programa', fecha_registro: 'Fecha', total_colaboradores: 'Participantes',
  total_exitosos: 'Matrículas completadas', total_fallidos: 'Fallos', tipo: 'Tipo',
  cantidad: 'Cantidad', intentos: 'Intentos', servicio: 'Servicio', referencia: 'Referencia',
  criticidad: 'Prioridad', descripcion: 'Descripción', creada_en: 'Inicio',
  actualizada_en: 'Actualización', resuelta_en: 'Resolución', duracion_segundos: 'Duración en segundos',
  comentario: 'Comentario', operador: 'Operador', fecha: 'Fecha',
};
const ocultas = new Set(['id', 'id_programa', 'id_lote']);
const Tabla: React.FC<{ filas: Fila[] }> = ({ filas }) => {
  const columnas = Object.keys(filas[0] ?? {}).filter(c => !ocultas.has(c));
  if (!filas.length) return <p className="my-3 text-sm">Sin registros para esta consulta.</p>;
  return <div className="my-4 overflow-x-auto"><table className="w-full text-left text-sm">
    <thead><tr>{columnas.map(c => <th className="p-2" key={c}>{etiquetas[c] ?? c}</th>)}</tr></thead>
    <tbody>{filas.map((fila, indice) => <tr key={String(fila.id ?? indice)}>{columnas.map(c =>
      <td className="border-t border-slate-300 p-2 dark:border-slate-700" key={c}>{String(fila[c] ?? '')}</td>)}</tr>)}</tbody>
  </table></div>;
};

export const Operaciones: React.FC = () => {
  const { user } = useAuth();
  const [desde, setDesde] = useState(new Date().toISOString().slice(0, 8) + '01');
  const [hasta, setHasta] = useState(new Date().toISOString().slice(0, 10));
  const [reporte, setReporte] = useState<Reporte>({});
  const [seccion, setSeccion] = useState('monedas');
  const [incidencias, setIncidencias] = useState<Fila[]>([]);
  const [historial, setHistorial] = useState<Fila[]>([]);
  const [seleccion, setSeleccion] = useState('');
  const [estado, setEstado] = useState('SEGUIMIENTO');
  const [comentario, setComentario] = useState('');
  const [error, setError] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [revision, setRevision] = useState(0);
  const campo = 'rounded border border-slate-400 bg-white p-2 text-slate-900 dark:bg-slate-900 dark:text-white';

  useEffect(() => {
    setReporte({});
    const controller = new AbortController();
    const cargar = async () => {
      setOcupado(true);
      try {
        const [datos, alertas] = await Promise.all([
          apiClient.get<Reporte>('/internal/operaciones/reporte', { params: { desde, hasta }, signal: controller.signal }),
          apiClient.get<Fila[]>('/internal/operaciones/incidencias', { signal: controller.signal }),
        ]);
        if (!controller.signal.aborted) {
          setReporte(datos.data); setIncidencias(alertas.data); setError('');
        }
      } catch (fallo) {
        if (!controller.signal.aborted) setError(getApiErrorMessage(fallo));
      } finally {
        if (!controller.signal.aborted) setOcupado(false);
      }
    };
    void cargar();
    const intervalo = window.setInterval(() => void cargar(), 60000);
    return () => { controller.abort(); window.clearInterval(intervalo); };
  }, [desde, hasta, revision]);

  const cambiarIncidencia = async (revisar: boolean) => {
    if (ocupado) return;
    setOcupado(true); setError('');
    try {
      if (revisar) await apiClient.post('/internal/operaciones/incidencias/revisar');
      else await apiClient.post('/internal/operaciones/incidencias/' + seleccion, { estado, comentario });
      setRevision(actual => actual + 1);
      setHistorial([]);
    } catch (fallo) { setError(getApiErrorMessage(fallo)); }
    finally { setOcupado(false); }
  };

  const exportar = () => {
    const filas = reporte[seccion] ?? [];
    const columnas = Object.keys(filas[0] ?? {}).filter(c => !ocultas.has(c));
    const celda = (valor: unknown) => {
      const texto = String(valor ?? '');
      const seguro = /^[=+@\-\t\r]/.test(texto) ? "'" + texto : texto;
      return '"' + seguro.split('"').join('""') + '"';
    };
    const csv = '\uFEFF' + [columnas.map(c => etiquetas[c] ?? c), ...filas.map(f => columnas.map(c => f[c]))]
      .map(fila => fila.map(celda).join(';')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const enlace = document.createElement('a');
    enlace.href = url; enlace.download = 'operaciones-' + seccion + '-' + desde + '-' + hasta + '.csv';
    enlace.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  return <section className="my-6 rounded-xl border border-slate-300 p-5 dark:border-slate-700">
    <h2 className="text-lg font-bold">Reportes operativos e incidencias</h2>
    <p className="my-2 text-sm">Información de órdenes, matrículas y avisos registrados. Actualización cada minuto.
      Los importes se presentan por moneda. Los lotes corporativos muestran resultados de matrícula.</p>
    {error && <p role="alert" className="my-2 text-red-600">{error}</p>}
    <div className="my-3 flex flex-wrap gap-3">
      <label>Desde <input className={campo} type="date" value={desde} onChange={e => setDesde(e.target.value)} /></label>
      <label>Hasta <input className={campo} type="date" value={hasta} onChange={e => setHasta(e.target.value)} /></label>
      <label>Reporte <select className={campo} value={seccion} onChange={e => setSeccion(e.target.value)}>
        {Object.entries(secciones).map(([id, titulo]) => <option key={id} value={id}>{titulo}</option>)}
      </select></label>
      <button className={campo} disabled={ocupado} onClick={() => setRevision(actual => actual + 1)}>Actualizar reportes</button>
      <button className={campo} disabled={!reporte[seccion]?.length || ocupado} onClick={exportar}>Descargar reporte CSV</button>
    </div>
    <h3 className="font-semibold">{secciones[seccion]}</h3>
    <Tabla filas={reporte[seccion] ?? []} />
    <h3 className="mt-6 font-semibold">Incidencias de matrícula y correo</h3>
    <p className="my-2 text-sm">La revisión detecta notificaciones fallidas y pagos que lleven más de 15 minutos sin matrícula sincronizada.
      La resolución automática indica que esa condición dejó de aparecer en los registros.</p>
    <Tabla filas={incidencias} />
    <div className="my-3 flex flex-wrap gap-3">
      <label>Incidencia <select className={campo} disabled={ocupado} value={seleccion} onChange={e => {
        setSeleccion(e.target.value); setHistorial([]);
      }}><option value="">Selecciona</option>{incidencias.map(i =>
        <option key={String(i.id)} value={String(i.id)}>{i.servicio} · {i.referencia} · {i.estado}</option>)}</select></label>
      <button className={campo} disabled={!seleccion || ocupado} onClick={async () => {
        setOcupado(true); setError('');
        try {
          const respuesta = await apiClient.get<Fila[]>('/internal/operaciones/incidencias/' + seleccion + '/historial');
          setHistorial(respuesta.data);
        } catch (fallo) { setError(getApiErrorMessage(fallo)); }
        finally { setOcupado(false); }
      }}>Consultar historial</button>
      {user?.rol === 'ADMIN' && <button className={campo} disabled={ocupado} onClick={() => void cambiarIncidencia(true)}>Revisar incidencias ahora</button>}
    </div>
    <Tabla filas={historial} />
    {user?.rol === 'ADMIN' && <form onSubmit={e => { e.preventDefault(); void cambiarIncidencia(false); }}>
      <fieldset className="flex flex-wrap items-center gap-3" disabled={ocupado || !seleccion}>
        <label>Nuevo estado <select className={campo} value={estado} onChange={e => setEstado(e.target.value)}>
          <option>ABIERTA</option><option>SEGUIMIENTO</option><option>RESUELTA</option>
        </select></label>
        <label>Acción realizada <textarea className={campo} required maxLength={1000} value={comentario} onChange={e => setComentario(e.target.value)} /></label>
        <button className={campo}>Guardar seguimiento</button>
      </fieldset>
    </form>}
  </section>;
};

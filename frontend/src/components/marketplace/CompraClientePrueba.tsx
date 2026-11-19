import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { CobrosDePrueba } from './CobrosDePrueba';
import { conversionPrueba } from '../../services/conversionPrueba';
import {
  compraClientePruebaService as compras,
  type CompraPrueba,
  type CotizacionPrueba,
  type EstadoCuota,
  type SolicitudPrueba,
} from '../../services/comprasPruebaService';
import { getApiErrorMessage } from '../../services/authService';

interface Props {
  clienteId: number;
  programas: number[];
}

type Borrador = Omit<SolicitudPrueba, 'clave' | 'acepta' | 'revision'>;
type Pendiente = { solicitud: SolicitudPrueba; cotizacion: CotizacionPrueba };

const estadoInicial: Borrador = { programas: [], codigo: '', cuotas: 1, medio: 'BCP' };
const estados: Record<string, string> = {
  PENDIENTE: 'Pendiente', EN_CUOTAS: 'En cuotas', APROBADA: 'Aprobada de prueba',
  RECHAZADA: 'Rechazada de prueba', PAGADA: 'Pagada de prueba', FALLIDA: 'Fallida', VENCIDA: 'Vencida',
};

export const CompraClientePrueba: React.FC<Props> = ({ clienteId, programas }) => {
  const primerPrograma = programas[0];
  useEffect(() => {void conversionPrueba.iniciar(primerPrograma).catch(() => {});}, [primerPrograma]);
  const almacenamiento = `lysandri.checkout.prueba.${clienteId}.${[...programas].sort((a, b) => a - b).join('-')}`;
  const [pendiente, setPendiente] = useState<Pendiente | null>(() => {
    try {
      const guardada = sessionStorage.getItem(almacenamiento);
      return guardada ? JSON.parse(guardada) : null;
    } catch {
      return null;
    }
  });
  const [datos, setDatos] = useState<Borrador>(pendiente?.solicitud ?? { ...estadoInicial, programas });
  const [cotizacion, setCotizacion] = useState<CotizacionPrueba | null>(pendiente?.cotizacion ?? null);
  const [acepta, setAcepta] = useState(false);
  const [compra, setCompra] = useState<CompraPrueba | null>(null);
  const [cuotas, setCuotas] = useState<EstadoCuota[]>([]);
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  const operando = useRef(false);
  const montado = useRef(true);

  useEffect(() => {
    montado.current = true;
    return () => { montado.current = false; };
  }, []);

  const modificar = (cambio: Partial<Borrador>) => {
    setDatos(actual => ({ ...actual, ...cambio }));
    setCotizacion(null);
    setAcepta(false);
    setError('');
  };

  const ejecutar = async (operacion: () => Promise<void>) => {
    if (operando.current) return;
    operando.current = true;
    setOcupado(true);
    setError('');
    try {
      await operacion();
    } catch (fallo) {
      if (montado.current) setError(getApiErrorMessage(fallo));
    } finally {
      operando.current = false;
      if (montado.current) setOcupado(false);
    }
  };

  const confirmar = () => ejecutar(async () => {
    let intento = pendiente;
    if (!intento) {
      if (!cotizacion || !acepta) return;
      intento = {
        solicitud: { ...datos, clave: crypto.randomUUID(), acepta: true, revision: cotizacion.revision },
        cotizacion,
      };
    }
    sessionStorage.setItem(almacenamiento, JSON.stringify(intento));
    setPendiente(intento);
    try {
      const guardada = await compras.registrar(intento.solicitud);
      void conversionPrueba.asociar(guardada.id).catch(() => {});
      if (montado.current) setCompra(guardada);
    } catch (fallo) {
      const status = axios.isAxiosError(fallo) ? fallo.response?.status : undefined;
      if (status === 400 || status === 409) {
        sessionStorage.removeItem(almacenamiento);
        if (montado.current) {
          setPendiente(null);
          setCotizacion(null);
          setAcepta(false);
        }
      }
      throw fallo;
    }
  });

  const actualizar = () => ejecutar(async () => {
    if (!compra) return;
    const seguimiento = await compras.seguimiento(compra.id);
    if (montado.current) {
      setCompra(seguimiento.compra);
      setCuotas(seguimiento.cuotas);
    }
  });

  const mostrar = (importe: number) => `${cotizacion?.moneda} ${importe.toFixed(2)}`;

  return (
    <section className="lx-checkout-section" aria-labelledby="checkout-prueba-cliente">
      <div className="lx-section-heading">
        <div>
          <h3 id="checkout-prueba-cliente">Compra de prueba</h3>
          <p>Simulación con programas de muestra. No genera cargos, comprobantes ni acceso a Moodle.</p>
        </div>
      </div>

      <div className="lx-method-panel">
        {error && <p className="lx-checkout-alert" role="alert">{error}</p>}
        {!pendiente && (
          <fieldset className="lx-fields-grid" disabled={ocupado}>
            <div className="lx-field">
              <label htmlFor="checkout-codigo-prueba">Código de descuento de prueba</label>
              <input id="checkout-codigo-prueba" value={datos.codigo} maxLength={92} placeholder="Hasta tres códigos separados por comas"
                onChange={event => modificar({ codigo: event.target.value })} />
            </div>
            <div className="lx-field">
              <label htmlFor="checkout-cuotas-prueba">Cuotas de prueba</label>
              <select id="checkout-cuotas-prueba" value={datos.cuotas}
                onChange={event => modificar({ cuotas: Number(event.target.value) })}>
                <option value="1">Una cuota</option>
                <option value="2">Dos cuotas</option>
                <option value="3">Tres cuotas</option>
              </select>
            </div>
            <div className="lx-field">
              <label htmlFor="checkout-medio-prueba">Medio de prueba</label>
              <select id="checkout-medio-prueba" value={datos.medio}
                onChange={event => modificar({ medio: event.target.value })}>
                <option value="BCP">BCP simulado</option>
                <option value="INTERBANK">Interbank simulado</option>
                <option value="IZIPAY">Izipay simulado</option>
              </select>
            </div>
            <button type="button" className="lx-checkout-primary-action"
              onClick={() => void ejecutar(async () => {
                const resultado = await compras.cotizar(datos);
                if (montado.current) {
                  setCotizacion(resultado);
                  setAcepta(false);
                }
              })}>
              Cotizar compra de prueba
            </button>
          </fieldset>
        )}

        {cotizacion && <div className="space-y-2 text-sm">
          <p>Precio base {mostrar(cotizacion.base)} · Descuento {mostrar(cotizacion.descuento)}</p>
          <p>Escenario sin intereses, impuestos ni comisiones de prueba.</p>
          <p><strong>Total simulado {mostrar(cotizacion.total)}</strong></p>
          <ol aria-label="Cronograma de prueba">
            {cotizacion.cuotas.map(cuota => {
              const estado = cuotas.find(actual => actual.numero === cuota.numero)?.estado;
              return <li key={cuota.numero}>Cuota {cuota.numero} · {cuota.vence} · {mostrar(cuota.importe)}
                {estado && ` · ${estados[estado] ?? estado}`}</li>;
            })}
          </ol>
          {!pendiente && <label className="flex items-center gap-2">
            <input type="checkbox" checked={acepta} disabled={ocupado}
              onChange={event => setAcepta(event.target.checked)} />
            Acepto el total y el cronograma de esta compra de prueba.
          </label>}
          {!compra && <button type="button" className="lx-checkout-primary-action" onClick={() => void confirmar()}
            disabled={ocupado || (!acepta && !pendiente)}>
            {pendiente ? 'Recuperar el resultado de esta solicitud' : 'Registrar compra de prueba'}
          </button>}
        </div>}

        {compra && <div className="space-y-2 text-sm">
          <p role="status">Compra de prueba registrada. Estado {estados[compra.estado] ?? compra.estado}.</p>
          <p className="break-all">Referencia {compra.id}</p>
          {compra.detalle?.medio === 'IZIPAY' && compra.detalle.cuotas.length > 1 && <CobrosDePrueba compra={compra.id} />}
          <button type="button" className="lx-checkout-primary-action" disabled={ocupado}
            onClick={() => void actualizar()}>Actualizar estado de prueba</button>
          <button type="button" className="lx-checkout-whatsapp-action" disabled={ocupado}
            onClick={() => {
              sessionStorage.removeItem(almacenamiento);
              setPendiente(null);
              setCompra(null);
              setCuotas([]);
              modificar({ ...estadoInicial, programas });
            }}>Iniciar otra compra de prueba</button>
        </div>}
      </div>
    </section>
  );
};

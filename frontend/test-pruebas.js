import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';
import esbuild from 'esbuild';
import React from 'react';

const dom = new JSDOM('<!doctype html><html><body></body></html>', {url: 'http://127.0.0.1/'});
for (const nombre of ['window','document','HTMLElement','Node','MutationObserver','getComputedStyle','sessionStorage','localStorage','navigator']) {
  Object.defineProperty(globalThis, nombre, {configurable: true, value: dom.window[nombre]});
}
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const {MemoryRouter} = await import('react-router-dom');
const {render, screen, fireEvent, cleanup, waitFor} = await import('@testing-library/react');
let usuario = {id: 1, nombre: 'Ana Prueba', email: 'ana@prueba.local', rol: 'ADMIN'};
let salidas = 0;
globalThis.pruebaAuth = {useAuth: () => ({user: usuario, isLoading: false, logout: () => {salidas++;}})};
globalThis.pruebaCuenta = {cambiarPassword: async () => ({mensaje: 'Contraseña actualizada.'})};
const programa = {id: 1001, titulo: 'Programa de prueba', precio: 89, moneda: 'USD'};
const cotizacion = {programas: [programa], moneda: 'USD', base: 89, descuento: 8.9, total: 80.1,
  codigo: 'PRUEBA10', medio: 'BCP', cuotas: [{numero: 1, vence: '2026-12-01', importe: 80.1}], revision: 'revision-1'};
const registro = datos => ({id: 'compra-1', clave: datos.clave, usuario: 1, estado: 'PENDIENTE', fecha: '2026-11-10', detalle: cotizacion});
const llamadas = {registrar: [], cotizar: [], resultado: [], beneficio: []};
const restablecer = () => {
  cleanup(); sessionStorage.clear(); salidas = 0;
  usuario = {id: 1, nombre: 'Ana Prueba', email: 'ana@prueba.local', rol: 'ADMIN'};
  for (const lista of Object.values(llamadas)) lista.length = 0;
  globalThis.pruebaComercial = {
    programas: async () => [programa], listar: async () => [],
    cuotas: async () => [{numero: 1, vence: '2026-12-01', importe: 80.1, estado: 'PENDIENTE'}],
    cuota: async () => ({...registro({}), estado: 'APROBADA'}),
    beneficio: async datos => {llamadas.beneficio.push(datos);},
    cotizar: async datos => {llamadas.cotizar.push({...datos}); return cotizacion;},
    registrar: async datos => {llamadas.registrar.push({...datos}); return registro(datos);},
    resultado: async (id, evento, resultado) => {llamadas.resultado.push({id, evento, resultado}); return {...registro({}), estado: resultado};},
  };
};
const archivos = [];
async function compilar(nombre) {
  const destino = path.resolve(import.meta.dirname, `.prueba-${nombre}.mjs`); archivos.push(destino);
  const resultado = await esbuild.build({entryPoints: [path.resolve(import.meta.dirname, `src/pages/${nombre}.tsx`)], bundle: true,
    format: 'esm', platform: 'node', write: false, external: ['react','react-router-dom','axios'],
    plugins: [{name: 'dependencias-de-prueba', setup(build) {
      build.onResolve({filter: /\/(AuthContext|authService|comprasPruebaService)$/}, args => ({path: args.path, namespace: 'pruebas'}));
      build.onLoad({filter: /.*/, namespace: 'pruebas'}, args => ({contents:
        args.path.endsWith('AuthContext') ? 'export const useAuth = () => globalThis.pruebaAuth.useAuth();' :
        args.path.endsWith('authService') ? 'export const authService = {cambiarPassword: (...args) => globalThis.pruebaCuenta.cambiarPassword(...args)}; export const getApiErrorMessage = e => e.message;' :
        'export const comprasPruebaService = new Proxy({}, {get: (_, nombre) => (...args) => globalThis.pruebaComercial[nombre](...args)});'
      }));
    }}]});
  fs.writeFileSync(destino, resultado.outputFiles[0].text);
  return import(pathToFileURL(destino).href);
}
let aprobadas = 0;
async function caso(nombre, prueba) {restablecer(); await prueba(); aprobadas++; console.log(`PASS ${nombre}`);}
const llenarCuenta = (nueva = 'NuevaPrueba2026', confirmar = nueva) => {
  fireEvent.change(screen.getByLabelText('Contraseña actual'), {target: {value: 'AnteriorPrueba2026'}});
  fireEvent.change(screen.getByLabelText('Nueva contraseña'), {target: {value: nueva}});
  fireEvent.change(screen.getByLabelText('Confirma la nueva contraseña'), {target: {value: confirmar}});
};
try {
  const {CuentaView} = await compilar('CuentaView');
  const {PruebasView} = await compilar('PruebasView');
  const cuenta = () => render(React.createElement(MemoryRouter, null, React.createElement(CuentaView)));
  const comercial = async () => {render(React.createElement(PruebasView)); await screen.findByLabelText(/Programa de prueba ·/); await waitFor(() => assert.equal(screen.getByText('Actualizar registros').disabled, false));};
  const preparar = async () => {
    await comercial(); fireEvent.click(screen.getByLabelText(/Programa de prueba ·/));
    fireEvent.click(screen.getByRole('button', {name: 'Ver cotización'}));
    await screen.findByText('He revisado las condiciones de esta compra de prueba.');
  };
  const confirmar = () => {
    fireEvent.click(screen.getByLabelText('He revisado las condiciones de esta compra de prueba.'));
    fireEvent.click(screen.getByRole('button', {name: 'Registrar compra de prueba'}));
  };
  await caso('Contraseñas diferentes no envían solicitudes', async () => {
    globalThis.pruebaCuenta.cambiarPassword = async () => {throw new Error('No debe invocarse');};
    cuenta(); llenarCuenta('NuevaPrueba2026', 'OtraPrueba2026'); fireEvent.click(screen.getByText('Guardar nueva contraseña'));
    assert.match(screen.getByRole('alert').textContent, /no coinciden/); assert.equal(salidas, 0);
  });
  await caso('Error de cambio conserva formulario y permite reintentar', async () => {
    globalThis.pruebaCuenta.cambiarPassword = async () => {throw new Error('Revisa la contraseña actual.');};
    cuenta(); llenarCuenta(); fireEvent.click(screen.getByText('Guardar nueva contraseña'));
    await screen.findByText('Revisa la contraseña actual.'); assert.equal(screen.getByLabelText('Contraseña actual').value, 'AnteriorPrueba2026');
    assert.equal(screen.getByText('Guardar nueva contraseña').disabled, false); assert.equal(salidas, 0);
  });
  await caso('Cambio confirmado cierra sesión y muestra enlace de ingreso', async () => {
    globalThis.pruebaCuenta.cambiarPassword = async () => ({mensaje: 'Contraseña actualizada.'});
    cuenta(); llenarCuenta(); fireEvent.click(screen.getByText('Guardar nueva contraseña'));
    await screen.findByText('Contraseña actualizada.'); assert.equal(salidas, 1);
    assert.equal(screen.getByRole('link', {name: 'Volver a iniciar sesión'}).getAttribute('href'), '/intranet');
  });
  await caso('Cotización exige aceptar las condiciones antes del registro', async () => {
    await preparar(); assert.equal(screen.getByText('Registrar compra de prueba').disabled, true);
    assert.equal(llamadas.registrar.length, 0); confirmar(); await screen.findByText(/guardada como PENDIENTE/);
    assert.equal(llamadas.registrar[0].acepta, true); assert.equal(llamadas.registrar[0].revision, 'revision-1');
  });
  await caso('Timeout conserva la misma solicitud y clave al reintentar', async () => {
    globalThis.pruebaComercial.registrar = async datos => {llamadas.registrar.push({...datos}); if (llamadas.registrar.length === 1) throw new Error('Sin respuesta'); return registro(datos);};
    await preparar(); confirmar(); await screen.findByText('Sin respuesta');
    assert.ok(sessionStorage.getItem('lysandri.compra.prueba.1'));
    fireEvent.click(screen.getByText('Reintentar el mismo registro')); await screen.findByText(/guardada como PENDIENTE/);
    assert.deepEqual(llamadas.registrar[0], llamadas.registrar[1]); assert.equal(sessionStorage.length, 0);
  });
  await caso('Recarga recupera la solicitud pendiente sin generar otra clave', async () => {
    const datos = {clave: 'anterior', programas: [1001], codigo: '', cuotas: 1, medio: 'BCP', acepta: true, revision: 'revision-1'};
    sessionStorage.setItem('lysandri.compra.prueba.1', JSON.stringify(datos));
    await comercial(); assert.equal(screen.getByText('Ver cotización').disabled, true);
    fireEvent.click(screen.getByText('Reintentar el mismo registro')); await screen.findByText(/guardada como PENDIENTE/);
    assert.deepEqual(llamadas.registrar, [datos]); assert.equal(llamadas.cotizar.length, 0);
  });
  await caso('Contabilidad consulta registros sin acciones administrativas', async () => {
    usuario = {...usuario, rol: 'CONTABILIDAD'};
    globalThis.pruebaComercial.listar = async () => [registro({})];
    render(React.createElement(PruebasView)); await screen.findByText('PENDIENTE');
    assert.equal(screen.queryByText('Preparar compra'), null); assert.equal(screen.queryByText('Registrar aprobación de prueba'), null);
  });
  await caso('Cambiar condiciones exige cotizar nuevamente', async () => {
    await preparar(); fireEvent.change(screen.getByLabelText('Número de cuotas'), {target: {value: '3'}});
    assert.equal(screen.queryByText('Registrar compra de prueba'), null);
    fireEvent.click(screen.getByText('Ver cotización')); await screen.findByText('He revisado las condiciones de esta compra de prueba.');
    assert.equal(llamadas.cotizar.at(-1).cuotas, 3);
  });
  await caso('Beneficio de prueba conserva porcentaje, cupos y vigencia elegidos', async () => {
    await comercial(); fireEvent.change(screen.getByLabelText('Descuento (%)'), {target: {value: '15'}});
    fireEvent.click(screen.getByText('Guardar beneficio de prueba')); await screen.findByText(/Beneficio de prueba guardado/);
    assert.equal(llamadas.beneficio[0].porcentaje, 15); assert.equal(llamadas.beneficio[0].cupos, 5); assert.equal(llamadas.beneficio[0].activo, true);
  });
  await caso('Error al cargar datos muestra el fallo y permite actualizar', async () => {
    globalThis.pruebaComercial.programas = async () => {throw new Error('Backend no disponible');};
    render(React.createElement(PruebasView)); await screen.findByText('Backend no disponible');
    globalThis.pruebaComercial.programas = async () => [programa]; fireEvent.click(screen.getByText('Actualizar registros'));
    await screen.findByText(/Programa de prueba ·/); assert.equal(screen.queryByRole('alert'), null);
  });
  await caso('Resultado sin respuesta reintenta con el mismo evento', async () => {
    globalThis.pruebaComercial.listar = async () => [registro({})];
    globalThis.pruebaComercial.resultado = async (id, evento, resultado) => {
      llamadas.resultado.push({id, evento, resultado});
      if (llamadas.resultado.length === 1) throw new Error('Sin respuesta');
      return {...registro({}), estado: resultado};
    };
    await comercial(); fireEvent.click(screen.getByText('Registrar aprobación de prueba')); await screen.findByText('Sin respuesta');
    fireEvent.click(screen.getByText('Registrar aprobación de prueba')); await screen.findByText('APROBADA');
    assert.deepEqual(llamadas.resultado[0], llamadas.resultado[1]);
  });
  await caso('Cuota registrada muestra su estado guardado y actualiza la compra', async () => {
    globalThis.pruebaComercial.listar = async () => [registro({})];
    await comercial(); fireEvent.click(screen.getByText('Ver estado de cuotas'));
    await screen.findByText('Registrar pago de cuota 1');
    globalThis.pruebaComercial.cuotas = async () => [{numero: 1, vence: '2026-12-01', importe: 80.1, estado: 'PAGADA'}];
    fireEvent.click(screen.getByText('Registrar pago de cuota 1'));
    await screen.findByText('APROBADA'); await screen.findByText('PAGADA');
    assert.equal(screen.queryByText('Registrar pago de cuota 1'), null);
  });
  await caso('Error de lectura después del pago conserva el evento del mismo intento', async () => {
    globalThis.pruebaComercial.listar = async () => [registro({})];
    let lecturas = 0;
    const pagos = [];
    globalThis.pruebaComercial.cuotas = async () => {
      lecturas++;
      if (lecturas === 2) throw new Error('No se pudo actualizar el detalle');
      return [{numero: 1, vence: '2026-12-01', importe: 40.05, estado: lecturas > 2 ? 'PAGADA' : 'PENDIENTE'}];
    };
    globalThis.pruebaComercial.cuota = async (...datos) => {pagos.push(datos); return {...registro({}), estado: 'EN_CUOTAS'};};
    await comercial(); fireEvent.click(screen.getByText('Ver estado de cuotas'));
    await screen.findByText('Registrar pago de cuota 1'); fireEvent.click(screen.getByText('Registrar pago de cuota 1'));
    await screen.findByText('No se pudo actualizar el detalle'); fireEvent.click(screen.getByText('Registrar pago de cuota 1'));
    await screen.findByText('PAGADA'); assert.equal(pagos.length, 2); assert.deepEqual(pagos[0], pagos[1]);
  });
  console.log(`RESULTADO: ${aprobadas}/${aprobadas} escenarios DOM aprobados. Servicios simulados; no acredita pasarelas reales.`);
} finally {cleanup(); for (const archivo of archivos) fs.rmSync(archivo, {force: true}); dom.window.close();}

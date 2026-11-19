import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';
import esbuild from 'esbuild';
import React from 'react';

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://127.0.0.1/' });
for (const name of ['window', 'document', 'HTMLElement', 'Node', 'MutationObserver', 'sessionStorage', 'navigator']) {
  Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] });
}
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const { cleanup, fireEvent, render, screen } = await import('@testing-library/react');
const archivo = path.join(import.meta.dirname, '.prueba-checkout.mjs');
const { outputFiles } = await esbuild.build({
  entryPoints: [path.join(import.meta.dirname, 'src/components/marketplace/CompraClientePrueba.tsx')],
  bundle: true,
  platform: 'node',
  format: 'esm',
  write: false,
  external: ['react', 'axios'],
  plugins: [{
    name: 'api-de-prueba',
    setup(build) {
      build.onResolve({ filter: /conversionPrueba$/, namespace: 'file' }, () => ({path:'conversion',namespace:'conversion'}));
      build.onLoad({ filter: /.*/, namespace: 'conversion' }, () => ({contents:'export const conversionPrueba={iniciar:async()=>{},asociar:async()=>{}};'}));
      build.onResolve({ filter: /\/(comprasPruebaService|authService)$/ }, args => ({
        path: args.path,
        namespace: 'prueba',
      }));
      build.onLoad({ filter: /.*/, namespace: 'prueba' }, args => ({
        contents: args.path.endsWith('/authService') ? 'export const getApiErrorMessage = error => error.message;' :
          'export const compraClientePruebaService = new Proxy({}, {get: (_, key) => globalThis.compraPrueba[key]});',
      }));
    },
  }],
});

try {
  await fs.writeFile(archivo, outputFiles[0].text);
  const { CompraClientePrueba } = await import(pathToFileURL(archivo).href);
  const montar = () => render(React.createElement(CompraClientePrueba, { clienteId: 3, programas: [1002] }));
  const cotizacion = {
    moneda: 'USD', base: 109, descuento: 16.35, total: 92.65, codigo: 'PRUEBA15', medio: 'BCP',
    revision: 'version-1', cuotas: [
      { numero: 1, vence: '2026-11-10', importe: 46.33 },
      { numero: 2, vence: '2026-12-10', importe: 46.32 },
    ],
  };
  const compra = { id: 'referencia-de-prueba', estado: 'PENDIENTE' };
  const solicitudes = [];
  globalThis.compraPrueba = {
    cotizar: async datos => {
      assert.deepEqual(datos, { programas: [1002], codigo: 'PRUEBA15', cuotas: 2, medio: 'BCP' });
      return cotizacion;
    },
    registrar: async datos => {
      solicitudes.push(datos);
      if (solicitudes.length === 1) throw new Error('No se recibió respuesta.');
      return compra;
    },
    seguimiento: async id => {
      assert.equal(id, compra.id);
      return {
        compra: { ...compra, estado: 'EN_CUOTAS' },
        cuotas: [{ ...cotizacion.cuotas[0], estado: 'PAGADA' }, { ...cotizacion.cuotas[1], estado: 'PENDIENTE' }],
      };
    },
  };

  montar();
  fireEvent.change(screen.getByLabelText('Código de descuento de prueba'), { target: { value: 'PRUEBA15' } });
  fireEvent.change(screen.getByLabelText('Cuotas de prueba'), { target: { value: '2' } });
  fireEvent.click(screen.getByRole('button', { name: 'Cotizar compra de prueba' }));
  await screen.findByText('Total simulado USD 92.65');
  const confirmar = screen.getByRole('button', { name: 'Registrar compra de prueba' });
  assert.equal(confirmar.disabled, true);
  fireEvent.click(screen.getByLabelText('Acepto el total y el cronograma de esta compra de prueba.'));
  fireEvent.click(confirmar);
  await screen.findByText('No se recibió respuesta.');
  cleanup();

  montar();
  fireEvent.click(screen.getByRole('button', { name: 'Recuperar el resultado de esta solicitud' }));
  await screen.findByRole('status');
  assert.deepEqual(solicitudes[0], solicitudes[1]);
  assert.equal(solicitudes[1].revision, cotizacion.revision);
  fireEvent.click(screen.getByRole('button', { name: 'Actualizar estado de prueba' }));
  await screen.findByText(/Cuota 1.*Pagada de prueba/);
  assert.match(screen.getByRole('status').textContent, /En cuotas/);
  console.log('PASS Checkout de prueba cotiza, exige aceptación, recupera la misma clave y consulta cuotas');
} finally {
  cleanup();
  dom.window.close();
  await fs.rm(archivo, { force: true });
}

import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { JSDOM } from 'jsdom';
import esbuild from 'esbuild';
import React from 'react';

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://127.0.0.1/' });
for (const name of ['window', 'document', 'HTMLElement', 'Node', 'MutationObserver', 'navigator']) {
  Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] });
}
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const { cleanup, fireEvent, render, screen, waitFor } = await import('@testing-library/react');
const archivo = path.join(import.meta.dirname, '.prueba-operaciones.mjs');
const { outputFiles } = await esbuild.build({
  entryPoints: [path.join(import.meta.dirname, 'src/components/accounting/Operaciones.tsx')],
  bundle: true, platform: 'node', format: 'esm', write: false, external: ['react'],
  plugins: [{
    name: 'dependencias-de-prueba',
    setup(build) {
      build.onResolve({ filter: /\/(api|authService|AuthContext)$/ }, args => ({
        path: args.path, namespace: 'prueba',
      }));
      build.onLoad({ filter: /.*/, namespace: 'prueba' }, args => ({
        contents: args.path.endsWith('/AuthContext') ? 'export const useAuth = () => ({user: globalThis.usuarioPrueba});' :
          args.path.endsWith('/authService') ? 'export const getApiErrorMessage = error => error.message;' :
          'export const apiClient = new Proxy({}, {get: (_, key) => globalThis.apiPrueba[key]});',
      }));
    },
  }],
});

try {
  await fs.writeFile(archivo, outputFiles[0].text);
  const { Operaciones } = await import(pathToFileURL(archivo).href);
  globalThis.usuarioPrueba = { rol: 'ADMIN' };
  const llamadas = [];
  globalThis.apiPrueba = {
    get: async ruta => ({ data: ruta.endsWith('/reporte') ? {
      monedas: [{ moneda: 'PEN', cobrado: 100 }, { moneda: 'USD', cobrado: 200 }],
      corporativos: [{ empresa_nombre: 'Empresa de prueba', total_exitosos: 4 }],
      notificaciones: [{ tipo: 'ACTIVACION', estado: 'FALLIDO', cantidad: 1 }],
    } : ruta.endsWith('/historial') ? [{ estado: 'ABIERTA', comentario: 'Incidencia detectada', operador: 'SISTEMA' }] :
      [{ id: 'incidencia-1', servicio: 'MATRICULA', referencia: 'ORD-1', estado: 'ABIERTA' }] }),
    post: async (ruta, datos) => { llamadas.push({ ruta, datos }); },
  };
  render(React.createElement(Operaciones));
  await screen.findByText('PEN');
  assert.ok(screen.getByText('USD'));
  await waitFor(() => assert.equal(screen.getByText('Actualizar reportes').disabled, false));
  fireEvent.change(screen.getByLabelText('Reporte'), { target: { value: 'corporativos' } });
  await screen.findByText('Empresa de prueba');
  fireEvent.change(screen.getByLabelText('Incidencia'), { target: { value: 'incidencia-1' } });
  fireEvent.click(screen.getByText('Consultar historial'));
  await screen.findByText('Incidencia detectada');
  await waitFor(() => assert.equal(screen.getByText('Guardar seguimiento').disabled, false));
  fireEvent.change(screen.getByLabelText('Acción realizada'), { target: { value: 'Se revisó la sincronización.' } });
  fireEvent.click(screen.getByText('Guardar seguimiento'));
  await waitFor(() => assert.equal(llamadas.length, 1));
  assert.equal(llamadas[0].ruta, '/internal/operaciones/incidencias/incidencia-1');
  assert.equal(llamadas[0].datos.estado, 'SEGUIMIENTO');
  console.log('PASS Reportes por moneda, historial corporativo y seguimiento de incidencia');
  cleanup();

  globalThis.usuarioPrueba = { rol: 'CONTABILIDAD' };
  render(React.createElement(Operaciones));
  await screen.findByText('PEN');
  assert.equal(screen.queryByText('Guardar seguimiento'), null);
  assert.equal(screen.queryByText('Revisar incidencias ahora'), null);
  console.log('PASS Contabilidad consulta sin acciones administrativas');
  cleanup();

  globalThis.apiPrueba.get = async () => { throw new Error('No se pudo cargar el reporte.'); };
  render(React.createElement(Operaciones));
  await screen.findByRole('alert');
  assert.match(screen.getByRole('alert').textContent, /No se pudo cargar/);
  console.log('PASS Error de carga visible y consulta reintentable');
} finally {
  cleanup(); dom.window.close(); await fs.rm(archivo, { force: true });
}

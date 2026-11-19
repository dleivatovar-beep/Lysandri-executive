import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { build } from 'esbuild';
import { JSDOM } from 'jsdom';
import React from 'react';

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost/' });
globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.HTMLElement = dom.window.HTMLElement;
Object.defineProperty(globalThis, 'navigator', { configurable: true, value: dom.window.navigator });
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const { render, screen, fireEvent, cleanup } = await import('@testing-library/react');
const archivo = new URL(`.catalogo-${randomUUID()}.mjs`, import.meta.url);
try {
  const carpetaSilabos = new URL('../ai-service/data/docs/Cursos/', import.meta.url);
  const documentos = {};
  for (const nombre of await fs.readdir(carpetaSilabos)) {
    if (nombre.endsWith('.md')) documentos[`../../../ai-service/data/docs/Cursos/${nombre}`] = await fs.readFile(new URL(nombre, carpetaSilabos), 'utf8');
  }
  const resultado = await build({
    stdin: {
      contents: "export { ProgramRow } from './src/components/marketplace/ProgramRow'; export { PlaybookCard } from './src/components/marketplace/PlaybookCard'; export { EditorialSyllabusModal } from './src/components/marketplace/EditorialSyllabusModal'; export { obtenerSilabo } from './src/services/silabos';",
      resolveDir: new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'),
      loader: 'tsx',
    },
    bundle: true, write: false, platform: 'node', format: 'esm', packages: 'external',
    loader: { '.css': 'empty' },
    plugins: [{
      name: 'carrito',
      setup(builder) {
        builder.onLoad({ filter: /services[\\/]silabos\.ts$/ }, async ({ path }) => ({
          contents: (await fs.readFile(path, 'utf8')).replace(/import\.meta\.glob<string>\([\s\S]*?\);/, JSON.stringify(documentos) + ';'),
          loader: 'ts',
        }));
        builder.onResolve({ filter: /context\/CartContext$/ }, () => ({ path: 'carrito', namespace: 'test' }));
        builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({
          contents: 'export const useCart=()=>({cartItems:[]}); export const isPlaybookPurchasable=()=>false;',
        }));
      },
    }],
  });
  await fs.writeFile(archivo, resultado.outputFiles[0].text);
  const { ProgramRow, PlaybookCard, EditorialSyllabusModal, obtenerSilabo } = await import(archivo.href);
  const curso = {
    id: '1', programId: 1, title: 'FinOps Empresarial', description: 'Gestión del gasto cloud.',
    category: 'Finanzas', tier: 'ENTERPRISE', tags: [], duration: '8 horas',
    price: 89, currency: 'USD', activo: true, precioAprobado: false,
    disponibleParaCompra: false, origin: 'BACKEND',
  };
  let consultado;
  render(React.createElement(ProgramRow, {
    playbook: curso, onPreviewSyllabus() {}, onAddToCart() {}, onAskAi(value) { consultado = value; },
  }));
  assert.match(document.body.textContent, /89[.,]00/);
  assert.match(document.body.textContent, /8 horas/);
  assert.equal(screen.queryByRole('button', { name: /Añadir al carrito/ }), null);
  fireEvent.click(screen.getByRole('button', { name: /Consultar IA/ }));
  assert.equal(consultado.programId, 1);
  cleanup();
  render(React.createElement(PlaybookCard, { playbook: curso }));
  assert.match(document.body.textContent, /89[.,]00/);
  assert.equal(screen.queryByRole('button', { name: /Inscribirme \/ Comprar/ }), null);
  cleanup();
  render(React.createElement(PlaybookCard, { playbook: { ...curso, currency: 'INVALID' } }));
  assert.match(document.body.textContent, /Consultar precio/);
  cleanup();
  for (let id = 1; id <= 11; id++) assert.equal(obtenerSilabo(id).modulos.length, 4);
  assert.equal(obtenerSilabo(999), null);
  let cerrado = 0;
  render(React.createElement(EditorialSyllabusModal, {
    isOpen: true, playbook: curso, onClose() { cerrado++; }, onAddToCart() {}, onAskAi(value) { consultado = value; },
  }));
  assert.equal(screen.getAllByRole('heading', { level: 4 }).length, 4);
  assert.equal(screen.queryByText(/inscripción no está habilitada/i), null);
  assert.match(screen.getByRole('dialog').textContent, /Fundamentos de FinOps/);
  fireEvent.click(screen.getByRole('button', { name: /Consultar sobre este programa/ }));
  assert.equal(consultado.programId, 1);
  assert.equal(cerrado, 1);
  fireEvent.keyDown(document, { key: 'Escape' });
  assert.equal(cerrado, 2);
  console.log('PASS: precio y duración visibles, consulta al curso correcto y compra deshabilitada en ambas vistas.');
  console.log('PASS: once sílabos con cuatro módulos, modal accesible, consulta y cierre por Escape.');
} finally {
  cleanup();
  dom.window.close();
  await fs.rm(archivo, { force: true });
}
